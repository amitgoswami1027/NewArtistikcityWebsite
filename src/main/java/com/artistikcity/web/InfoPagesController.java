package com.artistikcity.web;

import com.artistikcity.admin.SitePages;
import com.artistikcity.admin.SiteSettings;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.CourseService;
import com.artistikcity.support.Db;
import com.artistikcity.support.HtmlCleaner;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Our vision (/artistikcity-vision), the instructor page (/instructor), Customer support (/customer-support) and the
 * FAQ (/faq): React pages on the site layout (Pages/Info/*), with their texts from Admin > System parameters >
 * SEO &amp; Layout Controls ({@link SitePages}) and the questions from the {@code faqs} table.
 */
@Controller
public class InfoPagesController {

    private final Inertia inertia;
    private final Db db;
    private final SitePages pages;
    private final SiteSettings site;

    public InfoPagesController(Inertia inertia, Db db, SitePages pages, SiteSettings site) {
        this.inertia = inertia;
        this.db = db;
        this.pages = pages;
        this.site = site;
    }

    @GetMapping({"/artistikcity-vision", "/artistikcity-vision/", "/vision"})
    public ResponseEntity<String> vision(HttpServletRequest request) {
        Map<String, String> v = pages.values("vision");
        seo(request, v);
        Map<String, Object> page = new LinkedHashMap<>(v);
        page.put("body", HtmlCleaner.clean(v.get("body")));
        page.put("highlights", pages.cards(v.get("highlights")));
        return inertia.render(request, "Info/Vision", Map.of("page", page));
    }

    @GetMapping({"/instructor", "/instructor/", "/instructors"})
    public ResponseEntity<String> instructor(HttpServletRequest request) {
        Map<String, String> v = pages.values("instructor");
        seo(request, v);
        List<Map<String, Object>> people = new ArrayList<>();
        for (Map<String, Object> p : pages.cards(v.get("people"))) {
            Row a = null;
            String id = String.valueOf(p.getOrDefault("id", ""));
            String slug = String.valueOf(p.getOrDefault("slug", ""));
            if (id.matches("\\d{1,18}")) a = db.first("select id, name, slug, profile_title, designation, profile_description, profile_photo from admins where id = ?", Long.parseLong(id));
            else if (!slug.isBlank()) a = db.first("select id, name, slug, profile_title, designation, profile_description, profile_photo from admins where slug = ?", slug);
            if (a == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.get("id"));
            m.put("name", a.str("name", ""));
            m.put("title", a.str("profile_title", ""));
            m.put("designation", a.str("designation", ""));
            m.put("bio", HtmlCleaner.clean(a.str("profile_description", "")));
            m.put("photo", photo("teachers", a.lng("id"), a.str("profile_photo", "")));
            Map<String, String> links = new LinkedHashMap<>();
            for (String k : SitePages.PERSON_LINKS) {
                String u = String.valueOf(p.getOrDefault(k, "")).trim();
                if (u.matches("(?i)^https://[^\\s<>\"']+$")) links.put(k, u);
            }
            m.put("links", links);
            if ("1".equals(v.get("show_courses"))) {
                List<Map<String, Object>> courses = new ArrayList<>();
                for (Row c : db.select("select c.id, c.title, c.slug, c.sub_title, " + CourseService.photoSubquery("c.id")
                        + " from courses c where c.status = 1 and c.admin_id = ? order by c.id desc limit 6", a.get("id"))) {
                    courses.add(Map.of("title", c.str("title", ""), "subtitle", c.str("sub_title", ""), "href", "/course/" + c.str("slug", ""),
                            "photo", photo("courses", c.lng("id"), c.str("photo", ""))));
                }
                m.put("courses", courses);
            }
            people.add(m);
        }
        Map<String, Object> page = new LinkedHashMap<>(v);
        page.remove("people");
        return inertia.render(request, "Info/Instructor", Map.of("page", page, "people", people));
    }

    @GetMapping({"/customer-support", "/customer-support/", "/help"})
    public ResponseEntity<String> support(HttpServletRequest request) {
        Map<String, String> v = pages.values("support");
        seo(request, v);
        Map<String, Object> page = new LinkedHashMap<>(v);
        page.put("body", HtmlCleaner.clean(v.get("body")));
        page.put("topics", pages.cards(v.get("topics")));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("page", page);
        if ("1".equals(v.get("show_faq"))) {
            int n = v.get("faq_count").matches("\\d{1,2}") ? Integer.parseInt(v.get("faq_count")) : 5;
            props.put("faqs", faqRows(n));
        }
        props.put("contact", contact());
        return inertia.render(request, "Info/Support", props);
    }

    @GetMapping({"/faq", "/faq/", "/faqs"})
    public ResponseEntity<String> faq(HttpServletRequest request) {
        Map<String, String> v = pages.values("faq");
        seo(request, v);
        Map<String, List<Map<String, Object>>> byCategory = new LinkedHashMap<>();
        for (Map<String, Object> f : faqRows(500)) {
            byCategory.computeIfAbsent(String.valueOf(f.get("category")), k -> new ArrayList<>()).add(f);
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        byCategory.forEach((cat, items) -> {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("category", cat.isBlank() ? null : cat);
            g.put("items", items);
            groups.add(g);
        });
        return inertia.render(request, "Info/Faq", Map.of("page", v, "groups", groups, "contact", contact()));
    }

    private List<Map<String, Object>> faqRows(int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            for (Row f : db.select("select id, question, answer, category from faqs where status = 1 order by sort_order, id limit " + Math.max(1, Math.min(limit, 500)))) {
                out.add(Map.of("id", f.get("id"), "question", f.str("question", ""), "answer", HtmlCleaner.clean(f.str("answer", "")), "category", f.str("category", "")));
            }
        } catch (Db.DbException e) {
            // faqs table missing until the migration runs: no questions
        }
        return out;
    }

    /** Contact details from SEO & Layout Controls › Contact (empty ones left out). */
    private Map<String, Object> contact() {
        Map<String, Object> c = new LinkedHashMap<>();
        for (String k : List.of("email", "phone", "address")) {
            String v = site.get("contact", k, "");
            if (!v.isBlank()) c.put(k, v);
        }
        if (c.containsKey("phone")) c.put("phoneLink", String.valueOf(c.get("phone")).replaceAll("[^0-9+]", ""));
        return c;
    }

    private void seo(HttpServletRequest request, Map<String, String> v) {
        Map<String, Object> seo = new LinkedHashMap<>();
        seo.put("title", v.getOrDefault("seo_title", v.get("heading")));
        seo.put("description", v.getOrDefault("seo_description", v.getOrDefault("intro", "")));
        request.setAttribute(Inertia.SEO_ATTRIBUTE, seo);
    }

    /** An uploaded photo: a full link or site path as saved, else the classic /storage/uploads/{folder}/{id}/{file}. */
    static String photo(String folder, long id, String name) {
        if (name == null || name.isBlank()) return null;
        if (name.startsWith("http://") || name.startsWith("https://") || name.startsWith("/")) return name;
        return "/storage/uploads/" + folder + "/" + id + "/" + name;
    }
}
