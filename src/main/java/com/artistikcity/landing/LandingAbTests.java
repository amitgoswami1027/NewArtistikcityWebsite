package com.artistikcity.landing;

import com.artistikcity.support.Db;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * A/B tests on a landing page: the live version (A) against a variant (B) that is the saved draft at the moment the test
 * starts, stored as a version row. Visitors are split by their anonymous visitor hash ({@code ac_lv} cookie, hashed as in
 * {@link LandingAnalytics#visitorHash}): CRC32("testId:hash") % 100 &lt; split_b means B, so a visitor always sees the
 * same variant and needs no extra cookie. Events written while a test runs carry {@code ab_test_id} / {@code ab_variant}.
 * <p>Ending a test: winner B publishes the variant as a new version (the old live version is replaced and stays in the
 * history for rollback); winner A keeps the live version; "no winner" just stops the split. Publishing, scheduling or
 * rolling back is refused while a test runs; unpublishing or archiving ends it. One running test per page.
 * <p>Goal: PURCHASE for course pages (conversion = buyers / visitors, plus revenue), CTA for information pages.
 * Confidence = two-sided two-proportion z-test (shown once both variants have 30+ visitors).
 * Migration 2026-11-14_landing_ab_tests.sql; without it {@link #ready()} is false and nothing changes.
 */
@Service
public class LandingAbTests {

    public static final int MIN_SPLIT = 10, MAX_SPLIT = 90, MIN_VISITORS = 30;

    private final Db db;
    private final Json json;
    private volatile Boolean ready;
    private volatile long readyAt;

    public LandingAbTests(Db db, Json json) {
        this.db = db;
        this.json = json;
    }

    public boolean ready() {
        if (Boolean.TRUE.equals(ready)) return true;
        if (ready != null && System.currentTimeMillis() - readyAt < 60_000) return false;
        try {
            ready = db.count("select count(*) from information_schema.tables where table_schema = database() and table_name = 'landing_ab_tests'") > 0
                    && db.count("select count(*) from information_schema.columns where table_schema = database() and table_name = 'landing_events' and column_name = 'ab_variant'") > 0;
        } catch (RuntimeException e) {
            ready = false;
        }
        readyAt = System.currentTimeMillis();
        return ready;
    }

    /** The running test of a page, or null. */
    public Row running(long pageId) {
        if (!ready()) return null;
        return db.first("select * from landing_ab_tests where landing_page_id = ? and status = 'RUNNING' order by id desc limit 1", pageId);
    }

    /** 'A' or 'B' for a visitor hash (32 hex chars) in a test. */
    public static String assign(long testId, int splitB, String visitorHash) {
        if (visitorHash == null) return "A";
        CRC32 c = new CRC32();
        c.update((testId + ":" + visitorHash).getBytes(StandardCharsets.UTF_8));
        return c.getValue() % 100 < splitB ? "B" : "A";
    }

    /** What a visitor sees and records: {test, variant} or null when no test runs. */
    public record Arm(Row test, String variant) {
        public long testId() {
            return test.lng("id");
        }
    }

    public Arm arm(long pageId, String visitorHash) {
        Row t = running(pageId);
        if (t == null || visitorHash == null) return null;
        return new Arm(t, assign(t.lng("id"), t.integer("split_b"), visitorHash));
    }

    /** The test (running, or the one that ran) that covered an order created at {@code at}; for crediting a purchase. */
    public Arm armAt(long pageId, String visitorHash, LocalDateTime at) {
        if (!ready() || visitorHash == null || at == null) return null;
        Row t = db.first("select * from landing_ab_tests where landing_page_id = ? and started_at <= ? and (ended_at is null or ended_at >= ?) order by id desc limit 1",
                pageId, at, at);
        return t == null ? null : new Arm(t, assign(t.lng("id"), t.integer("split_b"), visitorHash));
    }

    /** The published config of the variant a visitor sees (null = the normal live config). */
    public Map<String, Object> variantConfig(Arm arm) {
        if (arm == null || !"B".equals(arm.variant())) return null;
        Row v = db.first("select config from landing_page_versions where id = ?", arm.test().get("variant_version_id"));
        return v == null ? null : json.decodeMap(v.str("config"));
    }

    // ------------------------------------------------------------------------------------------------ admin

    /** Starts a test of the saved draft against the live version. Returns errors (empty = started). */
    public Map<String, String> start(Row page, String name, int splitB, Long adminId, LandingConfig.Result draft) {
        Map<String, String> e = new LinkedHashMap<>();
        if (!ready()) {
            e.put("ab", "Apply the database update 2026-11-14_landing_ab_tests.sql first.");
            return e;
        }
        long pageId = page.lng("id");
        if (!"PUBLISHED".equals(page.str("status")) || page.get("published_version_id") == null) e.put("ab", "Publish the page first: the live version is A.");
        else if (running(pageId) != null) e.put("ab", "A test is already running on this page.");
        if (splitB < MIN_SPLIT || splitB > MAX_SPLIT) e.put("split", "Send between " + MIN_SPLIT + " % and " + MAX_SPLIT + " % of visitors to B.");
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) e.put("name", "Give the test a name, e.g. \"Shorter hero text\".");
        else if (n.length() > 120) e.put("name", "Keep the name under 120 characters.");
        if (!e.isEmpty()) return e;
        if (!draft.ok()) {
            e.putAll(draft.errors());
            return e;
        }
        Row live = db.first("select config, template_key from landing_page_versions where id = ?", page.get("published_version_id"));
        String variantJson = json.encode(draft.config());
        if (live != null && json.encode(json.decodeMap(live.str("config"))).equals(variantJson) && page.str("template_key").equals(live.str("template_key"))) {
            e.put("ab", "The saved draft is the same as the live page. Change something in the draft (and save it) to test it as B.");
            return e;
        }
        LocalDateTime now = LocalDateTime.now();
        int next = (int) db.count("select coalesce(max(version_no), 0) from landing_page_versions where landing_page_id = ?", pageId) + 1;
        long vid = db.insertReturningId("landing_page_versions", Row.of("landing_page_id", pageId, "version_no", next, "template_key", page.str("template_key"),
                "config", variantJson, "note", LandingCoupons.cut("A/B variant B: " + n, 255), "created_by", adminId, "created_at", now));
        db.insertReturningId("landing_ab_tests", Row.of("landing_page_id", pageId, "name", n, "status", "RUNNING",
                "goal", LandingPages.isSite(page) ? "CTA" : "PURCHASE", "split_b", splitB, "control_version_id", page.get("published_version_id"),
                "variant_version_id", vid, "started_by", adminId, "started_at", now, "created_at", now));
        return e;
    }

    /** Ends the running test; the caller publishes B when B won. */
    public Row end(long pageId, String winner, Long adminId) {
        Row t = running(pageId);
        if (t == null) return null;
        db.update("update landing_ab_tests set status = 'ENDED', winner = ?, ended_by = ?, ended_at = ? where id = ? and status = 'RUNNING'",
                winner, adminId, LocalDateTime.now(), t.get("id"));
        return t;
    }

    /** Ends a running test without a winner (page unpublished / archived). */
    public void stopFor(long pageId) {
        if (ready()) end(pageId, null, null);
    }

    /** Per-variant figures of a test. */
    public Map<String, Object> stats(Row t) {
        long id = t.lng("id");
        Map<String, Map<String, Object>> by = new LinkedHashMap<>();
        for (String v : List.of("A", "B")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("visitors", 0L);
            m.put("cta", 0L);
            m.put("checkouts", 0L);
            m.put("purchases", 0L);
            m.put("revenue", 0.0);
            by.put(v, m);
        }
        for (Row r : db.select("select ab_variant, count(distinct case when event = 'VIEW' then visitor end) as visitors,"
                + " count(distinct case when event = 'CTA' then visitor end) as cta, count(distinct case when event = 'CHECKOUT' then visitor end) as checkouts,"
                + " count(distinct case when event = 'PURCHASE' then order_id end) as purchases from landing_events where ab_test_id = ? and ab_variant in ('A', 'B')"
                + " group by ab_variant", id)) {
            Map<String, Object> m = by.get(r.str("ab_variant"));
            m.put("visitors", r.lng("visitors"));
            m.put("cta", r.lng("cta"));
            m.put("checkouts", r.lng("checkouts"));
            m.put("purchases", r.lng("purchases"));
        }
        for (Row r : db.select("select e.ab_variant, coalesce(sum(o.price), 0) as revenue from landing_events e join orders o on o.id = e.order_id"
                + " where e.ab_test_id = ? and e.event = 'PURCHASE' and o.payment_status = 1 and coalesce(o.is_test, 0) = 0 group by e.ab_variant", id)) {
            Map<String, Object> m = by.get(r.str("ab_variant"));
            if (m != null) m.put("revenue", ((Number) r.get("revenue")).doubleValue());
        }
        String goalKey = "CTA".equals(t.str("goal")) ? "cta" : "purchases";
        for (Map<String, Object> m : by.values()) {
            long vis = (Long) m.get("visitors");
            long conv = (Long) m.get(goalKey);
            m.put("conversions", conv);
            m.put("rate", vis == 0 ? 0.0 : Math.round(conv * 10000.0 / vis) / 100.0);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("variants", by);
        long va = (Long) by.get("A").get("visitors"), vb = (Long) by.get("B").get("visitors");
        long ca = (Long) by.get("A").get("conversions"), cb = (Long) by.get("B").get("conversions");
        out.put("enough", va >= MIN_VISITORS && vb >= MIN_VISITORS);
        double conf = confidence(ca, va, cb, vb);
        out.put("confidence", Math.round(conf * 1000) / 10.0);
        String leader = null;
        if (va > 0 && vb > 0) {
            double ra = (double) ca / va, rb = (double) cb / vb;
            if (ra != rb) leader = rb > ra ? "B" : "A";
        }
        out.put("leader", leader);
        double ra = va == 0 ? 0 : (double) ca / va, rb = vb == 0 ? 0 : (double) cb / vb;
        out.put("lift", ra == 0 ? null : Math.round((rb - ra) / ra * 1000) / 10.0);
        return out;
    }

    /** Two-sided confidence (0..1) that the two conversion rates differ (two-proportion z-test). */
    static double confidence(long ca, long na, long cb, long nb) {
        if (na == 0 || nb == 0) return 0;
        double pa = (double) ca / na, pb = (double) cb / nb, p = (double) (ca + cb) / (na + nb);
        double se = Math.sqrt(p * (1 - p) * (1.0 / na + 1.0 / nb));
        if (se == 0) return 0;
        double z = Math.abs(pa - pb) / se;
        return z == 0 ? 0 : Math.max(0, Math.min(1, 2 * phi(z) - 1));
    }

    /** Standard normal CDF (Abramowitz-Stegun 7.1.26 via erf). */
    static double phi(double z) {
        double x = z / Math.sqrt(2), t = 1 / (1 + 0.3275911 * Math.abs(x));
        double erf = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return 0.5 * (1 + (x >= 0 ? erf : -erf));
    }

    /** For the editor: the running test with figures, the earlier tests, and the limits. */
    public Map<String, Object> view(long pageId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ready", ready());
        m.put("minSplit", MIN_SPLIT);
        m.put("maxSplit", MAX_SPLIT);
        m.put("minVisitors", MIN_VISITORS);
        if (!ready()) return m;
        List<Map<String, Object>> tests = new ArrayList<>();
        for (Row t : db.select("select t.*, a.name as started_by_name, e.name as ended_by_name, cv.version_no as control_no, vv.version_no as variant_no"
                + " from landing_ab_tests t left join admins a on a.id = t.started_by left join admins e on e.id = t.ended_by"
                + " left join landing_page_versions cv on cv.id = t.control_version_id left join landing_page_versions vv on vv.id = t.variant_version_id"
                + " where t.landing_page_id = ? order by t.id desc limit 10", pageId)) {
            Map<String, Object> x = new LinkedHashMap<>();
            for (String k : List.of("id", "name", "status", "goal", "split_b", "control_version_id", "variant_version_id", "control_no", "variant_no", "winner",
                    "started_at", "started_by_name", "ended_at", "ended_by_name")) {
                x.put(k, t.get(k));
            }
            x.put("stats", stats(t));
            tests.add(x);
        }
        m.put("running", tests.stream().filter(x -> "RUNNING".equals(x.get("status"))).findFirst().orElse(null));
        m.put("history", tests.stream().filter(x -> !"RUNNING".equals(x.get("status"))).toList());
        return m;
    }
}
