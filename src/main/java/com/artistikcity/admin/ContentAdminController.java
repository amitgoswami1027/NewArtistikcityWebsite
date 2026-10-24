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
        SETTINGS.put("social", List.of("facebook", "instagram", "youtube", "linkedin"));
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
    public ResponseEntity<String> settingsPage(HttpServletRequest request) {
        return kit.page(request, "settings", "Admin/Settings", () -> {
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

    @PostMapping({"/admin/api/settings", "/admin/settings/update"})
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
            Map<String, String> group = new LinkedHashMap<>();
            Object raw = d.get(e.getKey());
            for (String k : e.getValue()) {
                Object v = raw instanceof Map<?, ?> m ? m.get(k) : null;
                String s = v == null ? "" : String.valueOf(v).trim();
                group.put(k, s);
                String field = e.getKey() + "." + k;
                if (s.length() > ("meta_description".equals(k) || "address".equals(k) ? 500 : 255)) {
                    errors.put(field, "This is too long.");
                } else if ("social".equals(e.getKey()) && !s.isEmpty() && !s.matches("(?i)^https?://\\S+$")) {
                    errors.put(field, "Enter the full link, starting with https://");
                } else if ("contact".equals(e.getKey()) && "email".equals(k) && !s.isEmpty() && !s.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    errors.put(field, "Enter a valid e-mail address.");
                }
            }
            clean.put(e.getKey(), group);
        }
        if (AdminKit.blank(clean.get("seo").get("site_title"))) {
            errors.put("seo.site_title", "The site needs a title for search results.");
        }
        UploadedFile og = in.hasFile("og_image") ? in.file("og_image") : null;
        String ogError = AdminKit.checkImage(og);
        if (ogError != null) {
            errors.put("seo.og_image", ogError);
        }
        if (!errors.isEmpty()) {
            return kit.invalid(errors);
        }
        if (og != null) {
            clean.get("seo").put("og_image", "/storage/uploads/site/" + storage.storeUpload(og, "uploads/site"));
        }
        for (Map.Entry<String, Map<String, String>> e : clean.entrySet()) {
            for (Map.Entry<String, String> kv : e.getValue().entrySet()) {
                if (db.updateWhere("settings", Row.of("meta_value", kv.getValue()), "meta_tag = ? and meta_key = ?", e.getKey(), kv.getKey()) == 0) {
                    db.insert("settings", Row.of("meta_tag", e.getKey(), "meta_key", kv.getKey(), "meta_value", kv.getValue()));
                }
            }
        }
        site.invalidate();
        audit.log(admin, "settings", 0, "updated", null);
        return kit.ok(Map.of("values", clean, "message", "Settings saved. The site uses them straight away."));
    }
}
