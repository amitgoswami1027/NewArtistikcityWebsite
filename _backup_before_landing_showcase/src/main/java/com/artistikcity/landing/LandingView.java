package com.artistikcity.landing;

import com.artistikcity.support.Db;
import com.artistikcity.support.HtmlCleaner;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the model for templates/landing/page.mustache from a page's config, the course, its instructor(s), approved
 * reviews, the live price and the payment options. Content left empty in the editor falls back to the course's own data;
 * nothing is invented (reviews and testimonials are only the approved ones in the database).
 */
@Component
public class LandingView {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private final Db db;
    private final Json json;
    private final LandingCommerce commerce;
    private final LandingPayments payments;

    public LandingView(Db db, Json json, LandingCommerce commerce, LandingPayments payments) {
        this.db = db;
        this.json = json;
        this.commerce = commerce;
        this.payments = payments;
    }

    /** Course facts shared by the page and the checkout. */
    public Map<String, Object> course(long courseId) {
        Row c = db.first("select c.*, (select name from mediums m where m.id = c.medium_id) as medium_name, (select name from skills s where s.id = c.skill_id) as level_name,"
                + " (select photo_name from course_photos p where p.course_id = c.id order by p.id limit 1) as photo from courses c where c.id = ?", courseId);
        Map<String, Object> m = new LinkedHashMap<>();
        if (c == null) {
            return m;
        }
        m.put("id", c.get("id"));
        m.put("title", c.str("title"));
        m.put("slug", c.str("slug"));
        m.put("subTitle", c.str("sub_title", ""));
        m.put("intro", plain(c.str("introduction", "")));
        m.put("introDetails", plain(c.str("introduction_details", "")));
        m.put("summary", plain(c.str("summary", "")));
        m.put("image", c.get("photo") == null ? null : "/storage/uploads/courses/" + c.get("id") + "/" + c.get("photo"));
        m.put("medium", c.str("medium_name", ""));
        m.put("level", c.str("level_name", ""));
        m.put("kind", c.lng("course_type_id", 1) == 2 ? "Workshop" : "Live course");
        String dur = c.str("duration", "");
        m.put("duration", dur.isEmpty() ? null : dur.matches("\\d+") ? dur + (c.lng("course_type_id", 1) == 2 ? " days" : " weeks") : dur);
        String sessions = c.str("sessions", "");
        m.put("sessions", sessions.isEmpty() ? null : sessions.matches("\\d+") ? sessions + " live sessions" : sessions);
        m.put("hours", c.str("time_required", "").matches("\\d+") ? c.str("time_required") + " hrs / session" : null);
        m.put("ageGroup", c.str("age_group", ""));
        String start = c.str("course_start_date", "");
        m.put("starts", start.length() >= 10 ? LocalDate.parse(start.substring(0, 10)).format(DAY) : null);
        m.put("adminId", c.get("admin_id"));
        m.put("mediumId", c.get("medium_id"));
        m.put("includes", json.decodeMap(c.str("course_includes", "")));
        m.put("learn", strings(c.str("course_learn", "")));
        m.put("highlights", strings(c.str("course_highlights", "")));
        m.put("forWhom", strings(c.str("course_for", "")));
        m.put("problems", strings(c.str("course_problems_solved", "")));
        m.put("deliverables", strings(c.str("course_deliverables", "")));
        m.put("introVideo", c.str("intro_video_url", ""));
        m.put("status", c.lng("status", 0));
        return m;
    }

    /** The whole page model. preview = admin preview of a draft (noindex, banner, no tracking). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> page(Row page, Map<String, Object> config, boolean preview, String baseUrl) {
        long pageId = page.lng("id");
        Map<String, Object> course = course(page.lng("course_id"));
        LandingPricing.Quote quote = commerce.quote(page);
        LandingPayments.Plan plan = payments.forPage(pageId);
        List<LandingPayments.Choice> choices = payments.choices(plan, quote.totalUsd() != null);
        Map<String, Object> design = LandingConfig.map(config.get("design"));
        Map<String, Object> cta = LandingConfig.map(config.get("cta"));
        Map<String, Object> seo = LandingConfig.map(config.get("seo"));
        Map<String, Object> marketing = LandingConfig.map(config.get("marketing"));
        String enroll = "/course/" + page.str("slug") + "/enroll";

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pageId", pageId);
        m.put("slug", page.str("slug"));
        m.put("preview", preview);
        m.put("enrollUrl", enroll);
        m.put("ctaPrimary", LandingConfig.str(cta.get("primary")).isEmpty() ? "Enroll now" : LandingConfig.str(cta.get("primary")));
        m.put("ctaSecondary", LandingConfig.str(cta.get("secondary")));
        m.put("sticky", LandingConfig.bool(cta.get("sticky"), true));
        m.put("course", course);
        m.put("accent", hexOr(design.get("accent"), "#9C0D48"));
        m.put("accentDark", shade(hexOr(design.get("accent"), "#9C0D48"), 0.82));
        m.put("accentInk", contrastInk(hexOr(design.get("accent"), "#9C0D48")));
        m.put("ink", hexOr(design.get("ink"), "#14213D"));
        m.put("surface", hexOr(design.get("surface"), "#FBF8F4"));
        m.put("fontSerif", "serif".equals(design.get("font")));
        m.put("radiusSharp", "sharp".equals(design.get("radius")));

        Map<String, Object> price = price(quote);
        m.put("price", price);

        // sections
        List<Map<String, Object>> sections = new ArrayList<>();
        int n = 0;
        for (Object o : LandingConfig.list(config.get("sections"))) {
            Map<String, Object> s = LandingConfig.map(o);
            if (!LandingConfig.bool(s.get("enabled"), true)) {
                continue;
            }
            String type = LandingConfig.str(s.get("type"));
            Map<String, Object> c = LandingConfig.map(s.get("content"));
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", LandingConfig.str(s.get("id")));
            v.put("layout", LandingConfig.str(s.get("layout")));
            v.put("bg", LandingConfig.str(s.get("background")));
            v.put("hideOnMobile", LandingConfig.bool(s.get("hideOnMobile"), false));
            v.put("is_" + type, true);
            v.put("heading", LandingConfig.str(c.get("heading")));
            v.put("body", paragraphs(LandingConfig.str(c.get("body"))));
            v.put("index", n++);
            v.put("lazy", n > 1);
            switch (type) {
                case "hero" -> hero(v, c, course, price, page);
                case "intro" -> intro(v, c, course);
                case "outcomes" -> {
                    List<Map<String, Object>> items = items(c);
                    if (items.isEmpty()) {
                        for (String s2 : (List<String>) course.get("learn")) items.add(Map.of("title", s2, "text", ""));
                        for (String s2 : (List<String>) course.get("highlights")) items.add(Map.of("title", s2, "text", ""));
                    }
                    numbered(items);
                    v.put("items", items);
                    v.put("hasItems", !items.isEmpty());
                }
                case "curriculum" -> curriculum(v, page.lng("course_id"));
                case "artist" -> artists(v, c, course);
                case "gallery" -> gallery(v, c, course);
                case "benefits" -> benefits(v, c, course, page);
                case "video" -> {
                    LandingVideo.Embed e = LandingVideo.embed(LandingConfig.str(c.get("video")).isEmpty() ? (String) course.get("introVideo") : LandingConfig.str(c.get("video")));
                    if (e == null) continue;
                    v.put("video", video(e, (String) course.get("image"), (String) course.get("title")));
                }
                case "testimonials" -> {
                    if (!testimonials(v, c, page.lng("course_id"))) continue;
                }
                case "pricing" -> pricing(v, page, price, choices);
                case "faq" -> {
                    List<Map<String, Object>> items = items(c);
                    if (items.isEmpty()) {
                        for (Row f : db.select("select question, answer from faqs where status = 1 order by sort_order, id limit 6")) {
                            items.add(new LinkedHashMap<>(Map.of("title", f.str("question"), "text", HtmlCleaner.text(f.str("answer", ""), 1200))));
                        }
                    }
                    for (Map<String, Object> it : items) it.put("textParas", paragraphs(String.valueOf(it.get("text"))));
                    if (items.isEmpty()) continue;
                    v.put("items", items);
                }
                case "related" -> {
                    List<Map<String, Object>> rel = related(page.lng("course_id"), course.get("mediumId"));
                    if (rel.isEmpty()) continue;
                    v.put("items", rel);
                }
                case "final_cta" -> {
                    if (((String) v.get("heading")).isEmpty()) v.put("heading", "Start painting with us");
                    String img = LandingConfig.str(c.get("image"));
                    v.put("image", image(img.isEmpty() ? (String) course.get("image") : img, 960, "(max-width: 900px) 100vw, 40vw"));
                }
                case "custom" -> {
                    String html = LandingConfig.str(c.get("html"));
                    if (html.isEmpty() && ((String) v.get("heading")).isEmpty()) continue;
                    v.put("html", HtmlCleaner.clean(html));
                }
                default -> { }
            }
            sections.add(v);
        }
        m.put("sections", sections);
        m.put("exploreId", sections.stream().filter(x -> Boolean.TRUE.equals(x.get("is_curriculum"))).map(x -> x.get("id")).findFirst()
                .orElse(sections.size() > 1 ? sections.get(1).get("id") : null));

        // SEO
        String title = LandingConfig.str(seo.get("title"));
        String description = LandingConfig.str(seo.get("description"));
        if (title.isEmpty()) title = course.get("title") + " · Online course | ArtistikCity";
        if (description.isEmpty()) description = cut(firstNonBlank((String) course.get("subTitle"), (String) course.get("summary"), (String) course.get("intro")), 160);
        String canonical = LandingConfig.str(seo.get("canonical"));
        if (canonical.isEmpty()) canonical = baseUrl + "/course/" + page.str("slug");
        else if (canonical.startsWith("/")) canonical = baseUrl + canonical;
        String og = LandingConfig.str(seo.get("ogImage"));
        if (og.isEmpty()) og = LandingImages.sized((String) course.get("image"), 1280);
        if (og != null && og.startsWith("/")) og = baseUrl + og;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", title);
        meta.put("description", description);
        meta.put("canonical", canonical);
        meta.put("ogTitle", LandingConfig.str(seo.get("ogTitle")).isEmpty() ? title : LandingConfig.str(seo.get("ogTitle")));
        meta.put("ogDescription", LandingConfig.str(seo.get("ogDescription")).isEmpty() ? description : LandingConfig.str(seo.get("ogDescription")));
        meta.put("ogImage", og);
        meta.put("noindex", preview || !LandingConfig.bool(seo.get("index"), true));
        if (LandingConfig.bool(seo.get("structuredData"), true)) {
            meta.put("jsonLd", jsonLd(page, course, quote, description, canonical, og));
        }
        m.put("meta", meta);

        // marketing: ids only; landing.js loads them after the visitor accepts cookies, never in preview
        Map<String, Object> mk = new LinkedHashMap<>();
        mk.put("ga4", preview ? "" : LandingConfig.str(marketing.get("ga4")));
        mk.put("gtm", preview ? "" : LandingConfig.str(marketing.get("gtm")));
        mk.put("pixel", preview ? "" : LandingConfig.str(marketing.get("metaPixel")));
        String wa = LandingConfig.str(marketing.get("whatsapp")).replace("+", "");
        mk.put("whatsapp", wa.isEmpty() ? null : "https://wa.me/" + wa + "?text=" + java.net.URLEncoder.encode("Hi! I have a question about " + course.get("title"), java.nio.charset.StandardCharsets.UTF_8));
        mk.put("leadCapture", LandingConfig.bool(marketing.get("leadCapture"), false));
        m.put("marketing", mk);
        return m;
    }

    // ------------------------------------------------------------------------------------------------ sections

    @SuppressWarnings("unchecked")
    private void hero(Map<String, Object> v, Map<String, Object> c, Map<String, Object> course, Map<String, Object> price, Row page) {
        String heading = LandingConfig.str(c.get("heading"));
        v.put("heading", heading.isEmpty() ? course.get("title") : heading);
        String eyebrow = LandingConfig.str(c.get("eyebrow"));
        if (eyebrow.isEmpty()) {
            eyebrow = String.join(" · ", nonBlank((String) course.get("kind"), (String) course.get("medium"), (String) course.get("level")));
        }
        v.put("eyebrow", eyebrow);
        String sub = LandingConfig.str(c.get("subheading"));
        v.put("subheading", sub.isEmpty() ? course.get("subTitle") : sub);
        String body = LandingConfig.str(c.get("body"));
        v.put("body", paragraphs(body.isEmpty() ? firstNonBlank((String) course.get("intro"), (String) course.get("summary")) : body));
        String img = LandingConfig.str(c.get("image"));
        String alt = LandingConfig.str(c.get("imageAlt"));
        Map<String, Object> image = image(img.isEmpty() ? (String) course.get("image") : img, 1280,
                "immersive".equals(v.get("layout")) ? "100vw" : "(max-width: 900px) 100vw, 50vw");
        if (image != null) {
            image.put("alt", alt.isEmpty() ? "Artwork from " + course.get("title") : alt);
            image.put("eager", true);
        }
        v.put("image", image);
        String vid = LandingConfig.str(c.get("video"));
        LandingVideo.Embed e = LandingVideo.embed(vid.isEmpty() ? (String) course.get("introVideo") : vid);
        if (e != null) v.put("video", video(e, image == null ? null : (String) image.get("src"), (String) course.get("title")));
        List<Map<String, Object>> facts = new ArrayList<>();
        fact(facts, "Duration", (String) course.get("duration"));
        fact(facts, "Level", (String) course.get("level"));
        fact(facts, "Language", LandingConfig.str(c.get("language")));
        fact(facts, "Lessons", lessonsLabel(page.lng("course_id"), (String) course.get("sessions")));
        fact(facts, "Starts", (String) course.get("starts"));
        v.put("facts", facts);
        List<String> trust = new ArrayList<>();
        for (Map<String, Object> it : items(c)) trust.add(String.valueOf(it.get("title")));
        v.put("trust", trust);
        v.put("price", price);
        Row a = db.first("select id, name, profile_title, profile_photo from admins where id = ?", course.get("adminId"));
        if (a != null) {
            Map<String, Object> artist = new LinkedHashMap<>();
            artist.put("name", a.str("name"));
            artist.put("title", a.str("profile_title", ""));
            artist.put("photo", a.str("profile_photo", "").isEmpty() ? null : LandingImages.sized("/storage/uploads/teachers/" + a.get("id") + "/" + a.str("profile_photo"), 320));
            v.put("artist", artist);
        }
        Row stats = db.first("select count(*) as n, avg(rating) as avg from course_reviews where course_id = ? and status = 1", page.get("course_id"));
        if (stats != null && stats.lng("n", 0) > 0) {
            v.put("rating", Map.of("avg", BigDecimal.valueOf(Double.parseDouble(String.valueOf(stats.get("avg")))).setScale(1, RoundingMode.HALF_UP).toPlainString(),
                    "count", stats.lng("n"), "label", stats.lng("n") == 1 ? "review" : "reviews"));
        }
        long students = db.count("select count(distinct user_id) from orders where course_id = ? and payment_status = 1", page.get("course_id"));
        if (students >= 5) v.put("students", students);
    }

    @SuppressWarnings("unchecked")
    private void intro(Map<String, Object> v, Map<String, Object> c, Map<String, Object> course) {
        String body = LandingConfig.str(c.get("body"));
        v.put("body", paragraphs(body.isEmpty() ? String.join("\n\n", nonBlank((String) course.get("intro"), (String) course.get("introDetails"), (String) course.get("summary"))) : body));
        List<Map<String, Object>> items = items(c);
        if (items.isEmpty()) {
            List<String> forWhom = (List<String>) course.get("forWhom");
            List<String> problems = (List<String>) course.get("problems");
            if (!forWhom.isEmpty()) items.add(Map.of("title", "Who it's for", "text", String.join(", ", forWhom)));
            if (!problems.isEmpty()) items.add(Map.of("title", "What it fixes", "text", String.join(", ", problems)));
            List<String> del = (List<String>) course.get("deliverables");
            if (!del.isEmpty()) items.add(Map.of("title", "What you finish with", "text", String.join(", ", del)));
        }
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> m = new LinkedHashMap<>(items.get(i));
            m.putIfAbsent("textParas", paragraphs(LandingConfig.str(m.get("text"))));
            items.set(i, m);
        }
        v.put("items", items);
        v.put("hasItems", !items.isEmpty());
    }

    private void curriculum(Map<String, Object> v, long courseId) {
        List<Map<String, Object>> modules = new ArrayList<>();
        List<Row> lessons = db.select("select id, module_id, lesson_title, lesson_description, case when video_url is null or video_url = '' then 0 else 1 end as has_video"
                + " from course_module_lessons where course_id = ? and status = '1' order by sort_order, id", courseId);
        int i = 1;
        int total = 0;
        for (Row m : db.select("select id, module_title, module_duration from course_modules where course_id = ? and status = '1' order by sort_order, id", courseId)) {
            List<Map<String, Object>> own = new ArrayList<>();
            for (Row l : lessons) {
                if (m.lng("id").equals(l.lng("module_id"))) {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("title", l.str("lesson_title"));
                    x.put("text", HtmlCleaner.text(l.str("lesson_description", ""), 300));
                    x.put("video", l.lng("has_video", 0) == 1);
                    own.add(x);
                }
            }
            total += own.size();
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("n", i);
            mm.put("title", m.str("module_title"));
            mm.put("duration", m.str("module_duration", ""));
            mm.put("count", own.size() + (own.size() == 1 ? " lesson" : " lessons"));
            mm.put("lessons", own);
            mm.put("open", i == 1);
            modules.add(mm);
            i++;
        }
        v.put("modules", modules);
        v.put("summary", modules.size() + (modules.size() == 1 ? " module" : " modules") + " · " + total + (total == 1 ? " lesson" : " lessons"));
        if (((String) v.get("heading")).isEmpty()) v.put("heading", "Curriculum");
    }

    @SuppressWarnings("unchecked")
    private void artists(Map<String, Object> v, Map<String, Object> c, Map<String, Object> course) {
        List<Long> ids = new ArrayList<>();
        for (Object o : LandingConfig.list(c.get("ids"))) ids.add(Long.parseLong(String.valueOf(o).replace(".0", "")));
        if (ids.isEmpty()) ids.add(((Number) course.get("adminId")).longValue());
        List<Map<String, Object>> artists = new ArrayList<>();
        String extra = LandingConfig.str(c.get("body"));
        for (Long id : ids) {
            Row a = db.first("select id, name, designation, profile_title, profile_description, profile_photo, slug from admins where id = ?", id);
            if (a == null) continue;
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", a.str("name"));
            x.put("title", firstNonBlank(a.str("profile_title", ""), a.str("designation", "")));
            x.put("bio", paragraphs(artists.isEmpty() && !extra.isEmpty() ? extra : HtmlCleaner.text(a.str("profile_description", ""), 2000)));
            x.put("photo", a.str("profile_photo", "").isEmpty() ? null : image("/storage/uploads/teachers/" + a.get("id") + "/" + a.str("profile_photo"), 640, "(max-width: 900px) 80vw, 30vw"));
            x.put("initial", a.str("name").substring(0, 1));
            artists.add(x);
        }
        if (artists.isEmpty()) return;
        v.put("artists", artists);
        v.put("multiple", artists.size() > 1);
        v.put("items", items(c));
        List<Map<String, Object>> socials = new ArrayList<>();
        for (Object o : LandingConfig.list(c.get("socials"))) {
            String url = LandingConfig.str(o);
            String host = url.replaceFirst("^https://(www\\.)?", "").replaceFirst("/.*$", "");
            String label = host.contains("instagram") ? "Instagram" : host.contains("youtube") ? "YouTube" : host.contains("facebook") ? "Facebook"
                    : host.contains("linkedin") ? "LinkedIn" : host.contains("behance") ? "Behance" : host;
            socials.add(Map.of("url", url, "label", label));
        }
        v.put("socials", socials);
        LandingVideo.Embed e = LandingVideo.embed(LandingConfig.str(c.get("video")));
        if (e != null) v.put("video", video(e, null, "the artist"));
    }

    private void gallery(Map<String, Object> v, Map<String, Object> c, Map<String, Object> course) {
        List<Map<String, Object>> pics = new ArrayList<>();
        for (Map<String, Object> it : items(c)) {
            Object img = it.get("image");
            if (img == null) continue;
            Map<String, Object> p = image((String) img, 960, "(max-width: 600px) 100vw, (max-width: 1000px) 50vw, 33vw");
            if (p == null) continue;
            p.put("alt", LandingConfig.str(it.get("alt")).isEmpty() ? LandingConfig.str(it.get("title")) : LandingConfig.str(it.get("alt")));
            p.put("title", it.get("title"));
            p.put("text", it.get("text"));
            p.put("full", LandingImages.sized((String) img, 1600));
            pics.add(p);
        }
        if (pics.isEmpty()) {
            // the course photos and approved student artworks (both already public on the site)
            for (Row r : db.select("select photo_name from course_photos where course_id = ? order by id limit 4", course.get("id"))) {
                Map<String, Object> p = image("/storage/uploads/courses/" + course.get("id") + "/" + r.str("photo_name"), 960, "(max-width: 600px) 100vw, 33vw");
                if (p != null) {
                    p.put("alt", "Artwork from " + course.get("title"));
                    p.put("title", course.get("title"));
                    p.put("full", LandingImages.sized((String) p.get("src"), 1600));
                    pics.add(p);
                }
            }
            for (Row r : db.select("select a.id, a.photo_name, u.name from student_artworks a join users u on u.id = a.user_id where a.status = 1 order by a.id desc limit 8")) {
                Map<String, Object> p = image("/storage/uploads/artworks/" + r.get("id") + "/" + r.str("photo_name"), 960, "(max-width: 600px) 100vw, 33vw");
                if (p != null) {
                    p.put("alt", "Student artwork by " + r.str("name"));
                    p.put("title", "By " + r.str("name"));
                    p.put("text", "Student work");
                    p.put("full", LandingImages.sized("/storage/uploads/artworks/" + r.get("id") + "/" + r.str("photo_name"), 1600));
                    pics.add(p);
                }
            }
        }
        for (int i = 0; i < pics.size(); i++) pics.get(i).put("i", i);
        v.put("pictures", pics);
        String before = LandingConfig.str(c.get("beforeImage"));
        String after = LandingConfig.str(c.get("afterImage"));
        if (!before.isEmpty() && !after.isEmpty()) {
            v.put("compare", Map.of("before", LandingImages.sized(before, 1280), "after", LandingImages.sized(after, 1280)));
        }
    }

    @SuppressWarnings("unchecked")
    private void benefits(Map<String, Object> v, Map<String, Object> c, Map<String, Object> course, Row page) {
        List<Map<String, Object>> items = items(c);
        if (items.isEmpty()) {
            if (course.get("sessions") != null) items.add(Map.of("title", "Live, structured classes", "text", course.get("sessions") + (course.get("hours") == null ? "" : ", " + course.get("hours")) + " with the artist."));
            items.add(Map.of("title", "Practical demonstrations", "text", "Watch each technique painted step by step, then try it with feedback."));
            Map<String, Object> inc = (Map<String, Object>) course.get("includes");
            if (inc != null && "yes".equalsIgnoreCase(String.valueOf(inc.get("study_material_access")))) items.add(Map.of("title", "Downloadable resources", "text", "Reference photos, colour charts and lesson notes."));
            if (inc != null && "yes".equalsIgnoreCase(String.valueOf(inc.get("certificate")))) items.add(Map.of("title", "Certificate of completion", "text", "Earned when your final work is approved."));
            Row pr = db.first("select access_duration from landing_pricing where landing_page_id = ?", page.get("id"));
            if (pr != null && !pr.str("access_duration", "").isEmpty()) items.add(Map.of("title", "Course access", "text", pr.str("access_duration")));
        }
        numbered(items);
        v.put("items", items);
    }

    private boolean testimonials(Map<String, Object> v, Map<String, Object> c, long courseId) {
        String source = LandingConfig.str(c.get("source"));
        List<Map<String, Object>> quotes = new ArrayList<>();
        if (!"testimonials".equals(source)) {
            for (Row r : db.select("select r.rating, r.review, u.name, u.id as user_id, u.profile_photo from course_reviews r join users u on u.id = r.added_by_id and r.added_by_type = 'Student'"
                    + " where r.course_id = ? and r.status = 1 and r.review is not null and r.review <> '' order by r.rating desc, r.id desc limit 9", courseId)) {
                Map<String, Object> q = new LinkedHashMap<>();
                q.put("name", r.str("name"));
                q.put("initial", r.str("name").substring(0, 1));
                q.put("text", HtmlCleaner.text(r.str("review"), 600));
                q.put("rating", r.lng("rating", 0));
                q.put("stars", "★".repeat((int) Math.max(0, Math.min(5, r.lng("rating", 0)))));
                q.put("photo", r.str("profile_photo", "").isEmpty() ? null : LandingImages.sized("/storage/uploads/students/" + r.get("user_id") + "/" + r.str("profile_photo"), 320));
                q.put("meta", "Verified student");
                quotes.add(q);
            }
        }
        if (!"reviews".equals(source)) {
            List<Long> ids = LandingCoupons.ids(c.get("ids"));
            String where = ids.isEmpty() ? "" : " and id in (" + String.join(",", ids.stream().map(String::valueOf).toList()) + ")";
            for (Row t : db.select("select id, name, title, photo, description from testimonials where status = 1" + where + " order by id desc limit 9")) {
                Map<String, Object> q = new LinkedHashMap<>();
                q.put("name", t.str("name"));
                q.put("initial", t.str("name").substring(0, 1));
                q.put("text", HtmlCleaner.text(t.str("description", ""), 600));
                q.put("photo", t.str("photo", "").isEmpty() ? null : LandingImages.sized("/storage/uploads/testimonials/" + t.get("id") + "/" + t.str("photo"), 320));
                q.put("meta", t.str("title", ""));
                quotes.add(q);
            }
        }
        quotes.removeIf(q -> String.valueOf(q.get("text")).isBlank());
        if (quotes.isEmpty()) return false;
        v.put("quotes", quotes);
        return true;
    }

    private void pricing(Map<String, Object> v, Row page, Map<String, Object> price, List<LandingPayments.Choice> choices) {
        Row pr = db.first("select access_duration, refund_policy, inclusions from landing_pricing where landing_page_id = ?", page.get("id"));
        List<String> inc = new ArrayList<>();
        if (pr != null && !pr.str("inclusions", "").isEmpty()) {
            for (String s : pr.str("inclusions").split("\n")) if (!s.isBlank()) inc.add(s.trim());
        }
        v.put("inclusions", inc);
        v.put("access", pr == null ? null : pr.str("access_duration", null));
        v.put("refund", pr == null || pr.str("refund_policy", "").isEmpty() ? null : paragraphs(pr.str("refund_policy")));
        v.put("price", price);
        List<Map<String, Object>> pay = new ArrayList<>();
        for (LandingPayments.Choice ch : choices) pay.add(Map.of("label", ch.label(), "key", ch.key()));
        v.put("payments", pay);
        v.put("hasPayments", !pay.isEmpty());
        boolean coupons = (boolean) price.get("couponsEnabled") && db.count("select count(*) from landing_coupons c where c.is_active = 1 and (c.expires_at is null or c.expires_at > ?)"
                + " and (c.all_pages = 1 or exists (select 1 from landing_coupon_targets t where t.coupon_id = c.id and ((t.target_type = 'PAGE' and t.target_id = ?)"
                + " or (t.target_type = 'COURSE' and t.target_id = ?))))", LocalDateTime.now(), page.get("id"), page.get("course_id")) > 0;
        v.put("couponNote", coupons);
        if (((String) v.get("heading")).isEmpty()) v.put("heading", "Enrol today");
    }

    private List<Map<String, Object>> related(long courseId, Object mediumId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Row r : db.select("select c.id, c.slug, c.title, c.sub_title, (select photo_name from course_photos p where p.course_id = c.id order by p.id limit 1) as photo,"
                + " (select l.slug from landing_pages l where l.course_id = c.id and l.status = 'PUBLISHED' and l.slug = c.slug limit 1) as landing"
                + " from courses c where c.status = 1 and c.id <> ? order by case when c.medium_id = ? then 0 else 1 end, c.id limit 3", courseId, mediumId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", r.str("title"));
            m.put("text", r.str("sub_title", ""));
            m.put("url", "/course/" + r.str("slug"));
            m.put("image", r.get("photo") == null ? null : image("/storage/uploads/courses/" + r.get("id") + "/" + r.get("photo"), 640, "(max-width: 700px) 100vw, 33vw"));
            out.add(m);
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ helpers


    public static String inr(BigDecimal v) {
        if (v == null) return null;
        BigDecimal r = v.setScale(2, RoundingMode.HALF_UP);
        boolean whole = r.stripTrailingZeros().scale() <= 0;
        String plain = (whole ? r.setScale(0, RoundingMode.HALF_UP) : r).abs().toPlainString();   // paise: always two digits
        String units = plain.contains(".") ? plain.substring(0, plain.indexOf('.')) : plain;
        String paise = plain.contains(".") ? plain.substring(plain.indexOf('.')) : "";
        return (r.signum() < 0 ? "-₹" : "₹") + indian(units) + paise;
    }

    /** Indian digit grouping (1,23,45,678), the same as the React pages' toLocaleString('en-IN'), whatever the JDK's locale data. */
    static String indian(String digits) {
        if (digits.length() <= 3) return digits;
        String last3 = digits.substring(digits.length() - 3);
        String rest = digits.substring(0, digits.length() - 3);
        StringBuilder out = new StringBuilder();
        for (int i = rest.length(); i > 0; i -= 2) {
            out.insert(0, rest.substring(Math.max(0, i - 2), i));
            if (i - 2 > 0) out.insert(0, ',');
        }
        return out + "," + last3;
    }

    public static String usd(BigDecimal v) {
        BigDecimal r = v.setScale(2, RoundingMode.HALF_UP);
        return "US$" + (r.stripTrailingZeros().scale() <= 0 ? r.setScale(0, RoundingMode.HALF_UP).toPlainString() : r.toPlainString());
    }

    Map<String, Object> price(LandingPricing.Quote q) {
        Map<String, Object> p = new LinkedHashMap<>(q.view());
        p.put("totalLabel", q.free() ? "Free" : inr(q.total()));
        boolean struck = q.list().compareTo(q.subtotal()) > 0;
        p.put("listLabel", struck ? inr(q.list()) : null);
        p.put("saving", struck ? inr(q.list().subtract(q.subtotal())) : null);
        p.put("savingPct", struck && q.list().signum() > 0 ? q.list().subtract(q.subtotal()).multiply(BigDecimal.valueOf(100)).divide(q.list(), 0, RoundingMode.HALF_UP) + "% off" : null);
        p.put("offerEndsLabel", q.offerEnds() == null ? null : q.offerEnds().format(DAY_TIME));
        p.put("offerEndsIso", q.offerEnds() == null ? null : q.offerEnds().toString());
        p.put("taxNote", q.tax().signum() == 0 ? null : "ADDED".equals(q.taxMode()) ? "+ " + q.taxLabel() + " " + inr(q.tax()) + " at checkout"
                : "Includes " + q.taxLabel());
        p.put("usdLabel", q.totalUsd() == null ? null : usd(q.totalUsd()));
        return p;
    }

    /** {src, srcset, sizes} for an image; null when there is no image. */
    static Map<String, Object> image(String url, int width, String sizes) {
        if (url == null || url.isBlank()) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("src", LandingImages.sized(url, Math.min(width, 960)));
        String set = LandingImages.srcset(url, width);
        m.put("srcset", set);
        m.put("sizes", set == null ? null : sizes);
        return m;
    }

    private static Map<String, Object> video(LandingVideo.Embed e, String poster, String title) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", e.kind());
        m.put("src", e.src());
        m.put("file", "file".equals(e.kind()));
        m.put("poster", e.poster() != null ? e.poster() : poster);
        m.put("title", "Video: " + title);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> c) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : LandingConfig.list(c.get("items"))) {
            Map<String, Object> it = new LinkedHashMap<>((Map<String, Object>) o);
            it.put("textParas", paragraphs(LandingConfig.str(it.get("text"))));
            out.add(it);
        }
        return out;
    }

    private static void numbered(List<Map<String, Object>> items) {
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> m = new LinkedHashMap<>(items.get(i));
            m.put("n", String.format("%02d", i + 1));
            m.putIfAbsent("textParas", paragraphs(LandingConfig.str(m.get("text"))));
            m.putIfAbsent("image", null);
            items.set(i, m);
        }
    }

    private String lessonsLabel(long courseId, String sessions) {
        long lessons = db.count("select count(*) from course_module_lessons where course_id = ? and status = '1'", courseId);
        String l = lessons == 0 ? null : lessons + (lessons == 1 ? " lesson" : " lessons");
        return l == null ? sessions : sessions == null ? l : l + " · " + sessions;
    }

    private static void fact(List<Map<String, Object>> facts, String label, String value) {
        if (value != null && !value.isBlank()) facts.add(Map.of("label", label, "value", value));
    }

    private List<String> strings(String jsonList) {
        List<String> out = new ArrayList<>();
        Object o = jsonList == null || jsonList.isBlank() ? null : json.decode(jsonList);
        if (o instanceof List<?> l) for (Object x : l) if (x != null && !String.valueOf(x).isBlank()) out.add(String.valueOf(x).trim());
        return out;
    }

    static List<String> paragraphs(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String p : text.split("\\n\\s*\\n|\\n")) if (!p.isBlank()) out.add(p.trim());
        return out;
    }

    private static String plain(String html) {
        return html == null || html.isBlank() ? "" : HtmlCleaner.text(html, 4000);
    }

    private static List<String> nonBlank(String... values) {
        List<String> out = new ArrayList<>();
        for (String s : values) if (s != null && !s.isBlank()) out.add(s);
        return out;
    }

    static String firstNonBlank(String... values) {
        for (String s : values) if (s != null && !s.isBlank()) return s;
        return "";
    }

    private static String cut(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max - 1).replaceFirst("\\s+\\S*$", "") + "…";
    }

    private static String hexOr(Object o, String def) {
        String s = LandingConfig.str(o);
        return s.matches("^#[0-9A-Fa-f]{6}$") ? s : def;
    }

    static String shade(String hex, double f) {
        int r = (int) (Integer.parseInt(hex.substring(1, 3), 16) * f);
        int g = (int) (Integer.parseInt(hex.substring(3, 5), 16) * f);
        int b = (int) (Integer.parseInt(hex.substring(5, 7), 16) * f);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    /** White or near-black text on the accent colour, whichever has more contrast (WCAG). */
    static String contrastInk(String hex) {
        double l = luminance(hex);
        return (1.05 / (l + 0.05)) >= ((l + 0.05) / 0.06) ? "#FFFFFF" : "#111111";
    }

    static double luminance(String hex) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = Integer.parseInt(hex.substring(1 + i * 2, 3 + i * 2), 16) / 255.0;
            c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    private String jsonLd(Row page, Map<String, Object> course, LandingPricing.Quote q, String description, String url, String image) {
        Map<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        ld.put("@type", "Course");
        ld.put("name", course.get("title"));
        ld.put("description", description);
        ld.put("url", url);
        if (image != null) ld.put("image", image);
        ld.put("provider", Map.of("@type", "Organization", "name", "ArtistikCity", "sameAs", url.replaceFirst("(https?://[^/]+).*", "$1")));
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("@type", "Offer");
        offer.put("price", q.total().toPlainString());
        offer.put("priceCurrency", "INR");
        offer.put("availability", "https://schema.org/InStock");
        offer.put("category", "Paid");
        offer.put("url", url);
        if (q.offerEnds() != null) offer.put("priceValidUntil", q.offerEnds().toLocalDate().toString());
        ld.put("offers", offer);
        ld.put("hasCourseInstance", Map.of("@type", "CourseInstance", "courseMode", "Online", "courseWorkload", course.get("duration") == null ? "" : String.valueOf(course.get("duration"))));
        Row stats = db.first("select count(*) as n, avg(rating) as avg from course_reviews where course_id = ? and status = 1 and rating is not null", page.get("course_id"));
        if (stats != null && stats.lng("n", 0) > 0) {
            ld.put("aggregateRating", Map.of("@type", "AggregateRating", "ratingValue", String.valueOf(stats.get("avg")).replaceFirst("(\\.\\d).*", "$1"), "reviewCount", stats.lng("n")));
        }
        // safe inside <script>: no "</" can close the tag
        return json.encode(ld).replace("</", "<\\/").replace("<!--", "<\\!--");
    }
}
