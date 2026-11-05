package com.artistikcity.http;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Session based authentication with two guards, as in the Laravel app:
 * <ul>
 *   <li>"web"   - students (table {@code users})</li>
 *   <li>"admin" - administrators and teachers (table {@code admins})</li>
 * </ul>
 * Passwords are bcrypt hashes; hashes created by Laravel ($2y$) are accepted unchanged.
 */
@Component
public class Auth {

    private static final String USER_KEY = "auth.web.id";
    private static final String ADMIN_KEY = "auth.admin.id";
    private static final String USER_CACHE = Auth.class.getName() + ".user";
    private static final String ADMIN_CACHE = Auth.class.getName() + ".admin";

    private final Db db;
    private final PasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2Y, 10);

    public Auth(Db db) {
        this.db = db;
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

    // ------------------------------------------------------------ web guard

    /** Logged in student (without password / remember_token) or null. */
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
            logout(request);
            return null;
        }
        Row safe = u.without("password", "remember_token");
        request.setAttribute(USER_CACHE, safe);
        return safe;
    }

    public Long userId(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        Object id = s == null ? null : s.getAttribute(USER_KEY);
        return id instanceof Long l ? l : null;
    }

    public boolean check(HttpServletRequest request) {
        return user(request) != null;
    }

    /** Auth::attempt(). */
    public boolean attempt(HttpServletRequest request, String email, String password) {
        if (email == null || password == null) {
            return false;
        }
        Row u = db.first("select id, password from users where email = ?", email);
        if (u == null || !check(password, u.str("password"))) {
            return false;
        }
        login(request, u.lng("id"));
        return true;
    }

    public void login(HttpServletRequest request, long userId) {
        request.getSession(true);
        request.changeSessionId(); // session fixation protection, like $request->session()->regenerate()
        request.getSession().setAttribute(USER_KEY, userId);
        request.removeAttribute(USER_CACHE);
    }

    public void logout(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s != null) {
            s.removeAttribute(USER_KEY);
        }
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
        Row a = db.first("select id, password from admins where email = ?", email);
        if (a == null || !check(password, a.str("password"))) {
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
