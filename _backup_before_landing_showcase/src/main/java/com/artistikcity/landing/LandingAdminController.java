package com.artistikcity.landing;

import com.artistikcity.admin.AdminKit;
import com.artistikcity.admin.Audit;
import com.artistikcity.lifecycle.Personas;
import com.artistikcity.payments.RefundQueue;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import com.artistikcity.support.UploadedFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin > Landing Page Management: every landing-page screen and API in one place (pages, templates and sections,
 * payments and checkout, coupons, orders and refunds, analytics, media, settings). Capabilities are checked on every
 * call ({@link Personas}); instructors only see pages of their own courses. Changes to money settings, publishing and
 * refunds are written to the audit log.
 */
@RestController
public class LandingAdminController {

    private static final Set<String> TABS = Set.of("overview", "pages", "templates", "payments", "coupons", "orders", "analytics", "media", "settings");

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final Audit audit;
    private final Storage storage;
    private final LandingPages pages;
    private final LandingCommerce commerce;
    private final LandingPayments payments;
    private final LandingCoupons coupons;
    private final LandingAnalytics analytics;
    private final LandingPublic publicView;
    private final LandingView view;
    private final RefundQueue refunds;

    public LandingAdminController(AdminKit kit, Db db, Json json, Audit audit, Storage storage, LandingPages pages, LandingCommerce commerce,
                                  LandingPayments payments, LandingCoupons coupons, LandingAnalytics analytics, LandingPublic publicView, LandingView view,
                                  RefundQueue refunds) {
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.audit = audit;
        this.storage = storage;
        this.pages = pages;
        this.commerce = commerce;
        this.payments = payments;
        this.coupons = coupons;
        this.analytics = analytics;
        this.publicView = publicView;
        this.view = view;
        this.refunds = refunds;
    }

    // =================================================================================================== pages

    @GetMapping({"/admin/landing", "/admin/landing/{tab}"})
    public ResponseEntity<String> index(HttpServletRequest request, @PathVariable(value = "tab", required = false) String tab) {
        String t = tab == null ? "overview" : tab;
        if (!TABS.contains(t)) {
            return ResponseEntity.status(HttpStatus.FOUND).header("Location", "/admin/landing").build();
        }
        return kit.page(request, "landing", "Admin/Landing/Index", () -> {
            Row admin = kit.admin(request);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("tab", t);
            p.put("caps", Personas.capabilities(admin).stream().filter(c -> c.startsWith("landing")).toList());
            p.put("lookups", lookups(admin));
            p.put("catalog", catalog());
            return p;
        });
    }

    @GetMapping({"/admin/landing/pages/new", "/admin/landing/pages/{id}", "/admin/landing/pages/{id}/{step}"})
    public ResponseEntity<String> editor(HttpServletRequest request, @PathVariable(value = "id", required = false) Long id) {
        return kit.page(request, "landing", "Admin/Landing/Editor", () -> {
            Row admin = kit.admin(request);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("pageId", id);
            p.put("caps", Personas.capabilities(admin).stream().filter(c -> c.startsWith("landing")).toList());
            p.put("lookups", lookups(admin));
            p.put("catalog", catalog());
            p.put("defaults", settings());
            return p;
        });
    }

    /** The draft (or one version) rendered exactly as the public page, for the editor's desktop / mobile frames. */
    @GetMapping("/admin/landing/preview/{id}")
    public ResponseEntity<String> preview(HttpServletRequest request, @PathVariable("id") long id, @RequestParam(value = "version", required = false) Long version) {
        Row admin = kit.admin(request);
        Row page = pages.find(id);
        if (!kit.can(admin, "landing") || page == null || !mayEdit(admin, page)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You don't have access to this page.");
        }
        Map<String, Object> config = pages.draft(page);
        if (version != null) {
            Row v = db.first("select config from landing_page_versions where id = ? and landing_page_id = ?", version, id);
            if (v != null) config = json.decodeMap(v.str("config"));
        }
        return publicView.preview(request, page, config);
    }

    // ===================================================================================================== API

    @GetMapping("/admin/api/landing/overview")
    public ResponseEntity<String> overview(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing")) return kit.forbidden();
        LandingAnalytics.Range r = LandingAnalytics.Range.of(request.getParameter("range"), request.getParameter("from"), request.getParameter("to"));
        Map<String, Object> out = analytics.overview(r, filter(request, admin));
        out.put("range", Map.of("from", r.from().toLocalDate().toString(), "to", r.to().toLocalDate().minusDays(1).toString()));
        if (!kit.can(admin, "landing.orders") && !kit.can(admin, "landing.pricing")) {
            // money figures are for staff who handle money
            hideMoney(out);
        }
        return kit.ok(out);
    }

    @SuppressWarnings("unchecked")
    private static void hideMoney(Map<String, Object> out) {
        Map<String, Object> k = (Map<String, Object>) out.get("kpis");
        for (String key : List.of("revenueInr", "revenueUsd", "aovInr", "refundsInr")) k.put(key, null);
        for (String list : List.of("series", "byPage", "byCourse", "byCampaign", "byMethod")) {
            for (Map<String, Object> row : (List<Map<String, Object>>) out.get(list)) row.remove("revenue");
        }
        out.put("moneyHidden", true);
    }

    private LandingAnalytics.Filter filter(HttpServletRequest request, Row admin) {
        return new LandingAnalytics.Filter(lng(request.getParameter("page")), lng(request.getParameter("course")), lng(request.getParameter("artist")),
                request.getParameter("campaign") == null || request.getParameter("campaign").isBlank() ? null : request.getParameter("campaign").trim(),
                kit.teacherScope(admin));
    }

    @GetMapping("/admin/api/landing/pages")
    public ResponseEntity<String> list(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing")) return kit.forbidden();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where 1 = 1");
        Long scope = kit.teacherScope(admin);
        if (scope != null) {
            where.append(" and c.admin_id = ?");
            params.add(scope);
        }
        String q = request.getParameter("q");
        if (q != null && !q.isBlank()) {
            where.append(" and (p.name like ? or p.slug like ? or c.title like ? or p.campaign like ?)");
            String like = "%" + q.trim().replace("%", "\\%").replace("_", "\\_") + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        String status = request.getParameter("status");
        if (status != null && Set.of("DRAFT", "SCHEDULED", "PUBLISHED", "ARCHIVED").contains(status)) {
            where.append(" and p.status = ?");
            params.add(status);
        } else if (!"all".equals(status)) {
            where.append(" and p.status <> 'ARCHIVED'");
        }
        Map<String, String> sorts = Map.of("name", "p.name", "updated", "p.updated_at", "created", "p.created_at", "published", "p.published_at", "course", "c.title", "status", "p.status");
        String sortKey = request.getParameter("sort");
        String sort = sortKey == null ? "p.updated_at" : sorts.getOrDefault(sortKey, "p.updated_at");
        String dir = "asc".equals(request.getParameter("dir")) ? "asc" : "desc";
        int per = Math.max(5, Math.min(100, intOr(request.getParameter("per"), 20)));
        long total = db.count("select count(*) from landing_pages p join courses c on c.id = p.course_id" + where, params.toArray());
        int pageNo = Math.max(1, Math.min(intOr(request.getParameter("page"), 1), (int) Math.max(1, (total + per - 1) / per)));
        List<Object> lp = new ArrayList<>(params);
        lp.add(per);
        lp.add((pageNo - 1) * per);
        Map<Long, Map<String, Object>> totals = analytics.totals();
        boolean money = kit.can(admin, "landing.orders") || kit.can(admin, "landing.pricing");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Row r : db.select("select p.id, p.name, p.slug, p.status, p.template_key, p.campaign, p.created_at, p.updated_at, p.published_at, p.publish_at,"
                + " c.title as course, c.slug as course_slug, a.name as artist from landing_pages p join courses c on c.id = p.course_id left join admins a on a.id = c.admin_id"
                + where + " order by " + sort + " " + dir + ", p.id desc limit ? offset ?", lp.toArray())) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            LandingCatalog.Template t = LandingCatalog.template(r.str("template_key"));
            m.put("template", t == null ? r.str("template_key") : t.name());
            Map<String, Object> s = totals.getOrDefault(r.lng("id"), Map.of());
            m.put("views", s.getOrDefault("views", 0L));
            m.put("enrollments", s.getOrDefault("enrollments", 0L));
            m.put("conversion", s.getOrDefault("conversion", 0.0));
            m.put("revenue", money ? s.get("revenue") : null);
            m.put("takesOverCourse", r.str("slug").equals(r.str("course_slug")));
            rows.add(m);
        }
        Map<String, Object> counts = new LinkedHashMap<>();
        for (Row r : db.select("select p.status, count(*) as n from landing_pages p join courses c on c.id = p.course_id" + (scope == null ? "" : " where c.admin_id = " + scope.longValue())
                + " group by p.status")) {
            counts.put(r.str("status"), r.lng("n"));
        }
        return kit.ok(Map.of("rows", rows, "meta", Map.of("total", total, "page", pageNo, "per", per, "pages", Math.max(1, (total + per - 1) / per)), "counts", counts));
    }

    @PostMapping("/admin/api/landing/pages")
    public ResponseEntity<String> create(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.publish")) return kit.forbidden();
        Map<String, Object> in = Input.of(request, json).all();
        Map<String, String> e = pages.checkBasics(in, null);
        String template = LandingConfig.str(in.get("template"));
        if (LandingCatalog.template(template) == null) e.put("template", "Choose a template.");
        if (e.isEmpty() && !courseAllowed(admin, Long.parseLong(LandingConfig.str(in.get("courseId")).replace(".0", "")))) e.put("courseId", "You can only make pages for your own courses.");
        if (!e.isEmpty()) return kit.invalid(e);
        long id = pages.create(in, template, admin.lng("id"));
        applyDefaults(id, admin.lng("id"));
        audit.log(admin, "landing_page", id, "create", "Created landing page " + in.get("name") + " (/" + in.get("slug") + ")");
        return kit.ok(Map.of("id", id, "message", "Landing page created as a draft."));
    }

    /** Settings > defaults applied to a new page: WhatsApp number, access, refund policy and checkout fields. */
    @SuppressWarnings("unchecked")
    private void applyDefaults(long id, long adminId) {
        Map<String, Object> d = settings();
        Row page = pages.find(id);
        Map<String, Object> config = pages.draft(page);
        String wa = LandingConfig.str(d.get("whatsapp"));
        if (!wa.isEmpty()) {
            ((Map<String, Object>) config.get("marketing")).put("whatsapp", wa);
            pages.saveDraft(id, config, adminId);
        }
        Map<String, Object> pr = new LinkedHashMap<>(commerce.pricingView(id));
        if (!LandingConfig.str(d.get("accessDuration")).isEmpty()) pr.put("accessDuration", d.get("accessDuration"));
        if (!LandingConfig.str(d.get("refundPolicy")).isEmpty()) pr.put("refundPolicy", d.get("refundPolicy"));
        if (d.get("taxRate") != null) pr.put("taxRate", d.get("taxRate"));
        if (d.get("taxMode") != null) pr.put("taxMode", d.get("taxMode"));
        commerce.savePricing(id, pr, adminId);
        Map<String, Object> co = LandingConfig.map(d.get("checkout"));
        if (!co.isEmpty()) commerce.saveCheckout(id, co, adminId);
    }

    @GetMapping("/admin/api/landing/pages/{id}")
    public ResponseEntity<String> show(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        Row page = pages.find(id);
        if (!kit.can(admin, "landing") || page == null || !mayEdit(admin, page)) return kit.forbidden();
        return kit.ok(editorData(admin, page));
    }

    private Map<String, Object> editorData(Row admin, Row page) {
        long id = page.lng("id");
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> p = new LinkedHashMap<>();
        for (String k : List.of("id", "name", "slug", "course_id", "campaign", "description", "template_key", "status", "published_at", "publish_at", "archived_at",
                "draft_saved_at", "created_at", "updated_at", "published_version_id")) {
            p.put(k, page.get(k));
        }
        Row course = db.first("select slug, title from courses where id = ?", page.get("course_id"));
        p.put("course_slug", course == null ? null : course.get("slug"));
        p.put("course_title", course == null ? null : course.get("title"));
        p.put("publicUrl", "/course/" + page.str("slug"));
        p.put("takesOverCourse", course != null && page.str("slug").equals(course.str("slug")));
        Row saver = page.get("draft_saved_by") == null ? null : db.first("select name from admins where id = ?", page.get("draft_saved_by"));
        p.put("draft_saved_by_name", saver == null ? null : saver.get("name"));
        m.put("page", p);
        m.put("config", pages.draft(page));
        m.put("hasUnpublishedChanges", hasUnpublished(page));
        m.put("versions", pages.versions(id));
        m.put("pricing", commerce.pricingView(id));
        m.put("quote", view.price(commerce.quote(page)));
        m.put("coursePrice", commerce.coursePrices(page.lng("course_id")));
        m.put("payments", payments.view(payments.forPage(id)));
        m.put("checkout", commerce.checkoutView(id));
        m.put("course", view.course(page.lng("course_id")));
        m.put("orders", db.count("select count(*) from orders where landing_page_id = ?", id));
        m.put("history", audit.history("landing_page", id).stream().limit(20).toList());
        return m;
    }

    private boolean hasUnpublished(Row page) {
        if (page.get("published_version_id") == null) return true;
        Row v = db.first("select config, template_key from landing_page_versions where id = ?", page.get("published_version_id"));
        if (v == null) return true;
        Object a = json.decode(v.str("config"));
        Object b = json.decode(page.str("draft_config"));
        return !String.valueOf(a).equals(String.valueOf(b)) || !v.str("template_key").equals(page.str("template_key"));
    }

    @PutMapping("/admin/api/landing/pages/{id}/basics")
    public ResponseEntity<String> basics(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        Row page = pages.find(id);
        if (!kit.can(admin, "landing.content") || page == null || !mayEdit(admin, page)) return kit.forbidden();
        Map<String, Object> in = Input.of(request, json).all();
        Map<String, String> e = pages.checkBasics(in, id);
        boolean addressChange = !page.str("slug").equals(LandingConfig.str(in.get("slug")).toLowerCase())
                || !String.valueOf(page.get("course_id")).equals(LandingConfig.str(in.get("courseId")).replace(".0", ""));
        if (e.isEmpty() && addressChange && "PUBLISHED".equals(page.str("status")) && !kit.can(admin, "landing.publish")) {
            e.put("slug", "Changing the address or course of a live page needs publishing rights.");
        }
        if (e.isEmpty() && !courseAllowed(admin, Long.parseLong(LandingConfig.str(in.get("courseId")).replace(".0", "")))) e.put("courseId", "You can only use your own courses.");
        if (e.isEmpty() && addressChange && db.count("select count(*) from orders where landing_page_id = ? and course_id <> ?", id, LandingConfig.str(in.get("courseId")).replace(".0", "")) > 0
                && !String.valueOf(page.get("course_id")).equals(LandingConfig.str(in.get("courseId")).replace(".0", ""))) {
            e.put("courseId", "People already bought through this page; duplicate it to sell another course.");
        }
        if (!e.isEmpty()) return kit.invalid(e);
        Row before = audit.snapshot("landing_pages", id);
        pages.updateBasics(id, in);
        audit.change(admin, "landing_page", id, "update", "Basics", strip(before), strip(audit.snapshot("landing_pages", id)));
        return kit.ok(Map.of("message", "Saved.", "data", editorData(admin, pages.find(id))));
    }

    private static Map<String, Object> strip(Row r) {
        if (r == null) return null;
        Map<String, Object> m = new LinkedHashMap<>(r);
        m.remove("draft_config");
        return m;
    }

    @PutMapping("/admin/api/landing/pages/{id}/draft")
    public ResponseEntity<String> draft(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        Row page = pages.find(id);
        if (!kit.can(admin, "landing.content") || page == null || !mayEdit(admin, page)) return kit.forbidden();
        Input in = Input.of(request, json);
        Object config = in.raw("config");
        // optimistic check: two people editing the same draft don't silently overwrite each other
        String seen = in.get("savedAt");
        if (seen != null && !seen.isBlank() && page.get("draft_saved_at") != null && !String.valueOf(page.get("draft_saved_at")).equals(seen) && !in.bool("force")) {
            Row who = page.get("draft_saved_by") == null ? null : db.first("select name from admins where id = ?", page.get("draft_saved_by"));
            return kit.fail(HttpStatus.CONFLICT, (who == null ? "Someone" : who.str("name")) + " saved this page after you opened it. Reload to see their changes, or save anyway.");
        }
        LandingConfig.Result r = pages.saveDraft(id, config, admin.lng("id"));
        if (!r.ok()) return kit.invalid(r.errors());
        Row fresh = pages.find(id);
        return kit.ok(Map.of("message", "Draft saved.", "config", r.config(), "savedAt", String.valueOf(fresh.get("draft_saved_at")), "hasUnpublishedChanges", hasUnpublished(fresh)));
    }

    @PutMapping("/admin/api/landing/pages/{id}/template")
    public ResponseEntity<String> template(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        Row page = pages.find(id);
        if (!kit.can(admin, "landing.content") || page == null || !mayEdit(admin, page)) return kit.forbidden();
        LandingCatalog.Template t = LandingCatalog.template(Input.of(request, json).get("template"));
        if (t == null) return kit.invalid(Map.of("template", "Choose a template."));
        Map<String, Object> config = pages.applyTemplate(pages.draft(page), t);
        LandingConfig.Result r = LandingConfig.normalize(config);
        pages.setTemplate(id, t.key(), r.config(), admin.lng("id"));
        audit.log(admin, "landing_page", id, "update", "Template changed to " + t.name());
        return kit.ok(Map.of("message", "Template applied to the draft. Nothing you wrote was lost.", "data", editorData(admin, pages.find(id))));
    }

    @PostMapping("/admin/api/landing/pages/{id}/{action:publish|schedule|unpublish|archive|restore|duplicate}")
    public ResponseEntity<String> lifecycle(HttpServletRequest request, @PathVariable("id") long id, @PathVariable("action") String action) {
        Row admin = kit.admin(request);
        Row page = pages.find(id);
        if (!kit.can(admin, "landing.publish") || page == null || !mayEdit(admin, page)) return kit.forbidden();
        Input in = Input.of(request, json);
        String message;
        switch (action) {
            case "publish" -> {
                if ("ARCHIVED".equals(page.str("status"))) return kit.fail(HttpStatus.CONFLICT, "Restore the page before publishing it.");
                String unpayable = unpayable(page);
                if (unpayable != null) return kit.invalid(Map.of("payments", unpayable));
                Map<String, String> e = pages.publish(id, admin.lng("id"), in.get("note"));
                if (!e.isEmpty()) return kit.invalid(e);
                message = "Published. The page is live at /course/" + page.str("slug") + ".";
            }
            case "schedule" -> {
                String unpayable = unpayable(page);
                if (unpayable != null) return kit.invalid(Map.of("payments", unpayable));
                Map<String, String> e = pages.schedule(id, LandingCoupons.dt(in.get("publishAt")));
                if (!e.isEmpty()) return kit.invalid(e);
                message = "Scheduled. It goes live at the time you chose (IST).";
            }
            case "unpublish" -> {
                pages.unpublish(id);
                message = "Unpublished. /course/" + page.str("slug") + " shows the regular course page again.";
            }
            case "archive" -> {
                pages.archive(id);
                message = "Archived.";
            }
            case "restore" -> {
                pages.restore(id);
                message = "Restored as a draft.";
            }
            default -> {
                long copy = pages.duplicate(id, admin.lng("id"));
                audit.log(admin, "landing_page", copy, "create", "Duplicated from landing page " + id);
                return kit.ok(Map.of("id", copy, "message", "Copy created as a draft."));
            }
        }
        audit.log(admin, "landing_page", id, "update", Character.toUpperCase(action.charAt(0)) + action.substring(1) + (in.get("note") == null ? "" : ": " + in.get("note")));
        return kit.ok(Map.of("message", message, "data", editorData(admin, pages.find(id))));
    }

    /** A page can only go live when buyers can pay on it (or it is free): the server decides, not the editor's checklist. */
    private String unpayable(Row page) {
        LandingPricing.Quote q = commerce.quote(page);
        if (q.free()) return null;
        return payments.choices(payments.forPage(page.lng("id")), q.totalUsd() != null).isEmpty()
                ? "No payment method is available on this page, so nobody could pay. Switch one on in Payment settings." : null;
    }

    @DeleteMapping("/admin/api/landing/pages/{id}")
    public ResponseEntity<String> delete(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        Row page = pages.find(id);
        if (!kit.can(admin, "landing.publish") || page == null || !mayEdit(admin, page)) return kit.forbidden();
        if ("PUBLISHED".equals(page.str("status"))) return kit.fail(HttpStatus.CONFLICT, "Unpublish the page before deleting it.");
        if (!pages.delete(id)) return kit.fail(HttpStatus.CONFLICT, "People bought through this page, so it can't be deleted. Archive it instead.");
        audit.log(admin, "landing_page", id, "delete", "Deleted landing page " + page.str("name") + " (/" + page.str("slug") + ")");
        return kit.ok(Map.of("message", "Deleted."));
    }

    @PostMapping("/admin/api/landing/pages/{id}/versions/{vid}/restore")
    public ResponseEntity<String> rollback(HttpServletRequest request, @PathVariable("id") long id, @PathVariable("vid") long vid) {
        Row admin = kit.admin(request);
        Row page = pages.find(id);
        if (!kit.can(admin, "landing.publish") || page == null || !mayEdit(admin, page)) return kit.forbidden();
        Map<String, String> e = pages.rollback(id, vid, admin.lng("id"));
        if (!e.isEmpty()) return kit.invalid(e);
        audit.log(admin, "landing_page", id, "update", "Rolled back to version id " + vid);
        return kit.ok(Map.of("message", "That version is live again (published as a new version).", "data", editorData(admin, pages.find(id))));
    }

    // ----------------------------------------------------------------------------------------- money settings

    @PutMapping("/admin/api/landing/pages/{id}/pricing")
    public ResponseEntity<String> pricing(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        Row page = pages.find(id);
        if (!kit.can(admin, "landing.pricing") || page == null || !mayEdit(admin, page)) return kit.forbidden();
        Map<String, Object> before = commerce.pricingView(id);
        Map<String, String> e = commerce.savePricing(id, Input.of(request, json).all(), admin.lng("id"));
        if (!e.isEmpty()) return kit.invalid(e);
        audit.change(admin, "landing_page", id, "update", "Pricing", before, commerce.pricingView(id));
        return kit.ok(Map.of("message", "Pricing saved. It applies on the page and at checkout right away.", "pricing", commerce.pricingView(id),
                "quote", view.price(commerce.quote(pages.find(id)))));
    }

    @PutMapping("/admin/api/landing/pages/{id}/payments")
    public ResponseEntity<String> pagePayments(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        Row page = pages.find(id);
        if (!kit.can(admin, "landing.payments") || page == null || !mayEdit(admin, page)) return kit.forbidden();
        Map<String, Object> in = Input.of(request, json).all();
        String mode = "CUSTOM".equals(LandingConfig.str(in.get("mode"))) ? "CUSTOM" : "GLOBAL";
        Map<String, Object> before = payments.view(payments.forPage(id));
        List<LandingPayments.ProviderSetting> providers = new ArrayList<>();
        List<String[]> methods = new ArrayList<>();
        if ("CUSTOM".equals(mode)) {
            Map<String, String> e = payments.validate(in, providers, methods);
            if (!e.isEmpty()) return kit.invalid(e);
        }
        payments.savePage(id, mode, "CUSTOM".equals(mode) ? LandingConfig.str(in.get("defaultProvider")) : null, providers, methods, admin.lng("id"));
        Map<String, Object> after = payments.view(payments.forPage(id));
        audit.change(admin, "landing_page", id, "update", "Payment settings (" + mode.toLowerCase() + ")", summary(before), summary(after));
        return kit.ok(Map.of("message", "GLOBAL".equals(mode) ? "This page now uses the global payment settings." : "Payment settings saved for this page only.",
                "payments", after));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> summary(Map<String, Object> v) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("mode", v.get("mode"));
        s.put("defaultProvider", v.get("defaultProvider"));
        s.put("providers", ((List<Map<String, Object>>) v.get("providers")).stream().filter(p -> Boolean.TRUE.equals(p.get("enabled"))).map(p -> p.get("key")).toList());
        s.put("methods", ((List<Map<String, Object>>) v.get("methods")).stream().filter(p -> Boolean.TRUE.equals(p.get("enabled"))).map(p -> p.get("key")).toList());
        return s;
    }

    @PutMapping("/admin/api/landing/pages/{id}/checkout")
    public ResponseEntity<String> checkout(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        Row page = pages.find(id);
        if (!kit.can(admin, "landing.payments") || page == null || !mayEdit(admin, page)) return kit.forbidden();
        Map<String, Object> before = commerce.checkoutView(id);
        Map<String, String> e = commerce.saveCheckout(id, Input.of(request, json).all(), admin.lng("id"));
        if (!e.isEmpty()) return kit.invalid(e);
        audit.change(admin, "landing_page", id, "update", "Checkout settings", before, commerce.checkoutView(id));
        return kit.ok(Map.of("message", "Checkout settings saved.", "checkout", commerce.checkoutView(id)));
    }

    /** Payments tab: global defaults and every page's mode at a glance. */
    @GetMapping("/admin/api/landing/payments")
    public ResponseEntity<String> paymentsOverview(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Row p : db.select("select p.id, p.name, p.slug, p.status, c.title as course from landing_pages p join courses c on c.id = p.course_id where p.status <> 'ARCHIVED' order by p.name")) {
            LandingPayments.Plan plan = payments.forPage(p.lng("id"));
            Map<String, Object> m = new LinkedHashMap<>(p);
            m.put("mode", plan.mode());
            m.put("providers", plan.providers().stream().filter(LandingPayments.ProviderSetting::enabled).map(LandingPayments.ProviderSetting::key).toList());
            m.put("methods", plan.enabledMethods());
            Row pr = db.first("select price_usd from landing_pricing where landing_page_id = ?", p.get("id"));
            m.put("choices", payments.choices(plan, pr != null && pr.get("price_usd") != null || commerce.coursePrices(db.first("select course_id from landing_pages where id = ?", p.get("id")).lng("course_id"))[1] != null)
                    .stream().map(LandingPayments.Choice::label).toList());
            rows.add(m);
        }
        return kit.ok(Map.of("defaults", payments.view(payments.defaults()), "pages", rows, "catalog", catalog().get("methods")));
    }

    @PutMapping("/admin/api/landing/payments/defaults")
    public ResponseEntity<String> paymentDefaults(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        Map<String, Object> in = Input.of(request, json).all();
        List<LandingPayments.ProviderSetting> providers = new ArrayList<>();
        List<String[]> methods = new ArrayList<>();
        Map<String, String> e = payments.validate(in, providers, methods);
        if (!e.isEmpty()) return kit.invalid(e);
        Map<String, Object> before = summary(payments.view(payments.defaults()));
        payments.saveDefaults(LandingConfig.str(in.get("defaultProvider")), providers, methods, admin.lng("id"));
        audit.change(admin, "landing_settings", 0, "update", "Global landing payment defaults", before, summary(payments.view(payments.defaults())));
        return kit.ok(Map.of("message", "Global defaults saved. Pages using global settings follow them now; pages with their own settings don't change.",
                "defaults", payments.view(payments.defaults())));
    }

    @PostMapping("/admin/api/landing/payments/test")
    public ResponseEntity<String> testProvider(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        String provider = Input.of(request, json).get("provider");
        if (LandingCatalog.provider(provider) == null) return kit.invalid(Map.of("provider", "Unknown provider."));
        String problem = payments.test(provider);
        audit.log(admin, "landing_settings", 0, "update", "Tested payment provider " + provider + (problem == null ? ": OK" : ": " + problem));
        return kit.ok(Map.of("ok", problem == null, "message", problem == null ? LandingCatalog.provider(provider).label() + " answered. " + payments.status(provider) + "." : problem,
                "status", payments.status(provider)));
    }

    // ------------------------------------------------------------------------------------------------- coupons

    @GetMapping("/admin/api/landing/coupons")
    public ResponseEntity<String> couponList(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.pricing")) return kit.forbidden();
        return kit.ok(Map.of("coupons", coupons.list()));
    }

    @PostMapping("/admin/api/landing/coupons")
    public ResponseEntity<String> couponCreate(HttpServletRequest request) {
        return couponSave(request, null);
    }

    @PutMapping("/admin/api/landing/coupons/{id}")
    public ResponseEntity<String> couponUpdate(HttpServletRequest request, @PathVariable("id") long id) {
        return couponSave(request, id);
    }

    private ResponseEntity<String> couponSave(HttpServletRequest request, Long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.pricing")) return kit.forbidden();
        if (id != null && db.find("landing_coupons", id) == null) return kit.fail(HttpStatus.NOT_FOUND, "That coupon doesn't exist.");
        Row before = id == null ? null : audit.snapshot("landing_coupons", id);
        long[] saved = new long[1];
        Map<String, String> e = coupons.save(id, Input.of(request, json).all(), admin.lng("id"), saved);
        if (!e.isEmpty()) return kit.invalid(e);
        audit.change(admin, "landing_coupon", saved[0], id == null ? "create" : "update", "Coupon " + db.find("landing_coupons", saved[0]).str("code"),
                before, audit.snapshot("landing_coupons", saved[0]));
        return kit.ok(Map.of("message", id == null ? "Coupon created." : "Coupon saved.", "coupons", coupons.list()));
    }

    @DeleteMapping("/admin/api/landing/coupons/{id}")
    public ResponseEntity<String> couponDelete(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.pricing")) return kit.forbidden();
        Row c = db.find("landing_coupons", id);
        if (c == null) return kit.fail(HttpStatus.NOT_FOUND, "That coupon doesn't exist.");
        if (db.count("select count(*) from landing_coupon_redemptions where coupon_id = ?", id) > 0
                || db.count("select count(*) from orders where coupon_code = ? and payment_status = 0", c.str("code")) > 0) {
            db.updateById("landing_coupons", id, Row.of("is_active", 0, "updated_at", LocalDateTime.now()));
            audit.log(admin, "landing_coupon", id, "update", "Deactivated coupon " + c.str("code") + " (it has been used, so it is kept)");
            return kit.ok(Map.of("message", "This code has been used, so it was switched off instead of deleted.", "coupons", coupons.list()));
        }
        db.deleteById("landing_coupons", id);
        audit.log(admin, "landing_coupon", id, "delete", "Deleted coupon " + c.str("code"));
        return kit.ok(Map.of("message", "Coupon deleted.", "coupons", coupons.list()));
    }

    // -------------------------------------------------------------------------------------------------- orders

    private static final Map<String, String> STATUS = Map.of("created", "o.payment_status = 0 and o.gateway_ref is null", "pending", "o.payment_status = 0 and o.gateway_ref is not null",
            "successful", "o.payment_status = 1", "failed", "o.payment_status = 2", "refunded", "o.payment_status = 3", "refund_due", "o.payment_status = 4");

    @GetMapping("/admin/api/landing/orders")
    public ResponseEntity<String> orders(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.orders")) return kit.forbidden();
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where o.landing_page_id is not null");
        Long scope = kit.teacherScope(admin);
        if (scope != null) {
            where.append(" and c.admin_id = ?");
            params.add(scope);
        }
        addEq(where, params, "o.landing_page_id", lng(request.getParameter("page")));
        addEq(where, params, "o.course_id", lng(request.getParameter("course")));
        addEq(where, params, "c.admin_id", lng(request.getParameter("artist")));
        String provider = request.getParameter("provider");
        if (provider != null && provider.matches("^[a-z]{2,20}$")) {
            where.append(" and o.payment_provider = ?");
            params.add(provider);
        }
        String method = request.getParameter("method");
        if (method != null && method.matches("^[a-z_]{2,30}$")) {
            where.append(" and o.payment_method = ?");
            params.add(method);
        }
        String st = request.getParameter("status");
        if (st != null && STATUS.containsKey(st)) where.append(" and ").append(STATUS.get(st));
        LandingAnalytics.Range r = LandingAnalytics.Range.of(request.getParameter("range") == null ? "90d" : request.getParameter("range"),
                request.getParameter("from"), request.getParameter("to"));
        where.append(" and o.created_at >= ? and o.created_at < ?");
        params.add(r.from());
        params.add(r.to());
        String q = request.getParameter("q");
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().replace("%", "\\%") + "%";
            where.append(" and (o.order_number like ? or o.billing_name like ? or o.billing_email like ? or o.payment_id like ? or o.gateway_ref like ? or o.coupon_code like ?)");
            for (int i = 0; i < 6; i++) params.add(like);
        }
        String from = " from orders o join courses c on c.id = o.course_id join landing_pages p on p.id = o.landing_page_id left join admins a on a.id = c.admin_id";
        int per = Math.max(10, Math.min(100, intOr(request.getParameter("per"), 25)));
        long total = db.count("select count(*)" + from + where, params.toArray());
        int pageNo = Math.max(1, Math.min(intOr(request.getParameter("page_no"), 1), (int) Math.max(1, (total + per - 1) / per)));
        List<Object> lp = new ArrayList<>(params);
        lp.add(per);
        lp.add((pageNo - 1) * per);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Row o : db.select("select o.id, o.order_number, o.billing_name, o.billing_email, o.price, o.price_type, o.payment_status, o.payment_option, o.payment_id, o.gateway_ref,"
                + " o.payment_method, o.payment_provider, o.coupon_code, o.discount_amount, o.list_amount, o.tax_amount, o.utm_source, o.utm_campaign, o.is_test, o.created_at, o.updated_at,"
                + " c.title as course, a.name as artist, p.name as page, p.slug as page_slug,"
                + " (select count(*) from enrollments e where e.course_id = o.course_id and e.user_id = o.user_id and e.status <> 'Refunded') as enrolled"
                + from + where + " order by o.id desc limit ? offset ?", lp.toArray())) {
            rows.add(orderRow(o));
        }
        return kit.ok(Map.of("rows", rows, "meta", Map.of("total", total, "page", pageNo, "per", per, "pages", Math.max(1, (total + per - 1) / per)),
                "range", Map.of("from", r.from().toLocalDate().toString(), "to", r.to().toLocalDate().minusDays(1).toString()),
                "canRefund", kit.can(admin, "landing.refunds")));
    }

    private static void addEq(StringBuilder where, List<Object> params, String col, Long v) {
        if (v != null) {
            where.append(" and ").append(col).append(" = ?");
            params.add(v);
        }
    }

    private Map<String, Object> orderRow(Row o) {
        Map<String, Object> m = new LinkedHashMap<>(o);
        int s = (int) o.lng("payment_status", 0);
        m.put("status", s == 1 ? "successful" : s == 2 ? "failed" : s == 3 ? "refunded" : s == 4 ? "refund_due" : o.get("gateway_ref") != null ? "pending" : "created");
        m.put("currency", "usd".equals(o.str("price_type")) ? "USD" : "INR");
        // course access comes from the paid order itself (the studio's enrolment row is created on the student's first visit)
        m.put("enrollment", s == 1 ? "Enrolled" : s == 3 || s == 4 ? "Refunded / due" : "Not enrolled");
        Row ref = db.first("select id, status, reason, gateway_refund_id from payment_refunds where source = 'COURSE' and source_ref = ? order by id desc limit 1", String.valueOf(o.get("id")));
        m.put("refund", ref);
        return m;
    }

    @GetMapping("/admin/api/landing/orders/{id}")
    public ResponseEntity<String> order(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.orders")) return kit.forbidden();
        Row o = db.first("select o.*, c.title as course, c.admin_id, a.name as artist, p.name as page, p.slug as page_slug,"
                + " (select count(*) from enrollments e where e.course_id = o.course_id and e.user_id = o.user_id and e.status <> 'Refunded') as enrolled"
                + " from orders o join courses c on c.id = o.course_id join landing_pages p on p.id = o.landing_page_id left join admins a on a.id = c.admin_id"
                + " where o.id = ?", id);
        if (o == null) return kit.fail(HttpStatus.NOT_FOUND, "Order not found.");
        Long scope = kit.teacherScope(admin);
        if (scope != null && o.lng("admin_id", -1) != scope) return kit.forbidden();
        Map<String, Object> m = orderRow(o);
        m.put("billing", Row.of("name", o.get("billing_name"), "email", o.get("billing_email"), "phone", o.get("billing_phone"), "address", o.get("billing_address"),
                "city", o.get("billing_city"), "state", o.get("billing_state"), "zip", o.get("billing_zipcode"), "country", o.get("billing_country")));
        m.put("log", db.select("select payment_option, payment_id, payment_status, created_at from payment_logs where order_id = ? order by id", id));
        m.put("events", db.select("select event, method, provider, created_at from landing_events where order_id = ? order by id", id));
        m.remove("payment_details");
        m.remove("session_id");
        return kit.ok(m);
    }

    /** Full refund through the gateway, from here (queued in the refunds queue and sent at once; audited). */
    @PostMapping("/admin/api/landing/orders/{id}/refund")
    public ResponseEntity<String> refund(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.refunds")) return kit.forbidden();
        Row o = db.first("select o.*, c.admin_id from orders o join courses c on c.id = o.course_id where o.id = ? and o.landing_page_id is not null", id);
        if (o == null) return kit.fail(HttpStatus.NOT_FOUND, "Order not found.");
        Long scope = kit.teacherScope(admin);
        if (scope != null && o.lng("admin_id", -1) != scope) return kit.forbidden();
        if (o.lng("payment_status", 0) != 1 && o.lng("payment_status", 0) != 4) return kit.fail(HttpStatus.CONFLICT, "Only paid orders can be refunded.");
        String note = Input.of(request, json).get("note");
        if (note == null || note.isBlank()) return kit.invalid(Map.of("note", "Say why the money goes back (kept in the audit log)."));
        String option = o.str("payment_option", "");
        String gateway = option.startsWith("PayPal") ? (option.contains("test") ? "TEST" : "PAYPAL")
                : option.startsWith("RazorPay") ? (option.contains("test") ? "TEST" : "RAZORPAY") : option.contains("Simulated") ? "TEST" : "MANUAL";
        refunds.flag(new RefundQueue.Flag("COURSE", String.valueOf(id), o.str("payment_id", "order-" + id), gateway, RefundQueue.dec(o.get("price")),
                "usd".equals(o.str("price_type")) ? "USD" : "INR", "OTHER", "Landing page refund: " + LandingCoupons.cut(note.trim(), 300), o.str("billing_email"), o.lng("user_id")));
        Row q = db.first("select id from payment_refunds where source = 'COURSE' and source_ref = ? and status in ('PENDING', 'FAILED') order by id desc limit 1", String.valueOf(id));
        if (q == null) return kit.fail(HttpStatus.CONFLICT, "This order already has a refund in progress or done.");
        RefundQueue.Decision d = refunds.refund(q.lng("id"), admin, note.trim());
        audit.log(admin, "landing_order", id, "update", "Refund " + (d.ok() ? "sent" : "failed") + ": " + d.message());
        return d.ok() ? kit.ok(Map.of("message", d.message())) : kit.fail(HttpStatus.BAD_GATEWAY, d.message());
    }

    // --------------------------------------------------------------------------------------------------- media

    @GetMapping("/admin/api/landing/media")
    public ResponseEntity<String> media(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing")) return kit.forbidden();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Row r : db.select("select m.*, a.name as uploaded_by_name from landing_media m left join admins a on a.id = m.uploaded_by order by m.id desc limit 300")) {
            Map<String, Object> x = new LinkedHashMap<>(r);
            x.put("thumb", LandingImages.sized(r.str("url"), 320));
            rows.add(x);
        }
        // course photos and instructor portraits are already in the site: offered too, read only
        List<Map<String, Object>> site = new ArrayList<>();
        for (Row r : db.select("select p.course_id, p.photo_name, c.title from course_photos p join courses c on c.id = p.course_id order by p.id desc limit 40")) {
            String url = "/storage/uploads/courses/" + r.get("course_id") + "/" + r.str("photo_name");
            site.add(Map.of("url", url, "thumb", LandingImages.sized(url, 320), "label", "Course: " + r.str("title")));
        }
        for (Row r : db.select("select id, name, profile_photo from admins where profile_photo is not null and profile_photo <> ''")) {
            String url = "/storage/uploads/teachers/" + r.get("id") + "/" + r.str("profile_photo");
            site.add(Map.of("url", url, "thumb", LandingImages.sized(url, 320), "label", "Artist: " + r.str("name")));
        }
        return kit.ok(Map.of("media", rows, "site", site));
    }

    @PostMapping("/admin/api/landing/media")
    public ResponseEntity<String> upload(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.content")) return kit.forbidden();
        Input in = Input.of(request, json);
        UploadedFile f = in.file("file");
        if (f == null) return kit.invalid(Map.of("file", "Choose an image."));
        String error = AdminKit.checkImage(f);
        if (error != null) return kit.invalid(Map.of("file", error));
        int[] size = dimensions(f.getContent());
        if (size == null) return kit.invalid(Map.of("file", "That file couldn't be read as an image."));
        if (size[0] < 600) return kit.invalid(Map.of("file", "Use an image at least 600 px wide so it stays sharp on large screens."));
        String dir = "uploads/landing/" + YearMonth.now();
        String name = storage.storeUpload(f, dir);
        String path = dir + "/" + name;
        String url = Storage.publicUrl(path);
        String alt = LandingCoupons.cut(LandingConfig.str(in.get("alt")), 255);
        long id = db.insertReturningId("landing_media", Row.of("path", path, "url", url, "alt", alt.isEmpty() ? null : alt, "width", size[0], "height", size[1],
                "bytes", (int) f.getSize(), "uploaded_by", admin.get("id"), "created_at", LocalDateTime.now()));
        return kit.ok(Map.of("id", id, "url", url, "thumb", LandingImages.sized(url, 320), "width", size[0], "height", size[1], "message", "Uploaded."));
    }

    @PutMapping("/admin/api/landing/media/{id}")
    public ResponseEntity<String> mediaAlt(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.content")) return kit.forbidden();
        String alt = LandingCoupons.cut(LandingConfig.str(Input.of(request, json).get("alt")), 255);
        db.update("update landing_media set alt = ? where id = ?", alt.isEmpty() ? null : alt, id);
        return kit.ok(Map.of("message", "Saved."));
    }

    @DeleteMapping("/admin/api/landing/media/{id}")
    public ResponseEntity<String> mediaDelete(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.content")) return kit.forbidden();
        Row m = db.find("landing_media", id);
        if (m == null) return kit.fail(HttpStatus.NOT_FOUND, "Not found.");
        String url = m.str("url");
        long used = db.count("select count(*) from landing_pages where draft_config like ?", "%" + url + "%")
                + db.count("select count(*) from landing_page_versions v join landing_pages p on p.published_version_id = v.id where v.config like ?", "%" + url + "%");
        if (used > 0) return kit.fail(HttpStatus.CONFLICT, "A landing page still uses this image. Replace it there first.");
        db.deleteById("landing_media", id);
        return kit.ok(Map.of("message", "Removed from the library."));
    }

    private static int[] dimensions(byte[] bytes) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            return img == null ? null : new int[]{img.getWidth(), img.getHeight()};
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------- team access

    /**
     * Who can do what in this section: every staff member with the landing permissions their persona gives and the ones
     * granted to them personally (admins.extra_capabilities). Anyone in the section can look; only a super admin
     * (people.manage) can change grants. Super admins already have everything, so they are not editable.
     */
    @GetMapping("/admin/api/landing/team")
    public ResponseEntity<String> team(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing")) return kit.forbidden();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Row a : db.select("select id, name, email, admin_type, extra_capabilities from admins order by case admin_type when 'admin' then 0 when 'moderator' then 1 else 2 end, name")) {
            String persona = Personas.of(a);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.get("id"));
            m.put("name", a.get("name"));
            m.put("email", a.get("email"));
            m.put("persona", persona);
            m.put("personaLabel", Personas.label(persona));
            m.put("fromPersona", Personas.personaCapabilities(persona).stream().filter(c -> c.startsWith("landing")).toList());
            m.put("granted", Personas.granted(a));
            m.put("effective", Personas.capabilities(a).stream().filter(c -> c.startsWith("landing")).toList());
            m.put("editable", !"admin".equals(persona));
            m.put("ownCoursesOnly", "teacher".equals(persona));
            rows.add(m);
        }
        return kit.ok(Map.of("team", rows, "roles", Personas.LANDING_ROLES, "grantable", Personas.GRANTABLE, "canEdit", kit.can(admin, "people.manage")));
    }

    @PutMapping("/admin/api/landing/team/{id}")
    public ResponseEntity<String> grant(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "people.manage")) return kit.forbidden();
        Row target = db.find("admins", id);
        if (target == null) return kit.fail(HttpStatus.NOT_FOUND, "Not found.");
        if ("admin".equals(Personas.of(target))) return kit.fail(HttpStatus.CONFLICT, "Super admins already have every landing page permission.");
        List<String> wanted = new ArrayList<>();
        for (Object o : LandingConfig.list(Input.of(request, json).raw("capabilities"))) {
            String c = LandingConfig.str(o);
            if (!Personas.GRANTABLE.contains(c)) return kit.invalid(Map.of("capabilities", "Unknown permission: " + LandingCoupons.cut(c, 40)));
            if (!wanted.contains(c)) wanted.add(c);
        }
        // any landing permission needs the section itself
        if (!wanted.isEmpty() && !wanted.contains("landing")) wanted.add(0, "landing");
        List<String> before = Personas.granted(target);
        db.updateById("admins", id, Row.of("extra_capabilities", wanted.isEmpty() ? null : String.join(",", wanted), "updated_at", LocalDateTime.now()));
        audit.change(admin, "admin", id, "update", "Landing page permissions for " + target.str("name"), Map.of("granted", before), Map.of("granted", wanted));
        return kit.ok(Map.of("message", wanted.isEmpty() ? "Personal landing permissions removed." : "Permissions saved. They apply from " + target.str("name") + "'s next click.",
                "granted", wanted));
    }

    // ------------------------------------------------------------------------------------------------ settings

    @GetMapping("/admin/api/landing/settings")
    public ResponseEntity<String> getSettings(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing")) return kit.forbidden();
        return kit.ok(Map.of("settings", settings(), "canEdit", kit.can(admin, "landing.publish") && kit.can(admin, "landing.payments")));
    }

    @PutMapping("/admin/api/landing/settings")
    public ResponseEntity<String> putSettings(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.publish") || !kit.can(admin, "landing.payments")) return kit.forbidden();
        Map<String, Object> in = Input.of(request, json).all();
        Map<String, String> e = new LinkedHashMap<>();
        Map<String, Object> s = new LinkedHashMap<>();
        String template = LandingConfig.str(in.get("template"));
        s.put("template", LandingCatalog.template(template) == null ? LandingCatalog.TEMPLATES.get(0).key() : template);
        String wa = LandingConfig.str(in.get("whatsapp")).replace(" ", "");
        if (!wa.isEmpty() && !wa.matches("^\\+?[0-9]{8,15}$")) e.put("whatsapp", "Use the number with country code, like +919876543210.");
        s.put("whatsapp", wa);
        String access = LandingConfig.str(in.get("accessDuration"));
        if (access.length() > 120) e.put("accessDuration", "At most 120 characters.");
        s.put("accessDuration", access);
        String refund = LandingConfig.str(in.get("refundPolicy"));
        if (refund.length() > 1000) e.put("refundPolicy", "At most 1000 characters.");
        s.put("refundPolicy", refund);
        java.math.BigDecimal tax = LandingCoupons.dec(in.get("taxRate"));
        if (tax != null && (tax.signum() < 0 || tax.compareTo(java.math.BigDecimal.valueOf(50)) > 0)) e.put("taxRate", "Between 0 and 50%.");
        s.put("taxRate", tax);
        s.put("taxMode", "ADDED".equals(LandingConfig.str(in.get("taxMode"))) ? "ADDED" : "INCLUDED");
        Map<String, Object> co = LandingConfig.map(in.get("checkout"));
        Map<String, Object> checkout = new LinkedHashMap<>();
        for (String k : List.of("requirePhone", "requireAddress", "requireTerms", "requirePrivacy", "showCoupon", "showSummary")) {
            checkout.put(k, LandingConfig.bool(co.get(k), List.of("requirePhone", "requireTerms", "showCoupon", "showSummary").contains(k)));
        }
        s.put("checkout", checkout);
        if (!e.isEmpty()) return kit.invalid(e);
        Map<String, Object> before = settings();
        LocalDateTime now = LocalDateTime.now();
        if (db.update("update settings set meta_value = ?, updated_at = ? where meta_tag = 'landing_pages' and meta_key = 'defaults'", json.encode(s), now) == 0) {
            db.insert("settings", Row.of("meta_tag", "landing_pages", "meta_key", "defaults", "meta_value", json.encode(s), "created_at", now, "updated_at", now));
        }
        audit.change(admin, "landing_settings", 0, "update", "Landing page defaults", before, s);
        return kit.ok(Map.of("message", "Defaults saved. They apply to pages created from now on.", "settings", s));
    }

    private Map<String, Object> settings() {
        Row r = db.first("select meta_value from settings where meta_tag = 'landing_pages' and meta_key = 'defaults' order by id desc limit 1");
        Map<String, Object> m = r == null ? null : json.decodeMap(r.str("meta_value"));
        if (m == null) {
            m = new LinkedHashMap<>();
            m.put("template", LandingCatalog.TEMPLATES.get(0).key());
            m.put("whatsapp", "");
            m.put("accessDuration", "");
            m.put("refundPolicy", "");
            m.put("taxRate", null);
            m.put("taxMode", "INCLUDED");
            m.put("checkout", Map.of("requirePhone", true, "requireAddress", false, "requireTerms", true, "requirePrivacy", false, "showCoupon", true, "showSummary", true));
        }
        return m;
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private Map<String, Object> lookups(Row admin) {
        Map<String, Object> m = new LinkedHashMap<>();
        Long scope = kit.teacherScope(admin);
        m.put("courses", db.select("select c.id, c.title, c.slug, c.status, a.name as artist, c.admin_id from courses c left join admins a on a.id = c.admin_id"
                + (scope == null ? "" : " where c.admin_id = " + scope.longValue()) + " order by c.title"));
        m.put("artists", db.select("select id, name, admin_type from admins where admin_type in ('admin', 'teacher') order by name"));
        m.put("testimonials", db.select("select id, name, title from testimonials where status = 1 order by id desc limit 100"));
        m.put("pages", db.select("select p.id, p.name, p.slug, p.status from landing_pages p join courses c on c.id = p.course_id"
                + (scope == null ? "" : " where c.admin_id = " + scope.longValue()) + " order by p.name"));
        m.put("campaigns", db.select("select distinct campaign from (select campaign from landing_pages where campaign is not null union select campaign from landing_coupons where campaign is not null"
                + " union select utm_campaign as campaign from orders where utm_campaign is not null and landing_page_id is not null) x order by campaign limit 100"));
        return m;
    }

    private static Map<String, Object> catalog() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("templates", LandingCatalog.TEMPLATES.stream().map(t -> Map.of("key", t.key(), "name", t.name(), "description", t.description(), "hero", t.hero(),
                "design", t.design(), "sections", t.sections())).toList());
        m.put("sections", LandingCatalog.SECTIONS.stream().map(s -> Map.of("key", s.key(), "label", s.label(), "description", s.description(), "layouts", s.layouts(), "single", s.single())).toList());
        m.put("methods", LandingCatalog.METHODS.stream().map(x -> Map.of("key", x.key(), "label", x.label(), "group", x.group(), "hint", x.hint())).toList());
        m.put("providers", LandingCatalog.PROVIDERS.stream().map(p -> Map.of("key", p.key(), "label", p.label(), "currency", p.currency(), "integrated", p.integrated(),
                "methods", p.methods(), "note", p.note())).toList());
        m.put("defaultSections", LandingCatalog.SECTIONS.stream().collect(java.util.stream.Collectors.toMap(LandingCatalog.SectionType::key,
                s -> LandingConfig.section(s.key(), s.layouts().get(0)))));
        return m;
    }

    private boolean mayEdit(Row admin, Row page) {
        Long scope = kit.teacherScope(admin);
        return scope == null || db.count("select count(*) from courses where id = ? and admin_id = ?", page.get("course_id"), scope) > 0;
    }

    private boolean courseAllowed(Row admin, long courseId) {
        Long scope = kit.teacherScope(admin);
        return scope == null || db.count("select count(*) from courses where id = ? and admin_id = ?", courseId, scope) > 0;
    }

    private static Long lng(String s) {
        try {
            return s == null || s.isBlank() ? null : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int intOr(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
