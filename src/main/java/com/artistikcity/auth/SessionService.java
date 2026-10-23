package com.artistikcity.auth;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import com.artistikcity.support.Values;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Server-side session store for student logins (table {@code auth_sessions}).
 *
 * <h2>Cookies</h2>
 * <ul>
 *   <li>{@code ac_session} - opaque 256-bit session token. Short lived (idle timeout, default 30 min) and
 *       <b>rotated</b> every 15 minutes of activity. The previous token stays valid for a short grace period so
 *       parallel requests during a rotation don't log the user out.</li>
 *   <li>{@code ac_refresh} - opaque 256-bit <b>single-use refresh token</b>. When the session token has expired
 *       (idle) or the browser was restarted, it is exchanged for a fresh session token + refresh token.
 *       Presenting an already-used refresh token again (after the grace period) means it was stolen and replayed:
 *       the whole login is revoked (refresh-token reuse detection, as in OAuth 2.0 Security BCP).</li>
 * </ul>
 * Both are {@code HttpOnly}, {@code SameSite=Lax}, {@code Path=/}, and {@code Secure} on HTTPS. Only
 * SHA-256 hashes of the tokens are stored. "Remember me" makes the refresh cookie persistent (30 days);
 * otherwise both cookies end with the browser session and the login is capped at 12 hours.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    public static final String SESSION_COOKIE = "ac_session";
    public static final String REFRESH_COOKIE = "ac_refresh";

    /** The authenticated login attached to a request. */
    public record AuthContext(long userId, long sessionId, String method) {
    }

    private final Db db;
    private final AuthEvents events;
    private final Duration idle;
    private final Duration rotateEvery;
    private final Duration grace;
    private final Duration rememberFor;
    private final Duration maxNoRemember;
    private final String cookieSecure;

    public SessionService(Db db, AuthEvents events, Environment env) {
        this.db = db;
        this.events = events;
        this.idle = Duration.ofMinutes(env.getProperty("app.auth.session-idle-minutes", Long.class, 30L));
        this.rotateEvery = Duration.ofMinutes(env.getProperty("app.auth.session-rotate-minutes", Long.class, 15L));
        this.grace = Duration.ofSeconds(env.getProperty("app.auth.rotation-grace-seconds", Long.class, 60L));
        this.rememberFor = Duration.ofDays(env.getProperty("app.auth.remember-days", Long.class, 30L));
        this.maxNoRemember = Duration.ofHours(env.getProperty("app.auth.session-max-hours", Long.class, 12L));
        this.cookieSecure = env.getProperty("app.auth.cookie-secure", "auto");
    }

    // ------------------------------------------------------------------ login / logout

    /** Starts a new login for {@code userId}: new session row, new token pair, cookies set on the response. */
    public AuthContext start(HttpServletRequest request, HttpServletResponse response, long userId, String method, boolean remember) {
        // a new login on this browser replaces whatever login it had before
        String oldToken = cookie(request, SESSION_COOKIE);
        if (oldToken != null) {
            db.update("update auth_sessions set revoked_at = ?, revoke_reason = 'replaced' where session_token_hash = ? and revoked_at is null",
                    LocalDateTime.now(), Tokens.sha256Hex(oldToken));
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plus(remember ? rememberFor : maxNoRemember);
        String sessionToken = Tokens.random(32);
        String refreshToken = Tokens.random(32);
        long sessionId = db.insertReturningId("auth_sessions", Row.of(
                "user_id", userId,
                "session_token_hash", Tokens.sha256Hex(sessionToken),
                "session_expires_at", min(now.plus(idle), expires),
                "rotated_at", now,
                "auth_method", method,
                "remember", remember,
                "ip_address", AuthEvents.ip(request),
                "user_agent", AuthEvents.userAgent(request),
                "created_at", now, // always the app clock, never the DB default (the DB may run in another time zone)
                "last_seen_at", now,
                "expires_at", expires));
        insertRefresh(sessionId, refreshToken, expires);
        db.update("update users set last_login_at = ? where id = ?", now, userId);
        writeCookies(request, response, sessionToken, refreshToken, remember ? expires : null);
        // new login = new HTTP session id too (CSRF token / flash data must not survive a fixation attack)
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        events.ok(request, userId, "login", method);
        return new AuthContext(userId, sessionId, method);
    }

    /** Ends the login attached to this request (if any) and clears the cookies. */
    public void end(HttpServletRequest request, HttpServletResponse response, String reason) {
        String token = cookie(request, SESSION_COOKIE);
        String refresh = cookie(request, REFRESH_COOKIE);
        LocalDateTime now = LocalDateTime.now();
        if (token != null) {
            db.update("update auth_sessions set revoked_at = ?, revoke_reason = ? where (session_token_hash = ? or previous_token_hash = ?) and revoked_at is null",
                    now, reason, Tokens.sha256Hex(token), Tokens.sha256Hex(token));
        }
        if (refresh != null) {
            Row rt = db.first("select session_id from auth_refresh_tokens where token_hash = ?", Tokens.sha256Hex(refresh));
            if (rt != null) {
                revoke(rt.lng("session_id"), reason);
            }
        }
        clearCookies(request, response);
    }

    public void revoke(long sessionId, String reason) {
        db.update("update auth_sessions set revoked_at = ?, revoke_reason = ? where id = ? and revoked_at is null",
                LocalDateTime.now(), reason, sessionId);
    }

    /** Signs the user out everywhere (optionally keeping the current login). */
    public int revokeAll(long userId, Long exceptSessionId, String reason) {
        if (exceptSessionId == null) {
            return db.update("update auth_sessions set revoked_at = ?, revoke_reason = ? where user_id = ? and revoked_at is null",
                    LocalDateTime.now(), reason, userId);
        }
        return db.update("update auth_sessions set revoked_at = ?, revoke_reason = ? where user_id = ? and id <> ? and revoked_at is null",
                LocalDateTime.now(), reason, userId, exceptSessionId);
    }

    /** Active logins of a user, newest first (for the "Login & security" page). */
    public List<Row> active(long userId) {
        return db.select("select id, auth_method, ip_address, user_agent, created_at, last_seen_at, remember from auth_sessions"
                + " where user_id = ? and revoked_at is null and expires_at > ? order by last_seen_at desc limit 50", userId, LocalDateTime.now());
    }

    // ------------------------------------------------------------------ per request

    /**
     * Resolves the login for this request from the cookies, rotating the session token or exchanging the
     * refresh token when due. Returns null for anonymous requests.
     */
    public AuthContext resolve(HttpServletRequest request, HttpServletResponse response) {
        String token = cookie(request, SESSION_COOKIE);
        String refresh = cookie(request, REFRESH_COOKIE);
        if (token == null && refresh == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (token != null) {
            String hash = Tokens.sha256Hex(token);
            Row s = db.first("select s.*, u.status as user_status from auth_sessions s join users u on u.id = s.user_id"
                    + " where s.session_token_hash = ?", hash);
            if (s != null && usable(s, now) && after(s.str("session_expires_at"), now)) {
                touch(request, response, s, token, now);
                return context(s);
            }
            if (s == null) {
                // the token was just rotated by a parallel request: accept it briefly, without rotating again
                Row p = db.first("select s.*, u.status as user_status from auth_sessions s join users u on u.id = s.user_id"
                        + " where s.previous_token_hash = ? and s.previous_valid_until > ?", hash, now);
                if (p != null && usable(p, now)) {
                    return context(p);
                }
            }
        }
        if (refresh != null) {
            return exchangeRefresh(request, response, refresh, now);
        }
        clearCookies(request, response);
        return null;
    }

    private AuthContext exchangeRefresh(HttpServletRequest request, HttpServletResponse response, String refresh, LocalDateTime now) {
        Row rt = db.first("select r.id as refresh_id, r.used_at, r.expires_at as refresh_expires_at, s.*, u.status as user_status"
                + " from auth_refresh_tokens r join auth_sessions s on s.id = r.session_id join users u on u.id = s.user_id"
                + " where r.token_hash = ?", Tokens.sha256Hex(refresh));
        if (rt == null || !usable(rt, now) || !after(rt.str("refresh_expires_at"), now)) {
            clearCookies(request, response);
            return null;
        }
        if (rt.get("used_at") != null) {
            LocalDateTime usedAt = parse(rt.str("used_at"));
            if (usedAt.plus(grace).isAfter(now)) {
                return context(rt); // a parallel request already exchanged it a moment ago
            }
            // a refresh token that was already exchanged is being replayed: assume it was stolen
            revoke(rt.lng("id"), "refresh_reuse");
            events.fail(request, rt.lng("user_id"), "refresh_reuse_detected", rt.str("auth_method"),
                    "session " + rt.lng("id") + " revoked");
            log.warn("Refresh token reuse detected for user {} (session {}); login revoked", rt.lng("user_id"), rt.lng("id"));
            clearCookies(request, response);
            return null;
        }
        // single use: only one request can win this update
        int won = db.update("update auth_refresh_tokens set used_at = ? where id = ? and used_at is null", now, rt.lng("refresh_id"));
        if (won == 0) {
            return context(rt);
        }
        LocalDateTime expires = parse(rt.str("expires_at"));
        String sessionToken = Tokens.random(32);
        String newRefresh = Tokens.random(32);
        db.update("update auth_sessions set session_token_hash = ?, session_expires_at = ?, previous_token_hash = null,"
                        + " previous_valid_until = null, rotated_at = ?, last_seen_at = ?, ip_address = ?, user_agent = ? where id = ?",
                Tokens.sha256Hex(sessionToken), min(now.plus(idle), expires), now, now, AuthEvents.ip(request), AuthEvents.userAgent(request), rt.lng("id"));
        insertRefresh(rt.lng("id"), newRefresh, expires);
        writeCookies(request, response, sessionToken, newRefresh, Values.truthy(rt.get("remember")) ? expires : null);
        return context(rt);
    }

    /** Sliding idle timeout + periodic rotation of the session token. */
    private void touch(HttpServletRequest request, HttpServletResponse response, Row s, String token, LocalDateTime now) {
        LocalDateTime expires = parse(s.str("expires_at"));
        LocalDateTime rotated = parse(s.str("rotated_at"));
        if (rotated.plus(rotateEvery).isBefore(now)) {
            String fresh = Tokens.random(32);
            int changed = db.update("update auth_sessions set session_token_hash = ?, previous_token_hash = ?, previous_valid_until = ?,"
                            + " rotated_at = ?, session_expires_at = ?, last_seen_at = ? where id = ? and session_token_hash = ?",
                    Tokens.sha256Hex(fresh), Tokens.sha256Hex(token), now.plus(grace), now, min(now.plus(idle), expires), now,
                    s.lng("id"), Tokens.sha256Hex(token));
            if (changed == 1) {
                writeSessionCookie(request, response, fresh);
            }
        } else if (parse(s.str("last_seen_at")).plusSeconds(60).isBefore(now)) {
            db.update("update auth_sessions set last_seen_at = ?, session_expires_at = ? where id = ?", now, min(now.plus(idle), expires), s.lng("id"));
        }
    }

    // ------------------------------------------------------------------ cookies

    private void writeCookies(HttpServletRequest request, HttpServletResponse response, String sessionToken, String refreshToken,
                              LocalDateTime persistentUntil) {
        writeSessionCookie(request, response, sessionToken);
        ResponseCookie.ResponseCookieBuilder b = base(request, REFRESH_COOKIE, refreshToken);
        if (persistentUntil != null) {
            b.maxAge(Duration.between(LocalDateTime.now(), persistentUntil));
        }
        response.addHeader("Set-Cookie", b.build().toString());
    }

    private void writeSessionCookie(HttpServletRequest request, HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", base(request, SESSION_COOKIE, token).build().toString());
    }

    public void clearCookies(HttpServletRequest request, HttpServletResponse response) {
        if (response == null) {
            return;
        }
        for (String name : new String[]{SESSION_COOKIE, REFRESH_COOKIE}) {
            if (cookie(request, name) != null) {
                response.addHeader("Set-Cookie", base(request, name, "").maxAge(0).build().toString());
            }
        }
    }

    private ResponseCookie.ResponseCookieBuilder base(HttpServletRequest request, String name, String value) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(secure(request)).sameSite("Lax").path("/");
    }

    /** Secure flag: "true", "false" or "auto" (= when the request arrived over HTTPS, also behind a proxy). */
    public boolean secure(HttpServletRequest request) {
        return switch (cookieSecure) {
            case "true" -> true;
            case "false" -> false;
            default -> request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        };
    }

    public static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank() && c.getValue().length() < 200) {
                return c.getValue();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private void insertRefresh(long sessionId, String token, LocalDateTime expires) {
        db.insertPlain("auth_refresh_tokens", Row.of("session_id", sessionId, "token_hash", Tokens.sha256Hex(token),
                "created_at", LocalDateTime.now(), "expires_at", expires));
    }

    private static boolean usable(Row s, LocalDateTime now) {
        return s.get("revoked_at") == null && after(s.str("expires_at"), now) && Values.toLong(s.get("user_status"), 1) != 0;
    }

    private static AuthContext context(Row s) {
        return new AuthContext(s.lng("user_id"), s.lng("id"), s.str("auth_method"));
    }

    private static boolean after(String dateTime, LocalDateTime now) {
        return dateTime != null && parse(dateTime).isAfter(now);
    }

    static LocalDateTime parse(String s) {
        return LocalDateTime.parse(s.replace(' ', 'T'));
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    /** Deletes long-dead rows so the tables stay small. */
    @Scheduled(cron = "0 17 3 * * *")
    public void purge() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int s = db.update("delete from auth_sessions where expires_at < ? or revoked_at < ?", cutoff, cutoff);
        int o = db.update("delete from auth_otps where created_at < ?", cutoff);
        int t = db.update("delete from auth_tokens where expires_at < ?", cutoff);
        int e = db.update("delete from auth_events where created_at < ?", LocalDateTime.now().minusDays(180));
        if (s + o + t + e > 0) {
            log.info("Auth cleanup: removed {} sessions, {} OTPs, {} tokens, {} events", s, o, t, e);
        }
    }
}
