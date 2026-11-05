package com.artistikcity.shop;

import com.artistikcity.admin.Audit;
import com.artistikcity.http.Auth;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.lifecycle.AdminConsoleController;
import com.artistikcity.lifecycle.LifecycleRepository;
import com.artistikcity.lifecycle.Personas;
import com.artistikcity.marketplace.MarketplaceException;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin console, Sales > Art shop (/admin/dashboard/shop). Tabs: orders (multi-item marketplace orders), artist
 * messages, the print price list, coupons, and per-listing shop settings (formats sold, edition size, high-resolution
 * file, artist contact links).
 * <ul>
 *   <li>Everyone with "marketplace" sees orders and messages; instructors only for their own artworks and can reply.</li>
 *   <li>"marketplace.orders" fulfils orders and records refunds.</li>
 *   <li>"marketplace.edit" (super admin) changes prices and coupons; "marketplace.edit" or "marketplace.publish" changes listing shop settings.</li>
 * </ul>
 */
@Controller
public class ShopAdminController {

    static final long MAX_HIRES_BYTES = 200L * 1024 * 1024;

    private final Inertia inertia;
    private final Auth auth;
    private final Json json;
    private final ShopRepository repo;
    private final ShopOrderService orders;
    private final ArtistHubService hub;
    private final LifecycleRepository lifecycle;
    private final Storage storage;
    private final Audit audit;
    private final PurchaseController purchase;

    public ShopAdminController(Inertia inertia, Auth auth, Json json, ShopRepository repo, ShopOrderService orders, ArtistHubService hub,
                               LifecycleRepository lifecycle, Storage storage, Audit audit, PurchaseController purchase) {
        this.inertia = inertia;
        this.auth = auth;
        this.json = json;
        this.repo = repo;
        this.orders = orders;
        this.hub = hub;
        this.lifecycle = lifecycle;
        this.storage = storage;
        this.audit = audit;
        this.purchase = purchase;
    }

    // ================================================================ page

    @GetMapping("/admin/dashboard/shop")
    public ResponseEntity<String> page(HttpServletRequest request) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace")) return inertia.render(request, "Admin/Denied", base(admin));
        Long scope = scope(admin);
        Map<String, Object> p = base(admin);
        p.put("orders", orderList(scope));
        p.put("threads", hub.threads(scope));
        p.put("prices", repo.optionRows());
        p.put("coupons", couponList());
        p.put("listings", listings(scope));
        p.put("can", Map.of(
                "orders", Personas.can(admin, "marketplace.orders"),
                "prices", Personas.can(admin, "marketplace.edit"),
                "listings", Personas.can(admin, "marketplace.edit") || Personas.can(admin, "marketplace.publish")));
        p.put("orderFlow", ShopOrderService.ORDER_FLOW);
        p.put("tab", request.getParameter("tab"));
        p.put("openThread", request.getParameter("thread"));
        return inertia.render(request, "Admin/Shop", p);
    }

    private List<Map<String, Object>> orderList(Long scope) {
        String sql = "select o.* from shop_orders o where o.status <> 'PAYMENT_PENDING'"
                + (scope == null ? "" : " and exists (select 1 from shop_order_items i join paintings p on p.id = i.painting_id where i.order_id = o.order_id and p.artist_admin_id = " + scope + ")")
                + " order by o.created_at desc limit 300";
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> o : repo.list(sql)) {
            out.add(ShopOrderService.view(o, orders.items(String.valueOf(o.get("order_id"))), null));
        }
        return out;
    }

    private List<Map<String, Object>> couponList() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : repo.list("select c.*, p.title as painting_title from shop_coupons c left join paintings p on p.id = c.painting_id order by c.is_active desc, c.created_at desc")) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            m.put("is_active", ShopRepository.bool(r.get("is_active")));
            m.put("starts_at", date(r.get("starts_at")));
            m.put("expires_at", date(r.get("expires_at")));
            m.remove("created_at");
            m.remove("updated_at");
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> listings(Long scope) {
        String sql = "select p.id, p.slug, p.title, p.artist_name, p.stock_status, p.final_price, p.shop_formats, p.edition_size, p.editions_sold, p.hires_name,"
                + " p.hires_path is not null as has_hires, p.artist_whatsapp, p.artist_instagram, p.artist_email, p.height_inches, p.width_inches,"
                + " (select i.image_url from painting_images i where i.painting_id = p.id order by i.is_primary desc, i.sort_order, i.id limit 1) as image_url"
                + " from paintings p where p.stock_status in ('AVAILABLE', 'RESERVED', 'SOLD', 'PENDING_REVIEW', 'DRAFT')"
                + (scope == null ? "" : " and p.artist_admin_id = " + scope) + " order by p.updated_at desc, p.id desc";
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : repo.list(sql)) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            m.put("formats", new ArrayList<>(ShopRepository.formats(r.get("shop_formats"))));
            m.remove("shop_formats");
            m.put("has_hires", ShopRepository.bool(r.get("has_hires")));
            out.add(m);
        }
        return out;
    }

    // ================================================================ orders

    /** JSON {status, courier, tracking, notes, relist} */
    @PostMapping("/admin/console/api/shop/orders/{orderId}")
    public ResponseEntity<String> advance(HttpServletRequest request, @PathVariable("orderId") String orderId) throws IOException {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace.orders")) return error(HttpStatus.FORBIDDEN, "Your role can't update orders.");
        Map<String, Object> b = body(request);
        try {
            Map<String, Object> o = orders.advance(orderId, str(b.get("status")), str(b.get("courier")), str(b.get("tracking")), str(b.get("notes")),
                    Boolean.TRUE.equals(b.get("relist")));
            audit.log(admin, "shop_order", 0, "status " + str(b.get("status")), orderId);
            return ok(ShopOrderService.view(o, orders.items(orderId), null));
        } catch (MarketplaceException e) {
            return fail(e);
        }
    }

    @PostMapping("/admin/console/api/shop/orders/{orderId}/refund-settled")
    public ResponseEntity<String> settled(HttpServletRequest request, @PathVariable("orderId") String orderId) throws IOException {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace.orders")) return error(HttpStatus.FORBIDDEN, "Your role can't record refunds.");
        Map<String, Object> b = body(request);
        try {
            Map<String, Object> o = orders.settlePartialRefund(orderId, str(b.get("notes")));
            audit.log(admin, "shop_order", 0, "partial refund settled", orderId);
            return ok(ShopOrderService.view(o, orders.items(orderId), null));
        } catch (MarketplaceException e) {
            return fail(e);
        }
    }

    // ================================================================ messages

    @GetMapping("/admin/console/api/shop/threads/{id}")
    public ResponseEntity<String> thread(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = auth.admin(request);
        Map<String, Object> t = hub.thread(id);
        if (t == null || !canSeeThread(admin, t)) return error(HttpStatus.NOT_FOUND, "Conversation not found.");
        hub.markReadByStaff(id);
        return ok(Map.of("messages", hub.messages(id), "status", t.get("status")));
    }

    /** JSON {body} */
    @PostMapping("/admin/console/api/shop/threads/{id}/reply")
    public ResponseEntity<String> reply(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        Row admin = auth.admin(request);
        Map<String, Object> t = hub.thread(id);
        if (t == null || !canSeeThread(admin, t)) return error(HttpStatus.NOT_FOUND, "Conversation not found.");
        try {
            hub.reply(id, admin.lng("id"), admin.str("name", "The artist"), str(body(request).get("body")), purchase.siteUrl(request));
        } catch (MarketplaceException e) {
            return fail(e);
        }
        return ok(Map.of("messages", hub.messages(id), "message", "Reply sent. The collector also gets it by e-mail."));
    }

    /** JSON {status: OPEN|CLOSED} */
    @PostMapping("/admin/console/api/shop/threads/{id}/status")
    public ResponseEntity<String> threadStatus(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        Row admin = auth.admin(request);
        Map<String, Object> t = hub.thread(id);
        if (t == null || !canSeeThread(admin, t)) return error(HttpStatus.NOT_FOUND, "Conversation not found.");
        hub.setStatus(id, str(body(request).get("status")));
        return ok(Map.of("ok", true));
    }

    private boolean canSeeThread(Row admin, Map<String, Object> t) {
        if (!Personas.can(admin, "marketplace")) return false;
        Long scope = scope(admin);
        return scope == null || (t.get("artist_admin_id") instanceof Number n && n.longValue() == scope);
    }

    // ================================================================ price list

    /** JSON {rows: [{id, label, long_side_in, price_inr, extra_inr, is_active}]} */
    @PutMapping("/admin/console/api/shop/prices")
    public ResponseEntity<String> prices(HttpServletRequest request) throws IOException {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace.edit")) return error(HttpStatus.FORBIDDEN, "Only a super admin can change prices.");
        Object rows = body(request).get("rows");
        if (!(rows instanceof List<?> list)) return error(HttpStatus.BAD_REQUEST, "Nothing to save.");
        Map<String, String> errors = new LinkedHashMap<>();
        List<Object[]> updates = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> r) || !(r.get("id") instanceof Number id)) continue;
            String key = String.valueOf(id.longValue());
            String label = r.get("label") == null ? "" : String.valueOf(r.get("label")).trim();
            BigDecimal price = decimal(r.get("price_inr"));
            BigDecimal extra = r.get("extra_inr") == null || String.valueOf(r.get("extra_inr")).isBlank() ? BigDecimal.ZERO : decimal(r.get("extra_inr"));
            BigDecimal longSide = r.get("long_side_in") == null || String.valueOf(r.get("long_side_in")).isBlank() ? null : decimal(r.get("long_side_in"));
            if (label.length() < 2 || label.length() > 120) errors.put(key + ".label", "Give it a name (2-120 characters).");
            if (price == null || price.signum() < 0 || price.compareTo(new BigDecimal("10000000")) > 0) errors.put(key + ".price_inr", "Enter a price.");
            if (extra == null || extra.signum() < 0) errors.put(key + ".extra_inr", "Enter 0 or more.");
            if (r.get("long_side_in") != null && !String.valueOf(r.get("long_side_in")).isBlank()
                    && (longSide == null || longSide.compareTo(BigDecimal.valueOf(4)) < 0 || longSide.compareTo(BigDecimal.valueOf(120)) > 0)) {
                errors.put(key + ".long_side_in", "Between 4 and 120 inches.");
            }
            updates.add(new Object[]{label, longSide, price, extra, Boolean.TRUE.equals(r.get("is_active")), Timestamp.valueOf(LocalDateTime.now()), id.longValue()});
        }
        if (!errors.isEmpty()) return invalid(errors);
        for (Object[] u : updates) {
            repo.update("update shop_price_options set label = ?, long_side_in = coalesce(?, long_side_in), price_inr = ?, extra_inr = ?, is_active = ?, updated_at = ? where id = ?", u);
        }
        audit.log(admin, "shop_prices", 0, "price list saved", updates.size() + " rows");
        return ok(Map.of("prices", repo.optionRows(), "message", "Price list saved. New prices apply to carts straight away."));
    }

    // ================================================================ coupons

    @PostMapping("/admin/console/api/shop/coupons")
    public ResponseEntity<String> createCoupon(HttpServletRequest request) throws IOException {
        return saveCoupon(request, null);
    }

    @PutMapping("/admin/console/api/shop/coupons/{id}")
    public ResponseEntity<String> updateCoupon(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        return saveCoupon(request, id);
    }

    @DeleteMapping("/admin/console/api/shop/coupons/{id}")
    public ResponseEntity<String> deleteCoupon(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace.edit")) return error(HttpStatus.FORBIDDEN, "Only a super admin can change coupons.");
        if (repo.count("select count(*) from shop_orders where coupon_code = (select code from shop_coupons where id = ?)", id) > 0) {
            repo.update("update shop_coupons set is_active = 0, updated_at = ? where id = ?", Timestamp.valueOf(LocalDateTime.now()), id);
            return ok(Map.of("coupons", couponList(), "message", "This code was used in orders, so it was switched off instead of deleted."));
        }
        repo.update("delete from shop_coupons where id = ?", id);
        audit.log(admin, "shop_coupon", id, "deleted", null);
        return ok(Map.of("coupons", couponList(), "message", "Coupon deleted."));
    }

    private ResponseEntity<String> saveCoupon(HttpServletRequest request, Long id) throws IOException {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace.edit")) return error(HttpStatus.FORBIDDEN, "Only a super admin can change coupons.");
        Map<String, Object> b = body(request);
        Map<String, String> e = new LinkedHashMap<>();
        String code = str(b.get("code")) == null ? "" : str(b.get("code")).trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9_-]{3,40}")) e.put("code", "3-40 letters, numbers, - or _.");
        else if (repo.count("select count(*) from shop_coupons where code = ? and id <> ?", code, id == null ? -1 : id) > 0) e.put("code", "That code already exists.");
        String type = "FLAT".equals(b.get("discount_type")) ? "FLAT" : "PERCENT";
        BigDecimal value = decimal(b.get("discount_value"));
        if (value == null || value.signum() <= 0) e.put("discount_value", "Enter the discount.");
        else if ("PERCENT".equals(type) && value.compareTo(BigDecimal.valueOf(90)) > 0) e.put("discount_value", "At most 90%.");
        Long paintingId = b.get("painting_id") instanceof Number n ? n.longValue() : null;
        if (paintingId != null && repo.count("select count(*) from paintings where id = ?", paintingId) == 0) e.put("painting_id", "Choose an artwork from the list.");
        String artist = str(b.get("artist_name")) == null || str(b.get("artist_name")).isBlank() ? null : str(b.get("artist_name")).trim();
        BigDecimal min = b.get("min_subtotal") == null || String.valueOf(b.get("min_subtotal")).isBlank() ? null : decimal(b.get("min_subtotal"));
        Integer maxUses = null;
        if (b.get("max_uses") != null && !String.valueOf(b.get("max_uses")).isBlank()) {
            try { maxUses = Integer.parseInt(String.valueOf(b.get("max_uses")).replaceAll("\\.0+$", "")); } catch (NumberFormatException x) { e.put("max_uses", "Enter a whole number."); }
            if (maxUses != null && maxUses < 1) e.put("max_uses", "At least 1, or leave empty for unlimited.");
        }
        LocalDateTime starts = day(b.get("starts_at"), false, e, "starts_at");
        LocalDateTime expires = day(b.get("expires_at"), true, e, "expires_at");
        if (starts != null && expires != null && expires.isBefore(starts)) e.put("expires_at", "Ends before it starts.");
        String description = str(b.get("description")) == null ? null : str(b.get("description")).trim();
        if (description != null && description.length() > 255) e.put("description", "255 characters at most.");
        if (!e.isEmpty()) return invalid(e);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        Object[] args = {code, description == null || description.isEmpty() ? null : description, type, value, paintingId, artist, min, maxUses,
                starts == null ? null : Timestamp.valueOf(starts), expires == null ? null : Timestamp.valueOf(expires), !Boolean.FALSE.equals(b.get("is_active")), now};
        if (id == null) {
            Object[] a = java.util.Arrays.copyOf(args, args.length + 1);
            a[args.length] = now;
            repo.update("insert into shop_coupons (code, description, discount_type, discount_value, painting_id, artist_name, min_subtotal, max_uses,"
                    + " starts_at, expires_at, is_active, updated_at, created_at, used_count) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)", a);
        } else {
            Object[] a = java.util.Arrays.copyOf(args, args.length + 1);
            a[args.length] = id;
            if (repo.update("update shop_coupons set code = ?, description = ?, discount_type = ?, discount_value = ?, painting_id = ?, artist_name = ?,"
                    + " min_subtotal = ?, max_uses = ?, starts_at = ?, expires_at = ?, is_active = ?, updated_at = ? where id = ?", a) == 0) {
                return error(HttpStatus.NOT_FOUND, "Coupon not found.");
            }
        }
        audit.log(admin, "shop_coupon", id == null ? 0 : id, id == null ? "created" : "updated", code);
        return ok(Map.of("coupons", couponList(), "message", id == null ? "Coupon " + code + " created." : "Coupon saved."));
    }

    private static LocalDateTime day(Object v, boolean endOfDay, Map<String, String> e, String field) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(String.valueOf(v).trim().substring(0, Math.min(10, String.valueOf(v).trim().length())));
            return endOfDay ? d.atTime(23, 59, 59) : d.atStartOfDay();
        } catch (RuntimeException x) {
            e.put(field, "Pick a date.");
            return null;
        }
    }

    private static String date(Object v) {
        LocalDateTime l = ShopRepository.ldt(v);
        return l == null ? null : l.toLocalDate().toString();
    }

    // ================================================================ listings

    /** JSON {formats: [CANVAS, POSTER, DIGITAL], edition_size, artist_whatsapp, artist_instagram, artist_email} */
    @PutMapping("/admin/console/api/shop/listings/{id}")
    public ResponseEntity<String> listing(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        Row admin = auth.admin(request);
        if (!(Personas.can(admin, "marketplace.edit") || Personas.can(admin, "marketplace.publish"))) {
            return error(HttpStatus.FORBIDDEN, "Your role can't change shop settings.");
        }
        Map<String, Object> p = repo.painting(id);
        if (p == null) return error(HttpStatus.NOT_FOUND, "Listing not found.");
        Map<String, Object> b = body(request);
        Map<String, String> e = new LinkedHashMap<>();
        List<String> formats = new ArrayList<>();
        if (b.get("formats") instanceof List<?> l) {
            for (Object o : l) {
                String f = String.valueOf(o).toUpperCase(Locale.ROOT);
                if (ShopPricing.PRINT_FORMATS.contains(f) && !formats.contains(f)) formats.add(f);
            }
        }
        if (formats.contains("DIGITAL") && p.get("hires_path") == null) e.put("formats", "Upload the high-resolution file before selling digital downloads.");
        int edition = 50;
        try { edition = Integer.parseInt(String.valueOf(b.get("edition_size")).replaceAll("\\.0+$", "")); } catch (NumberFormatException x) { e.put("edition_size", "Enter a whole number."); }
        int sold = p.get("editions_sold") instanceof Number n ? n.intValue() : 0;
        if (edition < Math.max(1, sold) || edition > 1000) e.put("edition_size", "Between " + Math.max(1, sold) + " and 1000 (" + sold + " already sold).");
        String wa = clean(b.get("artist_whatsapp"), 30);
        if (wa != null && ArtistHubService.whatsappNumber(wa) == null) e.put("artist_whatsapp", "Enter a phone number with country code, e.g. +91 98765 43210.");
        String ig = clean(b.get("artist_instagram"), 120);
        if (ig != null && ArtistHubService.handle(ig) == null) e.put("artist_instagram", "Enter an Instagram handle like @artistname.");
        String mail = clean(b.get("artist_email"), 190);
        if (mail != null && !mail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")) e.put("artist_email", "Enter a valid e-mail address.");
        if (!e.isEmpty()) return invalid(e);
        repo.update("update paintings set shop_formats = ?, edition_size = ?, artist_whatsapp = ?, artist_instagram = ?, artist_email = ?, updated_at = ? where id = ?",
                formats.isEmpty() ? null : String.join(",", formats), edition, wa, ig == null ? null : ArtistHubService.handle(ig), mail,
                Timestamp.valueOf(LocalDateTime.now()), id);
        audit.log(admin, "painting", id, "shop settings", String.join(",", formats));
        return ok(Map.of("listings", listings(scope(admin)), "message", "Shop settings saved."));
    }

    /** multipart "file": the high-resolution artwork file for digital downloads (JPEG, PNG, TIFF or PDF, up to 200 MB). */
    @PostMapping("/admin/console/api/shop/listings/{id}/hires")
    public ResponseEntity<String> hires(HttpServletRequest request, @PathVariable("id") long id,
                                        @RequestParam(value = "file", required = false) MultipartFile file) {
        Row admin = auth.admin(request);
        if (!(Personas.can(admin, "marketplace.edit") || Personas.can(admin, "marketplace.publish"))) {
            return error(HttpStatus.FORBIDDEN, "Your role can't upload files.");
        }
        Map<String, Object> p = repo.painting(id);
        if (p == null) return error(HttpStatus.NOT_FOUND, "Listing not found.");
        if (file == null || file.isEmpty()) return invalid(Map.of("file", "Choose the high-resolution file."));
        if (file.getSize() > MAX_HIRES_BYTES) return invalid(Map.of("file", "The file can be up to 200 MB."));
        String ext = hiresType(file);
        if (ext == null) return invalid(Map.of("file", "Upload a JPEG, PNG, TIFF or PDF file."));
        String original = file.getOriginalFilename() == null ? "artwork." + ext : file.getOriginalFilename();
        String rel = "hires/" + id + "/" + (System.currentTimeMillis() / 1000) + "_" + p.get("slug") + "." + ext;
        try (InputStream in = file.getInputStream()) {
            storage.putPrivate(rel, in, file.getSize());
        } catch (IOException | RuntimeException ex) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "The file could not be saved. Try again.");
        }
        Object old = p.get("hires_path");
        repo.update("update paintings set hires_path = ?, hires_name = ?, updated_at = ? where id = ?", rel, original.length() > 250 ? original.substring(0, 250) : original,
                Timestamp.valueOf(LocalDateTime.now()), id);
        if (old != null && !old.equals(rel)) {
            try { storage.deletePrivate(String.valueOf(old)); } catch (RuntimeException ignored) { /* keep going */ }
        }
        audit.log(admin, "painting", id, "high-resolution file", original);
        return ok(Map.of("listings", listings(scope(admin)), "message", "High-resolution file uploaded. You can now sell digital downloads."));
    }

    /** File extension by magic number, or null when it isn't an accepted type. */
    static String hiresType(MultipartFile f) {
        byte[] h = new byte[8];
        try (InputStream in = f.getInputStream()) {
            int n = in.readNBytes(h, 0, 8);
            if (n < 4) return null;
        } catch (IOException e) {
            return null;
        }
        if ((h[0] & 0xff) == 0xFF && (h[1] & 0xff) == 0xD8) return "jpg";
        if ((h[0] & 0xff) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "png";
        if ((h[0] == 'I' && h[1] == 'I' && h[2] == 42 && h[3] == 0) || (h[0] == 'M' && h[1] == 'M' && h[2] == 0 && h[3] == 42)) return "tif";
        if (h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F') return "pdf";
        return null;
    }

    // ================================================================ helpers

    /** Instructors only see their own artworks' orders and messages. */
    private static Long scope(Row admin) {
        return Personas.can(admin, "marketplace.edit") || Personas.can(admin, "marketplace.publish") ? null : admin.lng("id");
    }

    private Map<String, Object> base(Row admin) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("admin", Personas.view(admin));
        p.put("badges", AdminConsoleController.badges(lifecycle));
        return p;
    }

    private static String clean(Object o, int max) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) return null;
        try {
            return new BigDecimal(String.valueOf(o).trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> body(HttpServletRequest request) throws IOException {
        Map<String, Object> m = json.decodeMap(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        return m == null ? Map.of() : m;
    }

    private ResponseEntity<String> invalid(Map<String, String> errors) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                .body(json.encode(Map.of("message", "Please fix the highlighted fields.", "errors", errors)));
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
