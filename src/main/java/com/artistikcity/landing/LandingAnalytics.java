package com.artistikcity.landing;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Funnel events and the reports of Landing Page Management.
 * <p>Events (landing_events): VIEW, CTA, CHECKOUT, METHOD (payment method picked), PAY_START, PURCHASE, PAY_FAIL, LEAD (newsletter box). The visitor
 * is a random first-party cookie, stored only as a hash; no names, e-mails or IPs. A visitor's views of one page count once
 * per 30 minutes and one visitor can't write more than 120 events in 10 minutes.
 * <p>Money figures come from the orders table (landing_page_id), paid orders only, without test orders.
 */
@Service
public class LandingAnalytics {

    public static final Set<String> EVENTS = Set.of("VIEW", "CTA", "CHECKOUT", "METHOD", "PAY_START", "PURCHASE", "PAY_FAIL", "LEAD");

    private final Db db;

    private LandingAbTests abTests;

    @org.springframework.beans.factory.annotation.Autowired
    void setAbTests(LandingAbTests abTests) {
        this.abTests = abTests;
    }

    public LandingAnalytics(Db db) {
        this.db = db;
    }

    public static String visitorHash(String cookie) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(("lp:" + cookie).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h).substring(0, 32);
        } catch (Exception e) {
            return "0".repeat(32);
        }
    }

    public static String device(String userAgent) {
        String ua = userAgent == null ? "" : userAgent.toLowerCase();
        if (ua.contains("ipad") || ua.contains("tablet")) return "tablet";
        if (ua.contains("mobi") || ua.contains("android") || ua.contains("iphone")) return "mobile";
        return "desktop";
    }

    public record Utm(String source, String medium, String campaign) {
        public static Utm none() {
            return new Utm(null, null, null);
        }

        static String clean(String s, int max) {
            if (s == null) return null;
            String c = s.trim().replaceAll("[^A-Za-z0-9 ._+-]", "");
            return c.isEmpty() ? null : c.length() > max ? c.substring(0, max) : c;
        }

        public static Utm of(String source, String medium, String campaign) {
            return new Utm(clean(source, 80), clean(medium, 80), clean(campaign, 120));
        }
    }

    /** Records one event; returns false when it was dropped (unknown, duplicate view, or too many). */
    public boolean record(long pageId, String event, String visitorCookie, String method, String provider, Long orderId, Utm utm, String device) {
        if (!EVENTS.contains(event) || visitorCookie == null || visitorCookie.isBlank()) {
            return false;
        }
        String visitor = visitorHash(visitorCookie);
        LocalDateTime now = LocalDateTime.now();
        if (db.count("select count(*) from landing_events where visitor = ? and created_at > ?", visitor, now.minusMinutes(10)) >= 120) {
            return false;
        }
        if ("VIEW".equals(event) && db.count("select count(*) from landing_events where landing_page_id = ? and visitor = ? and event = 'VIEW' and created_at > ?",
                pageId, visitor, now.minusMinutes(30)) > 0) {
            return false;
        }
        Utm u = utm == null ? Utm.none() : utm;
        Row row = Row.of("landing_page_id", pageId, "event", event, "visitor", visitor,
                "method", method == null ? null : LandingCoupons.cut(method, 30), "provider", provider == null ? null : LandingCoupons.cut(provider, 20),
                "order_id", orderId, "utm_source", u.source(), "utm_medium", u.medium(), "utm_campaign", u.campaign(), "device", device, "created_at", now);
        LandingAbTests.Arm arm = abTests == null ? null : abTests.arm(pageId, visitor);
        if (arm != null) {
            row.put("ab_test_id", arm.testId());
            row.put("ab_variant", arm.variant());
        }
        db.insertReturningId("landing_events", row);
        return true;
    }

    // ------------------------------------------------------------------------------------------------ reports

    public record Range(LocalDateTime from, LocalDateTime to) {
        public static Range of(String preset, String from, String to) {
            LocalDate today = LocalDate.now();
            return switch (preset == null ? "30d" : preset) {
                case "today" -> new Range(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
                case "yesterday" -> new Range(today.minusDays(1).atStartOfDay(), today.atStartOfDay());
                case "7d" -> new Range(today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay());
                case "90d" -> new Range(today.minusDays(89).atStartOfDay(), today.plusDays(1).atStartOfDay());
                case "custom" -> {
                    LocalDate f = parse(from, today.minusDays(29));
                    LocalDate t = parse(to, today);
                    if (t.isBefore(f)) t = f;
                    if (f.plusDays(366).isBefore(t)) f = t.minusDays(366);
                    yield new Range(f.atStartOfDay(), t.plusDays(1).atStartOfDay());
                }
                default -> new Range(today.minusDays(29).atStartOfDay(), today.plusDays(1).atStartOfDay());
            };
        }

        private static LocalDate parse(String s, LocalDate def) {
            try {
                return s == null || s.isBlank() ? def : LocalDate.parse(s.trim());
            } catch (Exception e) {
                return def;
            }
        }
    }

    /** Filters shared by every report; teacherScope limits to the courses of one instructor. */
    public record Filter(Long pageId, Long courseId, Long artistId, String campaign, Long teacherScope) {
        String pageSql(String alias) {
            StringBuilder s = new StringBuilder();
            if (pageId != null) s.append(" and ").append(alias).append(".id = ").append(pageId.longValue());
            if (courseId != null) s.append(" and ").append(alias).append(".course_id = ").append(courseId.longValue());
            Long artist = teacherScope != null ? teacherScope : artistId;
            if (artist != null) s.append(" and ").append(alias).append(".course_id in (select id from courses where admin_id = ").append(artist.longValue()).append(")");
            return s.toString();
        }
    }

    private static String campaignEvents(Filter f, List<Object> params) {
        if (f.campaign() == null || f.campaign().isBlank()) return "";
        params.add(f.campaign());
        return " and e.utm_campaign = ?";
    }

    private static String campaignOrders(Filter f, List<Object> params) {
        if (f.campaign() == null || f.campaign().isBlank()) return "";
        params.add(f.campaign());
        params.add(f.campaign());
        return " and (o.utm_campaign = ? or o.coupon_code in (select code from landing_coupons where campaign = ?))";
    }

    public Map<String, Object> overview(Range r, Filter f) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> pages = new LinkedHashMap<>();
        for (Row row : db.select("select status, count(*) as n from landing_pages p where 1 = 1" + f.pageSql("p") + " group by status")) {
            pages.put(row.str("status"), row.lng("n"));
        }
        out.put("pages", pages);

        List<Object> ep = new ArrayList<>(List.of(r.from(), r.to()));
        String ec = campaignEvents(f, ep);
        Row ev = db.first("select sum(e.event = 'VIEW') as views, count(distinct case when e.event = 'VIEW' then e.visitor end) as visitors,"
                + " count(distinct case when e.event = 'CHECKOUT' then e.visitor end) as checkouts,"
                + " count(distinct case when e.event = 'PURCHASE' then e.visitor end) as buyers,"
                + " sum(e.event = 'CTA') as ctas"
                + " from landing_events e join landing_pages p on p.id = e.landing_page_id where e.created_at >= ? and e.created_at < ?" + ec + f.pageSql("p"), ep.toArray());
        List<Object> op = new ArrayList<>(List.of(r.from(), r.to()));
        String oc = campaignOrders(f, op);
        Row or = db.first("select sum(o.payment_status = 1) as paid, sum(o.payment_status = 2) as failed, sum(o.payment_status in (3, 4)) as refunded,"
                + " sum(o.payment_status = 0 and o.gateway_ref is not null) as pending,"
                + " coalesce(sum(case when o.payment_status = 1 and o.price_type = 'inr' then o.price + 0 end), 0) as revenue_inr,"
                + " coalesce(sum(case when o.payment_status = 1 and o.price_type = 'usd' then o.price + 0 end), 0) as revenue_usd,"
                + " coalesce(sum(case when o.payment_status in (3, 4) and o.price_type = 'inr' then o.price + 0 end), 0) as refunds_inr,"
                + " count(*) as orders"
                + " from orders o join landing_pages p on p.id = o.landing_page_id where o.created_at >= ? and o.created_at < ? and coalesce(o.is_test, 0) = 0"
                + oc + f.pageSql("p"), op.toArray());
        long visitors = ev == null ? 0 : ev.lng("visitors", 0);
        long paid = or == null ? 0 : or.lng("paid", 0);
        long paidInr = db.count("select count(*) from orders o join landing_pages p on p.id = o.landing_page_id where o.payment_status = 1 and o.price_type = 'inr'"
                + " and o.created_at >= ? and o.created_at < ? and coalesce(o.is_test, 0) = 0" + oc + f.pageSql("p"), op.toArray());
        BigDecimal revenue = dec(or == null ? null : or.get("revenue_inr"));
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("views", ev == null ? 0 : ev.lng("views", 0));
        k.put("visitors", visitors);
        k.put("ctaClicks", ev == null ? 0 : ev.lng("ctas", 0));
        k.put("checkouts", ev == null ? 0 : ev.lng("checkouts", 0));
        k.put("enrollments", paid);
        k.put("conversion", pct(paid, visitors));
        k.put("revenueInr", revenue);
        k.put("revenueUsd", dec(or == null ? null : or.get("revenue_usd")));
        k.put("successful", paid);
        k.put("failed", or == null ? 0 : or.lng("failed", 0));
        k.put("pending", or == null ? 0 : or.lng("pending", 0));
        k.put("refunded", or == null ? 0 : or.lng("refunded", 0));
        k.put("refundsInr", dec(or == null ? null : or.get("refunds_inr")));
        k.put("aovInr", paidInr == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(paidInr), 2, RoundingMode.HALF_UP));
        long checkouts = ev == null ? 0 : ev.lng("checkouts", 0);
        long buyers = ev == null ? 0 : ev.lng("buyers", 0);
        k.put("abandoned", Math.max(0, checkouts - buyers));
        k.put("abandonRate", pct(Math.max(0, checkouts - buyers), checkouts));
        long attempts = paid + (or == null ? 0 : or.lng("failed", 0));
        k.put("paymentSuccessRate", pct(paid, attempts));
        out.put("kpis", k);

        out.put("series", series(r, f));
        out.put("byPage", byPage(r, f));
        out.put("byCourse", byCourse(r, f));
        out.put("byCampaign", byCampaign(r, f));
        out.put("byMethod", byMethod(r, f));
        out.put("byProvider", byProvider(r, f));
        out.put("byDevice", byDevice(r, f));
        return out;
    }

    private List<Map<String, Object>> series(Range r, Filter f) {
        Map<String, Map<String, Object>> days = new LinkedHashMap<>();
        for (LocalDate d = r.from().toLocalDate(); d.isBefore(r.to().toLocalDate()); d = d.plusDays(1)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d.toString());
            m.put("visitors", 0L);
            m.put("checkouts", 0L);
            m.put("enrollments", 0L);
            m.put("revenue", BigDecimal.ZERO);
            days.put(d.toString(), m);
        }
        List<Object> ep = new ArrayList<>(List.of(r.from(), r.to()));
        String ec = campaignEvents(f, ep);
        for (Row row : db.select("select date(e.created_at) as d, count(distinct case when e.event = 'VIEW' then e.visitor end) as v,"
                + " count(distinct case when e.event = 'CHECKOUT' then e.visitor end) as c from landing_events e join landing_pages p on p.id = e.landing_page_id"
                + " where e.created_at >= ? and e.created_at < ?" + ec + f.pageSql("p") + " group by date(e.created_at)", ep.toArray())) {
            Map<String, Object> m = days.get(String.valueOf(row.get("d")).substring(0, 10));
            if (m != null) {
                m.put("visitors", row.lng("v", 0));
                m.put("checkouts", row.lng("c", 0));
            }
        }
        List<Object> op = new ArrayList<>(List.of(r.from(), r.to()));
        String oc = campaignOrders(f, op);
        for (Row row : db.select("select date(o.created_at) as d, count(*) as n, coalesce(sum(case when o.price_type = 'inr' then o.price + 0 end), 0) as rev"
                + " from orders o join landing_pages p on p.id = o.landing_page_id where o.payment_status = 1 and coalesce(o.is_test, 0) = 0"
                + " and o.created_at >= ? and o.created_at < ?" + oc + f.pageSql("p") + " group by date(o.created_at)", op.toArray())) {
            Map<String, Object> m = days.get(String.valueOf(row.get("d")).substring(0, 10));
            if (m != null) {
                m.put("enrollments", row.lng("n", 0));
                m.put("revenue", dec(row.get("rev")));
            }
        }
        return new ArrayList<>(days.values());
    }

    private List<Map<String, Object>> byPage(Range r, Filter f) {
        List<Object> p = new ArrayList<>(List.of(r.from(), r.to(), r.from(), r.to(), r.from(), r.to()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Row row : db.select("select p.id, p.name, p.slug, p.status, c.title as course,"
                + " (select count(distinct e.visitor) from landing_events e where e.landing_page_id = p.id and e.event = 'VIEW' and e.created_at >= ? and e.created_at < ?) as visitors,"
                + " (select count(*) from orders o where o.landing_page_id = p.id and o.payment_status = 1 and coalesce(o.is_test, 0) = 0 and o.created_at >= ? and o.created_at < ?) as enrollments,"
                + " (select coalesce(sum(o.price + 0), 0) from orders o where o.landing_page_id = p.id and o.payment_status = 1 and o.price_type = 'inr' and coalesce(o.is_test, 0) = 0"
                + "   and o.created_at >= ? and o.created_at < ?) as revenue"
                + " from landing_pages p left join courses c on c.id = p.course_id where p.status <> 'ARCHIVED'" + f.pageSql("p")
                + " order by revenue desc, enrollments desc, visitors desc limit 10", p.toArray())) {
            Map<String, Object> m = new LinkedHashMap<>(row);
            m.put("revenue", dec(row.get("revenue")));
            m.put("conversion", pct(row.lng("enrollments", 0), row.lng("visitors", 0)));
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> byCourse(Range r, Filter f) {
        List<Object> op = new ArrayList<>(List.of(r.from(), r.to()));
        String oc = campaignOrders(f, op);
        return rows(db.select("select c.id, c.title as course, a.name as artist, count(*) as enrollments, coalesce(sum(case when o.price_type = 'inr' then o.price + 0 end), 0) as revenue"
                + " from orders o join landing_pages p on p.id = o.landing_page_id join courses c on c.id = o.course_id left join admins a on a.id = c.admin_id"
                + " where o.payment_status = 1 and coalesce(o.is_test, 0) = 0 and o.created_at >= ? and o.created_at < ?" + oc + f.pageSql("p")
                + " group by c.id, c.title, a.name order by revenue desc limit 10", op.toArray()));
    }

    private List<Map<String, Object>> byCampaign(Range r, Filter f) {
        List<Object> op = new ArrayList<>(List.of(r.from(), r.to()));
        String oc = campaignOrders(f, op);
        return rows(db.select("select coalesce(nullif(o.utm_campaign, ''), (select lc.campaign from landing_coupons lc where lc.code = o.coupon_code), p.campaign, '(none)') as campaign,"
                + " count(*) as enrollments, coalesce(sum(case when o.price_type = 'inr' then o.price + 0 end), 0) as revenue, coalesce(sum(o.discount_amount), 0) as discounts"
                + " from orders o join landing_pages p on p.id = o.landing_page_id where o.payment_status = 1 and coalesce(o.is_test, 0) = 0"
                + " and o.created_at >= ? and o.created_at < ?" + oc + f.pageSql("p") + " group by 1 order by revenue desc limit 10", op.toArray()));
    }

    /** Per method: how often it was picked, payments started, paid, failed, and the conversion from pick to paid. */
    private List<Map<String, Object>> byMethod(Range r, Filter f) {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        List<Object> ep = new ArrayList<>(List.of(r.from(), r.to()));
        String ec = campaignEvents(f, ep);
        for (Row row : db.select("select e.method, sum(e.event = 'METHOD') as picked, sum(e.event = 'PAY_START') as started, sum(e.event = 'PAY_FAIL') as failed_events"
                + " from landing_events e join landing_pages p on p.id = e.landing_page_id where e.method is not null and e.created_at >= ? and e.created_at < ?" + ec + f.pageSql("p")
                + " group by e.method", ep.toArray())) {
            Map<String, Object> x = blankMethod(row.str("method"));
            x.put("picked", row.lng("picked", 0));
            x.put("started", row.lng("started", 0));
            m.put(row.str("method"), x);
        }
        List<Object> op = new ArrayList<>(List.of(r.from(), r.to()));
        String oc = campaignOrders(f, op);
        for (Row row : db.select("select coalesce(o.payment_method, 'unknown') as method, sum(o.payment_status = 1) as paid, sum(o.payment_status = 2) as failed,"
                + " coalesce(sum(case when o.payment_status = 1 and o.price_type = 'inr' then o.price + 0 end), 0) as revenue"
                + " from orders o join landing_pages p on p.id = o.landing_page_id where coalesce(o.is_test, 0) = 0 and o.created_at >= ? and o.created_at < ?" + oc + f.pageSql("p")
                + " group by 1", op.toArray())) {
            Map<String, Object> x = m.computeIfAbsent(row.str("method"), LandingAnalytics::blankMethod);
            x.put("paid", row.lng("paid", 0));
            x.put("failed", row.lng("failed", 0));
            x.put("revenue", dec(row.get("revenue")));
        }
        for (Map<String, Object> x : m.values()) {
            long paid = (long) x.get("paid");
            long picked = (long) x.get("picked");
            x.put("successRate", pct(paid, paid + (long) x.get("failed")));
            x.put("conversion", pct(paid, picked));
        }
        List<Map<String, Object>> out = new ArrayList<>(m.values());
        out.sort((a, b) -> Long.compare((long) b.get("paid"), (long) a.get("paid")));
        return out;
    }

    private static Map<String, Object> blankMethod(String method) {
        Map<String, Object> x = new LinkedHashMap<>();
        x.put("method", method);
        x.put("picked", 0L);
        x.put("started", 0L);
        x.put("paid", 0L);
        x.put("failed", 0L);
        x.put("revenue", BigDecimal.ZERO);
        return x;
    }

    private List<Map<String, Object>> byProvider(Range r, Filter f) {
        List<Object> op = new ArrayList<>(List.of(r.from(), r.to()));
        String oc = campaignOrders(f, op);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Row row : db.select("select coalesce(o.payment_provider, 'unknown') as provider, sum(o.payment_status = 1) as paid, sum(o.payment_status = 2) as failed,"
                + " sum(o.payment_status = 0 and o.gateway_ref is not null) as pending"
                + " from orders o join landing_pages p on p.id = o.landing_page_id where coalesce(o.is_test, 0) = 0 and o.created_at >= ? and o.created_at < ?" + oc + f.pageSql("p")
                + " group by 1", op.toArray())) {
            Map<String, Object> x = new LinkedHashMap<>(row);
            x.put("successRate", pct(row.lng("paid", 0), row.lng("paid", 0) + row.lng("failed", 0)));
            out.add(x);
        }
        return out;
    }

    private List<Map<String, Object>> byDevice(Range r, Filter f) {
        List<Object> ep = new ArrayList<>(List.of(r.from(), r.to()));
        String ec = campaignEvents(f, ep);
        return rows(db.select("select coalesce(e.device, 'desktop') as device, count(distinct case when e.event = 'VIEW' then e.visitor end) as visitors,"
                + " count(distinct case when e.event = 'PURCHASE' then e.visitor end) as buyers"
                + " from landing_events e join landing_pages p on p.id = e.landing_page_id where e.created_at >= ? and e.created_at < ?" + ec + f.pageSql("p")
                + " group by 1", ep.toArray()));
    }

    /** Lifetime numbers for the All landing pages table. */
    public Map<Long, Map<String, Object>> totals() {
        Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
        for (Row row : db.select("select p.id, (select count(*) from landing_events e where e.landing_page_id = p.id and e.event = 'VIEW') as views,"
                + " (select count(distinct e.visitor) from landing_events e where e.landing_page_id = p.id and e.event = 'VIEW') as visitors,"
                + " (select count(*) from orders o where o.landing_page_id = p.id and o.payment_status = 1 and coalesce(o.is_test, 0) = 0) as enrollments,"
                + " (select coalesce(sum(o.price + 0), 0) from orders o where o.landing_page_id = p.id and o.payment_status = 1 and o.price_type = 'inr' and coalesce(o.is_test, 0) = 0) as revenue"
                + " from landing_pages p")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("views", row.lng("views", 0));
            m.put("enrollments", row.lng("enrollments", 0));
            m.put("conversion", pct(row.lng("enrollments", 0), row.lng("visitors", 0)));
            m.put("revenue", dec(row.get("revenue")));
            out.put(row.lng("id"), m);
        }
        return out;
    }

    private static List<Map<String, Object>> rows(List<Row> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Row r : rows) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            if (m.containsKey("revenue")) m.put("revenue", dec(r.get("revenue")));
            out.add(m);
        }
        return out;
    }

    static double pct(long part, long whole) {
        return whole <= 0 ? 0 : Math.round(part * 1000.0 / whole) / 10.0;
    }

    static BigDecimal dec(Object o) {
        if (o == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(o)).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
