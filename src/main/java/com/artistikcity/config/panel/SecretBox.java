package com.artistikcity.config.panel;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM for the secrets saved in the Configuration Panel ({@code config_overrides.value_enc}).
 * The key never lives in the database: it is derived (SHA-256) from {@code CONFIG_ENCRYPTION_KEY}, or, when that is not
 * set, from {@code OTP_SECRET} (already required in production). Stored form: {@code v1:<base64 iv>:<base64 ciphertext+tag>}.
 */
public final class SecretBox {

    private static final String PREFIX = "v1:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    private SecretBox(byte[] material) {
        try {
            this.key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(material), "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** null when there is no key material (secrets can't be saved then). */
    public static SecretBox of(String material) {
        if (material == null || material.isBlank()) return null;
        return new SecretBox(("artistikcity-config-panel|" + material.trim()).getBytes(StandardCharsets.UTF_8));
    }

    public String seal(String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] out = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder b = Base64.getEncoder();
            return PREFIX + b.encodeToString(iv) + ":" + b.encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt the value", e);
        }
    }

    /** The plain text, or null when the value was sealed with another key (or tampered with). */
    public String open(String sealed) {
        if (sealed == null || !sealed.startsWith(PREFIX)) return null;
        String[] parts = sealed.substring(PREFIX.length()).split(":", 2);
        if (parts.length != 2) return null;
        try {
            Base64.Decoder b = Base64.getDecoder();
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, b.decode(parts[0])));
            return new String(c.doFinal(b.decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
