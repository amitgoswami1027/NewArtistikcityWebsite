package com.artistikcity.web;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Buying always starts with an account: "Enroll", "Buy now" and the checkouts send a guest to log in or join with
 * {@code ?return_to=} the checkout. This names what they were buying, so the sign-in page can say "Sign in to finish
 * buying X" and the person knows nothing is lost.
 */
@Component
public class PurchaseIntent {

    private static final Pattern MARKET_CHECKOUT = Pattern.compile("^/marketplace/([A-Za-z0-9-]{1,200})/checkout");

    private final Db db;

    public PurchaseIntent(Db db) {
        this.db = db;
    }

    /** Where a guest is sent before a checkout: the log-in page, coming back to {@code returnTo}. */
    public static String signIn(String returnTo) {
        return "/login?return_to=" + java.net.URLEncoder.encode(returnTo, StandardCharsets.UTF_8) + "&intent=buy";
    }

    /** {kind, title, image} for the remembered return path, or null when it isn't a checkout. */
    public Map<String, Object> describe(HttpServletRequest request) {
        // only for this visit (a log-in page opened later on its own says nothing about a purchase)
        if (request.getParameter("return_to") == null) return null;
        HttpSession s = request.getSession(false);
        Object to = s == null ? null : s.getAttribute("auth.return_to");
        if (!(to instanceof String path)) return null;
        try {
            URI u = URI.create(path);
            String p = u.getPath() == null ? "" : u.getPath();
            if (p.equals("/checkout")) {
                String slug = param(u.getRawQuery(), "course");
                Row c = slug == null ? null : db.first("select id, title, course_type_id from courses where slug = ?", slug);
                if (c == null) return of("course", "Your course", null);
                return of(c.lng("course_type_id", 1) == 2 ? "workshop" : "course", c.str("title"), null);
            }
            if (p.startsWith("/purchase/")) {
                Object draft = s.getAttribute("purchase.draft");
                Object id = draft instanceof Map<?, ?> m ? m.get("paintingId") : null;
                Row art = id == null ? null : db.first("select p.title, " + com.artistikcity.marketplace.MarketplaceRepository.PRIMARY_IMAGE_SQL + " as image_url from paintings p where p.id = ?", id);
                return of("artwork", art == null ? "Your artwork" : art.str("title"), art == null ? null : art.str("image_url"));
            }
            Matcher mk = MARKET_CHECKOUT.matcher(p);
            if (mk.find()) {
                Row art = db.first("select p.title, " + com.artistikcity.marketplace.MarketplaceRepository.PRIMARY_IMAGE_SQL + " as image_url from paintings p where p.slug = ?", mk.group(1));
                return of("artwork", art == null ? "Your artwork" : art.str("title"), art == null ? null : art.str("image_url"));
            }
            if (p.startsWith("/commission/checkout")) return of("commission", "Custom portrait commission", null);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    private static Map<String, Object> of(String kind, String title, String image) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("title", title);
        m.put("image", image);
        return m;
    }

    private static String param(String rawQuery, String key) {
        if (rawQuery == null) return null;
        for (String pair : rawQuery.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0 && pair.substring(0, i).equals(key)) return URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
        }
        return null;
    }
}
