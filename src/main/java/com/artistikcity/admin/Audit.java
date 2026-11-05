package com.artistikcity.admin;

import com.artistikcity.security.AuditLog;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Admin activity log. Every entry goes to two places: {@code admin_audit} (the short timeline shown on detail screens)
 * and the security audit log {@code audit_logs} ({@link AuditLog}: hash-chained, with IP, location and - through
 * {@link #change} - before/after snapshots for the JSON diff in Admin > Security log).
 */
@Component
public class Audit {

    private final Db db;
    private final AuditLog auditLog;

    public Audit(Db db, AuditLog auditLog) {
        this.db = db;
        this.auditLog = auditLog;
    }

    public void log(Row admin, String entity, long entityId, String action, String details) {
        change(admin, entity, entityId, action, details, null, null);
    }

    /** Like {@link #log}, with the record as it was before and after the change (secrets are masked, long texts cut). */
    public void change(Row admin, String entity, long entityId, String action, String details, Map<String, ?> before, Map<String, ?> after) {
        auditLog.write(AuditLog.entry(AuditLog.typeOf(action), entity + " " + action).actor(AuditLog.Actor.admin(admin))
                .resource(entity, entityId == 0 ? null : entityId).summary(details).before(before).after(after));
        try {
            db.insertReturningId("admin_audit", Row.of("admin_id", admin == null ? null : admin.get("id"), "entity", entity, "entity_id", entityId,
                    "action", action, "details", details == null ? null : AdminKit.trim(details, 1000), "created_at", LocalDateTime.now()));
        } catch (Db.DbException e) {
            // the log must never break the action itself (e.g. before the migration ran)
        }
    }

    /** The current row of a table, for {@link #change} (null when missing or unreadable). */
    public Row snapshot(String table, Object id) {
        try {
            return id == null ? null : db.find(table, id);
        } catch (Db.DbException e) {
            return null;
        }
    }

    public List<Row> history(String entity, long entityId) {
        try {
            return db.select("select x.action, x.details, x.created_at, a.name as admin_name from admin_audit x left join admins a on a.id = x.admin_id"
                    + " where x.entity = ? and x.entity_id = ? order by x.id desc limit 50", entity, entityId);
        } catch (Db.DbException e) {
            return List.of();
        }
    }
}
