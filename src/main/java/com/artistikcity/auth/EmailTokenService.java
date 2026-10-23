package com.artistikcity.auth;

import com.artistikcity.support.Db;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Single-use tokens delivered by e-mail ({@code auth_tokens}): magic sign-in links, e-mail verification and
 * password reset. The link carries 256 random bits; only their SHA-256 is stored. Issuing a new token of
 * the same purpose for the same address cancels older ones.
 */
@Service
public class EmailTokenService {

    public static final String MAGIC_LINK = "magic_link";
    public static final String VERIFY_EMAIL = "verify_email";
    public static final String PASSWORD_RESET = "password_reset";

    private final Db db;
    private final Json json;

    public EmailTokenService(Db db, Json json) {
        this.db = db;
        this.json = json;
    }

    /** @return the raw token to put in the link (never stored) */
    public String issue(HttpServletRequest request, String purpose, String email, Long userId, Duration ttl, Map<String, ?> meta) {
        LocalDateTime now = LocalDateTime.now();
        db.update("update auth_tokens set consumed_at = ? where email = ? and purpose = ? and consumed_at is null", now, email, purpose);
        String token = Tokens.random(32);
        db.insertPlain("auth_tokens", Row.of("purpose", purpose, "user_id", userId, "email", email, "token_hash", Tokens.sha256Hex(token),
                "expires_at", now.plus(ttl), "ip_address", AuthEvents.ip(request), "meta", meta == null ? null : json.encode(meta),
                "created_at", now));
        return token;
    }

    /** Number of tokens sent to {@code email} for {@code purpose} in the last {@code window}. */
    public long recentCount(String email, String purpose, Duration window) {
        return db.count("select count(*) from auth_tokens where email = ? and purpose = ? and created_at > ?", email, purpose,
                LocalDateTime.now().minus(window));
    }

    /** The still-valid token row (without consuming it), or null. */
    public Row peek(String purpose, String token) {
        if (token == null || token.length() < 20 || token.length() > 100) {
            return null;
        }
        Row t = db.first("select * from auth_tokens where token_hash = ? and purpose = ?", Tokens.sha256Hex(token), purpose);
        if (t == null || t.get("consumed_at") != null || !SessionService.parse(t.str("expires_at")).isAfter(LocalDateTime.now())) {
            return null;
        }
        return t;
    }

    /** Consumes the token (single use). Returns its row, or null if it is unknown, used or expired. */
    public Row consume(String purpose, String token) {
        Row t = peek(purpose, token);
        if (t == null) {
            return null;
        }
        int won = db.update("update auth_tokens set consumed_at = ? where id = ? and consumed_at is null", LocalDateTime.now(), t.lng("id"));
        return won == 1 ? t : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> meta(Row token) {
        Object m = token.get("meta");
        return m == null ? Map.of() : json.decodeMap(String.valueOf(m));
    }
}
