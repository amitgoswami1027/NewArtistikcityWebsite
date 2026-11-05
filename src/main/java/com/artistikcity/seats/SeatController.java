package com.artistikcity.seats;

import com.artistikcity.admin.AdminKit;
import com.artistikcity.admin.Audit;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seats for students ({@code POST /courses/{id}/waitlist}: join / leave the waitlist of a full course) and for staff
 * (Admin > Learning > Seats & waitlist, {@code /admin/seats}: seat use per capped course, the waitlist, invitations and
 * priority access links). Instructors only see their own courses.
 */
@Controller
public class SeatController {

    private final SeatService seats;
    private final Auth auth;
    private final Db db;
    private final Json json;
    private final AdminKit kit;
    private final Audit audit;

    public SeatController(SeatService seats, Auth auth, Db db, Json json, AdminKit kit, Audit audit) {
        this.seats = seats;
        this.auth = auth;
        this.db = db;
        this.json = json;
        this.kit = kit;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ students

    @PostMapping("/courses/{id}/waitlist")
    public ResponseEntity<String> waitlist(HttpServletRequest request, @PathVariable("id") long id) {
        Row user = auth.user(request);
        if (user == null) {
            return reply(401, Map.of("message", "Sign in to join the waitlist.", "login", "/login?return_to=" + request.getHeader("Referer")));
        }
        Input in = Input.of(request, json);
        if ("leave".equals(in.get("action"))) {
            seats.leaveWaitlist(id, user.lng("id"));
            return reply(200, Map.of("message", "You left the waitlist.", "seats", seats.forStudent(id, user.lng("id"))));
        }
        SeatService.Join j = seats.joinWaitlist(id, user.lng("id"), user.str("email"));
        String message = switch (j) {
            case JOINED -> "You're on the waitlist. We'll e-mail you the moment a seat frees up - it's then kept for you for 24 hours.";
            case ALREADY -> "You're already on the waitlist.";
            case NOT_FULL -> "Good news: there are seats free right now. Enrol straight away.";
            case ENROLLED -> "You're already enrolled in this course.";
            case NO_CAP -> "This course has no seat limit. Enrol straight away.";
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("result", j.name());
        body.put("seats", seats.forStudent(id, user.lng("id")));
        return reply(j == SeatService.Join.JOINED || j == SeatService.Join.ALREADY ? 200 : 409, body);
    }

    private ResponseEntity<String> reply(int status, Object body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    // ------------------------------------------------------------------ staff

    @GetMapping({"/admin/seats", "/admin/seats/"})
    public ResponseEntity<String> index(HttpServletRequest request) {
        Row admin = kit.admin(request);
        return kit.page(request, "catalog", "Admin/Seats", () -> {
            Long teacher = kit.teacherScope(admin);
            List<Row> courses = db.select("select c.id, c.title, c.slug, c.seat_limit, c.course_start_date, c.course_type_id from courses c"
                    + " where c.seat_limit is not null" + (teacher == null ? "" : " and c.admin_id = ?") + " order by c.course_start_date desc, c.id desc limit 200",
                    teacher == null ? new Object[0] : new Object[]{teacher});
            for (Row c : courses) {
                SeatService.Seats s = seats.seats(c.lng("id"));
                c.put("taken", s.taken());
                c.put("left", s.left());
                c.put("waiting", db.count("select count(*) from course_waitlist where course_id = ? and status = 'WAITING'", c.get("id")));
            }
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("courses", courses);
            p.put("uncapped", db.select("select id as value, title as label from courses where seat_limit is null and status = 1"
                    + (teacher == null ? "" : " and admin_id = ?") + " order by title limit 300", teacher == null ? new Object[0] : new Object[]{teacher}));
            return p;
        });
    }

    private Row course(HttpServletRequest request, Row admin, long id) {
        Long teacher = kit.teacherScope(admin);
        return db.first("select id, title, seat_limit, admin_id from courses where id = ?" + (teacher == null ? "" : " and admin_id = ?"),
                teacher == null ? new Object[]{id} : new Object[]{id, teacher});
    }

    @GetMapping("/admin/api/seats/{id}")
    public ResponseEntity<String> show(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "catalog")) return kit.forbidden();
        Row c = course(request, admin, id);
        if (c == null) return kit.forbidden();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("course", c);
        body.put("seats", seats.seats(id).toMap());
        body.put("waitlist", db.select("select w.id, w.status, w.email, w.created_at, u.name from course_waitlist w left join users u on u.id = w.user_id"
                + " where w.course_id = ? and w.status in ('WAITING', 'INVITED') order by w.id", id));
        body.put("grants", db.select("select id, email, kind, holds_seat, expires_at, used_at, revoked_at, note, created_at from access_grants"
                + " where course_id = ? order by id desc limit 100", id));
        body.put("holds", db.select("select h.status, h.expires_at, h.updated_at, u.email from seat_holds h left join users u on u.id = h.user_id"
                + " where h.course_id = ? and h.status in ('HELD', 'PAID') order by h.status, h.updated_at desc limit 200", id));
        return kit.ok(body);
    }

    /** Sets or clears a course's seat cap: {limit: n | null}. */
    @PostMapping("/admin/api/seats/{id}/limit")
    public ResponseEntity<String> limit(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "catalog")) return kit.forbidden();
        Row c = course(request, admin, id);
        if (c == null) return kit.forbidden();
        Input in = Input.of(request, json);
        String raw = in.get("limit") == null ? "" : in.get("limit").trim();
        Integer limit = null;
        if (!raw.isEmpty()) {
            if (!raw.matches("\\d{1,5}") || Integer.parseInt(raw) < 1) return kit.invalid(Map.of("limit", "Enter a number of seats (1 or more), or leave it empty for no limit."));
            limit = Integer.parseInt(raw);
        }
        Row before = audit.snapshot("courses", id);
        db.updateById("courses", id, Row.of("seat_limit", limit));
        audit.change(admin, "course", id, "seat limit " + (limit == null ? "removed" : "set to " + limit), c.str("title"), before, audit.snapshot("courses", id));
        int invited = limit == null ? 0 : seats.inviteWaiting(id);
        return kit.ok(Map.of("message", limit == null ? "No seat limit now." : "Seat limit saved." + (invited > 0 ? " " + invited + " waiting student(s) invited." : ""),
                "seats", seats.seats(id).toMap()));
    }

    /** {email, hours, holdsSeat, note}: e-mails a priority access link. */
    @PostMapping("/admin/api/seats/{id}/priority")
    public ResponseEntity<String> priority(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "catalog")) return kit.forbidden();
        Row c = course(request, admin, id);
        if (c == null) return kit.forbidden();
        Input in = Input.of(request, json);
        String email = in.get("email") == null ? "" : in.get("email").trim().toLowerCase();
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 255) return kit.invalid(Map.of("email", "Enter the student's e-mail address."));
        int hours;
        try {
            hours = Integer.parseInt(in.get("hours") == null ? "48" : in.get("hours").trim());
        } catch (NumberFormatException e) {
            return kit.invalid(Map.of("hours", "Enter how many hours the link stays valid."));
        }
        if (hours < 1 || hours > 720) return kit.invalid(Map.of("hours", "Between 1 hour and 30 days."));
        Row u = db.first("select id from users where lower(email) = ?", email);
        String token = seats.issuePriority(id, email, u == null ? null : u.lng("id"), hours, in.bool("holdsSeat"), admin.lng("id"), AdminKit.trim(in.get("note"), 255));
        audit.log(admin, "course", id, "priority access sent", email + " · " + hours + " h");
        Row slugRow = db.first("select slug from courses where id = ?", id);
        // shown once so staff can also paste it into WhatsApp; only its hash is stored
        String link = com.artistikcity.inertia.Inertia.baseUrl(request) + "/checkout?course=" + slugRow.str("slug") + "&access=" + token;
        return kit.ok(Map.of("message", "Priority link e-mailed to " + email + ".", "link", link));
    }

    @PostMapping("/admin/api/seats/grants/{grantId}/revoke")
    public ResponseEntity<String> revoke(HttpServletRequest request, @PathVariable("grantId") long grantId) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "catalog")) return kit.forbidden();
        Row g = db.first("select course_id, email from access_grants where id = ?", grantId);
        if (g == null || course(request, admin, g.lng("course_id")) == null) return kit.forbidden();
        if (!seats.revoke(grantId)) return kit.invalid(Map.of("grant", "This link was already used, revoked or has expired."));
        audit.log(admin, "course", g.lng("course_id"), "priority access revoked", g.str("email"));
        return kit.ok(Map.of("message", "Link revoked. Its seat went back to the course."));
    }

    @PostMapping("/admin/api/seats/{id}/invite-next")
    public ResponseEntity<String> inviteNext(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "catalog")) return kit.forbidden();
        if (course(request, admin, id) == null) return kit.forbidden();
        int n = seats.inviteWaiting(id);
        return kit.ok(Map.of("message", n == 0 ? "No one invited: the course is full or nobody is waiting." : n + " student(s) invited."));
    }
}
