package com.artistikcity.marketplace;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shapes database rows into the JSON the React pages read (stable keys, epoch-millis times, derived facts). */
final class MarketplaceViews {

    private static final BigDecimal CM_PER_IN = new BigDecimal("2.54");

    private MarketplaceViews() {
    }

    static Long millis(Object ts) {
        if (ts instanceof Timestamp t) return t.toInstant().toEpochMilli();
        if (ts instanceof java.time.LocalDateTime l) return l.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return null;
    }

    static BigDecimal cm(Object inches) {
        if (!(inches instanceof BigDecimal b)) return null;
        return b.multiply(CM_PER_IN).setScale(1, RoundingMode.HALF_UP);
    }

    static boolean bool(Object o) {
        return Boolean.TRUE.equals(o) || (o instanceof Number n && n.intValue() != 0);
    }

    /** A card in the gallery grid. RESERVED rows whose hold already lapsed are shown as AVAILABLE. */
    static Map<String, Object> card(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : List.of("id", "slug", "title", "medium", "surface", "subject", "style_tags", "height_inches", "width_inches", "depth_inches",
                "year_created", "base_price", "discount_percentage", "final_price", "currency", "source", "artist_name", "image_url", "version")) {
            m.put(k, r.get(k));
        }
        m.put("is_framed", bool(r.get("is_framed")));
        m.put("is_signed", bool(r.get("is_signed")));
        m.put("has_certificate", bool(r.get("has_certificate")));
        m.put("is_featured", bool(r.get("is_featured")));
        Long until = millis(r.get("reserved_until"));
        String status = String.valueOf(r.get("stock_status"));
        if ("RESERVED".equals(status) && (until == null || until < System.currentTimeMillis())) {
            status = "AVAILABLE";
            until = null;
        }
        m.put("stock_status", status);
        m.put("reserved_until", until);
        m.put("published_at", millis(r.get("published_at")));
        m.put("sold_at", millis(r.get("sold_at")));
        m.put("height_cm", cm(r.get("height_inches")));
        m.put("width_cm", cm(r.get("width_inches")));
        m.put("size_class", sizeClass(r.get("height_inches"), r.get("width_inches")));
        m.put("orientation", orientation(r.get("height_inches"), r.get("width_inches")));
        return m;
    }

    /** Everything the product page needs, including story, blueprint and shipping notes. */
    static Map<String, Object> detail(Map<String, Object> r, List<Map<String, Object>> images) {
        Map<String, Object> m = card(r);
        for (String k : List.of("description", "artist_notes", "frame_details", "weight_kg", "artist_admin_id", "artist_user_id", "submission_id")) {
            m.put(k, r.get(k));
        }
        m.put("depth_cm", cm(r.get("depth_inches")));
        m.put("images", images.stream().map(i -> Map.of(
                "id", i.get("id"), "url", i.get("image_url"), "primary", bool(i.get("is_primary")),
                "alt", i.get("alt_text") == null ? String.valueOf(r.get("title")) : i.get("alt_text"))).toList());
        m.put("framing", bool(r.get("is_framed"))
                ? "Framed and ready to hang" + (r.get("frame_details") == null ? "" : " · " + r.get("frame_details"))
                : (depth(r) >= 0.75 ? "Gallery-wrapped, ready to hang without a frame" : "Unframed, shipped flat and protected, ready to frame"));
        m.put("shipping", shipping(r));
        return m;
    }

    private static double depth(Map<String, Object> r) {
        return r.get("depth_inches") instanceof BigDecimal b ? b.doubleValue() : 0;
    }

    private static Map<String, Object> shipping(Map<String, Object> r) {
        double w = r.get("width_inches") instanceof BigDecimal b ? b.doubleValue() : 0;
        double h = r.get("height_inches") instanceof BigDecimal b2 ? b2.doubleValue() : 0;
        double kg = r.get("weight_kg") instanceof BigDecimal b3 ? b3.doubleValue() : Math.max(0.5, w * h / 300.0);
        double packedKg = Math.round((kg + Math.max(1.0, (w + 6) * (h + 6) * 4 / 5000.0)) * 10) / 10.0;
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("packed_weight_kg", packedKg);
        s.put("packed_size_in", Math.round(h + 6) + " × " + Math.round(w + 6) + " × " + Math.max(4, Math.round(depth(r) + 3)));
        s.put("india", "Insured, tracked courier. Ships in 2–3 working days, delivered in 5–8 working days.");
        s.put("international", "Insured, tracked air freight to most countries in 10–15 working days. Import duties and taxes are paid by the buyer on delivery.");
        s.put("compliance", "Packed with corner guards, acid-free glassine and a rigid crate or double-wall box. Shipped with a commercial invoice and certificate of authenticity; no restricted materials.");
        return s;
    }

    static String sizeClass(Object h, Object w) {
        if (!(h instanceof BigDecimal hb) || !(w instanceof BigDecimal wb)) return "medium";
        double longest = Math.max(hb.doubleValue(), wb.doubleValue());
        return longest < 16 ? "small" : longest <= 36 ? "medium" : "large";
    }

    static String orientation(Object h, Object w) {
        if (!(h instanceof BigDecimal hb) || !(w instanceof BigDecimal wb)) return "square";
        double ratio = hb.doubleValue() / wb.doubleValue();
        return ratio > 1.08 ? "portrait" : ratio < 0.92 ? "landscape" : "square";
    }
}
