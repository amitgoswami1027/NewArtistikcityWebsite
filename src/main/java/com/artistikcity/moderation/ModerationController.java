package com.artistikcity.moderation;

import com.artistikcity.admin.AdminKit;
import com.artistikcity.admin.Grid;
import com.artistikcity.auth.RateLimiter;
import com.artistikcity.http.Auth;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The Report button ({@code POST /reports}, signed-in visitors, 10 an hour) and Admin > Community > Reports & bans
 * ({@code /admin/moderation}): the moderation queue grouped per reported item with reason codes, bulk hide / dismiss,
 * and account restrictions. Capability {@code moderation}; restricting accounts needs {@code moderation.ban}.
 */
@Controller
public class ModerationController {

    private final Moderation moderation;
    private final Auth auth;
    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final RateLimiter limiter;

    public ModerationController(Moderation moderation, Auth auth, AdminKit kit, Db db, Json json, RateLimiter limiter) {
        this.moderation = moderation;
        this.auth = auth;
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.limiter = limiter;
    }

    private ResponseEntity<String> reply(int status, Object body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    // ------------------------------------------------------------------ visitors

    @PostMapping("/reports")
    public ResponseEntity<String> report(HttpServletRequest request) {
        Long uid = auth.userId(request);
        if (uid == null) return reply(401, Map.of("message", "Sign in to report something.", "reauth", true));
        if (!limiter.hit("reports:" + uid, 10, 3600).allowed()) return reply(429, Map.of("message", "You've sent a lot of reports. Thank you - our team will look at them."));
        Input in = Input.of(request, json);
        String type = String.valueOf(in.get("targetType")).toUpperCase();
        Long id = in.lng("targetId");
        String reason = String.valueOf(in.get("reason")).toUpperCase();
        if (!Moderation.TARGETS.contains(type) || id == null) return reply(422, Map.of("message", "Nothing to report."));
        if (!Moderation.REASONS.contains(reason)) return reply(422, Map.of("message", "Choose a reason.", "errors", Map.of("reason", List.of("Choose a reason."))));
        String detail = AdminKit.trim(in.get("detail"), 1000);
        if ("OTHER".equals(reason) && AdminKit.blank(detail)) return reply(422, Map.of("message", "Tell us what is wrong.", "errors", Map.of("detail", List.of("Tell us what is wrong."))));
        Moderation.Filed f = moderation.report(type, id, reason, detail, uid, request.getRemoteAddr());
        return switch (f) {
            case FILED -> reply(200, Map.of("message", "Thanks - our team will review it. We don't tell the owner who reported it."));
            case ALREADY -> reply(200, Map.of("message", "You've already reported this. Our team will review it."));
            case UNKNOWN_TARGET -> reply(404, Map.of("message", "This item no longer exists."));
        };
    }

    // ------------------------------------------------------------------ staff

    /** Open reports, one row per reported item, with counts per reason. */
    private Grid queue(HttpServletRequest request) {
        return Grid.from(db, request)
                .columns("r.target_type, r.target_id, count(*) as reports, min(r.created_at) as first_at, max(r.created_at) as last_at,"
                        + " group_concat(distinct r.reason_code order by r.reason_code) as reasons,"
                        + " substring(group_concat(r.detail order by r.id desc separator ' | '), 1, 400) as details, max(r.status) as status")
                .from("content_reports r")
                .search("r.detail", "r.reason_code")
                .sort("reports", "count(*)").sort("last", "max(r.created_at)").defaultSort("reports", "desc").tieBreak("max(r.id) desc")
                .filter("status", v -> List.of("OPEN", "ACTIONED", "DISMISSED").contains(v) ? Grid.where("r.status = ?", v) : null)
                .filter("type", v -> Moderation.TARGETS.contains(v) ? Grid.where("r.target_type = ?", v) : null)
                .filter("reason", v -> Moderation.REASONS.contains(v) ? Grid.where("r.reason_code = ?", v) : null)
                .where(request.getParameter("status") == null ? Grid.where("r.status = 'OPEN'") : null)   // the queue opens on what's still open
                .groupBy("r.target_type, r.target_id");
    }

    @GetMapping({"/admin/moderation", "/admin/moderation/"})
    public ResponseEntity<String> index(HttpServletRequest request) {
        return kit.page(request, "moderation", "Admin/Moderation", () -> {
            Map<String, Object> p = new LinkedHashMap<>();
            Supplier<Object> grid = () -> {
                Map<String, Object> page = queue(request).page();
                @SuppressWarnings("unchecked") List<Row> rows = (List<Row>) page.get("rows");
                for (Row r : rows) r.put("target", describe(r.str("target_type"), r.lng("target_id")));
                return page;
            };
            p.put("grid", grid);
            p.put("restrictions", db.select("select x.*, u.name, u.email, a.name as by_name from account_restrictions x join users u on u.id = x.user_id"
                    + " left join admins a on a.id = x.created_by where x.lifted_at is null and (x.until is null or x.until > ?) order by x.id desc limit 200", LocalDateTime.now()));
            p.put("canBan", kit.can(kit.admin(request), "moderation.ban"));
            p.put("counts", Map.of("open", db.count("select count(distinct target_type, target_id) from content_reports where status = 'OPEN'")));
            return p;
        });
    }

    /** A short description of a reported item, with links for staff. */
    private Map<String, Object> describe(String type, Long id) {
        Map<String, Object> m = new LinkedHashMap<>();
        Row r = switch (type) {
            case "PAINTING" -> db.first("select title as label, slug, stock_status as state, artist_name as owner, artist_user_id as owner_id from paintings where id = ?", id);
            case "ARTWORK" -> db.first("select a.photo_name as label, a.status as state, u.name as owner, a.user_id as owner_id from student_artworks a left join users u on u.id = a.user_id where a.id = ?", id);
            case "REVIEW" -> db.first("select substring(r.review, 1, 160) as label, r.status as state, u.name as owner, r.added_by_id as owner_id from course_reviews r"
                    + " left join users u on u.id = r.added_by_id and r.added_by_type = 'Student' where r.id = ?", id);
            case "SUBMISSION" -> db.first("select s.title as label, s.admin_status as state, u.name as owner, s.user_id as owner_id from student_submissions s left join users u on u.id = s.user_id where s.id = ?", id);
            case "USER" -> db.first("select name as label, email as state, name as owner, id as owner_id from users where id = ?", id);
            default -> null;
        };
        if (r != null) m.putAll(r);
        m.put("url", switch (type) {
            case "PAINTING" -> r == null ? null : "/marketplace/" + r.str("slug", String.valueOf(id));
            case "USER" -> "/admin/students?q=" + id;
            default -> null;
        });
        return m;
    }

    @GetMapping("/admin/api/moderation/reports")
    public ResponseEntity<String> reports(HttpServletRequest request) {
        if (!kit.can(kit.admin(request), "moderation")) return kit.forbidden();
        String type = request.getParameter("type");
        String id = request.getParameter("id");
        if (type == null || id == null || !id.matches("\\d+")) return kit.invalid(Map.of("id", "Which item?"));
        return kit.ok(Map.of("reports", db.select("select r.id, r.reason_code, r.detail, r.status, r.created_at, r.note, u.name as reporter, u.email as reporter_email"
                + " from content_reports r left join users u on u.id = r.reporter_user_id where r.target_type = ? and r.target_id = ? order by r.id desc", type, Long.parseLong(id)),
                "target", describe(type, Long.parseLong(id))));
    }

    /** {items: [{type, id}], action: hide|dismiss, note}. */
    @PostMapping("/admin/api/moderation/decide")
    @SuppressWarnings("unchecked")
    public ResponseEntity<String> decide(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "moderation")) return kit.forbidden();
        Map<String, Object> body = json.decodeMap(readBody(request));
        if (body == null || !(body.get("items") instanceof List<?> items) || items.isEmpty() || items.size() > 200) {
            return kit.invalid(Map.of("items", "Select at least one item (up to 200)."));
        }
        List<long[]> ids = new ArrayList<>();
        List<String> types = new ArrayList<>();
        for (Object o : items) {
            if (!(o instanceof Map<?, ?> m) || !Moderation.TARGETS.contains(String.valueOf(m.get("type"))) || !(m.get("id") instanceof Number n)) continue;
            ids.add(new long[]{n.longValue()});
            types.add(String.valueOf(m.get("type")));
        }
        String note = AdminKit.trim(body.get("note") == null ? null : String.valueOf(body.get("note")), 500);
        String action = String.valueOf(body.get("action"));
        if ("hide".equals(action)) {
            int n = moderation.hide(ids, types, admin, note);
            return kit.ok(Map.of("message", n + " item(s) hidden; their reports are closed."));
        }
        if ("dismiss".equals(action)) {
            int n = moderation.dismiss(ids, types, admin, note);
            return kit.ok(Map.of("message", n + " report(s) dismissed."));
        }
        return kit.invalid(Map.of("action", "Choose hide or dismiss."));
    }

    private static String readBody(HttpServletRequest request) {
        try {
            return new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** {userId | email, kind, reason, until (yyyy-MM-ddTHH:mm or empty)}. */
    @PostMapping("/admin/api/moderation/restrictions")
    public ResponseEntity<String> restrict(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "moderation.ban")) return kit.forbidden();
        Input in = Input.of(request, json);
        Long uid = in.lng("userId");
        if (uid == null && !AdminKit.blank(in.get("email"))) {
            Row u = db.first("select id from users where lower(email) = ?", in.get("email").trim().toLowerCase());
            uid = u == null ? null : u.lng("id");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (uid == null || db.find("users", uid) == null) errors.put("email", "No student account with that e-mail.");
        String kind = String.valueOf(in.get("kind")).toUpperCase();
        if (!Moderation.KINDS.contains(kind)) errors.put("kind", "Choose what to restrict.");
        String reason = AdminKit.trim(in.get("reason"), 500);
        if (AdminKit.blank(reason)) errors.put("reason", "Write the reason (kept in the log; shown to staff only).");
        LocalDateTime until = null;
        String u = in.get("until");
        if (!AdminKit.blank(u)) {
            try {
                until = LocalDateTime.parse(u.length() == 16 ? u + ":00" : u);
                if (until.isBefore(LocalDateTime.now())) errors.put("until", "Choose a time in the future, or leave it empty.");
            } catch (DateTimeParseException e) {
                errors.put("until", "Choose a date and time, or leave it empty.");
            }
        }
        if (!errors.isEmpty()) return kit.invalid(errors);
        moderation.restrict(uid, kind, reason, until, admin);
        return kit.ok(Map.of("message", "BAN".equals(kind) ? "Account suspended; every session was signed out." : "Restriction saved."));
    }

    @PostMapping("/admin/api/moderation/restrictions/{id}/lift")
    public ResponseEntity<String> lift(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "moderation.ban")) return kit.forbidden();
        return moderation.lift(id, admin) ? kit.ok(Map.of("message", "Restriction lifted.")) : kit.invalid(Map.of("id", "Already lifted."));
    }
}
