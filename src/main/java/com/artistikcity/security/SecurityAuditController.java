package com.artistikcity.security;

import com.artistikcity.admin.AdminKit;
import com.artistikcity.admin.Grid;
import com.artistikcity.config.Settings;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Admin > Setup > Security log ({@code /admin/security/audit}): the {@link AuditLog} stream as a dense, filterable
 * table, a detail drawer with the before/after JSON of a change, CSV export (itself logged as EXPORT) and the
 * integrity check. Super admins only (capabilities {@code security} and {@code security.export}).
 */
@Controller
public class SecurityAuditController {

    private final AdminKit kit;
    private final Db db;
    private final AuditLog auditLog;
    private final Settings settings;

    public SecurityAuditController(AdminKit kit, Db db, AuditLog auditLog, Settings settings) {
        this.kit = kit;
        this.db = db;
        this.auditLog = auditLog;
        this.settings = settings;
    }

    private Grid grid(HttpServletRequest request) {
        return Grid.from(db, request)
                .columns("l.id, l.occurred_at, l.actor_type, l.actor_id, l.actor_email, l.actor_role, l.action_type, l.action, l.resource_type,"
                        + " l.resource_id, l.summary, l.ip, l.country, l.city, l.http_method, l.path, l.outcome,"
                        + " (l.before_json is not null or l.after_json is not null) as has_diff")
                .from("audit_logs l")
                .search("l.action", "l.summary", "l.actor_email", "l.ip", "l.resource_type", "l.resource_id", "l.path")
                .sort("time", "l.occurred_at").sort("actor", "l.actor_email").sort("type", "l.action_type").sort("resource", "l.resource_type")
                .defaultSort("time", "desc").tieBreak("l.id desc")
                .filter("actor", v -> {
                    String t = v.trim();
                    if (t.matches("\\d+")) return Grid.where("l.actor_id = ?", Long.parseLong(t));
                    return t.isEmpty() ? null : Grid.where("l.actor_email like ?", "%" + t.replace("%", "\\%").replace("_", "\\_") + "%");
                })
                .filter("role", v -> List.of("admin", "teacher", "moderator", "student", "system").contains(v) ? Grid.where("l.actor_role = ?", v)
                        : "anonymous".equals(v) ? Grid.where("l.actor_type = 'anonymous'") : null)
                .filter("types", v -> {
                    List<String> picked = Arrays.stream(v.split(",")).map(String::trim).map(String::toUpperCase).filter(AuditLog.TYPES::contains).distinct().toList();
                    return picked.isEmpty() ? null : Grid.where("l.action_type in (" + Db.marks(picked.size()) + ")", picked.toArray());
                })
                .filter("outcome", v -> List.of("OK", "FAIL", "DENIED").contains(v) ? Grid.where("l.outcome = ?", v) : null)
                .filter("resource", v -> v.isBlank() ? null : Grid.where("l.resource_type = ?", v.trim()))
                .filter("window", v -> {
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime since = switch (v) {
                        case "1h" -> now.minusHours(1);
                        case "24h" -> now.minusHours(24);
                        case "7d" -> now.minusDays(7);
                        case "30d" -> now.minusDays(30);
                        default -> null;
                    };
                    return since == null ? null : Grid.where("l.occurred_at >= ?", since);
                })
                .filter("from", v -> time(v, false) == null ? null : Grid.where("l.occurred_at >= ?", time(v, false)))
                .filter("to", v -> time(v, true) == null ? null : Grid.where("l.occurred_at < ?", time(v, true)));
    }

    /** "2026-11-06" or "2026-11-06T14:30" (datetime-local). The end of a range is exclusive: a day / minute is included whole. */
    static LocalDateTime time(String v, boolean end) {
        try {
            if (v.length() == 10) {
                LocalDate d = LocalDate.parse(v);
                return end ? d.plusDays(1).atStartOfDay() : d.atStartOfDay();
            }
            LocalDateTime t = LocalDateTime.parse(v.length() == 16 ? v + ":00" : v);
            return end ? t.plusMinutes(1).withSecond(0).withNano(0) : t;
        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
    }

    @GetMapping({"/admin/security/audit", "/admin/security/audit/"})
    public ResponseEntity<String> index(HttpServletRequest request) {
        return kit.page(request, "security", "Admin/SecurityAudit", () -> {
            Map<String, Object> p = new LinkedHashMap<>();
            Supplier<Object> grid = () -> grid(request).page();
            p.put("grid", grid);
            Supplier<Object> stats = () -> {
                LocalDateTime day = LocalDateTime.now().minusHours(24);
                Map<String, Object> s = new LinkedHashMap<>();
                for (Row r : db.select("select action_type, count(*) as n from audit_logs where occurred_at >= ? group by action_type", day)) {
                    s.put(r.str("action_type"), r.lng("n", 0));
                }
                s.put("failedSignIns", db.count("select count(*) from audit_logs where action_type = 'AUTH' and outcome = 'FAIL' and occurred_at >= ?", day));
                s.put("revoked", db.count("select count(*) from audit_logs where action = 'session_revoked' and occurred_at >= ?", day));
                s.put("total", db.count("select count(*) from audit_logs"));
                return s;
            };
            p.put("stats", stats);
            p.put("resources", db.select("select distinct resource_type as value, resource_type as label from audit_logs"
                    + " where resource_type is not null and resource_type not like 'route%' order by resource_type limit 100"));
            p.put("canExport", kit.can(kit.admin(request), "security.export"));
            return p;
        });
    }

    @GetMapping("/admin/api/security/audit/{id}")
    public ResponseEntity<String> show(HttpServletRequest request, @PathVariable("id") long id) {
        if (!kit.can(kit.admin(request), "security")) {
            return kit.forbidden();
        }
        Row r = db.first("select * from audit_logs where id = ?", id);
        if (r == null) {
            return kit.fail(org.springframework.http.HttpStatus.NOT_FOUND, "That entry does not exist.");
        }
        return kit.ok(Map.of("entry", r));
    }

    @GetMapping("/admin/security/audit/export")
    public ResponseEntity<String> export(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "security.export")) {
            return kit.forbidden();
        }
        int max = settings.auditExportMaxRows();
        List<List<Object>> rows = new ArrayList<>();
        for (Row r : grid(request).all(max)) {
            rows.add(List.of(r.str("occurred_at", ""), r.str("action_type", ""), r.str("action", ""), r.str("outcome", ""),
                    r.str("actor_type", ""), r.str("actor_id", ""), r.str("actor_email", ""), r.str("actor_role", ""),
                    r.str("resource_type", ""), r.str("resource_id", ""), r.str("summary", ""), r.str("ip", ""),
                    r.str("country", ""), r.str("city", ""), r.str("http_method", ""), r.str("path", "")));
        }
        String filters = request.getQueryString() == null ? "no filters" : request.getQueryString();
        auditLog.write(AuditLog.entry(AuditLog.Type.EXPORT, "security log exported").request(request).actor(AuditLog.Actor.admin(admin))
                .resource("audit_logs", null).summary(rows.size() + " rows (" + AdminKit.trim(filters, 400) + ")"));
        return kit.csv("security-log-" + LocalDate.now() + ".csv",
                List.of("Time", "Type", "Action", "Outcome", "Actor type", "Actor id", "Actor e-mail", "Role", "Resource", "Resource id",
                        "Summary", "IP", "Country", "City", "Method", "Path"), rows);
    }

    @PostMapping("/admin/api/security/audit/verify")
    public ResponseEntity<String> verify(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "security")) {
            return kit.forbidden();
        }
        AuditLog.Verification v = auditLog.verify();
        auditLog.write(AuditLog.entry(AuditLog.Type.AUTH, "integrity check").request(request).actor(AuditLog.Actor.admin(admin))
                .resource("audit_logs", null).summary(v.ok() ? v.checked() + " rows, chain intact" : v.problem()).outcome(v.ok() ? "OK" : "FAIL"));
        return kit.ok(v.toMap());
    }
}
