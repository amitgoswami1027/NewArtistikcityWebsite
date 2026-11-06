package com.artistikcity.http;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The admin-controlled site password (Admin > System parameters > Site access, {@code /admin/parameters}).
 *
 * <p>Stored in {@code settings} under the private tag {@code system} (never sent to the browser): {@code site_lock_enabled}
 * ("1"/"0"), {@code site_lock_hash} (bcrypt of the password; the password itself is never stored), {@code site_lock_changed_at}
 * and {@code site_lock_changed_by}. {@link PreviewLockFilter} asks {@link #state()} on every request; the answer is cached
 * for {@value #TTL_MS} ms and dropped at once when this server saves, so other servers follow within a few seconds.</p>
 *
 * <p>The visitor's cookie holds {@link State#token()}: an HMAC keyed by the bcrypt hash. A new password gives a new hash and
 * so a new token, which signs everybody out of the preview; switching protection off and on again with the same password
 * keeps existing visitors in.</p>
 */
@Component
public class SiteLock {

    static final String TAG = "system";
    static final String K_ENABLED = "site_lock_enabled";
    static final String K_HASH = "site_lock_hash";
    static final String K_CHANGED_AT = "site_lock_changed_at";
    static final String K_CHANGED_BY = "site_lock_changed_by";
    static final long TTL_MS = 10_000;
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    /** Snapshot of the setting. {@code token} is empty when the lock is off. */
    public record State(boolean enabled, boolean hasPassword, String hash, String token, String changedAt, String changedBy) {
        static final State OFF = new State(false, false, "", "", "", "");

        public boolean active() {
            return enabled && hasPassword;
        }
    }

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2Y, 12);
    private volatile State cached;
    private volatile long loadedAt;

    public SiteLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public State state() {
        State s = cached;
        if (s != null && System.currentTimeMillis() - loadedAt < TTL_MS) return s;
        try {
            Map<String, String> v = new HashMap<>();
            for (Map<String, Object> r : jdbc.queryForList("select meta_key, meta_value from settings where meta_tag = ? and meta_key in (?, ?, ?, ?)",
                    TAG, K_ENABLED, K_HASH, K_CHANGED_AT, K_CHANGED_BY)) {
                v.put(String.valueOf(r.get("meta_key")), r.get("meta_value") == null ? "" : String.valueOf(r.get("meta_value")));
            }
            String hash = v.getOrDefault(K_HASH, "").trim();
            boolean has = hash.startsWith("$2");
            boolean on = "1".equals(v.getOrDefault(K_ENABLED, "0").trim());
            s = new State(on, has, has ? hash : "", on && has ? token(hash) : "", v.getOrDefault(K_CHANGED_AT, ""), v.getOrDefault(K_CHANGED_BY, ""));
        } catch (RuntimeException e) {
            // database not reachable: keep the last answer we had (the site can't serve pages without the database anyway)
            s = cached != null ? cached : State.OFF;
        }
        cached = s;
        loadedAt = System.currentTimeMillis();
        return s;
    }

    public void invalidate() {
        cached = null;
    }

    /** True when {@code given} is the current site password. Constant-time inside bcrypt. */
    public boolean matches(String given, State s) {
        if (given == null || s == null || !s.hasPassword()) return false;
        try {
            return bcrypt.matches(given, s.hash());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Saves the switch and, when {@code newPassword} is not null, a new password (hashed here). Returns the new state.
     */
    public State save(boolean enabled, String newPassword, String changedBy) {
        put(K_ENABLED, enabled ? "1" : "0");
        if (newPassword != null) put(K_HASH, bcrypt.encode(newPassword));
        put(K_CHANGED_AT, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        put(K_CHANGED_BY, changedBy == null ? "" : changedBy);
        invalidate();
        return state();
    }

    private void put(String key, String value) {
        int n = jdbc.update("update settings set meta_value = ?, updated_at = current_timestamp where meta_tag = ? and meta_key = ?", value, TAG, key);
        if (n == 0) {
            jdbc.update("insert into settings (meta_tag, meta_key, meta_value, created_at, updated_at) values (?, ?, ?, current_timestamp, current_timestamp)",
                    TAG, key, value);
        }
    }

    /** Checks a new password; null when it is fine. */
    public static String problem(String password) {
        if (password == null || password.isEmpty()) return "Enter a password.";
        if (!password.equals(password.strip())) return "Leave out spaces at the start and end.";
        if (password.length() < MIN_LENGTH) return "Use at least " + MIN_LENGTH + " characters.";
        if (password.length() > MAX_LENGTH) return "Use at most " + MAX_LENGTH + " characters.";
        if (List.of("password", "12345678", "artistikcity", "qwertyui").contains(password.toLowerCase())) return "That password is too easy to guess.";
        return null;
    }

    static String token(String hash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hash.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "s." + java.util.HexFormat.of().formatHex(mac.doFinal("artistikcity-site-lock-v1".getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
