package com.artistikcity.landing;

import com.artistikcity.support.HtmlCleaner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The versioned page configuration (landing_pages.draft_config, landing_page_versions.config): design tokens, the ordered
 * sections with their content, the calls to action, SEO and marketing tags. Everything that comes from the admin goes
 * through {@link #normalize}: unknown keys are dropped, texts are trimmed and capped, links must be site paths or https,
 * colours must be hex, tracking ids must look like tracking ids and custom HTML is cleaned (allow-list). Empty content
 * fields fall back to the course's own data when the page is rendered, so a new page is complete from the start.
 */
public final class LandingConfig {

    public static final int SCHEMA = 1;
    static final int MAX_SECTIONS = 30;
    static final int MAX_ITEMS = 24;

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern ID = Pattern.compile("^[a-z0-9-]{3,40}$");
    private static final Pattern GA4 = Pattern.compile("^G-[A-Z0-9]{4,15}$");
    private static final Pattern GTM = Pattern.compile("^GTM-[A-Z0-9]{4,12}$");
    private static final Pattern PIXEL = Pattern.compile("^[0-9]{6,20}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{8,15}$");

    private LandingConfig() {
    }

    /** Validation result: the cleaned config and, per field path, what was wrong. */
    public record Result(Map<String, Object> config, Map<String, String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    // ---------------------------------------------------------------------------------------------- new pages

    /** A complete starting config for a template; content left empty falls back to the course's data. */
    public static Map<String, Object> initial(LandingCatalog.Template t) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("schema", SCHEMA);
        c.put("design", new LinkedHashMap<>(t.design()));
        List<Map<String, Object>> sections = new ArrayList<>();
        for (String key : t.sections()) {
            sections.add(section(key, "hero".equals(key) ? t.hero() : LandingCatalog.section(key).layouts().get(0)));
        }
        c.put("sections", sections);
        boolean site = "SITE".equals(t.kind());
        Map<String, Object> cta = new LinkedHashMap<>();
        cta.put("primary", site ? "Explore courses" : "Enroll now");
        cta.put("primaryHref", site ? "/courses?type=all" : "");
        cta.put("secondary", site ? "" : "Explore the course");
        cta.put("secondaryHref", "");
        cta.put("secondaryVideo", false);
        cta.put("sticky", !site);
        c.put("cta", cta);
        Map<String, Object> seo = new LinkedHashMap<>();
        seo.put("title", "");
        seo.put("description", "");
        seo.put("canonical", "");
        seo.put("ogTitle", "");
        seo.put("ogDescription", "");
        seo.put("ogImage", "");
        seo.put("index", true);
        seo.put("sitemap", true);
        seo.put("structuredData", true);
        c.put("seo", seo);
        Map<String, Object> mk = new LinkedHashMap<>();
        mk.put("ga4", "");
        mk.put("gtm", "");
        mk.put("metaPixel", "");
        mk.put("whatsapp", "");
        mk.put("leadCapture", false);
        c.put("marketing", mk);
        return c;
    }

    public static Map<String, Object> section(String type, String layout) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", type.replace('_', '-') + "-" + UUID.randomUUID().toString().substring(0, 6));
        s.put("type", type);
        s.put("enabled", true);
        s.put("layout", layout);
        s.put("background", "hero".equals(type) || "final_cta".equals(type) ? "tint" : "plain");
        s.put("hideOnMobile", false);
        s.put("content", defaults(type));
        return s;
    }

    private static Map<String, Object> defaults(String type) {
        Map<String, Object> c = new LinkedHashMap<>();
        if (!"hero".equals(type)) {
            c.put("kicker", "");        // the small label above the heading; empty = the section's usual label
        }
        switch (type) {
            case "hero" -> {
                c.put("eyebrow", "");
                c.put("heading", "");
                c.put("subheading", "");
                c.put("body", "");
                c.put("image", "");
                c.put("imageAlt", "");
                c.put("video", "");
                c.put("language", "English, Hindi");
                c.put("items", List.of(item("Live classes with the artist", ""), item("Certificate of completion", ""), item("Small groups, personal feedback", "")));
                c.put("images", List.of());
            }
            case "intro" -> {
                c.put("heading", "Why this course");
                c.put("body", "");
                c.put("items", List.of());
            }
            case "outcomes" -> {
                c.put("heading", "What you'll be able to do");
                c.put("items", List.of());
            }
            case "curriculum" -> c.put("heading", "Curriculum");
            case "artist" -> {
                c.put("heading", "Meet the artist");
                c.put("body", "");
                c.put("ids", List.of());
                c.put("items", List.of());
                c.put("socials", List.of());
                c.put("video", "");
            }
            case "gallery" -> {
                c.put("heading", "Artwork from the course");
                c.put("body", "");
                c.put("source", "course");
                c.put("items", List.of());
                c.put("beforeImage", "");
                c.put("afterImage", "");
            }
            case "benefits" -> {
                c.put("heading", "What's included");
                c.put("items", List.of());
            }
            case "video" -> {
                c.put("heading", "Watch a preview");
                c.put("body", "");
                c.put("video", "");
            }
            case "testimonials" -> {
                c.put("heading", "What students say");
                c.put("source", "reviews");
                c.put("ids", List.of());
            }
            case "pricing" -> {
                c.put("heading", "Enrol today");
                c.put("body", "");
            }
            case "faq" -> {
                c.put("heading", "Questions, answered");
                c.put("items", List.of());
            }
            case "related" -> c.put("heading", "You might also like");
            case "final_cta" -> {
                c.put("heading", "Start painting with us");
                c.put("body", "");
                c.put("image", "");
                c.put("items", List.of());      // extra buttons: title = label, link = address
            }
            case "cards" -> {
                c.put("heading", "");
                c.put("body", "");
                c.put("source", "custom");
                c.put("items", List.of());
            }
            case "steps" -> {
                c.put("heading", "How it works");
                c.put("body", "");
                c.put("items", List.of());
            }
            case "courses" -> {
                c.put("heading", "Courses and workshops");
                c.put("body", "");
                c.put("ids", List.of());
                c.put("limit", 6);
            }
            case "artists" -> {
                c.put("heading", "Meet our artists");
                c.put("body", "");
                c.put("ids", List.of());
                c.put("limit", 6);
            }
            case "commission_form" -> {
                c.put("heading", "Choose the style of your painting");
                c.put("body", "");
            }
            case "commission_pricing" -> {
                c.put("heading", "What a commission costs");
                c.put("body", "");
            }
            case "custom" -> {
                c.put("heading", "");
                c.put("html", "");
            }
            default -> { }
        }
        return c;
    }

    static Map<String, Object> item(String title, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("text", text);
        return m;
    }

    // ----------------------------------------------------------------------------------------------- normalize

    /** Cleans a config sent by the editor. Errors are keyed like "sections.3.content.items.0.image". */
    @SuppressWarnings("unchecked")
    public static Result normalize(Object raw) {
        return normalize(raw, false);
    }

    /** site = an information page (no course): course-only sections are refused and the main button needs an address. */
    @SuppressWarnings("unchecked")
    public static Result normalize(Object raw, boolean site) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> in = raw instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema", SCHEMA);

        Map<String, Object> d = map(in.get("design"));
        Map<String, Object> design = new LinkedHashMap<>();
        design.put("palette", oneOf(d.get("palette"), List.of("burgundy", "ink", "terracotta", "sage", "custom"), "burgundy"));
        for (String k : List.of("accent", "ink", "surface")) {
            String v = str(d.get(k));
            if (!HEX.matcher(v).matches()) {
                errors.put("design." + k, "Use a colour like #9C0D48.");
                v = k.equals("accent") ? "#9C0D48" : k.equals("ink") ? "#14213D" : "#FBF8F4";
            }
            design.put(k, v.toUpperCase());
        }
        design.put("font", oneOf(d.get("font"), List.of("jakarta", "serif"), "jakarta"));
        design.put("radius", oneOf(d.get("radius"), List.of("soft", "sharp"), "soft"));
        design.put("texture", oneOf(d.get("texture"), List.of("none", "wash", "grain"), "none"));
        out.put("design", design);

        List<Object> rawSections = list(in.get("sections"));
        if (rawSections.size() > MAX_SECTIONS) {
            errors.put("sections", "A page can have at most " + MAX_SECTIONS + " sections.");
        }
        List<Map<String, Object>> sections = new ArrayList<>();
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        java.util.Set<String> seenSingles = new java.util.HashSet<>();
        int i = 0;
        for (Object o : rawSections.subList(0, Math.min(rawSections.size(), MAX_SECTIONS))) {
            Map<String, Object> s = map(o);
            String p = "sections." + i;
            LandingCatalog.SectionType type = LandingCatalog.section(str(s.get("type")));
            if (type == null) {
                errors.put(p + ".type", "Unknown section type.");
                i++;
                continue;
            }
            if (site && LandingCatalog.COURSE_ONLY.contains(type.key())) {
                errors.put(p + ".type", type.label() + " needs a course, so it can't be on an information page.");
            }
            if (type.single() && !seenSingles.add(type.key())) {
                errors.put(p + ".type", type.label() + " can only be on a page once.");
            }
            String id = str(s.get("id"));
            if (!ID.matcher(id).matches() || !seenIds.add(id)) {
                id = type.key().replace('_', '-') + "-" + UUID.randomUUID().toString().substring(0, 6);
                seenIds.add(id);
            }
            Map<String, Object> ns = new LinkedHashMap<>();
            ns.put("id", id);
            ns.put("type", type.key());
            ns.put("enabled", bool(s.get("enabled"), true));
            ns.put("layout", oneOf(s.get("layout"), type.layouts(), type.layouts().get(0)));
            ns.put("background", oneOf(s.get("background"), List.of("plain", "tint", "dark", "accent"), "plain"));
            ns.put("hideOnMobile", bool(s.get("hideOnMobile"), false));
            ns.put("content", content(type.key(), map(s.get("content")), p + ".content", errors));
            sections.add(ns);
            i++;
        }
        if (sections.isEmpty() || !"hero".equals(sections.get(0).get("type"))) {
            errors.put("sections", "The hero must be the first section.");
        }
        if (sections.stream().noneMatch(s -> "pricing".equals(s.get("type")) || "hero".equals(s.get("type")))) {
            errors.put("sections", "Keep the hero or the pricing section so visitors can enrol.");
        }
        out.put("sections", sections);

        Map<String, Object> c = map(in.get("cta"));
        Map<String, Object> cta = new LinkedHashMap<>();
        cta.put("primary", text(c.get("primary"), 40, site ? "" : "Enroll now"));
        cta.put("primaryHref", link(c.get("primaryHref"), "cta.primaryHref", errors, false));
        cta.put("secondary", text(c.get("secondary"), 40, site ? "" : "Explore the course"));
        cta.put("secondaryHref", link(c.get("secondaryHref"), "cta.secondaryHref", errors, false));
        cta.put("secondaryVideo", bool(c.get("secondaryVideo"), false));
        cta.put("sticky", bool(c.get("sticky"), !site));
        if (str(cta.get("primary")).isBlank()) {
            errors.put("cta.primary", site ? "The main button needs a label." : "The Enroll button needs a label.");
        }
        if (site && str(cta.get("primaryHref")).isEmpty()) {
            errors.put("cta.primaryHref", "Where should the main button go? For example /courses?type=all.");
        }
        out.put("cta", cta);

        Map<String, Object> s = map(in.get("seo"));
        Map<String, Object> seo = new LinkedHashMap<>();
        seo.put("title", text(s.get("title"), 70, ""));
        seo.put("description", text(s.get("description"), 170, ""));
        seo.put("canonical", link(s.get("canonical"), "seo.canonical", errors, false));
        seo.put("ogTitle", text(s.get("ogTitle"), 90, ""));
        seo.put("ogDescription", text(s.get("ogDescription"), 200, ""));
        seo.put("ogImage", link(s.get("ogImage"), "seo.ogImage", errors, true));
        seo.put("index", bool(s.get("index"), true));
        seo.put("sitemap", bool(s.get("sitemap"), true));
        seo.put("structuredData", bool(s.get("structuredData"), true));
        out.put("seo", seo);

        Map<String, Object> k = map(in.get("marketing"));
        Map<String, Object> mk = new LinkedHashMap<>();
        mk.put("ga4", tracking(k.get("ga4"), GA4, "marketing.ga4", "Use your GA4 measurement id, like G-ABC123XYZ.", errors));
        mk.put("gtm", tracking(k.get("gtm"), GTM, "marketing.gtm", "Use your container id, like GTM-ABC1234.", errors));
        mk.put("metaPixel", tracking(k.get("metaPixel"), PIXEL, "marketing.metaPixel", "Use the numeric pixel id.", errors));
        mk.put("whatsapp", tracking(str(k.get("whatsapp")).replace(" ", ""), PHONE, "marketing.whatsapp", "Use the number with country code, like +919876543210.", errors));
        mk.put("leadCapture", bool(k.get("leadCapture"), false));
        out.put("marketing", mk);
        return new Result(out, errors);
    }

    private static Map<String, Object> content(String type, Map<String, Object> in, String p, Map<String, String> errors) {
        Map<String, Object> c = new LinkedHashMap<>();
        Map<String, Object> def = defaults(type);
        for (String key : def.keySet()) {
            Object v = in.get(key);
            switch (key) {
                case "eyebrow", "kicker" -> c.put(key, text(v, 60, ""));
                case "heading" -> c.put(key, text(v, 120, ""));
                case "subheading" -> c.put(key, text(v, 200, ""));
                case "body" -> c.put(key, text(v, 2000, ""));
                case "imageAlt" -> c.put(key, text(v, 160, ""));
                case "language" -> c.put(key, text(v, 60, ""));
                case "socials" -> {
                    List<String> links = new ArrayList<>();
                    int j = 0;
                    for (Object o : list(v)) {
                        String l = str(o);
                        if (l.isEmpty()) continue;
                        if (!l.matches("^https://[^\\s\"'<>]{4,300}$")) {
                            errors.put(p + ".socials." + j, "Use a full https:// link.");
                        } else if (links.size() < 6) {
                            links.add(l);
                        }
                        j++;
                    }
                    c.put(key, links);
                }
                case "image", "beforeImage", "afterImage" -> c.put(key, link(v, p + "." + key, errors, true));
                case "video" -> c.put(key, video(v, p + "." + key, errors));
                case "source" -> {
                    if ("gallery".equals(type)) c.put(key, oneOf(v, List.of("course", "marketplace"), "course"));
                    else if ("cards".equals(type)) c.put(key, oneOf(v, List.of("custom", "course_for", "course_learn", "course_deliverables"), "custom"));
                    else c.put(key, oneOf(v, List.of("reviews", "testimonials", "both"), "reviews"));
                }
                case "limit" -> {
                    int n;
                    try {
                        n = (int) Double.parseDouble(str(v).isEmpty() ? "6" : str(v));
                    } catch (NumberFormatException e) {
                        n = 6;
                    }
                    c.put(key, Math.max(1, Math.min(12, n)));
                }
                case "images" -> {
                    List<String> imgs = new ArrayList<>();
                    int j = 0;
                    for (Object o : list(v)) {
                        String l = link(o, p + ".images." + j, errors, true);
                        if (!l.isEmpty() && imgs.size() < 4) imgs.add(l);
                        j++;
                    }
                    c.put(key, imgs);
                }
                case "ids" -> {
                    List<Long> ids = new ArrayList<>();
                    for (Object o : list(v)) {
                        try {
                            long n = Long.parseLong(String.valueOf(o).replace(".0", ""));
                            if (n > 0 && !ids.contains(n) && ids.size() < MAX_ITEMS) {
                                ids.add(n);
                            }
                        } catch (NumberFormatException ignored) {
                            // not an id
                        }
                    }
                    c.put(key, ids);
                }
                case "html" -> {
                    String html = str(v);
                    if (html.length() > 20000) {
                        errors.put(p + ".html", "That text is too long.");
                        html = html.substring(0, 20000);
                    }
                    c.put(key, html.isBlank() ? "" : HtmlCleaner.clean(html));
                }
                case "items" -> {
                    List<Object> raw = list(v);
                    if (raw.size() > MAX_ITEMS) {
                        errors.put(p + ".items", "At most " + MAX_ITEMS + " items.");
                    }
                    List<Map<String, Object>> items = new ArrayList<>();
                    int j = 0;
                    for (Object o : raw.subList(0, Math.min(raw.size(), MAX_ITEMS))) {
                        Map<String, Object> it = map(o);
                        Map<String, Object> ni = new LinkedHashMap<>();
                        ni.put("title", text(it.get("title"), 160, ""));
                        ni.put("text", text(it.get("text"), 1200, ""));
                        String img = link(it.get("image"), p + ".items." + j + ".image", errors, true);
                        if (!img.isEmpty()) {
                            ni.put("image", img);
                            ni.put("alt", text(it.get("alt"), 160, ""));
                        }
                        String href = link(it.get("link"), p + ".items." + j + ".link", errors, false);
                        if (!href.isEmpty()) {
                            ni.put("link", href);
                            ni.put("linkLabel", text(it.get("linkLabel"), 40, ""));
                        }
                        if (!str(ni.get("title")).isBlank() || !str(ni.get("text")).isBlank() || !img.isEmpty()) {
                            items.add(ni);
                        }
                        j++;
                    }
                    c.put(key, items);
                }
                default -> c.put(key, def.get(key));
            }
        }
        return c;
    }

    // ------------------------------------------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    static boolean bool(Object o, boolean def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(o);
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "on".equalsIgnoreCase(s);
    }

    private static String text(Object o, int max, String def) {
        String s = o == null ? def : String.valueOf(o).replace("\r", "").trim();
        s = s.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "");
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String oneOf(Object o, List<String> allowed, String def) {
        String s = str(o);
        return allowed.contains(s) ? s : def;
    }

    /** A site path ("/storage/...", "/course/...") or an https address (links may also be "#section-id"); may be empty. */
    static String link(Object o, String path, Map<String, String> errors, boolean image) {
        String s = str(o);
        if (s.isEmpty()) {
            return "";
        }
        boolean ok = (s.startsWith("/") && !s.startsWith("//") && !s.contains("..")) || s.matches("^https://[^\\s\"'<>]+$")
                || (!image && s.matches("^#[a-z0-9-]{1,40}$"));     // a section on the same page
        if (!ok || s.length() > 1000) {
            errors.put(path, image ? "Use an image from the media library or an https:// address." : "Use a site path like /course/... or an https:// address.");
            return "";
        }
        return s;
    }

    private static String video(Object o, String path, Map<String, String> errors) {
        String s = str(o);
        if (s.isEmpty()) {
            return "";
        }
        if (LandingVideo.embed(s) == null) {
            errors.put(path, "Use a YouTube or Vimeo link, or an uploaded .mp4 from the media library.");
            return "";
        }
        return s;
    }

    private static String tracking(Object o, Pattern p, String path, String message, Map<String, String> errors) {
        String s = str(o);
        if (s.isEmpty()) {
            return "";
        }
        if (!p.matcher(s).matches()) {
            errors.put(path, message);
            return "";
        }
        return s;
    }
}
