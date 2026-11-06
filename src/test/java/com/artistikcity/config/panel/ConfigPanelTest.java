package com.artistikcity.config.panel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Configuration Panel: secret encryption and the server-side format checks (the allow-list is the security boundary). */
class ConfigPanelTest {

    @Test
    void secretsRoundTripAndAreNeverStoredInClear() {
        SecretBox box = SecretBox.of("server-key-material");
        String sealed = box.seal("whsec_abcdefghijklmnop");
        assertTrue(sealed.startsWith("v1:"));
        assertFalse(sealed.contains("whsec_abcdefghijklmnop"));
        assertNotEquals(sealed, box.seal("whsec_abcdefghijklmnop"), "a fresh IV each time");
        assertEquals("whsec_abcdefghijklmnop", box.open(sealed));
    }

    @Test
    void wrongKeyOrTamperedValueOpensToNull() {
        String sealed = SecretBox.of("key-one").seal("secret-value");
        assertNull(SecretBox.of("key-two").open(sealed));
        char[] c = sealed.toCharArray();
        int i = sealed.lastIndexOf(':') + 2;                    // inside the ciphertext, away from base64 padding bits
        c[i] = c[i] == 'A' ? 'B' : 'A';
        assertNull(SecretBox.of("key-one").open(new String(c)));
        assertNull(SecretBox.of("key-one").open("garbage"));
        assertNull(SecretBox.of(""), "no key material: secrets can't be stored");
    }

    @Test
    void onlyAllowListedKeysCanBeSet() {
        assertNotNull(ConfigCatalog.field("razorpay.key"));
        assertNull(ConfigCatalog.field("spring.datasource.password"));
        assertNull(ConfigCatalog.field("app.config.encryption-key"), "the encryption key itself is never a panel setting");
        assertTrue(ConfigCatalog.field("social.google.client-secret").secret());
        assertTrue(ConfigCatalog.field("app.sms.twilio.auth-token").secret());
        assertTrue(ConfigCatalog.field("app.payments.stripe.secret-key").secret());
        assertFalse(ConfigCatalog.field("social.google.client-id").secret());
        assertEquals("•".repeat(8), ConfigCatalog.MASK);
    }

    @Test
    void formatsAreCheckedWithDescriptiveMessages() {
        assertNull(ConfigCatalog.check(ConfigCatalog.field("razorpay.key"), "rzp_live_ABCdef123456"));
        assertTrue(ConfigCatalog.check(ConfigCatalog.field("razorpay.key"), "abc").contains("rzp_live_"));
        assertNull(ConfigCatalog.check(ConfigCatalog.field("paypal.mode"), "live"));
        assertTrue(ConfigCatalog.check(ConfigCatalog.field("paypal.mode"), "production").contains("sandbox"));
        assertNull(ConfigCatalog.check(ConfigCatalog.field("commission.inr-per-usd"), "84.25"));
        assertNotNull(ConfigCatalog.check(ConfigCatalog.field("commission.inr-per-usd"), "84 rupees"));
        assertNull(ConfigCatalog.check(ConfigCatalog.field("social.google.redirect-uri"), "https://www.artistikcity.com/social-login/google/callback"));
        assertNotNull(ConfigCatalog.check(ConfigCatalog.field("social.google.redirect-uri"), "https://evil.example.com/steal"));
        assertNull(ConfigCatalog.check(ConfigCatalog.field("social.google.scopes"), "openid email profile"));
        assertNotNull(ConfigCatalog.check(ConfigCatalog.field("social.google.scopes"), "openid <script>"));
        assertNull(ConfigCatalog.check(ConfigCatalog.field("app.sms.twilio.account-sid"), "AC" + "0123456789abcdef".repeat(2)));
        assertNotNull(ConfigCatalog.check(ConfigCatalog.field("app.sms.sns.region"), "Mumbai"));
        assertNotNull(ConfigCatalog.check(ConfigCatalog.field("app.sms.infobip.base-url"), "http://xyz.api.infobip.com"));
        assertNull(ConfigCatalog.check(ConfigCatalog.field("app.payments.razorpay.enabled"), "false"));
        assertNotNull(ConfigCatalog.check(ConfigCatalog.field("app.payments.razorpay.enabled"), "maybe"));
        assertNull(ConfigCatalog.check(ConfigCatalog.field("razorpay.key"), ""), "empty is allowed (clears the value)");
    }
}
