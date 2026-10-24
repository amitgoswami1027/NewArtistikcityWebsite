package com.artistikcity.inertia;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Csrf;
import com.artistikcity.http.Flash;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.view.Views;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Server side adapter for Inertia.js (replaces inertiajs/inertia-laravel).
 *
 * <ul>
 *   <li>First visit: returns the root HTML page ("app" or "user" layout) with the page object in
 *       {@code <div id="app" data-page="...">} plus the Ziggy route list.</li>
 *   <li>Inertia visits (header {@code X-Inertia: true}): returns the page object as JSON.</li>
 *   <li>Partial reloads ({@code X-Inertia-Partial-Data}) and {@code Inertia::location()} are supported.</li>
 * </ul>
 * Shared props are the same as HandleInertiaRequests::share(): {@code auth.user}, {@code flash.message}
 * and {@code errors}.
 */
@Component
public class Inertia {

    public static final String ROOT_APP = "inertia/app";
    public static final String ROOT_USER = "inertia/user";
    /** Request attribute with page-specific SEO values ({title, description, image}) for the root template. */
    public static final String SEO_ATTRIBUTE = "ac.seo";

    private final com.artistikcity.admin.SiteSettings site;
    private final Json json;
    private final com.artistikcity.web.user.WishlistService wishlist;
    private final Auth auth;
    private final Routes routes;
    private final Views views;
    private final String version;
    private final String ziggyRoutesJson;
    private final String ziggyScript;

    public Inertia(Json json, Auth auth, Routes routes, Views views, Environment env,
                   com.artistikcity.web.user.WishlistService wishlist, com.artistikcity.admin.SiteSettings site) {
        this.site = site;
        this.json = json;
        this.wishlist = wishlist;
        this.auth = auth;
        this.routes = routes;
        this.views = views;
        this.version = assetVersion(env.getProperty("app.asset-version", ""));
        Map<String, Object> list = new LinkedHashMap<>();
        routes.all().forEach((name, r) -> list.put(name, Row.of("uri", r.uri(), "methods", r.methods())));
        this.ziggyRoutesJson = json.encode(list);
        try (InputStream in = Inertia.class.getResourceAsStream("/ziggy/route.js")) {
            this.ziggyScript = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new IllegalStateException("Missing /ziggy/route.js", e);
        }
    }

    /** Inertia::render() with the default root view (resources/views/app.blade.php). */
    public ResponseEntity<String> render(HttpServletRequest request, String component, Map<String, ?> props) {
        return render(request, ROOT_APP, component, props);
    }

    /** Inertia::setRootView('user') + Inertia::render() - the student area layout. */
    public ResponseEntity<String> renderUser(HttpServletRequest request, String component, Map<String, ?> props) {
        return render(request, ROOT_USER, component, props);
    }

    public ResponseEntity<String> render(HttpServletRequest request, String rootView, String component, Map<String, ?> props) {
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("errors", new LinkedHashMap<>(Flash.errors(request)));
        Row user = auth.user(request);
        all.put("auth", Row.of("user", user));
        if (user != null) {
            // counts for the "My studio" header menu (Your orders / Your wish list)
            long uid = user.lng("id");
            Supplier<Object> nav = () -> wishlist.nav(uid);
            all.put("nav", nav);
        }
        Supplier<Object> flashMessage = () -> Flash.get(request, "message");
        // contact details and social links managed in Admin > Site settings (footer, contact page)
        Supplier<Object> siteInfo = site::publicInfo;
        all.put("site", siteInfo);
        all.put("flash", Row.of("message", flashMessage));
        if (props != null) {
            all.putAll(props);
        }

        // partial reloads
        String only = request.getHeader("X-Inertia-Partial-Data");
        if (only != null && component.equals(request.getHeader("X-Inertia-Partial-Component"))) {
            Set<String> keep = new HashSet<>(Arrays.asList(only.split(",")));
            all.keySet().retainAll(keep);
        }

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("component", component);
        page.put("props", resolve(all));
        page.put("url", currentUrl(request));
        page.put("version", version);

        if (isInertia(request)) {
            String clientVersion = request.getHeader("X-Inertia-Version");
            if ("GET".equals(request.getMethod()) && clientVersion != null && !clientVersion.equals(version)) {
                // the page in this tab was built from older assets: make Inertia do one full reload
                return ResponseEntity.status(HttpStatus.CONFLICT).header("X-Inertia-Location", currentUrl(request)).body("");
            }
            return ResponseEntity.ok()
                    .header("X-Inertia", "true")
                    .header(HttpHeaders.VARY, "Accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.encode(page));
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("page", json.encode(page));
        model.put("ziggy", ziggy(request));
        model.put("csrf", Csrf.token(request));
        model.put("assetVersion", version);
        Map<String, Object> seo = site.seo(baseUrl(request));
        // a controller can set page-specific values (e.g. a blog post) with request attribute SEO_ATTRIBUTE
        if (request.getAttribute(SEO_ATTRIBUTE) instanceof Map<?, ?> custom) {
            custom.forEach((k, v) -> {
                if (v != null && !String.valueOf(v).isBlank()) {
                    seo.put(String.valueOf(k), v);
                }
            });
        }
        model.put("seo", seo);
        return views.page(request, rootView, model);
    }

    /** Inertia::location(): full page visit to an external/non-Inertia URL. */
    public ResponseEntity<String> location(HttpServletRequest request, String url) {
        if (isInertia(request)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).header("X-Inertia-Location", url).body("");
        }
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url).body("");
    }

    /**
     * Asset version: a short hash of the compiled bundle and theme, so every new build gets a new value. It is used
     * as {@code ?v=} on the script/style URLs (browsers fetch the new files instead of an old cached copy) and as the
     * Inertia version (open tabs reload once after a deploy). APP_ASSET_VERSION / app.asset-version overrides it.
     */
    static String assetVersion(String configured) {
        if (configured != null && !configured.isBlank() && !"1".equals(configured.trim())) {
            return configured.trim();
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            for (String path : new String[]{"static/js/app.js", "static/css/ac-theme.css"}) {
                try (java.io.InputStream in = Inertia.class.getClassLoader().getResourceAsStream(path)) {
                    if (in != null) {
                        md.update(in.readAllBytes());
                    }
                }
            }
            return java.util.HexFormat.of().formatHex(md.digest()).substring(0, 12);
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    public String assetVersion() {
        return version;
    }

    public static boolean isInertia(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader("X-Inertia"));
    }

    /** The @routes Blade directive: Ziggy config + route() function. */
    public String ziggy(HttpServletRequest request) {
        String base = baseUrl(request);
        int port = request.getServerPort();
        String config = "{\"url\":" + json.encode(base) + ",\"port\":" + (port == 80 || port == 443 ? "null" : port)
                + ",\"defaults\":{},\"routes\":" + ziggyRoutesJson + "}";
        return "<script type=\"text/javascript\">\n    const Ziggy = " + config.replace("</", "<\\/") + ";\n\n    "
                + ziggyScript + "\n</script>";
    }

    public static String baseUrl(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto") != null ? request.getHeader("X-Forwarded-Proto") : request.getScheme();
        String host = request.getHeader("X-Forwarded-Host") != null ? request.getHeader("X-Forwarded-Host") : request.getHeader("Host");
        if (host == null) {
            host = request.getServerName() + ":" + request.getServerPort();
        }
        return scheme + "://" + host;
    }

    private static String currentUrl(HttpServletRequest request) {
        String q = request.getQueryString();
        return request.getRequestURI() + (q == null || q.isEmpty() ? "" : "?" + q);
    }

    /** Resolves lazy (Supplier) props recursively, like closures in Laravel's share(). */
    @SuppressWarnings("unchecked")
    private static Object resolve(Object value) {
        if (value instanceof Supplier<?> s) {
            return resolve(s.get());
        }
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), resolve(v)));
            return out;
        }
        if (value instanceof java.util.List<?> l) {
            java.util.List<Object> out = new java.util.ArrayList<>(l.size());
            for (Object o : l) {
                out.add(resolve(o));
            }
            return out;
        }
        return value;
    }

    public Routes routes() {
        return routes;
    }
}
