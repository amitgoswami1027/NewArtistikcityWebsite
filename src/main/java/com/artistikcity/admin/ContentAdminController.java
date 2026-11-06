package com.artistikcity.admin;

import com.artistikcity.http.NotFoundException;
import com.artistikcity.support.Db;
import com.artistikcity.support.HtmlCleaner;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import com.artistikcity.support.UploadedFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Site content without its own module: contact messages (/admin/contacts), the FAQ (/admin/faq) and
 * site settings & SEO (/admin/settings).
 */
@Controller
public class ContentAdminController {

    /** Editable settings: tag -> keys (anything else in the request is ignored). */
    static final Map<String, List<String>> SETTINGS = new LinkedHashMap<>();

    static {
        SETTINGS.put("home", List.of("course_text_1", "course_text_2", "course_text_3", "workshop_text_1", "workshop_text_2", "workshop_text_3"));
        SETTINGS.put("seo", List.of("site_title", "meta_description", "meta_keywords", "og_image"));
        SETTINGS.put("contact", List.of("email", "phone", "address"));
        SETTINGS.put("social", List.of("facebook", "instagram", "youtube", "linkedin", "x", "pinterest", "whatsapp", "hidden"));
        // SEO & Layout Controls (/admin/setup/site-settings)
        SETTINGS.put("landing", List.of("hero_heading", "hero_emphasis", "hero_subtext", "cta_label", "cta_link", "search_placeholder"));
        SETTINGS.put("nav", List.of("menu"));
        SETTINGS.put("cookie", List.of("enabled", "message", "accept_label", "decline_label", "policy_link"));
    }

    /** Longest allowed value per key (default 255). */
    static int maxLength(String tag, String key) {
        return switch (tag + "." + key) {
            case "seo.meta_description", "contact.address", "cookie.message" -> 500;
            case "landing.hero_heading" -> 120;
            case "landing.hero_emphasis" -> 80;
            case "landing.hero_subtext" -> 300;
            case "landing.cta_label", "cookie.accept_label", "cookie.decline_label" -> 40;
            case "nav.menu" -> 4000;
            default -> 255;
        };
    }

    static final List<String> SOCIAL_NETWORKS = List.of("facebook", "instagram", "youtube", "linkedin", "x", "pinterest", "whatsapp");

    /** A site link: a path on this site ("/courses?type=all", no "//") or a full https:// address. */
    static boolean siteLink(String s) {
        return s.matches("^/(?!/)[^\\s<>\"']*$") || s.matches("(?i)^https://[^\\s<>\"']+$");
    }

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final Storage storage;
    private final SiteSettings site;
    private final Audit audit;

    public ContentAdminController(AdminKit kit, Db db, Json json, Storage storage, SiteSettings site, Audit audit) {
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.storage = storage;
        this.site = site;
        this.audit = audit;
    }

    // ================================================================ contacts

    private Grid contacts(HttpServletRequest request) {
        return Grid.from(db, request)
                .columns("id, name, email, mobile_no, message, status, read_at, created_at")
                .from("contacts")
                .search("name", "email", "mobile_no", "message")
                .sort("date", "created_at").sort("name", "name").defaultSort("date", "desc").tieBreak("id desc")
                .filter("status", v -> switch (v) {
                    case "new" -> Grid.where("status = 1");
                    case "read" -> Grid.where("status = 2");
                    case "archived" -> Grid.where("status = 3");
                    default -> null;
                });
    }

    @GetMapping({"/admin/contacts", "/admin/contacts/"})
    public ResponseEntity<String> contactsPage(HttpServletRequest request) {
        return kit.page(request, "settings", "Admin/Contacts", () -> {
            Map<String, Object> p = new LinkedHashMap<>();
            Supplier<Object> grid = () -> contacts(request).page();
            p.put("grid", grid);
            Supplier<Object> counts = () -> Map.of(
                    "new", db.count("select count(*) from contacts where status = 1"),
                    "read", db.count("select count(*) from contacts where status = 2"),
                    "archived", db.count("select count(*) from contacts where status = 3"),
                    "all", db.count("select count(*) from contacts"));
            p.put("counts", counts);
            return p;
        });
    }

    @GetMapping("/admin/contacts/export")
    public ResponseEntity<String> exportContacts(HttpServletRequest request) {
        if (!kit.can(kit.admin(request), "settings")) {
            return kit.forbidden();
        }
        List<List<Object>> rows = new ArrayList<>();
        for (Row r : contacts(request).all(20000)) {
            rows.add(List.of(r.str("created_at", ""), r.str("name", ""), r.str("email", ""), r.str("mobile_no", ""), r.str("message", "")));
        }
        return kit.csv("contact-messages-" + LocalDate.now() + ".csv", List.of("Date", "Name", "Email", "Phone", "Message"), rows);
    }

    @PostMapping("/admin/api/contacts/{id}/status")
    public ResponseEntity<String> contactStatus(HttpServletRequest request, @PathVariable("id") long id) {
        if (!kit.can(kit.admin(request), "settings")) {
            return kit.forbidden();
        }
        NotFoundException.orFail(db.find("contacts", id));
        String s = Input.of(request, json).get("status");
        int status = "new".equals(s) ? 1 : "archived".equals(s) ? 3 : 2;
        Row v = Row.of("status", status);
        if (status == 2) {
            v.put("read_at", LocalDateTime.now());
        }
        db.updateById("contacts", id, v);
        return kit.ok(Map.of("message", status == 3 ? "Archived." : status == 1 ? "Marked as new." : "Marked as read."));
    }

    @PostMapping("/admin/api/contacts/delete")
    public ResponseEntity<String> deleteContacts(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        int n = 0;
        if (Input.of(request, json).raw("ids") instanceof List<?> ids) {
            for (Object o : ids) {
                if (String.valueOf(o).matches("\\d+(\\.0)?")) {
                    n += db.update("delete from contacts where id = ?", (long) Double.parseDouble(String.valueOf(o)));
                }
            }
        }
        audit.log(admin, "contact", 0, "deleted", n + " messages");
        return kit.ok(Map.of("message", n + (n == 1 ? " message" : " messages") + " deleted."));
    }

    // ===================================================================== FAQ

    private List<Row> faqs() {
        return db.select("select * from faqs order by sort_order, id");
    }

    @GetMapping({"/admin/faq", "/admin/faq/", "/admin/faqs"})
    public ResponseEntity<String> faqPage(HttpServletRequest request) {
        return kit.page(request, "settings", "Admin/Faq", () -> Map.of("faqs", faqs()));
    }

    @PostMapping("/admin/api/faqs")
    public ResponseEntity<String> saveFaq(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        Input in = Input.of(request, json);
        Map<String, String> errors = new LinkedHashMap<>();
        String question = AdminKit.trim(in.get("question"), 500);
        if (AdminKit.blank(question)) {
            errors.put("question", "Write the question.");
        }
        String answer = HtmlCleaner.clean(in.get("answer"));
        if (HtmlCleaner.text(answer, 10).isBlank()) {
            errors.put("answer", "Write the answer.");
        }
        if (!errors.isEmpty()) {
            return kit.invalid(errors);
        }
        Row v = Row.of("question", question, "answer", answer, "category", AdminKit.trim(in.get("category"), 100),
                "status", in.has("status") && !in.bool("status") ? 0 : 1);
        Long id = in.lng("id");
        if (id == null) {
            v.put("sort_order", db.count("select coalesce(max(sort_order), 0) from faqs") + 1);
            id = db.insert("faqs", v);
        } else {
            NotFoundException.orFail(db.find("faqs", id));
            db.updateById("faqs", id, v);
        }
        audit.log(admin, "faq", id, in.lng("id") == null ? "created" : "updated", question);
        return kit.ok(Map.of("faqs", faqs(), "message", in.lng("id") == null ? "Question added." : "Saved."));
    }

    @PostMapping("/admin/api/faqs/{id}/move")
    public ResponseEntity<String> moveFaq(HttpServletRequest request, @PathVariable("id") long id) {
        if (!kit.can(kit.admin(request), "settings")) {
            return kit.forbidden();
        }
        String dir = Input.of(request, json).get("direction");
        List<Row> list = faqs();
        int i = -1;
        for (int k = 0; k < list.size(); k++) {
            if (list.get(k).lng("id") == id) {
                i = k;
            }
        }
        int j = "up".equals(dir) ? i - 1 : i + 1;
        if (i >= 0 && j >= 0 && j < list.size()) {
            Row a = list.get(i);
            list.set(i, list.get(j));
            list.set(j, a);
        }
        for (int k = 0; k < list.size(); k++) {
            db.update("update faqs set sort_order = ? where id = ?", k + 1, list.get(k).get("id"));
        }
        return kit.ok(Map.of("faqs", faqs(), "message", "Order saved."));
    }

    @DeleteMapping("/admin/api/faqs/{id}")
    public ResponseEntity<String> deleteFaq(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        Row f = NotFoundException.orFail(db.find("faqs", id));
        db.deleteById("faqs", id);
        audit.log(admin, "faq", id, "deleted", f.str("question"));
        return kit.ok(Map.of("faqs", faqs(), "message", "Question deleted."));
    }

    // ================================================================ settings

    @GetMapping({"/admin/settings", "/admin/settings/"})
    public ResponseEntity<String> legacySettingsPage(HttpServletRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND).header("Location", "/admin/setup/site-settings").build();
    }

    /** SEO & Layout Controls: landing hero, search & sharing, menu builder, cookie banner, social links, contact details. */
    @GetMapping({"/admin/setup/site-settings", "/admin/setup/site-settings/"})
    public ResponseEntity<String> settingsPage(HttpServletRequest request) {
        return kit.page(request, "settings", "Admin/Setup/SiteSettings", () -> {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : SETTINGS.entrySet()) {
                Map<String, String> group = new LinkedHashMap<>();
                for (String k : e.getValue()) {
                    group.put(k, site.get(e.getKey(), k, ""));
                }
                values.put(e.getKey(), group);
            }
            return Map.of("values", values, "defaults", Map.of("site_title", SiteSettings.DEFAULT_TITLE, "meta_description", SiteSettings.DEFAULT_DESCRIPTION));
        });
    }

    @PostMapping({"/admin/api/settings", "/admin/settings/update", "/admin/api/setup/site-settings"})
    public ResponseEntity<String> saveSettings(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        Input in = Input.of(request, json);
        Map<String, Object> d = json.decodeMap(in.get("data"));
        if (d == null) {
            return kit.invalid(Map.of("form", "Nothing to save."));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Map<String, String>> clean = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : SETTINGS.entrySet()) {
            Object raw = d.get(e.getKey());
            if (!(raw instanceof Map<?, ?> m)) continue;                 // groups not sent are left as they are
            Map<String, String> group = new LinkedHashMap<>();
            for (String k : e.getValue()) {
                if (!m.containsKey(k)) {                                  // keys not sent keep their saved value
                    group.put(k, site.get(e.getKey(), k, ""));
                    continue;
                }
                Object v = m.get(k);
                String s = v == null ? "" : v instanceof String str ? str.trim() : json.encode(v);
                group.put(k, s);
                String field = e.getKey() + "." + k;
                if ("nav.menu".equals(field)) {
                    continue;                                             // checked below
                } else if (s.length() > maxLength(e.getKey(), k)) {
                    errors.put(field, "Keep this under " + maxLength(e.getKey(), k) + " characters.");
                } else if ("social".equals(e.getKey()) && "hidden".equals(k)) {
                    for (String n : s.isEmpty() ? new String[0] : s.split(",")) {
                        if (!SOCIAL_NETWORKS.contains(n.trim())) { errors.put(field, "Unknown network: " + n.trim()); break; }
                    }
                } else if ("social".equals(e.getKey()) && !s.isEmpty() && !s.matches("(?i)^https?://[^\\s<>\"']+$")) {
                    errors.put(field, "Enter the full link, starting with https://");
                } else if ("contact".equals(e.getKey()) && "email".equals(k) && !s.isEmpty() && !s.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    errors.put(field, "Enter a valid e-mail address.");
                } else if (("landing.cta_link".equals(field) || "cookie.policy_link".equals(field)) && !s.isEmpty() && !siteLink(s)) {
                    errors.put(field, "Use a page on this site such as /courses?type=all, or a full https:// link.");
                } else if ("cookie.enabled".equals(field) && !s.isEmpty() && !s.equals("1") && !s.equals("0")) {
                    errors.put(field, "Choose on or off.");
                }
            }
            clean.put(e.getKey(), group);
        }
        if (clean.containsKey("seo") && AdminKit.blank(clean.get("seo").get("site_title"))) {
            errors.put("seo.site_title", "The site needs a title for search results.");
        }
        Map<String, String> landing = clean.get("landing");
        if (landing != null) {
            if (AdminKit.blank(landing.get("hero_heading"))) errors.put("landing.hero_heading", "The landing page needs a heading.");
            if (!AdminKit.blank(landing.get("cta_label")) && AdminKit.blank(landing.get("cta_link"))) errors.putIfAbsent("landing.cta_link", "Where should the button go?");
            if (AdminKit.blank(landing.get("cta_label")) && !AdminKit.blank(landing.get("cta_link"))) errors.putIfAbsent("landing.cta_label", "Give the button a label, or clear the link.");
        }
        Map<String, String> cookie = clean.get("cookie");
        if (cookie != null && "1".equals(cookie.get("enabled")) && AdminKit.blank(cookie.get("message"))) {
            errors.put("cookie.message", "Write the banner text, or switch the banner off.");
        }
        if (clean.containsKey("nav")) {
            String menu = clean.get("nav").get("menu");
            Object parsed = null;
            String menuError = null;
            if (!AdminKit.blank(menu)) {
                try { parsed = json.decode(menu); } catch (RuntimeException ex) { parsed = null; }
                menuError = checkMenu(parsed);
            }
            if (menuError != null) errors.put("nav.menu", menuError);
            else if (parsed != null) clean.get("nav").put("menu", json.encode(normalizeMenu((List<?>) parsed)));
        }
        UploadedFile og = in.hasFile("og_image") ? in.file("og_image") : null;
        String ogError = AdminKit.checkImage(og);
        if (ogError != null) {
            errors.put("seo.og_image", ogError);
        }
        if (!errors.isEmpty()) {
            return kit.invalid(errors);
        }
        if (og != null && clean.containsKey("seo")) {
            clean.get("seo").put("og_image", "/storage/uploads/site/" + storage.storeUpload(og, "uploads/site"));
        }
        Map<String, Object> before = settingsSnapshot(clean.keySet());
        for (Map.Entry<String, Map<String, String>> e : clean.entrySet()) {
            for (Map.Entry<String, String> kv : e.getValue().entrySet()) {
                if (db.updateWhere("settings", Row.of("meta_value", kv.getValue()), "meta_tag = ? and meta_key = ?", e.getKey(), kv.getKey()) == 0) {
                    db.insert("settings", Row.of("meta_tag", e.getKey(), "meta_key", kv.getKey(), "meta_value", kv.getValue()));
                }
            }
        }
        site.invalidate();
        audit.change(admin, "settings", 0, "updated", String.join(", ", clean.keySet()), before, settingsSnapshot(clean.keySet()));
        return kit.ok(Map.of("values", clean, "message", "Settings saved. The site uses them straight away."));
    }

    /**
     * The header menu: 1 to 10 items {label (1-30 characters), href (site path or https link), visible, accent}.
     * Returns the first problem, or null.
     */
    static String checkMenu(Object parsed) {
        if (!(parsed instanceof List<?> items)) return "The menu could not be read. Reload the page and try again.";
        if (items.isEmpty()) return "Keep at least one menu item (or reset the menu to the default).";
        if (items.size() > 10) return "Up to 10 menu items fit in the header.";
        java.util.Set<String> labels = new java.util.HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof Map<?, ?> it)) return "Menu item " + (i + 1) + " is not valid.";
            String label = it.get("label") == null ? "" : String.valueOf(it.get("label")).trim();
            String href = it.get("href") == null ? "" : String.valueOf(it.get("href")).trim();
            if (label.isEmpty()) return "Menu item " + (i + 1) + " needs a label.";
            if (label.length() > 30) return "“" + label + "” is too long for the header (30 characters at most).";
            if (!labels.add(label.toLowerCase())) return "“" + label + "” is in the menu twice.";
            if (href.isEmpty() || !siteLink(href)) return "“" + label + "”: use a page on this site such as /marketplace, or a full https:// link.";
        }
        return null;
    }

    static List<Map<String, Object>> normalizeMenu(List<?> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : items) {
            Map<?, ?> it = (Map<?, ?>) o;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", String.valueOf(it.get("label")).trim());
            m.put("href", String.valueOf(it.get("href")).trim());
            m.put("visible", !Boolean.FALSE.equals(it.get("visible")) && !"false".equals(String.valueOf(it.get("visible"))));
            m.put("accent", Boolean.TRUE.equals(it.get("accent")) || "true".equals(String.valueOf(it.get("accent"))));
            out.add(m);
        }
        return out;
    }

    /** The saved values of these settings groups as {"tag.key": value}, for the before/after diff of the security log. */
    private Map<String, Object> settingsSnapshot(java.util.Collection<String> tags) {
        Map<String, Object> m = new java.util.TreeMap<>();
        for (String tag : tags) {
            for (Row r : db.select("select meta_key, meta_value from settings where meta_tag = ?", tag)) {
                m.put(tag + "." + r.str("meta_key"), r.get("meta_value"));
            }
        }
        return m;
    }
}
