package com.artistikcity.security;

import com.artistikcity.config.Settings;
import com.artistikcity.http.Auth;
import com.artistikcity.service.Alerts;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Watches the admin console for refused requests.
 *
 * <ul>
 *   <li>Every 401 / 403 answer under {@code /admin/**} is written to the security log as DENIED, with who asked.</li>
 *   <li>A staff session refused {@code app.security.refusals-before-revoke} times (5) within
 *       {@code app.security.refusal-window-minutes} (10) is signed out: probing for pages or actions outside one's role
 *       is treated as a compromised or misused session. The staff are alerted by e-mail.</li>
 *   <li>A student session that calls the admin API ({@link #studentCalledAdminApi}) is signed out at once: no
 *       legitimate student page ever does that.</li>
 * </ul>
 */
@Component
public class SecurityWatch implements HandlerInterceptor {

    /** Request attribute: who made the request, captured before a guard signs them out. */
    public static final String ACTOR = "security.actor";

    private final Auth auth;
    private final AuditLog auditLog;
    private final Alerts alerts;
    private final Settings settings;
    private final JdbcTemplate jdbc;

    public SecurityWatch(Auth auth, AuditLog auditLog, Alerts alerts, Settings settings, JdbcTemplate jdbc) {
        this.auth = auth;
        this.auditLog = auditLog;
        this.alerts = alerts;
        this.settings = settings;
        this.jdbc = jdbc;
    }

    public static boolean isAdminApi(HttpServletRequest request) {
        String p = request.getRequestURI();
        return p.startsWith("/admin/api/") || p.startsWith("/admin/console/api/");
    }

    /** Called by the admin guard when a request without a staff session reaches the admin API. */
    public void studentCalledAdminApi(HttpServletRequest request) {
        Row user = auth.check(request) ? auth.user(request) : null;
        if (user == null) {
            return;
        }
        AuditLog.Actor actor = AuditLog.Actor.student(user.lng("id"), user.str("email", null));
        request.setAttribute(ACTOR, actor);
        auth.logout(request);
        auditLog.write(AuditLog.entry(AuditLog.Type.AUTH, "session_revoked").request(request).actor(AuditLog.Actor.system())
                .resource("student", user.lng("id"))
                .summary("Student session signed out: it called the admin API (" + request.getMethod() + " " + request.getRequestURI() + ")"));
        alerts.send("security:student-admin-api:" + user.lng("id"), "A student session called the admin API",
                List.of("Student: " + user.str("email", "#" + user.lng("id")) + " (id " + user.lng("id") + ")",
                        "Request: " + request.getMethod() + " " + request.getRequestURI(),
                        "From: " + request.getRemoteAddr(),
                        "The session was signed out. Check Admin > Setup > Security log for what else it did."),
                "/admin/security/audit?actor=" + user.lng("id"));
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        int status = response.getStatus();
        if (status != 401 && status != 403) {
            return;
        }
        Row admin = auth.admin(request);
        AuditLog.Actor actor = request.getAttribute(ACTOR) instanceof AuditLog.Actor a ? a
                : admin != null ? AuditLog.Actor.admin(admin)
                : auth.check(request) ? studentActor(request) : AuditLog.Actor.anonymous();
        auditLog.write(AuditLog.entry(AuditLog.Type.DENIED, status == 401 ? "not signed in" : "not allowed").request(request).actor(actor)
                .resource("route", request.getMethod() + " " + request.getRequestURI())
                .summary(status + (admin == null ? "" : " for " + admin.str("admin_type", "staff"))));
        if (admin != null) {
            revokeIfProbing(request, admin);
        }
    }

    private AuditLog.Actor studentActor(HttpServletRequest request) {
        Row u = auth.user(request);
        return u == null ? AuditLog.Actor.anonymous() : AuditLog.Actor.student(u.lng("id"), u.str("email", null));
    }

    private void revokeIfProbing(HttpServletRequest request, Row admin) {
        long id = admin.lng("id");
        LocalDateTime since = LocalDateTime.now().minusMinutes(settings.refusalWindowMinutes());
        try {
            Timestamp lastRevoke = jdbc.queryForObject("select max(occurred_at) from audit_logs where action_type = 'AUTH' and action = 'session_revoked'"
                    + " and resource_type = 'admin' and resource_id = ?", Timestamp.class, String.valueOf(id));
            if (lastRevoke != null && lastRevoke.toLocalDateTime().isAfter(since)) {
                since = lastRevoke.toLocalDateTime();
            }
        } catch (RuntimeException ignored) {
            // no table yet: count from the window start
        }
        long refused;
        try {
            refused = auditLog.deniedSince("admin", id, since);
        } catch (RuntimeException e) {
            return;
        }
        if (refused < settings.refusalsBeforeRevoke()) {
            return;
        }
        auth.logoutAdmin(request);
        auditLog.write(AuditLog.entry(AuditLog.Type.AUTH, "session_revoked").request(request).actor(AuditLog.Actor.system())
                .resource("admin", id)
                .summary("Staff session signed out after " + refused + " refused requests in " + settings.refusalWindowMinutes() + " minutes"));
        alerts.send("security:revoke:admin:" + id, "A staff session was signed out for refused requests",
                List.of("Staff: " + admin.str("name", "") + " <" + admin.str("email", "") + "> (" + admin.str("admin_type", "staff") + ")",
                        refused + " requests were refused within " + settings.refusalWindowMinutes() + " minutes; the last one: "
                                + request.getMethod() + " " + request.getRequestURI(),
                        "From: " + request.getRemoteAddr(),
                        "If this was not them, change their password in Admin > People."),
                "/admin/security/audit?actor=" + id);
    }
}
