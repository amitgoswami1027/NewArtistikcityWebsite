package com.artistikcity.admin;

import com.artistikcity.auth.PasswordPolicy;
import com.artistikcity.http.Auth;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.lifecycle.Personas;
import com.artistikcity.support.Db;
import com.artistikcity.support.HtmlCleaner;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Slugs;
import com.artistikcity.support.Storage;
import com.artistikcity.support.UploadedFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Staff side of the console: instructor public profiles (/admin/instructors), the signed-in admin's own
 * profile and password, and the student task inbox (/admin/tasks) where instructors answer practice tasks.
 */
@Controller
public class PeopleAdminController {

    static final List<String> TASK_STATUS = List.of("Under Review", "Feedback", "Reviewed", "Completed");

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final Auth auth;
    private final Storage storage;
    private final Slugs slugs;
    private final Audit audit;

    public PeopleAdminController(AdminKit kit, Db db, Json json, Auth auth, Storage storage, Slugs slugs, Audit audit) {
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.auth = auth;
        this.storage = storage;
        this.slugs = slugs;
        this.audit = audit;
    }

    // ============================================================ instructors

    @GetMapping({"/admin/instructors", "/admin/instructors/"})
    public ResponseEntity<String> instructors(HttpServletRequest request) {
        return kit.page(request, "people.manage", "Admin/Instructors", () -> {
            Map<String, Object> p = new LinkedHashMap<>();
            Supplier<Object> grid = () -> Grid.from(db, request)
                    .columns("a.id, a.name, a.email, a.slug, a.admin_type, a.location, a.designation, a.profile_title, a.profile_photo, a.profile_description, a.created_at,"
                            + " (select count(*) from courses c where c.admin_id = a.id) as courses,"
                            + " (select count(*) from courses c where c.admin_id = a.id and c.status = 1) as live_courses")
                    .from("admins a")
                    .search("a.name", "a.email", "a.designation")
                    .sort("name", "a.name").sort("courses", "courses").sort("date", "a.created_at").defaultSort("name", "asc").tieBreak("a.id")
                    .filter("role", v -> Personas.ALL.contains(v) ? Grid.where("a.admin_type = ?", v) : null)
                    .map(this::staffView)
                    .page();
            p.put("grid", grid);
            p.put("roles", Personas.ALL.stream().map(x -> Map.of("value", x, "label", Personas.label(x))).toList());
            return p;
        });
    }

    private Row staffView(Row a) {
        a.put("photo_url", a.str("profile_photo", "").isBlank() ? null : "/storage/uploads/teachers/" + a.get("id") + "/" + a.str("profile_photo"));
        a.put("role_label", Personas.label(Personas.of(a)));
        a.put("public_url", a.str("slug", "").isBlank() ? null : "/teacher/" + a.str("slug"));
        return a;
    }

    @PostMapping("/admin/api/instructors")
    public ResponseEntity<String> saveInstructor(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "people.manage")) {
            return kit.forbidden();
        }
        Input in = Input.of(request, json);
        Long id = in.lng("id");
        Row existing = id == null ? null : NotFoundException.orFail(db.find("admins", id));
        Map<String, String> errors = new LinkedHashMap<>();
        String name = AdminKit.trim(in.get("name"), 100);
        if (AdminKit.blank(name)) {
            errors.put("name", "Enter the name shown on the site.");
        }
        String email = in.get("email") == null ? "" : in.get("email").trim().toLowerCase();
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            errors.put("email", "Enter a valid e-mail address; it is used to sign in to the admin.");
        } else if (db.count("select count(*) from admins where lower(email) = ? and id <> ?", email, id == null ? 0 : id) > 0) {
            errors.put("email", "Another admin account already uses this e-mail.");
        }
        String slug = in.get("slug") == null ? "" : in.get("slug").trim().toLowerCase();
        if (!slug.isEmpty() && !slug.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$")) {
            errors.put("slug", "Use lowercase letters, numbers and single dashes only.");
        } else if (!slug.isEmpty() && db.count("select count(*) from admins where slug = ? and id <> ?", slug, id == null ? 0 : id) > 0) {
            errors.put("slug", "Another profile already uses this URL.");
        }
        String role = in.get("admin_type") == null ? "teacher" : in.get("admin_type");
        if (!Personas.ALL.contains(role)) {
            errors.put("admin_type", "Choose a role.");
        }
        if (existing != null && existing.lng("id").equals(admin.lng("id")) && !"admin".equals(role)) {
            errors.put("admin_type", "You can't remove your own super admin role.");
        }
        String password = in.get("password");
        if (existing == null || !AdminKit.blank(password)) {
            String problem = PasswordPolicy.problem(password, email, name);
            if (problem != null) {
                errors.put("password", existing == null ? "Set a first password: " + problem.toLowerCase() : problem);
            }
        }
        UploadedFile photo = in.hasFile("photo") ? in.file("photo") : null;
        String photoError = AdminKit.checkImage(photo);
        if (photoError != null) {
            errors.put("photo", photoError);
        }
        if (!errors.isEmpty()) {
            return kit.invalid(errors);
        }
        Row v = Row.of("name", name, "email", email, "slug", slug.isEmpty() ? slugs.unique("admins", "slug", name, id) : slug,
                "admin_type", role, "profile_title", AdminKit.trim(in.get("profile_title"), 255), "designation", AdminKit.trim(in.get("designation"), 255),
                "location", AdminKit.trim(in.get("location"), 255), "profile_description", HtmlCleaner.clean(in.get("profile_description")));
        if (!AdminKit.blank(password)) {
            v.put("password", auth.hash(password));
        }
        if (existing == null) {
            id = db.insert("admins", v);
            audit.log(admin, "admin", id, "created", name + " (" + role + ")");
        } else {
            db.updateById("admins", id, v);
            audit.log(admin, "admin", id, "updated", name + (AdminKit.blank(password) ? "" : " · password changed"));
        }
        if (photo != null) {
            db.updateById("admins", id, Row.of("profile_photo", storage.storeUpload(photo, "uploads/teachers/" + id)));
        }
        return kit.ok(Map.of("message", existing == null ? "Account created. Share the password with them securely." : "Saved."));
    }

    @DeleteMapping("/admin/api/instructors/{id}")
    public ResponseEntity<String> deleteInstructor(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "people.manage")) {
            return kit.forbidden();
        }
        Row a = NotFoundException.orFail(db.find("admins", id));
        if (a.lng("id").equals(admin.lng("id"))) {
            return kit.fail(HttpStatus.CONFLICT, "You can't delete your own account.");
        }
        long courses = db.count("select count(*) from courses where admin_id = ?", id);
        if (courses > 0) {
            return kit.fail(HttpStatus.CONFLICT, a.str("name") + " teaches " + courses + (courses == 1 ? " course" : " courses")
                    + ". Move them to another instructor first.");
        }
        if ("admin".equals(a.str("admin_type")) && db.count("select count(*) from admins where admin_type = 'admin'") <= 1) {
            return kit.fail(HttpStatus.CONFLICT, "This is the last super admin account.");
        }
        db.deleteById("admins", id);
        audit.log(admin, "admin", id, "deleted", a.str("name"));
        return kit.ok(Map.of("message", "Deleted " + a.str("name") + "'s account."));
    }

    // ============================================================ own profile

    @GetMapping({"/admin/profile", "/admin/change-password"})
    public ResponseEntity<String> profile(HttpServletRequest request) {
        Row admin = kit.admin(request);
        Map<String, Object> p = new LinkedHashMap<>();
        Row me = staffView(new Row(admin));
        me.remove("password");
        p.put("me", me);
        p.put("tab", request.getRequestURI().endsWith("change-password") ? "password" : "profile");
        return kit.page(request, "Admin/Profile", p);
    }

    @PostMapping({"/admin/api/profile", "/admin/save-profile"})
    public ResponseEntity<String> saveProfile(HttpServletRequest request) {
        Row admin = kit.admin(request);
        Input in = Input.of(request, json);
        Map<String, String> errors = new LinkedHashMap<>();
        String name = AdminKit.trim(in.get("name"), 100);
        if (AdminKit.blank(name)) {
            errors.put("name", "Enter your name.");
        }
        UploadedFile photo = in.hasFile("photo") ? in.file("photo") : null;
        String photoError = AdminKit.checkImage(photo);
        if (photoError != null) {
            errors.put("photo", photoError);
        }
        if (!errors.isEmpty()) {
            return kit.invalid(errors);
        }
        long id = admin.lng("id");
        db.updateById("admins", id, Row.of("name", name, "profile_title", AdminKit.trim(in.get("profile_title"), 255),
                "designation", AdminKit.trim(in.get("designation"), 255), "location", AdminKit.trim(in.get("location"), 255),
                "profile_description", HtmlCleaner.clean(in.get("profile_description"))));
        if (photo != null) {
            db.updateById("admins", id, Row.of("profile_photo", storage.storeUpload(photo, "uploads/teachers/" + id)));
        }
        auth.refresh(request);
        return kit.ok(Map.of("message", "Profile saved."));
    }

    @PostMapping({"/admin/api/password", "/admin/update-password"})
    public ResponseEntity<String> password(HttpServletRequest request) {
        Row admin = kit.admin(request);
        Input in = Input.of(request, json);
        Map<String, String> errors = new LinkedHashMap<>();
        Row hash = db.first("select password from admins where id = ?", admin.get("id"));
        if (AdminKit.blank(in.get("current_password")) || !auth.check(in.get("current_password"), hash.str("password"))) {
            errors.put("current_password", "That isn't your current password.");
        }
        String next = in.get("new_password");
        String problem = PasswordPolicy.problem(next, admin.str("email"), admin.str("name"));
        if (problem != null) {
            errors.put("new_password", problem);
        } else if (next.equals(in.get("current_password"))) {
            errors.put("new_password", "Choose a password you haven't used here before.");
        }
        if (next != null && !next.equals(in.get("confirm_password"))) {
            errors.put("confirm_password", "The two passwords don't match.");
        }
        if (!errors.isEmpty()) {
            return kit.invalid(errors);
        }
        db.updateById("admins", admin.get("id"), Row.of("password", auth.hash(next)));
        audit.log(admin, "admin", admin.lng("id"), "password changed", null);
        return kit.ok(Map.of("message", "Password changed."));
    }

    // ============================================================ student tasks

    private Grid taskGrid(HttpServletRequest request, Long teacher) {
        String latest = "(select s.review_status from student_tasks s where s.student_teacher_connect_id = x.id and s.from_type = 'Student'"
                + " and s.deleted_at is null order by s.id desc limit 1)";
        return Grid.from(db, request)
                .columns("x.id, x.created_at, u.id as student_id, u.name as student, u.email as student_email, t.task_title, l.lesson_title,"
                        + " c.id as course_id, c.title as course, " + latest + " as status,"
                        + " (select max(s.created_at) from student_tasks s where s.student_teacher_connect_id = x.id and s.deleted_at is null) as last_at,"
                        + " (select s.from_type from student_tasks s where s.student_teacher_connect_id = x.id and s.deleted_at is null order by s.id desc limit 1) as last_from,"
                        + " (select count(*) from student_tasks s where s.student_teacher_connect_id = x.id and s.deleted_at is null) as messages")
                .from("student_teacher_connects x join users u on u.id = x.student_id join course_module_lesson_tasks t on t.id = x.task_id"
                        + " left join course_module_lessons l on l.id = t.lesson_id left join courses c on c.id = t.course_id")
                .where(Grid.where("exists (select 1 from student_tasks s where s.student_teacher_connect_id = x.id and s.deleted_at is null)"))
                .where(teacher == null ? null : Grid.where("c.admin_id = ?", teacher))
                .search("u.name", "u.email", "t.task_title", "c.title")
                .sort("last", "last_at").sort("student", "u.name").defaultSort("last", "desc").tieBreak("x.id desc")
                .filter("status", v -> "waiting".equals(v) ? Grid.where("x.id in (select s.student_teacher_connect_id from student_tasks s where s.deleted_at is null and s.id ="
                        + " (select max(s2.id) from student_tasks s2 where s2.student_teacher_connect_id = s.student_teacher_connect_id and s2.deleted_at is null) and s.from_type = 'Student')")
                        : TASK_STATUS.contains(v) ? Grid.where(latest + " = ?", v) : null)
                .filter("course", v -> v.matches("\\d+") ? Grid.where("c.id = ?", Long.parseLong(v)) : null);
    }

    @GetMapping({"/admin/tasks", "/admin/tasks/"})
    public ResponseEntity<String> tasks(HttpServletRequest request) {
        return kit.page(request, "submissions", "Admin/Tasks", () -> {
            Long teacher = kit.teacherScope(kit.admin(request));
            Map<String, Object> p = new LinkedHashMap<>();
            Supplier<Object> grid = () -> taskGrid(request, teacher).page();
            p.put("grid", grid);
            p.put("statuses", TASK_STATUS);
            p.put("courses", teacher == null ? db.select("select id as value, title as label from courses order by title")
                    : db.select("select id as value, title as label from courses where admin_id = ? order by title", teacher));
            return p;
        });
    }

    private Row thread(HttpServletRequest request, long id) {
        Row x = NotFoundException.orFail(db.first("select x.*, c.admin_id as course_admin from student_teacher_connects x"
                + " join course_module_lesson_tasks t on t.id = x.task_id left join courses c on c.id = t.course_id where x.id = ?", id));
        Long teacher = kit.teacherScope(kit.admin(request));
        if (teacher != null && !teacher.equals(x.lng("course_admin"))) {
            throw new NotFoundException();
        }
        return x;
    }

    private Map<String, Object> threadView(long id) {
        Row x = db.first("select x.id, x.student_id, u.name as student, u.email as student_email, t.task_title, t.task_description,"
                + " l.lesson_title, c.title as course from student_teacher_connects x join users u on u.id = x.student_id"
                + " join course_module_lesson_tasks t on t.id = x.task_id left join course_module_lessons l on l.id = t.lesson_id"
                + " left join courses c on c.id = t.course_id where x.id = ?", id);
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Row m : db.select("select s.id, s.from_type, s.from_id, s.reply, s.review_status, s.created_at from student_tasks s"
                + " where s.student_teacher_connect_id = ? and s.deleted_at is null order by s.id", id)) {
            Map<String, Object> mv = new LinkedHashMap<>(m);
            Row who = "Teacher".equals(m.str("from_type")) ? db.first("select name from admins where id = ?", m.get("from_id"))
                    : db.first("select name from users where id = ?", m.get("from_id"));
            mv.put("from_name", who == null ? m.str("from_type") : who.str("name"));
            mv.put("reply", HtmlCleaner.clean(m.str("reply")));
            List<String> photos = new ArrayList<>();
            for (Row ph : db.select("select photo_name from student_task_photos where student_task_id = ? order by id", m.get("id"))) {
                photos.add("/storage/uploads/courses/task/" + m.get("id") + "/" + ph.str("photo_name"));
            }
            mv.put("photos", photos);
            messages.add(mv);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("thread", x);
        out.put("messages", messages);
        return out;
    }

    @GetMapping("/admin/api/tasks/{id}")
    public ResponseEntity<String> showTask(HttpServletRequest request, @PathVariable("id") long id) {
        if (!kit.can(kit.admin(request), "submissions")) {
            return kit.forbidden();
        }
        thread(request, id);
        return kit.ok(threadView(id));
    }

    @PostMapping("/admin/api/tasks/{id}/reply")
    public ResponseEntity<String> reply(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "submissions")) {
            return kit.forbidden();
        }
        Row x = thread(request, id);
        Input in = Input.of(request, json);
        String status = in.get("status");
        if (!TASK_STATUS.contains(status)) {
            return kit.invalid(Map.of("status", "Choose a status."));
        }
        String text = HtmlCleaner.clean(AdminKit.trim(in.get("reply"), 20000));
        if (HtmlCleaner.text(text, 10).isBlank() && "Feedback".equals(status)) {
            return kit.invalid(Map.of("reply", "Write your feedback for the student."));
        }
        LocalDateTime now = LocalDateTime.now();
        if (!HtmlCleaner.text(text, 10).isBlank()) {
            db.insert("student_tasks", Row.of("task_id", x.get("task_id"), "student_teacher_connect_id", id, "from_type", "Teacher", "from_id", admin.get("id"),
                    "to_type", "Student", "to_id", x.get("student_id"), "reply", text, "review_status", status, "review_date", now));
        }
        db.update("update student_tasks set review_status = ?, review_date = ?, updated_at = ? where student_teacher_connect_id = ? and from_type = 'Student' and deleted_at is null",
                status, now, now, id);
        audit.log(admin, "task_thread", id, "reviewed: " + status, null);
        Map<String, Object> out = threadView(id);
        out.put("message", HtmlCleaner.text(text, 10).isBlank() ? "Status updated." : "Reply sent.");
        return kit.ok(out);
    }
}
