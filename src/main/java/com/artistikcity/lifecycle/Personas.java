package com.artistikcity.lifecycle;

import com.artistikcity.support.Row;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-side personas (admins.admin_type) and what each can do in the admin console.
 * <ul>
 *   <li>admin      - Super admin: everything, including people, revenue and settings.</li>
 *   <li>teacher    - Instructor: reviews submissions and sees students of their own courses.</li>
 *   <li>moderator  - Gallery moderator: submissions, marketplace curation (review queue, holds, fulfilment),
 *       gallery and testimonials.</li>
 * </ul>
 * Marketplace capabilities:
 * <ul>
 *   <li>marketplace          - open the marketplace console (instructors see only their own listings and sales)</li>
 *   <li>marketplace.edit     - create and edit any listing, choose the artist, publish directly</li>
 *   <li>marketplace.own      - list your own originals; they go to the review queue before going live</li>
 *   <li>marketplace.publish  - approve / reject listings, unpublish, feature, release a stuck hold</li>
 *   <li>marketplace.orders   - fulfil orders (packed, shipped, delivered) and record refunds</li>
 * </ul>
 * Landing Page Management capabilities (one section of the console, see landing/):
 * <ul>
 *   <li>landing           - open the section: overview, pages, analytics</li>
 *   <li>landing.content   - edit content, sections, design, media, SEO and marketing tags (instructors: pages of their own courses)</li>
 *   <li>landing.publish   - create, duplicate, publish, schedule, unpublish, archive, roll back, delete</li>
 *   <li>landing.pricing   - prices, offers, early-bird prices and coupons</li>
 *   <li>landing.payments  - payment providers, payment methods and checkout settings per page</li>
 *   <li>landing.orders    - orders and transactions from landing pages</li>
 *   <li>landing.refunds   - refund a landing page order through the gateway</li>
 * </ul>
 * Besides the persona, a super admin can grant landing capabilities to one person (admins.extra_capabilities, People & roles):
 * e.g. a content manager, marketing manager or finance manager. Only {@link #GRANTABLE} can be granted this way.
 * Students are the `users` table and use the student studio (/dashboard).
 */
public final class Personas {

    private Personas() {
    }

    public static final List<String> ALL = List.of("admin", "teacher", "moderator");

    public static final List<String> GRANTABLE = List.of("landing", "landing.content", "landing.publish", "landing.pricing", "landing.payments",
            "landing.orders", "landing.refunds");

    /** Ready-made grants offered in People & roles. */
    public static final Map<String, List<String>> LANDING_ROLES = new LinkedHashMap<>(Map.of(
            "content", List.of("landing", "landing.content"),
            "marketing", List.of("landing", "landing.content", "landing.pricing"),
            "finance", List.of("landing", "landing.orders", "landing.refunds"),
            "publisher", List.of("landing", "landing.content", "landing.publish")));

    private static final Map<String, List<String>> CAPS = Map.of(
            "admin", List.of("overview", "revenue", "submissions", "commissions", "marketplace", "marketplace.edit", "marketplace.publish",
                    "marketplace.orders", "people", "people.manage", "testimonials", "gallery", "catalog", "settings", "setup", "security", "security.export", "moderation", "moderation.ban",
                    "landing", "landing.content", "landing.publish", "landing.pricing", "landing.payments", "landing.orders", "landing.refunds"),
            "teacher", List.of("overview", "submissions", "marketplace", "marketplace.own", "people", "catalog", "landing", "landing.content"),
            "moderator", List.of("overview", "submissions", "marketplace", "marketplace.publish", "marketplace.orders", "testimonials", "gallery", "commissions", "moderation",
                    "landing", "landing.content"));

    public static String of(Row admin) {
        String t = admin == null ? "" : admin.str("admin_type", "");
        return ALL.contains(t) ? t : "teacher";
    }

    public static List<String> capabilities(Row admin) {
        List<String> base = CAPS.get(of(admin));
        List<String> extra = granted(admin);
        if (extra.isEmpty()) {
            return base;
        }
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>(base);
        all.addAll(extra);
        return List.copyOf(all);
    }

    /** What a persona gives on its own, without per-person grants. */
    public static List<String> personaCapabilities(String persona) {
        return CAPS.getOrDefault(ALL.contains(persona) ? persona : "teacher", List.of());
    }

    /** Landing capabilities granted to this person on top of the persona (only GRANTABLE ones count). */
    public static List<String> granted(Row admin) {
        String raw = admin == null ? null : admin.str("extra_capabilities", null);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new java.util.ArrayList<>();
        for (String c : raw.split(",")) {
            String t = c.trim();
            if (GRANTABLE.contains(t) && !out.contains(t)) out.add(t);
        }
        return out;
    }

    public static boolean can(Row admin, String capability) {
        return admin != null && capabilities(admin).contains(capability);
    }

    public static String label(String persona) {
        return switch (persona) {
            case "admin" -> "Super admin";
            case "moderator" -> "Gallery moderator";
            default -> "Instructor";
        };
    }

    /** Admin summary shared with every console page. */
    public static Map<String, Object> view(Row admin) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", admin.get("id"));
        m.put("name", admin.str("name", "Admin"));
        m.put("email", admin.str("email", ""));
        m.put("persona", of(admin));
        m.put("personaLabel", label(of(admin)));
        m.put("photo", admin.get("profile_photo"));
        m.put("photoUrl", admin.str("profile_photo", "").isBlank() ? null
                : "/storage/uploads/teachers/" + admin.get("id") + "/" + admin.str("profile_photo"));
        m.put("capabilities", capabilities(admin));
        return m;
    }
}
