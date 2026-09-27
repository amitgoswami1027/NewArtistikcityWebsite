package com.artistikcity.web;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Flash;
import com.artistikcity.http.GuardInterceptors;
import com.artistikcity.http.Redirects;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.Mailer;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Slugs;
import com.artistikcity.support.Str;
import com.artistikcity.support.ValidationException;
import com.artistikcity.support.Validator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Student authentication - the Laravel Breeze controllers (routes/auth.php) plus UserAuthController
 * (the small login/registration forms on the checkout page).
 */
@Controller
public class AuthController {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_SECONDS = 60;
    private static final int RESET_EXPIRE_MINUTES = 60;

    private final Inertia inertia;
    private final Auth auth;
    private final Db db;
    private final Json json;
    private final Validator validator;
    private final Slugs slugs;
    private final Mailer mailer;
    private final Redirects redirect;
    /** throttle key -> [attempts, first attempt epoch second] (LoginRequest rate limiting). */
    private final Map<String, long[]> attempts = new ConcurrentHashMap<>();

    public AuthController(Inertia inertia, Auth auth, Db db, Json json, Validator validator, Slugs slugs, Mailer mailer, Redirects redirect) {
        this.inertia = inertia;
        this.auth = auth;
        this.db = db;
        this.json = json;
        this.validator = validator;
        this.slugs = slugs;
        this.mailer = mailer;
        this.redirect = redirect;
    }

    // ------------------------------------------------------------------ login

    @GetMapping("/login")
    public ResponseEntity<String> loginForm(HttpServletRequest request) {
        rememberReturnTo(request);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("canResetPassword", true);
        props.put("status", Flash.get(request, "status"));
        return inertia.render(request, "Auth/Login", props);
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "email", "required|string|email", "password", "required|string");
        String key = in.get("email").toLowerCase() + "|" + request.getRemoteAddr();
        long now = System.currentTimeMillis() / 1000;
        long[] a = attempts.get(key);
        if (a != null && now - a[1] > LOCKOUT_SECONDS) {
            attempts.remove(key);
            a = null;
        }
        if (a != null && a[0] >= MAX_ATTEMPTS) {
            long seconds = LOCKOUT_SECONDS - (now - a[1]);
            throw ValidationException.withMessage("email", "Too many login attempts. Please try again in " + seconds + " seconds.");
        }
        if (!auth.attempt(request, in.get("email"), in.get("password"))) {
            attempts.merge(key, new long[]{1, now}, (o, n) -> new long[]{o[0] + 1, o[1]});
            throw ValidationException.withMessage("email", "These credentials do not match our records.");
        }
        attempts.remove(key);
        return inertia.location(request, takeReturnTo(request, GuardInterceptors.HOME));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        auth.logout(request);
        request.getSession(true).invalidate();
        request.getSession(true);
        return inertia.location(request, "/");
    }

    // --------------------------------------------------------------- register

    @GetMapping({"/register", "/join"})
    public ResponseEntity<String> registerForm(HttpServletRequest request) {
        rememberReturnTo(request);
        return inertia.render(request, "Auth/Register", Map.of());
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in,
                "name", "required|string|max:255",
                "email", "required|string|email|max:255|unique:users",
                "password", "required|confirmed|min:8");
        long id = createUser(in.get("name"), in.get("email"), in.get("password"));
        auth.login(request, id);
        return inertia.location(request, takeReturnTo(request, GuardInterceptors.HOME));
    }

    /** Stores a safe, same-site ?return_to=/path so the visitor lands back where they started after signing in. */
    public static void rememberReturnTo(HttpServletRequest request) {
        String to = request.getParameter("return_to");
        if (isSafeReturnPath(to)) {
            request.getSession(true).setAttribute("auth.return_to", to);
        }
    }

    /** Returns (and clears) the remembered return path, or the fallback. */
    public static String takeReturnTo(HttpServletRequest request, String fallback) {
        var session = request.getSession(false);
        if (session == null) {
            return fallback;
        }
        Object to = session.getAttribute("auth.return_to");
        session.removeAttribute("auth.return_to");
        return to instanceof String s && isSafeReturnPath(s) ? s : fallback;
    }

    static boolean isSafeReturnPath(String to) {
        return to != null && to.startsWith("/") && !to.startsWith("//") && !to.contains("\\") && !to.contains("://")
                && !to.startsWith("/login") && !to.startsWith("/register") && !to.startsWith("/join") && to.length() < 500;
    }

    private long createUser(String name, String email, String password) {
        return db.insert("users", Row.of("name", name, "slug", slugs.unique("users", "slug", name, null), "email", email,
                "password", auth.hash(password), "status", 1, "free_courses", 0, "free_course_type", "default"));
    }

    // ------------------------------------------------ checkout page mini forms

    /** UserAuthController@userLogin. */
    @PostMapping("/user-login")
    public ResponseEntity<String> userLogin(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "email", "required", "password", "required");
        if (auth.attempt(request, in.get("email"), in.get("password"))) {
            if ("checkout".equals(in.get("redirect_url"))) {
                return redirect.route(request, "course.checkout");
            }
            return redirect.route(request, "user.dashboard");
        }
        Flash.put(request, "error", "Oopes! You have entered invalid credentials");
        return "checkout".equals(in.get("redirect_url")) ? redirect.route(request, "course.checkout") : redirect.route(request, "login");
    }

    /** UserAuthController@userRegistration. */
    @PostMapping("/user-registration")
    public ResponseEntity<String> userRegistration(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "name", "required", "email", "required|email|unique:users", "password", "required|min:6");
        long id = createUser(in.get("name"), in.get("email"), in.get("password"));
        auth.login(request, id);
        Flash.put(request, "success", "Great! You have Successfully loggedin");
        return "checkout".equals(in.get("redirect_url")) || request.getSession().getAttribute("cart") != null
                ? redirect.route(request, "course.checkout") : redirect.route(request, "user.dashboard");
    }

    // ---------------------------------------------------------- password reset

    @GetMapping("/forgot-password")
    public ResponseEntity<String> forgotForm(HttpServletRequest request) {
        return inertia.render(request, "Auth/ForgotPassword", Row.of("status", Flash.get(request, "status")));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> sendResetLink(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "email", "required|email");
        Row user = db.first("select id, name, email from users where email = ?", in.get("email"));
        if (user == null) {
            throw ValidationException.withMessage("email", "We can't find a user with that email address.");
        }
        String token = Str.random(64);
        db.update("delete from password_resets where email = ?", user.str("email"));
        db.insertPlain("password_resets", Row.of("email", user.str("email"), "token", auth.hash(token), "created_at", LocalDateTime.now()));
        String url = Inertia.baseUrl(request) + redirect.routes().path("password.reset", token, Map.of("email", user.str("email")));
        mailer.send(user.str("email"), "Reset Password Notification", "emails/reset-password",
                Row.of("name", user.str("name"), "url", url, "expire", RESET_EXPIRE_MINUTES, "baseUrl", Inertia.baseUrl(request)));
        Flash.put(request, "status", "We have emailed your password reset link!");
        return redirect.back(request);
    }

    @GetMapping("/reset-password/{token}")
    public ResponseEntity<String> resetForm(HttpServletRequest request, @PathVariable("token") String token,
                                            @RequestParam(value = "email", required = false) String email) {
        return inertia.render(request, "Auth/ResetPassword", Row.of("email", email, "token", token));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "token", "required", "email", "required|email", "password", "required|confirmed|min:8");
        Row reset = db.first("select * from password_resets where email = ?", in.get("email"));
        boolean valid = reset != null && auth.check(in.get("token"), reset.str("token"))
                && LocalDateTime.parse(reset.str("created_at").replace(' ', 'T')).plusMinutes(RESET_EXPIRE_MINUTES).isAfter(LocalDateTime.now());
        if (!valid) {
            throw ValidationException.withMessage("email", "This password reset token is invalid.");
        }
        db.updateWhere("users", Row.of("password", auth.hash(in.get("password")), "remember_token", Str.random(60)), "email = ?", in.get("email"));
        db.update("delete from password_resets where email = ?", in.get("email"));
        Flash.put(request, "status", "Your password has been reset!");
        return redirect.route(request, "login");
    }

    // ------------------------------------------------ email verification / confirm

    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyNotice(HttpServletRequest request) {
        Row user = auth.user(request);
        if (user.get("email_verified_at") != null) {
            return redirect.to(request, GuardInterceptors.HOME);
        }
        return inertia.render(request, "Auth/VerifyEmail", Row.of("status", Flash.get(request, "status")));
    }

    @GetMapping("/verify-email/{id}/{hash}")
    public ResponseEntity<String> verify(HttpServletRequest request, @PathVariable("id") Long id, @PathVariable("hash") String hash) {
        Row user = auth.user(request);
        if (user != null && user.lng("id").equals(id) && user.get("email_verified_at") == null) {
            db.updateById("users", id, Row.of("email_verified_at", LocalDateTime.now()));
        }
        return redirect.to(request, GuardInterceptors.HOME + "?verified=1");
    }

    @PostMapping("/email/verification-notification")
    public ResponseEntity<String> sendVerification(HttpServletRequest request) {
        Flash.put(request, "status", "verification-link-sent");
        return redirect.back(request);
    }

    @GetMapping("/confirm-password")
    public ResponseEntity<String> confirmForm(HttpServletRequest request) {
        return inertia.render(request, "Auth/ConfirmPassword", Map.of());
    }

    @PostMapping("/confirm-password")
    public ResponseEntity<String> confirm(HttpServletRequest request) {
        Input in = Input.of(request, json);
        Row hash = db.first("select password from users where id = ?", auth.userId(request));
        if (hash == null || !auth.check(in.get("password"), hash.str("password"))) {
            throw ValidationException.withMessage("password", "The provided password is incorrect.");
        }
        request.getSession().setAttribute("auth.password_confirmed_at", System.currentTimeMillis() / 1000);
        return redirect.to(request, GuardInterceptors.HOME);
    }
}
