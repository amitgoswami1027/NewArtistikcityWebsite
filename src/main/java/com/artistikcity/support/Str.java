package com.artistikcity.support;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;

/** String helpers (Laravel's Str facade + app/helpers.php). */
public final class Str {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private Str() {
    }

    /** Str::slug(). */
    public static String slug(String value) {
        if (value == null) {
            return "";
        }
        String s = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.toLowerCase(Locale.ROOT).replace("@", "-at-");
        s = s.replaceAll("[^a-z0-9\\s-]", "");
        s = s.trim().replaceAll("[\\s-]+", "-");
        return s.replaceAll("^-+|-+$", "");
    }

    /** Str::random(). */
    public static String random(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }

    /** get_avatar(): initials of each word. */
    public static String avatar(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String w : name.split("[\\s\\-.]")) {
            if (!w.isEmpty()) {
                sb.append(w.charAt(0));
            }
        }
        return sb.toString();
    }

    /** replace_extension(). */
    public static String replaceExtension(String filename, String ext) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        return base + "." + ext;
    }

    public static String ucfirst(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String limit(String s, int n) {
        if (s == null || s.length() <= n) {
            return s;
        }
        return s.substring(0, n);
    }

    /** Strip HTML tags (for plain text excerpts). */
    public static String stripTags(String html) {
        return html == null ? null : html.replaceAll("<[^>]*>", "");
    }
}
