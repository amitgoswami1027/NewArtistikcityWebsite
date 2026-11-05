package com.artistikcity.notices;

import com.artistikcity.admin.AdminKit;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin: message the students of a course, move its start date, cancel the run (course editor > Students tab).
 * Students: mark a studio notice as read. Instructors can message and reschedule their own courses; cancelling a run
 * (which queues refunds) needs the {@code settings} capability (super admins).
 */
@Controller
public class CourseNoticeController {

    private final CourseNotices notices;
    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final Auth auth;

    public CourseNoticeController(CourseNotices notices, AdminKit kit, Db db, Json json, Auth auth) {
        this.notices = notices;
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.auth = auth;
    }

    private Row ownCourse(Row admin, long id) {
        Long teacher = kit.teacherScope(admin);
        return db.first("select id, title, course_start_date, status, cancelled_at, cancel_reason from courses where id = ?" + (teacher == null ? "" : " and admin_id = ?"),
                teacher == null ? new Object[]{id} : new Object[]{id, teacher});
    }

    @GetMapping("/admin/api/courses/{id}/messages")
    public ResponseEntity<String> history(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "catalog")) return kit.forbidden();
        Row c = ownCourse(admin, id);
        if (c == null) return kit.forbidden();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("course", c);
        body.put("recipients", notices.recipients(id).size());
        body.put("messages", notices.history(id));
        body.put("canCancel", kit.can(admin, "settings"));
        return kit.ok(body);
    }

    @GetMapping("/admin/api/course-messages/{id}/deliveries")
    public ResponseEntity<String> deliveries(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "catalog")) return kit.forbidden();
        Row m = db.first("select course_id from course_messages where id = ?", id);
        if (m == null || ownCourse(admin, m.lng("course_id")) == null) return kit.forbidden();
        return kit.ok(Map.of("deliveries", notices.deliveries(id)));
    }

    @PostMapping("/admin/api/courses/{id}/messages")
    public ResponseEntity<String> send(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "catalog")) return kit.forbidden();
        if (ownCourse(admin, id) == null) return kit.forbidden();
        Input in = Input.of(request, json);
        String subject = AdminKit.trim(in.get("subject"), 200);
        String body = in.get("body") == null ? "" : in.get("body").trim();
        Map<String, String> errors = new LinkedHashMap<>();
        if (AdminKit.blank(subject)) errors.put("subject", "Add a subject.");
        if (body.isEmpty()) errors.put("body", "Write the message.");
        else if (body.length() > 10000) errors.put("body", "Keep it under 10,000 characters.");
        if (!errors.isEmpty()) return kit.invalid(errors);
        if (notices.recipients(id).isEmpty()) return kit.invalid(Map.of("body", "Nobody is enrolled in this course yet."));
        CourseNotices.Sent s = notices.message(id, admin, "MESSAGE", subject, body, !in.has("email") || in.bool("email"));
        Map<String, Object> out = new LinkedHashMap<>(s.toMap());
        out.put("message", "Sent to " + s.recipients() + " student(s)" + (s.emailed() > 0 ? ", " + s.emailed() + " by e-mail" : "") + (s.failed() > 0 ? " - " + s.failed() + " e-mail(s) failed" : "") + ".");
        return kit.ok(out);
    }

    @PostMapping("/admin/api/courses/{id}/reschedule")
    public ResponseEntity<String> reschedule(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "catalog")) return kit.forbidden();
        Row c = ownCourse(admin, id);
        if (c == null) return kit.forbidden();
        if (c.get("cancelled_at") != null) return kit.invalid(Map.of("date", "This run was cancelled."));
        Input in = Input.of(request, json);
        LocalDate date;
        try {
            date = LocalDate.parse(String.valueOf(in.get("date")));
        } catch (DateTimeParseException e) {
            return kit.invalid(Map.of("date", "Choose the new start date."));
        }
        if (date.isBefore(LocalDate.now())) return kit.invalid(Map.of("date", "The new date can't be in the past."));
        CourseNotices.Sent s = notices.reschedule(id, admin, date, AdminKit.trim(in.get("note"), 2000));
        Map<String, Object> out = new LinkedHashMap<>(s.toMap());
        out.put("message", "Start date moved. " + s.recipients() + " student(s) told.");
        return kit.ok(out);
    }

    @PostMapping("/admin/api/courses/{id}/cancel")
    public ResponseEntity<String> cancel(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) return kit.forbidden();
        Row c = ownCourse(admin, id);
        if (c == null) return kit.forbidden();
        if (c.get("cancelled_at") != null) return kit.invalid(Map.of("reason", "This run is already cancelled."));
        Input in = Input.of(request, json);
        String reason = AdminKit.trim(in.get("reason"), 500);
        if (AdminKit.blank(reason)) return kit.invalid(Map.of("reason", "Tell the students why (it goes into the e-mail)."));
        if (!String.valueOf(c.get("title")).equals(in.get("confirmTitle"))) return kit.invalid(Map.of("confirmTitle", "Type the course title exactly to confirm."));
        int pct = 100;
        if (in.get("refundPercent") != null && !in.get("refundPercent").isBlank()) {
            try {
                pct = Integer.parseInt(in.get("refundPercent").trim());
            } catch (NumberFormatException e) {
                pct = -1;
            }
            if (pct < 1 || pct > 100) return kit.invalid(Map.of("refundPercent", "Enter a percentage from 1 to 100."));
        }
        Map<String, Object> out = new LinkedHashMap<>(notices.cancel(id, admin, reason, !in.has("refund") || in.bool("refund"), pct));
        out.put("message", "Cancelled. " + out.get("recipients") + " student(s) told, " + out.get("refundsQueued") + " refund(s) waiting in Sales > Refunds.");
        return kit.ok(out);
    }

    // ------------------------------------------------------------------ students

    @PostMapping("/dashboard/notices/{id}/read")
    public ResponseEntity<String> read(HttpServletRequest request, @PathVariable("id") long id) {
        Long uid = auth.userId(request);
        if (uid == null) return ResponseEntity.status(401).contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"Unauthenticated.\"}");
        notices.markRead(uid, id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of("unread", notices.unread(uid))));
    }
}
