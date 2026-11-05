package com.artistikcity.shop;

import com.artistikcity.marketplace.MarketplaceException;
import com.artistikcity.marketplace.MarketplaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-item marketplace orders: originals, limited-edition canvases, posters and digital downloads paid together.
 *
 * <h3>Paying</h3>
 * {@link #createPending} snapshots the priced cart into shop_orders / shop_order_items (prices come from the server, never
 * the browser) and gives the buyer's original hold a few extra minutes so a slow UPI or PayPal approval can finish.
 *
 * <h3>Finalising</h3>
 * {@link #finalizeSale} runs once a payment is verified (Razorpay signature or webhook, PayPal capture or webhook) and is
 * idempotent. In one SERIALIZABLE transaction it marks each original SOLD (same rules as the single-original checkout: a
 * late payment still wins if nobody else holds or bought the piece), numbers the canvas editions, and issues download
 * links. A line that can no longer be fulfilled is flagged REFUND_REQUIRED and its amount added to {@code refund_due_inr};
 * the rest of the order goes ahead.
 */
@Service
public class ShopOrderService {

    private static final Logger log = LoggerFactory.getLogger(ShopOrderService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    public static final List<String> PAID_STATES = List.of("PAID", "PACKED", "SHIPPED", "DELIVERED");
    public static final int PAYMENT_GRACE_MINUTES = 5;
    public static final int DOWNLOAD_DAYS = 30;
    public static final int DOWNLOAD_LIMIT = 10;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final TransactionTemplate serializable;
    private final ShopRepository repo;
    private final MarketplaceRepository marketRepo;

    public ShopOrderService(JdbcTemplate jdbc, PlatformTransactionManager tm, ShopRepository repo, MarketplaceRepository marketRepo) {
        this.jdbc = jdbc;
        this.repo = repo;
        this.marketRepo = marketRepo;
        this.tx = new TransactionTemplate(tm);
        this.serializable = new TransactionTemplate(tm);
        this.serializable.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    private static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }

    public static String newOrderId() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("AC-S-").append(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)).append('-');
        for (int i = 0; i < 6; i++) sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return sb.toString();
    }

    // ================================================================ create

    public record Buyer(String name, String email, String phone, String line1, String line2, String city, String state, String postal, String country) {
    }

    /**
     * Stores a PAYMENT_PENDING order for the priced cart (all lines must be buyable) and cancels the owner's earlier
     * unpaid attempts. Each original's hold is moved to {@code ownerKey} and topped up for the payment window.
     */
    public void createPending(String orderId, Long userId, String ownerKey, List<String> holderKeys, Buyer b, CartService.Priced priced,
                              String gateway, String currency, BigDecimal amount) {
        tx.executeWithoutResult(s -> {
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("update shop_orders set status = 'CANCELLED', updated_at = ? where owner_key = ? and status = 'PAYMENT_PENDING'", ts(now), ownerKey);
            jdbc.update("insert into shop_orders (order_id, user_id, owner_key, buyer_name, buyer_email, buyer_phone, address_line1, address_line2, city, state,"
                            + " postal_code, country, needs_shipping, gateway, currency, amount, subtotal_inr, discount_inr, shipping_inr, total_inr, coupon_code,"
                            + " status, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PAYMENT_PENDING', ?, ?)",
                    orderId, userId, ownerKey, b.name(), b.email(), blank(b.phone()), blank(b.line1()), blank(b.line2()), blank(b.city()), blank(b.state()),
                    blank(b.postal()), b.country(), priced.needsShipping(), gateway, currency, amount, priced.subtotal(), priced.discount(), priced.shipping(),
                    priced.total(), priced.discount().signum() > 0 && priced.coupon() != null ? priced.coupon().code() : null, ts(now), ts(now));
            for (CartService.PricedLine l : priced.lines()) {
                if (l.itemId() == null || !l.ok()) continue;
                ShopPricing.Quote q = l.quote();
                Map<String, Object> p = l.painting();
                long paintingId = ((Number) p.get("id")).longValue();
                jdbc.update("insert into shop_order_items (order_id, painting_id, format, size_code, finish, frame, quantity, title, artist_name, image_url,"
                                + " variant_label, unit_price_inr, line_total_inr, discount_inr) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        orderId, paintingId, q.selection().format(), q.selection().size(), q.selection().finish(), q.selection().frame(),
                        q.selection().qty(), p.get("title"), p.get("artist_name"), p.get("image_url"), q.label(), q.unitPrice(), q.lineTotal(), l.discount());
                if ("ORIGINAL".equals(q.selection().format()) && !holderKeys.isEmpty()) {
                    List<Object> args = new ArrayList<>(List.of(ownerKey, ts(max(now.plusMinutes(PAYMENT_GRACE_MINUTES), l.holdExpiresAt())), paintingId));
                    args.addAll(holderKeys);
                    jdbc.update("update cart_reservations set session_or_user_id = ?, expires_at = ? where painting_id = ? and session_or_user_id in ("
                            + String.join(",", holderKeys.stream().map(k -> "?").toList()) + ")", args.toArray());
                }
            }
        });
    }

    private static LocalDateTime max(LocalDateTime a, Long epochMillis) {
        if (epochMillis == null) return a;
        LocalDateTime b = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), java.time.ZoneId.systemDefault());
        return b.isAfter(a) ? b : a;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public void setProviderRef(String orderId, String ref) {
        jdbc.update("update shop_orders set provider_ref = ?, updated_at = ? where order_id = ?", ref, ts(LocalDateTime.now()), orderId);
    }

    // ================================================================ read

    public Map<String, Object> order(String orderId) {
        List<Map<String, Object>> r = jdbc.queryForList("select * from shop_orders where order_id = ?", orderId);
        return r.isEmpty() ? null : r.get(0);
    }

    public Map<String, Object> orderByProviderRef(String ref) {
        if (ref == null) return null;
        List<Map<String, Object>> r = jdbc.queryForList("select * from shop_orders where provider_ref = ?", ref);
        return r.isEmpty() ? null : r.get(0);
    }

    public List<Map<String, Object>> items(String orderId) {
        return jdbc.queryForList("select i.*, p.slug from shop_order_items i join paintings p on p.id = i.painting_id where i.order_id = ? order by i.id", orderId);
    }

    // ================================================================ finalise

    public enum Outcome { PAID, PARTIAL, REFUND_REQUIRED, ALREADY_PAID, NOT_FOUND, IGNORED }

    public Outcome finalizeSale(String orderId, String transactionId) {
        Outcome[] result = new Outcome[1];
        List<Long> touched = new ArrayList<>();
        try {
            serializable.executeWithoutResult(s -> {
                touched.clear();
                List<Map<String, Object>> rows = jdbc.queryForList("select * from shop_orders where order_id = ? for update", orderId);
                if (rows.isEmpty()) { result[0] = Outcome.NOT_FOUND; return; }
                Map<String, Object> o = rows.get(0);
                String st = String.valueOf(o.get("status"));
                if (PAID_STATES.contains(st) || "REFUND_REQUIRED".equals(st)) { result[0] = Outcome.ALREADY_PAID; return; }
                if (!List.of("PAYMENT_PENDING", "EXPIRED", "CANCELLED").contains(st)) { result[0] = Outcome.IGNORED; return; }
                String owner = String.valueOf(o.get("owner_key"));
                LocalDateTime now = LocalDateTime.now();
                BigDecimal refund = BigDecimal.ZERO;
                int ok = 0;
                int failed = 0;
                for (Map<String, Object> i : jdbc.queryForList("select * from shop_order_items where order_id = ? order by id", orderId)) {
                    long itemId = ((Number) i.get("id")).longValue();
                    long pid = ((Number) i.get("painting_id")).longValue();
                    int qty = ((Number) i.get("quantity")).intValue();
                    String format = String.valueOf(i.get("format"));
                    boolean done = switch (format) {
                        case "ORIGINAL" -> sellOriginal(pid, owner, orderId, now);
                        case "CANVAS" -> {
                            int n = jdbc.update("update paintings set editions_sold = editions_sold + ?, updated_at = ? where id = ? and editions_sold + ? <= edition_size",
                                    qty, ts(now), pid, qty);
                            if (n > 0) {
                                Integer sold = jdbc.queryForObject("select editions_sold from paintings where id = ?", Integer.class, pid);
                                jdbc.update("update shop_order_items set edition_no = ? where id = ?", (sold == null ? qty : sold) - qty + 1, itemId);
                            }
                            yield n > 0;
                        }
                        case "DIGITAL" -> {
                            jdbc.update("update shop_order_items set download_token = ?, download_expires_at = ? where id = ?",
                                    token(), ts(now.plusDays(DOWNLOAD_DAYS)), itemId);
                            yield true;
                        }
                        default -> true;
                    };
                    if (done) {
                        ok++;
                        jdbc.update("update shop_order_items set status = 'OK' where id = ?", itemId);
                        if ("ORIGINAL".equals(format)) touched.add(pid);
                    } else {
                        failed++;
                        refund = refund.add(((BigDecimal) i.get("line_total_inr")).subtract((BigDecimal) i.get("discount_inr")));
                        jdbc.update("update shop_order_items set status = 'REFUND_REQUIRED' where id = ?", itemId);
                    }
                }
                String next;
                if (ok == 0) {
                    next = "REFUND_REQUIRED";
                    refund = (BigDecimal) o.get("total_inr");
                    result[0] = Outcome.REFUND_REQUIRED;
                } else {
                    next = ShopRepository.bool(o.get("needs_shipping")) ? "PAID" : "DELIVERED";
                    result[0] = failed > 0 ? Outcome.PARTIAL : Outcome.PAID;
                }
                jdbc.update("update shop_orders set status = ?, transaction_id = ?, paid_at = ?, refund_due_inr = ?, updated_at = ? where order_id = ?",
                        next, transactionId, ts(now), refund, ts(now), orderId);
                if (ok > 0 && o.get("coupon_code") != null) {
                    jdbc.update("update shop_coupons set used_count = used_count + 1, updated_at = ? where code = ?", ts(now), o.get("coupon_code"));
                }
            });
        } catch (ConcurrencyFailureException e) {
            log.info("Concurrent finalisation for shop order {} - re-reading", orderId);
            Map<String, Object> o = order(orderId);
            return o != null && !"PAYMENT_PENDING".equals(o.get("status")) && !"EXPIRED".equals(o.get("status")) && !"CANCELLED".equals(o.get("status"))
                    ? Outcome.ALREADY_PAID : finalizeSale(orderId, transactionId);
        }
        for (Long pid : touched) marketRepo.syncStudentListing(pid);
        if (result[0] == Outcome.PAID || result[0] == Outcome.PARTIAL || result[0] == Outcome.REFUND_REQUIRED) {
            Map<String, Object> o = order(orderId);
            repo.clearPurchased(String.valueOf(o.get("owner_key")), items(orderId));
            log.info("Shop order {} finalised: {}", orderId, result[0]);
        }
        return result[0];
    }

    /** Same rules as MarketplaceService.finalizeSale for one original inside a shop order. */
    private boolean sellOriginal(long pid, String owner, String orderId, LocalDateTime now) {
        String pst = jdbc.queryForObject("select stock_status from paintings where id = ?", String.class, pid);
        List<Map<String, Object>> res = jdbc.queryForList("select session_or_user_id, expires_at from cart_reservations where painting_id = ?", pid);
        boolean heldByOther = !res.isEmpty() && !owner.equals(String.valueOf(res.get(0).get("session_or_user_id")))
                && ShopRepository.ldt(res.get(0).get("expires_at")).isAfter(now);
        if (!("AVAILABLE".equals(pst) || "RESERVED".equals(pst)) || heldByOther) return false;
        int sold = jdbc.update("update paintings set stock_status = 'SOLD', sold_at = ?, version = version + 1, updated_at = ? where id = ? and stock_status in ('AVAILABLE', 'RESERVED')",
                ts(now), ts(now), pid);
        if (sold == 0) return false;
        jdbc.update("delete from cart_reservations where painting_id = ?", pid);
        jdbc.update("update marketplace_orders set status = 'CANCELLED', updated_at = ? where painting_id = ? and status = 'PAYMENT_PENDING'", ts(now), pid);
        jdbc.update("update shop_orders o set status = 'CANCELLED', updated_at = ? where o.order_id <> ? and o.status = 'PAYMENT_PENDING'"
                + " and exists (select 1 from shop_order_items i where i.order_id = o.order_id and i.painting_id = ? and i.format = 'ORIGINAL')", ts(now), orderId, pid);
        return true;
    }

    private static String token() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    // ================================================================ downloads

    /** The download item for a token, or a reason it can't be downloaded (as a MarketplaceException). */
    public Map<String, Object> download(String token) {
        if (token == null || !token.matches("[0-9a-f]{48}")) throw new MarketplaceException(404, "NOT_FOUND", "This download link isn't valid.");
        List<Map<String, Object>> r = jdbc.queryForList("select i.*, o.status as order_status, p.hires_path, p.hires_name, p.slug from shop_order_items i"
                + " join shop_orders o on o.order_id = i.order_id join paintings p on p.id = i.painting_id where i.download_token = ?", token);
        if (r.isEmpty()) throw new MarketplaceException(404, "NOT_FOUND", "This download link isn't valid.");
        Map<String, Object> i = r.get(0);
        if (!"OK".equals(i.get("status")) || !PAID_STATES.contains(String.valueOf(i.get("order_status")))) {
            throw new MarketplaceException(410, "GONE", "This download is no longer available. Contact us if you need help.");
        }
        LocalDateTime exp = ShopRepository.ldt(i.get("download_expires_at"));
        if (exp != null && LocalDateTime.now().isAfter(exp)) throw new MarketplaceException(410, "EXPIRED", "This download link has expired. Contact us and we'll send a new one.");
        if (((Number) i.get("download_count")).intValue() >= DOWNLOAD_LIMIT) {
            throw new MarketplaceException(429, "LIMIT", "This file has been downloaded " + DOWNLOAD_LIMIT + " times. Contact us if you need it again.");
        }
        if (i.get("hires_path") == null) throw new MarketplaceException(404, "MISSING", "The file is being prepared. Contact us and we'll send it to you.");
        return i;
    }

    public void countDownload(long itemId) {
        jdbc.update("update shop_order_items set download_count = download_count + 1 where id = ?", itemId);
    }

    // ================================================================ admin

    public static final Map<String, List<String>> ORDER_FLOW = Map.of(
            "PAID", List.of("PACKED", "SHIPPED", "REFUNDED"),
            "PACKED", List.of("SHIPPED", "REFUNDED"),
            "SHIPPED", List.of("DELIVERED"),
            "DELIVERED", List.of("REFUNDED"),
            "REFUND_REQUIRED", List.of("REFUNDED"));

    /** Moves an order along the fulfilment flow. A full refund of a paid order puts sold originals back on sale (or archives them). */
    public Map<String, Object> advance(String orderId, String next, String courier, String tracking, String notes, boolean relist) {
        tx.executeWithoutResult(s -> {
            List<Map<String, Object>> rows = jdbc.queryForList("select status, needs_shipping from shop_orders where order_id = ?", orderId);
            if (rows.isEmpty()) throw new MarketplaceException(404, "NOT_FOUND", "Order not found.");
            String cur = String.valueOf(rows.get(0).get("status"));
            if (!ORDER_FLOW.getOrDefault(cur, List.of()).contains(next)) {
                throw new MarketplaceException(409, "BAD_TRANSITION", "An order that is " + cur.replace('_', ' ').toLowerCase() + " can't move to " + next.replace('_', ' ').toLowerCase() + ".");
            }
            if ("SHIPPED".equals(next) && (tracking == null || tracking.isBlank())) {
                throw new MarketplaceException(422, "TRACKING", "Add the courier tracking number before marking it shipped.");
            }
            LocalDateTime now = LocalDateTime.now();
            int changed = jdbc.update("update shop_orders set status = ?, courier = coalesce(?, courier), tracking_number = coalesce(?, tracking_number),"
                            + " staff_notes = coalesce(?, staff_notes), updated_at = ? where order_id = ? and status = ?",
                    next, blank(courier), blank(tracking), blank(notes), ts(now), orderId, cur);
            if (changed == 0) throw new MarketplaceException(409, "STALE", "Someone else just updated this order. Refresh and try again.");
            if ("REFUNDED".equals(next)) {
                if (!"REFUND_REQUIRED".equals(cur)) {
                    for (Map<String, Object> i : jdbc.queryForList("select painting_id from shop_order_items where order_id = ? and format = 'ORIGINAL' and status = 'OK'", orderId)) {
                        jdbc.update("update paintings set stock_status = ?, sold_at = null, version = version + 1, updated_at = ? where id = ? and stock_status = 'SOLD'",
                                relist ? "AVAILABLE" : "ARCHIVED", ts(now), i.get("painting_id"));
                        marketRepo.syncStudentListing(((Number) i.get("painting_id")).longValue());
                    }
                }
                jdbc.update("update shop_order_items set status = 'REFUNDED', download_token = null where order_id = ?", orderId);
                jdbc.update("update shop_orders set refund_due_inr = 0 where order_id = ?", orderId);
            }
        });
        return order(orderId);
    }

    /** Records that the refund for the lines that couldn't be fulfilled has been paid back. */
    public Map<String, Object> settlePartialRefund(String orderId, String notes) {
        int n = jdbc.update("update shop_orders set refund_due_inr = 0, staff_notes = coalesce(?, staff_notes), updated_at = ? where order_id = ? and refund_due_inr > 0",
                blank(notes), ts(LocalDateTime.now()), orderId);
        if (n == 0) throw new MarketplaceException(409, "NOTHING_DUE", "There's no refund due on this order.");
        jdbc.update("update shop_order_items set status = 'REFUNDED' where order_id = ? and status = 'REFUND_REQUIRED'", orderId);
        return order(orderId);
    }

    /** Unpaid attempts older than a day are closed (a late verified payment still completes them). */
    @Scheduled(cron = "0 7 * * * *")
    public void expireAbandoned() {
        int n = jdbc.update("update shop_orders set status = 'EXPIRED', updated_at = ? where status = 'PAYMENT_PENDING' and created_at < ?",
                ts(LocalDateTime.now()), ts(LocalDateTime.now().minusDays(1)));
        if (n > 0) log.info("Expired {} abandoned shop order(s)", n);
    }

    // ================================================================ views

    public static Map<String, Object> view(Map<String, Object> o, List<Map<String, Object>> items, String baseUrl) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : List.of("order_id", "buyer_name", "buyer_email", "buyer_phone", "address_line1", "address_line2", "city", "state", "postal_code",
                "country", "gateway", "currency", "amount", "subtotal_inr", "discount_inr", "shipping_inr", "total_inr", "refund_due_inr", "coupon_code",
                "status", "courier", "tracking_number", "transaction_id", "staff_notes")) {
            m.put(k, o.get(k));
        }
        m.put("needs_shipping", ShopRepository.bool(o.get("needs_shipping")));
        m.put("paid_at", millis(o.get("paid_at")));
        m.put("created_at", millis(o.get("created_at")));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> i : items) {
            Map<String, Object> x = new LinkedHashMap<>();
            for (String k : List.of("id", "painting_id", "slug", "format", "size_code", "finish", "frame", "quantity", "title", "artist_name", "image_url",
                    "variant_label", "unit_price_inr", "line_total_inr", "discount_inr", "edition_no", "status", "download_count")) {
                x.put(k, i.get(k));
            }
            boolean canDownload = i.get("download_token") != null && "OK".equals(i.get("status")) && PAID_STATES.contains(String.valueOf(o.get("status")));
            x.put("download_url", canDownload && baseUrl != null ? baseUrl + "/shop/download/" + i.get("download_token") : null);
            x.put("download_expires_at", millis(i.get("download_expires_at")));
            list.add(x);
        }
        m.put("items", list);
        return m;
    }

    static Long millis(Object ts) {
        LocalDateTime l = ShopRepository.ldt(ts);
        return l == null ? null : l.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
