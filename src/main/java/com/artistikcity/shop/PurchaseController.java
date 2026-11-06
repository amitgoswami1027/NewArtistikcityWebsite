package com.artistikcity.shop;

import com.artistikcity.commission.CommissionPayments;
import com.artistikcity.http.Auth;
import com.artistikcity.http.Csrf;
import com.artistikcity.http.Redirects;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.marketplace.Holders;
import com.artistikcity.marketplace.MarketplaceException;
import com.artistikcity.marketplace.MarketplaceRepository;
import com.artistikcity.marketplace.MarketplaceService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The purchase flow for marketplace artworks, four screens and nothing in between:
 * <ol>
 *   <li>the artwork page {@code /marketplace/<slug>}: everything about the piece, how to buy it (original, numbered canvas,
 *       poster, digital), size and finish, and "Talk to the artist"; {@code /purchase/step-1} redirects there</li>
 *   <li>{@code /purchase/step-2}: the room preview (photographed rooms, one-click switch), size and framing</li>
 *   <li>{@code /purchase/checkout}: contact, delivery and payment in one accordion, with the order summary beside it</li>
 *   <li>{@code /purchase/orders/<id>}: the order summary / confirmation</li>
 * </ol>
 * {@code /purchase/preview} (the old review step) redirects to the room preview.
 * The step being configured (the "draft") lives in the HTTP session, so steps 2 and 3 work after a refresh. Choosing the
 * original reserves it for 15 minutes straight away, so nobody else can buy it while the collector decides.
 */
@Controller
public class PurchaseController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.artistikcity.payments.TestPayments testPayments;
    @org.springframework.beans.factory.annotation.Autowired
    private com.artistikcity.rooms.RoomScenes roomScenes;

    private static final Logger log = LoggerFactory.getLogger(PurchaseController.class);
    static final String DRAFT = "purchase.draft";
    static final String SESSION_ORDERS = "shop.orders";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    private final Inertia inertia;
    private final Json json;
    private final Auth auth;
    private final Redirects redirect;
    private final Environment env;
    private final Mailer mailer;
    private final ShopRepository repo;
    private final CartService cart;
    private final ShopOrderService orders;
    private final ArtistHubService hub;
    private final MarketplaceService market;
    private final MarketplaceRepository marketRepo;
    private final CommissionPayments payments;

    public PurchaseController(Inertia inertia, Json json, Auth auth, Redirects redirect, Environment env, Mailer mailer, ShopRepository repo,
                              CartService cart, ShopOrderService orders, ArtistHubService hub, MarketplaceService market,
                              MarketplaceRepository marketRepo, CommissionPayments payments) {
        this.inertia = inertia;
        this.json = json;
        this.auth = auth;
        this.redirect = redirect;
        this.env = env;
        this.mailer = mailer;
        this.repo = repo;
        this.cart = cart;
        this.orders = orders;
        this.hub = hub;
        this.market = market;
        this.marketRepo = marketRepo;
        this.payments = payments;
    }

    // ================================================================ pages

    /**
     * Screen 1 is the artwork page itself (format, size and "talk to the artist" in the buy box), so the old step-1 URL
     * and print links land there with the format preselected.
     */
    @GetMapping({"/purchase", "/purchase/", "/purchase/step-1"})
    public ResponseEntity<String> step1(HttpServletRequest request, @RequestParam(value = "artwork", required = false) String slug,
                                        @RequestParam(value = "format", required = false) String format) {
        Map<String, Object> draft = draft(request);
        if (slug == null || slug.isBlank()) {
            if (draft == null) return redirect.to(request, "/marketplace");
            slug = String.valueOf(draft.get("slug"));
        }
        if (!slug.matches("[A-Za-z0-9-]{1,200}")) return redirect.to(request, "/marketplace");
        String f = format == null ? null : format.toUpperCase(Locale.ROOT);
        boolean known = f != null && List.of("ORIGINAL", "CANVAS", "POSTER", "DIGITAL").contains(f);
        return redirect.to(request, "/marketplace/" + slug + (known ? "?format=" + f : "") + "#buy");
    }

    /** Screen 2: the room preview with size and framing. Digital downloads have nothing to hang, so they go to payment. */
    @GetMapping("/purchase/step-2")
    public ResponseEntity<String> step2(HttpServletRequest request) {
        Map<String, Object> draft = draft(request);
        if (auth.userId(request) == null) return redirect.to(request, draft == null ? "/marketplace" : com.artistikcity.web.PurchaseIntent.signIn("/purchase/step-2"));
        if (draft != null && "DIGITAL".equals(draft.get("format"))) return redirect.to(request, "/purchase/buy-now");
        ResponseEntity<String> held = holdAfterSignIn(request, draft);
        if (held != null) return held;
        return draftPage(request, "Purchase/Step2Room");
    }

    /**
     * A guest who chose the original was sent to sign in before it was held (nobody can block an original without an
     * account). Back here, signed in, the hold is taken now; if someone else got there first, back to the artwork page.
     */
    private ResponseEntity<String> holdAfterSignIn(HttpServletRequest request, Map<String, Object> draft) {
        if (draft == null || !"ORIGINAL".equals(draft.get("format"))) return null;
        long id = ((Number) draft.get("paintingId")).longValue();
        if (market.activeHold(id, Holders.all(request, auth)) != null) return null;
        try {
            // the piece they just chose wins over an older hold of theirs (one original at a time)
            market.reserve(id, Holders.primary(request, auth), Holders.all(request, auth), auth.userId(request), true);
            marketRepo.syncStudentListing(id);
            return null;
        } catch (MarketplaceException e) {
            log.info("Original {} could not be held after sign-in: {}", id, e.getMessage());
            return redirect.to(request, "/marketplace/" + draft.get("slug") + "?unavailable=" + e.body().get("code") + "#buy");
        }
    }

    /**
     * "Continue to payment": the chosen piece (the draft) becomes the only thing being bought. There is no cart: each
     * purchase is one artwork, so whatever was in the basket before is replaced.
     */
    @GetMapping("/purchase/buy-now")
    public ResponseEntity<String> buyNow(HttpServletRequest request) {
        Map<String, Object> draft = draft(request);
        if (draft == null) return redirect.to(request, "/marketplace");
        if (auth.userId(request) == null) return redirect.to(request, com.artistikcity.web.PurchaseIntent.signIn("/purchase/buy-now"));
        ResponseEntity<String> held = holdAfterSignIn(request, draft);
        if (held != null) return held;
        cart.replaceWith(request, ((Number) draft.get("paintingId")).longValue(), ShopPricing.Selection.of(draft.get("format"), draft.get("size"),
                draft.get("finish"), draft.get("frame"), draft.get("qty")));
        return redirect.to(request, "/purchase/checkout");
    }

    /** The old review step is now part of the room preview (price) and the checkout (coupon, totals). */
    @GetMapping("/purchase/preview")
    public ResponseEntity<String> preview(HttpServletRequest request) {
        Map<String, Object> draft = draft(request);
        return redirect.to(request, draft != null && "DIGITAL".equals(draft.get("format")) ? "/purchase/checkout" : "/purchase/step-2");
    }

    private ResponseEntity<String> draftPage(HttpServletRequest request, String component) {
        Map<String, Object> draft = draft(request);
        if (draft == null || draft.get("format") == null) return redirect.to(request, draft == null ? "/marketplace" : "/marketplace/" + draft.get("slug"));
        Map<String, Object> p = repo.painting(((Number) draft.get("paintingId")).longValue());
        if (p == null || !MarketplaceRepository.PUBLIC_STATUSES.contains(String.valueOf(p.get("stock_status")))) {
            clearDraft(request);
            return redirect.to(request, "/marketplace");
        }
        Map<String, Object> props = pageProps(request, p);
        props.put("contact", hub.contact(p));
        props.put("conversation", hub.conversation(Holders.all(request, auth), ((Number) p.get("id")).longValue()));
        Row user = auth.user(request);
        props.put("customer", user == null ? null : Map.of("name", user.str("name", ""), "email", user.str("email", "")));
        boolean roomsOn = roomScenes.enabled();
        props.put("rooms", roomsOn ? roomScenes.active() : List.of());
        props.put("roomsEnabled", roomsOn);
        return inertia.render(request, component, props);
    }

    private Map<String, Object> pageProps(HttpServletRequest request, Map<String, Object> p) {
        long id = ((Number) p.get("id")).longValue();
        ShopPricing.Art art = ShopRepository.art(p);
        MarketplaceService.Hold hold = market.activeHold(id, Holders.all(request, auth));
        String stock = String.valueOf(p.get("stock_status"));
        boolean heldByOther = hold == null && "RESERVED".equals(stock) && p.get("reserved_until") != null
                && ShopRepository.ldt(p.get("reserved_until")).isAfter(java.time.LocalDateTime.now());
        boolean originalAvailable = hold != null || ("AVAILABLE".equals(stock) || "RESERVED".equals(stock)) && !heldByOther;
        String reason = "SOLD".equals(stock) ? "This original has found its collector. Prints are still available."
                : heldByOther ? "Another collector is holding the original right now." : null;
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> painting = new LinkedHashMap<>();
        for (String k : List.of("id", "slug", "title", "artist_name", "medium", "surface", "height_inches", "width_inches", "year_created",
                "final_price", "base_price", "discount_percentage", "stock_status", "source", "image_url", "artist_notes")) {
            painting.put(k, p.get(k));
        }
        painting.put("is_framed", ShopRepository.bool(p.get("is_framed")));
        painting.put("is_signed", ShopRepository.bool(p.get("is_signed")));
        painting.put("has_certificate", ShopRepository.bool(p.get("has_certificate")));
        props.put("painting", painting);
        props.put("offer", ShopPricing.offer(art, repo.options(), originalAvailable, reason));
        props.put("hold", hold == null ? null : Map.of("expiresAt", hold.expiresAtMillis()));
        props.put("draft", draft(request));
        props.put("serverNow", System.currentTimeMillis());
        return props;
    }

    @GetMapping("/purchase/checkout")
    public ResponseEntity<String> checkout(HttpServletRequest request, @RequestParam(value = "payment", required = false) String payment) {
        if (auth.userId(request) == null) return redirect.to(request, com.artistikcity.web.PurchaseIntent.signIn("/purchase/checkout"));
        CartService.Priced priced = cart.price(request, "India", null, null);
        if (priced.lines().stream().noneMatch(l -> l.itemId() != null)) {
            Map<String, Object> draft = draft(request);
            return redirect.to(request, draft == null ? "/marketplace" : "/marketplace/" + draft.get("slug") + "#buy");
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("cart", cart.json(priced));
        Row user = auth.user(request);
        props.put("customer", user == null ? null : Map.of("name", user.str("name", ""), "email", user.str("email", ""), "phone", user.str("phone", "")));
        props.put("gateways", Map.of("razorpay", payments.razorpayConfigured(), "paypal", payments.paypalConfigured(), "testMode", payments.testMode(),
                "simulate", testPayments.offerSimulation(request)));
        props.put("inrPerUsd", inrPerUsd());
        props.put("payment", payment);
        props.put("serverNow", System.currentTimeMillis());
        return inertia.render(request, "Purchase/Checkout", props);
    }

    @GetMapping("/purchase/orders/{orderId}")
    public ResponseEntity<String> confirmation(HttpServletRequest request, @PathVariable("orderId") String orderId) {
        Map<String, Object> o = orders.order(orderId);
        if (o == null || !owns(request, o)) return redirect.to(request, "/marketplace");
        Map<String, Object> view = new LinkedHashMap<>(ShopOrderService.view(o, orders.items(orderId), siteUrl(request)));
        view.remove("staff_notes");                                    // internal notes never reach the collector
        return inertia.render(request, "Purchase/ThankYou", Map.of("order", view));
    }

    // ================================================================ draft + estimate API

    /**
     * JSON {slug|paintingId, format, size, finish, frame, qty, switch}: stores the step's choices in the session and
     * returns the priced selection. Choosing the original reserves it (409 HOLD_LIMIT / HELD_BY_OTHER / SOLD otherwise).
     */
    @PostMapping("/api/v1/purchase/draft")
    public ResponseEntity<String> saveDraft(HttpServletRequest request) throws IOException {
        if (!Csrf.matches(request)) return error(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
        Map<String, Object> b = body(request);
        Map<String, Object> p = null;
        if (b.get("paintingId") instanceof Number n) p = repo.painting(n.longValue());
        else if (b.get("slug") != null) {
            Map<String, Object> s = marketRepo.bySlug(String.valueOf(b.get("slug")));
            p = s == null ? null : repo.painting(((Number) s.get("id")).longValue());
        }
        if (p == null || !MarketplaceRepository.PUBLIC_STATUSES.contains(String.valueOf(p.get("stock_status")))) {
            return error(HttpStatus.NOT_FOUND, "This artwork is no longer listed.");
        }
        ShopPricing.Selection sel = ShopPricing.Selection.of(b.get("format"), b.get("size"), b.get("finish"), b.get("frame"), b.get("qty"));
        ShopPricing.Quote q = ShopPricing.quote(ShopRepository.art(p), repo.options(), sel);
        if (!q.ok()) return error(HttpStatus.UNPROCESSABLE_ENTITY, q.error());
        long id = ((Number) p.get("id")).longValue();
        Map<String, Object> res = new LinkedHashMap<>();
        boolean guest = auth.userId(request) == null;
        if ("ORIGINAL".equals(q.selection().format()) && !guest) {
            try {
                MarketplaceService.Hold hold = market.reserve(id, Holders.primary(request, auth), Holders.all(request, auth), auth.userId(request),
                        Boolean.TRUE.equals(b.get("switch")));
                marketRepo.syncStudentListing(id);
                res.put("hold", Map.of("expiresAt", hold.expiresAtMillis()));
            } catch (MarketplaceException e) {
                return fail(e);
            }
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("slug", p.get("slug"));
        d.put("paintingId", id);
        d.put("format", q.selection().format());
        d.put("size", q.selection().size());
        d.put("finish", q.selection().finish());
        d.put("frame", q.selection().frame());
        d.put("qty", q.selection().qty());
        request.getSession(true).setAttribute(DRAFT, Collections.unmodifiableMap(d));
        if (guest) {
            // buying starts with an account: the choice is kept, the guest signs in and lands on the next step
            String next = "DIGITAL".equals(q.selection().format()) ? "/purchase/buy-now" : "/purchase/step-2";
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of(
                    "code", "SIGN_IN", "message", "Log in or join to buy this artwork.", "redirect", com.artistikcity.web.PurchaseIntent.signIn(next))));
        }
        res.put("draft", d);
        res.put("quote", quoteJson(q));
        res.put("serverNow", System.currentTimeMillis());
        return ok(res);
    }

    /** JSON {country}: the cart plus the current draft, priced (the preview's cost estimate). */
    @PostMapping("/api/v1/purchase/estimate")
    public ResponseEntity<String> estimateApi(HttpServletRequest request) throws IOException {
        if (!Csrf.matches(request)) return error(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
        Map<String, Object> b = body(request);
        Map<String, Object> draft = draft(request);
        Map<String, Object> p = draft == null ? null : repo.painting(((Number) draft.get("paintingId")).longValue());
        return ok(estimate(request, p, draft, b.get("country") == null ? null : String.valueOf(b.get("country"))));
    }

    private Map<String, Object> estimate(HttpServletRequest request, Map<String, Object> p, Map<String, Object> draft, String country) {
        ShopPricing.Selection sel = draft == null || p == null ? null
                : ShopPricing.Selection.of(draft.get("format"), draft.get("size"), draft.get("finish"), draft.get("frame"), draft.get("qty"));
        // the same choice already in the cart (e.g. after "add to cart, keep browsing") isn't counted twice
        CartService.Priced current = cart.price(request, country, null, null);
        CartService.PricedLine existing = null;
        if (sel != null) {
            ShopPricing.Quote q = ShopPricing.quote(ShopRepository.art(p), repo.options(), sel);
            for (CartService.PricedLine l : current.lines()) {
                ShopPricing.Selection s = l.quote().selection();
                if (((Number) l.painting().get("id")).longValue() == ((Number) p.get("id")).longValue() && s.format().equals(q.selection().format())
                        && s.size().equals(q.selection().size()) && s.finish().equals(q.selection().finish()) && s.frame().equals(q.selection().frame())) {
                    existing = l;
                }
            }
        }
        final CartService.PricedLine found = existing;
        CartService.Priced priced = sel == null || found != null ? current : cart.price(request, country, p, sel);
        Map<String, Object> m = new LinkedHashMap<>();
        CartService.PricedLine line = sel == null ? null : found != null ? found : priced.lines().get(priced.lines().size() - 1);
        m.put("line", line == null ? null : CartService.lineJson(line));
        m.put("added", found != null);
        m.put("inCart", priced.lines().stream().filter(l -> l.itemId() != null && l.ok() && (found == null || !l.itemId().equals(found.itemId()))).count());
        m.put("totals", CartService.totalsJson(priced));
        return m;
    }

    static Map<String, Object> quoteJson(ShopPricing.Quote q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unitPrice", q.unitPrice());
        m.put("lineTotal", q.lineTotal());
        m.put("label", q.label());
        m.put("width", q.widthIn());
        m.put("height", q.heightIn());
        m.put("maxQty", q.maxQty());
        m.put("framePrice", q.framePrice());
        return m;
    }

    // ================================================================ checkout API

    /**
     * JSON {gateway: RAZORPAY|PAYPAL, name, email, phone, line1, line2, city, state, postal, country}. Prices the cart on
     * the server, stores a PAYMENT_PENDING order and starts the payment (Razorpay order, PayPal approval link, or test).
     */
    @PostMapping("/api/v1/purchase/checkout")
    public ResponseEntity<String> startCheckout(HttpServletRequest request) throws IOException {
        if (!Csrf.matches(request)) return error(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
        if (auth.userId(request) == null) return signInFirst("/purchase/checkout");
        Map<String, Object> b = body(request);
        String gateway = "PAYPAL".equals(b.get("gateway")) ? "PAYPAL" : "RAZORPAY";
        Map<String, String> f = new LinkedHashMap<>();
        for (String k : List.of("name", "email", "phone", "line1", "line2", "city", "state", "postal", "country")) {
            String v = b.get(k) == null ? "" : String.valueOf(b.get(k)).trim();
            f.put(k, v.length() > 250 ? v.substring(0, 250) : v);
        }
        if (f.get("country").isEmpty()) f.put("country", "RAZORPAY".equals(gateway) ? "India" : "");
        CartService.Priced priced = cart.price(request, f.get("country"), null, null);
        List<CartService.PricedLine> lines = priced.lines().stream().filter(l -> l.itemId() != null).toList();
        if (lines.isEmpty()) return error(HttpStatus.CONFLICT, "Your cart is empty.");
        if (lines.stream().anyMatch(l -> !l.ok())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of(
                    "code", "CART_CHANGED", "message", "Something in your cart changed. Check the highlighted items.", "cart", cart.json(priced))));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        boolean india = "india".equalsIgnoreCase(f.get("country"));
        if (f.get("name").length() < 2) errors.put("name", "Enter your full name.");
        if (!EMAIL.matcher(f.get("email")).matches()) errors.put("email", "Enter a valid email address for your receipt" + (priced.needsShipping() ? "." : " and download link."));
        if (f.get("country").length() < 2) errors.put("country", "Choose your country.");
        if (priced.needsShipping()) {
            if (f.get("line1").length() < 3) errors.put("line1", "Enter the street address.");
            if (f.get("city").length() < 2) errors.put("city", "Enter the city.");
            if (india && !f.get("postal").matches("\\d{6}")) errors.put("postal", "Enter the 6-digit PIN code.");
            if (!india && (f.get("postal").length() < 3 || f.get("postal").length() > 12)) errors.put("postal", "Enter the postal code.");
        }
        if ("RAZORPAY".equals(gateway) && india && priced.needsShipping() && !f.get("phone").replaceAll("\\D", "").matches("\\d{10,13}")) {
            errors.put("phone", "Enter a 10-digit mobile number for delivery updates.");
        }
        if ("RAZORPAY".equals(gateway) && !india) errors.put("country", "UPI, cards and NetBanking via Razorpay are for India. Choose International (PayPal).");
        if ("PAYPAL".equals(gateway) && india) errors.put("country", "For India, please pay in rupees with UPI, cards or NetBanking.");
        if (!errors.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                    .body(json.encode(Map.of("message", errors.values().iterator().next(), "errors", errors)));
        }
        boolean razorpay = "RAZORPAY".equals(gateway);
        // a test-buyer account (Admin > People) may simulate the payment on the live site
        boolean simulate = Boolean.TRUE.equals(b.get("simulate"));
        if (simulate && !testPayments.offerSimulation(request)) return error(HttpStatus.FORBIDDEN, "Simulated payments are only for test accounts.");
        boolean live = !simulate && (razorpay ? payments.razorpayConfigured() : payments.paypalConfigured());
        if (!live && !simulate && !payments.testMode()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, (razorpay ? "UPI and card" : "PayPal") + " payments are unavailable right now. Please try the other option or contact us.");
        }
        BigDecimal totalInr = priced.total();
        if (totalInr.signum() <= 0) return error(HttpStatus.CONFLICT, "Your cart total is zero. Remove the coupon or add an artwork.");
        String currency = razorpay ? "INR" : "USD";
        BigDecimal amount = razorpay ? totalInr.setScale(2, RoundingMode.HALF_UP) : toUsd(totalInr);
        String orderId = ShopOrderService.newOrderId();
        String owner = cart.ownerKey(request);
        orders.createPending(orderId, auth.userId(request), owner, Holders.all(request, auth),
                new ShopOrderService.Buyer(f.get("name"), f.get("email"), f.get("phone"), f.get("line1"), f.get("line2"), f.get("city"), f.get("state"),
                        f.get("postal"), f.get("country")), priced, live ? gateway : "TEST", currency, amount);
        remember(request, orderId);
        if (!live) jdbcMarkTest("shop_orders", orderId);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("orderId", orderId);
        res.put("currency", currency);
        res.put("amount", amount);
        String description = lines.size() == 1 ? lines.get(0).painting().get("title") + " · " + CartService.formatName(lines.get(0).quote().selection().format())
                : lines.size() + " artworks from ArtistikCity";
        try {
            if (!live) {
                res.put("provider", "test");
            } else if (razorpay) {
                String rzp = payments.razorpayCreateOrder(orderId, totalInr, Map.of("shop_order_id", orderId));
                orders.setProviderRef(orderId, rzp);
                res.put("provider", "razorpay");
                res.put("key", payments.razorpayKey());
                res.put("razorpayOrderId", rzp);
                res.put("amountPaise", totalInr.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact());
                res.put("prefill", Map.of("name", f.get("name"), "email", f.get("email"), "contact", f.get("phone")));
                res.put("description", description);
            } else {
                String base = Inertia.baseUrl(request);
                String[] pp = payments.paypalCreateOrder(orderId, amount, "ArtistikCity: " + description,
                        base + "/purchase/paypal/return?order=" + orderId, base + "/purchase/checkout?payment=cancelled");
                orders.setProviderRef(orderId, pp[0]);
                res.put("provider", "paypal");
                res.put("approveUrl", pp[1]);
            }
        } catch (Exception e) {
            log.error("Could not start shop payment {}", orderId, e);
            return error(HttpStatus.BAD_GATEWAY, "We couldn't reach the payment provider. Your cart is safe - please try again.");
        }
        return ok(res);
    }

    /** Browser confirmation: Razorpay Checkout handler (signature verified) or a simulated test payment. */
    @PostMapping("/api/v1/purchase/confirm")
    public ResponseEntity<String> confirm(HttpServletRequest request) throws IOException {
        if (!Csrf.matches(request)) return error(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
        Map<String, Object> b = body(request);
        String orderId = String.valueOf(b.get("orderId"));
        Map<String, Object> o = orders.order(orderId);
        if (o == null || !owns(request, o)) return error(HttpStatus.NOT_FOUND, "Order not found.");
        String provider = String.valueOf(b.get("provider"));
        String txn;
        if ("test".equals(provider)) {
            if (!"TEST".equals(o.get("gateway")) || !(payments.testMode() || testPayments.isTestBuyer(request))) return error(HttpStatus.FORBIDDEN, "Test payments are disabled.");
            txn = "TEST-" + System.currentTimeMillis();
        } else if ("razorpay".equals(provider)) {
            String rzpOrder = String.valueOf(b.get("razorpay_order_id"));
            String paymentId = String.valueOf(b.get("razorpay_payment_id"));
            if (!rzpOrder.equals(o.get("provider_ref")) || !payments.razorpayCheckoutSignatureValid(rzpOrder, paymentId, String.valueOf(b.get("razorpay_signature")))) {
                log.warn("Rejected Razorpay confirmation for shop order {}", orderId);
                return error(HttpStatus.BAD_REQUEST, "We couldn't verify this payment. If you were charged, contact us with order " + orderId + ".");
            }
            txn = paymentId;
        } else {
            return error(HttpStatus.BAD_REQUEST, "Unknown payment provider.");
        }
        ShopOrderService.Outcome out = orders.finalizeSale(orderId, txn);
        afterSale(request, orderId, out);
        return switch (out) {
            case PAID, PARTIAL, ALREADY_PAID, REFUND_REQUIRED -> ok(Map.of("status", out.name(), "redirect", "/purchase/orders/" + orderId));
            default -> error(HttpStatus.CONFLICT, "This order can't be completed. Contact us with order " + orderId + ".");
        };
    }

    @GetMapping("/purchase/paypal/return")
    public ResponseEntity<String> paypalReturn(HttpServletRequest request, @RequestParam("order") String orderId,
                                               @RequestParam(value = "token", required = false) String paypalOrderId) {
        Map<String, Object> o = orders.order(orderId);
        if (o == null || paypalOrderId == null || !paypalOrderId.equals(o.get("provider_ref"))) return redirect.to(request, "/marketplace");
        try {
            String[] cap = payments.paypalCapture(paypalOrderId);
            if ("COMPLETED".equals(cap[0]) && amountMatches(o, cap[2], cap[3])) {
                ShopOrderService.Outcome out = orders.finalizeSale(orderId, cap[1] == null ? paypalOrderId : cap[1]);
                remember(request, orderId);
                afterSale(request, orderId, out);
                return redirect.to(request, "/purchase/orders/" + orderId);
            }
            log.warn("PayPal capture for shop order {} returned {} {} {}", orderId, cap[0], cap[2], cap[3]);
        } catch (Exception e) {
            log.error("PayPal capture failed for shop order {}", orderId, e);
        }
        return redirect.to(request, "/purchase/checkout?payment=failed");
    }

    // ================================================================ after payment

    /** Receipt (with download links) to the buyer and a heads-up to the studio. Used by the webhook too. */
    public void afterSale(HttpServletRequest request, String orderId, ShopOrderService.Outcome out) {
        if (out != ShopOrderService.Outcome.PAID && out != ShopOrderService.Outcome.PARTIAL && out != ShopOrderService.Outcome.REFUND_REQUIRED) return;
        try {
            Map<String, Object> o = orders.order(orderId);
            List<Map<String, Object>> items = orders.items(orderId);
            String base = siteUrl(request);
            Map<String, Object> view = ShopOrderService.view(o, items, base);
            Map<String, Object> model = new LinkedHashMap<>(view);
            for (String k : List.of("subtotal_inr", "discount_inr", "shipping_inr", "total_inr", "refund_due_inr")) model.put(k, rupees(o.get(k)));
            List<Map<String, Object>> its = new ArrayList<>();
            boolean downloads = false;
            for (Object x : (List<?>) view.get("items")) {
                @SuppressWarnings("unchecked") Map<String, Object> i = new LinkedHashMap<>((Map<String, Object>) x);
                i.put("line_total_inr", rupees(i.get("line_total_inr")));
                i.put("refund", "REFUND_REQUIRED".equals(i.get("status")));
                downloads |= i.get("download_url") != null;
                its.add(i);
            }
            model.put("items", its);
            model.put("multi", its.size() > 1);
            model.put("hasDownloads", downloads);
            model.put("allRefund", out == ShopOrderService.Outcome.REFUND_REQUIRED);
            model.put("partialRefund", out == ShopOrderService.Outcome.PARTIAL);
            model.put("orderUrl", base + "/purchase/orders/" + orderId);
            mailer.send(String.valueOf(o.get("buyer_email")), out == ShopOrderService.Outcome.REFUND_REQUIRED
                    ? "About your ArtistikCity order " + orderId : "Your ArtistikCity order " + orderId, "emails/shop-order", model);
            String admin = env.getProperty("app.admin-email", "");
            if (!admin.isBlank()) mailer.send(admin, "Marketplace order " + orderId + (out == ShopOrderService.Outcome.PAID ? "" : " (refund needed)"), "emails/shop-order", model);
        } catch (RuntimeException e) {
            log.warn("Could not send shop e-mails for {}", orderId, e);
        }
    }

    // ================================================================ helpers

    @SuppressWarnings("unchecked")
    static Map<String, Object> draft(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s != null && s.getAttribute(DRAFT) instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    static void clearDraft(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s != null) s.removeAttribute(DRAFT);
    }

    @SuppressWarnings("unchecked")
    static void remember(HttpServletRequest request, String orderId) {
        HttpSession s = request.getSession(true);
        Object o = s.getAttribute(SESSION_ORDERS);
        List<String> list = o instanceof List ? new ArrayList<>((List<String>) o) : new ArrayList<>();
        if (!list.contains(orderId)) list.add(orderId);
        s.setAttribute(SESSION_ORDERS, Collections.unmodifiableList(list));
    }

    boolean owns(HttpServletRequest request, Map<String, Object> o) {
        Long userId = auth.userId(request);
        if (userId != null && o.get("user_id") instanceof Number n && n.longValue() == userId) return true;
        if (Holders.all(request, auth).contains(String.valueOf(o.get("owner_key")))) return true;
        HttpSession s = request.getSession(false);
        return s != null && s.getAttribute(SESSION_ORDERS) instanceof List<?> l && l.contains(String.valueOf(o.get("order_id")));
    }

    String siteUrl(HttpServletRequest request) {
        String u = env.getProperty("app.url", "");
        if (u != null && !u.isBlank()) return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
        return request == null ? "" : Inertia.baseUrl(request);
    }

    static String rupees(Object v) {
        BigDecimal b = ShopRepository.dec(v);
        if (b == null) return "0";
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN"));
        return nf.format(b.setScale(0, RoundingMode.HALF_UP));
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
        return inr.divide(inrPerUsd(), 2, RoundingMode.HALF_UP).max(new BigDecimal("1.00"));
    }

    static boolean amountMatches(Map<String, Object> o, String value, String currency) {
        try {
            return value != null && currency != null && currency.equalsIgnoreCase(String.valueOf(o.get("currency")))
                    && new BigDecimal(value).compareTo((BigDecimal) o.get("amount")) == 0;
        } catch (RuntimeException e) {
            return false;
        }
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

    @org.springframework.beans.factory.annotation.Autowired
    private com.artistikcity.support.Db testDb;

    /** A simulated (test) order: kept out of revenue, the ledger, payouts and the reconciler. */
    private void jdbcMarkTest(String table, String orderId) {
        testDb.update("update " + table + " set is_test = 1 where order_id = ?", orderId);
    }

    private ResponseEntity<String> signInFirst(String returnTo) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of(
                "code", "SIGN_IN", "message", "Please log in to finish your purchase.", "redirect", com.artistikcity.web.PurchaseIntent.signIn(returnTo))));
    }
}
