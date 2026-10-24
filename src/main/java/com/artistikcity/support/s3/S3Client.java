package com.artistikcity.support.s3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A small Amazon S3 client for the operations the site needs (put, get, head, copy, delete), built on
 * java.net.http and AWS Signature Version 4. No SDK dependency: credentials come from {@link AwsCredentialsProvider}
 * (environment variables, or the EC2 instance role through IMDSv2).
 *
 * <p>Requests use virtual-hosted style ({@code https://bucket.s3.region.amazonaws.com/key}) unless an endpoint
 * override is configured (tests / S3-compatible stores), which uses path style ({@code endpoint/bucket/key}).
 * Bodies are sent with {@code UNSIGNED-PAYLOAD} (allowed by S3 over TLS), so large files stream without hashing.
 */
public class S3Client {

    /** Thrown for any S3 answer other than success / not found. */
    public static class S3Exception extends UncheckedIOException {
        private final int status;

        public S3Exception(int status, String message) {
            super(new IOException("S3 " + status + ": " + message));
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /** An object read from S3. The caller must close {@link #body}. */
    public record S3Object(int status, InputStream body, long length, String contentType, String etag, String lastModified,
                           String cacheControl, String contentRange) {
    }

    private final String bucket;
    private final String region;
    private final URI endpoint;          // null = AWS
    private final Supplier<AwsCredentials> credentials;
    private final HttpClient http;
    private final Clock clock;

    public S3Client(String bucket, String region, String endpointOverride, Supplier<AwsCredentials> credentials) {
        this(bucket, region, endpointOverride, credentials, Clock.systemUTC());
    }

    S3Client(String bucket, String region, String endpointOverride, Supplier<AwsCredentials> credentials, Clock clock) {
        if (bucket == null || bucket.isBlank() || region == null || region.isBlank()) {
            throw new IllegalArgumentException("S3 needs a bucket and a region (S3_BUCKET, AWS_REGION)");
        }
        this.bucket = bucket;
        this.region = region;
        this.endpoint = endpointOverride == null || endpointOverride.isBlank() ? null : URI.create(endpointOverride.replaceAll("/+$", ""));
        this.credentials = credentials;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).version(HttpClient.Version.HTTP_1_1).build();
    }

    public String bucket() {
        return bucket;
    }

    // ------------------------------------------------------------------ operations

    /** Uploads bytes (retried on network errors and 5xx, since the body can be re-sent). */
    public void put(String key, byte[] bytes, String contentType, String cacheControl) {
        retry(() -> {
            send("PUT", key, headers(contentType, cacheControl), HttpRequest.BodyPublishers.ofByteArray(bytes), bytes.length, true);
            return null;
        });
    }

    /** Uploads a stream of known length (not retried: a stream can only be read once). Up to 5 GB per object. */
    public void put(String key, InputStream in, long length, String contentType, String cacheControl) {
        send("PUT", key, headers(contentType, cacheControl), HttpRequest.BodyPublishers.fromPublisher(
                HttpRequest.BodyPublishers.ofInputStream(() -> in), length), length, true);
    }

    /** The object, or empty when it does not exist. */
    public Optional<S3Object> get(String key) {
        return get(key, null);
    }

    /**
     * The object (or the requested byte range, e.g. {@code bytes=0-1023}, answered with status 206), or empty when it
     * does not exist. A range S3 cannot satisfy is reported as status 416 with an empty body.
     */
    public Optional<S3Object> get(String key, String range) {
        return retry(() -> {
            Map<String, String> h = new LinkedHashMap<>();
            if (range != null && range.matches("bytes=[0-9]*-[0-9]*")) {
                h.put("range", range);
            }
            HttpResponse<InputStream> r = send("GET", key, h, HttpRequest.BodyPublishers.noBody(), 0, false);
            if (r.statusCode() == 404) {
                close(r.body());
                return Optional.empty();
            }
            return Optional.of(new S3Object(r.statusCode(), r.body(), r.headers().firstValueAsLong("Content-Length").orElse(-1),
                    r.headers().firstValue("Content-Type").orElse("application/octet-stream"), r.headers().firstValue("ETag").orElse(null),
                    r.headers().firstValue("Last-Modified").orElse(null), r.headers().firstValue("Cache-Control").orElse(null),
                    r.headers().firstValue("Content-Range").orElse(null)));
        });
    }

    public byte[] read(String key) {
        S3Object o = get(key).orElseThrow(() -> new S3Exception(404, "no such key " + key));
        try (InputStream in = o.body()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean exists(String key) {
        return retry(() -> {
            HttpResponse<InputStream> r = send("HEAD", key, Map.of(), HttpRequest.BodyPublishers.noBody(), 0, false);
            close(r.body());
            return r.statusCode() == 200;
        });
    }

    /** Server-side copy inside the bucket (objects up to 5 GB). Missing source = no-op, like the local disk version. */
    public void copy(String fromKey, String toKey) {
        retry(() -> {
            Map<String, String> h = new LinkedHashMap<>();
            h.put("x-amz-copy-source", "/" + bucket + "/" + SigV4.uriEncode(fromKey, false));
            HttpResponse<InputStream> r = send("PUT", toKey, h, HttpRequest.BodyPublishers.noBody(), 0, false);
            String body = readBody(r);
            if (r.statusCode() == 404) {
                return null;
            }
            // S3 can answer 200 with an <Error> body for copies
            if (r.statusCode() / 100 != 2 || body.contains("<Error>")) {
                throw new S3Exception(r.statusCode() / 100 == 2 ? 500 : r.statusCode(), errorCode(body));
            }
            return null;
        });
    }

    public void delete(String key) {
        retry(() -> {
            send("DELETE", key, Map.of(), HttpRequest.BodyPublishers.noBody(), 0, true);
            return null;
        });
    }

    // ------------------------------------------------------------------ plumbing

    private static Map<String, String> headers(String contentType, String cacheControl) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("content-type", contentType == null ? "application/octet-stream" : contentType);
        if (cacheControl != null) {
            h.put("cache-control", cacheControl);
        }
        return h;
    }

    URI uri(String key) {
        String path = SigV4.uriEncode(key, false);
        if (endpoint != null) {
            return URI.create(endpoint + "/" + bucket + "/" + path);
        }
        return URI.create("https://" + bucket + ".s3." + region + ".amazonaws.com/" + path);
    }

    private HttpResponse<InputStream> send(String method, String key, Map<String, String> extra, HttpRequest.BodyPublisher body,
                                           long length, boolean failOnError) {
        if (key == null || key.isBlank() || key.startsWith("/")) {
            throw new IllegalArgumentException("Bad S3 key: " + key);
        }
        URI uri = uri(key);
        AwsCredentials c = credentials.get();
        Map<String, String> h = new LinkedHashMap<>(extra);
        String host = uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
        h.put("host", host);
        Map<String, String> signed = SigV4.sign(method, uri.getRawPath(), uri.getRawQuery(), h, SigV4.UNSIGNED_PAYLOAD, c, region, "s3", clock.instant());
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(10)).method(method, body);
        for (Map.Entry<String, String> e : signed.entrySet()) {
            String n = e.getKey();
            if (!n.equals("host")) {   // restricted: the HTTP client sets Host itself (same value)
                b.header(n, e.getValue());
            }
        }
        try {
            HttpResponse<InputStream> r = http.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (failOnError && r.statusCode() / 100 != 2) {
                String text = readBody(r);
                throw new S3Exception(r.statusCode(), errorCode(text) + " (" + method + " " + key + ")");
            }
            if (!failOnError && r.statusCode() / 100 != 2 && r.statusCode() != 404 && r.statusCode() != 416) {
                String text = readBody(r);
                throw new S3Exception(r.statusCode(), errorCode(text) + " (" + method + " " + key + ")");
            }
            return r;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("interrupted", e));
        }
    }

    private static <T> T retry(Supplier<T> op) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return op.get();
            } catch (S3Exception e) {
                if (e.status() < 500 && e.status() != 429) {
                    throw e;
                }
                last = e;
            } catch (UncheckedIOException e) {
                last = e;
            }
            try {
                Thread.sleep(200L * (attempt + 1) * (attempt + 1));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw last;
    }

    private static String readBody(HttpResponse<InputStream> r) {
        try (InputStream in = r.body() == null ? new ByteArrayInputStream(new byte[0]) : r.body()) {
            return new String(in.readNBytes(64 * 1024), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String errorCode(String xml) {
        int a = xml.indexOf("<Code>");
        int b = xml.indexOf("</Code>");
        String code = a >= 0 && b > a ? xml.substring(a + 6, b) : "error";
        int m1 = xml.indexOf("<Message>");
        int m2 = xml.indexOf("</Message>");
        return m1 >= 0 && m2 > m1 ? code + ": " + xml.substring(m1 + 9, m2) : code;
    }

    private static void close(InputStream in) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException ignored) {
            // nothing to do
        }
    }
}
