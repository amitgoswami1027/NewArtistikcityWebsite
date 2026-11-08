package com.artistikcity.landing;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-page pricing (landing_pricing) and checkout settings (landing_checkout): reading them, the server-side quote and
 * saving what the admin screens send. The quote is the only place the landing page price is worked out.
 */
@Service
public class LandingCommerce {

    private final Db db;
    private final LandingCoupons coupons;

    public LandingCommerce(Db db, LandingCoupons coupons) {
        this.db = db;
        this.coupons = coupons;
    }

    public LandingPricing.Rules rules(long pageId) {
        Row r = db.first("select * from landing_pricing where landing_page_id = ?", pageId);
        if (r == null) {
            return LandingPricing.Rules.none();
        }
        return new LandingPricing.Rules(d(r.get("list_price")), d(r.get("price")), d(r.get("price_usd")), r.str("promo_label"), d(r.get("promo_price")),
                t(r.get("promo_starts_at")), t(r.get("promo_ends_at")), d(r.get("early_price")), t(r.get("early_ends_at")),
                r.lng("coupons_enabled", 1) == 1, r.str("tax_mode", "INCLUDED"), d(r.get("tax_rate")), r.str("tax_label"));
    }

    /** [inr, usd] list prices of the course itself (Admin > Courses). */
    public BigDecimal[] coursePrices(long courseId) {
        Row p = db.first("select price_inr, price_usd from course_prices where course_id = ? order by id limit 1", courseId);
        BigDecimal inr = p == null || p.get("price_inr") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(p.get("price_inr")));
        BigDecimal usd = p == null || p.get("price_usd") == null ? null : new BigDecimal(String.valueOf(p.get("price_usd")));
        return new BigDecimal[]{inr, usd != null && usd.signum() == 0 ? null : usd};
    }

    /** The quote without a coupon (what the page shows). */
    public LandingPricing.Quote quote(Row page) {
        BigDecimal[] cp = coursePrices(page.lng("course_id"));
        return LandingPricing.quote(rules(page.lng("id")), cp[0], cp[1], LocalDateTime.now(), null);
    }

    public record Priced(LandingPricing.Quote quote, String couponError) {
    }

    /** The quote with a coupon checked for this buyer; a coupon that doesn't apply is dropped and explained. */
    public Priced quote(Row page, String coupon, Long userId) {
        BigDecimal[] cp = coursePrices(page.lng("course_id"));
        LandingPricing.Rules rules = rules(page.lng("id"));
        LocalDateTime now = LocalDateTime.now();
        LandingPricing.Quote plain = LandingPricing.quote(rules, cp[0], cp[1], now, null);
        if (coupon == null || coupon.isBlank()) {
            return new Priced(plain, null);
        }
        if (!rules.couponsEnabled()) {
            return new Priced(plain, "Coupons can't be used for this offer.");
        }
        LandingCoupons.Outcome o = coupons.check(coupon, page.lng("id"), page.lng("course_id"), userId, plain.subtotal(), now);
        if (!o.ok()) {
            return new Priced(plain, o.message());
        }
        return new Priced(LandingPricing.quote(rules, cp[0], cp[1], now, o.discount()), null);
    }

    // ------------------------------------------------------------------------------------------------- admin

    public Map<String, Object> pricingView(long pageId) {
        Row r = db.first("select * from landing_pricing where landing_page_id = ?", pageId);
        Map<String, Object> m = new LinkedHashMap<>();
        if (r == null) {
            return m;
        }
        for (String[] k : new String[][]{{"list_price", "listPrice"}, {"price", "price"}, {"price_usd", "priceUsd"}, {"promo_label", "promoLabel"},
                {"promo_price", "promoPrice"}, {"promo_starts_at", "promoStartsAt"}, {"promo_ends_at", "promoEndsAt"}, {"early_price", "earlyPrice"},
                {"early_ends_at", "earlyEndsAt"}, {"tax_mode", "taxMode"}, {"tax_rate", "taxRate"}, {"tax_label", "taxLabel"},
                {"access_duration", "accessDuration"}, {"refund_policy", "refundPolicy"}, {"inclusions", "inclusions"}}) {
            m.put(k[1], r.get(k[0]));
        }
        m.put("couponsEnabled", r.lng("coupons_enabled", 1) == 1);
        return m;
    }

    public Map<String, String> savePricing(long pageId, Map<String, Object> in, long adminId) {
        Map<String, String> e = new LinkedHashMap<>();
        BigDecimal list = money(in, "listPrice", e);
        BigDecimal price = money(in, "price", e);
        BigDecimal usd = money(in, "priceUsd", e);
        BigDecimal promo = money(in, "promoPrice", e);
        BigDecimal early = money(in, "earlyPrice", e);
        LocalDateTime ps = LandingCoupons.dt(in.get("promoStartsAt"));
        LocalDateTime pe = LandingCoupons.dt(in.get("promoEndsAt"));
        LocalDateTime ee = LandingCoupons.dt(in.get("earlyEndsAt"));
        Row page = db.find("landing_pages", pageId);
        BigDecimal base = price != null ? price : coursePrices(page.lng("course_id"))[0];
        if (list != null && list.compareTo(base) < 0) {
            e.put("listPrice", "The original price can't be lower than the price.");
        }
        if (promo != null) {
            if (promo.compareTo(base) >= 0) e.put("promoPrice", "An offer price must be lower than the price.");
            if (pe == null) e.put("promoEndsAt", "An offer needs a real end date (no endless countdowns).");
            if (ps != null && pe != null && !pe.isAfter(ps)) e.put("promoEndsAt", "The end must be after the start.");
        }
        if (early != null) {
            if (early.compareTo(base) >= 0) e.put("earlyPrice", "The early-bird price must be lower than the price.");
            if (ee == null) e.put("earlyEndsAt", "Set when the early-bird price ends.");
        }
        String taxMode = "ADDED".equals(LandingConfig.str(in.get("taxMode"))) ? "ADDED" : "INCLUDED";
        BigDecimal taxRate = LandingCoupons.dec(in.get("taxRate"));
        if (taxRate == null) taxRate = BigDecimal.ZERO;
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.valueOf(50)) > 0) {
            e.put("taxRate", "Between 0 and 50%.");
        }
        String refund = LandingConfig.str(in.get("refundPolicy"));
        if (refund.length() > 1000) e.put("refundPolicy", "At most 1000 characters.");
        String access = LandingConfig.str(in.get("accessDuration"));
        if (access.length() > 120) e.put("accessDuration", "At most 120 characters.");
        List<Object> inc = LandingConfig.list(in.get("inclusions"));
        if (inc.size() > 20) e.put("inclusions", "At most 20 items.");
        if (!e.isEmpty()) {
            return e;
        }
        StringBuilder inclusions = new StringBuilder();
        for (Object o : inc) {
            String s = LandingConfig.str(o);
            if (!s.isEmpty()) inclusions.append(LandingCoupons.cut(s, 160)).append('\n');
        }
        Row values = Row.of("list_price", list, "price", price, "price_usd", usd, "promo_label", LandingCoupons.cut(LandingConfig.str(in.get("promoLabel")), 80),
                "promo_price", promo, "promo_starts_at", promo == null ? null : ps, "promo_ends_at", promo == null ? null : pe,
                "early_price", early, "early_ends_at", early == null ? null : ee, "coupons_enabled", LandingConfig.bool(in.get("couponsEnabled"), true) ? 1 : 0,
                "tax_mode", taxMode, "tax_rate", taxRate, "tax_label", LandingCoupons.cut(LandingConfig.str(in.get("taxLabel")), 40),
                "access_duration", access.isEmpty() ? null : access, "refund_policy", refund.isEmpty() ? null : refund,
                "inclusions", inclusions.length() == 0 ? null : inclusions.toString().trim(), "updated_by", adminId, "updated_at", LocalDateTime.now());
        if (db.updateWhere("landing_pricing", values, "landing_page_id = ?", pageId) == 0) {
            values.put("landing_page_id", pageId);
            db.insertPlain("landing_pricing", values);
        }
        return e;
    }

    public Map<String, Object> checkoutView(long pageId) {
        Row r = db.first("select * from landing_checkout where landing_page_id = ?", pageId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requirePhone", r == null || r.lng("require_phone", 1) == 1);
        m.put("requireAddress", r != null && r.lng("require_address", 0) == 1);
        m.put("requireTerms", r == null || r.lng("require_terms", 1) == 1);
        m.put("requirePrivacy", r != null && r.lng("require_privacy", 0) == 1);
        m.put("showCoupon", r == null || r.lng("show_coupon", 1) == 1);
        m.put("showSummary", r == null || r.lng("show_summary", 1) == 1);
        m.put("successMessage", r == null ? null : r.get("success_message"));
        m.put("successRedirect", r == null ? null : r.get("success_redirect"));
        m.put("failureMessage", r == null ? null : r.get("failure_message"));
        m.put("pendingMessage", r == null ? null : r.get("pending_message"));
        return m;
    }

    public Map<String, String> saveCheckout(long pageId, Map<String, Object> in, long adminId) {
        Map<String, String> e = new LinkedHashMap<>();
        String redirect = LandingConfig.str(in.get("successRedirect"));
        // only a path on this site: an open redirect after payment would be a phishing tool
        if (!redirect.isEmpty() && !(redirect.startsWith("/") && !redirect.startsWith("//") && !redirect.contains("..") && redirect.length() <= 255 && !redirect.contains("\\"))) {
            e.put("successRedirect", "Use a page on this site, like /dashboard or /course/....");
        }
        for (String k : List.of("successMessage", "failureMessage", "pendingMessage")) {
            if (LandingConfig.str(in.get(k)).length() > 500) e.put(k, "At most 500 characters.");
        }
        if (!e.isEmpty()) {
            return e;
        }
        Row values = Row.of("require_phone", flag(in, "requirePhone", true), "require_address", flag(in, "requireAddress", false),
                "require_terms", flag(in, "requireTerms", true), "require_privacy", flag(in, "requirePrivacy", false),
                "show_coupon", flag(in, "showCoupon", true), "show_summary", flag(in, "showSummary", true),
                "success_message", nul(in, "successMessage"), "success_redirect", redirect.isEmpty() ? null : redirect,
                "failure_message", nul(in, "failureMessage"), "pending_message", nul(in, "pendingMessage"), "updated_by", adminId, "updated_at", LocalDateTime.now());
        if (db.updateWhere("landing_checkout", values, "landing_page_id = ?", pageId) == 0) {
            values.put("landing_page_id", pageId);
            db.insertPlain("landing_checkout", values);
        }
        return e;
    }

    private static int flag(Map<String, Object> in, String k, boolean def) {
        return LandingConfig.bool(in.get(k), def) ? 1 : 0;
    }

    private static String nul(Map<String, Object> in, String k) {
        String s = LandingConfig.str(in.get(k));
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal money(Map<String, Object> in, String key, Map<String, String> e) {
        String raw = LandingConfig.str(in.get(key));
        if (raw.isEmpty()) return null;
        BigDecimal v = LandingCoupons.dec(raw);
        if (v == null || v.signum() < 0 || v.compareTo(BigDecimal.valueOf(10_000_000)) > 0) {
            e.put(key, "Enter an amount, like 4999.");
            return null;
        }
        return v;
    }

    static BigDecimal d(Object o) {
        return o == null ? null : new BigDecimal(String.valueOf(o));
    }

    static LocalDateTime t(Object o) {
        return o == null ? null : LandingCoupons.ts(o);
    }
}
