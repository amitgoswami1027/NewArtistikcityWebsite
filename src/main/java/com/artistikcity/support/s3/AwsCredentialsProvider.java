package com.artistikcity.support.s3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Where the app gets its AWS credentials, in the same order as the AWS SDKs:
 * <ol>
 *   <li>environment variables {@code AWS_ACCESS_KEY_ID} / {@code AWS_SECRET_ACCESS_KEY} (+ {@code AWS_SESSION_TOKEN}) -
 *       for running on a laptop;</li>
 *   <li>the EC2 instance role through the instance metadata service, version 2 (session token). This is the normal case on
 *       AWS: no keys are stored anywhere. Docker containers need the instance's metadata hop limit set to 2.</li>
 * </ol>
 * Role credentials are cached and refreshed five minutes before they expire.
 */
public class AwsCredentialsProvider implements Supplier<AwsCredentials> {

    static final String DEFAULT_IMDS = "http://169.254.169.254";

    private final Map<String, String> env;
    private final String imds;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json = new ObjectMapper();
    private volatile AwsCredentials cached;

    public AwsCredentialsProvider() {
        // AWS_EC2_METADATA_SERVICE_ENDPOINT: same override the AWS SDKs read (only used for tests)
        this(System.getenv(), System.getenv().getOrDefault("AWS_EC2_METADATA_SERVICE_ENDPOINT", DEFAULT_IMDS), Clock.systemUTC());
    }

    AwsCredentialsProvider(Map<String, String> env, String imdsEndpoint, Clock clock) {
        this.env = env;
        this.imds = imdsEndpoint.replaceAll("/+$", "");
        this.clock = clock;
    }

    @Override
    public AwsCredentials get() {
        String id = env.get("AWS_ACCESS_KEY_ID");
        String secret = env.get("AWS_SECRET_ACCESS_KEY");
        if (id != null && !id.isBlank() && secret != null && !secret.isBlank()) {
            return new AwsCredentials(id, secret, env.get("AWS_SESSION_TOKEN"), null);
        }
        AwsCredentials c = cached;
        if (c == null || c.expiration() == null || clock.instant().isAfter(c.expiration().minus(Duration.ofMinutes(5)))) {
            synchronized (this) {
                c = cached;
                if (c == null || c.expiration() == null || clock.instant().isAfter(c.expiration().minus(Duration.ofMinutes(5)))) {
                    c = fromInstanceRole();
                    cached = c;
                }
            }
        }
        return c;
    }

    private AwsCredentials fromInstanceRole() {
        try {
            HttpResponse<String> t = http.send(HttpRequest.newBuilder(URI.create(imds + "/latest/api/token"))
                    .timeout(Duration.ofSeconds(2)).header("X-aws-ec2-metadata-token-ttl-seconds", "21600")
                    .PUT(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            if (t.statusCode() != 200) {
                throw new IOException("metadata token: HTTP " + t.statusCode());
            }
            String token = t.body().trim();
            String role = getText("/latest/meta-data/iam/security-credentials/", token).lines().findFirst().orElse("").trim();
            if (role.isEmpty()) {
                throw new IOException("no IAM role is attached to this instance");
            }
            JsonNode n = json.readTree(getText("/latest/meta-data/iam/security-credentials/" + role, token));
            if (!"Success".equals(n.path("Code").asText("Success"))) {
                throw new IOException("role credentials: " + n.path("Code").asText());
            }
            return new AwsCredentials(n.path("AccessKeyId").asText(), n.path("SecretAccessKey").asText(), n.path("Token").asText(null),
                    Instant.parse(n.path("Expiration").asText()));
        } catch (IOException e) {
            throw new UncheckedIOException(new IOException("No AWS credentials: set AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY or run on EC2 "
                    + "with an instance role (metadata hop limit 2 for Docker). " + e.getMessage(), e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("interrupted", e));
        }
    }

    private String getText(String path, String token) throws IOException, InterruptedException {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(imds + path)).timeout(Duration.ofSeconds(2))
                .header("X-aws-ec2-metadata-token", token).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new IOException(path + ": HTTP " + r.statusCode());
        }
        return r.body();
    }
}
