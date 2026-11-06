package com.artistikcity.config.panel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Every setting the Configuration Panel (Admin > System parameters) may show or change, grouped as the page shows them.
 * Anything not listed here can't be written through the panel: the allow-list is the security boundary. Each field
 * names its property key (as in config/artistikcity.yml), the environment variable that sets it on the server, how it is
 * checked and whether it is a secret (encrypted at rest, never sent back to the browser).
 */
public final class ConfigCatalog {

    public enum Type { TEXT, SECRET, SELECT, BOOL, NUMBER, URL }

    public record Field(String key, String env, String label, Type type, String help, Pattern pattern, String patternMessage,
                        List<String> options, String placeholder, int maxLength) {

        public boolean secret() {
            return type == Type.SECRET;
        }

        public Map<String, Object> describe() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("env", env);
            m.put("label", label);
            m.put("type", type.name().toLowerCase());
            m.put("help", help);
            m.put("options", options);
            m.put("placeholder", placeholder);
            m.put("maxLength", maxLength);
            return m;
        }
    }

    public record Group(String id, String title, String intro, String status, List<Field> fields) {
    }

    public record Tab(String id, String title, String icon, List<Group> groups) {
    }

    public static final String MASK = "••••••••";

    private static Field text(String key, String env, String label, String help, String regex, String msg, String placeholder) {
        return new Field(key, env, label, Type.TEXT, help, regex == null ? null : Pattern.compile(regex), msg, List.of(), placeholder, 255);
    }

    private static Field secret(String key, String env, String label, String help, String regex, String msg) {
        return new Field(key, env, label, Type.SECRET, help, regex == null ? null : Pattern.compile(regex), msg, List.of(), "", 512);
    }

    private static Field select(String key, String env, String label, String help, String... options) {
        return new Field(key, env, label, Type.SELECT, help, null, null, List.of(options), null, 40);
    }

    private static Field bool(String key, String env, String label, String help) {
        return new Field(key, env, label, Type.BOOL, help, null, null, List.of("true", "false"), null, 5);
    }

    private static Field number(String key, String env, String label, String help, String placeholder) {
        return new Field(key, env, label, Type.NUMBER, help, Pattern.compile("^\\d{1,6}(\\.\\d{1,4})?$"), "Enter a number, e.g. " + placeholder + ".", List.of(), placeholder, 12);
    }

    private static Field url(String key, String env, String label, String help, String regex, String msg, String placeholder) {
        return new Field(key, env, label, Type.URL, help, Pattern.compile(regex), msg, List.of(), placeholder, 255);
    }

    public static final List<Tab> TABS = List.of(
            new Tab("payments", "Payments", "fa-credit-card", List.of(
                    new Group("razorpay", "Razorpay", "Cards, UPI and net banking in India. Charges in INR.", null, List.of(
                            bool("app.payments.razorpay.enabled", "RAZORPAY_ENABLED", "Take payments with Razorpay", "Off = Razorpay is not offered at checkout, even with keys."),
                            text("razorpay.key", "RAZORPAY_KEY", "Key ID", "Razorpay Dashboard > Account & Settings > API keys.",
                                    "^rzp_(test|live)_[A-Za-z0-9]{8,32}$", "A Razorpay key ID looks like rzp_live_XXXXXXXXXXXX.", "rzp_live_…"),
                            secret("razorpay.secret", "RAZORPAY_SECRET", "Key secret", "Shown once when the key is generated.",
                                    "^[A-Za-z0-9]{16,64}$", "A Razorpay key secret is 16 to 64 letters and digits."),
                            secret("razorpay.webhook-secret", "RAZORPAY_WEBHOOK_SECRET", "Webhook secret",
                                    "The secret you typed when adding the webhook (Dashboard > Webhooks).", "^\\S{8,256}$", "Use 8 to 256 characters, no spaces."))),
                    new Group("paypal", "PayPal", "Cards and PayPal balances for buyers outside India. Charges in USD.", null, List.of(
                            bool("app.payments.paypal.enabled", "PAYPAL_ENABLED", "Take payments with PayPal", "Off = PayPal is not offered at checkout, even with keys."),
                            select("paypal.mode", "PAYPAL_MODE", "Environment", "Sandbox for testing with PayPal's test accounts; Live for real money.", "sandbox", "live"),
                            text("paypal.client-id", "PAYPAL_CLIENT_ID", "Client ID", "developer.paypal.com > Apps & Credentials.",
                                    "^[A-Za-z0-9_-]{20,128}$", "A PayPal client ID is 20 to 128 letters, digits, - or _.", ""),
                            secret("paypal.secret", "PAYPAL_SECRET", "Secret", null, "^[A-Za-z0-9_-]{20,128}$", "A PayPal secret is 20 to 128 letters, digits, - or _."),
                            text("paypal.webhook-id", "PAYPAL_WEBHOOK_ID", "Webhook ID", "Shown next to the webhook in the PayPal app settings.",
                                    "^[A-Z0-9]{8,40}$", "A PayPal webhook ID is 8 to 40 capital letters and digits.", ""))),
                    new Group("stripe", "Stripe", "Stripe is not connected to the checkout yet. Keys saved here are kept for when it is.", "not_connected", List.of(
                            text("app.payments.stripe.publishable-key", "STRIPE_PUBLISHABLE_KEY", "Publishable key", null,
                                    "^pk_(test|live)_[A-Za-z0-9]{10,200}$", "A Stripe publishable key starts with pk_live_ or pk_test_.", "pk_live_…"),
                            secret("app.payments.stripe.secret-key", "STRIPE_SECRET_KEY", "Secret key", null,
                                    "^(sk|rk)_(test|live)_[A-Za-z0-9]{10,200}$", "A Stripe secret key starts with sk_live_, sk_test_ or rk_."),
                            secret("app.payments.stripe.webhook-secret", "STRIPE_WEBHOOK_SECRET", "Webhook signing secret", null,
                                    "^whsec_[A-Za-z0-9]{10,200}$", "A Stripe webhook secret starts with whsec_."))),
                    new Group("currency", "Currencies and testing", "Indian buyers pay in INR through Razorpay, everyone else in USD through PayPal.", null, List.of(
                            number("commission.inr-per-usd", "COMMISSION_INR_PER_USD", "Rupees per US dollar (commissions)",
                                    "Commission prices are set in USD; this rate converts them when the buyer pays with Razorpay.", "84"),
                            bool("app.payments.test-buyers.enabled", "PAYMENTS_TEST_BUYERS", "Allow test-buyer accounts",
                                    "Accounts ticked \"Test buyer\" in People may simulate payments. Off = nobody can."))))),
            new Tab("sms", "SMS", "fa-commenting-o", List.of(
                    new Group("provider", "Provider", "Sends the sign-in codes by text message.", null, List.of(
                            select("app.sms.provider", "SMS_PROVIDER", "Send texts with",
                                    "log = not sent, written to the server log (development only).", "log", "twilio", "sns", "infobip"))),
                    new Group("twilio", "Twilio", null, null, List.of(
                            text("app.sms.twilio.account-sid", "TWILIO_ACCOUNT_SID", "Account SID", "Twilio Console > Account info.",
                                    "^AC[0-9a-fA-F]{32}$", "A Twilio Account SID is AC followed by 32 hex characters.", "AC…"),
                            secret("app.sms.twilio.auth-token", "TWILIO_AUTH_TOKEN", "Auth token", null, "^[0-9a-fA-F]{32}$", "A Twilio auth token is 32 hex characters."),
                            text("app.sms.twilio.from", "TWILIO_FROM", "Sender (From)", "A Twilio number in international format, or an alphanumeric sender ID.",
                                    "^(\\+[1-9]\\d{6,14}|[A-Za-z0-9]{1,11})$", "Use +<country code><number> or a sender ID of up to 11 letters and digits.", "+1…"),
                            text("app.sms.twilio.messaging-service-sid", "TWILIO_MESSAGING_SERVICE_SID", "Messaging service SID", "Optional; used instead of the sender.",
                                    "^MG[0-9a-fA-F]{32}$", "A messaging service SID is MG followed by 32 hex characters.", "MG…"))),
                    new Group("sns", "AWS SNS", "Leave the access keys empty to use the server's IAM role.", null, List.of(
                            text("app.sms.sns.region", "SMS_SNS_REGION", "Region", null, "^[a-z]{2}(-[a-z]+)+-\\d$", "Use an AWS region code such as ap-south-1.", "ap-south-1"),
                            text("app.sms.sns.access-key-id", "SMS_SNS_ACCESS_KEY_ID", "Access key ID", null, "^(AKIA|ASIA)[A-Z0-9]{16}$", "An AWS access key ID starts with AKIA or ASIA.", "AKIA…"),
                            secret("app.sms.sns.secret-access-key", "SMS_SNS_SECRET_ACCESS_KEY", "Secret access key", null, "^[A-Za-z0-9/+=]{40}$", "An AWS secret access key is 40 characters."),
                            text("app.sms.sns.sender-id", "SMS_SNS_SENDER_ID", "Sender ID", "Up to 11 letters and digits, where the country allows it.", "^[A-Za-z0-9]{1,11}$", "Up to 11 letters and digits.", "ARTSTK"),
                            select("app.sms.sns.sms-type", "SMS_SNS_TYPE", "Message type", "Transactional is right for sign-in codes.", "Transactional", "Promotional"),
                            text("app.sms.sns.entity-id", "SMS_SNS_ENTITY_ID", "DLT entity ID (India)", "Required by Indian carriers.", "^\\d{10,25}$", "A DLT entity ID is 10 to 25 digits.", ""),
                            text("app.sms.sns.template-id", "SMS_SNS_TEMPLATE_ID", "DLT template ID (India)", null, "^\\d{10,25}$", "A DLT template ID is 10 to 25 digits.", ""))),
                    new Group("infobip", "Infobip", null, null, List.of(
                            url("app.sms.infobip.base-url", "SMS_INFOBIP_BASE_URL", "Base URL", "Shown on the Infobip portal home page.",
                                    "^https://[a-z0-9]+\\.api\\.infobip\\.com$", "Use your Infobip base URL, e.g. https://xxxxx.api.infobip.com.", "https://xxxxx.api.infobip.com"),
                            secret("app.sms.infobip.api-key", "SMS_INFOBIP_API_KEY", "API key", null, "^[A-Za-z0-9-]{20,200}$", "An Infobip API key is 20 to 200 letters, digits or dashes."),
                            text("app.sms.infobip.sender", "SMS_INFOBIP_SENDER", "Sender", null, "^(\\+?[1-9]\\d{6,14}|[A-Za-z0-9]{1,11})$", "A number or a sender ID of up to 11 letters and digits.", "ArtistikCity"))))),
            new Tab("google", "Google sign-in", "fa-google", List.of(
                    new Group("oauth", "OAuth 2.0 client", "Google Cloud console > APIs & Services > Credentials > OAuth client ID (type: Web application).", null, List.of(
                            text("social.google.client-id", "GOOGLE_CLIENT_ID", "Client ID", null,
                                    "^[0-9]{6,20}-[A-Za-z0-9_]{10,64}\\.apps\\.googleusercontent\\.com$", "A Google client ID ends with .apps.googleusercontent.com.", "…apps.googleusercontent.com"),
                            secret("social.google.client-secret", "GOOGLE_CLIENT_SECRET", "Client secret", null, "^[A-Za-z0-9_-]{16,128}$", "A Google client secret is 16 to 128 letters, digits, - or _ (usually GOCSPX-…)."),
                            url("social.google.redirect-uri", "GOOGLE_REDIRECT_URI", "Authorised redirect URI",
                                    "Leave empty to use {site address}/social-login/google/callback. Register the same address in Google.",
                                    "^https?://[^\\s/]+(:\\d+)?/social-login/google/callback$", "The redirect URI must end with /social-login/google/callback.", "https://www.artistikcity.com/social-login/google/callback"),
                            text("social.google.scopes", "GOOGLE_SCOPES", "Scopes", "Space separated. openid and email are always requested.",
                                    "^[A-Za-z0-9_.:/ -]{2,500}$", "Use scope names separated by spaces.", "openid email profile"))))));

    private static final Map<String, Field> BY_KEY = new LinkedHashMap<>();

    static {
        for (Tab t : TABS) for (Group g : t.groups()) for (Field f : g.fields()) BY_KEY.put(f.key(), f);
    }

    private ConfigCatalog() {
    }

    public static Field field(String key) {
        return BY_KEY.get(key);
    }

    public static List<Field> fields() {
        return new ArrayList<>(BY_KEY.values());
    }

    public static List<String> secretKeys() {
        return BY_KEY.values().stream().filter(Field::secret).map(Field::key).toList();
    }

    /** null = fine, else what is wrong with this value (empty = "use the server / default value"). */
    public static String check(Field f, String v) {
        if (v == null || v.isEmpty()) return null;
        if (v.length() > f.maxLength()) return "Keep it under " + f.maxLength() + " characters.";
        if (f.type() == Type.SELECT || f.type() == Type.BOOL) {
            return f.options().contains(v) ? null : "Choose one of: " + String.join(", ", f.options()) + ".";
        }
        if (f.pattern() != null && !f.pattern().matcher(v).matches()) return f.patternMessage();
        if ("social.google.scopes".equals(f.key())) {
            for (String s : v.trim().split("\\s+")) if (!s.matches("[A-Za-z0-9_.:/-]{2,200}")) return "\"" + s + "\" isn't a scope name.";
        }
        return null;
    }
}
