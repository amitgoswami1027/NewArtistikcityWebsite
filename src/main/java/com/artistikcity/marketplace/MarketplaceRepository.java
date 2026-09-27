package com.artistikcity.marketplace;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue data for the 1-of-1 marketplace: listings, images, review decisions and the lists
 * the admin console needs. Reservation and payment state changes live in {@link MarketplaceService}.
 */
@Repository
public class MarketplaceRepository {

    /** Statuses a collector can see. RESERVED and SOLD stay visible (with a badge) so links never break. */
    public static final List<String> PUBLIC_STATUSES = List.of("AVAILABLE", "RESERVED", "SOLD");
    /** A listing can be edited only while no collector is holding or has bought it. */
    public static final List<String> EDITABLE = List.of("DRAFT", "PENDING_REVIEW", "AVAILABLE", "ARCHIVED");

    static final String PRIMARY_IMAGE = "(select top 1 i.image_url from painting_images i where i.painting_id = p.id order by i.is_primary desc, i.sort_order, i.id)";
    static final String CARD_COLUMNS = "p.id, p.slug, p.title, p.medium, p.surface, p.subject, p.style_tags, p.height_inches, p.width_inches, p.depth_inches,"
            + " p.year_created, p.is_framed, p.is_signed, p.has_certificate, p.base_price, p.discount_percentage, p.final_price, p.currency,"
            + " p.stock_status, p.source, p.artist_name, p.is_featured, p.published_at, p.sold_at, p.created_at, p.updated_at, p.version,"
            + " p.artist_admin_id, p.artist_user_id, p.submission_id, " + PRIMARY_IMAGE + " as image_url,"
            + " r.expires_at as reserved_until";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public MarketplaceRepository(JdbcTemplate jdbc, PlatformTransactionManager tm) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(tm);
    }

    static Timestamp now() {
        return Timestamp.valueOf(LocalDateTime.now());
    }

    // ================================================================ public catalogue

    public List<Map<String, Object>> publicListings() {
        return jdbc.queryForList("select " + CARD_COLUMNS + " from paintings p left join cart_reservations r on r.painting_id = p.id"
                + " where p.stock_status in ('AVAILABLE', 'RESERVED', 'SOLD')"
                + " order by case p.stock_status when 'AVAILABLE' then 0 when 'RESERVED' then 1 else 2 end, p.is_featured desc, p.published_at desc, p.id desc");
    }

    public Map<String, Object> bySlug(String slug) {
        List<Map<String, Object>> r = jdbc.queryForList("select p.*, " + PRIMARY_IMAGE + " as image_url, r.expires_at as reserved_until, r.session_or_user_id as holder_key"
                + " from paintings p left join cart_reservations r on r.painting_id = p.id where p.slug = ?", slug);
        return r.isEmpty() ? null : r.get(0);
    }

    public Map<String, Object> find(long id) {
        List<Map<String, Object>> r = jdbc.queryForList("select p.*, " + PRIMARY_IMAGE + " as image_url, r.expires_at as reserved_until, r.session_or_user_id as holder_key"
                + " from paintings p left join cart_reservations r on r.painting_id = p.id where p.id = ?", id);
        return r.isEmpty() ? null : r.get(0);
    }

    public List<Map<String, Object>> images(long paintingId) {
        return jdbc.queryForList("select id, image_url, is_primary, sort_order, alt_text from painting_images where painting_id = ? order by is_primary desc, sort_order, id", paintingId);
    }

    public List<Map<String, Object>> related(long paintingId, String artistName, int limit) {
        return jdbc.queryForList("select top " + Math.max(1, Math.min(limit, 12)) + " " + CARD_COLUMNS
                + " from paintings p left join cart_reservations r on r.painting_id = p.id"
                + " where p.id <> ? and p.stock_status in ('AVAILABLE', 'RESERVED')"
                + " order by case when p.artist_name = ? then 0 else 1 end, p.is_featured desc, p.published_at desc", paintingId, artistName);
    }

    // ================================================================ listing writes

    /** Inserts a listing and its images; returns the new id. */
    public long create(ListingInput in, String slug, String status, String source, String artistName,
                       Long artistAdminId, Long artistUserId, Long submissionId) {
        return tx.execute(s -> {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            Timestamp now = now();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement("insert into paintings (slug, title, description, artist_notes, medium, surface, subject, style_tags,"
                        + " height_inches, width_inches, depth_inches, weight_kg, year_created, is_framed, frame_details, is_signed, has_certificate,"
                        + " base_price, discount_percentage, final_price, currency, stock_status, version, source, artist_name, artist_admin_id, artist_user_id,"
                        + " submission_id, is_featured, published_at, created_at, updated_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'INR', ?, 0, ?, ?, ?, ?, ?, 0, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                int i = 1;
                ps.setString(i++, slug);
                ps.setString(i++, in.title());
                ps.setString(i++, in.description());
                ps.setString(i++, in.artistNotes());
                ps.setString(i++, in.medium());
                ps.setString(i++, in.surface());
                ps.setString(i++, in.subject());
                ps.setString(i++, in.styleTags());
                ps.setBigDecimal(i++, in.heightInches());
                ps.setBigDecimal(i++, in.widthInches());
                ps.setBigDecimal(i++, in.depthInches());
                ps.setBigDecimal(i++, in.weightKg());
                ps.setInt(i++, in.yearCreated());
                ps.setBoolean(i++, in.framed());
                ps.setString(i++, in.frameDetails());
                ps.setBoolean(i++, in.signed());
                ps.setBoolean(i++, in.certificate());
                ps.setBigDecimal(i++, in.basePrice());
                ps.setBigDecimal(i++, in.discountPercentage());
                ps.setBigDecimal(i++, in.finalPrice());
                ps.setString(i++, status);
                ps.setString(i++, source);
                ps.setString(i++, artistName);
                ps.setObject(i++, artistAdminId);
                ps.setObject(i++, artistUserId);
                ps.setObject(i++, submissionId);
                ps.setTimestamp(i++, "AVAILABLE".equals(status) ? now : null);
                ps.setTimestamp(i++, now);
                ps.setTimestamp(i, now);
                return ps;
            }, keys);
            long id = keys.getKeys() != null && keys.getKeys().get("id") != null
                    ? ((Number) keys.getKeys().get("id")).longValue() : keys.getKey().longValue();
            replaceImages(id, in.images());
            return id;
        });
    }

    /**
     * Updates a listing's content and pricing and moves it to {@code nextStatus}. Refuses when the piece is
     * held or sold, and uses the version column so two editors can't silently overwrite each other.
     * @return false when the row changed underneath (stale version or wrong status)
     */
    public boolean update(long id, int expectedVersion, ListingInput in, String nextStatus) {
        Boolean ok = tx.execute(s -> {
            String publish = "AVAILABLE".equals(nextStatus) ? " published_at = coalesce(published_at, ?)," : "";
            List<Object> args = new ArrayList<>(List.of(in.title(), in.description()));
            args.add(in.artistNotes());
            args.addAll(java.util.Arrays.asList(in.medium(), in.surface(), in.subject(), in.styleTags(),
                    in.heightInches(), in.widthInches(), in.depthInches(), in.weightKg(), in.yearCreated(), in.framed(), in.frameDetails(), in.signed(),
                    in.certificate(), in.basePrice(), in.discountPercentage(), in.finalPrice(), nextStatus));
            if (!publish.isEmpty()) args.add(now());
            args.addAll(List.of(now(), id, expectedVersion));
            int changed = jdbc.update("update paintings set title = ?, description = ?, artist_notes = ?, medium = ?, surface = ?, subject = ?, style_tags = ?,"
                            + " height_inches = ?, width_inches = ?, depth_inches = ?, weight_kg = ?, year_created = ?, is_framed = ?, frame_details = ?, is_signed = ?,"
                            + " has_certificate = ?, base_price = ?, discount_percentage = ?, final_price = ?, stock_status = ?," + publish
                            + " version = version + 1, updated_at = ? where id = ? and version = ? and stock_status in ('DRAFT', 'PENDING_REVIEW', 'AVAILABLE', 'ARCHIVED')",
                    args.toArray());
            if (changed == 0) {
                return false;
            }
            replaceImages(id, in.images());
            return true;
        });
        if (Boolean.TRUE.equals(ok)) {
            syncStudentListing(id);
        }
        return Boolean.TRUE.equals(ok);
    }

    private void replaceImages(long paintingId, List<ListingInput.ImageInput> images) {
        jdbc.update("delete from painting_images where painting_id = ?", paintingId);
        int order = 0;
        boolean primaryUsed = false;
        for (ListingInput.ImageInput img : images) {
            boolean primary = img.primary() && !primaryUsed;
            primaryUsed |= primary;
            jdbc.update("insert into painting_images (painting_id, image_url, is_primary, sort_order, alt_text) values (?, ?, ?, ?, ?)",
                    paintingId, img.url(), primary, order++, img.alt() == null || img.alt().isEmpty() ? null : img.alt());
        }
    }

    /** Compare-and-set status change for review / publish / archive. Returns false if the row wasn't in {@code from}. */
    public boolean transition(long id, List<String> from, String to, Long reviewerId, String notes) {
        String in = String.join(",", from.stream().map(x -> "'" + x.replace("'", "") + "'").toList());
        StringBuilder sql = new StringBuilder("update paintings set stock_status = ?, version = version + 1, updated_at = ?");
        List<Object> args = new ArrayList<>(List.of(to, now()));
        if ("AVAILABLE".equals(to)) { sql.append(", published_at = coalesce(published_at, ?)"); args.add(now()); }
        if (reviewerId != null) { sql.append(", reviewed_by = ?, reviewed_at = ?"); args.add(reviewerId); args.add(now()); }
        if (notes != null) { sql.append(", review_notes = ?"); args.add(notes); }
        sql.append(" where id = ? and stock_status in (").append(in).append(")");
        args.add(id);
        int changed = jdbc.update(sql.toString(), args.toArray());
        if (changed > 0) {
            syncStudentListing(id);
        }
        return changed > 0;
    }

    public void setFeatured(long id, boolean featured) {
        jdbc.update("update paintings set is_featured = ?, updated_at = ? where id = ?", featured, now(), id);
    }

    /**
     * Keeps the older student-studio table (portfolio_marketplace) in step with a student original, so the
     * studio journey, counters and "for sale" badges stay correct.
     */
    public void syncStudentListing(long paintingId) {
        List<Map<String, Object>> r = jdbc.queryForList("select submission_id, artist_user_id, stock_status, final_price from paintings where id = ? and source = 'STUDENT' and submission_id is not null", paintingId);
        if (r.isEmpty()) return;
        Map<String, Object> p = r.get(0);
        String st = String.valueOf(p.get("stock_status"));
        boolean listed = "AVAILABLE".equals(st) || "RESERVED".equals(st);
        int stock = "SOLD".equals(st) ? 0 : 1;
        int changed = jdbc.update("update portfolio_marketplace set is_listed_for_sale = ?, sale_price = ?, inventory_count = ?, updated_at = ? where submission_id = ?",
                listed, p.get("final_price"), stock, now(), p.get("submission_id"));
        if (changed == 0) {
            jdbc.update("insert into portfolio_marketplace (submission_id, user_id, is_listed_for_sale, sale_price, currency, inventory_count, created_at, updated_at)"
                    + " values (?, ?, ?, ?, 'INR', ?, ?, ?)", p.get("submission_id"), p.get("artist_user_id"), listed, p.get("final_price"), stock, now(), now());
        }
    }

    // ================================================================ admin / persona views

    /** All listings (or only an instructor's own when {@code artistAdminId} is set), with the latest order. */
    public List<Map<String, Object>> adminListings(Long artistAdminId) {
        String sql = "select " + CARD_COLUMNS + ", p.review_notes, p.reviewed_at, r.session_or_user_id as holder_key,"
                + " (select top 1 o.order_id from marketplace_orders o where o.painting_id = p.id and o.status not in ('CANCELLED', 'EXPIRED', 'PAYMENT_PENDING') order by o.created_at desc) as order_id,"
                + " (select count(*) from painting_images i where i.painting_id = p.id) as image_count"
                + " from paintings p left join cart_reservations r on r.painting_id = p.id";
        return artistAdminId == null
                ? jdbc.queryForList(sql + " order by p.updated_at desc, p.id desc")
                : jdbc.queryForList(sql + " where p.artist_admin_id = ? order by p.updated_at desc, p.id desc", artistAdminId);
    }

    public List<Map<String, Object>> activeHolds() {
        return jdbc.queryForList("select r.id, r.painting_id, r.session_or_user_id, r.user_id, r.reserved_at, r.expires_at, p.title, p.slug, p.final_price, p.artist_name,"
                + " " + PRIMARY_IMAGE + " as image_url, u.name as user_name, u.email as user_email,"
                + " (select count(*) from marketplace_orders o where o.painting_id = r.painting_id and o.holder_key = r.session_or_user_id and o.status = 'PAYMENT_PENDING') as payment_started"
                + " from cart_reservations r join paintings p on p.id = r.painting_id left join users u on u.id = r.user_id order by r.expires_at");
    }

    public List<Map<String, Object>> orders(Long artistAdminId) {
        String sql = "select o.*, p.title, p.slug, p.artist_name, p.source, " + PRIMARY_IMAGE + " as image_url"
                + " from marketplace_orders o join paintings p on p.id = o.painting_id where o.status <> 'PAYMENT_PENDING'";
        return artistAdminId == null
                ? jdbc.queryForList(sql + " order by o.created_at desc")
                : jdbc.queryForList(sql + " and p.artist_admin_id = ? order by o.created_at desc", artistAdminId);
    }

    public Map<String, Object> kpis(Long artistAdminId) {
        String scope = artistAdminId == null ? "" : " and p.artist_admin_id = " + artistAdminId;
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("live", count("select count(*) from paintings p where p.stock_status = 'AVAILABLE'" + scope));
        k.put("held", count("select count(*) from paintings p where p.stock_status = 'RESERVED'" + scope));
        k.put("sold", count("select count(*) from paintings p where p.stock_status = 'SOLD'" + scope));
        k.put("review", count("select count(*) from paintings p where p.stock_status = 'PENDING_REVIEW'" + scope));
        k.put("drafts", count("select count(*) from paintings p where p.stock_status = 'DRAFT'" + scope));
        k.put("toShip", count("select count(*) from marketplace_orders o join paintings p on p.id = o.painting_id where o.status in ('PAID', 'PACKED')" + scope));
        k.put("refunds", count("select count(*) from marketplace_orders o join paintings p on p.id = o.painting_id where o.status = 'REFUND_REQUIRED'" + scope));
        Object gmv = jdbc.queryForObject("select coalesce(sum(o.price_inr), 0) from marketplace_orders o join paintings p on p.id = o.painting_id"
                + " where o.status in ('PAID', 'PACKED', 'SHIPPED', 'DELIVERED')" + scope, Object.class);
        k.put("revenueInr", gmv);
        Object live = jdbc.queryForObject("select coalesce(sum(p.final_price), 0) from paintings p where p.stock_status in ('AVAILABLE', 'RESERVED')" + scope, Object.class);
        k.put("inventoryValueInr", live);
        return k;
    }

    // ================================================================ student view

    public List<Map<String, Object>> studentListings(long userId) {
        return jdbc.queryForList("select " + CARD_COLUMNS + ", p.review_notes"
                + " from paintings p left join cart_reservations r on r.painting_id = p.id where p.artist_user_id = ? order by p.updated_at desc", userId);
    }

    public Map<String, Object> listingForSubmission(long submissionId) {
        List<Map<String, Object>> r = jdbc.queryForList("select id, slug, stock_status, version from paintings where submission_id = ? and stock_status <> 'ARCHIVED' order by id desc", submissionId);
        return r.isEmpty() ? null : r.get(0);
    }

    // ================================================================ helpers

    public long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    public List<Map<String, Object>> list(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    /** Groups image rows by painting id (used by the admin list for thumbnails). */
    public Map<Long, List<Map<String, Object>>> imagesFor(List<Long> ids) {
        Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
        if (ids.isEmpty()) return out;
        String in = String.join(",", ids.stream().map(String::valueOf).toList());
        for (Map<String, Object> row : jdbc.queryForList("select painting_id, id, image_url, is_primary, sort_order, alt_text from painting_images where painting_id in (" + in + ") order by is_primary desc, sort_order, id")) {
            out.computeIfAbsent(((Number) row.get("painting_id")).longValue(), k -> new ArrayList<>()).add(row);
        }
        return out;
    }
}
