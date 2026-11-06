package com.artistikcity.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * "Not open yet" switch for a site that is online but not launched. When {@code SITE_PREVIEW_PASSWORD} is set, every
 * page asks for that password first (one form, remembered in a cookie for 30 days); without it the site behaves as usual.
 * While the lock is on, every response also says {@code X-Robots-Tag: noindex} and robots.txt disallows everything, so
 * search engines don't list the site.
 *
 * <p>Left open on purpose: {@code /healthz} (deploy checks), the payment webhooks (Razorpay / PayPal call them from
 * their servers) and static files (styles, scripts, fonts, images of the site itself). Uploaded files under /storage
 * are served by CloudFront straight from S3 and are not covered: they only have unguessable names.</p>
 *
 * <p>Turn it off by removing the setting (Parameter Store) and re-running the deploy.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class PreviewLockFilter extends OncePerRequestFilter {

    static final String COOKIE = "ac_preview";
    static final String FORM_PATH = "/__preview";
    private static final int COOKIE_DAYS = 30;
    private static final List<String> OPEN_PREFIXES = List.of("/assets/", "/css/", "/js/", "/fonts/", "/images/");
    private static final List<String> OPEN_PATHS = List.of("/healthz", "/favicon.ico", "/favicon.png", "/apple-touch-icon.png",
            "/api/v1/courses/razorpay-webhook", "/api/v1/marketplace/webhook", "/api/commissions/finalize-webhook");

    private final String password;
    private final String token;

    public PreviewLockFilter(@Value("${app.preview.password:}") String password) {
        this.password = password == null ? "" : password.trim();
        this.token = this.password.isEmpty() ? "" : hmac(this.password, "artistikcity-preview-v1");
    }

    public boolean enabled() {
        return !password.isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled()) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
        String path = request.getRequestURI().substring(request.getContextPath().length());

        if ("/robots.txt".equals(path)) {
            byte[] body = "User-agent: *\nDisallow: /\n".getBytes(StandardCharsets.UTF_8);
            response.setContentType("text/plain;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
            return;
        }
        if (OPEN_PATHS.contains(path) || OPEN_PREFIXES.stream().anyMatch(path::startsWith)) {
            chain.doFilter(request, response);
            return;
        }
        if (FORM_PATH.equals(path) && "POST".equals(request.getMethod())) {
            unlock(request, response);
            return;
        }
        if (hasPass(request)) {
            chain.doFilter(request, response);
            return;
        }
        String accept = String.valueOf(request.getHeader("Accept"));
        response.setHeader("Cache-Control", "no-store");
        if (path.startsWith("/api/") || accept.contains("application/json")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"PREVIEW_LOCKED\",\"message\":\"This site is not open yet.\"}");
            return;
        }
        String next = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        page(response, 401, safeNext(next), false);
    }

    private void unlock(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String given = request.getParameter("password");
        String next = safeNext(request.getParameter("next"));
        response.setHeader("Cache-Control", "no-store");
        if (given == null || !MessageDigest.isEqual(hmac(given.trim(), "artistikcity-preview-v1").getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            try {
                Thread.sleep(600);   // slows down guessing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            page(response, 401, next, true);
            return;
        }
        Cookie c = new Cookie(COOKIE, token);
        c.setHttpOnly(true);
        c.setSecure(request.isSecure());
        c.setPath("/");
        c.setMaxAge(COOKIE_DAYS * 24 * 3600);
        c.setAttribute("SameSite", "Lax");
        response.addCookie(c);
        response.setStatus(303);
        response.setHeader("Location", next);
    }

    boolean hasPass(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName()) && c.getValue() != null
                    && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), c.getValue().getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    /** Only paths on this site (no //host or scheme), so the form can't be used to send people elsewhere. */
    static String safeNext(String next) {
        if (next == null || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\") || next.startsWith(FORM_PATH)) return "/";
        return next.replaceAll("[\\r\\n]", "");
    }

    private static void page(HttpServletResponse response, int status, String next, boolean wrong) throws IOException {
        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        String html = """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex, nofollow">
                <title>ArtistikCity · Coming soon</title>
                <style>
                  *{box-sizing:border-box} body{margin:0;min-height:100vh;display:grid;place-items:center;padding:16px;
                  font-family:'Plus Jakarta Sans','Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#f7f7fb;color:#0e1a3a}
                  main{width:100%%;max-width:400px;background:#fff;border:1px solid #e3e6ef;border-radius:20px;padding:32px 28px;text-align:center}
                  img{height:40px;width:auto} h1{font-size:22px;margin:20px 0 6px;letter-spacing:-.02em} p{margin:0 0 20px;color:#5b6478;font-size:15px;line-height:1.5}
                  input{width:100%%;height:48px;border:1px solid #cfd4e0;border-radius:12px;padding:0 14px;font:inherit;font-size:16px}
                  input:focus{outline:3px solid rgba(229,21,107,.25);border-color:#e5156b}
                  button{margin-top:12px;width:100%%;height:48px;border:0;border-radius:999px;background:#e5156b;color:#fff;font:inherit;font-weight:800;font-size:16px;cursor:pointer}
                  .err{color:#b80f55;font-size:14px;margin:10px 0 0}
                </style></head><body><main>
                <img src="/assets/images/logo.png" alt="ArtistikCity">
                <h1>We're getting ready</h1>
                <p>ArtistikCity opens soon. If you're helping us test, enter the preview password.</p>
                <form method="post" action="%s">
                  <input type="hidden" name="next" value="%s">
                  <input type="password" name="password" placeholder="Preview password" aria-label="Preview password" autocomplete="current-password" required autofocus>
                  %s
                  <button type="submit">Enter</button>
                </form></main></body></html>
                """.formatted(FORM_PATH, esc(next), wrong ? "<p class=\"err\" role=\"alert\">That password isn't right.</p>" : "");
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String hmac(String key, String msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(msg.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
