package com.artistikcity.http;

import com.artistikcity.inertia.Inertia;
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

    public GuardInterceptors(Auth auth) {
        this.auth = auth;
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
                redirect(request, response, HOME);
                return false;
            }
        };
    }

    /** AdminMiddleware - admin area requires the "admin" guard. */
    public HandlerInterceptor admin() {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if (!(handler instanceof HandlerMethod) || auth.admin(request) != null) {
                    return true;
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
