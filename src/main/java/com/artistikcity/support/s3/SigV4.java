package com.artistikcity.support.s3;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * AWS Signature Version 4 (header-based), as documented in "Signing AWS API requests" and the S3
 * "Authenticating Requests (AWS Signature Version 4)" guide. Checked against AWS's published S3 examples
 * and an independent implementation in the unit tests.
 */
public final class SigV4 {

    public static final String UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD";
    public static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private SigV4() {
    }

    /**
     * Signs a request.
     *
     * @param rawPath      the already URI-encoded path (as sent on the wire), e.g. {@code /storage/my%20file.jpg}
     * @param rawQuery     the raw query string or null
     * @param headers      headers to send and sign (must include {@code host}); names are matched case-insensitively
     * @param payloadHash  hex SHA-256 of the body, or {@link #UNSIGNED_PAYLOAD}
     * @return all headers to send: the input headers plus x-amz-date, x-amz-content-sha256, x-amz-security-token (if any)
     *         and Authorization, with lower-case names
     */
    public static Map<String, String> sign(String method, String rawPath, String rawQuery, Map<String, String> headers, String payloadHash,
                                           AwsCredentials creds, String region, String service, Instant now) {
        String amzDate = AMZ_DATE.format(now);
        String day = amzDate.substring(0, 8);
        TreeMap<String, String> h = new TreeMap<>();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            h.put(e.getKey().toLowerCase(Locale.ROOT), trimAll(e.getValue()));
        }
        h.put("x-amz-date", amzDate);
        h.put("x-amz-content-sha256", payloadHash);
        if (creds.sessionToken() != null && !creds.sessionToken().isBlank()) {
            h.put("x-amz-security-token", creds.sessionToken());
        }
        StringBuilder canonicalHeaders = new StringBuilder();
        for (Map.Entry<String, String> e : h.entrySet()) {
            canonicalHeaders.append(e.getKey()).append(':').append(e.getValue()).append('\n');
        }
        String signedHeaders = String.join(";", h.keySet());
        String canonicalRequest = method + "\n"
                + (rawPath == null || rawPath.isEmpty() ? "/" : rawPath) + "\n"
                + canonicalQuery(rawQuery) + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;
        String scope = day + "/" + region + "/" + service + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + hex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        byte[] key = hmac(("AWS4" + creds.secretKey()).getBytes(StandardCharsets.UTF_8), day);
        key = hmac(key, region);
        key = hmac(key, service);
        key = hmac(key, "aws4_request");
        String signature = hex(hmac(key, stringToSign));
        Map<String, String> out = new LinkedHashMap<>(h);
        out.put("authorization", "AWS4-HMAC-SHA256 Credential=" + creds.accessKey() + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature);
        return out;
    }

    /** RFC 3986 encoding as AWS wants it: unreserved characters kept, everything else %XX (upper case); '/' kept in paths. */
    public static String uriEncode(String s, boolean encodeSlash) {
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~'
                    || (c == '/' && !encodeSlash)) {
                sb.append(c);
            } else {
                sb.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return sb.toString();
    }

    static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        List<String[]> pairs = new ArrayList<>();
        for (String p : rawQuery.split("&")) {
            int i = p.indexOf('=');
            pairs.add(new String[] {i < 0 ? p : p.substring(0, i), i < 0 ? "" : p.substring(i + 1)});
        }
        pairs.sort((a, b) -> a[0].equals(b[0]) ? a[1].compareTo(b[1]) : a[0].compareTo(b[0]));
        StringBuilder sb = new StringBuilder();
        for (String[] p : pairs) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(p[0]).append('=').append(p[1]);
        }
        return sb.toString();
    }

    private static String trimAll(String v) {
        return v == null ? "" : v.trim().replaceAll("\\s+", " ");
    }

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }
}
