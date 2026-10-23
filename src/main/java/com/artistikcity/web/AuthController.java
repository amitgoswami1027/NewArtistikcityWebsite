package com.artistikcity.web;

import com.artistikcity.auth.AccountService;
import com.artistikcity.auth.AuthEvents;
import com.artistikcity.auth.AuthMail;
import com.artistikcity.auth.EmailTokenService;
import com.artistikcity.auth.OtpService;
import com.artistikcity.auth.PasswordPolicy;
import com.artistikcity.auth.RateLimiter;
import com.artistikcity.auth.SessionService;
import com.artistikcity.http.Auth;
import com.artistikcity.http.Flash;
import com.artistikcity.http.GuardInterceptors;
import com.artistikcity.http.Redirects;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.ValidationException;
import com.artistikcity.support.Validator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Student sign-in pages and the form-based flows: e-mail + password, registration, magic-link confirmation,
 * e-mail verification and password reset. The JSON endpoints used by the sign-in screens (SMS OTP, magic-link
 * request, passkeys) are in {@link AuthApiController}; Google/Facebook in {@link SocialController}.
 *
 * Responses never reveal whether an e-mail address has an account (login errors are generic, password-reset
 * and magic-link requests always answer "check your inbox"), and every endpoint is rate limited.
 */
@Controller
public class AuthController {

    private final Inertia inertia;
    private final Auth auth;
    private final Db db;
    private final Json json;
    private final Validator validator;
    private final Redirects redirect;
    private final AccountService accounts;
    private final AuthMail authMail;
    private final EmailTokenService emailTokens;
    private final SessionService sessions;
    private final RateLimiter limiter;
    private final AuthEvents events;
    private final OtpService otp;
    private final Environment env;

    public AuthController(Inertia inertia, Auth auth, Db db, Json json, Validator validator, Redirects redirect, AccountService accounts,
                          AuthMail authMail, EmailTokenService emailTokens, SessionService sessions, RateLimiter limiter, AuthEvents events,
                          OtpService otp, Environment env) {
        this.inertia = inertia;
        this.auth = auth;
        this.db = db;
        this.json = json;
        this.validator = validator;
        this.redirect = redirect;
        this.accounts = accounts;
        this.authMail = authMail;
        this.emailTokens = emailTokens;
        this.sessions = sessions;
        this.limiter = limiter;
        this.events = events;
        this.otp = otp;
        this.env = env;
    }

    /** Props shared by the log in / join pages: which sign-in methods are available. */
    private Map<String, Object> methods(HttpServletRequest request) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("providers", SocialController.enabledProviders(env));
        m.put("defaultCountryCode", env.getProperty("app.auth.default-country-code", "91"));
        m.put("otpDevMode", otp.devCodesVisible(request));
        Map<String, Boolean> providers = SocialController.enabledProviders(env);
        // on the developer's own computer, explain why the Google / Facebook buttons are missing
        m.put("socialSetupHint", !providers.containsValue(true) && OtpService.isLocalRequest(request));
        m.put("status", Flash.get(request, "status"));
        return m;
    }

    // ------------------------------------------------------------------ login

    @GetMapping("/login")
    public ResponseEntity<String> loginForm(HttpServletRequest request) {
        rememberReturnTo(request);
        Map<String, Object> props = methods(request);
        props.put("canResetPassword", true);
        return inertia.render(request, "Auth/Login", props);
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "email", "required|string|email|max:255", "password", "required|string|max:200");
        String email = AccountService.normalizeEmail(in.get("email"));
        String ip = AuthEvents.ip(request);
        throttle("login:ip:" + ip, 30, 300);
        throttle("login:email:" + email, 10, 900);
        RateLimiter.Decision pair = limiter.peek("login:pair:" + email + "|" + ip, 5, 300);
        if (!pair.allowed()) {
            throw ValidationException.withMessage("email", "Too many failed attempts. Please try again in " + pair.retryAfterSeconds()
                    + " seconds, or sign in with an e-mail link.");
        }
        Long id = auth.verifyPassword(email, in.get("password"));
        if (id == null) {
            limiter.hit("login:pair:" + email + "|" + ip, 5, 300);
            Row u = accounts.findByEmail(email);
            events.fail(request, u == null ? null : u.lng("id"), "login_failed", "password", u == null ? "unknown e-mail" : "wrong password");
            throw ValidationException.withMessage("email", "That e-mail and password don't match. Check them, or sign in with an e-mail link.");
        }
        if (isDisabled(id)) {
            throw ValidationException.withMessage("email", "This account has been disabled. Please contact support.");
        }
        limiter.reset("login:pair:" + email + "|" + ip);
        auth.login(request, id, "password", Boolean.TRUE.equals(in.bool("remember")));
        return inertia.location(request, takeReturnTo(request, GuardInterceptors.HOME));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        auth.logout(request);
        HttpSession s = request.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        request.getSession(true);
        return inertia.location(request, "/");
    }

    // --------------------------------------------------------------- register

    @GetMapping({"/register", "/join"})
    public ResponseEntity<String> registerForm(HttpServletRequest request) {
        rememberReturnTo(request);
        return inertia.render(request, "Auth/Register", methods(request));
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in,
                "name", "required|string|max:255",
                "email", "required|string|email|max:255",
                "password", "required|confirmed|min:8|max:128");
        throttle("register:ip:" + AuthEvents.ip(request), 10, 3600);
        String email = AccountService.normalizeEmail(in.get("email"));
        String problem = PasswordPolicy.problem(in.get("password"), email, in.get("name"));
        if (problem != null) {
            throw ValidationException.withMessage("password", problem);
        }
        if (accounts.findByEmail(email) != null) {
            throw ValidationException.withMessage("email", "There's already an account with this e-mail. Log in instead, or use \"Email me a sign-in link\".");
        }
        long id = accounts.create(in.get("name"), email, false, null, false, auth.hash(in.get("password")), null);
        events.ok(request, id, "signup", "password");
        authMail.sendVerification(request, id, email, in.get("name"));
        auth.login(request, id, "signup", Boolean.TRUE.equals(in.bool("remember")));
        return inertia.location(request, takeReturnTo(request, GuardInterceptors.HOME));
    }

    // ------------------------------------------------------------------ magic link (landing page)

    /**
     * The link in the e-mail opens this confirmation page instead of signing in straight away, so mail
     * scanners and link previews that "click" links don't use up the single-use token.
     */
    @GetMapping("/auth/magic-link")
    public ResponseEntity<String> magicLinkPage(HttpServletRequest request, @RequestParam(value = "token", required = false) String token) {
        Row t = emailTokens.peek(EmailTokenService.MAGIC_LINK, token);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("token", t == null ? null : token);
        props.put("email", t == null ? null : t.str("email"));
        props.put("valid", t != null);
        return inertia.render(request, "Auth/MagicLink", props);
    }

    @PostMapping("/auth/magic-link/consume")
    public ResponseEntity<String> magicLinkConsume(HttpServletRequest request) {
        Input in = Input.of(request, json);
        throttle("magic:consume:ip:" + AuthEvents.ip(request), 20, 600);
        Row t = emailTokens.consume(EmailTokenService.MAGIC_LINK, in.get("token"));
        if (t == null) {
            Flash.put(request, "message", "That sign-in link has expired or was already used. Request a new one below.");
            return inertia.location(request, "/login");
        }
        String email = t.str("email");
        Row user = accounts.findByEmail(email);
        long id;
        if (user == null) {
            id = accounts.create(null, email, true, null, false, null, null); // the click proved the inbox: verified
            events.ok(request, id, "signup", "magic_link");
        } else {
            id = user.lng("id");
            if (user.get("email_verified_at") == null) {
                // first proof of ownership of this inbox: drop anything an earlier, unverified registrant set up
                db.updateById("users", id, Row.of("password_hash", null));
                sessions.revokeAll(id, null, "unverified_account_claimed");
                events.record(request, id, "unverified_account_claimed", "magic_link", true, email);
            }
            accounts.markEmailVerified(id);
            if (isDisabled(id)) {
                Flash.put(request, "message", "This account has been disabled. Please contact support.");
                return inertia.location(request, "/login");
            }
        }
        auth.login(request, id, "magic_link", true);
        Object rt = emailTokens.meta(t).get("return_to");
        return inertia.location(request, rt instanceof String s && isSafeReturnPath(s) ? s : takeReturnTo(request, GuardInterceptors.HOME));
    }

    // ------------------------------------------------------------------ return-to handling

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
                && !to.startsWith("/login") && !to.startsWith("/register") && !to.startsWith("/join") && !to.startsWith("/auth/")
                && to.length() < 500;
    }

    // ------------------------------------------------ checkout page mini forms

    /** UserAuthController@userLogin. */
    @PostMapping("/user-login")
    public ResponseEntity<String> userLogin(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "email", "required|email", "password", "required");
        String email = AccountService.normalizeEmail(in.get("email"));
        throttle("login:ip:" + AuthEvents.ip(request), 30, 300);
        throttle("login:email:" + email, 10, 900);
        Long id = auth.verifyPassword(email, in.get("password"));
        if (id != null && !isDisabled(id)) {
            auth.login(request, id, "password", false);
            return "checkout".equals(in.get("redirect_url")) ? redirect.route(request, "course.checkout") : redirect.route(request, "user.dashboard");
        }
        events.fail(request, null, "login_failed", "password", "checkout form");
        Flash.put(request, "error", "That e-mail and password don't match.");
        return "checkout".equals(in.get("redirect_url")) ? redirect.route(request, "course.checkout") : redirect.route(request, "login");
    }

    /** UserAuthController@userRegistration. */
    @PostMapping("/user-registration")
    public ResponseEntity<String> userRegistration(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "name", "required|max:255", "email", "required|email|max:255|unique:users", "password", "required|min:8|max:128");
        throttle("register:ip:" + AuthEvents.ip(request), 10, 3600);
        String email = AccountService.normalizeEmail(in.get("email"));
        String problem = PasswordPolicy.problem(in.get("password"), email, in.get("name"));
        if (problem != null) {
            throw ValidationException.withMessage("password", problem);
        }
        long id = accounts.create(in.get("name"), email, false, null, false, auth.hash(in.get("password")), null);
        events.ok(request, id, "signup", "password");
        authMail.sendVerification(request, id, email, in.get("name"));
        auth.login(request, id, "signup", false);
        Flash.put(request, "success", "Welcome to ArtistikCity! You're signed in.");
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
        validator.validate(in, "email", "required|email|max:255");
        String email = AccountService.normalizeEmail(in.get("email"));
        throttle("reset:ip:" + AuthEvents.ip(request), 10, 3600);
        Row user = accounts.findByEmail(email);
        if (user != null && emailTokens.recentCount(email, EmailTokenService.PASSWORD_RESET, Duration.ofHours(1)) < 3) {
            authMail.sendPasswordReset(request, user.lng("id"), email, user.str("name"));
            events.ok(request, user.lng("id"), "password_reset_requested", "password");
        }
        // same answer whether or not the account exists
        Flash.put(request, "status", "If an account exists for " + email + ", we've e-mailed a link to reset the password.");
        return redirect.back(request);
    }

    @GetMapping("/reset-password/{token}")
    public ResponseEntity<String> resetForm(HttpServletRequest request, @PathVariable("token") String token,
                                            @RequestParam(value = "email", required = false) String email) {
        return inertia.render(request, "Auth/ResetPassword", Row.of("email", email, "token", token,
                "valid", emailTokens.peek(EmailTokenService.PASSWORD_RESET, token) != null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "token", "required", "email", "required|email", "password", "required|confirmed|min:8|max:128");
        throttle("reset:consume:ip:" + AuthEvents.ip(request), 20, 600);
        String email = AccountService.normalizeEmail(in.get("email"));
        Row peek = emailTokens.peek(EmailTokenService.PASSWORD_RESET, in.get("token"));
        if (peek == null || !email.equals(peek.str("email"))) {
            throw ValidationException.withMessage("email", "This password reset link is invalid or has expired. Request a new one.");
        }
        String problem = PasswordPolicy.problem(in.get("password"), email, null);
        if (problem != null) {
            throw ValidationException.withMessage("password", problem);
        }
        Row t = emailTokens.consume(EmailTokenService.PASSWORD_RESET, in.get("token"));
        if (t == null) {
            throw ValidationException.withMessage("email", "This password reset link is invalid or has expired. Request a new one.");
        }
        long userId = t.lng("user_id");
        db.updateById("users", userId, Row.of("password_hash", auth.hash(in.get("password"))));
        accounts.markEmailVerified(userId); // they opened the mail
        sessions.revokeAll(userId, null, "password_reset");
        events.ok(request, userId, "password_reset", "password");
        Flash.put(request, "status", "Your password has been reset. You can log in with it now.");
        return redirect.route(request, "login");
    }

    // ------------------------------------------------ e-mail verification / confirm

    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyNotice(HttpServletRequest request) {
        Row user = auth.user(request);
        if (user.get("email") == null || user.get("email_verified_at") != null) {
            return redirect.to(request, GuardInterceptors.HOME);
        }
        return inertia.render(request, "Auth/VerifyEmail", Row.of("status", Flash.get(request, "status"), "email", user.get("email")));
    }

    /** Link from the verification e-mail. Works whether or not the browser is signed in. */
    @GetMapping("/verify-email/confirm")
    public ResponseEntity<String> verifyConfirm(HttpServletRequest request, @RequestParam(value = "token", required = false) String token) {
        Row t = emailTokens.consume(EmailTokenService.VERIFY_EMAIL, token);
        if (t == null || t.get("user_id") == null) {
            Flash.put(request, "message", "That confirmation link has expired or was already used.");
            return redirect.to(request, auth.userId(request) != null ? "/verify-email" : "/login");
        }
        Row u = db.find("users", t.get("user_id"));
        if (u != null && t.str("email").equals(u.str("email"))) { // the address may have changed since the mail was sent
            accounts.markEmailVerified(u.lng("id"));
            events.ok(request, u.lng("id"), "email_verified", "magic_link");
        }
        return redirect.to(request, auth.userId(request) != null ? GuardInterceptors.HOME + "?verified=1" : "/login?verified=1");
    }

    /** Old Laravel-style link format: no longer accepted (the hash was never checked); send people to request a new link. */
    @GetMapping("/verify-email/{id}/{hash}")
    public ResponseEntity<String> legacyVerify(HttpServletRequest request, @PathVariable("id") Long id, @PathVariable("hash") String hash) {
        Flash.put(request, "status", "Please use the latest confirmation e-mail, or send a new one below.");
        return redirect.to(request, "/verify-email");
    }

    @PostMapping("/email/verification-notification")
    public ResponseEntity<String> sendVerification(HttpServletRequest request) {
        Row user = auth.user(request);
        if (user.get("email") != null && user.get("email_verified_at") == null
                && emailTokens.recentCount(user.str("email"), EmailTokenService.VERIFY_EMAIL, Duration.ofHours(1)) < 3) {
            authMail.sendVerification(request, user.lng("id"), user.str("email"), user.str("name"));
        }
        String accept = request.getHeader("Accept");
        if (request.getHeader("X-Inertia") == null && accept != null && accept.contains("application/json")) {
            return ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(json.encode(Map.of("ok", true)));
        }
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
        throttle("confirm:user:" + auth.userId(request), 5, 300);
        Row hash = db.first("select password_hash from users where id = ?", auth.userId(request));
        if (hash == null || !auth.check(in.get("password"), hash.str("password_hash"))) {
            throw ValidationException.withMessage("password", "The provided password is incorrect.");
        }
        request.getSession().setAttribute("auth.password_confirmed_at", System.currentTimeMillis() / 1000);
        return redirect.to(request, GuardInterceptors.HOME);
    }

    // ------------------------------------------------------------------ helpers

    private void throttle(String key, int limit, long windowSeconds) {
        RateLimiter.Decision d = limiter.hit(key, limit, windowSeconds);
        if (!d.allowed()) {
            throw ValidationException.withMessage("email", "Too many attempts. Please try again in " + d.retryAfterSeconds() + " seconds.");
        }
    }

    private boolean isDisabled(long userId) {
        Row u = db.first("select status from users where id = ?", userId);
        return u == null || u.lng("status", 1) == 0;
    }

}
