package com.artistikcity.commission;

import com.artistikcity.support.Json;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Payment-broker calls for the commission checkout:
 * <ul>
 *   <li>Razorpay Orders API + Checkout signature and webhook signature verification (HMAC-SHA256).
 *       Razorpay Checkout handles cards, UPI and NetBanking, so "Credit card" and "UPI" both use it.</li>
 *   <li>PayPal Orders v2 (create, capture) and webhook signature verification through PayPal's API.</li>
 * </ul>
 * Keys come from the same settings the course checkout uses (razorpay.*, paypal.*) plus
 * razorpay.webhook-secret and paypal.webhook-id for the webhooks.
 */
@Service
public class CommissionPayments {

    private final Environment env;
    private final Json json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public CommissionPayments(Environment env, Json json) {
        this.env = env;
        this.json = json;
    }

    private String prop(String key) {
        return env.getProperty(key, "").trim();
    }

    public boolean testMode() {
        return Boolean.parseBoolean(env.getProperty("app.payment-test-mode", "false"));
    }

    // ------------------------------------------------------------------ Razorpay

    public boolean razorpayConfigured() {
        return !prop("razorpay.key").isEmpty() && !prop("razorpay.secret").isEmpty();
    }

    public String razorpayKey() {
        return prop("razorpay.key");
    }

    /** POST /v1/orders - amount in paise. Returns the Razorpay order id. */
    public String razorpayCreateOrder(String receipt, BigDecimal amountInr) throws IOException, InterruptedException {
        long paise = amountInr.movePointRight(2).longValueExact();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", paise);
        body.put("currency", "INR");
        body.put("receipt", receipt);
        body.put("notes", Map.of("commission_order_id", receipt));
        String auth = Base64.getEncoder().encodeToString((prop("razorpay.key") + ":" + prop("razorpay.secret")).getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1/orders"))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Basic " + auth).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encode(body))).build(), HttpResponse.BodyHandlers.ofString());
        return String.valueOf(checked(r).get("id"));
    }

    /** Checkout handler signature: HMAC_SHA256(order_id + "|" + payment_id, key_secret). */
    public boolean razorpayCheckoutSignatureValid(String razorpayOrderId, String paymentId, String signature) {
        return hmacEquals(prop("razorpay.secret"), razorpayOrderId + "|" + paymentId, signature);
    }

    /** Webhook signature: HMAC_SHA256(raw body, webhook secret), sent in X-Razorpay-Signature. */
    public boolean razorpayWebhookValid(String rawBody, String signature) {
        String secret = prop("razorpay.webhook-secret");
        return !secret.isEmpty() && hmacEquals(secret, rawBody, signature);
    }

    private static boolean hmacEquals(String secret, String data, String signature) {
        if (secret == null || secret.isEmpty() || signature == null || signature.isEmpty()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------- PayPal

    public boolean paypalConfigured() {
        return !prop("paypal.client-id").isEmpty() && !prop("paypal.secret").isEmpty();
    }

    private String paypalBase() {
        return "live".equalsIgnoreCase(prop("paypal.mode")) ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";
    }

    private String paypalToken() throws IOException, InterruptedException {
        String creds = Base64.getEncoder().encodeToString((prop("paypal.client-id") + ":" + prop("paypal.secret")).getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(paypalBase() + "/v1/oauth2/token"))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Basic " + creds)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build(), HttpResponse.BodyHandlers.ofString());
        return String.valueOf(checked(r).get("access_token"));
    }

    /** Creates a PayPal order (intent CAPTURE) and returns [paypalOrderId, approveUrl]. */
    public String[] paypalCreateOrder(String orderId, BigDecimal amountUsd, String description, String returnUrl, String cancelUrl)
            throws IOException, InterruptedException {
        Map<String, Object> unit = new LinkedHashMap<>();
        unit.put("reference_id", orderId);
        unit.put("custom_id", orderId);
        unit.put("invoice_id", orderId);
        unit.put("description", description);
        unit.put("amount", Map.of("currency_code", "USD", "value", amountUsd.toPlainString()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("intent", "CAPTURE");
        body.put("purchase_units", List.of(unit));
        body.put("application_context", Map.of("return_url", returnUrl, "cancel_url", cancelUrl,
                "brand_name", "ArtistikCity", "user_action", "PAY_NOW", "shipping_preference", "NO_SHIPPING"));
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(paypalBase() + "/v2/checkout/orders"))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + paypalToken()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encode(body))).build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> res = checked(r);
        String approve = null;
        if (res.get("links") instanceof List<?> links) {
            for (Object o : links) {
                if (o instanceof Map<?, ?> l && ("approve".equals(l.get("rel")) || "payer-action".equals(l.get("rel")))) {
                    approve = String.valueOf(l.get("href"));
                }
            }
        }
        return new String[]{String.valueOf(res.get("id")), approve};
    }

    /** Captures an approved PayPal order. Returns [status, captureId, amountValue, currency]. */
    @SuppressWarnings("unchecked")
    public String[] paypalCapture(String paypalOrderId) throws IOException, InterruptedException {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(paypalBase() + "/v2/checkout/orders/" + paypalOrderId + "/capture"))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + paypalToken()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> res = checked(r);
        String captureId = null, value = null, currency = null;
        try {
            Map<String, Object> pu = (Map<String, Object>) ((List<Object>) res.get("purchase_units")).get(0);
            Map<String, Object> cap = (Map<String, Object>) ((List<Object>) ((Map<String, Object>) pu.get("payments")).get("captures")).get(0);
            captureId = String.valueOf(cap.get("id"));
            Map<String, Object> amount = (Map<String, Object>) cap.get("amount");
            value = String.valueOf(amount.get("value"));
            currency = String.valueOf(amount.get("currency_code"));
        } catch (RuntimeException ignored) {
            // unusual payload - status alone decides
        }
        return new String[]{String.valueOf(res.get("status")), captureId, value, currency};
    }

    /** Asks PayPal to verify a webhook's transmission signature (POST /v1/notifications/verify-webhook-signature). */
    public boolean paypalWebhookValid(Map<String, String> headers, String rawBody) throws IOException, InterruptedException {
        String webhookId = prop("paypal.webhook-id");
        if (webhookId.isEmpty() || !paypalConfigured()) {
            return false;
        }
        Object event = json.decode(rawBody);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("auth_algo", headers.get("paypal-auth-algo"));
        body.put("cert_url", headers.get("paypal-cert-url"));
        body.put("transmission_id", headers.get("paypal-transmission-id"));
        body.put("transmission_sig", headers.get("paypal-transmission-sig"));
        body.put("transmission_time", headers.get("paypal-transmission-time"));
        body.put("webhook_id", webhookId);
        body.put("webhook_event", event);
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(paypalBase() + "/v1/notifications/verify-webhook-signature"))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + paypalToken()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encode(body))).build(), HttpResponse.BodyHandlers.ofString());
        return "SUCCESS".equals(String.valueOf(checked(r).get("verification_status")));
    }

    private Map<String, Object> checked(HttpResponse<String> r) throws IOException {
        Map<String, Object> m = json.decodeMap(r.body());
        if (r.statusCode() >= 400 || m == null) {
            throw new IOException("Payment provider error " + r.statusCode() + ": " + r.body());
        }
        return m;
    }
}
