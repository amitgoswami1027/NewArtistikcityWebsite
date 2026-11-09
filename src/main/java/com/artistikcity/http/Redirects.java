package com.artistikcity.http;

import com.artistikcity.inertia.Inertia;
import com.artistikcity.inertia.Routes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Redirect responses (Laravel's redirect(), Redirect::route(), back()).
 * For Inertia requests using PUT/PATCH/DELETE a 303 is returned, as the Inertia middleware does,
 * so the browser follows up with a GET.
 */
@Component
public class Redirects {

    private final Routes routes;

    public Redirects(Routes routes) {
        this.routes = routes;
    }

    public ResponseEntity<String> to(HttpServletRequest request, String url) {
        HttpStatus status = HttpStatus.FOUND;
        if (Inertia.isInertia(request) && !"GET".equals(request.getMethod()) && !"POST".equals(request.getMethod())) {
            status = HttpStatus.SEE_OTHER;
        }
        return ResponseEntity.status(status).header(HttpHeaders.LOCATION, url).body("");
    }

    public ResponseEntity<String> route(HttpServletRequest request, String name, Object... params) {
        return to(request, routes.path(name, params));
    }

    public ResponseEntity<String> back(HttpServletRequest request) {
        return to(request, previous(request));
    }

    /** Forms posted to the address of their own page: without a Referer, go back to that page (not the home page). */
    private static final java.util.Set<String> SELF_FORMS = java.util.Set.of("/admin/login", "/login", "/register", "/join", "/forgot-password");

    /** URL of the previous page (Referer), falling back to the form's own page or the home page. */
    public static String previous(HttpServletRequest request) {
        String ref = request.getHeader("Referer");
        if (ref != null && !ref.isBlank()) return ref;
        String path = request.getRequestURI();
        return path != null && SELF_FORMS.contains(path) ? path : "/";
    }

    /** Flash a value then redirect to a named route (->route(...)->with(key, value)). */
    public ResponseEntity<String> routeWith(HttpServletRequest request, String key, Object value, String name, Object... params) {
        Flash.put(request, key, value);
        return route(request, name, params);
    }

    public ResponseEntity<String> backWith(HttpServletRequest request, String key, Object value) {
        Flash.put(request, key, value);
        return back(request);
    }

    public Routes routes() {
        return routes;
    }
}
