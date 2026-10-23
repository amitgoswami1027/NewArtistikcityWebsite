package com.artistikcity.auth;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokensAndPolicyTest {

    @Test
    void randomTokensAreLongAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String t = Tokens.random(32);
            assertThat(t).hasSize(43).matches("[A-Za-z0-9_-]+");
            assertThat(seen.add(t)).isTrue();
        }
    }

    @Test
    void numericCodesHaveExactlySixDigitsAndCoverAllDigits() {
        Set<Character> digits = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String c = Tokens.numericCode(6);
            assertThat(c).matches("[0-9]{6}");
            c.chars().forEach(ch -> digits.add((char) ch));
        }
        assertThat(digits).hasSize(10);
    }

    @Test
    void pkceChallengeMatchesRfc7636Example() {
        // RFC 7636 appendix B
        assertThat(Tokens.pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void hashesAreStableAndComparedInConstantTime() {
        assertThat(Tokens.sha256Hex("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(Tokens.constantTimeEquals("a", "a")).isTrue();
        assertThat(Tokens.constantTimeEquals("a", "b")).isFalse();
        assertThat(Tokens.constantTimeEquals(null, "b")).isFalse();
    }

    @Test
    void phoneNumbersAreNormalisedToE164() {
        assertThat(PhoneNumbers.normalize("98765 43210", "91")).isEqualTo("+919876543210");
        assertThat(PhoneNumbers.normalize("+91 98765-43210", "91")).isEqualTo("+919876543210");
        assertThat(PhoneNumbers.normalize("09876543210", "91")).isEqualTo("+919876543210");
        assertThat(PhoneNumbers.normalize("919876543210", "91")).isEqualTo("+919876543210");
        assertThat(PhoneNumbers.normalize("0044 7911 123456", "91")).isEqualTo("+447911123456");
        assertThat(PhoneNumbers.normalize("+1 (415) 555-2671", "91")).isEqualTo("+14155552671");
        assertThat(PhoneNumbers.normalize("12345", "91")).isNull();          // too short
        assertThat(PhoneNumbers.normalize("5876543210", "91")).isNull();     // Indian mobiles start 6-9
        assertThat(PhoneNumbers.normalize("98765abc10", "91")).isNull();
        assertThat(PhoneNumbers.normalize("", "91")).isNull();
        assertThat(PhoneNumbers.mask("+919876543210")).isEqualTo("+91 ••••• 43210");
    }

    @Test
    void passwordPolicy() {
        assertThat(PasswordPolicy.problem("short", null, null)).contains("8 characters");
        assertThat(PasswordPolicy.problem("password123", null, null)).contains("easy to guess");
        assertThat(PasswordPolicy.problem("aaaaaaaaaa", null, null)).contains("easy to guess");
        assertThat(PasswordPolicy.problem("aarav.sharma!2026", "aarav.sharma@example.com", null)).contains("e-mail");
        assertThat(PasswordPolicy.problem("aarav2026!", "x@example.com", "Aarav Sharma")).contains("name");
        assertThat(PasswordPolicy.problem("watercolour skies at dusk", "aarav@example.com", "Aarav Sharma")).isNull();
    }

    @Test
    void rateLimiterBlocksAfterTheLimitAndReportsRetry() {
        RateLimiter r = new RateLimiter();
        for (int i = 0; i < 3; i++) {
            assertThat(r.hit("k", 3, 60).allowed()).isTrue();
        }
        RateLimiter.Decision d = r.hit("k", 3, 60);
        assertThat(d.allowed()).isFalse();
        assertThat(d.retryAfterSeconds()).isBetween(1L, 60L);
        assertThat(r.peek("other", 3, 60).allowed()).isTrue();
        r.reset("k");
        assertThat(r.hit("k", 3, 60).allowed()).isTrue();
    }
}
