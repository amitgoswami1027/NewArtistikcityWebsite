package com.artistikcity.shop;

import com.artistikcity.marketplace.MarketplaceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Data access for the shop: the price list, listing facts, carts and coupons. */
@Repository
public class ShopRepository {

    private final JdbcTemplate jdbc;

    public ShopRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static Timestamp now() {
        return Timestamp.valueOf(LocalDateTime.now());
    }

    static LocalDateTime ldt(Object v) {
        if (v instanceof LocalDateTime l) return l;
        if (v instanceof Timestamp t) return t.toLocalDateTime();
        return null;
    }

    static boolean bool(Object o) {
        return Boolean.TRUE.equals(o) || (o instanceof Number n && n.intValue() != 0);
    }

    static BigDecimal dec(Object o) {
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        return o == null ? null : new BigDecimal(String.valueOf(o));
    }

    // ================================================================ price list

    public List<ShopPricing.Option> options() {
        return jdbc.queryForList("select * from shop_price_options order by kind, sort_order, id").stream().map(r -> new ShopPricing.Option(
                String.valueOf(r.get("kind")), String.valueOf(r.get("code")), String.valueOf(r.get("label")), dec(r.get("long_side_in")),
                dec(r.get("price_inr")), dec(r.get("extra_inr")), ((Number) r.get("sort_order")).intValue(), bool(r.get("is_active")))).toList();
    }

    public List<Map<String, Object>> optionRows() {
        return jdbc.queryForList("select id, kind, code, label, long_side_in, price_inr, extra_inr, sort_order, is_active from shop_price_options order by kind, sort_order, id");
    }

    // ================================================================ listings

    /** A listing with its primary image and live hold (same columns as MarketplaceRepository.find). */
    public Map<String, Object> painting(long id) {
        List<Map<String, Object>> r = jdbc.queryForList("select p.*, " + MarketplaceRepository.PRIMARY_IMAGE_SQL + " as image_url, r.expires_at as reserved_until,"
                + " r.session_or_user_id as holder_key from paintings p left join cart_reservations r on r.painting_id = p.id where p.id = ?", id);
        return r.isEmpty() ? null : r.get(0);
    }

    public static Set<String> formats(Object csv) {
        if (csv == null) return Set.of();
        return Arrays.stream(String.valueOf(csv).split(",")).map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(ShopPricing.PRINT_FORMATS::contains).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static ShopPricing.Art art(Map<String, Object> p) {
        return new ShopPricing.Art(((Number) p.get("id")).longValue(), String.valueOf(p.get("title")), String.valueOf(p.get("artist_name")),
                String.valueOf(p.get("surface")), String.valueOf(p.get("stock_status")), dec(p.get("final_price")), dec(p.get("height_inches")),
                dec(p.get("width_inches")), bool(p.get("is_framed")), formats(p.get("shop_formats")),
                p.get("hires_path") != null && !String.valueOf(p.get("hires_path")).isBlank(),
                p.get("edition_size") instanceof Number n ? n.intValue() : 50, p.get("editions_sold") instanceof Number s ? s.intValue() : 0);
    }

    // ================================================================ carts

    public Map<String, Object> cartByOwner(String ownerKey) {
        List<Map<String, Object>> r = jdbc.queryForList("select * from shop_carts where owner_key = ?", ownerKey);
        return r.isEmpty() ? null : r.get(0);
    }

    public long createCart(String ownerKey, Long userId) {
        Timestamp now = now();
        jdbc.update("insert ignore into shop_carts (owner_key, user_id, created_at, updated_at) values (?, ?, ?, ?)", ownerKey, userId, now, now);
        return ((Number) cartByOwner(ownerKey).get("id")).longValue();
    }

    public List<Map<String, Object>> items(long cartId) {
        return jdbc.queryForList("select * from shop_cart_items where cart_id = ? order by created_at, id", cartId);
    }

    public Map<String, Object> item(long cartId, long itemId) {
        List<Map<String, Object>> r = jdbc.queryForList("select * from shop_cart_items where cart_id = ? and id = ?", cartId, itemId);
        return r.isEmpty() ? null : r.get(0);
    }

    /** Adds a line, or raises the quantity of the identical line already in the cart. */
    public void upsertItem(long cartId, long paintingId, ShopPricing.Selection s, int maxQty) {
        Timestamp now = now();
        jdbc.update("insert into shop_cart_items (cart_id, painting_id, format, size_code, finish, frame, quantity, created_at, updated_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?) on duplicate key update quantity = least(?, quantity + values(quantity)), updated_at = values(updated_at)",
                cartId, paintingId, s.format(), s.size(), s.finish(), s.frame(), s.qty(), now, now, Math.max(1, maxQty));
        touch(cartId);
    }

    public void setQuantity(long cartId, long itemId, int qty) {
        jdbc.update("update shop_cart_items set quantity = ?, updated_at = ? where cart_id = ? and id = ?", qty, now(), cartId, itemId);
        touch(cartId);
    }

    public void deleteItem(long cartId, long itemId) {
        jdbc.update("delete from shop_cart_items where cart_id = ? and id = ?", cartId, itemId);
        touch(cartId);
    }

    public void setCoupon(long cartId, String code) {
        jdbc.update("update shop_carts set coupon_code = ?, updated_at = ? where id = ?", code, now(), cartId);
    }

    private void touch(long cartId) {
        jdbc.update("update shop_carts set updated_at = ? where id = ?", now(), cartId);
    }

    /** Moves a guest cart's lines into the signed-in collector's cart (after sign-in), then removes the guest cart. */
    public void merge(long fromCartId, long intoCartId) {
        if (fromCartId == intoCartId) return;
        Timestamp now = now();
        jdbc.update("insert into shop_cart_items (cart_id, painting_id, format, size_code, finish, frame, quantity, created_at, updated_at)"
                + " select * from (select ? as cart_id, painting_id, format, size_code, finish, frame, quantity as q, created_at, ? as updated_at"
                + " from shop_cart_items where cart_id = ?) as src"
                + " on duplicate key update quantity = least(10, shop_cart_items.quantity + src.q), updated_at = src.updated_at", intoCartId, now, fromCartId);
        jdbc.update("update shop_carts t join shop_carts f on f.id = ? set t.coupon_code = coalesce(t.coupon_code, f.coupon_code), t.updated_at = ? where t.id = ?",
                fromCartId, now, intoCartId);
        jdbc.update("delete from shop_carts where id = ?", fromCartId);
    }

    public int count(long cartId) {
        Integer n = jdbc.queryForObject("select coalesce(sum(quantity), 0) from shop_cart_items where cart_id = ?", Integer.class, cartId);
        return n == null ? 0 : n;
    }

    /** Removes the lines an order paid for (and the coupon it used) from the buyer's cart. */
    public void clearPurchased(String ownerKey, List<Map<String, Object>> orderItems) {
        Map<String, Object> cart = cartByOwner(ownerKey);
        if (cart == null) return;
        long cartId = ((Number) cart.get("id")).longValue();
        for (Map<String, Object> i : orderItems) {
            jdbc.update("delete from shop_cart_items where cart_id = ? and painting_id = ? and format = ? and size_code = ? and finish = ? and frame = ?",
                    cartId, i.get("painting_id"), i.get("format"), i.get("size_code"), i.get("finish"), i.get("frame"));
        }
        jdbc.update("update shop_carts set coupon_code = null, updated_at = ? where id = ?", now(), cartId);
    }

    /** Guest carts nobody touched for 30 days. */
    public int deleteStaleGuestCarts() {
        return jdbc.update("delete from shop_carts where user_id is null and updated_at < ?", Timestamp.valueOf(LocalDateTime.now().minusDays(30)));
    }

    // ================================================================ coupons

    public ShopPricing.Coupon coupon(String code) {
        if (code == null || code.isBlank()) return null;
        List<Map<String, Object>> r = jdbc.queryForList("select * from shop_coupons where code = ?", code.trim().toUpperCase(Locale.ROOT));
        return r.isEmpty() ? null : coupon(r.get(0));
    }

    static ShopPricing.Coupon coupon(Map<String, Object> r) {
        return new ShopPricing.Coupon(((Number) r.get("id")).longValue(), String.valueOf(r.get("code")),
                r.get("description") == null ? null : String.valueOf(r.get("description")), String.valueOf(r.get("discount_type")),
                dec(r.get("discount_value")), r.get("painting_id") instanceof Number n ? n.longValue() : null,
                r.get("artist_name") == null ? null : String.valueOf(r.get("artist_name")), dec(r.get("min_subtotal")),
                r.get("max_uses") instanceof Number m ? m.intValue() : null, ((Number) r.get("used_count")).intValue(),
                ldt(r.get("starts_at")), ldt(r.get("expires_at")), bool(r.get("is_active")));
    }

    public List<Map<String, Object>> list(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    public long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }
}
