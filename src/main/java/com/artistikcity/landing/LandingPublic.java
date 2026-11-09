package com.artistikcity.landing;

import com.artistikcity.http.Auth;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.view.Views;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Renders a published landing page at its own address /landing/{slug} (LandingCheckoutController maps it) and the admin
 * preview of a draft. A landing page never takes over an existing page of the site. Sets the anonymous visitor cookie (ac_lv) and keeps utm_* from the address for 30 days (ac_utm) so an order
 * placed later is credited to its campaign. Views by bots, staff and previews are not counted.
 */
@Component
public class LandingPublic {

    public static final String VISITOR = "ac_lv";
    public static final String UTM = "ac_utm";

    private final LandingPages pages;
    private final LandingView view;
    private final LandingAnalytics analytics;
    private final Views views;
    private final Auth auth;
    private final Json json;

    @org.springframework.beans.factory.annotation.Autowired
    private LandingAbTests abTests;

    public LandingPublic(LandingPages pages, LandingView view, LandingAnalytics analytics, Views views, Auth auth, Json json) {
        this.pages = pages;
        this.view = view;
        this.analytics = analytics;
        this.views = views;
        this.auth = auth;
        this.json = json;
    }

    public Row published(String slug) {
        return pages.published(slug);
    }

    public ResponseEntity<String> render(HttpServletRequest request, Row page) {
        if (request.getHeader("X-Inertia") != null) {
            // a link inside the React site: Inertia does a full page load of this server-rendered page
            String url = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            return ResponseEntity.status(409).header("X-Inertia-Location", url).build();
        }
        HttpHeaders h = new HttpHeaders();
        String visitor = cookie(request, VISITOR);
        if (visitor == null || !visitor.matches("[a-f0-9-]{36}")) {
            visitor = UUID.randomUUID().toString();
            h.add(HttpHeaders.SET_COOKIE, ResponseCookie.from(VISITOR, visitor).path("/").httpOnly(true).sameSite("Lax")
                    .secure(request.isSecure()).maxAge(Duration.ofDays(365)).build().toString());
        }
        Map<String, Object> config = json.decodeMap(page.str("published_config"));
        // A/B test: the variant this visitor is assigned to (sticky by the visitor cookie); ?ab=A|B lets staff look at either
        LandingAbTests.Arm arm = abTests.arm(page.lng("id"), LandingAnalytics.visitorHash(visitor));
        String force = request.getParameter("ab");
        if (arm != null && force != null && auth.admin(request) != null && force.matches("[AB]")) arm = new LandingAbTests.Arm(arm.test(), force);
        Map<String, Object> variant = abTests.variantConfig(arm);
        if (variant != null) config = variant;
        Map<String, Object> model = view.page(page, config == null ? Map.of() : config, false, Inertia.baseUrl(request));
        String body = views.render("landing/page", withShared(request, model));
        if (arm != null) h.add("X-AB-Variant", arm.variant());
        LandingAnalytics.Utm utm = LandingAnalytics.Utm.of(request.getParameter("utm_source"), request.getParameter("utm_medium"), request.getParameter("utm_campaign"));
        if (utm.source() != null || utm.medium() != null || utm.campaign() != null) {
            String v = URLEncoder.encode(nz(utm.source()) + "|" + nz(utm.medium()) + "|" + nz(utm.campaign()), StandardCharsets.UTF_8);
            h.add(HttpHeaders.SET_COOKIE, ResponseCookie.from(UTM, v).path("/").httpOnly(true).sameSite("Lax").secure(request.isSecure())
                    .maxAge(Duration.ofDays(30)).build().toString());
        } else {
            utm = utm(request);
        }
        if (!isBot(request) && auth.admin(request) == null) {
            analytics.record(page.lng("id"), "VIEW", visitor, null, null, null, utm, LandingAnalytics.device(request.getHeader("User-Agent")));
        }
        h.setCacheControl("no-cache");
        return ResponseEntity.ok().headers(h).contentType(new MediaType("text", "html", StandardCharsets.UTF_8)).body(body);
    }

    /** Admin preview of a draft (or of one version): same renderer, noindex, nothing recorded. */
    public ResponseEntity<String> preview(HttpServletRequest request, Row page, Map<String, Object> config) {
        Map<String, Object> model = view.page(page, config, true, Inertia.baseUrl(request));
        String body = views.render("landing/page", withShared(request, model));
        return ResponseEntity.ok().header("X-Robots-Tag", "noindex").contentType(new MediaType("text", "html", StandardCharsets.UTF_8)).body(body);
    }

    private Map<String, Object> withShared(HttpServletRequest request, Map<String, Object> model) {
        Map<String, Object> ctx = views.shared(request);
        ctx.putAll(model);
        return ctx;
    }

    public static String cookie(HttpServletRequest request, String name) {
        Cookie[] cs = request.getCookies();
        if (cs == null) return null;
        for (Cookie c : cs) if (name.equals(c.getName())) return c.getValue();
        return null;
    }

    public static LandingAnalytics.Utm utm(HttpServletRequest request) {
        String raw = cookie(request, UTM);
        if (raw == null) return LandingAnalytics.Utm.none();
        String[] p = URLDecoder.decode(raw, StandardCharsets.UTF_8).split("\\|", -1);
        return LandingAnalytics.Utm.of(p.length > 0 ? p[0] : null, p.length > 1 ? p[1] : null, p.length > 2 ? p[2] : null);
    }

    static boolean isBot(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua == null || ua.isBlank() || ua.toLowerCase().matches(".*(bot|crawl|spider|slurp|lighthouse|headless|preview|facebookexternalhit|whatsapp).*");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
