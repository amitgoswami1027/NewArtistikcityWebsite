package com.artistikcity.web;

import com.artistikcity.http.NotFoundException;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.CourseService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Public student and teacher profiles (StudentController.php, TeacherController.php). */
@Controller
public class ProfilesController {

    private final Inertia inertia;
    private final Db db;

    public ProfilesController(Inertia inertia, Db db) {
        this.inertia = inertia;
        this.db = db;
    }

    @GetMapping("/student/{slug}")
    public ResponseEntity<String> student(HttpServletRequest request, @PathVariable("slug") String slug) {
        Row student = NotFoundException.orFail(db.first("select * from users where slug = ?", slug)).without("password", "remember_token");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("student", student);
        props.put("enrolledCourses", db.select(
                "select c.title, c.sub_title, o.id, o.order_number, o.created_at, c.id as course_id, c.course_type_id, c.schedule_pdf, c.age_group,"
                        + " (select top 1 name from mediums where id = c.medium_id) as medium,"
                        + " (select top 1 name from admins where id = c.admin_id) as teacher,"
                        + " (select top 1 name from skills where id = c.skill_id) as skill, "
                        + CourseService.photoSubquery("o.course_id")
                        + " from orders o join courses c on c.id = o.course_id"
                        + " where o.user_id = ? and o.payment_status = 1 and o.payment_id <> '' order by o.id", student.get("id")));
        props.put("portfolio", db.select("select * from student_artworks where user_id = ? and status = 1 order by id", student.get("id")));
        return inertia.render(request, "Student", props);
    }

    @GetMapping("/student-feedback")
    public ResponseEntity<String> studentFeedback(HttpServletRequest request) {
        return inertia.render(request, "StudentFeedback", Map.of("studentFeedback", ""));
    }

    @GetMapping("/teacher/{slug}")
    public ResponseEntity<String> teacher(HttpServletRequest request, @PathVariable("slug") String slug) {
        Row teacher = NotFoundException.orFail(db.first("select * from admins where slug = ?", slug)).without("password", "remember_token");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("teacher", teacher);
        props.put("teachingCources", db.select("select c.*,"
                + " (select top 1 name from mediums where id = c.medium_id) as medium,"
                + " (select top 1 name from admins where id = c.admin_id) as teacher,"
                + " (select top 1 name from skills where id = c.skill_id) as skill, "
                + CourseService.photoSubquery("c.id")
                + " from courses c where c.status = 1 and c.admin_id = ? order by c.id", teacher.get("id")));
        return inertia.render(request, "Teacher", props);
    }

    @GetMapping("/instructor")
    public ResponseEntity<String> instructor(HttpServletRequest request) {
        Row i = db.first("select * from admins where slug = ?", WelcomeController.INSTRUCTOR_SLUG);
        return inertia.render(request, "Instructor", Map.of("instructor", i == null ? new Row() : i.without("password", "remember_token")));
    }
}
