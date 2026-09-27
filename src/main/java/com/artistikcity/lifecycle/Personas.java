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
 * Students are the `users` table and use the student studio (/dashboard).
 */
public final class Personas {

    private Personas() {
    }

    public static final List<String> ALL = List.of("admin", "teacher", "moderator");

    private static final Map<String, List<String>> CAPS = Map.of(
            "admin", List.of("overview", "revenue", "submissions", "commissions", "marketplace", "marketplace.edit", "marketplace.publish",
                    "marketplace.orders", "people", "people.manage", "testimonials", "gallery", "catalog", "settings"),
            "teacher", List.of("overview", "submissions", "marketplace", "marketplace.own", "people", "catalog"),
            "moderator", List.of("overview", "submissions", "marketplace", "marketplace.publish", "marketplace.orders", "testimonials", "gallery", "commissions"));

    public static String of(Row admin) {
        String t = admin == null ? "" : admin.str("admin_type", "");
        return ALL.contains(t) ? t : "teacher";
    }

    public static List<String> capabilities(Row admin) {
        return CAPS.get(of(admin));
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
        m.put("capabilities", capabilities(admin));
        return m;
    }
}
