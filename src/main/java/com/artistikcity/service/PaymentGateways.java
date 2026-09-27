package com.artistikcity.service;

import com.artistikcity.support.Json;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * REST clients for the two payment providers used by the site, replacing the razorpay/razorpay and
 * paypal/rest-api-sdk-php packages:
 * <ul>
 *   <li>Razorpay (INR): fetch + capture a payment created by Razorpay Checkout.</li>
 *   <li>PayPal (USD): classic v1 Payments API (create -> approve -> execute), as the PHP SDK used.</li>
 * </ul>
 */
@Service
public class PaymentGateways {

    private final Environment env;
    private final Json json;
    private final HttpClient http = HttpClient.newHttpClient();

    public PaymentGateways(Environment env, Json json) {
        this.env = env;
        this.json = json;
    }

    // ------------------------------------------------------------- Razorpay

    public String razorpayKey() {
        return env.getProperty("razorpay.key", "");
    }

    public boolean razorpayConfigured() {
        return !razorpayKey().isBlank() && !env.getProperty("razorpay.secret", "").isBlank();
    }

    /** Test mode (dev profile): lets you complete a simulated payment when no gateway keys are set. */
    public boolean testMode() {
        return Boolean.parseBoolean(env.getProperty("app.payment-test-mode", "false"));
    }

    private String razorpayAuth() {
        String creds = razorpayKey() + ":" + env.getProperty("razorpay.secret", "");
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
    }

    /** GET /v1/payments/{id}. */
    public Map<String, Object> razorpayFetch(String paymentId) throws IOException, InterruptedException {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1/payments/" + paymentId))
                .header("Authorization", razorpayAuth()).GET().build(), HttpResponse.BodyHandlers.ofString());
        return checked(r);
    }

    /** POST /v1/payments/{id}/capture. */
    public Map<String, Object> razorpayCapture(String paymentId, Object amount, Object currency) throws IOException, InterruptedException {
        String body = json.encode(Map.of("amount", amount, "currency", currency == null ? "INR" : currency));
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1/payments/" + paymentId + "/capture"))
                .header("Authorization", razorpayAuth()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        return checked(r);
    }

    // --------------------------------------------------------------- PayPal

    public boolean paypalConfigured() {
        return !env.getProperty("paypal.client-id", "").isBlank() && !env.getProperty("paypal.secret", "").isBlank();
    }

    private String paypalBase() {
        return "live".equalsIgnoreCase(env.getProperty("paypal.mode", "sandbox"))
                ? "https://api-m.paypal.com" : "https://api-m.sandbox.paypal.com";
    }

    private String paypalToken() throws IOException, InterruptedException {
        String creds = env.getProperty("paypal.client-id", "") + ":" + env.getProperty("paypal.secret", "");
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(paypalBase() + "/v1/oauth2/token"))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build(), HttpResponse.BodyHandlers.ofString());
        return String.valueOf(checked(r).get("access_token"));
    }

    /**
     * Creates a PayPal payment and returns [paymentId, approvalUrl].
     */
    public String[] paypalCreate(String itemName, String amountUsd, String returnUrl, String cancelUrl) throws IOException, InterruptedException {
        Map<String, Object> payment = Map.of(
                "intent", "sale",
                "payer", Map.of("payment_method", "paypal"),
                "redirect_urls", Map.of("return_url", returnUrl, "cancel_url", cancelUrl),
                "transactions", List.of(Map.of(
                        "amount", Map.of("currency", "USD", "total", amountUsd),
                        "item_list", Map.of("items", List.of(Map.of("name", itemName, "currency", "USD", "quantity", "1", "price", amountUsd))),
                        "description", "Purchase of course: " + itemName)));
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(paypalBase() + "/v1/payments/payment"))
                .header("Authorization", "Bearer " + paypalToken()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encode(payment))).build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> res = checked(r);
        String approval = null;
        Object links = res.get("links");
        if (links instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> link && "approval_url".equals(link.get("rel"))) {
                    approval = String.valueOf(link.get("href"));
                }
            }
        }
        return new String[]{String.valueOf(res.get("id")), approval};
    }

    /** Executes an approved payment; returns the payment state ("approved" on success). */
    public String paypalExecute(String paymentId, String payerId) throws IOException, InterruptedException {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(paypalBase() + "/v1/payments/payment/" + paymentId + "/execute"))
                .header("Authorization", "Bearer " + paypalToken()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encode(Map.of("payer_id", payerId)))).build(), HttpResponse.BodyHandlers.ofString());
        return String.valueOf(checked(r).get("state"));
    }

    private Map<String, Object> checked(HttpResponse<String> r) throws IOException {
        Map<String, Object> m = json.decodeMap(r.body());
        if (r.statusCode() >= 400 || m == null) {
            throw new IOException("Payment gateway error " + r.statusCode() + ": " + r.body());
        }
        return m;
    }
}
