package com.artistikcity.auth;

import com.artistikcity.auth.sms.SmsSender;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * SMS one-time passwords.
 * <ul>
 *   <li>6 random digits from SecureRandom, valid 5 minutes, single use; a new code cancels the previous one.</li>
 *   <li>Stored only as HMAC-SHA256(server secret, phone|purpose|code): a leaked table can't be brute-forced offline
 *       without the secret (set OTP_SECRET in production).</li>
 *   <li>At most 5 guesses per code; then the code is burnt.</li>
 *   <li>Sending is limited per number (30 s cooldown, 5/hour, 10/day) and per IP address (15/hour), counted in the
 *       database so limits survive restarts; verification is limited per IP as well.</li>
 *   <li>The SMS ends with the WebOTP line ({@code @host #code}) so Android Chrome can fill the code in automatically.</li>
 * </ul>
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    public static final int DIGITS = 6;
    public static final int TTL_SECONDS = 300;
    public static final int MAX_ATTEMPTS = 5;
    public static final int RESEND_COOLDOWN_SECONDS = 30;

    public record SendResult(int expiresIn, int resendIn, String devCode) {
    }

    public enum Outcome { OK, INVALID, EXPIRED, LOCKED, NONE }

    public record VerifyResult(Outcome outcome, int attemptsLeft) {
    }

    public static class OtpLimitException extends RuntimeException {
        private final long retryAfter;

        public OtpLimitException(String message, long retryAfter) {
            super(message);
            this.retryAfter = retryAfter;
        }

        public long retryAfter() {
            return retryAfter;
        }
    }

    private final Db db;
    private final SmsSender sms;
    private final RateLimiter limiter;
    private final AuthEvents events;
    private final byte[] secret;
    private final boolean exposeDevCode;
    private final String appUrl;

    public OtpService(Db db, SmsSender sms, RateLimiter limiter, AuthEvents events, Environment env) {
        this.db = db;
        this.sms = sms;
        this.limiter = limiter;
        this.events = events;
        String configured = env.getProperty("app.auth.otp-secret", "");
        if (configured.isBlank()) {
            log.warn("OTP_SECRET is not set - using a random key; codes sent before a restart stop working. Set OTP_SECRET in production.");
            this.secret = Tokens.randomBytes(32);
        } else {
            this.secret = configured.getBytes(StandardCharsets.UTF_8);
        }
        // only ever true in development with the log SMS gateway (never with a real SMS provider)
        this.exposeDevCode = env.getProperty("app.auth.expose-dev-otp", Boolean.class, false) && sms.isDevelopment();
        this.appUrl = env.getProperty("app.url", "");
    }

    public boolean devCodesVisible() {
        return exposeDevCode;
    }

    /** Sends a new code to {@code phone} (E.164). Throws {@link OtpLimitException} when a limit is hit. */
    public SendResult send(HttpServletRequest request, String phone, String purpose, Long userId) throws SmsSender.SmsException {
        String ip = AuthEvents.ip(request);
        LocalDateTime now = LocalDateTime.now();

        RateLimiter.Decision burst = limiter.hit("otp:send:ip:" + ip, 5, 60);
        if (!burst.allowed()) {
            throw new OtpLimitException("Too many codes requested. Please wait a minute.", burst.retryAfterSeconds());
        }
        Row last = db.first("select created_at from auth_otps where phone_number = ? order by id desc limit 1", phone);
        if (last != null) {
            long since = java.time.Duration.between(SessionService.parse(last.str("created_at")), now).getSeconds();
            if (since < RESEND_COOLDOWN_SECONDS) {
                throw new OtpLimitException("Please wait " + (RESEND_COOLDOWN_SECONDS - since) + " seconds before asking for another code.",
                        RESEND_COOLDOWN_SECONDS - since);
            }
        }
        if (db.count("select count(*) from auth_otps where phone_number = ? and created_at > ?", phone, now.minusHours(1)) >= 5
                || db.count("select count(*) from auth_otps where phone_number = ? and created_at > ?", phone, now.minusDays(1)) >= 10) {
            events.fail(request, userId, "otp_limit", "otp", phone);
            throw new OtpLimitException("Too many codes were sent to this number. Please try again later, or sign in another way.", 3600);
        }
        if (ip != null && db.count("select count(*) from auth_otps where ip_address = ? and created_at > ?", ip, now.minusHours(1)) >= 15) {
            events.fail(request, userId, "otp_limit_ip", "otp", ip);
            throw new OtpLimitException("Too many codes were requested from your network. Please try again later.", 3600);
        }

        String code = Tokens.numericCode(DIGITS);
        db.update("update auth_otps set consumed_at = ? where phone_number = ? and purpose = ? and consumed_at is null", now, phone, purpose);
        long id = db.insertReturningId("auth_otps", Row.of("phone_number", phone, "purpose", purpose, "user_id", userId,
                "code_hash", hash(phone, purpose, code), "attempts", 0, "expires_at", now.plusSeconds(TTL_SECONDS),
                "ip_address", ip, "created_at", now));
        try {
            sms.send(phone, message(request, code));
        } catch (SmsSender.SmsException e) {
            db.update("delete from auth_otps where id = ?", id);
            log.error("SMS to {} failed: {}", PhoneNumbers.mask(phone), e.getMessage());
            throw e;
        }
        events.ok(request, userId, "otp_sent", "otp");
        return new SendResult(TTL_SECONDS, RESEND_COOLDOWN_SECONDS, exposeDevCode ? code : null);
    }

    /** Checks a code; on success it is consumed and can't be used again. */
    public VerifyResult verify(HttpServletRequest request, String phone, String code, String purpose, Long userId) {
        RateLimiter.Decision d = limiter.hit("otp:verify:ip:" + AuthEvents.ip(request), 30, 600);
        if (!d.allowed()) {
            throw new OtpLimitException("Too many attempts. Please wait a few minutes.", d.retryAfterSeconds());
        }
        Row otp = userId == null
                ? db.first("select * from auth_otps where phone_number = ? and purpose = ? and consumed_at is null order by id desc limit 1", phone, purpose)
                : db.first("select * from auth_otps where phone_number = ? and purpose = ? and user_id = ? and consumed_at is null order by id desc limit 1",
                        phone, purpose, userId);
        if (otp == null) {
            return new VerifyResult(Outcome.NONE, 0);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!SessionService.parse(otp.str("expires_at")).isAfter(now)) {
            db.update("update auth_otps set consumed_at = ? where id = ?", now, otp.lng("id"));
            return new VerifyResult(Outcome.EXPIRED, 0);
        }
        // count the guess first (atomic), so parallel guesses can't exceed the limit
        int counted = db.update("update auth_otps set attempts = attempts + 1 where id = ? and attempts < ? and consumed_at is null",
                otp.lng("id"), MAX_ATTEMPTS);
        if (counted == 0) {
            db.update("update auth_otps set consumed_at = ? where id = ?", now, otp.lng("id"));
            return new VerifyResult(Outcome.LOCKED, 0);
        }
        int used = (int) otp.lng("attempts", 0) + 1;
        String clean = code == null ? "" : code.replaceAll("\\s", "");
        if (clean.length() != DIGITS || !Tokens.constantTimeEquals(hash(phone, purpose, clean), otp.str("code_hash"))) {
            events.fail(request, userId, "otp_invalid", "otp", PhoneNumbers.mask(phone));
            if (used >= MAX_ATTEMPTS) {
                db.update("update auth_otps set consumed_at = ? where id = ?", now, otp.lng("id"));
                return new VerifyResult(Outcome.LOCKED, 0);
            }
            return new VerifyResult(Outcome.INVALID, MAX_ATTEMPTS - used);
        }
        int won = db.update("update auth_otps set consumed_at = ? where id = ? and consumed_at is null", now, otp.lng("id"));
        return won == 1 ? new VerifyResult(Outcome.OK, 0) : new VerifyResult(Outcome.NONE, 0);
    }

    private String hash(String phone, String purpose, String code) {
        return Tokens.hmacSha256Hex(secret, phone + "|" + purpose + "|" + code);
    }

    private String message(HttpServletRequest request, String code) {
        String host = appUrl.isBlank() ? request.getServerName() : appUrl.replaceAll("^https?://", "").replaceAll("[:/].*$", "");
        return code + " is your ArtistikCity sign-in code. It expires in 5 minutes. Never share it with anyone.\n\n@" + host + " #" + code;
    }
}
