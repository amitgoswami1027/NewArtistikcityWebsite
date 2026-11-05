package com.artistikcity.http;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * The "web" middleware group: starts the session, ages flash data, publishes the XSRF-TOKEN cookie
 * (read by axios/Inertia) and verifies the CSRF token on state-changing requests.
 * Routes under /api are stateless like Laravel's "api" group and are not CSRF protected.
 */
@Component
public class SessionInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        if (request.getRequestURI().startsWith("/api/")) {
            return true;
        }
        Flash.age(request);
        String token = Csrf.token(request);
        Cookie cookie = new Cookie("XSRF-TOKEN", token);
        cookie.setPath("/");
        response.addCookie(cookie);

        if (!SAFE.contains(request.getMethod()) && !Csrf.matches(request)) {
            response.setStatus(419);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<!DOCTYPE html><html><head><title>Page Expired</title></head>"
                    + "<body style=\"font-family:sans-serif;text-align:center;padding:60px\"><h1>419 | Page Expired</h1>"
                    + "<p>Your session has expired. Please go back, refresh the page and try again.</p></body></html>");
            return false;
        }
        return true;
    }
}
