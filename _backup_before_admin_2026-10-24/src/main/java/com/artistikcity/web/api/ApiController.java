package com.artistikcity.web.api;

import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import com.artistikcity.support.Str;
import com.artistikcity.support.Validator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON API (routes/api.php): Api\GeneralController, Api\NewsletterController, Api\ModuleController
 * and Api\TaskController. Used by the React footer/newsletter and by the admin course editor.
 */
@RestController
public class ApiController {

    private final Db db;
    private final Json json;
    private final Validator validator;
    private final Storage storage;

    public ApiController(Db db, Json json, Validator validator, Storage storage) {
        this.db = db;
        this.json = json;
        this.validator = validator;
        this.storage = storage;
    }

    private ResponseEntity<String> ok(Object body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    /** $validator->errors() returned with HTTP 200, as the original API did. */
    private ResponseEntity<String> errors(Map<String, String> errors) {
        Map<String, Object> m = new LinkedHashMap<>();
        errors.forEach((k, v) -> m.put(k, List.of(v)));
        return ok(m);
    }

    // ------------------------------------------------------------ general

    @GetMapping("/api/get_mediums")
    public ResponseEntity<String> mediums() {
        return ok(db.select("select * from mediums order by id"));
    }

    @GetMapping("/api/get_footer_data")
    public ResponseEntity<String> footerData() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("latestCourses", db.select("select id, title, slug from courses where course_type_id = 1 and course_start_date >= ? order by id", LocalDate.now()));
        m.put("latestWorkshop", db.select("select id, title, slug from courses where course_type_id = 2 and course_start_date >= ? order by id", LocalDate.now()));
        return ok(m);
    }

    /** Saves a certificate image (base64 data URL) generated in the browser. */
    @PostMapping("/api/save_cert_img")
    public ResponseEntity<String> saveCertImg(HttpServletRequest request) {
        Input in = Input.of(request, json);
        String data = in.get("imgBase64");
        String name = in.get("imgName");
        if (data == null || name == null || !name.matches("[A-Za-z0-9._-]+")) {
            return ok(Row.of("status", "error"));
        }
        byte[] image = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
        storage.put("certificates/" + name, image);
        return ok(Row.of("status", "success", "imgPath", storage.resolve("certificates/" + name).toString()));
    }

    // --------------------------------------------------------- newsletter

    @PostMapping("/api/newsletter")
    public ResponseEntity<String> newsletter(HttpServletRequest request) {
        Input in = Input.of(request, json);
        Map<String, String> errors = validator.check(in, "email", "required|unique:newsletters,email");
        if (!errors.isEmpty()) {
            Map<String, Object> e = new LinkedHashMap<>();
            errors.forEach((k, v) -> e.put(k, List.of(v)));
            return ok(Row.of("error", e));
        }
        db.insert("newsletters", Row.of("email", in.get("email")));
        return ok(Row.of("message", "success"));
    }

    // ----------------------------------------------------- modules/lessons

    private static Row moduleResource(Row m) {
        return Row.of("id", m.get("id"), "module_title", m.get("module_title"), "module_duration", m.get("module_duration"),
                "created_at", m.get("created_at"), "updated_at", m.get("updated_at"));
    }

    private static Row lessonResource(Row l) {
        return Row.of("id", l.get("id"), "course_id", l.get("course_id"), "module_id", l.get("module_id"),
                "lesson_title", l.get("lesson_title"), "lesson_description", Str.limit(l.str("lesson_description"), 10),
                "status", "1".equals(l.str("status")) ? "Active" : "InActive",
                "created_at", l.get("created_at"), "updated_at", l.get("updated_at"));
    }

    private static Row projectResource(Row p) {
        return Row.of("id", p.get("id"), "project_title", p.get("project_title"), "project_description", p.get("project_description"),
                "created_at", p.get("created_at"), "updated_at", p.get("updated_at"));
    }

    private static Row taskResource(Row t) {
        return Row.of("id", t.get("id"), "task_title", t.get("task_title"), "status", t.get("status"),
                "created_at", t.get("created_at"), "updated_at", t.get("updated_at"));
    }

    private static List<Row> map(List<Row> rows, java.util.function.Function<Row, Row> f) {
        List<Row> out = new ArrayList<>();
        for (Row r : rows) {
            out.add(f.apply(r));
        }
        return out;
    }

    @GetMapping("/api/modules")
    public ResponseEntity<String> modules() {
        List<Object> body = new ArrayList<>();
        body.add(map(db.select("select * from course_modules order by created_at desc, id desc"), ApiController::moduleResource));
        body.add("Modules fetched.");
        return ok(body);
    }

    @GetMapping("/api/modules/{course_id}")
    public ResponseEntity<String> modulesByCourse(@PathVariable("course_id") Long courseId) {
        return ok(map(db.select("select * from course_modules where course_id = ? order by id", courseId), ApiController::moduleResource));
    }

    @PostMapping("/api/modules/create")
    public ResponseEntity<String> storeModule(HttpServletRequest request) {
        Input in = Input.of(request, json);
        Map<String, String> errors = validator.check(in, "course_id", "required", "module_title", "required|max:255");
        if (!errors.isEmpty()) {
            return errors(errors);
        }
        long id = db.insert("course_modules", Row.of("admin_id", in.lng("admin_id"), "course_id", in.lng("course_id"),
                "module_title", in.get("module_title"), "module_duration", in.get("module_duration"), "status", "0"));
        return ok(List.of("Module created successfully.", moduleResource(db.find("course_modules", id))));
    }

    @GetMapping("/api/modules/lessons/{course_id}")
    public ResponseEntity<String> lessonsByCourse(@PathVariable("course_id") Long courseId) {
        return ok(map(db.select("select * from course_module_lessons where course_id = ? order by id", courseId), ApiController::lessonResource));
    }

    @PostMapping("/api/modules/lesson/create")
    public ResponseEntity<String> storeLesson(HttpServletRequest request) {
        Input in = Input.of(request, json);
        Map<String, String> errors = validator.check(in, "course_id", "required", "module_id", "required", "lesson_title", "required|max:255");
        if (!errors.isEmpty()) {
            return errors(errors);
        }
        long id = db.insert("course_module_lessons", Row.of("admin_id", in.lng("admin_id"), "course_id", in.lng("course_id"),
                "module_id", in.lng("module_id"), "lesson_title", in.get("lesson_title"),
                "lesson_description", in.get("lesson_description"), "status", "1"));
        if (in.hasFile("lesson_pdf")) {
            String name = storage.storeUpload(in.file("lesson_pdf"), "uploads/courses/lesson/" + id);
            db.updateById("course_module_lessons", id, Row.of("lesson_pdf", name));
        }
        return ok(List.of("Lesson created successfully.", lessonResource(db.find("course_module_lessons", id))));
    }

    @GetMapping("/api/project/{course_id}")
    public ResponseEntity<String> projectsByCourse(@PathVariable("course_id") Long courseId) {
        return ok(map(db.select("select * from course_projects where course_id = ? order by id", courseId), ApiController::projectResource));
    }

    @PostMapping("/api/project/create")
    public ResponseEntity<String> storeProject(HttpServletRequest request) {
        Input in = Input.of(request, json);
        Map<String, String> errors = validator.check(in, "course_id", "required", "project_title", "required|max:255");
        if (!errors.isEmpty()) {
            return errors(errors);
        }
        long id = db.insert("course_projects", Row.of("admin_id", in.lng("admin_id"), "course_id", in.lng("course_id"),
                "project_title", in.get("project_title"), "project_description", in.get("project_description"), "status", "1"));
        return ok(List.of("Project created successfully.", projectResource(db.find("course_projects", id))));
    }

    // -------------------------------------------------------------- tasks

    @GetMapping("/api/modules/lessons/task/{lesson_id}")
    public ResponseEntity<String> tasksByLesson(@PathVariable("lesson_id") Long lessonId) {
        return ok(map(db.select("select * from course_module_lesson_tasks where lesson_id = ? order by id desc", lessonId), ApiController::taskResource));
    }

    @PostMapping("/api/modules/lesson/task/create")
    public ResponseEntity<String> createTask(HttpServletRequest request) {
        Input in = Input.of(request, json);
        Map<String, String> errors = validator.check(in, "task_course_id", "required", "task_title", "required|max:255");
        if (!errors.isEmpty()) {
            return errors(errors);
        }
        long id = db.insert("course_module_lesson_tasks", Row.of("admin_id", in.lng("admin_id"), "course_id", in.lng("task_course_id"),
                "module_id", in.lng("task_module_id"), "lesson_id", in.lng("task_lesson_id"), "task_title", in.get("task_title"),
                "task_description", in.get("task_description"), "status", "1"));
        return ok(Row.of("message", "Task created successfully.", "data", taskResource(db.find("course_module_lesson_tasks", id))));
    }

    /** Teacher reply to a student task submission. */
    @PostMapping("/api/modules/lessons/task/reply")
    public ResponseEntity<String> reply(HttpServletRequest request) {
        Input in = Input.of(request, json);
        db.insert("student_tasks", Row.of("task_id", in.lng("task_id"), "student_teacher_connect_id", in.lng("student_teacher_connect_id"),
                "from_type", "Teacher", "from_id", in.lng("from_id"), "to_type", "Student", "to_id", in.lng("to_id"),
                "reply", in.get("reply"), "review_status", "Completed", "review_date", LocalDateTime.now()));
        return ok(Row.of("message", "Reply saved."));
    }

    @PostMapping("/api/modules/lessons/task/change-status")
    public ResponseEntity<String> changeStatus(HttpServletRequest request) {
        Input in = Input.of(request, json);
        Long id = in.lng("student_task_id");
        String status = in.get("status");
        if (id == null || !List.of("Under Review", "Reviewed", "Feedback", "Completed").contains(status)) {
            return ok(Row.of("message", "Invalid status."));
        }
        db.updateById("student_tasks", id, Row.of("review_status", status, "review_date", LocalDate.now().atStartOfDay()));
        Row t = db.find("student_tasks", id);
        return ok(Row.of("message", "Status changed successfully.", "data", taskResource(t)));
    }
}
