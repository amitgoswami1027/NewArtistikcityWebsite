package com.artistikcity.auth;

import com.artistikcity.service.Mailer;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Six-digit sign-in codes sent by e-mail: the quick sign-in of landing pages (type the code, no link to open, no
 * password). Same rules as the SMS codes ({@link OtpService}): codes are stored as an HMAC only, expire after 10
 * minutes, allow 5 guesses, a new code replaces the old one, and sending is rate-limited per address and per network.
 * Table auth_email_codes (database/migrations/2026-11-11_landing_quick_signin.sql).
 */
@Service
public class EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);

    public static final int DIGITS = 6;
    public static final int TTL_SECONDS = 600;
    public static final int MAX_ATTEMPTS = 5;
    public static final int RESEND_COOLDOWN_SECONDS = 30;

    public record SendResult(int expiresIn, int resendIn, String devCode, boolean simulated) {
    }

    private final Db db;
    private final Mailer mailer;
    private final RateLimiter limiter;
    private final AuthEvents events;
    private final AuthMail authMail;
    private final byte[] secret;
    private final boolean exposeDevCode;

    public EmailCodeService(Db db, Mailer mailer, RateLimiter limiter, AuthEvents events, AuthMail authMail, Environment env) {
        this.db = db;
        this.mailer = mailer;
        this.limiter = limiter;
        this.events = events;
        this.authMail = authMail;
        String configured = env.getProperty("app.auth.otp-secret", "");
        this.secret = configured.isBlank() ? Tokens.randomBytes(32) : ("email|" + configured).getBytes(StandardCharsets.UTF_8);
        // development only: no mail server, and the dev switch on (the code is then shown on the page to a local browser)
        this.exposeDevCode = env.getProperty("app.auth.expose-dev-otp", Boolean.class, false);
    }

    private volatile boolean tableReady;

    /**
     * E-mail codes can reach people: a mail server is set (MAIL_HOST), or this is a development machine; and the
     * table exists (migration 2026-11-11 applied - until then the option is simply not offered).
     */
    public boolean available(HttpServletRequest request) {
        return (mailer.enabled() || devCodesVisible(request)) && tableReady();
    }

    public boolean tableReady() {
        if (!tableReady) {
            try {
                tableReady = db.count("select count(*) from information_schema.tables where table_schema = database() and table_name = 'auth_email_codes'") > 0;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return tableReady;
    }

    public boolean devCodesVisible(HttpServletRequest request) {
        return exposeDevCode && !mailer.enabled() && OtpService.isLocalRequest(request);
    }

    /** Sends a new code to {@code email} (normalised). Throws {@link OtpService.OtpLimitException} when a limit is hit. */
    public SendResult send(HttpServletRequest request, String email, String name) {
        return send(request, email, name, false);
    }

    /** With {@code simulate} nothing is e-mailed: the code is returned to be shown on the screen (see {@link LoginSettings#email}). */
    public SendResult send(HttpServletRequest request, String email, String name, boolean simulate) {
        String ip = AuthEvents.ip(request);
        LocalDateTime now = LocalDateTime.now();
        RateLimiter.Decision burst = limiter.hit("ecode:send:ip:" + ip, 5, 60);
        if (!burst.allowed()) {
            throw new OtpService.OtpLimitException("Too many codes requested. Please wait a minute.", burst.retryAfterSeconds());
        }
        Row last = db.first("select created_at from auth_email_codes where email = ? order by id desc limit 1", email);
        if (last != null) {
            long since = Duration.between(SessionService.parse(last.str("created_at")), now).getSeconds();
            if (since < RESEND_COOLDOWN_SECONDS) {
                throw new OtpService.OtpLimitException("Please wait " + (RESEND_COOLDOWN_SECONDS - since) + " seconds before asking for another code.",
                        RESEND_COOLDOWN_SECONDS - since);
            }
        }
        if (db.count("select count(*) from auth_email_codes where email = ? and created_at > ?", email, now.minusHours(1)) >= 5
                || db.count("select count(*) from auth_email_codes where email = ? and created_at > ?", email, now.minusDays(1)) >= 10) {
            events.fail(request, null, "email_code_limit", "email_code", email);
            throw new OtpService.OtpLimitException("Too many codes were sent to this address. Please try again later, or sign in another way.", 3600);
        }
        if (ip != null && db.count("select count(*) from auth_email_codes where ip_address = ? and created_at > ?", ip, now.minusHours(1)) >= 15) {
            events.fail(request, null, "email_code_limit_ip", "email_code", ip);
            throw new OtpService.OtpLimitException("Too many codes were requested from your network. Please try again later.", 3600);
        }
        String code = Tokens.numericCode(DIGITS);
        db.update("update auth_email_codes set consumed_at = ? where email = ? and consumed_at is null", now, email);
        long id = db.insertReturningId("auth_email_codes", Row.of("email", email, "purpose", "login", "code_hash", hash(email, code), "attempts", 0,
                "expires_at", now.plusSeconds(TTL_SECONDS), "ip_address", ip, "created_at", now));
        if (simulate) {
            events.ok(request, null, "email_code_simulated", "email_code");
            return new SendResult(TTL_SECONDS, RESEND_COOLDOWN_SECONDS, code, true);
        }
        boolean sent = mailer.send(email, code + " is your ArtistikCity sign-in code", "emails/sign-in-code",
                Row.of("name", name == null || name.isBlank() ? "there" : name, "code", code, "expire", TTL_SECONDS / 60,
                        "requestedFrom", AuthMail.describe(request), "baseUrl", authMail.baseUrl(request)));
        if (!sent) {
            db.update("delete from auth_email_codes where id = ?", id);
            log.error("Sign-in code e-mail could not be sent");
            throw new IllegalStateException("mail failed");
        }
        events.ok(request, null, "email_code_sent", "email_code");
        return new SendResult(TTL_SECONDS, RESEND_COOLDOWN_SECONDS, devCodesVisible(request) ? code : null, false);
    }

    /** Checks a code; on success it is consumed and can't be used again. */
    public OtpService.VerifyResult verify(HttpServletRequest request, String email, String code) {
        RateLimiter.Decision d = limiter.hit("ecode:verify:ip:" + AuthEvents.ip(request), 30, 600);
        if (!d.allowed()) {
            throw new OtpService.OtpLimitException("Too many attempts. Please wait a few minutes.", d.retryAfterSeconds());
        }
        Row row = db.first("select * from auth_email_codes where email = ? and consumed_at is null order by id desc limit 1", email);
        if (row == null) {
            return new OtpService.VerifyResult(OtpService.Outcome.NONE, 0);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!SessionService.parse(row.str("expires_at")).isAfter(now)) {
            db.update("update auth_email_codes set consumed_at = ? where id = ?", now, row.lng("id"));
            return new OtpService.VerifyResult(OtpService.Outcome.EXPIRED, 0);
        }
        int counted = db.update("update auth_email_codes set attempts = attempts + 1 where id = ? and attempts < ? and consumed_at is null",
                row.lng("id"), MAX_ATTEMPTS);
        if (counted == 0) {
            db.update("update auth_email_codes set consumed_at = ? where id = ?", now, row.lng("id"));
            return new OtpService.VerifyResult(OtpService.Outcome.LOCKED, 0);
        }
        int used = (int) row.lng("attempts", 0) + 1;
        String clean = code == null ? "" : code.replaceAll("\\s", "");
        if (clean.length() != DIGITS || !Tokens.constantTimeEquals(hash(email, clean), row.str("code_hash"))) {
            events.fail(request, null, "email_code_invalid", "email_code", email);
            if (used >= MAX_ATTEMPTS) {
                db.update("update auth_email_codes set consumed_at = ? where id = ?", now, row.lng("id"));
                return new OtpService.VerifyResult(OtpService.Outcome.LOCKED, 0);
            }
            return new OtpService.VerifyResult(OtpService.Outcome.INVALID, MAX_ATTEMPTS - used);
        }
        int won = db.update("update auth_email_codes set consumed_at = ? where id = ? and consumed_at is null", now, row.lng("id"));
        return won == 1 ? new OtpService.VerifyResult(OtpService.Outcome.OK, 0) : new OtpService.VerifyResult(OtpService.Outcome.NONE, 0);
    }

    private String hash(String email, String code) {
        return Tokens.hmacSha256Hex(secret, email + "|login|" + code);
    }
}
