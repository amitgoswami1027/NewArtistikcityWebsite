package com.artistikcity.auth;

import java.util.regex.Pattern;

/**
 * Normalises user-typed phone numbers to E.164 ({@code +<country><number>}).
 * Numbers without a country code use the default country (app.auth.default-country-code, 91 = India).
 */
public final class PhoneNumbers {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final Pattern INDIA_MOBILE = Pattern.compile("^\\+91[6-9][0-9]{9}$");

    private PhoneNumbers() {
    }

    /** @return the E.164 number, or null when the input can't be a valid mobile number */
    public static String normalize(String input, String defaultCountryCode) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        if (s.isEmpty() || s.length() > 30) {
            return null;
        }
        boolean plus = s.startsWith("+");
        String digits = s.replaceAll("[\\s().\\-]", "");
        if (plus) {
            digits = digits.substring(1);
        }
        if (!digits.matches("[0-9]+")) {
            return null;
        }
        String cc = defaultCountryCode == null ? "91" : defaultCountryCode.replace("+", "").trim();
        String e164;
        if (plus) {
            e164 = "+" + digits;
        } else if (digits.startsWith("00")) {
            e164 = "+" + digits.substring(2);
        } else if ("91".equals(cc) && digits.length() == 12 && digits.startsWith("91")) {
            e164 = "+" + digits;
        } else if (digits.startsWith("0") && digits.length() == 11) {
            e164 = "+" + cc + digits.substring(1); // national trunk prefix, e.g. 09876543210
        } else {
            e164 = "+" + cc + digits;
        }
        if (!E164.matcher(e164).matches()) {
            return null;
        }
        if (e164.startsWith("+91") && !INDIA_MOBILE.matcher(e164).matches()) {
            return null; // Indian mobiles are 10 digits starting 6-9
        }
        return e164;
    }

    /** +919876543210 -> +91 ••••• 43210 (for messages and the UI) */
    public static String mask(String e164) {
        if (e164 == null || e164.length() < 8) {
            return e164;
        }
        String cc = e164.startsWith("+91") ? "+91" : e164.substring(0, Math.min(3, e164.length() - 5));
        return cc + " ••••• " + e164.substring(e164.length() - 5);
    }
}
