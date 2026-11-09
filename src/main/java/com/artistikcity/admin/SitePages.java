package com.artistikcity.admin;

import com.artistikcity.support.Db;
import com.artistikcity.support.HtmlCleaner;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The content of four public pages, edited in Admin > System parameters > SEO &amp; Layout Controls (tabs Our vision,
 * Instructor, Customer support, FAQ) and stored in {@code settings} under the tags {@code page_vision},
 * {@code page_instructor}, {@code page_support} and {@code page_faq}. An empty value falls back to {@link #DEFAULTS},
 * so a fresh database shows sensible, factual pages. The FAQ questions themselves stay in the {@code faqs} table.
 * <p>Value kinds: text (plain, length-limited), html (cleaned by {@link HtmlCleaner}), link (site path or https),
 * flag ("1"/"0"), number, cards (JSON list of {title, text, label, href, icon}) and people (JSON list of
 * {id, website, facebook, instagram, youtube, linkedin}).
 */
@Service
public class SitePages {

    public static final List<String> PAGES = List.of("vision", "instructor", "support", "faq");

    record Field(String key, String kind, int max) {}

    static final Map<String, List<Field>> FIELDS = new LinkedHashMap<>();
    static final Map<String, Map<String, String>> DEFAULTS = new LinkedHashMap<>();

    static {
        FIELDS.put("vision", List.of(new Field("heading", "text", 120), new Field("intro", "text", 400), new Field("body", "html", 20000),
                new Field("highlights", "cards", 6), new Field("cta_label", "text", 40), new Field("cta_link", "link", 300),
                new Field("seo_title", "text", 120), new Field("seo_description", "text", 300)));
        FIELDS.put("instructor", List.of(new Field("heading", "text", 120), new Field("intro", "text", 400), new Field("people", "people", 8),
                new Field("show_courses", "flag", 1), new Field("seo_title", "text", 120), new Field("seo_description", "text", 300)));
        FIELDS.put("support", List.of(new Field("heading", "text", 120), new Field("intro", "text", 400), new Field("topics", "cards", 9),
                new Field("show_faq", "flag", 1), new Field("faq_count", "number", 20), new Field("show_contact", "flag", 1),
                new Field("hours", "text", 200), new Field("body", "html", 20000),
                new Field("seo_title", "text", 120), new Field("seo_description", "text", 300)));
        FIELDS.put("faq", List.of(new Field("heading", "text", 120), new Field("intro", "text", 400), new Field("show_search", "flag", 1),
                new Field("show_contact", "flag", 1), new Field("seo_title", "text", 120), new Field("seo_description", "text", 300)));

        DEFAULTS.put("vision", Map.of(
                "heading", "Our vision",
                "intro", "Art is for everyone. ArtistikCity is an online art school where anyone, at any age, can learn to draw and paint with a working artist.",
                "body", "<h2>Why ArtistikCity</h2><p>Learning art works best with a teacher who paints, a clear path and honest feedback. "
                        + "ArtistikCity brings that online: live courses and workshops, a personal studio for your practice, and a place to share and sell what you make.</p>"
                        + "<h2>How we teach</h2><p>Every course follows the same path: choose a course, learn lesson by lesson, submit your artwork for review, "
                        + "and earn a certificate once your work is approved.</p>",
                "highlights", "[{\"icon\":\"fa-video-camera\",\"title\":\"Learn live\",\"text\":\"Live courses and workshops in watercolour, acrylic, sketching and more, for children and adults.\"},"
                        + "{\"icon\":\"fa-paint-brush\",\"title\":\"Practise with feedback\",\"text\":\"Submit your artworks from My studio and get comments from your instructor.\"},"
                        + "{\"icon\":\"fa-certificate\",\"title\":\"Share and sell\",\"text\":\"Earn a verifiable certificate, build a portfolio and list approved originals on the marketplace.\"}]",
                "cta_label", "Browse courses",
                "cta_link", "/courses?type=all",
                "seo_title", "Our vision · ArtistikCity",
                "seo_description", "Why ArtistikCity exists: live online art classes, personal feedback, certificates and a marketplace for student art."));
        DEFAULTS.put("instructor", Map.of(
                "heading", "Meet the instructor",
                "intro", "The artist who teaches the ArtistikCity courses and reviews your work.",
                "people", "",
                "show_courses", "1",
                "seo_title", "Our instructor · ArtistikCity",
                "seo_description", "Meet the artist who teaches the ArtistikCity live courses and workshops."));
        DEFAULTS.put("support", Map.of(
                "heading", "Customer support",
                "intro", "Find an answer quickly, or get in touch and we will help.",
                "topics", "[{\"icon\":\"fa-graduation-cap\",\"title\":\"Courses and classes\",\"text\":\"Find a course or workshop, check dates and what you need.\",\"label\":\"Browse courses\",\"href\":\"/courses?type=all\"},"
                        + "{\"icon\":\"fa-play-circle\",\"title\":\"My courses\",\"text\":\"Continue your lessons and submit your artworks.\",\"label\":\"Open My studio\",\"href\":\"/dashboard\"},"
                        + "{\"icon\":\"fa-shopping-bag\",\"title\":\"Orders and payments\",\"text\":\"See every course, artwork and commission you ordered, with receipts.\",\"label\":\"Your orders\",\"href\":\"/user/orders\"},"
                        + "{\"icon\":\"fa-certificate\",\"title\":\"Certificates and portfolio\",\"text\":\"Download your certificates and create your portfolio PDF.\",\"label\":\"Certificates\",\"href\":\"/dashboard/certificates\"},"
                        + "{\"icon\":\"fa-shield\",\"title\":\"Login and security\",\"text\":\"Change how you sign in, your password or linked accounts.\",\"label\":\"Login & security\",\"href\":\"/user/linked-accounts\"},"
                        + "{\"icon\":\"fa-picture-o\",\"title\":\"Art and commissions\",\"text\":\"Buy original art and prints, or request a painting made for you.\",\"label\":\"Commission art\",\"href\":\"/commission/step-1\"}]",
                "show_faq", "1",
                "faq_count", "5",
                "show_contact", "1",
                "seo_title", "Customer support · ArtistikCity",
                "seo_description", "Help with courses, orders, payments, certificates and your ArtistikCity account."));
        DEFAULTS.put("faq", Map.of(
                "heading", "Frequently asked questions",
                "intro", "Answers to the questions students ask most.",
                "show_search", "1",
                "show_contact", "1",
                "seo_title", "FAQ · ArtistikCity",
                "seo_description", "Answers about ArtistikCity courses, payments, classes, certificates and your account."));
        // the default instructor (WelcomeController.INSTRUCTOR_SLUG) with the links already used elsewhere on the site
        Map<String, String> ins = new LinkedHashMap<>(DEFAULTS.get("instructor"));
        ins.put("people", "[{\"slug\":\"vaishali-j-goswami\",\"website\":\"https://studiovaishaliarts.com/\",\"facebook\":\"https://www.facebook.com/StudioVaishaliArts/\","
                + "\"instagram\":\"\",\"youtube\":\"\",\"linkedin\":\"https://in.linkedin.com/in/vaishali-jetly-goswami-4b5874233\"}]");
        DEFAULTS.put("instructor", ins);
    }

    public static final List<String> PERSON_LINKS = List.of("website", "facebook", "instagram", "youtube", "linkedin");

    private final Db db;
    private final Json json;
    private final SiteSettings site;

    public SitePages(Db db, Json json, SiteSettings site) {
        this.db = db;
        this.json = json;
        this.site = site;
    }

    static String tag(String page) {
        return "page_" + page;
    }

    /** What is saved (empty = use the default). */
    public Map<String, String> saved(String page) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Field f : FIELDS.get(page)) out.put(f.key(), site.get(tag(page), f.key(), ""));
        return out;
    }

    /** The values the public page uses: saved, else the default. */
    public Map<String, String> values(String page) {
        Map<String, String> out = new LinkedHashMap<>();
        Map<String, String> def = DEFAULTS.get(page);
        for (Field f : FIELDS.get(page)) {
            String v = site.get(tag(page), f.key(), "");
            out.put(f.key(), v.isBlank() ? def.getOrDefault(f.key(), "") : v);
        }
        return out;
    }

    public Map<String, Object> adminProps() {
        Map<String, Object> saved = new LinkedHashMap<>();
        for (String p : PAGES) saved.put(p, saved(p));
        return Map.of("values", saved, "defaults", DEFAULTS);
    }

    public List<Map<String, Object>> cards(String raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        Object o;
        try {
            o = raw == null || raw.isBlank() ? null : json.decode(raw);
        } catch (RuntimeException e) {
            return out;
        }
        if (o instanceof List<?> l) {
            for (Object x : l) if (x instanceof Map<?, ?> m) {
                Map<String, Object> c = new LinkedHashMap<>();
                m.forEach((k, v) -> c.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Checks and cleans what the admin sent for one page. Returns field errors (empty = saved). Keys not sent keep
     * their value; an empty value means "use the default".
     */
    public Map<String, String> save(String page, Map<String, Object> in) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, String> clean = new LinkedHashMap<>();
        for (Field f : FIELDS.get(page)) {
            if (!in.containsKey(f.key())) continue;
            Object raw = in.get(f.key());
            String v = raw == null ? "" : raw instanceof String s ? s.trim() : json.encode(raw);
            String err = null;
            switch (f.kind()) {
                case "text" -> { v = v.replaceAll("[<>]", ""); if (v.length() > f.max()) err = "Keep this under " + f.max() + " characters."; }
                case "html" -> {
                    if (v.length() > f.max()) err = "This text is too long.";
                    v = HtmlCleaner.text(v, 5).isBlank() ? "" : HtmlCleaner.clean(v);
                }
                case "link" -> { if (!v.isEmpty() && !ContentAdminController.siteLink(v)) err = "Use a page on this site such as /courses?type=all, or a full https:// link."; }
                case "flag" -> { if (!v.isEmpty() && !v.equals("1") && !v.equals("0")) err = "Choose on or off."; }
                case "number" -> { if (!v.isEmpty() && (!v.matches("\\d{1,2}") || Integer.parseInt(v) < 1 || Integer.parseInt(v) > f.max())) err = "Choose a number from 1 to " + f.max() + "."; }
                case "cards" -> {
                    String[] res = checkCards(v, f.max());
                    err = res[0];
                    v = res[1];
                }
                case "people" -> {
                    String[] res = checkPeople(v, f.max());
                    err = res[0];
                    v = res[1];
                }
                default -> err = "Unknown field.";
            }
            if (err != null) errors.put(f.key(), err);
            else clean.put(f.key(), v);
        }
        if (!errors.isEmpty()) return errors;
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, String> kv : clean.entrySet()) {
            if (db.update("update settings set meta_value = ?, updated_at = ? where meta_tag = ? and meta_key = ?", kv.getValue(), now, tag(page), kv.getKey()) == 0) {
                db.insert("settings", Row.of("meta_tag", tag(page), "meta_key", kv.getKey(), "meta_value", kv.getValue(), "created_at", now, "updated_at", now));
            }
        }
        site.invalidate();
        return errors;
    }

    /** [error or null, cleaned JSON or ""]. Cards: up to max of {icon, title (1-80), text (<=300), label (<=40), href}. */
    String[] checkCards(String v, int max) {
        if (v.isEmpty() || "[]".equals(v)) return new String[]{null, v.isEmpty() ? "" : "[]"};
        Object o;
        try {
            o = json.decode(v);
        } catch (RuntimeException e) {
            return new String[]{"The list could not be read. Reload the page and try again.", ""};
        }
        if (!(o instanceof List<?> l)) return new String[]{"The list could not be read. Reload the page and try again.", ""};
        if (l.size() > max) return new String[]{"Up to " + max + " items fit here.", ""};
        List<Map<String, String>> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            if (!(l.get(i) instanceof Map<?, ?> m)) return new String[]{"Item " + (i + 1) + " is not valid.", ""};
            String title = str(m.get("title")), text = str(m.get("text")), label = str(m.get("label")), href = str(m.get("href")), icon = str(m.get("icon"));
            if (title.isEmpty()) return new String[]{"Item " + (i + 1) + " needs a title.", ""};
            if (title.length() > 80) return new String[]{"“" + title + "”: keep the title under 80 characters.", ""};
            if (text.length() > 300) return new String[]{"“" + title + "”: keep the text under 300 characters.", ""};
            if (label.length() > 40) return new String[]{"“" + title + "”: keep the button label under 40 characters.", ""};
            if (!href.isEmpty() && !ContentAdminController.siteLink(href)) return new String[]{"“" + title + "”: use a page on this site such as /user/orders, or a full https:// link.", ""};
            if (!icon.isEmpty() && !icon.matches("fa-[a-z0-9-]{1,40}")) icon = "";
            Map<String, String> c = new LinkedHashMap<>();
            c.put("icon", icon);
            c.put("title", title);
            c.put("text", text);
            c.put("label", label);
            c.put("href", href);
            out.add(c);
        }
        return new String[]{null, json.encode(out)};
    }

    /** People shown on /instructor: staff accounts (by id) with their own links (https only). */
    String[] checkPeople(String v, int max) {
        if (v.isEmpty()) return new String[]{null, ""};
        Object o;
        try {
            o = json.decode(v);
        } catch (RuntimeException e) {
            return new String[]{"The list could not be read. Reload the page and try again.", ""};
        }
        if (!(o instanceof List<?> l)) return new String[]{"The list could not be read. Reload the page and try again.", ""};
        if (l.size() > max) return new String[]{"Up to " + max + " people fit on the page.", ""};
        List<Map<String, Object>> out = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Object x : l) {
            if (!(x instanceof Map<?, ?> m)) return new String[]{"The list could not be read. Reload the page and try again.", ""};
            long id;
            try {
                id = Long.parseLong(str(m.get("id")));
            } catch (NumberFormatException e) {
                return new String[]{"Choose a staff member for every row.", ""};
            }
            Row a = db.first("select id, name from admins where id = ?", id);
            if (a == null) return new String[]{"A chosen staff member no longer exists.", ""};
            if (!seen.add(id)) return new String[]{a.str("name") + " is in the list twice.", ""};
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", id);
            for (String k : PERSON_LINKS) {
                String u = str(m.get(k));
                if (!u.isEmpty() && (!u.matches("(?i)^https://[^\\s<>\"']+$") || u.length() > 300)) {
                    return new String[]{a.str("name") + ": enter the full " + k + " link, starting with https://", ""};
                }
                p.put(k, u);
            }
            out.add(p);
        }
        return new String[]{null, json.encode(out)};
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
