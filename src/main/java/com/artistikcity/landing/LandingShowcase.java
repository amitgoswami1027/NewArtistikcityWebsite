package com.artistikcity.landing;

import com.artistikcity.support.Db;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates the three showcase landing pages once, through the landing module itself (LandingPages: create, save draft,
 * publish as version N), so they are ordinary pages afterwards: editable, versioned and unpublishable in
 * Admin › Landing Page Management.
 * <ul>
 *   <li>How it works (information page at /landing/how-it-works)</li>
 *   <li>Watercolour Landscapes for Beginners (course page at /landing/{the course's slug})</li>
 *   <li>Commission artwork (information page at /landing/commission)</li>
 * </ul>
 * They have their own addresses: /how-it-works, /course/... and /commission/step-1 stay the site's original pages.
 * Runs once per database (settings: landing_pages / showcase_v1, which also records what happened to each page; a
 * Watercolour page skipped for want of the course is tried again on later starts) and never overwrites someone's work:
 * an address that already has a landing page is left alone; an existing Watercolour page is
 * only updated when it has no unpublished edits, by publishing the new content as a new version (the old version stays
 * one click away under Versions). Nothing is deleted. A page is reported as published only when publishing succeeded;
 * otherwise it stays a draft with the reason. Every text states what the platform really does: prices, durations,
 * reviews, artists and courses are read from the database when the page is shown, never written into the content.
 * Switch off with app.landing.showcase=false.
 */
@Component
public class LandingShowcase implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LandingShowcase.class);
    static final String FLAG = "showcase_v1";
    static final String COURSE_SLUG = "watercolour-landscapes-for-beginners";
    private static final String NOTE = "Showcase content (installed automatically)";

    private final Db db;
    private final Json json;
    private final LandingPages pages;
    private final LandingCommerce commerce;
    private final LandingPayments payments;
    private final Storage storage;
    private final Environment env;

    public LandingShowcase(Db db, Json json, LandingPages pages, LandingCommerce commerce, LandingPayments payments, Storage storage, Environment env) {
        this.db = db;
        this.json = json;
        this.pages = pages;
        this.commerce = commerce;
        this.payments = payments;
        this.storage = storage;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"true".equalsIgnoreCase(env.getProperty("app.landing.showcase", "true"))) {
            return;
        }
        try {
            Row done = db.first("select id, meta_value from settings where meta_tag = 'landing_pages' and meta_key = ? order by id limit 1", FLAG);
            if (done != null) {
                retryWatercolour(done);
                return;
            }
            if (db.count("select count(*) from information_schema.columns where table_schema = database() and table_name = 'landing_pages' and column_name = 'kind'") == 0) {
                log.warn("Landing showcase pages not installed: apply database/migrations/2026-11-09_landing_site_pages.sql first.");
                return;
            }
            install();
        } catch (RuntimeException e) {
            log.warn("Landing showcase pages could not be installed (will try again on the next start): {}", e.getMessage());
        }
    }

    /** Installs the pages and records the outcome; returns it (page key -> {id, status, reason}). */
    public Map<String, Object> install() {
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("how-it-works", sitePage("how-it-works", "How it works", "platform-showcase", howItWorks()));
        outcome.put("watercolour", watercolour());
        outcome.put("commission", sitePage("commission", "Commission artwork", "commission-studio", commission()));
        LocalDateTime now = LocalDateTime.now();
        db.insert("settings", Row.of("meta_tag", "landing_pages", "meta_key", FLAG, "meta_value", json.encode(outcome), "created_at", now, "updated_at", now));
        log.info("Landing showcase pages: {}", outcome);
        return outcome;
    }

    /** An earlier start found no Watercolour course: look again (the course may have been added or have another address). */
    @SuppressWarnings("unchecked")
    private void retryWatercolour(Row done) {
        Map<String, Object> outcome = json.decodeMap(done.str("meta_value", ""));
        if (outcome == null) return;
        Object w = outcome.get("watercolour");
        if (!(w instanceof Map<?, ?> m) || !"skipped".equals(String.valueOf(m.get("status"))) || !String.valueOf(m.get("detail")).startsWith("There is no")) return;     // also the first release's wording
        Map<String, Object> again = watercolour();
        if (String.valueOf(again.get("detail")).startsWith(NO_COURSE)) return;
        outcome.put("watercolour", again);
        db.updateById("settings", done.lng("id"), Row.of("meta_value", json.encode(outcome), "updated_at", LocalDateTime.now()));
        log.info("Landing showcase pages (Watercolour, second try): {}", again);
    }

    // ------------------------------------------------------------------------------------------------- pages

    private static final String NO_COURSE = "There is no Watercolour Landscapes course";

    /** An information page (no course) at /landing/{slug}; left alone when a page with that address already exists. */
    private Map<String, Object> sitePage(String slug, String name, String template, Map<String, Object> config) {
        Row existing = db.first("select id from landing_pages where slug = ?", slug);
        if (existing != null) {
            return result(existing.lng("id"), "skipped", "A landing page with the address " + LandingPages.path(slug) + " already exists; it was left as it is.");
        }
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("kind", "SITE");
        in.put("name", name);
        in.put("slug", slug);
        in.put("description", "Showcase information page (no course). Its own address; the site's original pages are unchanged.");
        long id = pages.create(in, template, null);
        return saveAndPublish(id, config);
    }

    /** The Watercolour course: by its usual address, else by its title. */
    private Row findCourse() {
        Row c = db.first("select id, slug, course_type_id, course_includes, status from courses where slug = ?", COURSE_SLUG);
        if (c == null) {
            c = db.first("select id, slug, course_type_id, course_includes, status from courses where lower(title) like '%watercolo%r%landscape%'"
                    + " order by (status = 1) desc, id limit 1");
        }
        return c;
    }

    private Map<String, Object> watercolour() {
        Row course = findCourse();
        if (course == null) {
            return result(null, "skipped", NO_COURSE + " (address " + COURSE_SLUG + " or a title with \"watercolour\" and \"landscape\").");
        }
        String slug = course.str("slug");
        Row page = db.first("select * from landing_pages where slug = ?", slug);
        Map<String, Object> config;
        if (page == null) {
            Map<String, Object> in = new LinkedHashMap<>();
            in.put("kind", "COURSE");
            in.put("courseId", String.valueOf(course.get("id")));
            in.put("name", "Watercolour Landscapes for Beginners");
            in.put("slug", slug);
            in.put("description", "Course landing page (Watercolour Wash) at its own address; the regular course page is unchanged.");
            long id = pages.create(in, "watercolour-wash", null);
            config = watercolourConfig(course, null);
            return saveAndPublish(id, config);
        }
        if (LandingPages.isSite(page) || !String.valueOf(page.get("course_id")).equals(String.valueOf(course.get("id")))) {
            return result(page.lng("id"), "skipped", "The address is used by a landing page for something else; it was left as it is.");
        }
        if ("ARCHIVED".equals(page.str("status"))) {
            return result(page.lng("id"), "skipped", "The existing page is archived; it was left as it is.");
        }
        if (hasUnpublishedEdits(page)) {
            return result(page.lng("id"), "skipped", "The existing page has unpublished edits, so its draft was not replaced.");
        }
        config = watercolourConfig(course, pages.draft(page));
        LocalDateTime now = LocalDateTime.now();
        db.updateById("landing_pages", page.lng("id"), Row.of("template_key", "watercolour-wash", "updated_at", now));
        return saveAndPublish(page.lng("id"), config);
    }

    private boolean hasUnpublishedEdits(Row page) {
        if (page.get("published_version_id") == null) {
            // never published: an untouched draft (no one saved it since it was created) can be replaced
            return page.get("draft_saved_by") != null && "DRAFT".equals(page.str("status")) && !page.str("created_at").equals(page.str("draft_saved_at"));
        }
        Row v = db.first("select config from landing_page_versions where id = ?", page.get("published_version_id"));
        return v == null || !String.valueOf(json.decode(v.str("config"))).equals(String.valueOf(json.decode(page.str("draft_config"))));
    }

    private Map<String, Object> saveAndPublish(long id, Map<String, Object> config) {
        LandingConfig.Result r = pages.saveDraft(id, config, null);
        if (!r.ok()) {
            return result(id, "draft", "The content didn't pass the editor's checks: " + r.errors());
        }
        Row page = pages.find(id);
        if (!LandingPages.isSite(page)) {
            LandingPricing.Quote q = commerce.quote(page);
            if (!q.free() && payments.choices(payments.forPage(id), q.totalUsd() != null).isEmpty()) {
                return result(id, "draft", "Saved as a draft: no payment method is live for this page yet (Payment settings), so it was not published.");
            }
        }
        if (!"DRAFT".equals(page.str("status")) && !"PUBLISHED".equals(page.str("status"))) {
            return result(id, "draft", "Saved as a draft; the page is " + page.str("status").toLowerCase() + ", so it was not published.");
        }
        Map<String, String> errors = pages.publish(id, null, NOTE);
        if (!errors.isEmpty()) {
            return result(id, "draft", "Saved as a draft; publishing was refused: " + errors);
        }
        Row live = pages.find(id);
        return "PUBLISHED".equals(live.str("status")) ? result(id, "published", LandingPages.publicPath(live)) : result(id, "draft", "Publishing did not complete.");
    }

    private static Map<String, Object> result(Long id, String status, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("status", status);
        m.put("detail", reason);
        return m;
    }

    // ----------------------------------------------------------------------------------------------- content

    private Map<String, Object> base(String templateKey) {
        Map<String, Object> c = LandingConfig.initial(LandingCatalog.template(templateKey));
        c.put("sections", new ArrayList<>());
        return c;
    }

    @SuppressWarnings("unchecked")
    private static void add(Map<String, Object> config, String id, String type, String layout, String background, Map<String, Object> content) {
        Map<String, Object> s = LandingConfig.section(type, layout);
        s.put("id", id);
        s.put("background", background);
        Map<String, Object> c = (Map<String, Object>) s.get("content");
        c.putAll(content);
        ((List<Object>) config.get("sections")).add(s);
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static Map<String, Object> item(String title, String text) {
        return m("title", title, "text", text);
    }

    private static Map<String, Object> item(String title, String text, String link, String linkLabel) {
        return m("title", title, "text", text, "link", link, "linkLabel", linkLabel);
    }

    @SuppressWarnings("unchecked")
    private static void seo(Map<String, Object> config, String title, String description) {
        Map<String, Object> seo = (Map<String, Object>) config.get("seo");
        seo.put("title", title);
        seo.put("description", description);
        seo.put("ogTitle", title);
        seo.put("ogDescription", description);
    }

    @SuppressWarnings("unchecked")
    private static void cta(Map<String, Object> config, String primary, String primaryHref, String secondary, String secondaryHref, boolean video, boolean sticky) {
        Map<String, Object> cta = (Map<String, Object>) config.get("cta");
        cta.put("primary", primary);
        cta.put("primaryHref", primaryHref);
        cta.put("secondary", secondary);
        cta.put("secondaryHref", secondaryHref);
        cta.put("secondaryVideo", video);
        cta.put("sticky", sticky);
    }

    /** Images that are really there: marketplace artwork first (public listings), then course photos. */
    private List<String> artworks(int max, String prefer) {
        List<String> out = new ArrayList<>();
        String order = prefer == null ? "p.is_featured desc, p.id" : "(lower(concat(p.title, ' ', coalesce(i.alt_text, ''), ' ', p.medium)) like '%" + prefer + "%') desc, p.is_featured desc, p.id";
        for (Row r : db.select("select i.image_url from paintings p join painting_images i on i.painting_id = p.id and i.is_primary = 1"
                + " where p.stock_status in ('AVAILABLE', 'RESERVED', 'SOLD') order by " + order + " limit 20")) {
            String u = r.str("image_url", "");
            if (available(u) && !out.contains(u)) out.add(u);
            if (out.size() >= max) return out;
        }
        for (Row r : db.select("select p.course_id, p.photo_name from course_photos p join courses c on c.id = p.course_id where c.status = 1 order by p.id limit 20")) {
            String u = "/storage/uploads/courses/" + r.get("course_id") + "/" + r.str("photo_name");
            if (available(u) && !out.contains(u)) out.add(u);
            if (out.size() >= max) return out;
        }
        return out;
    }

    private String altFor(String url) {
        Row r = db.first("select i.alt_text, p.title, p.artist_name from painting_images i join paintings p on p.id = i.painting_id where i.image_url = ? limit 1", url);
        if (r == null) return "";
        return r.str("alt_text", "").isEmpty() ? r.str("title") + " by " + r.str("artist_name") : r.str("alt_text");
    }

    boolean available(String url) {
        if (url == null || url.isBlank()) return false;
        if (url.startsWith("https://")) return true;
        if (url.startsWith("/storage/")) {
            try {
                return storage.exists(url.substring("/storage/".length()));
            } catch (RuntimeException e) {
                return false;
            }
        }
        return url.startsWith("/assets/") && !url.contains("..") && LandingShowcase.class.getResource("/static" + url) != null;
    }

    private Map<String, Object> howItWorks() {
        Map<String, Object> c = base("platform-showcase");
        cta(c, "Explore Courses", "/courses?type=all", "Discover How It Works", "#how-steps", false, false);
        seo(c, "How ArtistikCity Works | Discover Art, Artists and Creative Learning",
                "Discover how ArtistikCity connects art lovers, aspiring artists, and creative enthusiasts with artists, courses, and personalized artistic experiences.");
        add(c, "hero", "hero", "collage", "tint", m("eyebrow", "How ArtistikCity works",
                "heading", "Discover, Learn and Create with ArtistikCity",
                "subheading", "Your creative journey starts here. Learn from talented artists, explore your creativity, and bring your artistic ideas to life.",
                "images", artworks(4, null), "language", "",
                "items", List.of(item("Live online classes with practising artists", ""), item("Certificates for approved work", ""), item("Original art in the marketplace", ""))));
        add(c, "what-is", "intro", "columns", "plain", m("kicker", "What is ArtistikCity",
                "heading", "One place to learn art, collect it and have it made for you",
                "body", "ArtistikCity brings art lovers and working artists together. Join live online courses and short workshops taught by practising artists, "
                        + "practise with lessons and tasks in your own studio, and earn a certificate when your final work is approved.\n\n"
                        + "Browse original artwork in the marketplace, ask an artist a question about a piece, or commission a hand-painted artwork from a photo you love.",
                "items", List.of()));
        add(c, "services", "cards", "grid", "tint", m("kicker", "What you can do here", "heading", "Everything you need to grow as an artist", "source", "custom",
                "items", List.of(
                        item("Learn from artists", "Live online sessions led by practising artists. Your schedule, lessons and tasks wait in My studio.", "/courses?type=all", "Browse courses"),
                        item("Art courses", "Multi-week courses with modules, lessons and a certificate when your final work is approved.", "/courses?type=course", "See courses"),
                        item("Workshops", "Short workshops to try a new medium or subject in just a few sessions.", "/courses?type=workshop", "See workshops"),
                        item("Commission artwork", "Send a photo you love and an ArtistikCity artist paints it by hand, in the style, size and frame you choose.", "/commission/step-1", "Start a commission"),
                        item("Connect with creative professionals", "Ask an artist a question or chat with them from any artwork in the marketplace, and visit each teaching artist's profile.", "/marketplace", "Visit the marketplace"))));
        add(c, "how-steps", "steps", "row", "plain", m("kicker", "Your journey", "heading", "From first look to first sale, in five steps",
                "items", List.of(
                        item("Discover", "Browse courses, workshops and original artwork. Each course shows its artist, curriculum and price."),
                        item("Join and enrol", "Create your account and enrol in the course or workshop that fits your level."),
                        item("Learn live", "Join live online sessions with your artist and work through the lessons and tasks in My studio."),
                        item("Get certified", "Submit your work. When your final work is approved, a verifiable certificate is added to your studio."),
                        item("Share and sell", "Approved work can be listed as a one-of-a-kind original in the marketplace, from your portfolio.", "/marketplace", "Explore the marketplace"))));
        add(c, "courses", "courses", "cards", "tint", m("kicker", "Courses and workshops", "heading", "Start with a course", "body",
                "Live courses and workshops open for enrolment now.", "limit", 6));
        add(c, "meet-artists", "artists", "cards", "plain", m("kicker", "Our artists", "heading", "Meet our artists",
                "body", "The practising artists who teach on ArtistikCity. Open a profile to see their story and their courses.", "limit", 6));
        add(c, "why", "cards", "list", "tint", m("kicker", "Why ArtistikCity", "heading", "Why learners choose ArtistikCity", "source", "custom",
                "items", List.of(
                        item("Taught live by working artists", "Every course is led by a practising artist you can watch, ask and learn from in class."),
                        item("Learn at your level", "Courses are labelled by level and age group, from first brushstrokes to masterclasses."),
                        item("Proof of your progress", "Certificates for approved final work and a portfolio of what you made."),
                        item("Pay the way you prefer", "UPI, cards and NetBanking in India; PayPal from other countries."),
                        item("Learning and art in one place", "Courses, workshops, commissions and a marketplace of originals, all with one account."))));
        add(c, "showcase", "gallery", "masonry", "plain", m("kicker", "Artwork showcase", "heading", "Original work from our artists", "source", "marketplace",
                "body", "A selection from the marketplace. Tap an artwork to see it larger."));
        add(c, "grow", "cards", "tiles", "plain", m("kicker", "How we help you grow", "heading", "Support at every stage", "source", "custom",
                "items", List.of(
                        item("Practise with structure", "Modules and lessons build one skill at a time, with tasks after each class."),
                        item("Feedback on your work", "Submit your artwork for review before it is approved."),
                        item("A portfolio that grows", "Approved work collects in your portfolio, ready to share."),
                        item("From student to seller", "List approved originals in the marketplace when you are ready.", "/marketplace", "See the marketplace"))));
        add(c, "voices", "testimonials", "cards", "tint", m("kicker", "In their words", "heading", "What our community says", "source", "testimonials", "ids", List.of()));
        add(c, "faq", "faq", "accordion", "plain", m("heading", "Questions, answered", "items", List.of(
                item("What is ArtistikCity?", "An online art school and marketplace: live courses and workshops taught by practising artists, hand-painted commissions from your photos, and original art for sale."),
                item("How do the live classes work?", "Each course runs as a series of live online sessions with your instructor. After you enrol, the schedule, lessons and tasks appear under My studio > My courses."),
                item("Who are the artists?", "Practising artists who teach on ArtistikCity. Each one has a profile page with their background and courses; open it from Meet our artists above."),
                item("Can I commission an artwork?", "Yes. Upload a photo, choose the style, size and frame, review everything and pay at checkout. A digital proof is e-mailed to you to approve or request changes before your piece is finished."),
                item("Which payment methods can I use?", "In India you can pay with UPI, cards and NetBanking. From other countries you can pay with PayPal."),
                item("Will I get a certificate?", "Yes. When you complete the lessons and your final work is approved, a verifiable certificate is added to My studio > Certificates & reports."),
                item("Can I sell my artwork on ArtistikCity?", "Approved student work can be listed as a one-of-a-kind original in the marketplace from your portfolio."),
                item("How do I get help?", "Visit the Help centre or send us a message from the Contact page."))));
        add(c, "start", "final_cta", "band", "tint", m("heading", "Your Creative Journey Starts Today",
                "body", "Learn from artists, find a piece you love, or have one painted for you.",
                "items", List.of(item("Explore Courses", "", "/courses?type=all", ""), item("Discover Artists", "", "#meet-artists", ""),
                        item("Commission Artwork", "", "/commission/step-1", ""))));
        return c;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> watercolourConfig(Row course, Map<String, Object> previous) {
        Map<String, Object> c = base("watercolour-wash");
        if (previous != null && previous.get("marketing") instanceof Map<?, ?> mk) {
            c.put("marketing", new LinkedHashMap<>((Map<String, Object>) mk));       // keep the page's own tracking ids and WhatsApp number
        }
        cta(c, "Enroll Now", "", "Watch Course Preview", "", true, true);
        seo(c, "Watercolour Landscapes for Beginners | ArtistikCity",
                "Learn watercolour landscape painting with ArtistikCity. Explore the course, discover its learning experience, and begin your creative journey.");
        // trust points only for what the course record says
        List<Object> trust = new ArrayList<>();
        if (course.lng("course_type_id", 1) != 2) trust.add(item("Live classes with the artist", ""));
        Map<String, Object> inc = json.decodeMap(course.str("course_includes", ""));
        if (inc != null && "yes".equalsIgnoreCase(String.valueOf(inc.get("certificate")))) trust.add(item("Certificate of completion", ""));
        if (inc != null && "yes".equalsIgnoreCase(String.valueOf(inc.get("study_material_access")))) trust.add(item("Study material included", ""));
        add(c, "hero", "hero", "split", "tint", m("eyebrow", "", "heading", "Master the Art of Watercolour Landscapes",
                "subheading", "Discover the joy of watercolour painting and learn to create beautiful landscapes, one brushstroke at a time.",
                "body", "", "image", "", "imageAlt", "", "video", "", "language", "", "items", trust));
        add(c, "about", "intro", "columns", "plain", m("kicker", "About the course", "heading", "Paint the landscapes you love", "body", "", "items", List.of()));
        add(c, "learn", "outcomes", "cards", "tint", m("kicker", "Learning outcomes", "heading", "What you will learn", "items", List.of()));
        add(c, "curriculum", "curriculum", "accordion", "plain", m("heading", "Course curriculum"));
        add(c, "artist", "artist", "portrait", "tint", m("heading", "Meet your artist", "body", "", "ids", List.of(), "items", List.of(), "socials", List.of(), "video", ""));
        add(c, "gallery", "gallery", "masonry", "plain", m("heading", "Artwork from the course", "body", "", "source", "course", "items", List.of()));
        add(c, "who", "cards", "list", "tint", m("kicker", "Who it's for", "heading", "Who should join", "source", "course_for", "items", List.of()));
        add(c, "included", "benefits", "grid", "plain", m("heading", "What's included", "items", List.of()));
        add(c, "reviews", "testimonials", "cards", "tint", m("heading", "What students say", "source", "reviews", "ids", List.of()));
        add(c, "enrol", "pricing", "split", "plain", m("heading", "Enrol in the course", "body", ""));
        add(c, "faq", "faq", "accordion", "tint", m("heading", "Questions, answered", "items", List.of()));
        add(c, "start", "final_cta", "artwork", "tint", m("heading", "Start your watercolour journey", "body", "Join the next batch and paint your first landscape with an artist beside you.", "image", "", "items", List.of()));
        return c;
    }

    private Map<String, Object> commission() {
        Map<String, Object> c = base("commission-studio");
        cta(c, "Start Your Art Commission", "#commission-start", "Explore Artwork", "/marketplace", false, false);
        seo(c, "Custom Art Commissions | Personalized Artwork | ArtistikCity",
                "Create something meaningful with personalized artwork from ArtistikCity. Explore custom art options and start your commission request.");
        List<String> portrait = artworks(1, "portrait");
        String heroImage = portrait.isEmpty() ? "" : portrait.get(0);
        add(c, "hero", "hero", "split", "tint", m("eyebrow", "Custom art commissions", "heading", "Turn Your Memories Into Timeless Art",
                "subheading", "Bring your ideas to life through personalized artwork, created with care by talented artists.",
                "image", heroImage, "imageAlt", altFor(heroImage), "language", "",
                "items", List.of(item("Painted by hand from your photo", ""), item("See a preview before you pay", ""), item("Approve a digital proof", ""))));
        add(c, "what", "intro", "columns", "plain", m("kicker", "What is a commission", "heading", "A painting made for you, from your photo",
                "body", "A commission is an original artwork made just for you. Send a photo you love, of a person, a pet or a place, and an ArtistikCity artist paints it by hand in the style you choose.\n\n"
                        + "You decide how it comes to you: a high-resolution digital file, a canvas print, or the hand-painted original, signed and shipped to you.",
                "items", List.of()));
        add(c, "why", "cards", "grid", "tint", m("kicker", "Why commission", "heading", "Why commission a piece", "source", "custom", "items", List.of(
                item("Made from your memories", "Turn a favourite photo into a piece to hang at home or give as a gift."),
                item("Hand-painted, not filtered", "A real artist paints your commission by hand from your photo."),
                item("Your style, size and frame", "Four styles, four sizes, three ways to receive it, and a choice of frames and an optional mat."),
                item("Approve before it's finished", "A digital proof is e-mailed to you to approve or request changes."))));
        add(c, "gallery", "gallery", "masonry", "plain", m("kicker", "Gallery", "heading", "Work by our artists", "source", "marketplace",
                "body", "Originals from the ArtistikCity marketplace. Tap an artwork to see it larger."));
        add(c, "process", "steps", "timeline", "tint", m("kicker", "The process", "heading", "How a commission works", "items", List.of(
                item("Choose a style", "Classic portraiture, scenic landscape, fluid watercolour or expressive charcoal."),
                item("Pick delivery and size", "Digital file, canvas print or the original painting, from 8 × 10 to 24 × 36 inches, portrait or landscape."),
                item("Upload your photo", "A clear JPG or PNG up to 15 MB. Preview the style on your own photo and add notes for the artist."),
                item("Frame and review", "Choose a frame and an optional mat for prints and originals, then check everything on the review page."),
                item("Checkout", "Sign in and pay by UPI, card or PayPal. Nothing is charged before this step."),
                item("Proof, painting and delivery", "A digital proof is e-mailed to you to approve or request changes. Then your piece is painted and delivered by download link or tracked shipping."))));
        add(c, "options", "cards", "grid", "plain", m("kicker", "Personalise it", "heading", "Make it yours", "source", "custom", "items", List.of(
                item("Style", "Classic portraiture for people and pets, scenic landscapes, fluid watercolour, or expressive charcoal."),
                item("Delivery", "Digital file by download link, canvas print, or the original painting, signed and shipped."),
                item("Size and orientation", "8 × 10, 12 × 16, 16 × 20 or 24 × 36 inches, portrait or landscape."),
                item("Notes for the artist", "Tell the artist what matters: up to 4,000 characters of special instructions."),
                item("Frame and mat", "Gallery canvas wrap, matte black, gallery white or warm walnut, with an optional 1-inch white mat."))));
        add(c, "request", "commission_form", "cards", "tint", m("kicker", "Step 1 of 6", "heading", "Choose the style of your painting",
                "body", "Pick the look you love. You can preview it on your own photo in step 3."));
        add(c, "prices", "commission_pricing", "table", "plain", m("kicker", "Pricing", "heading", "What a commission costs",
                "body", "Prices come from the same price list the order form uses, so what you see here is what you'll see in the steps."));
        add(c, "artists", "artists", "cards", "tint", m("kicker", "Our artists", "heading", "The artists behind the work", "limit", 6,
                "body", "Practising artists who teach and create on ArtistikCity."));
        add(c, "faq", "faq", "accordion", "plain", m("heading", "Questions about commissions", "items", List.of(
                item("How much does a commission cost?", "It depends on the style, size, delivery option and frame. Starting prices are listed above, and the exact total is shown at every step before you pay."),
                item("Do I pay a deposit?", "No. There is no separate deposit: you pay the full price once, at checkout, after you have reviewed your order."),
                item("What photo should I send?", "A clear, well-lit JPG or PNG up to 15 MB. The sharper the photo, the more detail the artist can paint."),
                item("Can I ask for changes?", "Yes. After you order, a digital proof is e-mailed to you to approve or request changes before the painting is finished."),
                item("How will I receive it?", "Digital files by download link; canvas prints and original paintings by tracked shipping."),
                item("Where can I follow my order?", "Each stage (order received, digital proof, painting in progress, delivered) shows on your order in My studio."),
                item("Do I need an account?", "You can plan your commission without one. You sign in at checkout, before you pay."))));
        add(c, "start", "final_cta", "band", "tint", m("heading", "Let's Create Something Meaningful Together",
                "body", "Choose a style, upload your photo and see it come together before you pay.",
                "items", List.of(item("Start Your Art Commission", "", "#commission-start", ""), item("Explore Artwork", "", "/marketplace", ""))));
        return c;
    }
}
