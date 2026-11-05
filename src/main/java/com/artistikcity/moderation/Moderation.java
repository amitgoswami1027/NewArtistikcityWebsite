package com.artistikcity.moderation;

import com.artistikcity.auth.SessionService;
import com.artistikcity.support.ValidationException;
import com.artistikcity.security.AuditLog;
import com.artistikcity.support.DbErrors;
import com.artistikcity.support.Row;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Account restrictions and content reports.
 *
 * <p>Restrictions ({@code account_restrictions}): BAN ends every session at once and refuses new sign-ins
 * ({@link #assertMaySignIn}); POSTING and SELLING are checked where students write or list something
 * ({@link #assertAllowed}). A restriction ends at {@code until} or when staff lift it.</p>
 *
 * <p>Reports ({@code content_reports}): one per reporter and item; staff hide the item (which closes every open report
 * on it) or dismiss.</p>
 */
@Service
public class Moderation {

    public static final List<String> KINDS = List.of("BAN", "POSTING", "SELLING");
    public static final List<String> TARGETS = List.of("PAINTING", "ARTWORK", "REVIEW", "SUBMISSION", "USER");
    public static final List<String> REASONS = List.of("SPAM", "OFFENSIVE", "COPYRIGHT", "MISLEADING", "PERSONAL_INFO", "OTHER");

    private final JdbcTemplate jdbc;
    private final SessionService sessions;
    private final AuditLog auditLog;

    public Moderation(JdbcTemplate jdbc, SessionService sessions, AuditLog auditLog) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.auditLog = auditLog;
    }

    // ------------------------------------------------------------------ restrictions

    /** The active restriction of this kind (or a BAN, which covers everything), or null. */
    public Map<String, Object> active(long userId, String kind) {
        try {
            List<Map<String, Object>> r = jdbc.queryForList("select id, kind, reason, until from account_restrictions where user_id = ? and kind in (?, 'BAN')"
                    + " and lifted_at is null and (until is null or until > ?) order by case when kind = 'BAN' then 0 else 1 end, id desc limit 1",
                    userId, kind, Timestamp.valueOf(LocalDateTime.now()));
            return r.isEmpty() ? null : r.get(0);
        } catch (RuntimeException e) {
            return null;    // before the migration ran: nobody is restricted
        }
    }

    private static String untilText(Object until) {
        if (until == null) return "";
        LocalDateTime t = until instanceof Timestamp ts ? ts.toLocalDateTime() : until instanceof LocalDateTime l ? l : null;
        return t == null ? "" : " until " + t.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"));
    }

    /** Throws a 422 with a plain message when the student may not do this. */
    public void assertAllowed(Long userId, String kind) {
        if (userId == null) return;
        Map<String, Object> r = active(userId, kind);
        if (r == null) return;
        String what = switch (kind) {
            case "SELLING" -> "offer work for sale";
            case "POSTING" -> "post or send messages";
            default -> "do this";
        };
        throw ValidationException.withMessage("restricted", "Your account can't " + what + untilText(r.get("until"))
                + ". If you think this is a mistake, write to us through the contact page.");
    }

    /** Called before a sign-in completes. */
    public void assertMaySignIn(long userId) {
        Map<String, Object> r = active(userId, "BAN");
        if (r != null && "BAN".equals(r.get("kind"))) {
            throw ValidationException.withMessage("email", "This account is suspended" + untilText(r.get("until"))
                    + ". If you think this is a mistake, write to us through the contact page.");
        }
    }

    public long restrict(long userId, String kind, String reason, LocalDateTime until, Row admin) {
        org.springframework.jdbc.support.GeneratedKeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into account_restrictions (user_id, kind, reason, until, created_by, created_at) values (?, ?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, kind);
            ps.setString(3, reason);
            ps.setTimestamp(4, until == null ? null : Timestamp.valueOf(until));
            ps.setObject(5, admin == null ? null : admin.get("id"));
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keys);
        long id = keys.getKey().longValue();
        int ended = 0;
        if ("BAN".equals(kind)) ended = sessions.revokeAll(userId, null, "banned");
        auditLog.write(AuditLog.entry(AuditLog.Type.CREATE, "account restricted").actor(AuditLog.Actor.admin(admin)).resource("student", userId)
                .summary(kind + untilText(until == null ? null : Timestamp.valueOf(until)) + " · " + reason + ("BAN".equals(kind) ? " · " + ended + " session(s) ended" : "")));
        return id;
    }

    public boolean lift(long restrictionId, Row admin) {
        List<Map<String, Object>> r = jdbc.queryForList("select user_id, kind from account_restrictions where id = ? and lifted_at is null", restrictionId);
        if (r.isEmpty()) return false;
        jdbc.update("update account_restrictions set lifted_at = ?, lifted_by = ? where id = ? and lifted_at is null",
                Timestamp.valueOf(LocalDateTime.now()), admin.get("id"), restrictionId);
        auditLog.write(AuditLog.entry(AuditLog.Type.UPDATE, "restriction lifted").actor(AuditLog.Actor.admin(admin)).resource("student", r.get(0).get("user_id"))
                .summary(String.valueOf(r.get(0).get("kind"))));
        return true;
    }

    // ------------------------------------------------------------------ reports

    public enum Filed { FILED, ALREADY, UNKNOWN_TARGET }

    public Filed report(String type, long id, String reason, String detail, Long reporter, String ip) {
        if (!exists(type, id)) return Filed.UNKNOWN_TARGET;
        try {
            jdbc.update("insert into content_reports (target_type, target_id, reason_code, detail, reporter_user_id, reporter_ip, status, created_at)"
                    + " values (?, ?, ?, ?, ?, ?, 'OPEN', ?)", type, id, reason, detail, reporter, ip, Timestamp.valueOf(LocalDateTime.now()));
            return Filed.FILED;
        } catch (RuntimeException e) {
            if (DbErrors.isDuplicateKey(e)) return Filed.ALREADY;
            throw e;
        }
    }

    private boolean exists(String type, long id) {
        String sql = switch (type) {
            case "PAINTING" -> "select count(*) from paintings where id = ?";
            case "ARTWORK" -> "select count(*) from student_artworks where id = ?";
            case "REVIEW" -> "select count(*) from course_reviews where id = ?";
            case "SUBMISSION" -> "select count(*) from student_submissions where id = ?";
            case "USER" -> "select count(*) from users where id = ?";
            default -> null;
        };
        if (sql == null) return false;
        Integer n = jdbc.queryForObject(sql, Integer.class, id);
        return n != null && n > 0;
    }

    /** Hides the reported items (closing their open reports as ACTIONED). Returns how many items were hidden. */
    public int hide(List<long[]> targets, List<String> types, Row admin, String note) {
        int hidden = 0;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        for (int i = 0; i < targets.size(); i++) {
            String type = types.get(i);
            long id = targets.get(i)[0];
            int changed = switch (type) {
                case "PAINTING" -> jdbc.update("update paintings set stock_status = 'ARCHIVED', updated_at = ? where id = ? and stock_status in ('AVAILABLE', 'PENDING_REVIEW', 'DRAFT')", now, id);
                case "ARTWORK" -> jdbc.update("update student_artworks set status = 0 where id = ?", id);
                case "REVIEW" -> jdbc.update("update course_reviews set status = 0 where id = ?", id);
                case "SUBMISSION" -> jdbc.update("update student_submissions set admin_status = 'Rejected' where id = ?", id);
                default -> 0;   // USER: restrict the account instead
            };
            jdbc.update("update content_reports set status = 'ACTIONED', action = 'HIDDEN', decided_by = ?, decided_at = ?, note = ? where target_type = ? and target_id = ? and status = 'OPEN'",
                    admin.get("id"), now, note, type, id);
            if (changed > 0) hidden++;
            auditLog.write(AuditLog.entry(AuditLog.Type.UPDATE, "reported content hidden").actor(AuditLog.Actor.admin(admin)).resource(type.toLowerCase(), id).summary(note));
        }
        return hidden;
    }

    public int dismiss(List<long[]> targets, List<String> types, Row admin, String note) {
        int n = 0;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        for (int i = 0; i < targets.size(); i++) {
            n += jdbc.update("update content_reports set status = 'DISMISSED', action = 'NONE', decided_by = ?, decided_at = ?, note = ? where target_type = ? and target_id = ? and status = 'OPEN'",
                    admin.get("id"), now, note, types.get(i), targets.get(i)[0]);
        }
        auditLog.write(AuditLog.entry(AuditLog.Type.UPDATE, "reports dismissed").actor(AuditLog.Actor.admin(admin)).resource("content_reports", null).summary(n + " report(s) · " + note));
        return n;
    }
}
