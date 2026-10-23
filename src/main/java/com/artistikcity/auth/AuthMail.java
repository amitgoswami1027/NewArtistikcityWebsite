package com.artistikcity.auth;

import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.Mailer;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** E-mails with single-use links: magic sign-in, e-mail verification, password reset. */
@Service
public class AuthMail {

    public static final Duration MAGIC_LINK_TTL = Duration.ofMinutes(15);
    public static final Duration VERIFY_TTL = Duration.ofHours(48);
    public static final Duration RESET_TTL = Duration.ofMinutes(60);

    private final EmailTokenService tokens;
    private final Mailer mailer;
    private final Environment env;

    public AuthMail(EmailTokenService tokens, Mailer mailer, Environment env) {
        this.tokens = tokens;
        this.mailer = mailer;
        this.env = env;
    }

    /** Public base URL for links in e-mails: APP_URL when set (recommended behind proxies), else the request. */
    public String baseUrl(HttpServletRequest request) {
        String app = env.getProperty("app.url", "");
        return app.isBlank() ? Inertia.baseUrl(request) : app.replaceAll("/+$", "");
    }

    /** Sends a magic sign-in link. {@code userId} may be null (the link then creates the account). */
    public boolean sendMagicLink(HttpServletRequest request, String email, String name, Long userId, String returnTo) {
        String token = tokens.issue(request, EmailTokenService.MAGIC_LINK, email, userId, MAGIC_LINK_TTL,
                returnTo == null ? null : Map.of("return_to", returnTo));
        String url = baseUrl(request) + "/auth/magic-link?token=" + enc(token);
        return mailer.send(email, "Your ArtistikCity sign-in link", "emails/magic-link",
                Row.of("name", name == null ? "there" : name, "forEmail", email, "url", url,
                        "expire", MAGIC_LINK_TTL.toMinutes(), "requestedFrom", describe(request), "baseUrl", baseUrl(request)));
    }

    public boolean sendVerification(HttpServletRequest request, long userId, String email, String name) {
        String token = tokens.issue(request, EmailTokenService.VERIFY_EMAIL, email, userId, VERIFY_TTL, null);
        String url = baseUrl(request) + "/verify-email/confirm?token=" + enc(token);
        return mailer.send(email, "Confirm your e-mail address", "emails/verify-email",
                Row.of("name", name, "forEmail", email, "url", url, "expire", VERIFY_TTL.toHours(), "baseUrl", baseUrl(request)));
    }

    public boolean sendPasswordReset(HttpServletRequest request, long userId, String email, String name) {
        String token = tokens.issue(request, EmailTokenService.PASSWORD_RESET, email, userId, RESET_TTL, null);
        String url = baseUrl(request) + "/reset-password/" + enc(token) + "?email=" + enc(email);
        return mailer.send(email, "Reset Password Notification", "emails/reset-password",
                Row.of("name", name, "url", url, "expire", RESET_TTL.toMinutes(), "baseUrl", baseUrl(request)));
    }

    /** "Chrome on Windows (1.2.3.4)" - shown in the magic-link mail so people can spot requests they didn't make. */
    static String describe(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        String browser = ua == null ? "a browser" : ua.contains("Edg/") ? "Edge" : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Firefox/") ? "Firefox" : ua.contains("Safari/") ? "Safari" : "a browser";
        String os = ua == null ? "" : ua.contains("Windows") ? " on Windows" : ua.contains("Android") ? " on Android"
                : ua.contains("iPhone") || ua.contains("iPad") ? " on iOS" : ua.contains("Mac OS") ? " on macOS" : ua.contains("Linux") ? " on Linux" : "";
        return browser + os + " (" + AuthEvents.ip(request) + ")";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
