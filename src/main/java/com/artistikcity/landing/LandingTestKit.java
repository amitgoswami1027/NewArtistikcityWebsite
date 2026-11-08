package com.artistikcity.landing;

import com.artistikcity.admin.AdminKit;
import com.artistikcity.admin.Audit;
import com.artistikcity.auth.LoginSettings;
import com.artistikcity.commission.CommissionPayments;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Login & verification › Test pages: one click creates four published test landing pages, one per way of verifying a
 * buyer, so the whole flow (simulated SMS code, simulated e-mail code, simulated Razorpay, congratulations, thank-you
 * e-mail) can be tried on any server, including the live one before launch:
 * <ul>
 *   <li>/landing/test-mobile-otp-razorpay - a code to the mobile (PHONE)</li>
 *   <li>/landing/test-email-otp-razorpay - a code to the e-mail (EMAIL)</li>
 *   <li>/landing/test-buyer-chooses-otp - the buyer picks mobile or e-mail (EITHER)</li>
 *   <li>/landing/test-sign-in-first - the earlier flow: sign in before the details (SIGNIN)</li>
 * </ul>
 * They are ordinary landing pages made through {@link LandingPages} (create, draft, publish as version 1), for the
 * course of the first published course landing page (else the first live paid course), kept out of search engines and
 * the sitemap, and labelled "[Test]". Existing pages with those addresses are left alone. "Switch on simulation" also
 * turns on simulated SMS / e-mail codes and simulated Razorpay. "Remove" unpublishes and archives them (orders made
 * through them are kept; nothing is deleted). Capabilities landing.publish and landing.payments; audited.
 */
@RestController
public class LandingTestKit {

    record Spec(String slug, String name, String verify, String note) {
    }

    static final List<Spec> PAGES = List.of(
            new Spec("test-mobile-otp-razorpay", "[Test] Mobile code + Razorpay", "PHONE", "Guest checkout; a 6-digit SMS code at Pay; Razorpay."),
            new Spec("test-email-otp-razorpay", "[Test] E-mail code + Razorpay", "EMAIL", "Guest checkout; a 6-digit e-mail code at Pay; Razorpay."),
            new Spec("test-buyer-chooses-otp", "[Test] Buyer chooses mobile or e-mail code", "EITHER", "Guest checkout; the buyer picks SMS or e-mail; Razorpay."),
            new Spec("test-sign-in-first", "[Test] Sign in first", "SIGNIN", "The checkout starts with the sign-in methods (Google, codes, passkey, password)."));

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final Audit audit;
    private final LandingPages pages;
    private final LandingCommerce commerce;
    private final LandingPayments payments;
    private final LoginSettings login;
    private final CommissionPayments gateways;

    public LandingTestKit(AdminKit kit, Db db, Json json, Audit audit, LandingPages pages, LandingCommerce commerce, LandingPayments payments,
                          LoginSettings login, CommissionPayments gateways) {
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.audit = audit;
        this.pages = pages;
        this.commerce = commerce;
        this.payments = payments;
        this.login = login;
        this.gateways = gateways;
    }

    @GetMapping("/admin/api/landing/test-kit")
    public ResponseEntity<String> status(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        return kit.ok(Map.of("pages", list()));
    }

    /** {simulate: true|false} -> creates (and publishes) the four test pages; with simulate, switches the simulations on. */
    @PostMapping("/admin/api/landing/test-kit")
    public ResponseEntity<String> create(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments") || !kit.can(admin, "landing.publish")) return kit.forbidden();
        Input in = Input.of(request, json);
        long adminId = admin.lng("id");
        if (in.bool("simulate")) {
            LoginSettings.Values v = login.values();
            if (!v.simulatePhone() || !v.simulateEmail()) {
                LoginSettings.Values after = new LoginSettings.Values(v.checkoutVerify(), true, true);
                login.save(after);
                audit.change(admin, "landing_settings", 0, "update", "Login & verification: simulated SMS and e-mail codes on (test pages)", login.asMap(v), login.asMap(after));
            }
            if (!gateways.razorpayConfigured() && !payments.simulateRazorpaySetting()) {
                payments.saveSimulateRazorpay(true);
                audit.change(admin, "landing_settings", 0, "update", "Simulated Razorpay switched on (test pages)", Map.of("simulate_razorpay", false), Map.of("simulate_razorpay", true));
            }
        }
        Row course = course();
        if (course == null) {
            return kit.invalid(Map.of("course", "There is no live course with a price to sell on the test pages. Publish a paid course first."));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Spec spec : PAGES) {
            out.add(install(spec, course.lng("id"), adminId));
        }
        audit.log(admin, "landing_page", 0, "create", "Landing test pages: " + out.stream().map(m -> m.get("slug") + "=" + m.get("status")).toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pages", list());
        body.put("results", out);
        long published = out.stream().filter(m -> "published".equals(m.get("status"))).count();
        body.put("message", published > 0 ? published + " test page" + (published == 1 ? "" : "s") + " published. Open one in a private window and press Enrol now."
                : "No new test page was published: see the reason next to each page.");
        return kit.ok(body);
    }

    /** Unpublishes and archives the test pages (their orders stay). */
    @PostMapping("/admin/api/landing/test-kit/remove")
    public ResponseEntity<String> remove(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments") || !kit.can(admin, "landing.publish")) return kit.forbidden();
        int n = 0;
        for (Spec spec : PAGES) {
            Row p = db.first("select id, status, name from landing_pages where slug = ?", spec.slug());
            if (p == null || "ARCHIVED".equals(p.str("status")) || !p.str("name", "").startsWith("[Test]")) continue;
            if ("PUBLISHED".equals(p.str("status")) || "SCHEDULED".equals(p.str("status"))) pages.unpublish(p.lng("id"));
            pages.archive(p.lng("id"));
            n++;
        }
        audit.log(admin, "landing_page", 0, "update", "Landing test pages archived: " + n);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pages", list());
        body.put("message", n == 0 ? "There were no test pages to remove." : n + " test page" + (n == 1 ? "" : "s") + " unpublished and archived. Orders made on them are kept.");
        return kit.ok(body);
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> install(Spec spec, long courseId, long adminId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("slug", spec.slug());
        r.put("url", LandingPages.path(spec.slug()));
        r.put("verify", spec.verify());
        Row existing = db.first("select id, status, name from landing_pages where slug = ?", spec.slug());
        long id;
        if (existing != null) {
            if (!existing.str("name", "").startsWith("[Test]")) {
                r.put("status", "skipped");
                r.put("detail", "Another landing page already uses this address; it was left as it is.");
                return r;
            }
            id = existing.lng("id");
            if ("PUBLISHED".equals(existing.str("status"))) {
                r.put("id", id);
                r.put("status", "published");
                r.put("detail", "Already published.");
                return r;
            }
            if ("ARCHIVED".equals(existing.str("status"))) pages.restore(id);
        } else {
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("kind", "COURSE");
            in.put("courseId", String.valueOf(courseId));
            in.put("name", spec.name());
            in.put("slug", spec.slug());
            in.put("description", "Test page made by Login & verification › Test pages. " + spec.note());
            id = pages.create(in, "watercolour-wash", adminId);
        }
        r.put("id", id);
        Map<String, Object> cfg = pages.draft(pages.find(id));
        prepare(cfg, spec);
        LandingConfig.Result saved = pages.saveDraft(id, cfg, adminId);
        if (!saved.ok()) {
            r.put("status", "draft");
            r.put("detail", "The content didn't pass the editor's checks: " + saved.errors());
            return r;
        }
        Map<String, Object> co = new LinkedHashMap<>(commerce.checkoutView(id));
        co.put("verifyWith", spec.verify());
        co.put("requirePhone", true);
        co.put("requireTerms", true);
        co.put("successRedirect", "");
        commerce.saveCheckout(id, co, adminId);
        LandingPricing.Quote q = commerce.quote(pages.find(id));
        if (!q.free() && payments.choices(payments.forPage(id), q.totalUsd() != null).isEmpty()) {
            r.put("status", "draft");
            r.put("detail", "Saved as a draft: no way to pay is live (switch on Simulate Razorpay, or add the Razorpay keys), so it was not published.");
            return r;
        }
        Map<String, String> errors = pages.publish(id, adminId, "Test page (Login & verification)");
        if (!errors.isEmpty()) {
            r.put("status", "draft");
            r.put("detail", "Not published: " + String.join(" ", errors.values()));
            return r;
        }
        r.put("status", "published");
        r.put("detail", "Published.");
        return r;
    }

    @SuppressWarnings("unchecked")
    private static void prepare(Map<String, Object> cfg, Spec spec) {
        Object secs = cfg.get("sections");
        if (secs instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && "hero".equals(m.get("type")) && m.get("content") instanceof Map<?, ?> c) {
                    Map<String, Object> content = (Map<String, Object>) c;
                    content.put("eyebrow", "Test page · " + label(spec.verify()));
                    content.put("subheading", spec.note() + " No real money is taken while Razorpay is simulated.");
                }
            }
        }
        if (cfg.get("seo") instanceof Map<?, ?> s) {
            Map<String, Object> seo = (Map<String, Object>) s;
            seo.put("index", false);        // never in search engines or the sitemap
            seo.put("sitemap", false);
        }
    }

    private static String label(String verify) {
        return switch (verify) {
            case "PHONE" -> "mobile code at Pay";
            case "EMAIL" -> "e-mail code at Pay";
            case "EITHER" -> "mobile or e-mail code";
            default -> "sign in first";
        };
    }

    /** The course to sell: the first published course landing page's course, else the first live course with a price. */
    private Row course() {
        Row p = db.first("select course_id as id from landing_pages where status = 'PUBLISHED' and course_id is not null"
                + " and slug not like 'test-%' order by id limit 1");
        if (p != null && paid(p.lng("id"))) return p;
        for (Row c : db.select("select id from courses where status = 1 order by id limit 50")) {
            if (paid(c.lng("id"))) return c;
        }
        return null;
    }

    private boolean paid(long courseId) {
        BigDecimal[] prices = commerce.coursePrices(courseId);
        return prices != null && prices[0] != null && prices[0].signum() > 0;
    }

    private List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Spec spec : PAGES) {
            Row p = db.first("select id, status, name from landing_pages where slug = ?", spec.slug());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slug", spec.slug());
            m.put("url", LandingPages.path(spec.slug()));
            m.put("verify", spec.verify());
            m.put("name", spec.name());
            m.put("id", p == null ? null : p.get("id"));
            m.put("status", p == null ? "MISSING" : p.str("status"));
            m.put("ours", p == null || p.str("name", "").startsWith("[Test]"));
            out.add(m);
        }
        return out;
    }
}
