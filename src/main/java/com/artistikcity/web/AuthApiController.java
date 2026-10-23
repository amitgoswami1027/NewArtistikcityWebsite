package com.artistikcity.web;

import com.artistikcity.auth.AccountService;
import com.artistikcity.auth.AccountService.AccountConflictException;
import com.artistikcity.auth.AuthEvents;
import com.artistikcity.auth.AuthMail;
import com.artistikcity.auth.EmailTokenService;
import com.artistikcity.auth.OtpService;
import com.artistikcity.auth.PasswordPolicy;
import com.artistikcity.auth.PhoneNumbers;
import com.artistikcity.auth.RateLimiter;
import com.artistikcity.auth.SessionService;
import com.artistikcity.auth.sms.SmsSender;
import com.artistikcity.auth.webauthn.WebAuthnService;
import com.artistikcity.http.Auth;
import com.artistikcity.http.GuardInterceptors;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.ValidationException;
import com.artistikcity.support.Validator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON endpoints behind the sign-in screens and the "Login & security" page. All are POST/DELETE under
 * /auth/ and therefore CSRF protected (the X-XSRF-TOKEN header that axios sends automatically).
 *
 * <ul>
 *   <li>SMS OTP: {@code /auth/otp/send}, {@code /auth/otp/verify}, {@code /auth/otp/complete} (new accounts: name step)</li>
 *   <li>Magic link: {@code /auth/magic-link} (request; the link itself is handled by {@link AuthController})</li>
 *   <li>Passkeys: {@code /auth/passkey/options} + {@code /auth/passkey/verify} (sign in);
 *       {@code /auth/passkey/register/options} + {@code /auth/passkey/register} + {@code DELETE /auth/passkey/{id}}</li>
 *   <li>Account security (signed in): phone, password, e-mail, connected accounts, sessions</li>
 * </ul>
 */
@RestController
public class AuthApiController {

    private static final String PENDING_PHONE = "auth.pending_phone_signup";
    private static final long PENDING_TTL = 600;
    /** sensitive changes need a login younger than this (otherwise: "sign in again") */
    private static final Duration RECENT_AUTH = Duration.ofMinutes(30);

    private final Auth auth;
    private final Db db;
    private final Json json;
    private final Validator validator;
    private final AccountService accounts;
    private final OtpService otp;
    private final AuthMail authMail;
    private final EmailTokenService emailTokens;
    private final WebAuthnService webauthn;
    private final SessionService sessions;
    private final RateLimiter limiter;
    private final AuthEvents events;
    private final String countryCode;

    public AuthApiController(Auth auth, Db db, Json json, Validator validator, AccountService accounts, OtpService otp, AuthMail authMail,
                             EmailTokenService emailTokens, WebAuthnService webauthn, SessionService sessions, RateLimiter limiter,
                             AuthEvents events, Environment env) {
        this.auth = auth;
        this.db = db;
        this.json = json;
        this.validator = validator;
        this.accounts = accounts;
        this.otp = otp;
        this.authMail = authMail;
        this.emailTokens = emailTokens;
        this.webauthn = webauthn;
        this.sessions = sessions;
        this.limiter = limiter;
        this.events = events;
        this.countryCode = env.getProperty("app.auth.default-country-code", "91");
    }

    // ================================================================== SMS OTP sign-in

    @PostMapping("/auth/otp/send")
    public ResponseEntity<String> otpSend(HttpServletRequest request) {
        Input in = Input.of(request, json);
        String phone = phone(in.get("phone"));
        try {
            OtpService.SendResult r = otp.send(request, phone, "login", null);
            Map<String, Object> body = ok();
            body.put("phone", phone);
            body.put("masked", PhoneNumbers.mask(phone));
            body.put("expiresIn", r.expiresIn());
            body.put("resendIn", r.resendIn());
            if (r.devCode() != null) {
                body.put("devCode", r.devCode()); // development only (SMS_PROVIDER=log + expose-dev-otp)
            }
            return json(HttpStatus.OK, body);
        } catch (OtpService.OtpLimitException e) {
            return tooMany(e.getMessage(), e.retryAfter());
        } catch (SmsSender.SmsException e) {
            return error(HttpStatus.BAD_GATEWAY, "We couldn't send a text message right now. Please try again, or sign in another way.");
        }
    }

    @PostMapping("/auth/otp/verify")
    public ResponseEntity<String> otpVerify(HttpServletRequest request) {
        Input in = Input.of(request, json);
        String phone = phone(in.get("phone"));
        validator.validate(in, "code", "required|max:10");
        OtpService.VerifyResult r;
        try {
            r = otp.verify(request, phone, in.get("code"), "login", null);
        } catch (OtpService.OtpLimitException e) {
            return tooMany(e.getMessage(), e.retryAfter());
        }
        ResponseEntity<String> failure = otpFailure(r);
        if (failure != null) {
            return failure;
        }
        Row user = accounts.findByVerifiedPhone(phone);
        if (user != null) {
            if (user.lng("status", 1) == 0) {
                return error(HttpStatus.FORBIDDEN, "This account has been disabled. Please contact support.");
            }
            auth.login(request, user.lng("id"), "otp", in.bool("remember"));
            return signedIn(request);
        }
        // a verified number without an account: ask for a name to finish creating it
        HttpSession s = request.getSession(true);
        s.setAttribute(PENDING_PHONE, Map.of("phone", phone, "at", Instant.now().getEpochSecond(), "remember", in.bool("remember")));
        Map<String, Object> body = ok();
        body.put("next", "profile");
        return json(HttpStatus.OK, body);
    }

    @PostMapping("/auth/otp/complete")
    @SuppressWarnings("unchecked")
    public ResponseEntity<String> otpComplete(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        Object p = s == null ? null : s.getAttribute(PENDING_PHONE);
        if (!(p instanceof Map<?, ?> pending) || Instant.now().getEpochSecond() - ((Number) pending.get("at")).longValue() > PENDING_TTL) {
            return error(HttpStatus.GONE, "This step has expired. Please enter your phone number again.");
        }
        Input in = Input.of(request, json);
        validator.validate(in, "name", "required|string|max:255", "email", "email|max:255");
        String phone = (String) pending.get("phone");
        String email = AccountService.normalizeEmail(in.get("email"));
        if (email != null && accounts.findByEmail(email) != null) {
            throw ValidationException.withMessage("email", "That e-mail already has an account. Sign in with it, then add your phone number in Login & security.");
        }
        long id;
        Row existing = accounts.findByVerifiedPhone(phone);
        if (existing != null) {
            id = existing.lng("id"); // finished in another tab meanwhile
        } else {
            // the number may sit unverified on another account (typed into a profile): it now belongs to this person
            Row typed = db.first("select id from users where phone_number = ?", phone);
            if (typed != null) {
                db.update("update users set phone_number = null where id = ?", typed.lng("id"));
                events.record(request, typed.lng("id"), "unverified_phone_released", "otp", true, phone);
            }
            id = accounts.create(in.get("name"), email, false, phone, true, null, null);
            events.ok(request, id, "signup", "otp");
            if (email != null) {
                authMail.sendVerification(request, id, email, in.get("name"));
            }
        }
        s.removeAttribute(PENDING_PHONE);
        auth.login(request, id, "otp", Boolean.TRUE.equals(pending.get("remember")));
        return signedIn(request);
    }

    // ================================================================== magic link request

    @PostMapping("/auth/magic-link")
    public ResponseEntity<String> magicLink(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "email", "required|email|max:255");
        String email = AccountService.normalizeEmail(in.get("email"));
        RateLimiter.Decision ip = limiter.hit("magic:ip:" + AuthEvents.ip(request), 10, 3600);
        if (!ip.allowed()) {
            return tooMany("Too many sign-in links requested from your network. Please try again later.", ip.retryAfterSeconds());
        }
        // per address: at most 3 mails per 15 minutes (counted in the database), silently - same answer either way
        if (emailTokens.recentCount(email, EmailTokenService.MAGIC_LINK, Duration.ofMinutes(15)) < 3) {
            Row user = accounts.findByEmail(email);
            if (user == null || user.lng("status", 1) != 0) {
                AuthController.rememberReturnTo(request);
                String returnTo = in.get("return_to");
                authMail.sendMagicLink(request, email, user == null ? null : user.str("name"), user == null ? null : user.lng("id"),
                        AuthController.isSafeReturnPath(returnTo) ? returnTo : null);
                events.ok(request, user == null ? null : user.lng("id"), "magic_link_sent", "magic_link");
            }
        }
        Map<String, Object> body = ok();
        body.put("message", "Check your inbox: we've sent a sign-in link to " + email + ". It expires in 15 minutes.");
        return json(HttpStatus.OK, body);
    }

    // ================================================================== passkey sign-in

    @PostMapping("/auth/passkey/options")
    public ResponseEntity<String> passkeyOptions(HttpServletRequest request) {
        RateLimiter.Decision d = limiter.hit("passkey:ip:" + AuthEvents.ip(request), 30, 600);
        if (!d.allowed()) {
            return tooMany("Too many attempts. Please wait a few minutes.", d.retryAfterSeconds());
        }
        return json(HttpStatus.OK, webauthn.authenticationOptions(request));
    }

    @PostMapping("/auth/passkey/verify")
    public ResponseEntity<String> passkeyVerify(HttpServletRequest request) {
        Input in = Input.of(request, json);
        Map<String, Object> credential = in.map("credential");
        if (credential == null) {
            return error(HttpStatus.BAD_REQUEST, "No passkey response received.");
        }
        try {
            long userId = webauthn.finishAuthentication(request, credential);
            auth.login(request, userId, "passkey", in.bool("remember"));
            return signedIn(request);
        } catch (WebAuthnService.WebAuthnException e) {
            events.fail(request, null, "passkey_failed", "passkey", e.getMessage());
            return error(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    // ================================================================== signed in: passkeys

    @PostMapping("/auth/passkey/register/options")
    public ResponseEntity<String> passkeyRegisterOptions(HttpServletRequest request) {
        Row user = requireUser(request);
        ResponseEntity<String> reauth = requireRecentAuth(request);
        if (reauth != null) {
            return reauth;
        }
        if (db.count("select count(*) from webauthn_credentials where user_id = ?", user.lng("id")) >= 20) {
            return error(HttpStatus.CONFLICT, "You already have 20 passkeys. Remove one first.");
        }
        return json(HttpStatus.OK, webauthn.registrationOptions(request, db.find("users", user.lng("id"))));
    }

    @PostMapping("/auth/passkey/register")
    public ResponseEntity<String> passkeyRegister(HttpServletRequest request) {
        Row user = requireUser(request);
        Input in = Input.of(request, json);
        Map<String, Object> credential = in.map("credential");
        if (credential == null) {
            return error(HttpStatus.BAD_REQUEST, "No passkey response received.");
        }
        try {
            long id = webauthn.finishRegistration(request, user.lng("id"), credential, in.get("name"));
            events.ok(request, user.lng("id"), "passkey_added", "passkey");
            Map<String, Object> body = ok();
            body.put("id", id);
            return json(HttpStatus.OK, body);
        } catch (WebAuthnService.WebAuthnException e) {
            events.fail(request, user.lng("id"), "passkey_add_failed", "passkey", e.getMessage());
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/auth/passkey/{id}")
    public ResponseEntity<String> passkeyDelete(HttpServletRequest request, @PathVariable("id") long id) {
        Row user = requireUser(request);
        ResponseEntity<String> reauth = requireRecentAuth(request);
        if (reauth != null) {
            return reauth;
        }
        Row c = db.first("select id from webauthn_credentials where id = ? and user_id = ?", id, user.lng("id"));
        if (c == null) {
            return error(HttpStatus.NOT_FOUND, "Passkey not found.");
        }
        if (accounts.methodsLeftAfterRemoving(user.lng("id"), null) <= 1) {
            return error(HttpStatus.CONFLICT, "This is your only way to sign in. Add another method before removing it.");
        }
        db.update("delete from webauthn_credentials where id = ?", id);
        events.ok(request, user.lng("id"), "passkey_removed", "passkey");
        return json(HttpStatus.OK, ok());
    }

    // ================================================================== signed in: phone number

    @PostMapping("/auth/phone/send")
    public ResponseEntity<String> phoneSend(HttpServletRequest request) {
        Row user = requireUser(request);
        ResponseEntity<String> reauth = requireRecentAuth(request);
        if (reauth != null) {
            return reauth;
        }
        String phone = phone(Input.of(request, json).get("phone"));
        Row other = accounts.findByVerifiedPhone(phone);
        if (other != null && other.lng("id") != user.lng("id")) {
            throw ValidationException.withMessage("phone", "This number is already verified on another account.");
        }
        try {
            OtpService.SendResult r = otp.send(request, phone, "verify_phone", user.lng("id"));
            Map<String, Object> body = ok();
            body.put("phone", phone);
            body.put("masked", PhoneNumbers.mask(phone));
            body.put("resendIn", r.resendIn());
            if (r.devCode() != null) {
                body.put("devCode", r.devCode());
            }
            return json(HttpStatus.OK, body);
        } catch (OtpService.OtpLimitException e) {
            return tooMany(e.getMessage(), e.retryAfter());
        } catch (SmsSender.SmsException e) {
            return error(HttpStatus.BAD_GATEWAY, "We couldn't send a text message right now. Please try again later.");
        }
    }

    @PostMapping("/auth/phone/verify")
    public ResponseEntity<String> phoneVerify(HttpServletRequest request) {
        Row user = requireUser(request);
        Input in = Input.of(request, json);
        String phone = phone(in.get("phone"));
        validator.validate(in, "code", "required|max:10");
        OtpService.VerifyResult r;
        try {
            r = otp.verify(request, phone, in.get("code"), "verify_phone", user.lng("id"));
        } catch (OtpService.OtpLimitException e) {
            return tooMany(e.getMessage(), e.retryAfter());
        }
        ResponseEntity<String> failure = otpFailure(r);
        if (failure != null) {
            return failure;
        }
        try {
            accounts.assignVerifiedPhone(request, user.lng("id"), phone);
        } catch (AccountConflictException e) {
            return error(HttpStatus.CONFLICT, e.getMessage());
        }
        events.ok(request, user.lng("id"), "phone_verified", "otp");
        return json(HttpStatus.OK, ok());
    }

    // ================================================================== signed in: password / e-mail

    @PostMapping("/auth/password")
    public ResponseEntity<String> setPassword(HttpServletRequest request) {
        Row user = requireUser(request);
        Input in = Input.of(request, json);
        validator.validate(in, "password", "required|confirmed|min:8|max:128");
        Row u = db.first("select email, name, password_hash from users where id = ?", user.lng("id"));
        if (u.get("password_hash") != null) {
            RateLimiter.Decision d = limiter.hit("password:change:" + user.lng("id"), 5, 900);
            if (!d.allowed()) {
                return tooMany("Too many attempts. Please wait a few minutes.", d.retryAfterSeconds());
            }
            if (!auth.check(in.get("current_password"), u.str("password_hash"))) {
                throw ValidationException.withMessage("current_password", "Your current password isn't correct.");
            }
        } else {
            ResponseEntity<String> reauth = requireRecentAuth(request);
            if (reauth != null) {
                return reauth;
            }
        }
        String problem = PasswordPolicy.problem(in.get("password"), u.str("email"), u.str("name"));
        if (problem != null) {
            throw ValidationException.withMessage("password", problem);
        }
        db.updateById("users", user.lng("id"), Row.of("password_hash", auth.hash(in.get("password"))));
        int ended = sessions.revokeAll(user.lng("id"), currentSessionId(request), "password_changed");
        events.ok(request, user.lng("id"), u.get("password_hash") == null ? "password_set" : "password_changed", "password");
        Map<String, Object> body = ok();
        body.put("signedOutOthers", ended);
        return json(HttpStatus.OK, body);
    }

    @PostMapping("/auth/email")
    public ResponseEntity<String> changeEmail(HttpServletRequest request) {
        Row user = requireUser(request);
        ResponseEntity<String> reauth = requireRecentAuth(request);
        if (reauth != null) {
            return reauth;
        }
        Input in = Input.of(request, json);
        validator.validate(in, "email", "required|email|max:255");
        String email = AccountService.normalizeEmail(in.get("email"));
        Row other = accounts.findByEmail(email);
        if (other != null && other.lng("id") != user.lng("id")) {
            throw ValidationException.withMessage("email", "That e-mail is already used by another account.");
        }
        if (!email.equals(user.str("email"))) {
            db.updateById("users", user.lng("id"), Row.of("email", email, "email_verified_at", null));
            events.ok(request, user.lng("id"), "email_changed", null);
        }
        if (emailTokens.recentCount(email, EmailTokenService.VERIFY_EMAIL, Duration.ofHours(1)) < 3) {
            authMail.sendVerification(request, user.lng("id"), email, user.str("name"));
        }
        auth.refresh(request);
        Map<String, Object> body = ok();
        body.put("message", "We've sent a confirmation link to " + email + ".");
        return json(HttpStatus.OK, body);
    }

    // ================================================================== signed in: connected accounts / sessions

    @DeleteMapping("/auth/linked/{provider}")
    public ResponseEntity<String> unlink(HttpServletRequest request, @PathVariable("provider") String provider) {
        Row user = requireUser(request);
        ResponseEntity<String> reauth = requireRecentAuth(request);
        if (reauth != null) {
            return reauth;
        }
        try {
            accounts.unlink(request, user.lng("id"), provider);
            return json(HttpStatus.OK, ok());
        } catch (AccountConflictException e) {
            return error(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/auth/sessions/{id}/revoke")
    public ResponseEntity<String> revokeSession(HttpServletRequest request, @PathVariable("id") long id) {
        Row user = requireUser(request);
        int n = db.update("update auth_sessions set revoked_at = ?, revoke_reason = 'user_revoked' where id = ? and user_id = ? and revoked_at is null",
                LocalDateTime.now(), id, user.lng("id"));
        events.ok(request, user.lng("id"), "session_revoked", null);
        if (Long.valueOf(id).equals(currentSessionId(request))) {
            auth.logout(request);
        }
        return n == 1 ? json(HttpStatus.OK, ok()) : error(HttpStatus.NOT_FOUND, "Session not found.");
    }

    @PostMapping("/auth/sessions/revoke-others")
    public ResponseEntity<String> revokeOthers(HttpServletRequest request) {
        Row user = requireUser(request);
        int n = sessions.revokeAll(user.lng("id"), currentSessionId(request), "user_revoked_all");
        events.ok(request, user.lng("id"), "sessions_revoked", null);
        Map<String, Object> body = ok();
        body.put("revoked", n);
        return json(HttpStatus.OK, body);
    }

    /** "Sign in again" for sensitive changes: ends this login and sends the user to the login page. */
    @PostMapping("/auth/reauthenticate")
    public ResponseEntity<String> reauthenticate(HttpServletRequest request) {
        auth.logout(request);
        Map<String, Object> body = ok();
        body.put("redirect", "/login?return_to=%2Fuser%2Flinked-accounts");
        return json(HttpStatus.OK, body);
    }

    // ================================================================== helpers

    private String phone(String input) {
        String phone = PhoneNumbers.normalize(input, countryCode);
        if (phone == null) {
            throw ValidationException.withMessage("phone", "Enter a valid mobile number" + ("91".equals(countryCode) ? " (10 digits, or with country code like +44...)." : "."));
        }
        return phone;
    }

    private ResponseEntity<String> otpFailure(OtpService.VerifyResult r) {
        return switch (r.outcome()) {
            case OK -> null;
            case INVALID -> {
                Map<String, Object> errors = new LinkedHashMap<>();
                errors.put("code", java.util.List.of("That code isn't right. " + r.attemptsLeft() + (r.attemptsLeft() == 1 ? " try" : " tries") + " left."));
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("message", "That code isn't right.");
                body.put("errors", errors);
                body.put("attemptsLeft", r.attemptsLeft());
                yield json(HttpStatus.UNPROCESSABLE_ENTITY, body);
            }
            case EXPIRED -> error(HttpStatus.GONE, "This code has expired. Request a new one.");
            case LOCKED -> error(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong codes. Request a new one.");
            case NONE -> error(HttpStatus.GONE, "There's no active code for this number. Request a new one.");
        };
    }

    private ResponseEntity<String> signedIn(HttpServletRequest request) {
        Map<String, Object> body = ok();
        body.put("redirect", AuthController.takeReturnTo(request, GuardInterceptors.HOME));
        return json(HttpStatus.OK, body);
    }

    private Row requireUser(HttpServletRequest request) {
        Row u = auth.user(request);
        if (u == null) {
            throw new UnauthorizedException();
        }
        return u;
    }

    private Long currentSessionId(HttpServletRequest request) {
        SessionService.AuthContext c = auth.context(request);
        return c == null ? null : c.sessionId();
    }

    /** Sensitive changes need a recent login, so a stolen, long-lived session can't take the account over. */
    private ResponseEntity<String> requireRecentAuth(HttpServletRequest request) {
        Long sid = currentSessionId(request);
        Row s = sid == null ? null : db.first("select created_at from auth_sessions where id = ?", sid);
        if (s != null && LocalDateTime.parse(s.str("created_at").replace(' ', 'T')).plus(RECENT_AUTH).isAfter(LocalDateTime.now())) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "For your security, please sign in again to make this change.");
        body.put("reauth", true);
        return json(HttpStatus.FORBIDDEN, body);
    }

    private static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        return m;
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        return json(status, body);
    }

    private ResponseEntity<String> tooMany(String message, long retryAfter) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("retryAfter", retryAfter);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", String.valueOf(retryAfter))
                .contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    private ResponseEntity<String> json(HttpStatus status, Object body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    /** Signed-out call to an endpoint that needs a login. */
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException() {
            super("Please sign in.");
        }
    }
}
