package com.artistikcity.marketplace;

import com.artistikcity.commission.CommissionPayments;
import com.artistikcity.http.Auth;
import com.artistikcity.http.Csrf;
import com.artistikcity.http.Redirects;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.Mailer;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The public 1-of-1 Original Art Marketplace (replaces the legacy /student-feedback page).
 * <ul>
 *   <li>Pages: /marketplace, /marketplace/{slug}, /marketplace/{slug}/checkout, /marketplace/orders/{orderId}</li>
 *   <li>Public API: GET /api/v1/marketplace/paintings, GET /api/v1/marketplace/paintings/{id}</li>
 *   <li>Collector API (CSRF): POST /api/v1/marketplace/paintings/{id}/reserve, DELETE /api/v1/marketplace/reservations/{paintingId},
 *       GET /api/v1/marketplace/reservations/mine, POST /api/v1/marketplace/checkout, POST /api/v1/marketplace/confirm</li>
 *   <li>Gateways: GET /marketplace/paypal/return, POST /api/v1/marketplace/webhook (Razorpay + PayPal, signature-verified)</li>
 * </ul>
 * Listing changes (POST/PUT/DELETE) are staff/artist only and live in {@link MarketplaceAdminController}
 * and {@link StudentListingController}.
 */
@Controller
public class MarketplaceController {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceController.class);
    private static final String SESSION_ORDERS = "marketplace.orders";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    private final Inertia inertia;
    private final Json json;
    private final Auth auth;
    private final Redirects redirect;
    private final Environment env;
    private final Mailer mailer;
    private final MarketplaceRepository repo;
    private final MarketplaceService market;
    private final CommissionPayments payments;

    public MarketplaceController(Inertia inertia, Json json, Auth auth, Redirects redirect, Environment env, Mailer mailer,
                                 MarketplaceRepository repo, MarketplaceService market, CommissionPayments payments) {
        this.inertia = inertia;
        this.json = json;
        this.auth = auth;
        this.redirect = redirect;
        this.env = env;
        this.mailer = mailer;
        this.repo = repo;
        this.market = market;
        this.payments = payments;
    }

    // ================================================================ legacy routes

    /** @deprecated the placeholder testimonials page moved to the marketplace; kept as a permanent redirect. */
    @Deprecated
    @GetMapping("/student-feedback")
    public ResponseEntity<String> legacyStudentFeedback() {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).location(URI.create("/marketplace")).build();
    }

    /** @deprecated the student shop is now the "Student originals" filter of the marketplace. */
    @Deprecated
    @GetMapping("/student-shop")
    public ResponseEntity<String> legacyStudentShop() {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).location(URI.create("/marketplace?source=student")).build();
    }

    // ================================================================ pages

    @GetMapping({"/marketplace", "/marketplace/"})
    public ResponseEntity<String> index(HttpServletRequest request) {
        List<Map<String, Object>> cards = repo.publicListings().stream().map(MarketplaceViews::card).toList();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("paintings", cards);
        props.put("mediums", cards.stream().map(c -> String.valueOf(c.get("medium"))).distinct().sorted().toList());
        props.put("holds", holdsView(request));
        props.put("serverNow", System.currentTimeMillis());
        props.put("initialSource", request.getParameter("source"));
        props.put("stats", Map.of(
                "available", cards.stream().filter(c -> "AVAILABLE".equals(c.get("stock_status"))).count(),
                "artists", cards.stream().map(c -> c.get("artist_name")).distinct().count(),
                "sold", cards.stream().filter(c -> "SOLD".equals(c.get("stock_status"))).count()));
        return inertia.render(request, "Marketplace/Index", props);
    }

    @GetMapping("/marketplace/{slug}")
    public ResponseEntity<String> show(HttpServletRequest request, @PathVariable("slug") String slug) {
        Map<String, Object> p = repo.bySlug(slug);
        if (p == null || !visible(request, p)) {
            return inertia.render(request, "Marketplace/NotFound", Map.of("slug", slug));
        }
        long id = ((Number) p.get("id")).longValue();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("painting", MarketplaceViews.detail(p, repo.images(id)));
        props.put("preview", !MarketplaceRepository.PUBLIC_STATUSES.contains(String.valueOf(p.get("stock_status"))) ? String.valueOf(p.get("stock_status")) : null);
        props.put("hold", holdState(request, id));
        props.put("holds", holdsView(request));
        props.put("related", repo.related(id, String.valueOf(p.get("artist_name")), 4).stream().map(MarketplaceViews::card).toList());
        props.put("serverNow", System.currentTimeMillis());
        props.put("notice", request.getParameter("hold"));
        return inertia.render(request, "Marketplace/Show", props);
    }

    @GetMapping("/marketplace/{slug}/checkout")
    public ResponseEntity<String> checkout(HttpServletRequest request, @PathVariable("slug") String slug) {
        Map<String, Object> p = repo.bySlug(slug);
        if (p == null) return redirect.to(request, "/marketplace");
        long id = ((Number) p.get("id")).longValue();
        MarketplaceService.Hold hold = market.activeHold(id, Holders.all(request, auth));
        if (hold == null) {
            return redirect.to(request, "/marketplace/" + slug + "?hold=expired");
        }
        Row user = auth.user(request);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("painting", MarketplaceViews.detail(p, repo.images(id)));
        props.put("expiresAt", hold.expiresAtMillis());
        props.put("serverNow", System.currentTimeMillis());
        props.put("usdPrice", toUsd((BigDecimal) p.get("final_price")));
        props.put("inrPerUsd", inrPerUsd());
        props.put("gateways", Map.of("razorpay", payments.razorpayConfigured(), "paypal", payments.paypalConfigured(), "testMode", payments.testMode()));
        props.put("customer", user == null ? null : Map.of("name", user.str("name", ""), "email", user.str("email", ""), "phone", user.str("phone", "")));
        props.put("payment", request.getParameter("payment"));
        return inertia.render(request, "Marketplace/Checkout", props);
    }

    @GetMapping("/marketplace/orders/{orderId}")
    public ResponseEntity<String> confirmation(HttpServletRequest request, @PathVariable("orderId") String orderId) {
        Map<String, Object> o = market.order(orderId);
        if (o == null || !ownsOrder(request, o)) {
            return redirect.to(request, "/marketplace");
        }
        return inertia.render(request, "Marketplace/Confirmation", Map.of("order", orderView(o)));
    }

    // ================================================================ public API

    @GetMapping("/api/v1/marketplace/paintings")
    public ResponseEntity<String> apiList() {
        return ok(repo.publicListings().stream().map(MarketplaceViews::card).toList());
    }

    @GetMapping("/api/v1/marketplace/paintings/{id}")
    public ResponseEntity<String> apiShow(@PathVariable("id") long id) {
        Map<String, Object> p = repo.find(id);
        if (p == null || !MarketplaceRepository.PUBLIC_STATUSES.contains(String.valueOf(p.get("stock_status")))) {
            return error(HttpStatus.NOT_FOUND, "Painting not found.");
        }
        return ok(MarketplaceViews.detail(p, repo.images(id)));
    }

    // ================================================================ collector API

    @PostMapping("/api/v1/marketplace/paintings/{id}/reserve")
    public ResponseEntity<String> reserve(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        if (!Csrf.matches(request)) return error(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
        Map<String, Object> body = body(request);
        try {
            MarketplaceService.Hold hold = market.reserve(id, Holders.primary(request, auth), Holders.all(request, auth), auth.userId(request),
                    Boolean.TRUE.equals(body.get("switch")));
            repo.syncStudentListing(id);
            Map<String, Object> p = repo.find(id);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("expiresAt", hold.expiresAtMillis());
            res.put("serverNow", System.currentTimeMillis());
            res.put("created", hold.created());
            res.put("checkoutUrl", "/marketplace/" + p.get("slug") + "/checkout");
            return ok(res);
        } catch (MarketplaceException e) {
            return fail(e);
        }
    }

    @DeleteMapping("/api/v1/marketplace/reservations/{paintingId}")
    public ResponseEntity<String> release(HttpServletRequest request, @PathVariable("paintingId") long paintingId) {
        if (!Csrf.matches(request)) return error(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
        boolean released = market.release(paintingId, Holders.all(request, auth));
        return ok(Map.of("released", released));
    }

    @GetMapping("/api/v1/marketplace/reservations/mine")
    public ResponseEntity<String> mine(HttpServletRequest request) {
        return ok(Map.of("holds", holdsView(request), "serverNow", System.currentTimeMillis()));
    }

    /**
     * JSON: { paintingId, gateway: RAZORPAY|PAYPAL, name, email, phone, line1, line2, city, state, postal, country }.
     * Prices come from the database, never from the browser.
     */
    @PostMapping("/api/v1/marketplace/checkout")
    public ResponseEntity<String> startCheckout(HttpServletRequest request) throws IOException {
        if (!Csrf.matches(request)) return error(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
        Map<String, Object> b = body(request);
        Long paintingId = b.get("paintingId") instanceof Number n ? n.longValue() : null;
        if (paintingId == null) return error(HttpStatus.BAD_REQUEST, "Missing painting.");
        String gateway = "PAYPAL".equals(b.get("gateway")) ? "PAYPAL" : "RAZORPAY";

        Map<String, String> buyer = new LinkedHashMap<>();
        for (String k : List.of("name", "email", "phone", "line1", "line2", "city", "state", "postal", "country")) {
            String v = b.get(k) == null ? "" : String.valueOf(b.get(k)).trim();
            buyer.put(k, v.length() > 250 ? v.substring(0, 250) : v);
        }
        List<String> errors = new ArrayList<>();
        if (buyer.get("name").length() < 2) errors.add("Enter your full name.");
        if (!EMAIL.matcher(buyer.get("email")).matches()) errors.add("Enter a valid email address for your receipt.");
        if (buyer.get("line1").length() < 3) errors.add("Enter the delivery address.");
        if (buyer.get("city").length() < 2) errors.add("Enter the city.");
        if (buyer.get("postal").length() < 3 || buyer.get("postal").length() > 12) errors.add("Enter a valid postal / PIN code.");
        if (buyer.get("country").length() < 2) errors.add("Choose the delivery country.");
        boolean india = "india".equalsIgnoreCase(buyer.get("country"));
        if ("RAZORPAY".equals(gateway) && !india) errors.add("UPI, cards and NetBanking via Razorpay are for deliveries in India. Choose International (PayPal).");
        if ("PAYPAL".equals(gateway) && india) errors.add("For deliveries in India, please pay in rupees with UPI, cards or NetBanking.");
        if ("RAZORPAY".equals(gateway) && india && !buyer.get("phone").replaceAll("\\D", "").matches("\\d{10,13}")) errors.add("Enter a 10-digit mobile number for delivery updates.");
        if (!errors.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                    .body(json.encode(Map.of("message", errors.get(0), "errors", errors)));
        }

        List<String> keys = Holders.all(request, auth);
        MarketplaceService.Hold hold = market.activeHold(paintingId, keys);
        if (hold == null) {
            return fail(new MarketplaceException(409, "HOLD_EXPIRED", "Your 15-minute hold has ended, so the piece went back to the gallery. Reserve it again if it's still available."));
        }
        Map<String, Object> p = repo.find(paintingId);
        String holder = String.valueOf(p.get("holder_key"));
        if (!"RESERVED".equals(p.get("stock_status"))) {
            return fail(new MarketplaceException(409, "UNAVAILABLE", "This piece is no longer available."));
        }

        boolean razorpay = "RAZORPAY".equals(gateway);
        boolean live = razorpay ? payments.razorpayConfigured() : payments.paypalConfigured();
        if (!live && !payments.testMode()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, (razorpay ? "UPI and card" : "PayPal") + " payments are unavailable right now. Please try the other option or contact us.");
        }
        BigDecimal priceInr = (BigDecimal) p.get("final_price");
        String currency = razorpay ? "INR" : "USD";
        BigDecimal amount = razorpay ? priceInr : toUsd(priceInr);
        String orderId = newOrderId();
        market.createPendingOrder(new MarketplaceService.NewOrder(orderId, paintingId, auth.userId(request), holder, buyer,
                live ? gateway : "TEST", currency, amount, priceInr));
        rememberOrder(request, orderId);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("orderId", orderId);
        res.put("currency", currency);
        res.put("amount", amount);
        res.put("expiresAt", market.activeHold(paintingId, keys) == null ? hold.expiresAtMillis() : market.activeHold(paintingId, keys).expiresAtMillis());
        res.put("serverNow", System.currentTimeMillis());
        try {
            if (!live) {
                res.put("provider", "test");
            } else if (razorpay) {
                String rzp = payments.razorpayCreateOrder(orderId, priceInr, Map.of("marketplace_order_id", orderId, "painting_id", String.valueOf(paintingId), "slug", String.valueOf(p.get("slug"))));
                market.setProviderRef(orderId, rzp);
                res.put("provider", "razorpay");
                res.put("key", payments.razorpayKey());
                res.put("razorpayOrderId", rzp);
                res.put("amountPaise", priceInr.movePointRight(2).longValueExact());
                res.put("prefill", Map.of("name", buyer.get("name"), "email", buyer.get("email"), "contact", buyer.get("phone")));
                res.put("description", String.valueOf(p.get("title")) + " (1-of-1 original)");
            } else {
                String base = Inertia.baseUrl(request);
                String[] pp = payments.paypalCreateOrder(orderId, amount, "ArtistikCity original: " + p.get("title"),
                        base + "/marketplace/paypal/return?order=" + orderId, base + "/marketplace/" + p.get("slug") + "/checkout?payment=cancelled");
                market.setProviderRef(orderId, pp[0]);
                res.put("provider", "paypal");
                res.put("approveUrl", pp[1]);
            }
        } catch (Exception e) {
            log.error("Could not start marketplace payment {}", orderId, e);
            return error(HttpStatus.BAD_GATEWAY, "We couldn't reach the payment provider. Your hold is still active - please try again.");
        }
        return ok(res);
    }

    /** Browser confirmation: Razorpay Checkout handler (signature verified) or a simulated test payment. */
    @PostMapping("/api/v1/marketplace/confirm")
    public ResponseEntity<String> confirm(HttpServletRequest request) throws IOException {
        if (!Csrf.matches(request)) return error(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
        Map<String, Object> b = body(request);
        String orderId = String.valueOf(b.get("orderId"));
        Map<String, Object> o = market.order(orderId);
        if (o == null || !ownsOrder(request, o)) return error(HttpStatus.NOT_FOUND, "Order not found.");
        String provider = String.valueOf(b.get("provider"));
        String txn;
        if ("test".equals(provider)) {
            if (!"TEST".equals(o.get("gateway")) || !payments.testMode()) return error(HttpStatus.FORBIDDEN, "Test payments are disabled.");
            txn = "TEST-" + System.currentTimeMillis();
        } else if ("razorpay".equals(provider)) {
            String rzpOrder = String.valueOf(b.get("razorpay_order_id"));
            String paymentId = String.valueOf(b.get("razorpay_payment_id"));
            if (!rzpOrder.equals(o.get("provider_ref")) || !payments.razorpayCheckoutSignatureValid(rzpOrder, paymentId, String.valueOf(b.get("razorpay_signature")))) {
                log.warn("Rejected Razorpay confirmation for marketplace order {}", orderId);
                return error(HttpStatus.BAD_REQUEST, "We couldn't verify this payment. If you were charged, contact us with order " + orderId + ".");
            }
            txn = paymentId;
        } else {
            return error(HttpStatus.BAD_REQUEST, "Unknown payment provider.");
        }
        return outcome(orderId, market.finalizeSale(orderId, txn));
    }

    @GetMapping("/marketplace/paypal/return")
    public ResponseEntity<String> paypalReturn(HttpServletRequest request, @RequestParam("order") String orderId,
                                               @RequestParam(value = "token", required = false) String paypalOrderId) {
        Map<String, Object> o = market.order(orderId);
        if (o == null || paypalOrderId == null || !paypalOrderId.equals(o.get("provider_ref"))) {
            return redirect.to(request, "/marketplace");
        }
        String back = "/marketplace/" + o.get("slug") + "/checkout?payment=failed";
        try {
            String[] cap = payments.paypalCapture(paypalOrderId);
            if ("COMPLETED".equals(cap[0]) && amountMatches(o, cap[2], cap[3])) {
                MarketplaceService.Outcome out = market.finalizeSale(orderId, cap[1] == null ? paypalOrderId : cap[1]);
                rememberOrder(request, orderId);
                afterSale(orderId, out);
                return redirect.to(request, "/marketplace/orders/" + orderId);
            }
            log.warn("PayPal capture for marketplace {} returned {} {} {}", orderId, cap[0], cap[2], cap[3]);
        } catch (Exception e) {
            log.error("PayPal capture failed for marketplace order {}", orderId, e);
        }
        return redirect.to(request, back);
    }

    /**
     * Server-to-server notifications. Razorpay (X-Razorpay-Signature, HMAC-SHA256 of the raw body):
     * payment.captured / order.paid. PayPal (verified through PayPal's API): CHECKOUT.ORDER.APPROVED /
     * PAYMENT.CAPTURE.COMPLETED. Amounts are compared to the stored order before anything changes.
     */
    @PostMapping("/api/v1/marketplace/webhook")
    @SuppressWarnings("unchecked")
    public ResponseEntity<String> webhook(HttpServletRequest request) throws IOException {
        String raw = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, Object> event = json.decodeMap(raw);
        if (event == null) return error(HttpStatus.BAD_REQUEST, "Invalid payload.");
        try {
            String sig = request.getHeader("X-Razorpay-Signature");
            if (sig != null) {
                if (!payments.razorpayWebhookValid(raw, sig)) {
                    log.warn("Rejected marketplace Razorpay webhook with an invalid signature");
                    return error(HttpStatus.UNAUTHORIZED, "Invalid signature.");
                }
                String type = String.valueOf(event.get("event"));
                if (!"payment.captured".equals(type) && !"order.paid".equals(type)) return ok(Map.of("ignored", type));
                Map<String, Object> payment = (Map<String, Object>) path(event, "payload", "payment", "entity");
                if (payment == null) return ok(Map.of("ignored", "no payment entity"));
                Map<String, Object> o = market.orderByProviderRef(String.valueOf(payment.get("order_id")));
                if (o == null) return ok(Map.of("ignored", "not a marketplace order"));
                long expected = ((BigDecimal) o.get("amount")).movePointRight(2).longValue();
                long paid = payment.get("amount") instanceof Number n ? n.longValue() : -1;
                if (expected != paid || !"INR".equalsIgnoreCase(String.valueOf(payment.get("currency")))) {
                    log.warn("Marketplace Razorpay amount mismatch for {}: expected {} got {}", o.get("order_id"), expected, paid);
                    return error(HttpStatus.UNPROCESSABLE_ENTITY, "Amount mismatch.");
                }
                String orderId = String.valueOf(o.get("order_id"));
                MarketplaceService.Outcome out = market.finalizeSale(orderId, String.valueOf(payment.get("id")));
                afterSale(orderId, out);
                return ok(Map.of("orderId", orderId, "outcome", out.name()));
            }
            if (request.getHeader("paypal-transmission-id") != null) {
                Map<String, String> headers = new LinkedHashMap<>();
                for (String h : List.of("paypal-auth-algo", "paypal-cert-url", "paypal-transmission-id", "paypal-transmission-sig", "paypal-transmission-time")) {
                    headers.put(h, request.getHeader(h));
                }
                if (!payments.paypalWebhookValid(headers, raw)) {
                    log.warn("Rejected marketplace PayPal webhook that failed verification");
                    return error(HttpStatus.UNAUTHORIZED, "Invalid signature.");
                }
                String type = String.valueOf(event.get("event_type"));
                Map<String, Object> resource = (Map<String, Object>) event.get("resource");
                if (resource == null) return ok(Map.of("ignored", "no resource"));
                Map<String, Object> o = null;
                String txn = null;
                if ("CHECKOUT.ORDER.APPROVED".equals(type)) {
                    o = market.orderByProviderRef(String.valueOf(resource.get("id")));
                    if (o == null) return ok(Map.of("ignored", "not a marketplace order"));
                    String[] cap = payments.paypalCapture(String.valueOf(resource.get("id")));
                    if (!"COMPLETED".equals(cap[0]) || !amountMatches(o, cap[2], cap[3])) return ok(Map.of("status", String.valueOf(cap[0])));
                    txn = cap[1];
                } else if ("PAYMENT.CAPTURE.COMPLETED".equals(type)) {
                    Object related = path(resource, "supplementary_data", "related_ids", "order_id");
                    o = related == null ? null : market.orderByProviderRef(String.valueOf(related));
                    if (o == null && resource.get("custom_id") != null) o = market.order(String.valueOf(resource.get("custom_id")));
                    if (o == null) return ok(Map.of("ignored", "not a marketplace order"));
                    Map<String, Object> amount = (Map<String, Object>) resource.get("amount");
                    if (amount == null || !amountMatches(o, String.valueOf(amount.get("value")), String.valueOf(amount.get("currency_code")))) {
                        return error(HttpStatus.UNPROCESSABLE_ENTITY, "Amount mismatch.");
                    }
                    txn = String.valueOf(resource.get("id"));
                } else {
                    return ok(Map.of("ignored", type));
                }
                String orderId = String.valueOf(o.get("order_id"));
                MarketplaceService.Outcome out = market.finalizeSale(orderId, txn);
                afterSale(orderId, out);
                return ok(Map.of("orderId", orderId, "outcome", out.name()));
            }
        } catch (Exception e) {
            log.error("Marketplace webhook failed", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Webhook processing failed.");
        }
        return error(HttpStatus.BAD_REQUEST, "Unrecognised webhook source.");
    }

    // ================================================================ helpers

    private ResponseEntity<String> outcome(String orderId, MarketplaceService.Outcome out) {
        afterSale(orderId, out);
        return switch (out) {
            case SOLD, ALREADY_PAID -> ok(Map.of("status", "PAID", "redirect", "/marketplace/orders/" + orderId));
            case REFUND_REQUIRED -> ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of(
                    "code", "REFUND_REQUIRED", "redirect", "/marketplace/orders/" + orderId,
                    "message", "Your payment arrived after the hold ended and the piece was taken. We'll refund you in full within 5–7 working days.")));
            default -> error(HttpStatus.CONFLICT, "This order can't be completed. Contact us with order " + orderId + ".");
        };
    }

    /** Receipt to the collector and a heads-up to the studio (logged when mail isn't configured). */
    private void afterSale(String orderId, MarketplaceService.Outcome out) {
        if (out != MarketplaceService.Outcome.SOLD && out != MarketplaceService.Outcome.REFUND_REQUIRED) return;
        try {
            Map<String, Object> o = market.order(orderId);
            if (o == null) return;
            Map<String, Object> model = new LinkedHashMap<>(orderView(o));
            model.put("refund", out == MarketplaceService.Outcome.REFUND_REQUIRED);
            mailer.send(String.valueOf(o.get("buyer_email")), out == MarketplaceService.Outcome.SOLD
                    ? "Your ArtistikCity original: " + o.get("title") : "About your ArtistikCity order " + orderId, "emails/marketplace-order", model);
            String admin = env.getProperty("app.admin-email", "");
            if (!admin.isBlank()) {
                mailer.send(admin, (out == MarketplaceService.Outcome.SOLD ? "Sold: " : "Refund needed: ") + o.get("title") + " (" + orderId + ")", "emails/marketplace-order", model);
            }
        } catch (RuntimeException e) {
            log.warn("Could not send marketplace e-mails for {}", orderId, e);
        }
    }

    private boolean visible(HttpServletRequest request, Map<String, Object> p) {
        if (MarketplaceRepository.PUBLIC_STATUSES.contains(String.valueOf(p.get("stock_status")))) return true;
        if (auth.admin(request) != null) return true;
        Long userId = auth.userId(request);
        return userId != null && p.get("artist_user_id") instanceof Number n && n.longValue() == userId;
    }

    private Map<String, Object> holdState(HttpServletRequest request, long paintingId) {
        Map<String, Object> m = new LinkedHashMap<>();
        MarketplaceService.Hold mine = market.activeHold(paintingId, Holders.all(request, auth));
        m.put("mine", mine != null);
        m.put("expiresAt", mine == null ? null : mine.expiresAtMillis());
        return m;
    }

    private List<Map<String, Object>> holdsView(HttpServletRequest request) {
        return market.myHolds(Holders.all(request, auth)).stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("paintingId", h.get("painting_id"));
            m.put("slug", h.get("slug"));
            m.put("title", h.get("title"));
            m.put("imageUrl", h.get("image_url"));
            m.put("finalPrice", h.get("final_price"));
            m.put("expiresAt", MarketplaceViews.millis(h.get("expires_at")));
            return m;
        }).toList();
    }

    static Map<String, Object> orderView(Map<String, Object> o) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : List.of("order_id", "painting_id", "title", "slug", "artist_name", "medium", "surface", "height_inches", "width_inches", "image_url",
                "buyer_name", "buyer_email", "buyer_phone", "address_line1", "address_line2", "city", "state", "postal_code", "country",
                "gateway", "currency", "amount", "price_inr", "status", "courier", "tracking_number", "transaction_id")) {
            m.put(k, o.get(k));
        }
        m.put("has_certificate", MarketplaceViews.bool(o.get("has_certificate")));
        m.put("paid_at", MarketplaceViews.millis(o.get("paid_at")));
        m.put("created_at", MarketplaceViews.millis(o.get("created_at")));
        return m;
    }

    private BigDecimal inrPerUsd() {
        String v = env.getProperty("marketplace.inr-per-usd", env.getProperty("commission.inr-per-usd", "84"));
        try {
            BigDecimal r = new BigDecimal(v.trim());
            return r.signum() > 0 ? r : new BigDecimal("84");
        } catch (NumberFormatException e) {
            return new BigDecimal("84");
        }
    }

    private BigDecimal toUsd(BigDecimal inr) {
        BigDecimal usd = inr.divide(inrPerUsd(), 2, RoundingMode.HALF_UP);
        return usd.max(new BigDecimal("1.00"));
    }

    private static boolean amountMatches(Map<String, Object> o, String value, String currency) {
        try {
            return value != null && currency != null && currency.equalsIgnoreCase(String.valueOf(o.get("currency")))
                    && new BigDecimal(value).compareTo((BigDecimal) o.get("amount")) == 0;
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

    private static String newOrderId() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("AC-M-").append(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)).append('-');
        for (int i = 0; i < 6; i++) sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void rememberOrder(HttpServletRequest request, String orderId) {
        HttpSession s = request.getSession(true);
        Object o = s.getAttribute(SESSION_ORDERS);
        List<String> list = o instanceof List ? new ArrayList<>((List<String>) o) : new ArrayList<>();
        if (!list.contains(orderId)) list.add(orderId);
        s.setAttribute(SESSION_ORDERS, Collections.unmodifiableList(list));
    }

    private boolean ownsOrder(HttpServletRequest request, Map<String, Object> o) {
        Long userId = auth.userId(request);
        if (userId != null && o.get("user_id") instanceof Number n && n.longValue() == userId) return true;
        HttpSession s = request.getSession(false);
        return s != null && s.getAttribute(SESSION_ORDERS) instanceof List<?> l && l.contains(String.valueOf(o.get("order_id")));
    }

    private Map<String, Object> body(HttpServletRequest request) throws IOException {
        Map<String, Object> m = json.decodeMap(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        return m == null ? Map.of() : m;
    }

    private ResponseEntity<String> fail(MarketplaceException e) {
        return ResponseEntity.status(e.status()).contentType(MediaType.APPLICATION_JSON).body(json.encode(e.body()));
    }

    private ResponseEntity<String> ok(Object body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of("message", message)));
    }
}
