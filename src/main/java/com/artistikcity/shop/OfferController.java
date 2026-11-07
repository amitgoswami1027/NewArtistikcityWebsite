package com.artistikcity.shop;

import com.artistikcity.admin.Audit;
import com.artistikcity.lifecycle.Personas;
import com.artistikcity.http.Auth;
import com.artistikcity.http.Csrf;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.marketplace.MarketplaceException;
import com.artistikcity.marketplace.MarketplaceRepository;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Make an offer" (see {@link OfferService}) and the cart page.
 * <ul>
 *   <li>Buyer: page {@code /user/offers} (Account > Your offers); {@code GET /api/v1/offers/painting/{id}} (terms + the buyer's live
 *       offer for the artwork page), {@code POST /api/v1/offers} {paintingId, amount, message},
 *       {@code POST /api/v1/offers/{id}} {action: accept|decline|withdraw|reoffer, amount, note},
 *       {@code POST /api/v1/offers/{id}/pay} (puts the original in the cart at the agreed price and returns the checkout).</li>
 *   <li>Student seller (My studio > Sell your art): {@code GET /api/studio/sell/offers}, {@code POST /api/studio/sell/offers/{id}}
 *       {action: accept|counter|decline|withdraw, amount, note}, {@code POST /api/studio/sell/listings/{id}/offers} {enabled, minPct}.</li>
 *   <li>Staff (capability {@code marketplace}; teachers only their own pieces): {@code GET /admin/console/api/offers},
 *       {@code POST /admin/console/api/offers/{id}} {action: accept|counter|decline|withdraw|cancel}, {@code PUT /admin/console/api/offers/settings},
 *       {@code PUT /admin/console/api/offers/paintings/{id}} {enabled: true|false|null, minPct}.</li>
 * </ul>
 * Every /api POST checks CSRF; buyers must be signed in (401 {code: SIGN_IN, redirect}).
 */
@Controller
public class OfferController {

    private final Inertia inertia;
    private final Auth auth;
    private final Json json;
    private final OfferService offers;
    private final ShopRepository shop;
    private final CartService cart;
    private final Audit audit;
    private final MarketplaceRepository marketRepo;

    public OfferController(Inertia inertia, Auth auth, Json json, OfferService offers, ShopRepository shop, CartService cart, Audit audit, MarketplaceRepository marketRepo) {
        this.inertia = inertia;
        this.auth = auth;
        this.json = json;
        this.offers = offers;
        this.shop = shop;
        this.cart = cart;
        this.audit = audit;
        this.marketRepo = marketRepo;
    }

    // ================================================================ pages

    /** The full cart: lines, quantities, save for later, coupon, totals, "Checkout" (the whole cart). */
    @GetMapping({"/cart", "/cart/"})
    public ResponseEntity<String> cartPage(HttpServletRequest request) {
        CartService.Priced priced = cart.price(request, "India", null, null);
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> c = cart.json(priced);
        c.put("serverNow", System.currentTimeMillis());
        props.put("cart", c);
        props.put("signedIn", auth.userId(request) != null);
        return inertia.render(request, "Shop/Cart", props);
    }

    @GetMapping({"/user/offers", "/user/offers/"})
    public ResponseEntity<String> buyerPage(HttpServletRequest request) {
        Long uid = auth.userId(request);
        if (uid == null) return ResponseEntity.status(HttpStatus.FOUND).header("Location", com.artistikcity.web.PurchaseIntent.signIn("/user/offers")).build();
        List<Map<String, Object>> list = offers.forBuyer(uid);
        offers.markBuyerRead(uid);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("offers", list);
        props.put("serverNow", System.currentTimeMillis());
        return inertia.renderUser(request, "Account/Offers", props);
    }

    // ================================================================ buyer API

    /** Terms for the artwork page: whether offers are taken, the lowest offer, and the buyer's live offer if any. */
    @GetMapping("/api/v1/offers/painting/{id}")
    public ResponseEntity<String> terms(HttpServletRequest request, @PathVariable("id") long id) {
        Map<String, Object> p = shop.painting(id);
        if (p == null) return error(HttpStatus.NOT_FOUND, "This artwork is no longer listed.");
        OfferService.Settings s = offers.settings();
        OfferService.Terms t = offers.terms(p, s);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("open", t.open());
        m.put("reason", t.reason());
        m.put("minAmount", t.minAmount());
        m.put("minPct", t.minPct());
        m.put("price", t.price());
        m.put("expiryHours", s.expiryHours());
        m.put("payHours", s.payHours());
        Long uid = auth.userId(request);
        m.put("signedIn", uid != null);
        m.put("own", uid != null && p.get("artist_user_id") instanceof Number a && a.longValue() == uid);
        m.put("mine", uid == null ? null : offers.liveForBuyer(id, uid));
        return ok(m);
    }

    @PostMapping("/api/v1/offers")
    public ResponseEntity<String> create(HttpServletRequest request) throws IOException {
        if (!Csrf.matches(request)) return expired();
        Map<String, Object> b = body(request);
        Row user = auth.user(request);
        if (user == null) {
            Map<String, Object> p = b.get("paintingId") instanceof Number n ? shop.painting(n.longValue()) : null;
            return signIn(p == null ? "/marketplace" : "/marketplace/" + p.get("slug") + "?offer=1#buy");
        }
        if (!(b.get("paintingId") instanceof Number n)) return error(HttpStatus.BAD_REQUEST, "Missing artwork.");
        if (!Boolean.TRUE.equals(b.get("agree"))) {
            return invalid("agree", "Tick the box to confirm you'll pay within the time shown if your offer is accepted.");
        }
        try {
            String email = user.str("email", "");
            if (email.isBlank()) return invalid("amount", "Add an e-mail address to your account first, so the artist's answer can reach you.");
            Map<String, Object> o = offers.create(n.longValue(), user.lng("id"), user.str("name", "Collector"), email, money(b.get("amount")),
                    b.get("message") == null ? null : String.valueOf(b.get("message")));
            return ok(Map.of("offer", o, "message", "Offer sent. We'll e-mail you when the artist answers."));
        } catch (MarketplaceException e) {
            return fail(e);
        }
    }

    @PostMapping("/api/v1/offers/{id}")
    public ResponseEntity<String> buyerAct(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        if (!Csrf.matches(request)) return expired();
        Row user = auth.user(request);
        if (user == null) return signIn("/user/offers");
        Map<String, Object> o = offers.offer(id);
        if (o == null || ((Number) o.get("user_id")).longValue() != user.lng("id")) return error(HttpStatus.NOT_FOUND, "That offer no longer exists.");
        Map<String, Object> b = body(request);
        String action = String.valueOf(b.get("action"));
        if (!List.of("accept", "decline", "withdraw", "reoffer").contains(action)) return error(HttpStatus.BAD_REQUEST, "Unknown action.");
        try {
            Map<String, Object> v = offers.act(id, OfferService.Actor.buyer(user.lng("id"), user.str("name", "Collector")), action, money(b.get("amount")), str(b.get("note")));
            return ok(Map.of("offer", v, "message", switch (action) {
                case "accept" -> "Deal! The piece is held for you. Pay now to make it yours.";
                case "decline" -> "Counter-offer declined.";
                case "withdraw" -> "Offer withdrawn.";
                default -> "New offer sent.";
            }));
        } catch (MarketplaceException e) {
            return fail(e);
        }
    }

    /** Pay for an accepted offer: the original goes into the cart at the agreed price and the checkout pays for it alone. */
    @PostMapping("/api/v1/offers/{id}/pay")
    public ResponseEntity<String> pay(HttpServletRequest request, @PathVariable("id") long id) {
        if (!Csrf.matches(request)) return expired();
        Row user = auth.user(request);
        if (user == null) return signIn("/user/offers");
        Map<String, Object> o = offers.offer(id);
        if (o == null || ((Number) o.get("user_id")).longValue() != user.lng("id")) return error(HttpStatus.NOT_FOUND, "That offer no longer exists.");
        if (!"ACCEPTED".equals(o.get("status"))) return error(HttpStatus.CONFLICT, "This offer can't be paid any more. Refresh to see the latest.");
        try {
            cart.buyNow(request, ((Number) o.get("painting_id")).longValue(), ShopPricing.Selection.of("ORIGINAL", "", "", "NONE", 1));
        } catch (MarketplaceException e) {
            return fail(e);
        }
        return ok(Map.of("redirect", "/purchase/checkout"));
    }

    // ================================================================ student seller API

    @GetMapping("/api/studio/sell/offers")
    public ResponseEntity<String> studentList(HttpServletRequest request) {
        Long uid = auth.userId(request);
        if (uid == null) return error(HttpStatus.UNAUTHORIZED, "Log in to see offers on your paintings.");
        List<Map<String, Object>> list = offers.forStudentSeller(uid);
        offers.markSellerRead(list.stream().map(o -> ((Number) o.get("id")).longValue()).toList());
        return ok(Map.of("offers", list, "serverNow", System.currentTimeMillis()));
    }

    @PostMapping("/api/studio/sell/offers/{id}")
    public ResponseEntity<String> studentAct(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        if (!Csrf.matches(request)) return expired();
        Row user = auth.user(request);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Log in to answer offers.");
        Map<String, Object> o = offers.offer(id);
        Long seller = o == null ? null : OfferService.studentSeller(o);
        if (seller == null || seller != user.lng("id")) return error(HttpStatus.NOT_FOUND, "That offer no longer exists.");
        Map<String, Object> b = body(request);
        String action = String.valueOf(b.get("action"));
        if (!List.of("accept", "counter", "decline", "withdraw").contains(action)) return error(HttpStatus.BAD_REQUEST, "Unknown action.");
        try {
            Map<String, Object> v = offers.act(id, OfferService.Actor.seller(user.lng("id"), user.str("name", "Artist")), action, money(b.get("amount")), str(b.get("note")));
            return ok(Map.of("offer", v, "message", sellerMessage(action)));
        } catch (MarketplaceException e) {
            return fail(e);
        }
    }

    /** The student's per-listing switch: take offers or not, and the lowest offer (% of the price; null = the site default). */
    @PostMapping("/api/studio/sell/listings/{id}/offers")
    public ResponseEntity<String> studentTerms(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        if (!Csrf.matches(request)) return expired();
        Long uid = auth.userId(request);
        if (uid == null) return error(HttpStatus.UNAUTHORIZED, "Log in to change your listing.");
        Map<String, Object> p = shop.painting(id);
        if (p == null || !"STUDENT".equals(String.valueOf(p.get("source"))) || !(p.get("artist_user_id") instanceof Number a) || a.longValue() != uid) {
            return error(HttpStatus.NOT_FOUND, "That listing isn't yours.");
        }
        Map<String, Object> b = body(request);
        return saveTerms(id, b, null);
    }

    // ================================================================ staff API

    @GetMapping("/admin/console/api/offers")
    public ResponseEntity<String> staffList(HttpServletRequest request) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace")) return error(HttpStatus.FORBIDDEN, "You don't have access to marketplace offers.");
        return ok(staffProps(admin));
    }

    public Map<String, Object> staffProps(Row admin) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("offers", offers.forStaff(scope(admin)));
        OfferService.Settings s = offers.settings();
        m.put("settings", Map.of("enabled", s.enabled(), "minPct", s.minPct(), "expiryHours", s.expiryHours(), "payHours", s.payHours(),
                "maxRounds", s.maxRounds(), "dailyLimit", s.dailyLimit()));
        m.put("canSettings", Personas.can(admin, "marketplace.edit"));
        List<Map<String, Object>> paintings = new java.util.ArrayList<>();
        for (Map<String, Object> r : shop.list("select id, title, slug, source, final_price, stock_status, offers_enabled, offer_min_pct, artist_admin_id from paintings"
                + " where stock_status in ('AVAILABLE', 'RESERVED', 'PENDING_REVIEW', 'DRAFT') order by title")) {
            Long sc = scope(admin);
            if (sc != null && !(r.get("artist_admin_id") instanceof Number n && n.longValue() == sc)) continue;
            OfferService.Terms t = offers.terms(r, s);
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", r.get("id"));
            x.put("title", r.get("title"));
            x.put("slug", r.get("slug"));
            x.put("student", "STUDENT".equals(String.valueOf(r.get("source"))));
            x.put("price", r.get("final_price"));
            x.put("status", r.get("stock_status"));
            x.put("enabled", r.get("offers_enabled") == null ? null : ShopRepository.bool(r.get("offers_enabled")));
            x.put("effective", t.enabledForPainting());
            x.put("minPct", r.get("offer_min_pct"));
            x.put("minAmount", t.minAmount());
            paintings.add(x);
        }
        m.put("paintings", paintings);
        m.put("serverNow", System.currentTimeMillis());
        return m;
    }

    @PostMapping("/admin/console/api/offers/{id}")
    public ResponseEntity<String> staffAct(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace")) return error(HttpStatus.FORBIDDEN, "You don't have access to marketplace offers.");
        Map<String, Object> o = offers.offer(id);
        Long sc = scope(admin);
        if (o == null || (sc != null && !(o.get("artist_admin_id") instanceof Number n && n.longValue() == sc))) return error(HttpStatus.NOT_FOUND, "That offer no longer exists.");
        Map<String, Object> b = body(request);
        String action = String.valueOf(b.get("action"));
        if (!List.of("accept", "counter", "decline", "withdraw", "cancel").contains(action)) return error(HttpStatus.BAD_REQUEST, "Unknown action.");
        try {
            Map<String, Object> v = offers.act(id, OfferService.Actor.staff(admin.str("name", "Studio")), action, money(b.get("amount")), str(b.get("note")));
            audit.log(admin, "painting_offer", id, "offer " + action, o.get("title") + " · " + o.get("buyer_name")
                    + (b.get("amount") != null && "counter".equals(action) ? " · counter ₹" + b.get("amount") : ""));
            return ok(Map.of("offer", v, "message", sellerMessage(action)));
        } catch (MarketplaceException e) {
            return fail(e);
        }
    }

    @PutMapping("/admin/console/api/offers/settings")
    public ResponseEntity<String> staffSettings(HttpServletRequest request) throws IOException {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace.edit")) return error(HttpStatus.FORBIDDEN, "Only a super admin can change the offer rules.");
        Map<String, Object> b = body(request);
        OfferService.Settings before = offers.settings();
        OfferService.Settings s = new OfferService.Settings(!Boolean.FALSE.equals(b.get("enabled")), intOf(b.get("minPct")), intOf(b.get("expiryHours")),
                intOf(b.get("payHours")), intOf(b.get("maxRounds")), intOf(b.get("dailyLimit")));
        Map<String, String> errors = offers.saveSettings(s);
        if (!errors.isEmpty()) return invalidAll(errors);
        audit.change(admin, "offer_settings", 0, "updated", "Offer rules saved", Map.of("settings", before.toString()), Map.of("settings", s.toString()));
        return ok(Map.of("message", s.enabled() ? "Offer rules saved." : "Offers are paused. Open offers can still be answered.", "settings", staffProps(admin).get("settings")));
    }

    @PutMapping("/admin/console/api/offers/paintings/{id}")
    public ResponseEntity<String> staffPainting(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace")) return error(HttpStatus.FORBIDDEN, "You don't have access to marketplace offers.");
        Map<String, Object> p = shop.painting(id);
        Long sc = scope(admin);
        if (p == null || (sc != null && !(p.get("artist_admin_id") instanceof Number n && n.longValue() == sc))) return error(HttpStatus.NOT_FOUND, "That painting isn't listed.");
        ResponseEntity<String> r = saveTerms(id, body(request), admin);
        return r;
    }

    private ResponseEntity<String> saveTerms(long id, Map<String, Object> b, Row admin) {
        Object en = b.get("enabled");
        Boolean enabled = en instanceof Boolean x ? x : null;
        Integer pct = b.get("minPct") == null || String.valueOf(b.get("minPct")).isBlank() ? null : intOf(b.get("minPct"));
        if (pct != null && (pct < 10 || pct > 99)) return invalid("minPct", "Between 10% and 99% of the price, or leave it empty for the site default.");
        if (admin == null && enabled == null) enabled = false;
        shop.update("update paintings set offers_enabled = ?, offer_min_pct = ?, updated_at = ? where id = ?", enabled, pct, ShopRepository.now(), id);
        if (admin != null) audit.log(admin, "painting", id, "offer terms", "offers " + (enabled == null ? "default" : enabled ? "on" : "off") + (pct == null ? "" : ", from " + pct + "%"));
        Map<String, Object> p = shop.painting(id);
        OfferService.Terms t = offers.terms(p, offers.settings());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", p.get("offers_enabled") == null ? null : ShopRepository.bool(p.get("offers_enabled")));
        m.put("effective", t.enabledForPainting());
        m.put("minPct", p.get("offer_min_pct"));
        m.put("minAmount", t.minAmount());
        m.put("message", t.enabledForPainting() ? "Offers are on for this painting." : "Offers are off for this painting.");
        return ok(m);
    }

    private static Long scope(Row admin) {
        return Personas.can(admin, "marketplace.edit") || Personas.can(admin, "marketplace.publish") ? null : admin.lng("id");
    }

    private static String sellerMessage(String action) {
        return switch (action) {
            case "accept" -> "Offer accepted. The piece is held for the buyer while they pay.";
            case "counter" -> "Counter-offer sent.";
            case "decline" -> "Offer declined.";
            case "withdraw" -> "Counter-offer withdrawn.";
            default -> "Agreed price cancelled; the piece is back on sale.";
        };
    }

    // ================================================================ helpers

    static BigDecimal money(Object o) {
        if (o == null) return null;
        try {
            String s = String.valueOf(o).replace(",", "").replace("₹", "").trim();
            return s.isEmpty() ? null : new BigDecimal(s);
        } catch (NumberFormatException e) {
            return BigDecimal.valueOf(-1);
        }
    }

    private static Integer intOf(Object o) {
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Map<String, Object> body(HttpServletRequest request) throws IOException {
        Map<String, Object> m = json.decodeMap(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        return m == null ? Map.of() : m;
    }

    private ResponseEntity<String> signIn(String back) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of(
                "code", "SIGN_IN", "message", "Log in or join to make an offer.", "redirect", com.artistikcity.web.PurchaseIntent.signIn(back))));
    }

    private ResponseEntity<String> expired() {
        return error(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
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

    private ResponseEntity<String> invalid(String field, String message) {
        return invalidAll(Map.of(field, message));
    }

    private ResponseEntity<String> invalidAll(Map<String, String> errors) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                .body(json.encode(Map.of("message", errors.values().iterator().next(), "errors", errors)));
    }
}
