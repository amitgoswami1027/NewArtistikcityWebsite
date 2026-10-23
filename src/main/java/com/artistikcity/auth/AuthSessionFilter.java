package com.artistikcity.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Attaches the student login (if any) to every dynamic request, before the controllers run, so the
 * session token can be rotated / the refresh token exchanged while response headers are still writable.
 * Static files are skipped. Also adds baseline security headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthSessionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthSessionFilter.class);

    /** request attribute holding the {@link SessionService.AuthContext} (absent = anonymous) */
    public static final String CONTEXT = AuthSessionFilter.class.getName() + ".context";
    /** request attribute holding the response, so code that logs users in can set cookies */
    public static final String RESPONSE = AuthSessionFilter.class.getName() + ".response";

    private final SessionService sessions;

    public AuthSessionFilter(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return p.startsWith("/assets/") || p.startsWith("/js/") || p.startsWith("/css/") || p.startsWith("/storage/")
                || p.startsWith("/fonts/") || p.startsWith("/images/") || p.startsWith("/user/assets/")
                || p.endsWith(".ico") || p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".svg") || p.endsWith(".webmanifest");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.setAttribute(RESPONSE, response);
        try {
            SessionService.AuthContext ctx = sessions.resolve(request, response);
            if (ctx != null) {
                request.setAttribute(CONTEXT, ctx);
            }
        } catch (RuntimeException e) {
            // never let a session-store problem take the whole site down: continue as anonymous
            log.error("Could not resolve the login session", e);
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        if (request.getRequestURI().startsWith("/auth/") || request.getRequestURI().startsWith("/login")
                || request.getRequestURI().startsWith("/join") || request.getRequestURI().startsWith("/register")) {
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }

    public static SessionService.AuthContext context(HttpServletRequest request) {
        Object c = request.getAttribute(CONTEXT);
        return c instanceof SessionService.AuthContext a ? a : null;
    }

    public static HttpServletResponse response(HttpServletRequest request) {
        Object r = request.getAttribute(RESPONSE);
        return r instanceof HttpServletResponse h ? h : null;
    }
}
