package com.artistikcity.web.admin;

import com.artistikcity.admin.AdminKit;
import com.artistikcity.admin.Audit;
import com.artistikcity.auth.AuthEvents;
import com.artistikcity.auth.RateLimiter;
import com.artistikcity.http.Auth;
import com.artistikcity.http.Flash;
import com.artistikcity.security.mfa.AdminMfa;
import com.artistikcity.security.mfa.QrCode;
import com.artistikcity.security.mfa.Totp;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.view.Views;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Two-step verification for staff (see {@link AdminMfa}).
 * <p><b>Sign-in</b> (server-rendered, admin/mfa-*.mustache): after a correct password the session only holds a
 * <i>pending</i> sign-in (10 minutes, 5 wrong codes, then back to the password); the admin session starts when the
 * authenticator / recovery code is right ({@code /admin/login/verify}), or, when MFA is compulsory and not set up yet,
 * after scanning the QR code and confirming a code ({@code /admin/login/setup}; recovery codes shown once).</p>
 * <p><b>Account › Security</b> ({@code /admin/account/security}, page Admin/Account/Security): every staff member sets
 * up, replaces or turns off their own MFA (turning off / new recovery codes need a current code), and forgets trusted
 * browsers. Super admins (capability {@code setup}) make it compulsory for all staff and reset a colleague's MFA.
 * Everything is in the security log.</p>
 */
@Controller
public class AdminMfaController {

    static final String PENDING = "admin.mfa.pending";
    static final String SETUP = "admin.mfa.setup";
    private static final long PENDING_TTL = 600;
    private static final int MAX_TRIES = 5;
    private static final String ISSUER = "ArtistikCity";

    private final Db db;
    private final Auth auth;
    private final Views views;
    private final Json json;
    private final AdminMfa mfa;
    private final AdminKit kit;
    private final Audit audit;
    private final AuthEvents events;
    private final RateLimiter limiter;

    public AdminMfaController(Db db, Auth auth, Views views, Json json, AdminMfa mfa, AdminKit kit, Audit audit, AuthEvents events, RateLimiter limiter) {
        this.db = db;
        this.auth = auth;
        this.views = views;
        this.json = json;
        this.mfa = mfa;
        this.kit = kit;
        this.audit = audit;
        this.events = events;
        this.limiter = limiter;
    }

    // =================================================================== sign-in: second step

    /** Called by the password step: remembers the pending sign-in and sends the browser to the code (or set-up) page. */
    ResponseEntity<String> start(HttpServletRequest request, long adminId, boolean setup) {
        request.getSession(true);
        request.changeSessionId();
        HttpSession s = request.getSession();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", adminId);
        p.put("at", Instant.now().getEpochSecond());
        p.put("tries", 0);
        p.put("setup", setup);
        if (setup) p.put("secret", Totp.newSecret());
        s.setAttribute(PENDING, p);
        events.admin(request, row(adminId), setup ? "admin_mfa_setup_required" : "admin_mfa_challenge", true, null);
        return redirect(setup ? "/admin/login/setup" : "/admin/login/verify");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pending(HttpServletRequest request, boolean setup) {
        HttpSession s = request.getSession(false);
        Object o = s == null ? null : s.getAttribute(PENDING);
        if (!(o instanceof Map<?, ?> m)) return null;
        Map<String, Object> p = (Map<String, Object>) m;
        if (Instant.now().getEpochSecond() - ((Number) p.get("at")).longValue() > PENDING_TTL || setup != Boolean.TRUE.equals(p.get("setup"))) {
            if (Instant.now().getEpochSecond() - ((Number) p.get("at")).longValue() > PENDING_TTL) s.removeAttribute(PENDING);
            return null;
        }
        return p;
    }

    private ResponseEntity<String> backToPassword(HttpServletRequest request, String message) {
        HttpSession s = request.getSession(false);
        if (s != null) s.removeAttribute(PENDING);
        Flash.putErrors(request, Map.of("email", message));
        return redirect("/admin/login");
    }

    @GetMapping("/admin/login/verify")
    public ResponseEntity<String> verifyPage(HttpServletRequest request, @RequestParam(value = "recovery", required = false) String recovery) {
        if (auth.admin(request) != null) return redirect("/admin/dashboard");
        Map<String, Object> p = pending(request, false);
        if (p == null) return backToPassword(request, "Your sign-in timed out. Please enter your password again.");
        return codePage(request, p, recovery != null, null, HttpStatus.OK);
    }

    private ResponseEntity<String> codePage(HttpServletRequest request, Map<String, Object> p, boolean recovery, String error, HttpStatus status) {
        Row a = row(((Number) p.get("id")).longValue());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", a == null ? "" : a.str("email"));
        m.put("recovery", recovery);
        m.put("error", error);
        m.put("trustDays", AdminMfa.TRUST_DAYS);
        return views.page(request, "admin/mfa-verify", m, status);
    }

    @PostMapping("/admin/login/verify")
    public ResponseEntity<String> verify(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> p = pending(request, false);
        if (p == null) return backToPassword(request, "Your sign-in timed out. Please enter your password again.");
        long id = ((Number) p.get("id")).longValue();
        Input in = Input.of(request, json);
        boolean recovery = in.get("recovery") != null;
        RateLimiter.Decision d = limiter.hit("admin:mfa:" + id, 10, 900);
        if (!d.allowed()) {
            events.admin(request, row(id), "admin_mfa_locked", false, "too many codes");
            return backToPassword(request, "Too many wrong codes. Please wait " + Math.max(1, d.retryAfterSeconds() / 60) + " minutes and sign in again.");
        }
        AdminMfa.Result r = mfa.verify(id, in.get("code"));
        if (r == AdminMfa.Result.OK || r == AdminMfa.Result.RECOVERY_USED) {
            request.getSession().removeAttribute(PENDING);
            limiter.reset("admin:mfa:" + id);
            auth.loginAdmin(request, id);
            if (in.bool("trust")) mfa.trust(request, response, id);
            Row a = auth.admin(request);
            events.admin(request, a, "admin_login", true, r == AdminMfa.Result.OK ? "two-step verification" : "recovery code");
            if (r == AdminMfa.Result.RECOVERY_USED) {
                long left = mfa.recoveryCodesLeft(id);
                Flash.put(request, "warning", "You signed in with a recovery code. " + left + " left: make new ones in Account › Security.");
                audit.log(a, "admin", id, "update", "Signed in with a recovery code (" + left + " left)");
            }
            return redirect("/admin/dashboard");
        }
        int tries = ((Number) p.get("tries")).intValue() + 1;
        p.put("tries", tries);
        events.admin(request, row(id), "admin_mfa_failed", false, r == AdminMfa.Result.REPLAY ? "code already used" : recovery ? "wrong recovery code" : "wrong code");
        if (tries >= MAX_TRIES) {
            return backToPassword(request, "Too many wrong codes. Please sign in again.");
        }
        String msg = r == AdminMfa.Result.REPLAY ? "That code was already used. Wait for the next one in your app."
                : recovery ? "That recovery code isn't right (or was used before)." : "That code isn't right. Check the time on your phone is set automatically.";
        return codePage(request, p, recovery, msg + " " + (MAX_TRIES - tries) + (MAX_TRIES - tries == 1 ? " try" : " tries") + " left.", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @GetMapping("/admin/login/setup")
    public ResponseEntity<String> setupPage(HttpServletRequest request) {
        if (auth.admin(request) != null) return redirect("/admin/dashboard");
        Map<String, Object> p = pending(request, true);
        if (p == null) return backToPassword(request, "Your sign-in timed out. Please enter your password again.");
        return setupView(request, p, null, HttpStatus.OK);
    }

    private ResponseEntity<String> setupView(HttpServletRequest request, Map<String, Object> p, String error, HttpStatus status) {
        Row a = row(((Number) p.get("id")).longValue());
        String email = a == null ? "" : a.str("email");
        String secret = (String) p.get("secret");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", email);
        m.put("required", mfa.required());
        m.put("qr", QrCode.encode(Totp.uri(ISSUER, email, secret)).toSvg(2, "QR code for your authenticator app"));
        m.put("secretGrouped", secret.replaceAll("(.{4})(?!$)", "$1 "));
        m.put("error", error);
        return views.page(request, "admin/mfa-setup", m, status);
    }

    @PostMapping("/admin/login/setup")
    public ResponseEntity<String> setup(HttpServletRequest request) {
        Map<String, Object> p = pending(request, true);
        if (p == null) return backToPassword(request, "Your sign-in timed out. Please enter your password again.");
        long id = ((Number) p.get("id")).longValue();
        if (!limiter.hit("admin:mfa:" + id, 10, 900).allowed()) return backToPassword(request, "Too many wrong codes. Please wait and sign in again.");
        Input in = Input.of(request, json);
        String secret = (String) p.get("secret");
        if (!AdminMfa.matchesNew(secret, in.get("code"))) {
            int tries = ((Number) p.get("tries")).intValue() + 1;
            p.put("tries", tries);
            if (tries >= MAX_TRIES) return backToPassword(request, "Too many wrong codes. Please sign in again.");
            return setupView(request, p, "That code isn't right. Scan the QR code again, or check the time on your phone is set automatically.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        List<String> codes = mfa.enable(id, secret);
        request.getSession().removeAttribute(PENDING);
        limiter.reset("admin:mfa:" + id);
        auth.loginAdmin(request, id);
        Row a = auth.admin(request);
        audit.log(a, "admin", id, "update", "Two-step verification turned on (at sign-in)");
        events.admin(request, a, "admin_login", true, "two-step verification set up");
        return views.page(request, "admin/mfa-codes", Map.of("codes", codes, "next", "/admin/dashboard"), HttpStatus.OK);
    }

    @GetMapping("/admin/login/cancel")
    public ResponseEntity<String> cancel(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        if (s != null) s.removeAttribute(PENDING);
        return redirect("/admin/login");
    }

    // =================================================================== Account › Security

    @GetMapping("/admin/account/security")
    public ResponseEntity<String> page(HttpServletRequest request) {
        return kit.page(request, "Admin/Account/Security", Map.of());
    }

    @GetMapping("/admin/api/account/mfa")
    @ResponseBody
    public ResponseEntity<String> status(HttpServletRequest request) {
        Row a = kit.admin(request);
        return kit.ok(view(request, a));
    }

    private Map<String, Object> view(HttpServletRequest request, Row a) {
        long id = a.lng("id");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", mfa.available());
        m.put("schema", mfa.schemaReady());
        m.put("enabled", mfa.enabled(id));
        Row r = mfa.schemaReady() ? db.first("select mfa_enabled_at from admins where id = ?", id) : null;
        m.put("enabledAt", r == null ? null : r.get("mfa_enabled_at"));
        m.put("recoveryLeft", mfa.recoveryCodesLeft(id));
        m.put("required", mfa.required());
        m.put("trusted", mfa.trusted(request, id));
        boolean boss = kit.can(a, "setup");
        m.put("canManage", boss);
        if (boss && mfa.schemaReady()) {
            m.put("staff", db.select("select id, name, email, mfa_enabled_at as enabled_at from admins order by name"));
        }
        return m;
    }

    /** Starts setting up: a new secret, kept in the session until a code confirms it. */
    @PostMapping("/admin/api/account/mfa/setup")
    @ResponseBody
    public ResponseEntity<String> beginSetup(HttpServletRequest request) {
        Row a = kit.admin(request);
        if (!mfa.available()) return kit.fail(HttpStatus.CONFLICT, "Two-step verification needs the server's encryption key (CONFIG_ENCRYPTION_KEY or OTP_SECRET) and the database update 2026-11-13_admin_mfa.sql.");
        if (mfa.enabled(a.lng("id"))) return kit.fail(HttpStatus.CONFLICT, "Two-step verification is already on.");
        String secret = Totp.newSecret();
        request.getSession().setAttribute(SETUP, secret);
        String uri = Totp.uri(ISSUER, a.str("email", ""), secret);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("secret", secret);
        body.put("secretGrouped", secret.replaceAll("(.{4})(?!$)", "$1 "));
        body.put("uri", uri);
        body.put("qr", QrCode.encode(uri).toSvg(2, "QR code for your authenticator app"));
        return kit.ok(body);
    }

    @PostMapping("/admin/api/account/mfa/enable")
    @ResponseBody
    public ResponseEntity<String> enable(HttpServletRequest request) {
        Row a = kit.admin(request);
        long id = a.lng("id");
        Object secret = request.getSession().getAttribute(SETUP);
        if (!(secret instanceof String sec)) return kit.fail(HttpStatus.GONE, "Start again: scan a new QR code.");
        if (!limiter.hit("admin:mfa:" + id, 10, 900).allowed()) return kit.fail(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong codes. Please wait a few minutes.");
        if (!AdminMfa.matchesNew(sec, Input.of(request, json).get("code"))) return kit.invalid(Map.of("code", "That code isn't right. Check the time on your phone is set automatically."));
        List<String> codes = mfa.enable(id, sec);
        request.getSession().removeAttribute(SETUP);
        limiter.reset("admin:mfa:" + id);
        audit.log(a, "admin", id, "update", "Two-step verification turned on");
        Map<String, Object> body = view(request, a);
        body.put("recoveryCodes", codes);
        body.put("message", "Two-step verification is on. Save your recovery codes now.");
        return kit.ok(body);
    }

    /** {code}: a current authenticator or recovery code is needed to turn it off or make new recovery codes. */
    private ResponseEntity<String> confirmCode(HttpServletRequest request, Row a) {
        long id = a.lng("id");
        if (!mfa.enabled(id)) return kit.fail(HttpStatus.CONFLICT, "Two-step verification is off.");
        if (!limiter.hit("admin:mfa:" + id, 10, 900).allowed()) return kit.fail(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong codes. Please wait a few minutes.");
        AdminMfa.Result r = mfa.verify(id, Input.of(request, json).get("code"));
        if (r != AdminMfa.Result.OK && r != AdminMfa.Result.RECOVERY_USED) {
            events.admin(request, a, "admin_mfa_failed", false, "account security: wrong code");
            return kit.invalid(Map.of("code", r == AdminMfa.Result.REPLAY ? "That code was already used. Wait for the next one." : "That code isn't right."));
        }
        return null;
    }

    @PostMapping("/admin/api/account/mfa/disable")
    @ResponseBody
    public ResponseEntity<String> disable(HttpServletRequest request, HttpServletResponse response) {
        Row a = kit.admin(request);
        if (mfa.required()) return kit.fail(HttpStatus.CONFLICT, "Two-step verification is required for all staff, so it can't be turned off. You can set it up again on a new phone instead (ask a super admin to reset it).");
        ResponseEntity<String> bad = confirmCode(request, a);
        if (bad != null) return bad;
        mfa.disable(a.lng("id"));
        AdminMfa.forgetCookie(response);
        audit.log(a, "admin", a.lng("id"), "update", "Two-step verification turned off");
        Map<String, Object> body = view(request, a);
        body.put("message", "Two-step verification is off.");
        return kit.ok(body);
    }

    @PostMapping("/admin/api/account/mfa/recovery-codes")
    @ResponseBody
    public ResponseEntity<String> recoveryCodes(HttpServletRequest request) {
        Row a = kit.admin(request);
        ResponseEntity<String> bad = confirmCode(request, a);
        if (bad != null) return bad;
        List<String> codes = mfa.newRecoveryCodes(a.lng("id"));
        audit.log(a, "admin", a.lng("id"), "update", "New two-step recovery codes made (the old ones stopped working)");
        Map<String, Object> body = view(request, a);
        body.put("recoveryCodes", codes);
        body.put("message", "New recovery codes made. The old ones no longer work.");
        return kit.ok(body);
    }

    @PostMapping("/admin/api/account/mfa/forget-browsers")
    @ResponseBody
    public ResponseEntity<String> forgetBrowsers(HttpServletRequest request, HttpServletResponse response) {
        Row a = kit.admin(request);
        mfa.forgetTrustedBrowsers(a.lng("id"));
        AdminMfa.forgetCookie(response);
        audit.log(a, "admin", a.lng("id"), "update", "Trusted browsers forgotten (two-step verification asked again everywhere)");
        Map<String, Object> body = view(request, a);
        body.put("message", "Every browser will ask for a code again.");
        return kit.ok(body);
    }

    /** {required}: super admins only. Switching it on needs your own MFA on first, so you can't lock yourself out. */
    @PutMapping("/admin/api/setup/mfa-policy")
    @ResponseBody
    public ResponseEntity<String> policy(HttpServletRequest request) {
        Row a = kit.admin(request);
        if (!kit.can(a, "setup")) return kit.forbidden();
        boolean on = Input.of(request, json).bool("required");
        if (on && !mfa.available()) return kit.fail(HttpStatus.CONFLICT, "Needs the server's encryption key (CONFIG_ENCRYPTION_KEY or OTP_SECRET) and the database update 2026-11-13_admin_mfa.sql.");
        if (on && !mfa.enabled(a.lng("id"))) return kit.fail(HttpStatus.CONFLICT, "Turn on two-step verification for your own account first.");
        boolean before = mfa.requiredSetting();
        mfa.setRequired(on);
        audit.change(a, "settings", 0, "update", on ? "Two-step verification required for all staff" : "Two-step verification optional for staff",
                Map.of("admin_mfa_required", before), Map.of("admin_mfa_required", on));
        Map<String, Object> body = view(request, a);
        body.put("message", on ? "Two-step verification is now required: staff without it set it up at their next sign-in." : "Two-step verification is optional again.");
        return kit.ok(body);
    }

    /** Lost phone: a super admin turns a colleague's MFA off (they set it up again at the next sign-in when required). */
    @PostMapping("/admin/api/setup/mfa/{adminId}/reset")
    @ResponseBody
    public ResponseEntity<String> reset(HttpServletRequest request, @PathVariable("adminId") long adminId) {
        Row a = kit.admin(request);
        if (!kit.can(a, "setup")) return kit.forbidden();
        if (adminId == a.lng("id")) return kit.fail(HttpStatus.CONFLICT, "Use your own account security settings for your account.");
        Row t = row(adminId);
        if (t == null) return kit.fail(HttpStatus.NOT_FOUND, "Staff member not found.");
        mfa.disable(adminId);
        audit.log(a, "admin", adminId, "update", "Two-step verification reset for " + t.str("name", "") + " <" + t.str("email", "") + ">");
        Map<String, Object> body = view(request, a);
        body.put("message", "Reset. " + t.str("name", "They") + (mfa.required() ? " sets up two-step verification again at the next sign-in." : " can set it up again in Account › Security."));
        return kit.ok(body);
    }

    // =================================================================== helpers

    private Row row(long adminId) {
        return db.first("select id, name, email from admins where id = ?", adminId);
    }

    private static ResponseEntity<String> redirect(String to) {
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", to).build();
    }
}
