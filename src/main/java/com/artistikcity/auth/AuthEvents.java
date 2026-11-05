package com.artistikcity.auth;

import com.artistikcity.security.AuditLog;
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
    private final AuditLog auditLog;

    public AuthEvents(Db db, AuditLog auditLog) {
        this.db = db;
        this.auditLog = auditLog;
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
        String email = null;
        if (userId != null) {
            try {
                Object e = db.scalar("select email from users where id = ?", userId);
                email = e == null ? null : String.valueOf(e);
            } catch (RuntimeException ignored) {
                // the e-mail is only a convenience for the security log
            }
        }
        auditLog.write(AuditLog.entry(AuditLog.Type.AUTH, event).request(request).actor(AuditLog.Actor.student(userId, email))
                .resource(method == null ? "auth" : "auth:" + method, userId).summary(detail).outcome(success ? "OK" : "FAIL"));
        if (!success) {
            log.info("auth event {} ({}) failed for user {}: {}", event, method, userId, detail);
        }
    }

    /** Staff sign-in events (admins are not in the users table, so they only go to the security audit log). */
    public void admin(HttpServletRequest request, Row admin, String event, boolean success, String detail) {
        auditLog.write(AuditLog.entry(AuditLog.Type.AUTH, event).request(request)
                .actor(admin == null ? new AuditLog.Actor("anonymous", null, detail, null) : AuditLog.Actor.admin(admin))
                .resource("auth:admin", admin == null ? null : admin.get("id")).summary(detail).outcome(success ? "OK" : "FAIL"));
        if (!success) {
            log.info("admin auth event {} failed: {}", event, detail);
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
