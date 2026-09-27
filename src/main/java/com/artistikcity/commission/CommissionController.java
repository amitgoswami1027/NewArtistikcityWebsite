package com.artistikcity.commission;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Csrf;
import com.artistikcity.http.Redirects;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import com.artistikcity.support.UploadedFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Artist Commission Portal.
 * <ul>
 *   <li>Wizard pages (Inertia/React): /commission/step-1 … step-4, /commission/preview, /commission/checkout,
 *       /commission/thank-you.</li>
 *   <li>POST /api/commissions/upload - reference photo upload.</li>
 *   <li>POST /api/commissions/initialize-checkout - validates the wizard state, prices it on the server,
 *       stores a PAYMENT_PENDING order and prepares the payment broker.</li>
 *   <li>POST /api/commissions/confirm - browser-side confirmation (Razorpay Checkout signature / test mode).</li>
 *   <li>GET  /commission/paypal/return - PayPal approval return, captures the order.</li>
 *   <li>POST /api/commissions/finalize-webhook - server-to-server webhooks from Razorpay and PayPal.</li>
 * </ul>
 */
@Controller
public class CommissionController {

    private static final Logger log = LoggerFactory.getLogger(CommissionController.class);
    private static final String SESSION_ORDERS = "commission.orders";
    private static final long MAX_PHOTO_BYTES = 15L * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Inertia inertia;
    private final Json json;
    private final Auth auth;
    private final Storage storage;
    private final Redirects redirect;
    private final CommissionPricing pricing;
    private final CommissionRepository orders;
    private final CommissionPayments payments;

    public CommissionController(Inertia inertia, Json json, Auth auth, Storage storage, Redirects redirect,
                                CommissionPricing pricing, CommissionRepository orders, CommissionPayments payments) {
        this.inertia = inertia;
        this.json = json;
        this.auth = auth;
        this.storage = storage;
        this.redirect = redirect;
        this.pricing = pricing;
        this.orders = orders;
        this.payments = payments;
    }

    // ================================================================ pages

    @GetMapping({"/commission", "/commission/"})
    public ResponseEntity<String> start(HttpServletRequest request) {
        return redirect.to(request, "/commission/step-1");
    }

    @GetMapping("/commission/step-1")
    public ResponseEntity<String> step1(HttpServletRequest request) {
        return page(request, "Commission/Step1Theme");
    }

    @GetMapping("/commission/step-2")
    public ResponseEntity<String> step2(HttpServletRequest request) {
        return page(request, "Commission/Step2Fulfillment");
    }

    @GetMapping("/commission/step-3")
    public ResponseEntity<String> step3(HttpServletRequest request) {
        return page(request, "Commission/Step3Upload");
    }

    @GetMapping("/commission/step-4")
    public ResponseEntity<String> step4(HttpServletRequest request) {
        return page(request, "Commission/Step4Frame");
    }

    @GetMapping("/commission/preview")
    public ResponseEntity<String> preview(HttpServletRequest request) {
        return page(request, "Commission/Preview");
    }

    @GetMapping("/commission/checkout")
    public ResponseEntity<String> checkout(HttpServletRequest request) {
        return page(request, "Commission/Checkout");
    }

    @GetMapping("/commission/thank-you")
    public ResponseEntity<String> thankYou(HttpServletRequest request, @RequestParam(value = "order", required = false) String orderId) {
        Map<String, Object> order = orderId != null && ownsOrder(request, orderId) ? orders.find(orderId) : null;
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("order", order == null ? null : publicView(order));
        return inertia.render(request, "Commission/ThankYou", props);
    }

    private ResponseEntity<String> page(HttpServletRequest request, String component) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("pricing", pricing.table());
        props.put("gateways", Map.of(
                "razorpay", payments.razorpayConfigured(),
                "paypal", payments.paypalConfigured(),
                "testMode", payments.testMode()));
        Row user = auth.user(request);
        props.put("customer", user == null ? null : Map.of("name", user.str("name", ""), "email", user.str("email", ""),
                "phone", user.str("phone", "")));
        return inertia.render(request, component, props);
    }

    // ================================================================ API

    /** Multipart field "photo" (.jpg/.jpeg/.png, max 15 MB). Stored under a random name. */
    @PostMapping("/api/commissions/upload")
    public ResponseEntity<String> upload(HttpServletRequest request) {
        if (!Csrf.matches(request)) {
            return error(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
        }
        UploadedFile file = Input.of(request, json).file("photo");
        if (file == null || file.getContent() == null || file.getContent().length == 0) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "Choose a photo to upload.");
        }
        byte[] bytes = file.getContent();
        if (bytes.length > MAX_PHOTO_BYTES) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "That photo is larger than 15 MB. Please choose a smaller file.");
        }
        String ext = sniffImage(bytes);
        if (ext == null) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "Please upload a JPG or PNG image.");
        }
        String name = UUID.randomUUID() + "." + ext;
        storage.put("uploads/commissions/" + name, bytes);
        return ok(Map.of("url", "/storage/uploads/commissions/" + name));
    }

    @PostMapping("/api/commissions/initialize-checkout")
    public ResponseEntity<String> initializeCheckout(HttpServletRequest request) throws IOException {
        if (!Csrf.matches(request)) {
            return error(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
        }
        Map<String, Object> body = json.decodeMap(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        if (body == null) {
            return error(HttpStatus.BAD_REQUEST, "Invalid request.");
        }
        CommissionRequest req;
        try {
            req = CommissionRequest.from(body);
        } catch (CommissionRequest.InvalidCommissionException e) {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("message", e.errors().get(0));
            res.put("errors", e.errors());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON).body(json.encode(res));
        }
        String relativePhoto = req.photoUrl().substring("/storage/".length());
        if (!storage.exists(relativePhoto)) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "Your reference photo was not found. Please upload it again.");
        }

        boolean razorpay = !"PAYPAL".equals(req.paymentMethod());
        boolean live = razorpay ? payments.razorpayConfigured() : payments.paypalConfigured();
        if (!live && !payments.testMode()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, (razorpay ? "Card and UPI" : "PayPal")
                    + " payments are not available right now. Please choose another method or contact us.");
        }

        CommissionPricing.Quote quote = pricing.quote(req);
        String currency = razorpay ? "INR" : "USD";
        BigDecimal total = razorpay ? quote.totalInr() : quote.totalUsd();
        String orderId = newOrderId();
        Row user = auth.user(request);
        orders.createPending(orderId, "ITEM-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase(),
                user == null ? null : user.lng("id"), req, currency, total);
        rememberOrder(request, orderId);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("orderId", orderId);
        res.put("currency", currency);
        res.put("total", total);
        res.put("quote", quote.toMap());
        try {
            if (!live) {
                res.put("provider", "test");
            } else if (razorpay) {
                String rzpOrder = payments.razorpayCreateOrder(orderId, total);
                orders.setProviderRef(orderId, rzpOrder);
                res.put("provider", "razorpay");
                res.put("key", payments.razorpayKey());
                res.put("razorpayOrderId", rzpOrder);
                res.put("amount", total.movePointRight(2).longValueExact());
                res.put("method", "CREDIT_CARD".equals(req.paymentMethod()) ? "card" : "upi");
                res.put("prefill", Map.of("name", req.name(), "email", req.email(), "contact", req.phone()));
            } else {
                String base = Inertia.baseUrl(request);
                String[] pp = payments.paypalCreateOrder(orderId, total, "ArtistikCity custom commission " + orderId,
                        base + "/commission/paypal/return?order=" + orderId, base + "/commission/checkout?cancelled=1");
                orders.setProviderRef(orderId, pp[0]);
                res.put("provider", "paypal");
                res.put("approveUrl", pp[1]);
            }
        } catch (Exception e) {
            log.error("Could not start payment for commission {}", orderId, e);
            return error(HttpStatus.BAD_GATEWAY, "We couldn't reach the payment provider. Please try again in a moment.");
        }
        return ok(res);
    }

    /** Browser confirmation after Razorpay Checkout succeeds (signature verified) or a simulated test payment. */
    @PostMapping("/api/commissions/confirm")
    public ResponseEntity<String> confirm(HttpServletRequest request) throws IOException {
        if (!Csrf.matches(request)) {
            return error(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
        }
        Map<String, Object> body = json.decodeMap(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        String orderId = body == null ? null : String.valueOf(body.get("orderId"));
        if (orderId == null || !ownsOrder(request, orderId)) {
            return error(HttpStatus.NOT_FOUND, "Order not found.");
        }
        Map<String, Object> order = orders.find(orderId);
        if (order == null) {
            return error(HttpStatus.NOT_FOUND, "Order not found.");
        }
        String provider = String.valueOf(body.get("provider"));
        if ("test".equals(provider)) {
            boolean usingRazorpay = !"PAYPAL".equals(order.get("payment_method"));
            boolean live = usingRazorpay ? payments.razorpayConfigured() : payments.paypalConfigured();
            if (!payments.testMode() || live) {
                return error(HttpStatus.FORBIDDEN, "Test payments are disabled.");
            }
            orders.markQueued(orderId, "TEST-" + System.currentTimeMillis());
            return ok(Map.of("status", "QUEUED", "redirect", "/commission/thank-you?order=" + orderId));
        }
        if ("razorpay".equals(provider)) {
            String rzpOrder = String.valueOf(body.get("razorpay_order_id"));
            String paymentId = String.valueOf(body.get("razorpay_payment_id"));
            String signature = String.valueOf(body.get("razorpay_signature"));
            if (!rzpOrder.equals(order.get("payment_provider_ref"))
                    || !payments.razorpayCheckoutSignatureValid(rzpOrder, paymentId, signature)) {
                log.warn("Rejected Razorpay confirmation for commission {}", orderId);
                return error(HttpStatus.BAD_REQUEST, "We couldn't verify this payment. If you were charged, contact us with order " + orderId + ".");
            }
            orders.markQueued(orderId, paymentId);
            return ok(Map.of("status", "QUEUED", "redirect", "/commission/thank-you?order=" + orderId));
        }
        return error(HttpStatus.BAD_REQUEST, "Unknown payment provider.");
    }

    /** PayPal sends the buyer back here after approval; capture and queue. */
    @GetMapping("/commission/paypal/return")
    public ResponseEntity<String> paypalReturn(HttpServletRequest request, @RequestParam("order") String orderId,
                                               @RequestParam(value = "token", required = false) String paypalOrderId) {
        Map<String, Object> order = orders.find(orderId);
        if (order == null || paypalOrderId == null || !paypalOrderId.equals(order.get("payment_provider_ref"))) {
            return redirect.to(request, "/commission/checkout?payment=failed");
        }
        try {
            String[] cap = payments.paypalCapture(paypalOrderId);
            if ("COMPLETED".equals(cap[0]) && amountMatches(order, cap[2], cap[3])) {
                orders.markQueued(orderId, cap[1] == null ? paypalOrderId : cap[1]);
                rememberOrder(request, orderId);
                return redirect.to(request, "/commission/thank-you?order=" + orderId);
            }
            log.warn("PayPal capture for {} returned {} {} {}", orderId, cap[0], cap[2], cap[3]);
        } catch (Exception e) {
            log.error("PayPal capture failed for commission {}", orderId, e);
        }
        return redirect.to(request, "/commission/checkout?payment=failed");
    }

    /**
     * Server-to-server webhooks. Razorpay: header X-Razorpay-Signature, events payment.captured / order.paid.
     * PayPal: paypal-transmission-* headers, events PAYMENT.CAPTURE.COMPLETED / CHECKOUT.ORDER.APPROVED.
     * Always verified before any state change; the PENDING -> QUEUED transition is an atomic compare-and-set.
     */
    @PostMapping("/api/commissions/finalize-webhook")
    @SuppressWarnings("unchecked")
    public ResponseEntity<String> finalizeWebhook(HttpServletRequest request) throws IOException {
        String raw = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> event = json.decodeMap(raw);
        if (event == null) {
            return error(HttpStatus.BAD_REQUEST, "Invalid payload.");
        }
        try {
            String rzpSignature = request.getHeader("X-Razorpay-Signature");
            if (rzpSignature != null) {
                if (!payments.razorpayWebhookValid(raw, rzpSignature)) {
                    log.warn("Rejected Razorpay webhook with an invalid signature");
                    return error(HttpStatus.UNAUTHORIZED, "Invalid signature.");
                }
                String type = String.valueOf(event.get("event"));
                if (!"payment.captured".equals(type) && !"order.paid".equals(type)) {
                    return ok(Map.of("ignored", type));
                }
                Map<String, Object> payment = (Map<String, Object>) path(event, "payload", "payment", "entity");
                if (payment == null) {
                    return ok(Map.of("ignored", "no payment entity"));
                }
                Map<String, Object> order = orders.findByProviderRef(String.valueOf(payment.get("order_id")));
                if (order == null) {
                    return ok(Map.of("ignored", "unknown order"));
                }
                long expectedPaise = ((BigDecimal) order.get("total_price")).movePointRight(2).longValue();
                long paidPaise = payment.get("amount") instanceof Number n ? n.longValue() : -1;
                if (expectedPaise != paidPaise || !"INR".equalsIgnoreCase(String.valueOf(payment.get("currency")))) {
                    log.warn("Razorpay amount mismatch for {}: expected {} got {}", order.get("order_id"), expectedPaise, paidPaise);
                    return error(HttpStatus.UNPROCESSABLE_ENTITY, "Amount mismatch.");
                }
                boolean changed = orders.markQueued(String.valueOf(order.get("order_id")), String.valueOf(payment.get("id")));
                return ok(Map.of("orderId", order.get("order_id"), "queued", changed));
            }

            if (request.getHeader("paypal-transmission-id") != null) {
                Map<String, String> headers = new LinkedHashMap<>();
                for (String h : List.of("paypal-auth-algo", "paypal-cert-url", "paypal-transmission-id", "paypal-transmission-sig", "paypal-transmission-time")) {
                    headers.put(h, request.getHeader(h));
                }
                if (!payments.paypalWebhookValid(headers, raw)) {
                    log.warn("Rejected PayPal webhook that failed verification");
                    return error(HttpStatus.UNAUTHORIZED, "Invalid signature.");
                }
                String type = String.valueOf(event.get("event_type"));
                Map<String, Object> resource = (Map<String, Object>) event.get("resource");
                if (resource == null) {
                    return ok(Map.of("ignored", "no resource"));
                }
                if ("CHECKOUT.ORDER.APPROVED".equals(type)) {
                    Map<String, Object> order = orders.findByProviderRef(String.valueOf(resource.get("id")));
                    if (order == null) return ok(Map.of("ignored", "unknown order"));
                    String[] cap = payments.paypalCapture(String.valueOf(resource.get("id")));
                    if ("COMPLETED".equals(cap[0]) && amountMatches(order, cap[2], cap[3])) {
                        boolean changed = orders.markQueued(String.valueOf(order.get("order_id")), cap[1]);
                        return ok(Map.of("orderId", order.get("order_id"), "queued", changed));
                    }
                    return ok(Map.of("orderId", order.get("order_id"), "status", cap[0]));
                }
                if ("PAYMENT.CAPTURE.COMPLETED".equals(type)) {
                    Object relatedOrder = path(resource, "supplementary_data", "related_ids", "order_id");
                    Map<String, Object> order = relatedOrder != null ? orders.findByProviderRef(String.valueOf(relatedOrder)) : null;
                    if (order == null && resource.get("custom_id") != null) order = orders.find(String.valueOf(resource.get("custom_id")));
                    if (order == null) return ok(Map.of("ignored", "unknown order"));
                    Map<String, Object> amount = (Map<String, Object>) resource.get("amount");
                    if (amount == null || !amountMatches(order, String.valueOf(amount.get("value")), String.valueOf(amount.get("currency_code")))) {
                        return error(HttpStatus.UNPROCESSABLE_ENTITY, "Amount mismatch.");
                    }
                    boolean changed = orders.markQueued(String.valueOf(order.get("order_id")), String.valueOf(resource.get("id")));
                    return ok(Map.of("orderId", order.get("order_id"), "queued", changed));
                }
                return ok(Map.of("ignored", type));
            }
        } catch (Exception e) {
            log.error("Commission webhook failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Webhook processing failed.");
        }
        return error(HttpStatus.BAD_REQUEST, "Unrecognised webhook source.");
    }

    // ============================================================= helpers

    private static boolean amountMatches(Map<String, Object> order, String value, String currency) {
        try {
            return value != null && currency != null && currency.equalsIgnoreCase(String.valueOf(order.get("currency")))
                    && new BigDecimal(value).compareTo((BigDecimal) order.get("total_price")) == 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object path(Map<String, Object> m, String... keys) {
        Object cur = m;
        for (String k : keys) {
            if (!(cur instanceof Map)) return null;
            cur = ((Map<String, Object>) cur).get(k);
        }
        return cur;
    }

    private static String sniffImage(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        return null;
    }

    private static String newOrderId() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("AC-").append(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)).append('-');
        for (int i = 0; i < 6; i++) sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void rememberOrder(HttpServletRequest request, String orderId) {
        HttpSession s = request.getSession(true);
        Object o = s.getAttribute(SESSION_ORDERS);
        List<String> list = o instanceof List ? new ArrayList<>((List<String>) o) : new ArrayList<>();
        list.add(orderId);
        s.setAttribute(SESSION_ORDERS, Collections.unmodifiableList(list));
    }

    private static boolean ownsOrder(HttpServletRequest request, String orderId) {
        HttpSession s = request.getSession(false);
        return s != null && s.getAttribute(SESSION_ORDERS) instanceof List<?> l && l.contains(orderId);
    }

    private static Map<String, Object> publicView(Map<String, Object> o) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : List.of("order_id", "customer_name", "customer_email", "fulfillment_type", "order_status", "currency",
                "total_price", "payment_method", "artwork_theme", "width_inches", "height_inches", "frame_material", "has_matting",
                "uploaded_photo_url", "shipping_city", "shipping_country", "created_at")) {
            Object v = o.get(k);
            m.put(k, v == null ? null : (v instanceof Number || v instanceof Boolean ? v : String.valueOf(v)));
        }
        return m;
    }

    private ResponseEntity<String> ok(Object body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of("message", message)));
    }
}
