package com.artistikcity.landing;

import com.artistikcity.support.Db;
import com.artistikcity.support.DbErrors;
import com.artistikcity.support.Row;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coupons for landing pages (Landing Page Management > Coupons & promotions). Every check runs on the server when the
 * buyer applies a code, again when the order is created and again when payment starts:
 * active, inside its dates, for this page / course (or all pages), minimum amount, total uses, uses per buyer and
 * "first course only". A coupon is counted as used only when the payment is verified ({@link #redeem}).
 */
@Service
public class LandingCoupons {

    private final Db db;

    public LandingCoupons(Db db) {
        this.db = db;
    }

    public record Outcome(boolean ok, Long couponId, LandingPricing.Discount discount, String message) {
        static Outcome no(String message) {
            return new Outcome(false, null, null, message);
        }
    }

    public static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase().replaceAll("\\s+", "");
    }

    public Outcome check(String rawCode, long pageId, long courseId, Long userId, BigDecimal subtotal, LocalDateTime now) {
        String code = normalizeCode(rawCode);
        if (code.isEmpty()) {
            return Outcome.no("Enter a code.");
        }
        Row c = db.first("select * from landing_coupons where code = ?", code);
        if (c == null || c.lng("is_active", 0) != 1) {
            return Outcome.no("We don't recognise that code.");
        }
        if (c.get("starts_at") != null && now.isBefore(ts(c.get("starts_at")))) {
            return Outcome.no("This code isn't active yet.");
        }
        if (c.get("expires_at") != null && !now.isBefore(ts(c.get("expires_at")))) {
            return Outcome.no("This code has expired.");
        }
        long id = c.lng("id");
        boolean target = c.lng("all_pages", 0) == 1
                || db.count("select count(*) from landing_coupon_targets where coupon_id = ? and ((target_type = 'PAGE' and target_id = ?) or (target_type = 'COURSE' and target_id = ?))",
                id, pageId, courseId) > 0;
        if (!target) {
            return Outcome.no("This code can't be used for this course.");
        }
        if (c.get("min_amount") != null && subtotal.compareTo(new BigDecimal(String.valueOf(c.get("min_amount")))) < 0) {
            return Outcome.no("This code needs an order of at least ₹" + new BigDecimal(String.valueOf(c.get("min_amount"))).stripTrailingZeros().toPlainString() + ".");
        }
        if (c.get("max_uses") != null && c.lng("used_count", 0) >= c.lng("max_uses")) {
            return Outcome.no("This code has been used up.");
        }
        if (userId != null) {
            if (c.get("per_user_limit") != null
                    && db.count("select count(*) from landing_coupon_redemptions where coupon_id = ? and user_id = ?", id, userId) >= c.lng("per_user_limit")) {
                return Outcome.no("You've already used this code.");
            }
            if (c.lng("first_time_only", 0) == 1 && db.count("select count(*) from orders where user_id = ? and payment_status = 1", userId) > 0) {
                return Outcome.no("This code is for your first course with us.");
            }
        }
        BigDecimal value = new BigDecimal(String.valueOf(c.get("value")));
        return new Outcome(true, id, new LandingPricing.Discount(code, c.str("kind"), value), null);
    }

    /** After a verified payment: one redemption per order (a webhook and the browser arriving together count once). */
    public void redeem(long orderId) {
        // simulated payments (test buyers, the admin simulator) never use up a coupon; development test mode still counts
        Row o = db.first("select id, user_id, coupon_code, discount_amount from orders where id = ? and coupon_code is not null"
                + " and not (coalesce(is_test, 0) = 1 and payment_option = 'Simulated (test)')", orderId);
        if (o == null) {
            return;
        }
        Row c = db.first("select id from landing_coupons where code = ?", o.str("coupon_code"));
        if (c == null) {
            return;
        }
        try {
            db.insertReturningId("landing_coupon_redemptions", Row.of("coupon_id", c.get("id"), "order_id", orderId, "user_id", o.get("user_id"),
                    "discount", o.get("discount_amount") == null ? BigDecimal.ZERO : o.get("discount_amount"), "created_at", LocalDateTime.now()));
            db.update("update landing_coupons set used_count = used_count + 1, updated_at = ? where id = ?", LocalDateTime.now(), c.get("id"));
        } catch (RuntimeException e) {
            if (!DbErrors.isDuplicateKey(e)) {
                throw e;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ admin

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Row c : db.select("select c.*, (select coalesce(sum(r.discount), 0) from landing_coupon_redemptions r where r.coupon_id = c.id) as discount_total"
                + " from landing_coupons c order by c.is_active desc, c.created_at desc")) {
            Map<String, Object> m = new LinkedHashMap<>(c);
            List<Long> pages = new ArrayList<>();
            List<Long> courses = new ArrayList<>();
            for (Row t : db.select("select target_type, target_id from landing_coupon_targets where coupon_id = ?", c.get("id"))) {
                ("PAGE".equals(t.str("target_type")) ? pages : courses).add(t.lng("target_id"));
            }
            m.put("pages", pages);
            m.put("courses", courses);
            out.add(m);
        }
        return out;
    }

    /** Validates and saves; returns field errors (empty = saved). */
    public Map<String, String> save(Long id, Map<String, Object> in, long adminId, long[] savedId) {
        Map<String, String> e = new LinkedHashMap<>();
        String code = normalizeCode(LandingConfig.str(in.get("code")));
        if (!code.matches("^[A-Z0-9_-]{3,40}$")) {
            e.put("code", "Use 3-40 letters, numbers, - or _.");
        } else if (db.count("select count(*) from landing_coupons where code = ? and id <> ?", code, id == null ? 0 : id) > 0) {
            e.put("code", "That code already exists.");
        }
        String kind = LandingConfig.str(in.get("kind"));
        if (!"PERCENT".equals(kind) && !"FLAT".equals(kind)) {
            e.put("kind", "Choose percentage or fixed amount.");
        }
        BigDecimal value = dec(in.get("value"));
        if (value == null || value.signum() <= 0) {
            e.put("value", "Enter a discount above 0.");
        } else if ("PERCENT".equals(kind) && value.compareTo(BigDecimal.valueOf(90)) > 0) {
            e.put("value", "A percentage discount can be at most 90%.");
        }
        BigDecimal min = dec(in.get("minAmount"));
        Integer maxUses = intOrNull(in.get("maxUses"));
        Integer perUser = intOrNull(in.get("perUserLimit"));
        if (maxUses != null && maxUses < 1) e.put("maxUses", "At least 1, or leave empty for no limit.");
        if (perUser != null && perUser < 1) e.put("perUserLimit", "At least 1, or leave empty for no limit.");
        LocalDateTime starts = dt(in.get("startsAt"));
        LocalDateTime expires = dt(in.get("expiresAt"));
        if (starts != null && expires != null && !expires.isAfter(starts)) {
            e.put("expiresAt", "The end must be after the start.");
        }
        boolean all = LandingConfig.bool(in.get("allPages"), false);
        List<Long> pages = ids(in.get("pages"));
        List<Long> courses = ids(in.get("courses"));
        if (!all && pages.isEmpty() && courses.isEmpty()) {
            e.put("pages", "Choose the landing pages or courses this code is for, or tick All landing pages.");
        }
        if (!e.isEmpty()) {
            return e;
        }
        LocalDateTime now = LocalDateTime.now();
        Row values = Row.of("code", code, "description", cut(LandingConfig.str(in.get("description")), 255), "kind", kind, "value", value,
                "campaign", cut(LandingConfig.str(in.get("campaign")), 120), "all_pages", all ? 1 : 0, "min_amount", min, "max_uses", maxUses,
                "per_user_limit", perUser, "first_time_only", LandingConfig.bool(in.get("firstTimeOnly"), false) ? 1 : 0,
                "starts_at", starts, "expires_at", expires, "is_active", LandingConfig.bool(in.get("active"), true) ? 1 : 0, "updated_at", now);
        long cid;
        if (id == null) {
            values.put("created_by", adminId);
            values.put("created_at", now);
            cid = db.insert("landing_coupons", values);
        } else {
            db.updateById("landing_coupons", id, values);
            cid = id;
        }
        db.update("delete from landing_coupon_targets where coupon_id = ?", cid);
        for (Long p : pages) {
            db.insertPlain("landing_coupon_targets", Row.of("coupon_id", cid, "target_type", "PAGE", "target_id", p));
        }
        for (Long c : courses) {
            db.insertPlain("landing_coupon_targets", Row.of("coupon_id", cid, "target_type", "COURSE", "target_id", c));
        }
        savedId[0] = cid;
        return e;
    }

    // ------------------------------------------------------------------------------------------------ parsing

    static BigDecimal dec(Object o) {
        String s = LandingConfig.str(o).replace(",", "");
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer intOrNull(Object o) {
        String s = LandingConfig.str(o);
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s.replace(".0", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** "2026-12-01T10:00" or "2026-12-01 10:00:00" or "2026-12-01". */
    static LocalDateTime dt(Object o) {
        String s = LandingConfig.str(o);
        if (s.isEmpty()) return null;
        try {
            if (s.length() == 10) return java.time.LocalDate.parse(s).atStartOfDay();
            return LocalDateTime.parse(s.replace(' ', 'T').substring(0, Math.min(19, s.length())));
        } catch (Exception e) {
            return null;
        }
    }

    static LocalDateTime ts(Object o) {
        if (o instanceof LocalDateTime l) return l;
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime();
        LocalDateTime d = dt(o);
        return d == null ? LocalDateTime.MIN : d;
    }

    static List<Long> ids(Object o) {
        List<Long> out = new ArrayList<>();
        for (Object x : LandingConfig.list(o)) {
            try {
                long n = Long.parseLong(String.valueOf(x).replace(".0", ""));
                if (n > 0 && !out.contains(n)) out.add(n);
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return out;
    }

    static String cut(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
