package com.artistikcity.orders;

import com.artistikcity.http.Auth;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.support.Db;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.view.Views;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * My account > Your orders, across every order system.
 * <ul>
 *   <li>{@code GET /user/orders}: the page (Inertia, {@code Account/Orders}); {@code ?type=} and {@code ?q=} pre-filter it</li>
 *   <li>{@code GET /api/v1/user/orders?type=&q=}: JSON {@code {orders: UnifiedOrder[], counts: {type: n}, total}}, newest first</li>
 *   <li>{@code GET /marketplace/certificates/{orderId}/{item}}: printable certificate of authenticity for a paid original or
 *       numbered canvas (owner, or any admin)</li>
 * </ul>
 */
@Controller
public class UserOrdersController {

    private final Inertia inertia;
    private final Auth auth;
    private final Json json;
    private final Db db;
    private final Views views;
    private final UnifiedOrderService orders;

    public UserOrdersController(Inertia inertia, Auth auth, Json json, Db db, Views views, UnifiedOrderService orders) {
        this.inertia = inertia;
        this.auth = auth;
        this.json = json;
        this.db = db;
        this.views = views;
        this.orders = orders;
    }

    @GetMapping("/user/orders")
    public ResponseEntity<String> page(HttpServletRequest request, @RequestParam(value = "type", required = false) String type,
                                       @RequestParam(value = "q", required = false) String q) {
        Long uid = auth.userId(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.FOUND).header("Location", "/login").build();
        }
        UnifiedOrderService.Result all = orders.list(uid, "all", "");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("orders", all.orders());
        props.put("counts", all.counts());
        props.put("filter", Map.of("type", type == null || !UnifiedOrderService.TYPES.contains(type) ? "all" : type, "q", q == null ? "" : q));
        return inertia.renderUser(request, "Account/Orders", props);
    }

    @GetMapping("/api/v1/user/orders")
    public ResponseEntity<String> api(HttpServletRequest request, @RequestParam(value = "type", required = false) String type,
                                      @RequestParam(value = "q", required = false) String q) {
        Long uid = auth.userId(request);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                    .body(json.encode(Map.of("message", "Sign in to see your orders.")));
        }
        UnifiedOrderService.Result r = orders.list(uid, type, q);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orders", r.orders());
        body.put("counts", r.counts());
        body.put("total", r.total());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header("Cache-Control", "no-store").body(json.encode(body));
    }

    // ================================================================ certificate of authenticity

    @GetMapping("/marketplace/certificates/{orderId}/{item}")
    public ResponseEntity<String> certificate(HttpServletRequest request, @PathVariable("orderId") String orderId, @PathVariable("item") String item) {
        Long uid = auth.userId(request);
        boolean admin = auth.admin(request) != null;
        Row r;
        if ("original".equals(item)) {
            r = db.first("select o.order_id, o.user_id, o.buyer_name, o.paid_at, o.status, 'ORIGINAL' as format, null as edition_no, p.edition_size,"
                    + " p.title, p.artist_name, p.medium, p.surface, p.height_inches, p.width_inches, p.year_created, p.slug, p.has_certificate, 'OK' as item_status,"
                    + " (select i.image_url from painting_images i where i.painting_id = p.id order by i.is_primary desc, i.sort_order, i.id limit 1) as image_url"
                    + " from marketplace_orders o join paintings p on p.id = o.painting_id where o.order_id = ?", orderId);
        } else {
            long itemId;
            try {
                itemId = Long.parseLong(item);
            } catch (NumberFormatException e) {
                return notFound(request);
            }
            r = db.first("select o.order_id, o.user_id, o.buyer_name, o.paid_at, o.status, i.format, i.edition_no, i.quantity, p.edition_size,"
                    + " p.title, p.artist_name, p.medium, p.surface, p.height_inches, p.width_inches, p.year_created, p.slug, p.has_certificate,"
                    + " i.status as item_status, i.variant_label, i.image_url"
                    + " from shop_order_items i join shop_orders o on o.order_id = i.order_id join paintings p on p.id = i.painting_id"
                    + " where o.order_id = ? and i.id = ?", orderId, itemId);
        }
        if (r == null) return notFound(request);
        boolean owner = uid != null && r.get("user_id") instanceof Number n && n.longValue() == uid;
        boolean paid = java.util.List.of("PAID", "PACKED", "SHIPPED", "DELIVERED").contains(r.str("status")) && "OK".equals(r.str("item_status"));
        String format = r.str("format");
        boolean eligible = ("ORIGINAL".equals(format) && Boolean.TRUE.equals(OrderSources.asBool(r.get("has_certificate")))) || "CANVAS".equals(format);
        if (!(owner || admin) || !paid || !eligible) return notFound(request);

        Map<String, Object> m = new LinkedHashMap<>();
        boolean original = "ORIGINAL".equals(format);
        m.put("original", original);
        m.put("title", r.str("title"));
        m.put("artist", r.str("artist_name"));
        m.put("medium", r.str("medium"));
        m.put("surface", r.str("surface") == null ? "" : r.str("surface").toLowerCase(Locale.ROOT));
        m.put("size", OrderSources.plain(r.get("height_inches")) + " × " + OrderSources.plain(r.get("width_inches")) + " in");
        m.put("year", r.get("year_created"));
        m.put("variant", original ? "Original artwork, one of one" : r.str("variant_label"));
        Integer ed = r.get("edition_no") instanceof Number n ? n.intValue() : null;
        int qty = r.get("quantity") instanceof Number q ? q.intValue() : 1;
        m.put("edition", ed == null ? null : (qty > 1 ? "Nos. " + ed + "–" + (ed + qty - 1) : "No. " + ed) + " of " + r.get("edition_size"));
        m.put("collector", r.str("buyer_name"));
        m.put("orderId", r.str("order_id"));
        Object paidAt = r.get("paid_at");
        LocalDateTime pd = paidAt instanceof LocalDateTime l ? l : paidAt instanceof java.sql.Timestamp t ? t.toLocalDateTime() : null;
        m.put("date", pd == null ? "" : pd.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)));
        m.put("image", r.str("image_url"));
        m.put("certificateNo", "AC-COA-" + r.str("order_id").replaceAll("[^A-Z0-9]", "") + ("original".equals(item) ? "" : "-" + item));
        m.put("artworkUrl", "/marketplace/" + r.str("slug"));
        return views.page(request, "certificates/artwork-coa", m);
    }

    private ResponseEntity<String> notFound(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML)
                .body("<!doctype html><meta charset=utf-8><title>Certificate</title><body style=\"font-family:sans-serif;padding:40px\">"
                        + "<h1 style=\"font-size:22px\">Certificate not available</h1><p>Certificates are issued for paid originals and numbered canvas editions, "
                        + "and only the collector can open them.</p><p><a href=\"/user/orders\">Your orders</a></p>");
    }
}
