package com.artistikcity.auth;

import com.artistikcity.auth.sms.SmsSender;
import com.artistikcity.service.Mailer;
import com.artistikcity.support.Db;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Login & verification (Admin › Landing Page Management › Login & verification): how a landing page's buyer proves who
 * they are when paying, and the simulated one-time codes used for testing.
 * <ul>
 *   <li><b>checkoutVerify</b>: the default for every landing page (a page can choose its own in its Checkout step):
 *       PHONE (a code by SMS), EMAIL (a code by e-mail), EITHER (the buyer picks) or SIGNIN (sign in before the details,
 *       the earlier behaviour). Visitors see the whole page and the checkout without an account; the code is asked for
 *       when they press Pay, and the account is created (or signed in) by that code.</li>
 *   <li><b>simulatePhone / simulateEmail</b>: testing without an SMS provider or mail server. Nothing is sent; the code
 *       is shown on the screen. To keep real accounts safe, a code is only simulated for a number or address that has no
 *       account yet, for test buyers, and for accounts created with a simulated code (auth_events 'simulated_signup');
 *       a real account still gets a real code, or is asked to sign in another way.</li>
 * </ul>
 * Stored in settings, private tag landing_pages, key login (JSON), cached 10 s. Never sent to the browser as is.
 */
@Service
public class LoginSettings {

    public static final List<String> VERIFY = List.of("PHONE", "EMAIL", "EITHER", "SIGNIN");
    public static final String SIMULATED_SIGNUP = "simulated_signup";

    public record Values(String checkoutVerify, boolean simulatePhone, boolean simulateEmail) {
    }

    /** How a code reaches this person: SIMULATE = shown on screen, SEND = sent for real, REFUSE = can't (see message). */
    public enum Mode { SIMULATE, SEND, REFUSE }

    public record Decision(Mode mode, String message) {
    }

    private final Db db;
    private final Json json;
    private final SmsSender sms;
    private final OtpService otp;
    private final EmailCodeService emailCodes;
    private final Mailer mailer;
    private final AuthEvents events;
    private volatile Values cached;
    private volatile long cachedAt;

    public LoginSettings(Db db, Json json, SmsSender sms, OtpService otp, EmailCodeService emailCodes, Mailer mailer, AuthEvents events) {
        this.db = db;
        this.json = json;
        this.sms = sms;
        this.otp = otp;
        this.emailCodes = emailCodes;
        this.mailer = mailer;
        this.events = events;
    }

    public Values values() {
        Values v = cached;
        if (v == null || System.currentTimeMillis() - cachedAt > 10_000) {
            Map<String, Object> m = null;
            try {
                Row r = db.first("select meta_value from settings where meta_tag = 'landing_pages' and meta_key = 'login' order by id desc limit 1");
                m = r == null ? null : json.decodeMap(r.str("meta_value"));
            } catch (RuntimeException e) {
                m = null;
            }
            if (m == null) m = Map.of();
            String verify = String.valueOf(m.getOrDefault("checkoutVerify", "PHONE")).toUpperCase();
            v = new Values(VERIFY.contains(verify) ? verify : "PHONE", Boolean.TRUE.equals(m.get("simulatePhone")), Boolean.TRUE.equals(m.get("simulateEmail")));
            cached = v;
            cachedAt = System.currentTimeMillis();
        }
        return v;
    }

    public void save(Values v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("checkoutVerify", v.checkoutVerify());
        m.put("simulatePhone", v.simulatePhone());
        m.put("simulateEmail", v.simulateEmail());
        String s = json.encode(m);
        LocalDateTime now = LocalDateTime.now();
        if (db.update("update settings set meta_value = ?, updated_at = ? where meta_tag = 'landing_pages' and meta_key = 'login'", s, now) == 0) {
            db.insert("settings", Row.of("meta_tag", "landing_pages", "meta_key", "login", "meta_value", s, "created_at", now, "updated_at", now));
        }
        cached = v;
        cachedAt = System.currentTimeMillis();
    }

    public Map<String, Object> asMap(Values v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("checkoutVerify", v.checkoutVerify());
        m.put("simulatePhone", v.simulatePhone());
        m.put("simulateEmail", v.simulateEmail());
        return m;
    }

    // ------------------------------------------------------------------ what works on this server

    /** A real SMS provider is chosen (Configuration Panel › SMS). */
    public boolean smsReal() {
        return !sms.isDevelopment();
    }

    /** SMS codes can be used: a provider, the development screen code (local), or simulation. */
    public boolean phoneReady(HttpServletRequest request) {
        return smsReal() || otp.devCodesVisible(request) || values().simulatePhone();
    }

    /** E-mail codes can be used: a mail server, the development screen code (local), or simulation (and the table exists). */
    public boolean emailReady(HttpServletRequest request) {
        return emailCodes.available(request) || (values().simulateEmail() && emailCodes.tableReady());
    }

    // ------------------------------------------------------------------ per person

    /** How a code for this mobile number (E.164) is delivered right now. */
    public Decision phone(HttpServletRequest request, String phone) {
        Row u = db.first("select id, test_buyer from users where phone_number = ? and phone_verified_at is not null", phone);
        if (values().simulatePhone() && maySimulate(u)) return new Decision(Mode.SIMULATE, null);
        if (smsReal() || otp.devCodesVisible(request)) return new Decision(Mode.SEND, null);
        return new Decision(Mode.REFUSE, u != null
                ? "This number already has an ArtistikCity account, and text messages aren't switched on yet. Please log in with your e-mail instead."
                : "Text messages aren't switched on yet. Please use your e-mail instead.");
    }

    /** How a code for this e-mail address (normalised) is delivered right now. */
    public Decision email(HttpServletRequest request, String email) {
        Row u = db.first("select id, test_buyer from users where email = ?", email);
        if (values().simulateEmail() && emailCodes.tableReady() && maySimulate(u)) return new Decision(Mode.SIMULATE, null);
        if (emailCodes.available(request)) return new Decision(Mode.SEND, null);
        return new Decision(Mode.REFUSE, u != null
                ? "This e-mail already has an ArtistikCity account, and e-mail codes aren't switched on yet. Please log in with your password instead."
                : "E-mail codes aren't switched on yet. Please use your mobile number instead.");
    }

    /** New person, a test buyer, or an account that was itself created with a simulated code. */
    private boolean maySimulate(Row user) {
        if (user == null) return true;
        Object tb = user.get("test_buyer");
        if (Boolean.TRUE.equals(tb) || "1".equals(String.valueOf(tb))) return true;
        return isSimulatedAccount(user.lng("id"));
    }

    public boolean isSimulatedAccount(long userId) {
        return db.count("select count(*) from auth_events where user_id = ? and event = ?", userId, SIMULATED_SIGNUP) > 0;
    }

    /** Remembers that this account was created with a simulated code (it may keep using simulated codes). */
    public void markSimulated(HttpServletRequest request, long userId, String method) {
        events.ok(request, userId, SIMULATED_SIGNUP, method);
    }

    public long simulatedAccounts() {
        return db.count("select count(distinct user_id) from auth_events where event = ?", SIMULATED_SIGNUP);
    }

    public boolean mailServer() {
        return mailer.enabled();
    }
}
