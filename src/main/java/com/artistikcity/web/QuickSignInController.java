package com.artistikcity.web;

import com.artistikcity.auth.AccountService;
import com.artistikcity.auth.AuthEvents;
import com.artistikcity.auth.EmailCodeService;
import com.artistikcity.auth.JwtVerifier;
import com.artistikcity.auth.LoginSettings;
import com.artistikcity.auth.OtpService;
import com.artistikcity.auth.RateLimiter;
import com.artistikcity.auth.Tokens;
import com.artistikcity.auth.sms.SmsSender;
import com.artistikcity.http.Auth;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quick sign-in for pages that must not send the visitor away (the landing page checkout): everything is a JSON call
 * from the page itself, using the industry's standard pieces.
 * <ul>
 *   <li><b>Google</b> one-tap / "Sign in with Google" button (Google Identity Services): the browser gets a signed ID
 *       token from Google and posts it here; it is checked against Google's keys, audience = our client id, and a
 *       single-use nonce issued by {@link #options}.</li>
 *   <li><b>E-mail code</b>: a six-digit code by e-mail ({@link EmailCodeService}); new e-mail = new account.</li>
 *   <li><b>Phone code</b> (SMS, with WebOTP autofill) and <b>passkeys</b> reuse the /auth/otp/* and /auth/passkey/*
 *       endpoints of {@link AuthApiController}.</li>
 * </ul>
 * Answers {ok, user:{name, email, phone}} or {message, errors, retryAfter}.
 */
@RestController
public class QuickSignInController {

    private static final Logger log = LoggerFactory.getLogger(QuickSignInController.class);
    static final String GIS_NONCE = "auth.gis_nonce";

    private final Auth auth;
    private final Json json;
    private final AccountService accounts;
    private final EmailCodeService emailCodes;
    private final OtpService otp;
    private final SmsSender sms;
    private final JwtVerifier jwt;
    private final RateLimiter limiter;
    private final AuthEvents events;
    private final Environment env;
    private final Db db;
    private final LoginSettings login;

    public QuickSignInController(Auth auth, Json json, AccountService accounts, EmailCodeService emailCodes, OtpService otp, SmsSender sms,
                                 JwtVerifier jwt, RateLimiter limiter, AuthEvents events, Environment env, Db db, LoginSettings login) {
        this.db = db;
        this.login = login;
        this.auth = auth;
        this.json = json;
        this.accounts = accounts;
        this.emailCodes = emailCodes;
        this.otp = otp;
        this.sms = sms;
        this.jwt = jwt;
        this.limiter = limiter;
        this.events = events;
        this.env = env;
    }

    public static final List<String> METHODS = List.of("google", "email", "phone", "passkey", "password");

    /**
     * The landing checkout's sign-in switches (Admin › Landing Page Management › Payments & checkout › Sign-in at checkout):
     * settings tag landing_pages, key signin, JSON {method: true|false}; every method is on unless switched off.
     */
    public Map<String, Boolean> switches() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        Row r = db.first("select meta_value from settings where meta_tag = 'landing_pages' and meta_key = 'signin' order by id desc limit 1");
        Map<String, Object> saved = r == null ? null : json.decodeMap(r.str("meta_value"));
        for (String k : METHODS) m.put(k, saved == null || !Boolean.FALSE.equals(saved.get(k)));
        return m;
    }

    public void saveSwitches(Map<String, Boolean> on) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String v = json.encode(on);
        if (db.update("update settings set meta_value = ?, updated_at = ? where meta_tag = 'landing_pages' and meta_key = 'signin'", v, now) == 0) {
            db.insert("settings", Row.of("meta_tag", "landing_pages", "meta_key", "signin", "meta_value", v, "created_at", now, "updated_at", now));
        }
    }

    /** What each method needs on this server: {ready, detail} (shown in the admin; never a secret). */
    public Map<String, Object> readiness(HttpServletRequest request) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean google = !env.getProperty("social.google.client-id", "").isBlank();
        m.put("google", Map.of("ready", google, "detail", google
                ? "Uses the Google client ID from the Configuration Panel. Add your site's address (e.g. https://www.artistikcity.com) under Authorized JavaScript origins of that client in Google Cloud Console."
                : "Add the Google client ID (and secret) in System parameters › Configuration Panel › Google sign-in."));
        LoginSettings.Values lv = login.values();
        boolean mail = emailCodes.available(request);
        m.put("email", Map.of("ready", mail || login.emailReady(request), "detail", mail ? "Codes are sent by the site's mail server."
                : login.emailReady(request) ? "Simulated: no mail server yet, so the code is shown on the screen (Login & verification)."
                : "Needs a mail server: set MAIL_HOST (for example Amazon SES) in the server settings, or switch on simulated e-mail codes in Login & verification."));
        boolean phone = login.smsReal() || otp.devCodesVisible(request);
        m.put("phone", Map.of("ready", phone || lv.simulatePhone(), "detail", phone ? "Codes are sent by the SMS provider chosen in the Configuration Panel."
                : lv.simulatePhone() ? "Simulated: no SMS provider yet, so the code is shown on the screen (Login & verification)."
                : "Choose an SMS provider (Twilio, Amazon SNS or Infobip) in System parameters › Configuration Panel › SMS, or switch on simulated SMS codes in Login & verification."));
        m.put("passkey", Map.of("ready", true, "detail", "For buyers who saved a passkey in My account › Login & security; their browser decides."));
        m.put("password", Map.of("ready", true, "detail", "E-mail and password: works with no setup. New buyers create an account with a password."));
        return m;
    }

    /** Which quick sign-in methods work on this server right now and are switched on (a method that can't reach people is not offered). */
    public Map<String, Object> options(HttpServletRequest request) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Boolean> on = switches();
        String clientId = env.getProperty("social.google.client-id", "").trim();
        if (!clientId.isEmpty() && on.get("google")) {
            HttpSession s = request.getSession(true);
            Object n = s.getAttribute(GIS_NONCE);
            String nonce = n instanceof String x ? x : Tokens.random(24);
            s.setAttribute(GIS_NONCE, nonce);
            m.put("google", Map.of("clientId", clientId, "nonce", nonce));
        } else {
            m.put("google", null);
        }
        m.put("email", on.get("email") && login.emailReady(request));
        m.put("phone", on.get("phone") && login.phoneReady(request));
        m.put("passkey", on.get("passkey"));       // the browser decides whether it can
        // e-mail + password (the site's own /login and /register): needs no setup; on when switched on, or when nothing else could work
        boolean anyCode = Boolean.TRUE.equals(m.get("email")) || Boolean.TRUE.equals(m.get("phone")) || m.get("google") != null;
        m.put("password", on.get("password") || !anyCode);
        m.put("country", env.getProperty("app.auth.default-country-code", "91"));
        LoginSettings.Values lv = login.values();
        m.put("simulated", Map.of("phone", lv.simulatePhone(), "email", lv.simulateEmail()));
        return m;
    }

    @GetMapping("/auth/quick/options")
    public ResponseEntity<String> optionsJson(HttpServletRequest request) {
        return json(HttpStatus.OK, options(request));
    }

    // ------------------------------------------------------------------ e-mail code

    @PostMapping("/auth/email-code/send")
    public ResponseEntity<String> sendCode(HttpServletRequest request) {
        Input in = Input.of(request, json);
        String email = email(in.get("email"));
        if (email == null) return invalid("email", "Enter a valid e-mail address.");
        LoginSettings.Decision how = login.email(request, email);
        if (how.mode() == LoginSettings.Mode.REFUSE) return json(HttpStatus.CONFLICT, Map.of("message", how.message(), "code", "CODE_UNAVAILABLE"));
        try {
            EmailCodeService.SendResult r = emailCodes.send(request, email, trim(in.get("name"), 120), how.mode() == LoginSettings.Mode.SIMULATE);
            Map<String, Object> body = ok();
            body.put("email", email);
            body.put("expiresIn", r.expiresIn());
            body.put("resendIn", r.resendIn());
            if (r.devCode() != null) body.put("devCode", r.devCode());     // development (local browser) or a simulated code
            body.put("simulated", r.simulated());
            return json(HttpStatus.OK, body);
        } catch (OtpService.OtpLimitException e) {
            return tooMany(e.getMessage(), e.retryAfter());
        } catch (RuntimeException e) {
            return error(HttpStatus.BAD_GATEWAY, "We couldn't send the e-mail right now. Please try again, or sign in another way.");
        }
    }

    /** {email, code, name?, remember} -> signed in; an address without an account gets one (the code proves the address). */
    @PostMapping("/auth/email-code/verify")
    public ResponseEntity<String> verifyCode(HttpServletRequest request) {
        Input in = Input.of(request, json);
        String email = email(in.get("email"));
        if (email == null) return invalid("email", "Enter a valid e-mail address.");
        OtpService.VerifyResult r;
        try {
            r = emailCodes.verify(request, email, in.get("code"));
        } catch (OtpService.OtpLimitException e) {
            return tooMany(e.getMessage(), e.retryAfter());
        }
        switch (r.outcome()) {
            case OK -> { }
            case INVALID -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("message", "That code isn't right.");
                body.put("errors", Map.of("code", List.of("That code isn't right. " + r.attemptsLeft() + (r.attemptsLeft() == 1 ? " try" : " tries") + " left.")));
                body.put("attemptsLeft", r.attemptsLeft());
                return json(HttpStatus.UNPROCESSABLE_ENTITY, body);
            }
            case EXPIRED -> { return error(HttpStatus.GONE, "This code has expired. Request a new one."); }
            case LOCKED -> { return error(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong codes. Request a new one."); }
            default -> { return error(HttpStatus.GONE, "There's no active code for this address. Request a new one."); }
        }
        Row user = accounts.findByEmail(email);
        long id;
        boolean created = false;
        if (user != null) {
            if (user.lng("status", 1) == 0) return error(HttpStatus.FORBIDDEN, "This account has been disabled. Please contact support.");
            id = user.lng("id");
            accounts.markEmailVerified(id);
        } else {
            id = accounts.create(trim(in.get("name"), 255), email, true, null, false, null, null);
            created = true;
            events.ok(request, id, "signup", "email_code");
            if (login.values().simulateEmail()) login.markSimulated(request, id, "email_code");
        }
        auth.login(request, id, "otp", in.get("remember") == null || in.bool("remember"));
        events.ok(request, id, "login", "email_code");
        ResponseEntity<String> res = signedIn(request, created);
        if (in.bool("returnTo")) {
            // the main log-in page: go where the visitor was heading (e.g. "Go to my course" in the thank-you e-mail)
            Map<String, Object> body = json.decodeMap(res.getBody());
            body.put("redirect", AuthController.takeReturnTo(request, com.artistikcity.http.GuardInterceptors.HOME));
            return json(HttpStatus.OK, body);
        }
        return res;
    }

    // ------------------------------------------------------------------ Google Identity Services

    /** {credential} = the ID token Google's one-tap / button handed the page. */
    @PostMapping("/auth/google/credential")
    public ResponseEntity<String> google(HttpServletRequest request) {
        String clientId = env.getProperty("social.google.client-id", "").trim();
        if (clientId.isEmpty()) return error(HttpStatus.NOT_FOUND, "Sign in with Google isn't available right now.");
        if (!limiter.hit("gis:ip:" + AuthEvents.ip(request), 30, 600).allowed()) {
            return tooMany("Too many sign-in attempts. Please wait a few minutes and try again.", 600);
        }
        Input in = Input.of(request, json);
        String credential = in.get("credential");
        HttpSession s = request.getSession(false);
        Object nonce = s == null ? null : s.getAttribute(GIS_NONCE);
        if (credential == null || credential.length() > 4096 || !(nonce instanceof String)) {
            return error(HttpStatus.BAD_REQUEST, "Google didn't send a sign-in. Please try again.");
        }
        Map<String, Object> c;
        try {
            c = jwt.verify(credential, env.getProperty("social.google.jwks-url", "https://www.googleapis.com/oauth2/v3/certs"),
                    List.of(env.getProperty("social.google.issuer", "https://accounts.google.com"), "accounts.google.com", "https://accounts.google.com"),
                    clientId, (String) nonce);
        } catch (JwtVerifier.InvalidTokenException | RuntimeException e) {     // a malformed token can fail anywhere in the parsing
            log.warn("Google one-tap token refused: {}", e.toString());
            events.fail(request, null, "oauth_exchange_failed", "google", e.getMessage());
            return error(HttpStatus.UNAUTHORIZED, "We couldn't confirm your Google sign-in. Please try again.");
        }
        s.removeAttribute(GIS_NONCE);   // single use: a new nonce comes with the next options call
        Object verified = c.get("email_verified");
        Map<String, Object> raw = new LinkedHashMap<>();
        for (String k : List.of("sub", "email", "email_verified", "name", "given_name", "family_name", "picture", "locale", "hd")) {
            if (c.get(k) != null) raw.put(k, c.get(k));
        }
        AccountService.ProviderProfile profile = new AccountService.ProviderProfile("google", String.valueOf(c.get("sub")), (String) c.get("email"),
                Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified)),
                (String) c.get("name"), (String) c.get("given_name"), (String) c.get("family_name"), (String) c.get("picture"), raw);
        try {
            AccountService.LinkResult r = accounts.signInWithProvider(request, profile, null);
            auth.login(request, r.userId(), "google", true);
            return signedIn(request, r.created());
        } catch (AccountService.AccountConflictException e) {
            events.fail(request, null, "oauth_conflict", "google", e.getMessage());
            return error(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ helpers

    /** The signed-in person, as the landing checkout shows it. */
    private ResponseEntity<String> signedIn(HttpServletRequest request, boolean created) {
        auth.refresh(request);
        Row u = auth.user(request);
        Map<String, Object> body = ok();
        body.put("created", created);
        body.put("user", u == null ? null : summary(u));
        return json(HttpStatus.OK, body);
    }

    public static Map<String, Object> summary(Row u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", u.str("name", ""));
        m.put("email", u.str("email", ""));
        m.put("phone", u.str("phone_number", u.str("phone", "")));
        return m;
    }

    private static String email(String raw) {
        String e = AccountService.normalizeEmail(raw);
        return e != null && e.length() <= 255 && e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") ? e : null;
    }

    private static String trim(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        return m;
    }

    private ResponseEntity<String> invalid(String field, String message) {
        return json(HttpStatus.UNPROCESSABLE_ENTITY, Map.of("message", message, "errors", Map.of(field, List.of(message))));
    }

    private ResponseEntity<String> tooMany(String message, long retryAfter) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", String.valueOf(retryAfter)).contentType(MediaType.APPLICATION_JSON)
                .body(json.encode(Map.of("message", message, "retryAfter", retryAfter)));
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        return json(status, Map.of("message", message));
    }

    private ResponseEntity<String> json(HttpStatus status, Object body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }
}
