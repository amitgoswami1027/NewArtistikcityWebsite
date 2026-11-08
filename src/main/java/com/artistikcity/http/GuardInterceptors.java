package com.artistikcity.http;

import com.artistikcity.inertia.Inertia;
import com.artistikcity.security.SecurityWatch;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Route middleware: "auth" (students), "guest" and "admin". Registered with URL patterns in
 * {@link com.artistikcity.config.WebConfig}.
 */
@Component
public class GuardInterceptors {

    public static final String HOME = "/dashboard";

    private final Auth auth;
    private final SecurityWatch watch;

    public GuardInterceptors(Auth auth, SecurityWatch watch) {
        this.auth = auth;
        this.watch = watch;
    }

    /** "auth" middleware - redirect to /login when not logged in. */
    public HandlerInterceptor authenticated() {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if (!(handler instanceof HandlerMethod) || auth.check(request)) {
                    return true;
                }
                if (wantsJson(request)) {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Unauthenticated.\"}");
                    return false;
                }
                redirect(request, response, "/login");
                return false;
            }
        };
    }

    /** "guest" middleware - logged in users are sent to their dashboard. */
    public HandlerInterceptor guest() {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if (!(handler instanceof HandlerMethod) || !auth.check(request)) {
                    return true;
                }
                // already signed in: go where the link wanted to take them (e.g. "Go to my course" in an e-mail), else the studio
                String to = request.getParameter("return_to");
                boolean safe = to != null && to.startsWith("/") && !to.startsWith("//") && !to.contains("\\") && !to.contains("://")
                        && !to.startsWith("/login") && !to.startsWith("/register") && !to.startsWith("/join") && !to.startsWith("/auth/") && to.length() < 500;
                redirect(request, response, safe ? to : HOME);
                return false;
            }
        };
    }

    /**
     * AdminMiddleware - admin area requires the "admin" guard. Pages redirect to the sign-in page; the JSON API answers
     * 401 (never a redirect, so scripts and the console's fetch calls see the refusal), and a student session calling it
     * is signed out ({@link SecurityWatch}).
     */
    public HandlerInterceptor admin() {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if (!(handler instanceof HandlerMethod) || auth.admin(request) != null) {
                    return true;
                }
                if (SecurityWatch.isAdminApi(request) || wantsJson(request)) {
                    watch.studentCalledAdminApi(request);
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\":\"Your admin session has ended. Please sign in again.\",\"reauth\":true}");
                    return false;
                }
                Flash.put(request, "error", "Please Login to access Admin area");
                redirect(request, response, "/admin/login");
                return false;
            }
        };
    }

    private static boolean wantsJson(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return !Inertia.isInertia(request) && accept != null && accept.contains("application/json");
    }

    private static void redirect(HttpServletRequest request, HttpServletResponse response, String url) {
        if (Inertia.isInertia(request)) {
            // Full page visit so that the right root layout is loaded
            response.setStatus(409);
            response.setHeader("X-Inertia-Location", url);
            return;
        }
        response.setStatus(302);
        response.setHeader("Location", url);
    }
}
