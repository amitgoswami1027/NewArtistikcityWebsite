package com.artistikcity.security.mfa;

import com.artistikcity.auth.Tokens;
import com.artistikcity.config.panel.PanelConfig;
import com.artistikcity.config.panel.SecretBox;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Two-step verification (MFA) for the admin console: an authenticator app (TOTP, RFC 6238) plus ten single-use
 * recovery codes.
 * <ul>
 *   <li>The TOTP secret is stored encrypted with the Configuration Panel key ({@link PanelConfig#secretBox()},
 *       AES-256-GCM, CONFIG_ENCRYPTION_KEY or OTP_SECRET); without that key MFA can't be switched on.</li>
 *   <li>Recovery codes are stored as SHA-256 only and work once.</li>
 *   <li>A code's time step is remembered ({@code admins.mfa_last_step}), so a code can't be used twice.</li>
 *   <li>"Trust this browser for 30 days": an HttpOnly cookie {@code ac_mfa_trust} = admin id, expiry and an HMAC over
 *       them and {@code mfa_changed_at} (keyed by the server secret), so turning MFA off / on or "forget trusted
 *       browsers" makes every such cookie worthless.</li>
 *   <li>Policy: settings tag {@code system}, key {@code admin_mfa_required} = 1 makes it compulsory for every staff
 *       account; an account without it is taken through the set-up right after its password at the next sign-in.</li>
 * </ul>
 * Columns {@code admins.mfa_secret|mfa_enabled_at|mfa_last_step|mfa_changed_at}, table {@code admin_mfa_recovery_codes}
 * (database/migrations/2026-11-13_admin_mfa.sql).
 */
@Service
public class AdminMfa {

    public static final String TRUST_COOKIE = "ac_mfa_trust";
    public static final int TRUST_DAYS = 30;
    public static final int RECOVERY_CODES = 10;

    private final Db db;
    private final PanelConfig panel;
    private final byte[] hmacKey;
    private volatile Boolean required;
    private volatile long requiredAt;
    private volatile Boolean ready;

    public AdminMfa(Db db, PanelConfig panel, Environment env) {
        this.db = db;
        this.panel = panel;
        String s = env.getProperty("app.auth.otp-secret", "");
        this.hmacKey = s.isBlank() ? Tokens.randomBytes(32) : ("admin-mfa|" + s).getBytes(StandardCharsets.UTF_8);
    }

    /** The migration is applied (otherwise MFA simply isn't offered and sign-in works as before). */
    public boolean schemaReady() {
        if (ready == null || !ready) {
            try {
                ready = db.count("select count(*) from information_schema.columns where table_schema = database() and table_name = 'admins' and column_name = 'mfa_secret'") > 0
                        && db.count("select count(*) from information_schema.tables where table_schema = database() and table_name = 'admin_mfa_recovery_codes'") > 0;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return ready;
    }

    /** Secrets can be stored (the server has an encryption key) and the schema is there. */
    public boolean available() {
        return schemaReady() && panel.secretBox() != null;
    }

    public boolean enabled(long adminId) {
        if (!schemaReady()) return false;
        Row r = db.first("select mfa_enabled_at, mfa_secret from admins where id = ?", adminId);
        return r != null && r.get("mfa_enabled_at") != null && r.get("mfa_secret") != null;
    }

    // ------------------------------------------------------------------ policy

    public boolean required() {
        if (!schemaReady()) return false;
        if (required == null || System.currentTimeMillis() - requiredAt > 10_000) {
            Row r = db.first("select meta_value from settings where meta_tag = 'system' and meta_key = 'admin_mfa_required' order by id desc limit 1");
            required = r != null && "1".equals(r.str("meta_value"));
            requiredAt = System.currentTimeMillis();
        }
        return required && available();
    }

    public boolean requiredSetting() {
        required();
        return Boolean.TRUE.equals(required);
    }

    public void setRequired(boolean on) {
        LocalDateTime now = LocalDateTime.now();
        if (db.update("update settings set meta_value = ?, updated_at = ? where meta_tag = 'system' and meta_key = 'admin_mfa_required'", on ? "1" : "0", now) == 0) {
            db.insert("settings", Row.of("meta_tag", "system", "meta_key", "admin_mfa_required", "meta_value", on ? "1" : "0", "created_at", now, "updated_at", now));
        }
        required = on;
        requiredAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ set-up

    /** Saves a confirmed secret, switches MFA on and returns fresh recovery codes (shown once). */
    public List<String> enable(long adminId, String base32Secret) {
        SecretBox box = panel.secretBox();
        if (box == null) throw new IllegalStateException("no encryption key");
        LocalDateTime now = LocalDateTime.now();
        db.update("update admins set mfa_secret = ?, mfa_enabled_at = ?, mfa_last_step = ?, mfa_changed_at = ? where id = ?",
                box.seal(base32Secret), now, Totp.currentStep(), now, adminId);
        return newRecoveryCodes(adminId);
    }

    public void disable(long adminId) {
        db.update("update admins set mfa_secret = null, mfa_enabled_at = null, mfa_last_step = null, mfa_changed_at = ? where id = ?", LocalDateTime.now(), adminId);
        db.update("delete from admin_mfa_recovery_codes where admin_id = ?", adminId);
    }

    /** Invalidates every "trusted browser" cookie of this account. */
    public void forgetTrustedBrowsers(long adminId) {
        db.update("update admins set mfa_changed_at = ? where id = ?", LocalDateTime.now(), adminId);
    }

    public List<String> newRecoveryCodes(long adminId) {
        db.update("delete from admin_mfa_recovery_codes where admin_id = ?", adminId);
        List<String> codes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 10; j++) {
                if (j == 5) sb.append('-');
                sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            }
            String code = sb.toString();
            codes.add(code);
            db.insertPlain("admin_mfa_recovery_codes", Row.of("admin_id", adminId, "code_hash", Tokens.sha256Hex(normalizeRecovery(code)), "created_at", now));
        }
        return codes;
    }

    public long recoveryCodesLeft(long adminId) {
        return schemaReady() ? db.count("select count(*) from admin_mfa_recovery_codes where admin_id = ? and used_at is null", adminId) : 0;
    }

    // ------------------------------------------------------------------ verification

    public enum Result { OK, RECOVERY_USED, INVALID, REPLAY }

    /** A 6-digit authenticator code or a recovery code. */
    public Result verify(long adminId, String input) {
        Row a = db.first("select mfa_secret, mfa_last_step from admins where id = ? and mfa_enabled_at is not null", adminId);
        SecretBox box = panel.secretBox();
        if (a == null || box == null || input == null) return Result.INVALID;
        String clean = input.trim();
        if (clean.replaceAll("\\s", "").matches("\\d{6}")) {
            String secret;
            try {
                secret = box.open(a.str("mfa_secret"));
            } catch (RuntimeException e) {
                return Result.INVALID;
            }
            long step = Totp.match(secret, clean, Totp.currentStep());
            if (step < 0) return Result.INVALID;
            // single use: the step must be newer than the last accepted one (atomic, so two tabs can't both use it)
            int won = db.update("update admins set mfa_last_step = ? where id = ? and (mfa_last_step is null or mfa_last_step < ?)", step, adminId, step);
            return won == 1 ? Result.OK : Result.REPLAY;
        }
        String norm = normalizeRecovery(clean);
        if (norm.length() != 10) return Result.INVALID;
        int used = db.update("update admin_mfa_recovery_codes set used_at = ? where admin_id = ? and code_hash = ? and used_at is null",
                LocalDateTime.now(), adminId, Tokens.sha256Hex(norm));
        return used == 1 ? Result.RECOVERY_USED : Result.INVALID;
    }

    /** Checks a code against a secret that is not saved yet (during set-up). */
    public static boolean matchesNew(String base32Secret, String code) {
        return Totp.match(base32Secret, code, Totp.currentStep()) >= 0;
    }

    static String normalizeRecovery(String code) {
        return code == null ? "" : code.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    // ------------------------------------------------------------------ trusted browser

    public void trust(HttpServletRequest request, HttpServletResponse response, long adminId) {
        long exp = System.currentTimeMillis() / 1000 + TRUST_DAYS * 86400L;
        String value = adminId + "." + exp + "." + sign(adminId, exp);
        Cookie c = new Cookie(TRUST_COOKIE, value);
        c.setHttpOnly(true);
        c.setSecure(request.isSecure());
        c.setPath("/admin");
        c.setMaxAge(TRUST_DAYS * 86400);
        c.setAttribute("SameSite", "Strict");
        response.addCookie(c);
    }

    public boolean trusted(HttpServletRequest request, long adminId) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        for (Cookie c : cookies) {
            if (!TRUST_COOKIE.equals(c.getName())) continue;
            String[] p = c.getValue().split("\\.");
            if (p.length != 3) continue;
            try {
                long id = Long.parseLong(p[0]);
                long exp = Long.parseLong(p[1]);
                if (id == adminId && exp > System.currentTimeMillis() / 1000 && Tokens.constantTimeEquals(sign(id, exp), p[2])) return true;
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return false;
    }

    private String sign(long adminId, long exp) {
        Row r = db.first("select mfa_changed_at from admins where id = ?", adminId);
        String changed = r == null ? "" : r.str("mfa_changed_at", "");
        return Tokens.hmacSha256Hex(hmacKey, adminId + "|" + exp + "|" + changed);
    }

    public static void forgetCookie(HttpServletResponse response) {
        Cookie c = new Cookie(TRUST_COOKIE, "");
        c.setPath("/admin");
        c.setMaxAge(0);
        c.setHttpOnly(true);
        response.addCookie(c);
    }
}
