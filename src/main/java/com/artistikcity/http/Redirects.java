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

    /** URL of the previous page (Referer), falling back to the home page. */
    public static String previous(HttpServletRequest request) {
        String ref = request.getHeader("Referer");
        return ref == null || ref.isBlank() ? "/" : ref;
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
