package com.artistikcity.auth;

import java.util.Locale;
import java.util.Set;

/**
 * Password rules in the spirit of NIST SP 800-63B: at least 8 characters (up to 128), no composition rules,
 * but block very common passwords and ones built from the person's own name or e-mail.
 */
public final class PasswordPolicy {

    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password12", "password123", "password1234", "passw0rd", "p@ssw0rd", "p@ssword",
            "12345678", "123456789", "1234567890", "0123456789", "87654321", "11111111", "00000000", "12341234",
            "qwerty123", "qwertyuiop", "qwerty12", "1q2w3e4r", "1q2w3e4r5t", "1qaz2wsx", "zaq12wsx", "asdfghjkl",
            "iloveyou", "iloveyou1", "sunshine", "princess", "football", "baseball", "welcome1", "welcome123",
            "abc12345", "abcd1234", "abcdefgh", "letmein1", "trustno1", "dragon123", "monkey123", "superman",
            "starwars", "whatever", "freedom1", "shadow123", "master123", "computer", "michael1", "jennifer",
            "india123", "india@123", "mumbai123", "delhi123", "artistik", "artistikcity", "artistik123", "artist123",
            "painting", "painting1", "drawing1", "changeme", "default1", "admin123", "administrator", "secret123");

    private PasswordPolicy() {
    }

    /** @return a message describing what is wrong, or null when the password is acceptable */
    public static String problem(String password, String email, String name) {
        if (password == null || password.length() < 8) {
            return "Use at least 8 characters.";
        }
        if (password.length() > 128) {
            return "Use at most 128 characters.";
        }
        String p = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(p) || p.chars().distinct().count() <= 2) {
            return "That password is too easy to guess. Try a short phrase of a few words.";
        }
        if (email != null && email.contains("@")) {
            String local = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            if (local.length() >= 4 && p.contains(local)) {
                return "Don't use your e-mail address in your password.";
            }
        }
        if (name != null) {
            String letters = p.replaceAll("[^a-z]", "");
            for (String part : name.toLowerCase(Locale.ROOT).split("\\s+")) {
                if (part.length() >= 3 && letters.equals(part)) {
                    return "Don't build your password from your name.";
                }
            }
        }
        return null;
    }
}
