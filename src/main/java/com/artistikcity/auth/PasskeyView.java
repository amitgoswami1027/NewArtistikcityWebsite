package com.artistikcity.auth;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import org.springframework.stereotype.Component;

import java.util.List;

/** Read models for the "Login & security" page (no secrets: public keys and hashes are never sent). */
@Component
public class PasskeyView {

    private final Db db;

    public PasskeyView(Db db) {
        this.db = db;
    }

    public List<Row> list(long userId) {
        return db.select("select id, name, created_at, last_used_at, backed_up, transports from webauthn_credentials"
                + " where user_id = ? order by id", userId);
    }

    /** "Chrome on Windows" from a User-Agent header. */
    public static String describeAgent(String ua) {
        if (ua == null || ua.isBlank()) {
            return "Unknown device";
        }
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera" : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Firefox/") ? "Firefox" : ua.contains("Safari/") ? "Safari" : "Browser";
        String os = ua.contains("Windows") ? "Windows" : ua.contains("Android") ? "Android" : ua.contains("iPhone") ? "iPhone"
                : ua.contains("iPad") ? "iPad" : ua.contains("Mac OS") ? "Mac" : ua.contains("Linux") ? "Linux" : "unknown system";
        return browser + " on " + os;
    }
}
