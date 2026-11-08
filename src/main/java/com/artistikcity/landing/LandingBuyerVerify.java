package com.artistikcity.landing;

import com.artistikcity.auth.AccountService;
import com.artistikcity.auth.AuthEvents;
import com.artistikcity.auth.EmailCodeService;
import com.artistikcity.auth.LoginSettings;
import com.artistikcity.auth.OtpService;
import com.artistikcity.auth.PhoneNumbers;
import com.artistikcity.auth.Tokens;
import com.artistikcity.auth.sms.SmsSender;
import com.artistikcity.http.Auth;
import com.artistikcity.http.Flash;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.web.QuickSignInController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verify the buyer when they pay (Admin › Landing Page Management › Login & verification): a visitor sees the whole
 * landing page and its checkout without an account; pressing Pay asks for a six-digit code sent to their mobile (SMS)
 * or e-mail, and that code signs them in, creating their account when it is new. Then the payment opens.
 * <ul>
 *   <li>{@code POST /landing/checkout/{page}/verify/send} {channel, phone, email, name}: sends (or simulates) the code;
 *       what was asked for is kept in the server session, so the check can't be pointed at someone else.</li>
 *   <li>{@code POST /landing/checkout/{page}/verify/check} {code}: signs in (auth_sessions method otp).</li>
 *   <li>{@code GET /auth/continue?t=}: the "Log in to My studio" button of the congratulations screen: a single-use link
 *       bound to this browser's session (30 minutes), which signs the buyer in if needed and opens My studio.</li>
 * </ul>
 * An e-mail typed with a mobile number that belongs to another account is never attached to the new account: the code
 * then goes to that e-mail instead (or the buyer is asked to log in). Every POST here is outside /api: CSRF applies.
 */
@RestController
public class LandingBuyerVerify {

    private static final Logger log = LoggerFactory.getLogger(LandingBuyerVerify.class);
    static final String PENDING = "landing.verify";
    static final String CONTINUE = "landing.continue";
    private static final long PENDING_TTL = 15 * 60;
    private static final long CONTINUE_TTL = 30 * 60;

    private final Db db;
    private final Json json;
    private final Auth auth;
    private final AccountService accounts;
    private final OtpService otp;
    private final EmailCodeService emailCodes;
    private final LoginSettings login;
    private final LandingCommerce commerce;
    private final AuthEvents events;
    private final String country;

    public LandingBuyerVerify(Db db, Json json, Auth auth, AccountService accounts, OtpService otp, EmailCodeService emailCodes, LoginSettings login,
                              LandingCommerce commerce, AuthEvents events, Environment env) {
        this.db = db;
        this.json = json;
        this.auth = auth;
        this.accounts = accounts;
        this.otp = otp;
        this.emailCodes = emailCodes;
        this.login = login;
        this.commerce = commerce;
        this.events = events;
        this.country = env.getProperty("app.auth.default-country-code", "91");
    }

    // ------------------------------------------------------------------ what the page uses

    /** The page's way of verifying: its own choice (Checkout step) or the default. PHONE, EMAIL, EITHER or SIGNIN. */
    public String mode(long pageId) {
        String own = String.valueOf(commerce.checkoutView(pageId).get("verifyWith"));
        return "DEFAULT".equals(own) ? login.values().checkoutVerify() : own;
    }

    /** The channels a guest can verify with on this page right now (empty = sign in before the details, as before). */
    public List<String> channels(HttpServletRequest request, long pageId) {
        String mode = mode(pageId);
        List<String> out = new ArrayList<>();
        if (("PHONE".equals(mode) || "EITHER".equals(mode)) && login.phoneReady(request)) out.add("phone");
        if (("EMAIL".equals(mode) || "EITHER".equals(mode)) && login.emailReady(request)) out.add("email");
        return out;
    }

    /** For the checkout state: {mode, channels, simulated{phone, email}, country}. */
    public Map<String, Object> view(HttpServletRequest request, long pageId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", mode(pageId));
        m.put("channels", channels(request, pageId));
        LoginSettings.Values v = login.values();
        m.put("simulated", Map.of("phone", v.simulatePhone(), "email", v.simulateEmail()));
        m.put("country", country);
        return m;
    }

    // ------------------------------------------------------------------ send

    @PostMapping("/landing/checkout/{pageId}/verify/send")
    public ResponseEntity<String> send(HttpServletRequest request, @PathVariable("pageId") long pageId) {
        if (auth.user(request) != null) return json(HttpStatus.CONFLICT, Map.of("message", "You're already signed in.", "signedIn", true));
        if (db.count("select count(*) from landing_pages where id = ? and status = 'PUBLISHED' and course_id is not null", pageId) == 0) {
            return error(HttpStatus.GONE, "This page is no longer available.");
        }
        List<String> channels = channels(request, pageId);
        if (channels.isEmpty()) return json(HttpStatus.CONFLICT, Map.of("message", "Please log in or create your account to continue.", "signIn", true));
        Input in = Input.of(request, json);
        String channel = String.valueOf(in.get("channel"));
        if (!channels.contains(channel)) channel = channels.get(0);
        Map<String, String> errors = new LinkedHashMap<>();
        String name = trim(in.get("name"), 190);
        if (name.length() < 2) errors.put("name", "Enter your full name.");
        String email = AccountService.normalizeEmail(in.get("email"));
        if (email == null || email.length() > 255 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            errors.put("email", "Enter your e-mail: your receipt and course details go there.");
        }
        String phone = PhoneNumbers.normalize(in.get("phone"), country);
        if ("phone".equals(channel) && phone == null) {
            errors.put("phone", "Enter a valid mobile number" + ("91".equals(country) ? " (10 digits, or with the country code like +44…)." : "."));
        }
        if (!errors.isEmpty()) return json(HttpStatus.UNPROCESSABLE_ENTITY, Map.of("message", "Please check the highlighted fields.", "errors", errors));

        String notice = null;
        if ("phone".equals(channel)) {
            Row byEmail = accounts.findByEmail(email);
            Row byPhone = accounts.findByVerifiedPhone(phone);
            if (byEmail != null && (byPhone == null || byPhone.lng("id") != byEmail.lng("id"))) {
                // the e-mail is somebody's account: only a code to that inbox may open it
                if (!login.emailReady(request)) {
                    return json(HttpStatus.CONFLICT, Map.of("message", "This e-mail already has an ArtistikCity account. Please log in to it to continue.",
                            "code", "EMAIL_HAS_ACCOUNT", "signIn", true));
                }
                channel = "email";
                notice = "This e-mail already has an ArtistikCity account, so we sent the code to your e-mail.";
            }
        }
        LoginSettings.Decision how = "phone".equals(channel) ? login.phone(request, phone) : login.email(request, email);
        if (how.mode() == LoginSettings.Mode.REFUSE) {
            return json(HttpStatus.CONFLICT, Map.of("message", how.message(), "code", "CODE_UNAVAILABLE", "signIn", true));
        }
        boolean simulate = how.mode() == LoginSettings.Mode.SIMULATE;
        String code;
        int expiresIn;
        int resendIn;
        try {
            if ("phone".equals(channel)) {
                OtpService.SendResult r = otp.send(request, phone, "login", null, simulate);
                code = r.devCode();
                expiresIn = r.expiresIn();
                resendIn = r.resendIn();
            } else {
                EmailCodeService.SendResult r = emailCodes.send(request, email, name, simulate);
                code = r.devCode();
                expiresIn = r.expiresIn();
                resendIn = r.resendIn();
            }
        } catch (OtpService.OtpLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", String.valueOf(e.retryAfter())).contentType(MediaType.APPLICATION_JSON)
                    .body(json.encode(Map.of("message", e.getMessage(), "retryAfter", e.retryAfter())));
        } catch (SmsSender.SmsException e) {
            return error(HttpStatus.BAD_GATEWAY, "We couldn't send a text message right now. Please try again, or use your e-mail.");
        } catch (RuntimeException e) {
            log.warn("Checkout verification code could not be sent: {}", e.toString());
            return error(HttpStatus.BAD_GATEWAY, "We couldn't send the code right now. Please try again.");
        }
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("page", pageId);
        pending.put("channel", channel);
        pending.put("phone", phone);
        pending.put("email", email);
        pending.put("name", name);
        pending.put("simulated", simulate);
        pending.put("at", Instant.now().getEpochSecond());
        request.getSession(true).setAttribute(PENDING, pending);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("channel", channel);
        body.put("masked", "phone".equals(channel) ? PhoneNumbers.mask(phone) : maskEmail(email));
        body.put("expiresIn", expiresIn);
        body.put("resendIn", resendIn);
        body.put("simulated", simulate);
        if (code != null) body.put("code", code);       // simulated (Login & verification) or a development server, local browser
        if (notice != null) body.put("notice", notice);
        return json(HttpStatus.OK, body);
    }

    // ------------------------------------------------------------------ check

    @PostMapping("/landing/checkout/{pageId}/verify/check")
    @SuppressWarnings("unchecked")
    public ResponseEntity<String> check(HttpServletRequest request, @PathVariable("pageId") long pageId) {
        HttpSession s = request.getSession(false);
        Object p = s == null ? null : s.getAttribute(PENDING);
        if (!(p instanceof Map<?, ?> raw) || !Long.valueOf(pageId).equals(((Number) ((Map<String, Object>) raw).get("page")).longValue())
                || Instant.now().getEpochSecond() - ((Number) ((Map<String, Object>) raw).get("at")).longValue() > PENDING_TTL) {
            return error(HttpStatus.GONE, "This code has expired. Please ask for a new one.");
        }
        Map<String, Object> pending = (Map<String, Object>) raw;
        String channel = (String) pending.get("channel");
        String phone = (String) pending.get("phone");
        String email = (String) pending.get("email");
        String name = (String) pending.get("name");
        Input in = Input.of(request, json);
        OtpService.VerifyResult r;
        try {
            r = "phone".equals(channel) ? otp.verify(request, phone, in.get("code"), "login", null) : emailCodes.verify(request, email, in.get("code"));
        } catch (OtpService.OtpLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", String.valueOf(e.retryAfter())).contentType(MediaType.APPLICATION_JSON)
                    .body(json.encode(Map.of("message", e.getMessage(), "retryAfter", e.retryAfter())));
        }
        switch (r.outcome()) {
            case OK -> { }
            case INVALID -> {
                String text = "That code isn't right. " + r.attemptsLeft() + (r.attemptsLeft() == 1 ? " try" : " tries") + " left.";
                return json(HttpStatus.UNPROCESSABLE_ENTITY, Map.of("message", text, "errors", Map.of("code", text), "attemptsLeft", r.attemptsLeft()));
            }
            case EXPIRED -> { return error(HttpStatus.GONE, "This code has expired. Please ask for a new one."); }
            case LOCKED -> { return error(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong codes. Please ask for a new one."); }
            default -> { return error(HttpStatus.GONE, "There's no active code. Please ask for a new one."); }
        }
        boolean simulated = Boolean.TRUE.equals(pending.get("simulated"));
        long id;
        boolean created = false;
        if ("phone".equals(channel)) {
            Row user = accounts.findByVerifiedPhone(phone);
            if (user != null) {
                id = user.lng("id");
            } else {
                if (email != null && accounts.findByEmail(email) != null) {
                    s.removeAttribute(PENDING);
                    return json(HttpStatus.CONFLICT, Map.of("message", "This e-mail already has an ArtistikCity account. Please ask for a new code: it goes to that e-mail.",
                            "code", "EMAIL_HAS_ACCOUNT"));
                }
                // the number may sit unverified on another account (typed into a profile): it now belongs to this person
                Row typed = db.first("select id from users where phone_number = ?", phone);
                if (typed != null) {
                    db.update("update users set phone_number = null where id = ?", typed.lng("id"));
                    events.record(request, typed.lng("id"), "unverified_phone_released", "otp", true, PhoneNumbers.mask(phone));
                }
                id = accounts.create(name, email, false, phone, true, null, null);
                created = true;
            }
        } else {
            Row user = accounts.findByEmail(email);
            if (user != null) {
                id = user.lng("id");
                accounts.markEmailVerified(id);
            } else {
                id = accounts.create(name, email, true, null, false, null, null);
                created = true;
            }
        }
        Row u = db.first("select status from users where id = ?", id);
        if (u == null || u.lng("status", 1) == 0) {
            return error(HttpStatus.FORBIDDEN, "This account has been disabled. Please contact support.");
        }
        if (created) {
            events.ok(request, id, "signup", "phone".equals(channel) ? "otp" : "email_code");
            if (simulated) login.markSimulated(request, id, "phone".equals(channel) ? "otp" : "email_code");
        }
        s.removeAttribute(PENDING);
        auth.login(request, id, "otp", true);
        events.ok(request, id, "login", "phone".equals(channel) ? "otp" : "email_code");
        auth.refresh(request);
        Row me = auth.user(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("created", created);
        body.put("user", me == null ? null : QuickSignInController.summary(me));
        return json(HttpStatus.OK, body);
    }

    // ------------------------------------------------------------------ "Log in to My studio"

    /** A single-use link for this browser that signs the buyer in (if needed) and opens My studio. */
    public static String continueUrl(HttpServletRequest request, long userId) {
        HttpSession s = request.getSession(true);
        Object o = s.getAttribute(CONTINUE);
        long now = Instant.now().getEpochSecond();
        if (o instanceof Map<?, ?> m && Long.valueOf(userId).equals(m.get("uid")) && now - ((Number) m.get("at")).longValue() < CONTINUE_TTL - 60) {
            return "/auth/continue?t=" + m.get("t");
        }
        String t = Tokens.random(24);
        s.setAttribute(CONTINUE, Map.of("t", t, "uid", userId, "at", now));
        return "/auth/continue?t=" + t;
    }

    @GetMapping("/auth/continue")
    public ResponseEntity<String> continueToStudio(HttpServletRequest request, @RequestParam(value = "t", required = false) String t) {
        HttpSession s = request.getSession(false);
        Object o = s == null ? null : s.getAttribute(CONTINUE);
        if (t != null && o instanceof Map<?, ?> m && m.get("t") instanceof String saved && Tokens.constantTimeEquals(saved, t)
                && Instant.now().getEpochSecond() - ((Number) m.get("at")).longValue() < CONTINUE_TTL) {
            s.removeAttribute(CONTINUE);
            long uid = ((Number) m.get("uid")).longValue();
            Long current = auth.userId(request);
            if (current == null || current != uid) {
                Row u = db.first("select status from users where id = ?", uid);
                if (u != null && u.lng("status", 1) == 1) {
                    auth.login(request, uid, "otp", true);
                    events.ok(request, uid, "login", "continue_link");
                }
            }
            if (auth.userId(request) != null) {
                return redirect("/dashboard");
            }
        }
        if (auth.userId(request) != null) return redirect("/dashboard");
        Flash.put(request, "status", "Please log in to open My studio: use your mobile number or e-mail and a one-time code.");
        return redirect("/login?return_to=/dashboard");
    }

    // ------------------------------------------------------------------ helpers

    static String maskEmail(String e) {
        int at = e.indexOf('@');
        if (at <= 1) return e;
        return e.charAt(0) + "•••" + e.substring(at - 1);
    }

    private static String trim(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private ResponseEntity<String> json(HttpStatus status, Object body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        return json(status, Map.of("message", message));
    }

    private static ResponseEntity<String> redirect(String to) {
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", to).build();
    }
}
