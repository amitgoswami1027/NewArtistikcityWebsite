package com.artistikcity.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Cryptographic helpers shared by the authentication code.
 * <ul>
 *   <li>Secrets handed to the browser or sent by e-mail/SMS are random values from {@link SecureRandom}.</li>
 *   <li>Only their SHA-256 (tokens) or HMAC-SHA256 (short OTP codes) is ever stored, so a database leak
 *       does not reveal usable tokens.</li>
 *   <li>Comparisons of secrets use {@link MessageDigest#isEqual}, which runs in constant time.</li>
 * </ul>
 */
public final class Tokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();

    private Tokens() {
    }

    /** {@code bytes} random bytes, base64url encoded (32 bytes = 256 bits = 43 characters). */
    public static String random(int bytes) {
        return B64URL.encodeToString(randomBytes(bytes));
    }

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    /** Uniformly distributed numeric code with exactly {@code digits} digits (leading zeros allowed). */
    public static String numericCode(int digits) {
        StringBuilder sb = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    public static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.UTF_8)));
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hmacSha256Hex(byte[] key, String data) {
        return HexFormat.of().formatHex(hmacSha256(key, data.getBytes(StandardCharsets.UTF_8)));
    }

    /** Constant-time equality for two strings (e.g. hex digests, state values). */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static String b64url(byte[] data) {
        return B64URL.encodeToString(data);
    }

    public static byte[] fromB64url(String s) {
        if (s == null) {
            throw new IllegalArgumentException("missing base64url value");
        }
        // tolerate standard base64 and padding from older clients
        return B64URL_DEC.decode(s.replace('+', '-').replace('/', '_').replace("=", ""));
    }

    /** PKCE S256 code challenge for a verifier (RFC 7636). */
    public static String pkceChallenge(String verifier) {
        return b64url(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
    }
}
