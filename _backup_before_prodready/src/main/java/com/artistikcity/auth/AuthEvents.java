package com.artistikcity.auth;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Security audit trail ({@code auth_events}): sign-ins, failures, account linking, revocations.
 * Writing an event never breaks the request that triggered it.
 */
@Component
public class AuthEvents {

    private static final Logger log = LoggerFactory.getLogger(AuthEvents.class);

    private final Db db;

    public AuthEvents(Db db) {
        this.db = db;
    }

    public void record(HttpServletRequest request, Long userId, String event, String method, boolean success, String detail) {
        try {
            db.insertPlain("auth_events", Row.of(
                    "user_id", userId,
                    "event", event,
                    "method", method,
                    "success", success,
                    "ip_address", ip(request),
                    "user_agent", userAgent(request),
                    "detail", detail == null ? null : (detail.length() > 500 ? detail.substring(0, 500) : detail),
                    "created_at", LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.warn("Could not record auth event {}", event, e);
        }
        if (!success) {
            log.info("auth event {} ({}) failed for user {}: {}", event, method, userId, detail);
        }
    }

    public void ok(HttpServletRequest request, Long userId, String event, String method) {
        record(request, userId, event, method, true, null);
    }

    public void fail(HttpServletRequest request, Long userId, String event, String method, String detail) {
        record(request, userId, event, method, false, detail);
    }

    /** Client address. Behind a reverse proxy, enable server.forward-headers-strategy so this is the real client. */
    public static String ip(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest request) {
        String ua = request == null ? null : request.getHeader("User-Agent");
        return ua == null ? null : (ua.length() > 500 ? ua.substring(0, 500) : ua);
    }
}
