package com.artistikcity.http;

import com.artistikcity.auth.AuthSessionFilter;
import com.artistikcity.auth.SessionService;
import com.artistikcity.support.Str;
import jakarta.servlet.http.HttpServletResponse;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Authentication guards:
 * <ul>
 *   <li>"web"   - students (table {@code users}). Logins live in the server-side session store
 *       ({@link SessionService}, table {@code auth_sessions}) with rotating session tokens and single-use
 *       refresh tokens in HttpOnly cookies. Sign-in methods: password, Google, Facebook, SMS OTP,
 *       e-mail magic link, passkeys (package {@code com.artistikcity.auth}).</li>
 *   <li>"admin" - administrators and teachers (table {@code admins}), HTTP-session based.</li>
 * </ul>
 * Passwords are bcrypt hashes (cost 12); hashes created by Laravel ($2y$10) are accepted and upgraded.
 */
@Component
public class Auth {

    private static final String USER_KEY = "auth.web.id";
    private static final String ADMIN_KEY = "auth.admin.id";
    private static final String USER_CACHE = Auth.class.getName() + ".user";
    private static final String ADMIN_CACHE = Auth.class.getName() + ".admin";

    private final Db db;
    private final SessionService sessions;
    /** cost 12 for new hashes; older ($2y$10 from Laravel) hashes are upgraded on the next successful login */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2Y, BCRYPT_COST);
    private static final int BCRYPT_COST = 12;
    /** hash of a random password, compared against when the account does not exist (same timing either way) */
    private final String dummyHash;

    public Auth(Db db, SessionService sessions) {
        this.db = db;
        this.sessions = sessions;
        this.dummyHash = passwordEncoder.encode(Str.random(24));
    }

    // ------------------------------------------------------------- passwords

    public String hash(String plain) {
        return passwordEncoder.encode(plain);
    }

    public boolean check(String plain, String hash) {
        if (plain == null || hash == null || !hash.startsWith("$2")) {
            return false;
        }
        try {
            return passwordEncoder.matches(plain, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** True when a stored hash uses a lower cost than today's setting and should be re-hashed. */
    public boolean needsRehash(String hash) {
        try {
            return hash == null || Integer.parseInt(hash.substring(4, 6)) < BCRYPT_COST;
        } catch (RuntimeException e) {
            return true;
        }
    }

    // ------------------------------------------------------------ web guard

    /**
     * Logged-in student or null. Secrets are removed; {@code phone} (old name of phone_number) and
     * {@code has_password} are added for the React pages.
     */
    public Row user(HttpServletRequest request) {
        Object cached = request.getAttribute(USER_CACHE);
        if (cached instanceof Row r) {
            return r;
        }
        Long id = userId(request);
        if (id == null) {
            return null;
        }
        Row u = db.find("users", id);
        if (u == null) {
            return null;
        }
        Row safe = u.without("password_hash", "remember_token", "webauthn_user_handle");
        safe.put("phone", u.get("phone_number"));
        safe.put("has_password", u.get("password_hash") != null);
        request.setAttribute(USER_CACHE, safe);
        return safe;
    }

    public Long userId(HttpServletRequest request) {
        SessionService.AuthContext ctx = AuthSessionFilter.context(request);
        return ctx == null ? null : ctx.userId();
    }

    /** The current login (session id, method), or null. */
    public SessionService.AuthContext context(HttpServletRequest request) {
        return AuthSessionFilter.context(request);
    }

    public boolean check(HttpServletRequest request) {
        return user(request) != null;
    }

    /**
     * Email + password check. Always runs one bcrypt comparison (against a dummy hash for unknown
     * e-mails) so response times don't reveal which accounts exist. Upgrades old hashes on success.
     * @return the user id, or null
     */
    public Long verifyPassword(String email, String password) {
        if (email == null || password == null || password.length() > 200) {
            return null;
        }
        Row u = db.first("select id, password_hash, status from users where email = ?", email.trim().toLowerCase());
        String hash = u == null || u.get("password_hash") == null ? dummyHash : u.str("password_hash");
        boolean ok = check(password, hash);
        if (u == null || u.get("password_hash") == null || !ok) {
            return null;
        }
        if (needsRehash(hash)) {
            db.update("update users set password_hash = ? where id = ?", hash(password), u.lng("id"));
        }
        return u.lng("id");
    }

    /** Auth::attempt() (kept for the checkout page mini form). */
    public boolean attempt(HttpServletRequest request, String email, String password) {
        Long id = verifyPassword(email, password);
        if (id == null) {
            return false;
        }
        login(request, id, "password", false);
        return true;
    }

    /** Starts a login for the user: new server-side session, rotated tokens, HttpOnly cookies. */
    public void login(HttpServletRequest request, long userId, String method, boolean remember) {
        HttpServletResponse response = AuthSessionFilter.response(request);
        SessionService.AuthContext ctx = sessions.start(request, response, userId, method, remember);
        request.setAttribute(AuthSessionFilter.CONTEXT, ctx);
        request.removeAttribute(USER_CACHE);
    }

    public void login(HttpServletRequest request, long userId) {
        login(request, userId, "password", false);
    }

    public void logout(HttpServletRequest request) {
        sessions.end(request, AuthSessionFilter.response(request), "logout");
        request.removeAttribute(AuthSessionFilter.CONTEXT);
        request.removeAttribute(USER_CACHE);
    }

    /** Forget the cached user so the next call re-reads the database (after a profile update). */
    public void refresh(HttpServletRequest request) {
        request.removeAttribute(USER_CACHE);
        request.removeAttribute(ADMIN_CACHE);
    }

    // ----------------------------------------------------------- admin guard

    public Row admin(HttpServletRequest request) {
        Object cached = request.getAttribute(ADMIN_CACHE);
        if (cached instanceof Row r) {
            return r;
        }
        HttpSession s = request.getSession(false);
        Object id = s == null ? null : s.getAttribute(ADMIN_KEY);
        if (!(id instanceof Long)) {
            return null;
        }
        Row a = db.find("admins", id);
        if (a == null) {
            logoutAdmin(request);
            return null;
        }
        Row safe = a.without("password", "remember_token");
        request.setAttribute(ADMIN_CACHE, safe);
        return safe;
    }

    public Long adminId(HttpServletRequest request) {
        Row a = admin(request);
        return a == null ? null : a.lng("id");
    }

    public boolean attemptAdmin(HttpServletRequest request, String email, String password) {
        if (email == null || password == null) {
            return false;
        }
        Row a = db.first("select id, password from admins where email = ?", email.trim());
        boolean ok = check(password, a == null ? dummyHash : a.str("password"));
        if (a == null || !ok) {
            return false;
        }
        request.getSession(true);
        request.changeSessionId();
        request.getSession().setAttribute(ADMIN_KEY, a.lng("id"));
        request.removeAttribute(ADMIN_CACHE);
        return true;
    }

    public void logoutAdmin(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s != null) {
            s.removeAttribute(ADMIN_KEY);
        }
        request.removeAttribute(ADMIN_CACHE);
    }
}
