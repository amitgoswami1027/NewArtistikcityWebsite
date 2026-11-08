package com.artistikcity.landing;

import com.artistikcity.support.Db;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Landing page lifecycle: DRAFT → (SCHEDULED) → PUBLISHED, unpublish back to DRAFT, ARCHIVED. Editing changes only the
 * draft; publishing copies the draft into a new numbered version and points the page at it, so the public page never shows
 * a half-edited draft and any earlier version can be restored (rollback publishes it as a new version).
 */
@Service
public class LandingPages {

    private static final Logger log = LoggerFactory.getLogger(LandingPages.class);
    public static final java.util.regex.Pattern SLUG = java.util.regex.Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Set<String> RESERVED = Set.of("enroll", "checkout", "new", "edit", "preview", "admin", "api", "e", "order");

    /** Every landing page has its own address: /landing/{slug}. It never takes over an existing page of the site. */
    public static final String PREFIX = "/landing/";

    private final Db db;
    private final Json json;

    public LandingPages(Db db, Json json) {
        this.db = db;
        this.json = json;
    }

    /** Null while the landing migrations aren't applied: public pages then simply have no landing page (no error). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private LandingSchema schema;

    private boolean ready() {
        return schema == null || schema.ok();
    }

    public Row find(long id) {
        return db.find("landing_pages", id);
    }

    public Map<String, Object> draft(Row page) {
        Map<String, Object> m = json.decodeMap(page.str("draft_config"));
        return m == null ? new LinkedHashMap<>() : m;
    }

    /** The live page for a public address, with its published config; null when there is none. */
    public Row published(String slug) {
        if (slug == null || !SLUG.matcher(slug).matches() || !ready()) {
            return null;
        }
        return db.first("select p.*, v.config as published_config, v.version_no from landing_pages p join landing_page_versions v on v.id = p.published_version_id"
                + " where p.slug = ? and p.status = 'PUBLISHED'", slug);
    }

    public static boolean isSite(Row page) {
        return page != null && "SITE".equals(page.str("kind", "COURSE"));
    }

    /** Where visitors see the page: /landing/{slug}. */
    public static String publicPath(Row page) {
        return path(page.str("slug"));
    }

    public static String path(String slug) {
        return PREFIX + slug;
    }

    /** The checkout of a course page. */
    public static String enrollPath(String slug) {
        return PREFIX + slug + "/enroll";
    }

    /** A free address made from a name: "Diwali offer" -> diwali-offer, diwali-offer-2, ... */
    public String freeSlug(String name, Long exceptId) {
        String base = java.text.Normalizer.normalize(name == null ? "" : name, java.text.Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
                .toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.length() > 100) base = base.substring(0, 100).replaceAll("-+$", "");
        if (base.length() < 3) base = "page" + (base.isEmpty() ? "" : "-" + base);
        if (RESERVED.contains(base)) base = base + "-page";
        String slug = base;
        for (int i = 2; db.count("select count(*) from landing_pages where slug = ? and id <> ?", slug, exceptId == null ? 0 : exceptId) > 0; i++) {
            slug = base + "-" + i;
        }
        return slug;
    }

    // --------------------------------------------------------------------------------------------- basics

    public Map<String, String> checkBasics(Map<String, Object> in, Long id) {
        Row existing = id == null ? null : find(id);
        boolean site = existing != null ? isSite(existing) : "SITE".equals(LandingConfig.str(in.get("kind")));
        Map<String, String> e = new LinkedHashMap<>();
        String name = LandingConfig.str(in.get("name"));
        if (name.isEmpty()) {
            e.put("name", "Give the page a name (only staff see it).");
        } else if (name.length() > 190) {
            e.put("name", "At most 190 characters.");
        }
        Long courseId = LandingCoupons.ids(List.of(LandingConfig.str(in.get("courseId")))).stream().findFirst().orElse(null);
        Row course = site || courseId == null ? null : db.first("select id, slug from courses where id = ?", courseId);
        if (!site && course == null) {
            e.put("courseId", "Choose the course this page sells.");
        }
        String slug = LandingConfig.str(in.get("slug")).toLowerCase();
        if (!SLUG.matcher(slug).matches() || slug.length() < 3 || slug.length() > 120) {
            e.put("slug", "Use 3-120 lowercase letters, numbers and single dashes.");
        } else if (RESERVED.contains(slug)) {
            e.put("slug", "That address is reserved.");
        } else if (db.count("select count(*) from landing_pages where slug = ? and id <> ?", slug, id == null ? 0 : id) > 0) {
            e.put("slug", "Another landing page already uses this address.");
        }
        if (LandingConfig.str(in.get("campaign")).length() > 120) {
            e.put("campaign", "At most 120 characters.");
        }
        if (LandingConfig.str(in.get("description")).length() > 500) {
            e.put("description", "At most 500 characters.");
        }
        return e;
    }

    public long create(Map<String, Object> in, String templateKey, Long adminId) {
        LandingCatalog.Template t = LandingCatalog.template(templateKey);
        if (t == null) {
            t = LandingCatalog.TEMPLATES.get(0);
        }
        boolean site = "SITE".equals(LandingConfig.str(in.get("kind")));
        LocalDateTime now = LocalDateTime.now();
        long id = db.insert("landing_pages", Row.of("name", LandingConfig.str(in.get("name")), "slug", LandingConfig.str(in.get("slug")).toLowerCase(),
                "kind", site ? "SITE" : "COURSE", "site_route", null,
                "course_id", site ? null : Long.parseLong(LandingConfig.str(in.get("courseId")).replace(".0", "")),
                "campaign", blankToNull(LandingConfig.str(in.get("campaign"))), "description", blankToNull(LandingConfig.str(in.get("description"))),
                "template_key", t.key(), "status", "DRAFT", "draft_config", json.encode(LandingConfig.initial(t)), "draft_saved_at", now, "draft_saved_by", adminId,
                "created_by", adminId, "created_at", now, "updated_at", now));
        db.insertPlain("landing_pricing", Row.of("landing_page_id", id, "coupons_enabled", 1, "tax_mode", "INCLUDED", "tax_rate", 0, "tax_label", "GST",
                "updated_by", adminId, "updated_at", now));
        db.insertPlain("landing_checkout", Row.of("landing_page_id", id, "updated_by", adminId, "updated_at", now));
        db.insertPlain("landing_payment_settings", Row.of("landing_page_id", id, "mode", "GLOBAL", "default_provider", "razorpay", "updated_by", adminId, "updated_at", now));
        return id;
    }

    public void updateBasics(long id, Map<String, Object> in) {
        boolean site = isSite(find(id));
        db.updateById("landing_pages", id, Row.of("name", LandingConfig.str(in.get("name")), "slug", LandingConfig.str(in.get("slug")).toLowerCase(),
                "course_id", site ? null : Long.parseLong(LandingConfig.str(in.get("courseId")).replace(".0", "")),
                "campaign", blankToNull(LandingConfig.str(in.get("campaign"))), "description", blankToNull(LandingConfig.str(in.get("description"))),
                "updated_at", LocalDateTime.now()));
    }

    // ----------------------------------------------------------------------------------------------- draft

    public LandingConfig.Result saveDraft(long id, Object config, Long adminId) {
        LandingConfig.Result r = LandingConfig.normalize(config, isSite(find(id)));
        if (r.ok()) {
            LocalDateTime now = LocalDateTime.now();
            db.updateById("landing_pages", id, Row.of("draft_config", json.encode(r.config()), "draft_saved_at", now, "draft_saved_by", adminId, "updated_at", now));
        }
        return r;
    }

    /** Switch template: its design and hero layout, sections in its order (content of sections already on the page is kept). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> applyTemplate(Map<String, Object> config, LandingCatalog.Template t) {
        Map<String, Object> c = new LinkedHashMap<>(config);
        c.put("design", new LinkedHashMap<>(t.design()));
        List<Map<String, Object>> current = new ArrayList<>();
        for (Object o : LandingConfig.list(config.get("sections"))) {
            current.add(new LinkedHashMap<>((Map<String, Object>) o));
        }
        List<Map<String, Object>> next = new ArrayList<>();
        for (String key : t.sections()) {
            Map<String, Object> found = current.stream().filter(s -> key.equals(s.get("type"))).findFirst().orElse(null);
            if (found != null) {
                current.remove(found);
                found.put("enabled", true);
            } else {
                found = LandingConfig.section(key, LandingCatalog.section(key).layouts().get(0));
            }
            if ("hero".equals(key)) {
                found.put("layout", t.hero());
            }
            next.add(found);
        }
        // sections the template doesn't use are kept, switched off, at the end (nothing is lost)
        for (Map<String, Object> s : current) {
            s.put("enabled", false);
            next.add(s);
        }
        c.put("sections", next);
        return c;
    }

    public void setTemplate(long id, String key, Map<String, Object> config, long adminId) {
        LocalDateTime now = LocalDateTime.now();
        db.updateById("landing_pages", id, Row.of("template_key", key, "draft_config", json.encode(config), "draft_saved_at", now, "draft_saved_by", adminId, "updated_at", now));
    }

    // -------------------------------------------------------------------------------------------- publishing

    /** Publishes the current draft as a new version. Returns errors (empty = published). */
    public Map<String, String> publish(long id, Long adminId, String note) {
        Row page = find(id);
        LandingConfig.Result r = LandingConfig.normalize(draft(page), isSite(page));
        if (!r.ok()) {
            return r.errors();
        }
        int next = (int) db.count("select coalesce(max(version_no), 0) from landing_page_versions where landing_page_id = ?", id) + 1;
        LocalDateTime now = LocalDateTime.now();
        long vid = db.insertReturningId("landing_page_versions", Row.of("landing_page_id", id, "version_no", next, "template_key", page.str("template_key"),
                "config", json.encode(r.config()), "note", note == null || note.isBlank() ? null : LandingCoupons.cut(note.trim(), 255),
                "created_by", adminId, "created_at", now));
        db.updateById("landing_pages", id, Row.of("status", "PUBLISHED", "published_version_id", vid, "published_at", now, "publish_at", null,
                "archived_at", null, "updated_at", now));
        return Map.of();
    }

    public Map<String, String> schedule(long id, LocalDateTime at) {
        if (at == null || !at.isAfter(LocalDateTime.now().plusMinutes(1))) {
            return Map.of("publishAt", "Choose a time at least a couple of minutes from now.");
        }
        Row page = find(id);
        LandingConfig.Result r = LandingConfig.normalize(draft(page), isSite(page));
        if (!r.ok()) {
            return r.errors();
        }
        db.updateById("landing_pages", id, Row.of("status", "SCHEDULED", "publish_at", at, "updated_at", LocalDateTime.now()));
        return Map.of();
    }

    public void unpublish(long id) {
        db.updateById("landing_pages", id, Row.of("status", "DRAFT", "publish_at", null, "updated_at", LocalDateTime.now()));
    }

    public void archive(long id) {
        LocalDateTime now = LocalDateTime.now();
        db.updateById("landing_pages", id, Row.of("status", "ARCHIVED", "publish_at", null, "archived_at", now, "updated_at", now));
    }

    public void restore(long id) {
        db.updateById("landing_pages", id, Row.of("status", "DRAFT", "archived_at", null, "updated_at", LocalDateTime.now()));
    }

    /** Only pages nobody bought through can be deleted; the others are archived instead. */
    public boolean delete(long id) {
        if (db.count("select count(*) from orders where landing_page_id = ?", id) > 0) {
            return false;
        }
        db.update("delete from landing_events where landing_page_id = ?", id);
        db.update("delete from landing_coupon_targets where target_type = 'PAGE' and target_id = ?", id);
        db.deleteById("landing_pages", id);
        return true;
    }

    public List<Row> versions(long id) {
        return db.select("select v.id, v.version_no, v.template_key, v.note, v.created_at, a.name as created_by_name, (p.published_version_id = v.id) as live"
                + " from landing_page_versions v join landing_pages p on p.id = v.landing_page_id left join admins a on a.id = v.created_by"
                + " where v.landing_page_id = ? order by v.version_no desc", id);
    }

    /** Puts an earlier version back in the draft and publishes it as a new version. */
    public Map<String, String> rollback(long id, long versionId, long adminId) {
        Row v = db.first("select * from landing_page_versions where id = ? and landing_page_id = ?", versionId, id);
        if (v == null) {
            return Map.of("version", "That version doesn't exist.");
        }
        LocalDateTime now = LocalDateTime.now();
        db.updateById("landing_pages", id, Row.of("draft_config", v.str("config"), "template_key", v.str("template_key"), "draft_saved_at", now,
                "draft_saved_by", adminId, "updated_at", now));
        return publish(id, adminId, "Restored version " + v.get("version_no"));
    }

    public long duplicate(long id, long adminId) {
        Row p = find(id);
        String base = p.str("slug") + "-copy";
        String slug = base;
        for (int i = 2; db.count("select count(*) from landing_pages where slug = ?", slug) > 0; i++) {
            slug = base + "-" + i;
        }
        LocalDateTime now = LocalDateTime.now();
        long copy = db.insert("landing_pages", Row.of("name", LandingCoupons.cut(p.str("name") + " (copy)", 190), "slug", slug, "kind", p.str("kind", "COURSE"),
                "site_route", null, "course_id", p.get("course_id"),
                "campaign", p.get("campaign"), "description", p.get("description"), "template_key", p.get("template_key"), "status", "DRAFT",
                "draft_config", p.get("draft_config"), "draft_saved_at", now, "draft_saved_by", adminId, "created_by", adminId, "created_at", now, "updated_at", now));
        for (String table : List.of("landing_pricing", "landing_checkout", "landing_payment_settings")) {
            Row r = db.first("select * from " + table + " where landing_page_id = ?", id);
            if (r != null) {
                Row n = new Row(r);
                n.put("landing_page_id", copy);
                n.put("updated_by", adminId);
                n.put("updated_at", now);
                db.insertPlain(table, n);
            }
        }
        for (Row r : db.select("select provider, enabled, priority from landing_payment_providers where landing_page_id = ?", id)) {
            db.insertReturningId("landing_payment_providers", Row.of("landing_page_id", copy, "provider", r.get("provider"), "enabled", r.get("enabled"), "priority", r.get("priority")));
        }
        for (Row r : db.select("select method, enabled, sort_order from landing_payment_methods where landing_page_id = ?", id)) {
            db.insertReturningId("landing_payment_methods", Row.of("landing_page_id", copy, "method", r.get("method"), "enabled", r.get("enabled"), "sort_order", r.get("sort_order")));
        }
        return copy;
    }

    /** Scheduled pages go live on time (checked every minute). */
    @Scheduled(cron = "15 */1 * * * *")
    public void publishDue() {
        if (!ready()) return;
        for (Row p : db.select("select id from landing_pages where status = 'SCHEDULED' and publish_at <= ?", LocalDateTime.now())) {
            Map<String, String> errors = publish(p.lng("id"), null, "Scheduled publish");
            if (!errors.isEmpty()) {
                log.warn("Scheduled landing page {} could not be published: {}", p.get("id"), errors);
                db.updateById("landing_pages", p.lng("id"), Row.of("status", "DRAFT", "publish_at", null, "updated_at", LocalDateTime.now()));
            }
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
