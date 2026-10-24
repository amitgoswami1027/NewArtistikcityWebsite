package com.artistikcity.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Runs first when the site sits behind Amazon CloudFront ({@code TRUST_CLOUDFRONT=true}).
 * <ul>
 *   <li>Every request must carry the secret {@code X-Origin-Verify} header that only CloudFront adds (origin custom
 *       header). Anything else gets 403, so the server cannot be used directly, bypassing CloudFront.</li>
 *   <li>The visitor's IP (used by the sign-in rate limits and the audit log) comes from {@code CloudFront-Viewer-Address},
 *       and https from {@code CloudFront-Forwarded-Proto}. Without this every visitor would look like a CloudFront
 *       server on plain http: they would share one rate limit and secure cookies would be off.</li>
 *   <li>Client-sent {@code X-Forwarded-Proto/Host} headers are replaced, so they cannot be spoofed.</li>
 * </ul>
 * {@code /healthz} is exempt so the deploy script can check the container directly.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CloudFrontFilter extends OncePerRequestFilter {

    static final String SECRET_HEADER = "X-Origin-Verify";

    private final boolean trust;
    private final byte[] secret;

    public CloudFrontFilter(@Value("${app.cloudfront.trust:false}") boolean trust,
                            @Value("${app.cloudfront.origin-secret:}") String secret) {
        this.trust = trust;
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (trust && (secret == null || secret.length() < 16)) {
            throw new IllegalStateException("TRUST_CLOUDFRONT=true needs ORIGIN_VERIFY_SECRET (at least 16 characters), "
                    + "the same value CloudFront sends in the X-Origin-Verify header");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!trust || "/healthz".equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        String given = request.getHeader(SECRET_HEADER);
        if (given == null || !MessageDigest.isEqual(secret, given.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(403, "Forbidden");
            return;
        }
        chain.doFilter(new Viewer(request), response);
    }

    /** The visitor's IP from CloudFront's headers ("ip:port", IPv4 or IPv6), or null. */
    static String viewerIp(HttpServletRequest request) {
        String v = request.getHeader("CloudFront-Viewer-Address");
        if (v != null && v.lastIndexOf(':') > 0) {
            return v.substring(0, v.lastIndexOf(':')).replace("[", "").replace("]", "").trim();
        }
        // fallback: CloudFront appends the viewer's address as the last X-Forwarded-For entry
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            return parts[parts.length - 1].trim();
        }
        return null;
    }

    static final class Viewer extends HttpServletRequestWrapper {
        private final String ip;
        private final boolean https;

        Viewer(HttpServletRequest r) {
            super(r);
            String found = viewerIp(r);
            this.ip = found == null || found.isEmpty() ? r.getRemoteAddr() : found;
            String proto = r.getHeader("CloudFront-Forwarded-Proto");
            this.https = proto == null || "https".equalsIgnoreCase(proto.trim());   // the distribution redirects http to https
        }

        @Override
        public String getRemoteAddr() {
            return ip;
        }

        @Override
        public String getRemoteHost() {
            return ip;
        }

        @Override
        public boolean isSecure() {
            return https;
        }

        @Override
        public String getScheme() {
            return https ? "https" : "http";
        }

        @Override
        public int getServerPort() {
            return https ? 443 : 80;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer sb = new StringBuffer(getScheme()).append("://").append(getServerName());
            return sb.append(getRequestURI());
        }

        @Override
        public String getHeader(String name) {
            String n = name.toLowerCase(Locale.ROOT);
            if (n.equals("x-forwarded-proto")) {
                return getScheme();
            }
            if (n.equals("x-forwarded-host") || n.equals("forwarded")) {
                return null;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String n = name.toLowerCase(Locale.ROOT);
            if (n.equals("x-forwarded-proto")) {
                return Collections.enumeration(List.of(getScheme()));
            }
            if (n.equals("x-forwarded-host") || n.equals("forwarded")) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }
    }
}
