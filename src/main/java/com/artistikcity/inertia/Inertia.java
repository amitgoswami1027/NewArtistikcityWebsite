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

    private final Json json;
    private final Auth auth;
    private final Routes routes;
    private final Views views;
    private final String version;
    private final String ziggyRoutesJson;
    private final String ziggyScript;

    public Inertia(Json json, Auth auth, Routes routes, Views views, Environment env) {
        this.json = json;
        this.auth = auth;
        this.routes = routes;
        this.views = views;
        this.version = env.getProperty("app.asset-version", "1");
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
        Supplier<Object> flashMessage = () -> Flash.get(request, "message");
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
        return views.page(request, rootView, model);
    }

    /** Inertia::location(): full page visit to an external/non-Inertia URL. */
    public ResponseEntity<String> location(HttpServletRequest request, String url) {
        if (isInertia(request)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).header("X-Inertia-Location", url).body("");
        }
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url).body("");
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
