package com.artistikcity.http;

import com.artistikcity.support.Str;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** CSRF token handling (Laravel's VerifyCsrfToken). */
public final class Csrf {

    private static final String KEY = "_token";

    private Csrf() {
    }

    public static String token(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Object t = session.getAttribute(KEY);
        if (t instanceof String s) {
            return s;
        }
        String token = Str.random(40);
        session.setAttribute(KEY, token);
        return token;
    }

    /** Accepts the token from the _token field, X-CSRF-TOKEN or X-XSRF-TOKEN (axios / Inertia) headers. */
    public static boolean matches(HttpServletRequest request) {
        String expected = token(request);
        String given = request.getParameter("_token");
        if (given == null) {
            given = request.getHeader("X-CSRF-TOKEN");
        }
        if (given == null) {
            given = request.getHeader("X-XSRF-TOKEN");
            if (given != null) {
                given = java.net.URLDecoder.decode(given, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return expected.equals(given);
    }
}
