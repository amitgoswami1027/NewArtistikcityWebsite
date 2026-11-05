package com.artistikcity.shop;

import com.artistikcity.auth.RateLimiter;
import com.artistikcity.http.Auth;
import com.artistikcity.http.Csrf;
import com.artistikcity.marketplace.Holders;
import com.artistikcity.marketplace.MarketplaceException;
import com.artistikcity.support.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cart and "Meet the artist" JSON API (CSRF-checked). Every cart answer is the whole re-priced cart, so the drawer and
 * the checkout always show what the server will charge.
 * <ul>
 *   <li>GET /api/v1/cart?country=, POST /api/v1/cart/items {paintingId, format, size, finish, frame, qty, switch},
 *       PATCH /api/v1/cart/items/{id} {qty}, DELETE /api/v1/cart/items/{id}</li>
 *   <li>POST /api/v1/cart/coupon {code}, DELETE /api/v1/cart/coupon</li>
 *   <li>GET /api/v1/artist-hub/{paintingId}, POST /api/v1/artist-hub/{paintingId}/messages {name, email, body, context}</li>
 * </ul>
 */
@Controller
public class CartController {

    private final Json json;
    private final Auth auth;
    private final CartService cart;
    private final ShopRepository repo;
    private final ArtistHubService hub;
    private final RateLimiter limiter;
    private final PurchaseController purchase;

    public CartController(Json json, Auth auth, CartService cart, ShopRepository repo, ArtistHubService hub, RateLimiter limiter, PurchaseController purchase) {
        this.json = json;
        this.auth = auth;
        this.cart = cart;
        this.repo = repo;
        this.hub = hub;
        this.limiter = limiter;
        this.purchase = purchase;
    }

    @GetMapping("/api/v1/cart")
    public ResponseEntity<String> show(HttpServletRequest request, @RequestParam(value = "country", required = false) String country) {
        return ok(view(request, country));
    }

    @PostMapping("/api/v1/cart/items")
    public ResponseEntity<String> add(HttpServletRequest request) throws IOException {
        if (!Csrf.matches(request)) return expired();
        Map<String, Object> b = body(request);
        if (!(b.get("paintingId") instanceof Number n)) return error(HttpStatus.BAD_REQUEST, "Missing artwork.");
        try {
            cart.add(request, n.longValue(), ShopPricing.Selection.of(b.get("format"), b.get("size"), b.get("finish"), b.get("frame"), b.get("qty")),
                    Boolean.TRUE.equals(b.get("switch")));
        } catch (MarketplaceException e) {
            return fail(e);
        }
        return ok(view(request, null));
    }

    @PatchMapping("/api/v1/cart/items/{id}")
    public ResponseEntity<String> quantity(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        if (!Csrf.matches(request)) return expired();
        Map<String, Object> b = body(request);
        int qty = b.get("qty") instanceof Number n ? n.intValue() : 1;
        try {
            cart.setQuantity(request, id, qty);
        } catch (MarketplaceException e) {
            return fail(e);
        }
        return ok(view(request, null));
    }

    @DeleteMapping("/api/v1/cart/items/{id}")
    public ResponseEntity<String> remove(HttpServletRequest request, @PathVariable("id") long id) {
        if (!Csrf.matches(request)) return expired();
        cart.remove(request, id);
        return ok(view(request, null));
    }

    @PostMapping("/api/v1/cart/coupon")
    public ResponseEntity<String> coupon(HttpServletRequest request) throws IOException {
        if (!Csrf.matches(request)) return expired();
        String key = "coupon:" + Holders.primary(request, auth);
        if (!limiter.hit(key, 15, 600).allowed()) return error(HttpStatus.TOO_MANY_REQUESTS, "Too many tries. Wait a few minutes and try again.");
        Map<String, Object> b = body(request);
        try {
            cart.applyCoupon(request, b.get("code") == null ? "" : String.valueOf(b.get("code")));
        } catch (MarketplaceException e) {
            return fail(e);
        }
        return ok(view(request, b.get("country") == null ? null : String.valueOf(b.get("country"))));
    }

    @DeleteMapping("/api/v1/cart/coupon")
    public ResponseEntity<String> clearCoupon(HttpServletRequest request) {
        if (!Csrf.matches(request)) return expired();
        cart.clearCoupon(request);
        return ok(view(request, null));
    }

    private Map<String, Object> view(HttpServletRequest request, String country) {
        Map<String, Object> m = cart.json(cart.price(request, country, null, null));
        m.put("serverNow", System.currentTimeMillis());
        return m;
    }

    // ================================================================ meet the artist

    @GetMapping("/api/v1/artist-hub/{paintingId}")
    public ResponseEntity<String> conversation(HttpServletRequest request, @PathVariable("paintingId") long paintingId) {
        return ok(hub.conversation(Holders.all(request, auth), paintingId));
    }

    @PostMapping("/api/v1/artist-hub/{paintingId}/messages")
    public ResponseEntity<String> post(HttpServletRequest request, @PathVariable("paintingId") long paintingId) throws IOException {
        if (!Csrf.matches(request)) return expired();
        String owner = cart.ownerKey(request);
        if (!limiter.hit("artist-hub:" + owner, 12, 900).allowed()) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "You've sent a lot of messages. Give the artist a little time to reply.");
        }
        Map<String, Object> p = repo.painting(paintingId);
        if (p == null || !com.artistikcity.marketplace.MarketplaceRepository.PUBLIC_STATUSES.contains(String.valueOf(p.get("stock_status")))) {
            return error(HttpStatus.NOT_FOUND, "This artwork is no longer listed.");
        }
        Map<String, Object> b = body(request);
        com.artistikcity.support.Row user = auth.user(request);
        String name = str(b.get("name"));
        String email = str(b.get("email"));
        if (user != null) {
            if (name == null || name.isBlank()) name = user.str("name", "");
            if (email == null || email.isBlank()) email = user.str("email", "");
        }
        try {
            hub.post(owner, auth.userId(request), p, name, email, str(b.get("body")), str(b.get("context")), purchase.siteUrl(request));
        } catch (MarketplaceException e) {
            return fail(e);
        }
        Map<String, Object> res = new LinkedHashMap<>(hub.conversation(Holders.all(request, auth), paintingId));
        res.put("message", "Sent. The artist will reply here and by e-mail.");
        return ok(res);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    // ================================================================ helpers

    private Map<String, Object> body(HttpServletRequest request) throws IOException {
        Map<String, Object> m = json.decodeMap(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        return m == null ? Map.of() : m;
    }

    private ResponseEntity<String> expired() {
        return error(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
    }

    private ResponseEntity<String> fail(MarketplaceException e) {
        return ResponseEntity.status(e.status()).contentType(MediaType.APPLICATION_JSON).body(json.encode(e.body()));
    }

    private ResponseEntity<String> ok(Object body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header("Cache-Control", "no-store").body(json.encode(body));
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of("message", message)));
    }
}
