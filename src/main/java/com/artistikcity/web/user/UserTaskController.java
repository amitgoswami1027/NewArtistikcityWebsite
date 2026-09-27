package com.artistikcity.web.user;

import com.artistikcity.http.Auth;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.http.Redirects;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.inertia.Routes;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import com.artistikcity.support.Str;
import com.artistikcity.support.UploadedFile;
import com.artistikcity.support.Validator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lesson tasks: the student <-> teacher submission thread (User\TaskController). */
@Controller
public class UserTaskController {

    private final Inertia inertia;
    private final Db db;
    private final Json json;
    private final Auth auth;
    private final Validator validator;
    private final Storage storage;
    private final Routes routes;
    private final Redirects redirect;
    private final UserCoursesController courses;

    public UserTaskController(Inertia inertia, Db db, Json json, Auth auth, Validator validator, Storage storage,
                              Routes routes, Redirects redirect, UserCoursesController courses) {
        this.inertia = inertia;
        this.db = db;
        this.json = json;
        this.auth = auth;
        this.validator = validator;
        this.storage = storage;
        this.routes = routes;
        this.redirect = redirect;
        this.courses = courses;
    }

    @GetMapping("/user/course/module/lesson/task/{task_id}")
    public ResponseEntity<String> taskDetails(HttpServletRequest request, @PathVariable("task_id") Long taskId) {
        Row task = NotFoundException.orFail(db.find("course_module_lesson_tasks", taskId));
        Row teacher = db.find("admins", task.get("admin_id"));
        if (teacher == null) {
            teacher = db.first("select a.* from admins a join courses c on c.admin_id = a.id where c.id = ?", task.get("course_id"));
        }
        teacher = teacher == null ? new Row() : teacher.without("password", "remember_token");
        task.put("teacher", teacher);
        task.put("taskTeacherAvatar", Str.avatar(teacher.str("name")));
        task.put("taskTeacherPhoto", teacher.get("profile_photo"));
        task.put("taskTeacherProfileUrl", teacher.get("slug") == null ? "#" : routes.path("teacher.profile", teacher.get("slug")));
        task.put("admin_id", teacher.get("id"));

        Row lesson = db.find("course_module_lessons", task.get("lesson_id"));
        if (lesson != null) {
            lesson.put("module", db.first("select id, module_title from course_modules where id = ?", lesson.get("module_id")));
            lesson.put("course", db.first("select id, title from courses where id = ?", lesson.get("course_id")));
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("taskDetails", task);
        props.put("lessonDetails", lesson == null ? new Row() : lesson);
        props.put("lessonNav", courses.moduleLessonNavigation(task.lng("module_id")));
        props.put("messages", messages(taskId, auth.userId(request)));
        return inertia.renderUser(request, "Students/LessonTask", props);
    }

    private Map<String, Object> messages(long taskId, long studentId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("student_data", person("Student", studentId));
        List<Map<String, Object>> list = new ArrayList<>();
        Row connect = db.first("select * from student_teacher_connects where task_id = ? and student_id = ?", taskId, studentId);
        if (connect != null) {
            for (Row t : db.select("select * from student_tasks where student_teacher_connect_id = ? and deleted_at is null order by id", connect.get("id"))) {
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("from_type", t.get("from_type"));
                msg.put("from_id", t.get("from_id"));
                msg.put("from_data", person(t.str("from_type"), t.lng("from_id")));
                msg.put("to_type", t.get("to_type"));
                msg.put("to_id", t.get("to_id"));
                msg.put("to_data", person(t.str("to_type"), t.lng("to_id")));
                msg.put("reply", t.get("reply"));
                msg.put("review_status", t.get("review_status"));
                msg.put("reply_photos", db.select("select * from student_task_photos where student_task_id = ? order by id", t.get("id")));
                list.add(msg);
            }
        }
        m.put("message", list);
        return m;
    }

    /** get_messanger_data(): name, avatar initials, photo URL (or false) and profile link. */
    private Map<String, Object> person(String type, long id) {
        boolean teacher = "Teacher".equals(type);
        Row p = db.first("select id, name, email, profile_photo, slug from " + (teacher ? "admins" : "users") + " where id = ?", id);
        Map<String, Object> d = new LinkedHashMap<>();
        if (p == null) {
            return d;
        }
        d.put("id", p.get("id"));
        d.put("name", p.get("name"));
        d.put("email", p.get("email"));
        String photo = p.str("profile_photo");
        d.put("profile_photo", photo == null || photo.isEmpty() ? false
                : photo.startsWith("http") ? photo : "/storage/uploads/" + (teacher ? "teachers/" : "students/") + p.get("id") + "/" + photo);
        d.put("avatar", Str.avatar(p.str("name")));
        d.put("profile_url", p.get("slug") == null ? "#" : routes.path(teacher ? "teacher.profile" : "student.profile", p.get("slug")));
        return d;
    }

    @PostMapping("/user/course/module/lesson/task/save")
    public ResponseEntity<String> saveTask(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "reply", "required");
        Long me = auth.userId(request);
        Long taskId = in.lng("task_id");
        if (!me.equals(in.lng("from_id")) || taskId == null) {
            return redirect.back(request);
        }
        Row teacher = db.first("select c.admin_id as teacher_id from course_module_lesson_tasks t join courses c on c.id = t.course_id where t.id = ?", taskId);
        if (teacher != null) {
            Row connect = db.first("select * from student_teacher_connects where task_id = ? and student_id = ?", taskId, me);
            long connectId = connect != null ? connect.lng("id")
                    : db.insert("student_teacher_connects", Row.of("task_id", taskId, "student_id", me, "teacher_id", teacher.get("teacher_id")));
            long submission = db.insert("student_tasks", Row.of("task_id", taskId, "student_teacher_connect_id", connectId,
                    "from_type", "Student", "from_id", me, "to_type", "Teacher", "to_id", teacher.get("teacher_id"),
                    "reply", in.get("reply"), "review_status", "Under Review"));
            for (UploadedFile f : in.files("images")) {
                String name = storage.storeUpload(f, "uploads/courses/task/" + submission);
                db.insert("student_task_photos", Row.of("task_id", taskId, "student_task_id", submission, "photo_name", name));
            }
        }
        return redirect.route(request, "user.course.module.lesson.task", taskId);
    }
}
