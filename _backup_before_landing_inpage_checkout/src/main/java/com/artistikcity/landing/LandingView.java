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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LandingView.class);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private final Db db;
    private final Json json;
    private final LandingCommerce commerce;
    private final LandingPayments payments;
    private final com.artistikcity.commission.CommissionPricing commissionPricing;

    public LandingView(Db db, Json json, LandingCommerce commerce, LandingPayments payments, com.artistikcity.commission.CommissionPricing commissionPricing) {
        this.db = db;
        this.json = json;
        this.commerce = commerce;
        this.payments = payments;
        this.commissionPricing = commissionPricing;
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
        boolean site = LandingPages.isSite(page);
        long courseId = site ? 0L : page.lng("course_id");
        Map<String, Object> course = site ? new LinkedHashMap<>() : course(courseId);
        LandingPricing.Quote quote = site ? null : commerce.quote(page);
        List<LandingPayments.Choice> choices = site ? List.of() : payments.choices(payments.forPage(pageId), quote.totalUsd() != null);
        Map<String, Object> design = LandingConfig.map(config.get("design"));
        Map<String, Object> cta = LandingConfig.map(config.get("cta"));
        Map<String, Object> seo = LandingConfig.map(config.get("seo"));
        Map<String, Object> marketing = LandingConfig.map(config.get("marketing"));
        // site pages: the main button goes where the editor says; course pages: always the page's own checkout
        String enroll = site ? firstNonBlank(LandingConfig.str(cta.get("primaryHref")), "/courses?type=all") : LandingPages.enrollPath(page.str("slug"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pageId", pageId);
        m.put("slug", page.str("slug"));
        m.put("site", site);
        m.put("pageName", page.str("name"));
        m.put("preview", preview);
        m.put("enrollUrl", enroll);
        m.put("ctaPrimary", LandingConfig.str(cta.get("primary")).isEmpty() ? (site ? "Explore courses" : "Enroll now") : LandingConfig.str(cta.get("primary")));
        m.put("sticky", !site && LandingConfig.bool(cta.get("sticky"), true));
        m.put("course", course);
        m.put("accent", hexOr(design.get("accent"), "#9C0D48"));
        m.put("accentDark", shade(hexOr(design.get("accent"), "#9C0D48"), 0.82));
        m.put("accentInk", contrastInk(hexOr(design.get("accent"), "#9C0D48")));
        m.put("ink", hexOr(design.get("ink"), "#14213D"));
        m.put("surface", hexOr(design.get("surface"), "#FBF8F4"));
        m.put("fontSerif", "serif".equals(design.get("font")));
        m.put("radiusSharp", "sharp".equals(design.get("radius")));
        m.put("texture", LandingConfig.str(design.get("texture")).isEmpty() ? "none" : LandingConfig.str(design.get("texture")));

        Map<String, Object> price = site ? null : price(quote);
        m.put("price", price);

        // sections
        boolean forWhomCards = LandingConfig.list(config.get("sections")).stream().map(LandingConfig::map)
                .anyMatch(x -> "cards".equals(x.get("type")) && LandingConfig.bool(x.get("enabled"), true)
                        && "course_for".equals(LandingConfig.map(x.get("content")).get("source")));
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
            String kicker = LandingConfig.str(c.get("kicker"));
            v.put("kicker", kicker.isEmpty() ? defaultKicker(type, site) : kicker);
            v.put("index", n++);
            v.put("lazy", n > 1);
            if (site && LandingCatalog.COURSE_ONLY.contains(type)) {
                continue;       // never on a site page (the editor refuses them too)
            }
            try {
            switch (type) {
                case "hero" -> {
                    if (site) siteHero(v, c, page);
                    else hero(v, c, course, price, page);
                }
                case "intro" -> {
                    v.put("forWhomElsewhere", forWhomCards);
                    intro(v, c, course);
                    v.remove("forWhomElsewhere");
                }
                case "outcomes" -> {
                    List<Map<String, Object>> items = items(c);
                    if (items.isEmpty() && !course.isEmpty()) {
                        for (String s2 : (List<String>) course.get("learn")) items.add(Map.of("title", s2, "text", ""));
                        for (String s2 : (List<String>) course.get("highlights")) items.add(Map.of("title", s2, "text", ""));
                    }
                    numbered(items);
                    v.put("items", items);
                    v.put("hasItems", !items.isEmpty());
                }
                case "curriculum" -> curriculum(v, page.lng("course_id"));
                case "artist" -> {
                    if (site && LandingConfig.list(c.get("ids")).isEmpty()) continue;
                    artists(v, c, course);
                    if (v.get("artists") == null) continue;
                }
                case "gallery" -> gallery(v, c, course);
                case "benefits" -> {
                    benefits(v, c, course, page);
                    if (((List<?>) v.get("items")).isEmpty()) continue;
                }
                case "video" -> {
                    LandingVideo.Embed e = LandingVideo.embed(LandingConfig.str(c.get("video")).isEmpty() ? (String) course.get("introVideo") : LandingConfig.str(c.get("video")));
                    if (e == null) continue;
                    v.put("video", video(e, (String) course.get("image"), (String) course.get("title")));
                }
                case "testimonials" -> {
                    if (!testimonials(v, c, courseId)) continue;
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
                    List<Map<String, Object>> rel = related(courseId, course.get("mediumId"));
                    if (rel.isEmpty()) continue;
                    v.put("items", rel);
                }
                case "final_cta" -> {
                    if (((String) v.get("heading")).isEmpty()) v.put("heading", "Start painting with us");
                    String img = LandingConfig.str(c.get("image"));
                    v.put("image", image(img.isEmpty() ? (String) course.get("image") : img, 960, "(max-width: 900px) 100vw, 40vw"));
                    List<Map<String, Object>> buttons = new ArrayList<>();
                    int b = 0;
                    for (Map<String, Object> it : items(c)) {
                        if (LandingConfig.str(it.get("link")).isEmpty() || LandingConfig.str(it.get("title")).isEmpty()) continue;
                        buttons.add(Map.of("label", it.get("title"), "href", it.get("link"), "cta", "final-" + (++b), "first", b == 1));
                    }
                    v.put("buttons", buttons);
                    v.put("hasButtons", !buttons.isEmpty());
                }
                case "cards" -> {
                    if (!cards(v, c, course)) continue;
                }
                case "steps" -> {
                    List<Map<String, Object>> items = items(c);
                    if (items.isEmpty()) continue;
                    numbered(items);
                    for (Map<String, Object> it : items) linkOf(it);
                    v.put("items", items);
                    v.put("count", items.size());
                }
                case "courses" -> {
                    if (!courses(v, c)) continue;
                }
                case "artists" -> {
                    if (!artistCards(v, c)) continue;
                }
                case "commission_form" -> commissionForm(v);
                case "commission_pricing" -> commissionPricing(v);
                case "custom" -> {
                    String html = LandingConfig.str(c.get("html"));
                    if (html.isEmpty() && ((String) v.get("heading")).isEmpty()) continue;
                    v.put("html", HtmlCleaner.clean(html));
                }
                default -> { }
            }
            } catch (RuntimeException ex) {
                // a section that can't be built (unexpected data) is left out and logged; the rest of the page still shows
                log.warn("Landing page {}: section {} ({}) skipped: {}", pageId, v.get("id"), type, ex.toString(), ex);
                continue;
            }
            sections.add(v);
        }
        m.put("sections", sections);
        Object exploreId = sections.stream().filter(x -> Boolean.TRUE.equals(x.get("is_curriculum"))).map(x -> x.get("id")).findFirst()
                .orElse(sections.size() > 1 ? sections.get(1).get("id") : null);
        m.put("exploreId", exploreId);
        // the second hero button: a link set in the editor, the course preview video (only when there is one), or "explore"
        String secondLabel = LandingConfig.str(cta.get("secondary"));
        String secondHref = LandingConfig.str(cta.get("secondaryHref"));
        Map<String, Object> second = null;
        if (LandingConfig.bool(cta.get("secondaryVideo"), false)) {
            Object videoAt = sections.stream().filter(x -> x.get("video") != null).map(x -> x.get("id")).findFirst().orElse(null);
            if (videoAt != null) {
                second = Map.of("label", secondLabel.isEmpty() ? "Watch the preview" : secondLabel, "href", "#" + videoAt, "play", true);
            } else if (exploreId != null) {
                // no preview video on the course yet: never a button that promises one
                second = Map.of("label", "Explore the course", "href", "#" + exploreId, "play", false);
            }
        } else if (!secondLabel.isEmpty()) {
            String href = !secondHref.isEmpty() ? secondHref : exploreId == null ? null : "#" + exploreId;
            if (href != null) second = Map.of("label", secondLabel, "href", href, "play", false);
        }
        m.put("secondary", second);

        // SEO
        String title = LandingConfig.str(seo.get("title"));
        String description = LandingConfig.str(seo.get("description"));
        Map<String, Object> heroModel = sections.isEmpty() ? Map.of() : sections.get(0);
        if (title.isEmpty()) title = site ? page.str("name") + " | ArtistikCity" : course.get("title") + " · Online course | ArtistikCity";
        if (description.isEmpty()) description = site ? cut(firstNonBlank(LandingConfig.str(heroModel.get("subheading"))), 160)
                : cut(firstNonBlank((String) course.get("subTitle"), (String) course.get("summary"), (String) course.get("intro")), 160);
        String canonical = LandingConfig.str(seo.get("canonical"));
        if (canonical.isEmpty()) canonical = baseUrl + LandingPages.publicPath(page);
        else if (canonical.startsWith("/")) canonical = baseUrl + canonical;
        String og = LandingConfig.str(seo.get("ogImage"));
        if (og.isEmpty()) og = site ? heroImageUrl(heroModel) : LandingImages.sized((String) course.get("image"), 1280);
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
            meta.put("jsonLd", site ? siteJsonLd(title, description, canonical, og, sections, baseUrl) : jsonLd(page, course, quote, description, canonical, og));
        }
        m.put("meta", meta);

        // marketing: ids only; landing.js loads them after the visitor accepts cookies, never in preview
        Map<String, Object> mk = new LinkedHashMap<>();
        mk.put("ga4", preview ? "" : LandingConfig.str(marketing.get("ga4")));
        mk.put("gtm", preview ? "" : LandingConfig.str(marketing.get("gtm")));
        mk.put("pixel", preview ? "" : LandingConfig.str(marketing.get("metaPixel")));
        String wa = LandingConfig.str(marketing.get("whatsapp")).replace("+", "");
        mk.put("whatsapp", wa.isEmpty() ? null : "https://wa.me/" + wa + "?text=" + java.net.URLEncoder.encode("Hi! I have a question about " + (site ? page.str("name") : course.get("title")), java.nio.charset.StandardCharsets.UTF_8));
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
        v.put("body", paragraphs(body.isEmpty() && !course.isEmpty() ? String.join("\n\n", nonBlank((String) course.get("intro"), (String) course.get("introDetails"), (String) course.get("summary"))) : body));
        List<Map<String, Object>> items = items(c);
        if (items.isEmpty() && !course.isEmpty()) {
            List<String> forWhom = (List<String>) course.getOrDefault("forWhom", List.of());
            List<String> problems = (List<String>) course.getOrDefault("problems", List.of());
            // "Who it's for" is left out when the page has its own "Who should join" cards built from the same list
            if (!forWhom.isEmpty() && !Boolean.TRUE.equals(v.get("forWhomElsewhere"))) items.add(Map.of("title", "Who it's for", "text", String.join(", ", forWhom)));
            if (!problems.isEmpty()) items.add(Map.of("title", "What it fixes", "text", String.join(", ", problems)));
            List<String> del = (List<String>) course.getOrDefault("deliverables", List.of());
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
            x.put("initial", initial(a.str("name", "")));
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
        if (pics.isEmpty() && "marketplace".equals(LandingConfig.str(c.get("source")))) {
            // artworks listed in the marketplace (already public, with the alt text the artist gave), newest featured first
            for (Row r : db.select("select p.id, p.title, p.artist_name, p.medium, i.image_url, i.alt_text from paintings p join painting_images i on i.painting_id = p.id and i.is_primary = 1"
                    + " where p.stock_status in ('AVAILABLE', 'RESERVED', 'SOLD') order by p.is_featured desc, p.id desc limit 9")) {
                Map<String, Object> pic = image(r.str("image_url"), 960, "(max-width: 600px) 100vw, (max-width: 1000px) 50vw, 33vw");
                if (pic == null) continue;
                pic.put("alt", firstNonBlank(r.str("alt_text", ""), r.str("title") + " by " + r.str("artist_name")));
                pic.put("title", r.str("title"));
                pic.put("text", String.join(" · ", nonBlank(r.str("artist_name", ""), r.str("medium", ""))));
                pic.put("full", LandingImages.sized(r.str("image_url"), 1600));
                pics.add(pic);
            }
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
        if (items.isEmpty() && !course.isEmpty()) {
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
                q.put("initial", initial(r.str("name", "")));
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
                q.put("initial", initial(t.str("name", "")));
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
                + " (select l.slug from landing_pages l where l.course_id = c.id and l.status = 'PUBLISHED' order by l.published_at desc limit 1) as landing"
                + " from courses c where c.status = 1 and c.id <> ? order by case when c.medium_id = ? then 0 else 1 end, c.id limit 3", courseId, mediumId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", r.str("title"));
            m.put("text", r.str("sub_title", ""));
            m.put("url", r.get("landing") == null ? "/course/" + r.str("slug") : LandingPages.path(r.str("landing")));
            m.put("image", r.get("photo") == null ? null : image("/storage/uploads/courses/" + r.get("id") + "/" + r.get("photo"), 640, "(max-width: 700px) 100vw, 33vw"));
            out.add(m);
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------- site pages and new sections

    private static String defaultKicker(String type, boolean site) {
        return switch (type) {
            case "intro" -> site ? "" : "About the course";
            case "outcomes" -> "Learning outcomes";
            case "gallery" -> "Gallery";
            case "benefits" -> "Benefits";
            case "video" -> "Preview";
            case "testimonials" -> site ? "In their words" : "Student reviews";
            case "pricing" -> "Enrolment";
            case "faq" -> "FAQ";
            case "courses" -> "Courses";
            case "artists" -> "Artists";
            case "commission_form" -> "Step 1 of 6";
            case "commission_pricing" -> "Pricing";
            default -> "";
        };
    }

    /** Hero of a site page: no price, no course facts; one artwork or a collage of up to four. */
    private void siteHero(Map<String, Object> v, Map<String, Object> c, Row page) {
        String heading = LandingConfig.str(c.get("heading"));
        v.put("heading", heading.isEmpty() ? page.str("name") : heading);
        v.put("eyebrow", LandingConfig.str(c.get("eyebrow")));
        String sub = LandingConfig.str(c.get("subheading"));
        v.put("subheading", sub.isEmpty() ? null : sub);
        List<Map<String, Object>> collage = new ArrayList<>();
        int i = 0;
        for (Object o : LandingConfig.list(c.get("images"))) {
            Map<String, Object> img = image(LandingConfig.str(o), 960, i == 0 ? "(max-width: 900px) 90vw, 36vw" : "(max-width: 900px) 45vw, 18vw");
            if (img == null) continue;
            img.put("alt", "");          // decorative: the artworks are described in the gallery below
            img.put("n", ++i);
            img.put("eager", i == 1);
            collage.add(img);
        }
        String alt = LandingConfig.str(c.get("imageAlt"));
        Map<String, Object> image = image(LandingConfig.str(c.get("image")), 1280,
                "immersive".equals(v.get("layout")) ? "100vw" : "(max-width: 900px) 100vw, 50vw");
        if (image == null && !collage.isEmpty()) {
            image = new LinkedHashMap<>(collage.get(0));
        }
        if (image != null) {
            image.put("alt", alt);
            image.put("eager", true);
        }
        if ("collage".equals(v.get("layout")) && !collage.isEmpty()) {
            if (!alt.isEmpty()) collage.get(0).put("alt", alt);
            v.put("collage", collage);
            v.put("image", null);
            v.put("preloadImage", collage.get(0));
        } else {
            v.put("image", image);
        }
        LandingVideo.Embed e = LandingVideo.embed(LandingConfig.str(c.get("video")));
        if (e != null) v.put("video", video(e, image == null ? null : (String) image.get("src"), page.str("name")));
        List<String> trust = new ArrayList<>();
        for (Map<String, Object> it : items(c)) trust.add(String.valueOf(it.get("title")));
        v.put("trust", trust);
        v.put("facts", List.of());
    }

    private static String heroImageUrl(Map<String, Object> hero) {
        Object img = hero.get("image") != null ? hero.get("image") : hero.get("preloadImage");
        if (!(img instanceof Map<?, ?> m) || m.get("src") == null) return null;
        String src = String.valueOf(m.get("src"));
        java.util.regex.Matcher x = java.util.regex.Pattern.compile("^/img/w\\d+/(uploads/.*)$").matcher(src);
        return x.matches() ? LandingImages.sized("/storage/" + x.group(1), 1280) : src;
    }

    /** Adds "href" / "label" for an item's optional link (internal links open in the same tab). */
    private static void linkOf(Map<String, Object> it) {
        String href = LandingConfig.str(it.get("link"));
        if (href.isEmpty()) return;
        it.put("href", href);
        it.put("hrefLabel", LandingConfig.str(it.get("linkLabel")).isEmpty() ? "Learn more" : LandingConfig.str(it.get("linkLabel")));
        it.put("external", href.startsWith("https://"));
    }

    @SuppressWarnings("unchecked")
    private boolean cards(Map<String, Object> v, Map<String, Object> c, Map<String, Object> course) {
        String source = LandingConfig.str(c.get("source"));
        List<Map<String, Object>> items = items(c);
        if (!source.isEmpty() && !"custom".equals(source)) {
            // the course's own lists (Admin › Courses), so the page never says more than the course does
            String key = switch (source) {
                case "course_for" -> "forWhom";
                case "course_learn" -> "learn";
                default -> "deliverables";
            };
            Object list = course.get(key);
            if (list instanceof List<?> l && !l.isEmpty()) {
                items = new ArrayList<>();
                for (Object x : l) items.add(new LinkedHashMap<>(Map.of("title", String.valueOf(x), "text", "")));
            }
        }
        if (items.isEmpty()) return false;
        numbered(items);
        for (Map<String, Object> it : items) {
            linkOf(it);
            Object img = it.get("image");
            if (img != null && !String.valueOf(img).isEmpty()) {
                Map<String, Object> pic = image(String.valueOf(img), 640, "(max-width: 700px) 100vw, 33vw");
                if (pic != null) pic.put("alt", LandingConfig.str(it.get("alt")));
                it.put("pic", pic);
            }
        }
        v.put("items", items);
        v.put("hasItems", true);
        return true;
    }

    /** Published courses and workshops (the catalogue), each linking to the page visitors would land on for it. */
    private boolean courses(Map<String, Object> v, Map<String, Object> c) {
        List<Long> ids = LandingCoupons.ids(c.get("ids"));
        int limit = (int) Math.max(1, Math.min(12, Double.parseDouble(String.valueOf(c.getOrDefault("limit", 6)))));
        String where = ids.isEmpty() ? "" : " and c.id in (" + String.join(",", ids.stream().map(String::valueOf).toList()) + ")";
        List<Map<String, Object>> out = new ArrayList<>();
        for (Row r : db.select("select c.id, c.slug, c.title, c.sub_title, c.course_type_id, c.duration,"
                + " (select name from mediums m where m.id = c.medium_id) as medium, (select name from skills k where k.id = c.skill_id) as level,"
                + " (select name from admins a where a.id = c.admin_id) as artist,"
                + " (select photo_name from course_photos p where p.course_id = c.id order by p.id limit 1) as photo,"
                + " (select l.slug from landing_pages l where l.course_id = c.id and l.status = 'PUBLISHED' order by l.published_at desc limit 1) as landing"
                + " from courses c where c.status = 1" + where + " order by c.id limit " + limit)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", r.str("title"));
            m.put("text", r.str("sub_title", ""));
            m.put("kind", r.lng("course_type_id", 1) == 2 ? "Workshop" : "Live course");
            m.put("meta", String.join(" · ", nonBlank(r.str("medium", ""), r.str("level", ""))));
            m.put("artist", r.str("artist", ""));
            m.put("url", r.get("landing") == null ? "/course/" + r.str("slug") : LandingPages.path(r.str("landing")));
            m.put("image", r.get("photo") == null ? null : image("/storage/uploads/courses/" + r.get("id") + "/" + r.get("photo"), 640, "(max-width: 700px) 100vw, 33vw"));
            m.put("alt", "Artwork from " + r.str("title"));
            out.add(m);
        }
        if (out.isEmpty()) return false;
        v.put("items", out);
        return true;
    }

    /** Artist profiles (Admin › Instructors), linking to their public page /teacher/{slug}. */
    private boolean artistCards(Map<String, Object> v, Map<String, Object> c) {
        List<Long> ids = LandingCoupons.ids(c.get("ids"));
        int limit = (int) Math.max(1, Math.min(12, Double.parseDouble(String.valueOf(c.getOrDefault("limit", 6)))));
        String where = ids.isEmpty() ? " where a.admin_type = 'teacher'" : " where a.id in (" + String.join(",", ids.stream().map(String::valueOf).toList()) + ")";
        List<Map<String, Object>> out = new ArrayList<>();
        for (Row a : db.select("select a.id, a.name, a.slug, a.designation, a.profile_title, a.profile_description, a.profile_photo,"
                + " (select count(*) from courses c where c.admin_id = a.id and c.status = 1) as courses from admins a" + where + " order by courses desc, a.id limit " + limit)) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("name", a.str("name"));
            x.put("initial", initial(a.str("name", "")));
            x.put("title", firstNonBlank(a.str("profile_title", ""), a.str("designation", "")));
            x.put("bio", cut(HtmlCleaner.text(a.str("profile_description", ""), 600), 200));
            x.put("photo", a.str("profile_photo", "").isEmpty() ? null : image("/storage/uploads/teachers/" + a.get("id") + "/" + a.str("profile_photo"), 640, "(max-width: 700px) 60vw, 25vw"));
            x.put("url", a.str("slug", "").isEmpty() ? null : "/teacher/" + a.str("slug"));
            long n = a.lng("courses", 0);
            x.put("courses", n == 0 ? null : n + (n == 1 ? " course" : " courses") + " on ArtistikCity");
            out.add(x);
        }
        if (out.isEmpty()) return false;
        v.put("artists", out);
        return true;
    }

    /** The commission styles, exactly as step 1 of the wizard offers them (CommissionStore.THEMES). */
    public static final List<Map<String, String>> COMMISSION_THEMES = List.of(
            Map.of("id", "PORTRAIT", "title", "Classic Portraiture", "blurb", "Oil-style painting of a person or pet, rich tones and soft brushwork."),
            Map.of("id", "LANDSCAPE", "title", "Scenic Landscape", "blurb", "Nature, cityscapes and architecture with vivid, textured colour."),
            Map.of("id", "WATERCOLOR", "title", "Fluid Watercolor", "blurb", "Soft edges, paper texture and gentle colour bleeds."),
            Map.of("id", "CHARCOAL", "title", "Expressive Charcoal", "blurb", "High-contrast monochrome sketch with bold, textured strokes."));

    /** Same order and labels as the wizard's progress bar (Components/Commission/Stepper.js). */
    public static final List<String> COMMISSION_STEPS = List.of("Theme", "Size & delivery", "Photo", "Frame", "Review", "Checkout");

    private void commissionForm(Map<String, Object> v) {
        List<Map<String, Object>> themes = new ArrayList<>();
        for (Map<String, String> t : COMMISSION_THEMES) {
            if (!com.artistikcity.commission.CommissionPricing.THEMES.contains(t.get("id"))) continue;
            Map<String, Object> m = new LinkedHashMap<>(t);
            m.put("is_" + t.get("id"), true);
            themes.add(m);
        }
        v.put("themes", themes);
        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < COMMISSION_STEPS.size(); i++) steps.add(Map.of("n", i + 1, "label", COMMISSION_STEPS.get(i), "current", i == 0));
        v.put("steps", steps);
    }

    /** "From" prices per delivery option, worked out with the wizard's own price list (CommissionPricing), never typed in. */
    private void commissionPricing(Map<String, Object> v) {
        Map<String, String> names = Map.of("DIGITAL_ONLY", "Digital file", "PHYSICAL_PRINT", "Canvas print", "ORIGINAL_PAINTING", "Original painting");
        Map<String, String> notes = Map.of("DIGITAL_ONLY", "High-resolution file, delivered by download link.",
                "PHYSICAL_PRINT", "Canvas print of your painting, shipped to you. Includes shipping.",
                "ORIGINAL_PAINTING", "The hand-painted original, signed and shipped to you. Includes shipping.");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String f : com.artistikcity.commission.CommissionPricing.FULFILLMENTS) {
            com.artistikcity.commission.CommissionPricing.Quote best = null;
            for (String theme : com.artistikcity.commission.CommissionPricing.THEMES) {
                com.artistikcity.commission.CommissionPricing.Quote q = commissionPricing.quote(new com.artistikcity.commission.CommissionRequest(theme, f,
                        com.artistikcity.commission.CommissionPricing.SIZES.get(0), "PORTRAIT", "NONE", false, null, null, null, null, null, null, null, null, null, null));
                if (best == null || q.totalInr().compareTo(best.totalInr()) < 0) best = q;
            }
            if (best == null) continue;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", names.getOrDefault(f, f));
            r.put("note", notes.getOrDefault(f, ""));
            r.put("from", inr(best.totalInr()));
            r.put("fromUsd", usd(best.totalUsd()));
            rows.add(r);
        }
        v.put("rows", rows);
        v.put("smallest", com.artistikcity.commission.CommissionPricing.SIZES.get(0).replace("x", " × ") + " in");
    }

    private String siteJsonLd(String title, String description, String url, String image, List<Map<String, Object>> sections, String baseUrl) {
        List<Object> graph = new ArrayList<>();
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("@type", "WebPage");
        page.put("name", title);
        page.put("description", description);
        page.put("url", url);
        if (image != null) page.put("primaryImageOfPage", image.startsWith("/") ? baseUrl + image : image);
        page.put("isPartOf", Map.of("@type", "WebSite", "name", "ArtistikCity", "url", baseUrl + "/"));
        graph.add(page);
        graph.add(Map.of("@type", "Organization", "name", "ArtistikCity", "url", baseUrl + "/", "logo", baseUrl + "/assets/images/logo.png"));
        for (Map<String, Object> s : sections) {
            if (!Boolean.TRUE.equals(s.get("is_faq")) || !(s.get("items") instanceof List<?> items) || items.isEmpty()) continue;
            List<Object> qs = new ArrayList<>();
            for (Object o : items) {
                Map<?, ?> it = (Map<?, ?>) o;
                qs.add(Map.of("@type", "Question", "name", String.valueOf(it.get("title")),
                        "acceptedAnswer", Map.of("@type", "Answer", "text", String.valueOf(it.get("text")))));
            }
            graph.add(Map.of("@type", "FAQPage", "mainEntity", qs));
            break;
        }
        Map<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        ld.put("@graph", graph);
        return json.encode(ld).replace("</", "<\\/").replace("<!--", "<\\!--");
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

    static String initial(String name) {
        String n = name == null ? "" : name.trim();
        return n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase();
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
