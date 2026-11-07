package com.artistikcity.landing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What landing pages are made of, in code so new templates, sections and payment methods are added in one place:
 * <ul>
 *   <li>{@link #TEMPLATES}: starting points (section order, hero layout and design tokens). A page keeps its own copy of
 *       the sections once created, so changing a template never changes existing pages.</li>
 *   <li>{@link #SECTIONS}: the section library with the layouts each section offers.</li>
 *   <li>{@link #METHODS} and {@link #PROVIDERS}: the payment capability model. A method can only be switched on for a page
 *       when at least one provider that is switched on for the page supports it; the checkout then shows it only when that
 *       provider is also connected (keys set, or test mode).</li>
 * </ul>
 */
public final class LandingCatalog {

    private LandingCatalog() {
    }

    // --------------------------------------------------------------------------------------------------- sections

    public record SectionType(String key, String label, String description, List<String> layouts, boolean single) {
    }

    public static final List<SectionType> SECTIONS = List.of(
            new SectionType("hero", "Hero", "Title, tagline, artwork, price and the Enroll button. Always first.", List.of("split", "immersive", "editorial", "video", "collage"), true),
            new SectionType("intro", "Course introduction", "What the course teaches, who it is for and what makes it different.", List.of("columns", "stacked"), true),
            new SectionType("outcomes", "Learning outcomes", "Cards with what students will be able to do.", List.of("cards", "list"), true),
            new SectionType("curriculum", "Curriculum", "Modules and lessons from the course, expandable.", List.of("accordion"), true),
            new SectionType("artist", "Meet the artist", "The instructor's profile, story, specialisations and links. Several artists allowed.", List.of("portrait", "wide"), true),
            new SectionType("gallery", "Artwork gallery", "Masonry gallery with full-screen view, captions and an optional before/after.", List.of("masonry", "strip"), false),
            new SectionType("benefits", "Course benefits", "Structured learning, demonstrations, resources, certificate, access.", List.of("grid", "list"), true),
            new SectionType("video", "Video preview", "A course trailer or lesson preview (YouTube, Vimeo or an uploaded file).", List.of("wide", "split"), false),
            new SectionType("testimonials", "Student reviews", "Approved course reviews and approved site testimonials only.", List.of("cards", "quote"), true),
            new SectionType("pricing", "Pricing & enrolment", "Price, offer, inclusions, access, refund policy and payment options.", List.of("card", "split"), true),
            new SectionType("faq", "FAQ", "Questions and answers, expandable.", List.of("accordion"), true),
            new SectionType("related", "Related courses", "Other published courses, by medium.", List.of("cards"), true),
            new SectionType("final_cta", "Final call to action", "A closing enrolment band.", List.of("band", "artwork"), true),
            new SectionType("custom", "Custom content", "Free text with headings, lists and links (cleaned).", List.of("narrow", "wide"), false),
            new SectionType("cards", "Feature cards", "Cards with a title, text, optional image and link: services, reasons, who it's for, what you need.", List.of("grid", "list", "tiles"), false),
            new SectionType("steps", "Steps / journey", "Numbered steps: how the platform works, how a commission is made.", List.of("timeline", "row"), false),
            new SectionType("courses", "Course list", "Published courses and workshops from the catalogue, each linking to its own (landing) page.", List.of("cards"), false),
            new SectionType("artists", "Artists", "Artist profiles from Admin › Instructors, each linking to the artist's page.", List.of("cards"), false),
            new SectionType("commission_form", "Commission request", "Step 1 of the commission wizard (choose the style), then on to the existing steps 2-4, preview and checkout.", List.of("cards"), true),
            new SectionType("commission_pricing", "Commission prices", "Starting prices per option, calculated from the commission price list (never typed in).", List.of("table"), true));

    /** Sections that need a course: not allowed on information pages (kind SITE). */
    public static final Set<String> COURSE_ONLY = Set.of("curriculum", "pricing", "related");

    public static SectionType section(String key) {
        return SECTIONS.stream().filter(s -> s.key().equals(key)).findFirst().orElse(null);
    }

    // -------------------------------------------------------------------------------------------------- templates

    /** kind: COURSE (sells a course) or SITE (an information page without a course, price or checkout). */
    public record Template(String key, String name, String description, String hero, Map<String, String> design, List<String> sections, String kind) {
        public Template(String key, String name, String description, String hero, Map<String, String> design, List<String> sections) {
            this(key, name, description, hero, design, sections, "COURSE");
        }
    }

    private static Map<String, String> design(String palette, String accent, String ink, String surface, String font, String radius) {
        return design(palette, accent, ink, surface, font, radius, "none");
    }

    private static Map<String, String> design(String palette, String accent, String ink, String surface, String font, String radius, String texture) {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("palette", palette);
        d.put("accent", accent);
        d.put("ink", ink);
        d.put("surface", surface);
        d.put("font", font);
        d.put("radius", radius);
        d.put("texture", texture);
        return d;
    }

    public static final List<Template> TEMPLATES = List.of(
            new Template("artist-showcase", "Artist Showcase", "Leads with the artwork and the artist's story; gallery high on the page.", "split",
                    design("burgundy", "#9C0D48", "#14213D", "#FBF8F4", "jakarta", "soft"),
                    List.of("hero", "intro", "gallery", "artist", "outcomes", "curriculum", "testimonials", "pricing", "faq", "final_cta")),
            new Template("premium-masterclass", "Premium Masterclass", "Dark, immersive hero and a confident, editorial rhythm.", "immersive",
                    design("ink", "#C8A15A", "#111318", "#F6F3EE", "serif", "sharp"),
                    List.of("hero", "intro", "outcomes", "artist", "curriculum", "video", "testimonials", "pricing", "faq", "final_cta")),
            new Template("creative-workshop", "Creative Workshop", "Warm and friendly for short workshops: benefits and FAQ first.", "split",
                    design("terracotta", "#B4532A", "#2B2118", "#FFF8F1", "jakarta", "soft"),
                    List.of("hero", "benefits", "outcomes", "gallery", "curriculum", "testimonials", "pricing", "faq", "final_cta")),
            new Template("minimal-editorial", "Minimal Editorial", "Quiet, text-led layout with generous white space.", "editorial",
                    design("ink", "#9C0D48", "#1C1D1F", "#FFFFFF", "serif", "sharp"),
                    List.of("hero", "intro", "curriculum", "artist", "testimonials", "pricing", "faq", "final_cta")),
            new Template("video-first", "Video-First Course", "The trailer is the hero; lessons with previews follow.", "video",
                    design("burgundy", "#9C0D48", "#14213D", "#F7F5F2", "jakarta", "soft"),
                    List.of("hero", "outcomes", "curriculum", "artist", "testimonials", "pricing", "faq", "final_cta")),
            new Template("conversion-sales", "Conversion-Focused Sales Page", "Price and proof early, repeated calls to action, sticky enrol bar.", "split",
                    design("sage", "#2F6B4F", "#14213D", "#F5F8F4", "jakarta", "soft"),
                    List.of("hero", "testimonials", "outcomes", "pricing", "curriculum", "artist", "benefits", "faq", "final_cta")),
            new Template("watercolour-wash", "Watercolour Wash", "Soft paper tones and washes of colour; the artwork leads, the curriculum and price follow.", "split",
                    design("custom", "#3E6E8E", "#2A2522", "#FBF6EE", "serif", "soft", "wash"),
                    List.of("hero", "intro", "outcomes", "curriculum", "artist", "gallery", "cards", "benefits", "testimonials", "pricing", "faq", "final_cta")),
            new Template("platform-showcase", "Platform Showcase", "For information pages: an editorial collage of real artwork, services, the journey, courses and artists.", "collage",
                    design("custom", "#9C0D48", "#16130F", "#F7F2EA", "serif", "sharp", "grain"),
                    List.of("hero", "intro", "cards", "steps", "courses", "artists", "gallery", "testimonials", "faq", "final_cta"), "SITE"),
            new Template("commission-studio", "Commission Studio", "For information pages: personal and warm, with the commission request built in.", "split",
                    design("custom", "#8C3B4A", "#1F1A24", "#FAF6F2", "serif", "soft", "grain"),
                    List.of("hero", "intro", "cards", "gallery", "steps", "commission_form", "commission_pricing", "artists", "testimonials", "faq", "final_cta"), "SITE"));

    public static Template template(String key) {
        return TEMPLATES.stream().filter(t -> t.key().equals(key)).findFirst().orElse(null);
    }

    // ----------------------------------------------------------------------------------------------------- payments

    /** A payment method customers can pick, grouped for the admin screen. */
    public record Method(String key, String label, String group, String hint) {
    }

    public static final List<Method> METHODS = List.of(
            new Method("upi", "UPI", "india", "Any UPI app, by UPI ID"),
            new Method("upi_qr", "UPI QR", "india", "Scan a QR code (desktop)"),
            new Method("upi_intent", "UPI app (intent)", "india", "Opens the UPI app on the phone"),
            new Method("gpay", "Google Pay", "india", "Shown when the provider offers it"),
            new Method("phonepe", "PhonePe", "india", "Shown when the provider offers it"),
            new Method("paytm", "Paytm", "india", "Shown when the provider offers it"),
            new Method("credit_card", "Credit card", "india", "Visa, Mastercard, RuPay, Amex"),
            new Method("debit_card", "Debit card", "india", "Visa, Mastercard, RuPay"),
            new Method("visa", "Visa", "networks", "Card network"),
            new Method("mastercard", "Mastercard", "networks", "Card network"),
            new Method("rupay", "RuPay", "networks", "Card network"),
            new Method("netbanking", "Net banking", "india", "All major Indian banks"),
            new Method("wallets", "Wallets", "india", "Paytm, PhonePe, Amazon Pay, Mobikwik ..."),
            new Method("emi", "EMI", "india", "Card EMI, needs the provider's EMI to be activated"),
            new Method("nocost_emi", "No-cost EMI", "india", "Needs a no-cost EMI offer at the provider"),
            new Method("intl_cards", "International cards", "international", "Cards issued outside India (provider must allow them)"),
            new Method("paypal", "PayPal", "international", "PayPal balance and cards via PayPal, charged in USD"));

    public static final Set<String> METHOD_KEYS = Set.copyOf(METHODS.stream().map(Method::key).toList());

    /** Card networks only narrow the card methods; they are not a way to pay on their own. */
    public static final Set<String> NETWORKS = Set.of("visa", "mastercard", "rupay");

    /** A payment provider and what it can do. {@code integrated} = this app has a working adapter for it. */
    public record Provider(String key, String label, String currency, boolean integrated, Set<String> methods, String note) {
    }

    public static final List<Provider> PROVIDERS = List.of(
            new Provider("razorpay", "Razorpay", "INR", true,
                    Set.of("upi", "upi_qr", "upi_intent", "gpay", "phonepe", "paytm", "credit_card", "debit_card", "visa", "mastercard", "rupay",
                            "netbanking", "wallets", "emi", "nocost_emi", "intl_cards"),
                    "Charged in INR. International cards and EMI need to be activated on the Razorpay account."),
            new Provider("paypal", "PayPal", "USD", true, Set.of("paypal", "intl_cards"),
                    "Charged in USD at the page's USD price. For buyers outside India."),
            new Provider("cashfree", "Cashfree Payments", "INR", false,
                    Set.of("upi", "upi_qr", "upi_intent", "gpay", "phonepe", "paytm", "credit_card", "debit_card", "visa", "mastercard", "rupay",
                            "netbanking", "wallets", "emi", "nocost_emi", "intl_cards"),
                    "Not connected yet: the adapter is planned. It can be listed but not switched on."),
            new Provider("payu", "PayU", "INR", false,
                    Set.of("upi", "upi_qr", "upi_intent", "credit_card", "debit_card", "visa", "mastercard", "rupay", "netbanking", "wallets", "emi", "nocost_emi"),
                    "Not connected yet: the adapter is planned. It can be listed but not switched on."));

    public static Provider provider(String key) {
        return PROVIDERS.stream().filter(p -> p.key().equals(key)).findFirst().orElse(null);
    }

    /** Global defaults for new pages and for pages that use "global settings". */
    public static final List<String> DEFAULT_METHODS = List.of("upi", "upi_qr", "upi_intent", "gpay", "phonepe", "paytm", "credit_card", "debit_card",
            "visa", "mastercard", "rupay", "netbanking", "intl_cards", "paypal");
}
