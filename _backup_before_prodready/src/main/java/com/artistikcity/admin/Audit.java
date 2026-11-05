package com.artistikcity.admin;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** Admin activity log (table admin_audit): who changed which record, shown as a timeline on detail screens. */
@Component
public class Audit {

    private final Db db;

    public Audit(Db db) {
        this.db = db;
    }

    public void log(Row admin, String entity, long entityId, String action, String details) {
        try {
            db.insertReturningId("admin_audit", Row.of("admin_id", admin == null ? null : admin.get("id"), "entity", entity, "entity_id", entityId,
                    "action", action, "details", details == null ? null : AdminKit.trim(details, 1000), "created_at", LocalDateTime.now()));
        } catch (Db.DbException e) {
            // the log must never break the action itself (e.g. before the migration ran)
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
