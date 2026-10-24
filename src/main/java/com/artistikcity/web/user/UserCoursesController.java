package com.artistikcity.web.user;

import com.artistikcity.http.Auth;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.CourseService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Paginator;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Student area: dashboard, my courses / workshops, course home, syllabus, modules, lessons and free courses
 * (User\DashboardController, User\CoursesController, User\WorkshopController, User\FreeCourseController).
 * All pages use the "user" root layout.
 */
@Controller
public class UserCoursesController {

    private final Inertia inertia;
    private final Db db;
    private final Auth auth;
    private final com.artistikcity.lifecycle.LifecycleRepository lifecycle;
    private final com.artistikcity.http.Redirects redirect;

    public UserCoursesController(Inertia inertia, Db db, Auth auth, com.artistikcity.lifecycle.LifecycleRepository lifecycle,
                                 com.artistikcity.http.Redirects redirect) {
        this.inertia = inertia;
        this.db = db;
        this.auth = auth;
        this.lifecycle = lifecycle;
        this.redirect = redirect;
    }

    /** Paid orders of the current student joined with their course (optionally one course type). */
    private List<Row> enrolled(HttpServletRequest request, Integer courseType) {
        return db.select("select c.title, c.sub_title, o.id as order_id, c.id as course_id, c.course_type_id, c.schedule_pdf, c.age_group, c.duration, "
                        + CourseService.photoSubquery("o.course_id") + ","
                        + " (select name from mediums where id = c.medium_id limit 1) as medium,"
                        + " (select name from skills where id = c.skill_id limit 1) as skill"
                        + " from orders o join courses c on c.id = o.course_id"
                        + " where o.user_id = ? and o.payment_status = 1" + (courseType == null ? "" : " and c.course_type_id = " + courseType)
                        + " order by o.id desc",
                auth.userId(request));
    }

    @GetMapping("/user/dashboard")
    public ResponseEntity<String> dashboard(HttpServletRequest request) {
        return inertia.renderUser(request, "Dashboard", Map.of("enrolledCourses", enrolled(request, null)));
    }

    /** My studio > My courses: every paid course and workshop with progress, in one list. */
    @GetMapping("/user/my-courses")
    public ResponseEntity<String> courses(HttpServletRequest request) {
        long userId = auth.userId(request);
        lifecycle.syncEnrollments(userId);
        Map<Object, Map<String, Object>> progress = new LinkedHashMap<>();
        for (Map<String, Object> e : lifecycle.enrollments(userId)) {
            progress.put(String.valueOf(e.get("course_id")), e);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Row o : enrolled(request, null)) {
            String courseId = String.valueOf(o.get("course_id"));
            if (!seen.add(courseId)) {
                continue; // one card per course, even when it was bought twice
            }
            Map<String, Object> e = progress.getOrDefault(courseId, Map.of());
            Map<String, Object> m = new LinkedHashMap<>(o);
            m.put("type", Integer.valueOf(CourseService.TYPE_WORKSHOP).equals(((Number) o.get("course_type_id")).intValue()) ? "workshop" : "course");
            m.put("progress", e.getOrDefault("progress_percentage", 0));
            m.put("completed_lessons", e.getOrDefault("completed_lessons", 0));
            m.put("total_lessons", e.getOrDefault("total_lessons", 0));
            m.put("submissions", e.getOrDefault("submissions", 0));
            m.put("start_date", e.get("course_start_date"));
            list.add(m);
        }
        return inertia.render(request, "Studio/Courses", Map.of("courses", list));
    }

    /** Workshops are listed together with courses now. */
    @GetMapping("/user/my-workshop")
    public ResponseEntity<String> workshops(HttpServletRequest request) {
        return redirect.to(request, "/user/my-courses?type=workshop");
    }

    /** CoursesController::module_lesson_navigation(). */
    public Map<String, Object> moduleLessonNavigation(Long moduleId) {
        Row module = NotFoundException.orFail(db.find("course_modules", moduleId));
        Row course = db.first("select id, title from courses where id = ?", module.get("course_id"));
        Map<String, Object> nav = new LinkedHashMap<>();
        nav.put("course_id", course == null ? null : course.get("id"));
        nav.put("course_title", course == null ? null : course.get("title"));
        nav.put("course_module_id", module.get("id"));
        nav.put("course_module_title", module.get("module_title"));
        List<Row> lessons = new ArrayList<>();
        for (Row l : db.select("select id, lesson_title from course_module_lessons where module_id = ? order by sort_order, id", moduleId)) {
            lessons.add(Row.of("lesson_id", l.get("id"), "module_id", module.get("id"), "lesson_title", l.get("lesson_title")));
        }
        nav.put("course_module_lesson", lessons);
        return nav;
    }

    /** CoursesController::course_navigation(). */
    public Map<String, Object> courseNavigation(Long courseId) {
        Row course = NotFoundException.orFail(db.first("select id, title, sub_title from courses where id = ?", courseId));
        Map<String, Object> nav = new LinkedHashMap<>();
        nav.put("course_id", course.get("id"));
        nav.put("course_title", course.get("title"));
        nav.put("course_sub_title", course.get("sub_title"));
        List<Row> modules = new ArrayList<>();
        for (Row m : db.select("select id, module_title from course_modules where course_id = ? order by sort_order, id", courseId)) {
            modules.add(Row.of("module_id", m.get("id"), "module_title", m.get("module_title")));
        }
        nav.put("course_module", modules);
        return nav;
    }

    // ------------------------------------------------------------ free courses

    @GetMapping("/user/free-courses")
    public ResponseEntity<String> freeCourses(HttpServletRequest request, @RequestParam(value = "page", required = false) String pageParam) {
        int perPage = 5;
        int page = Paginator.page(pageParam);
        long total = db.count("select count(*) from free_courses where status = 1");
        List<Row> rows = db.select("select id, title, photo, description from free_courses where status = 1 order by id desc limit " + perPage
                + " offset " + ((page - 1) * perPage));
        String path = Inertia.baseUrl(request) + request.getRequestURI();
        return inertia.renderUser(request, "Students/FreeCourses", Map.of("FreeCourses", Paginator.paginate(rows, total, perPage, page, path)));
    }

    @GetMapping("/user/free-course/{id}")
    public ResponseEntity<String> freeCourseHome(HttpServletRequest request, @PathVariable("id") Long id) {
        Row course = NotFoundException.orFail(db.find("free_courses", id));
        List<Row> videos = db.select("select * from free_course_videos where free_course_id = ? order by id", id);
        course.put("free_course_videos", videos);
        Map<String, Object> menu = new LinkedHashMap<>();
        menu.put("title", course.get("title"));
        menu.put("syllabus", "");
        menu.put("type", "videos");
        List<Row> v = new ArrayList<>();
        for (Row r : videos) {
            v.add(Row.of("video_title", r.get("video_title"), "video_url", r.get("video_url")));
        }
        menu.put("videos", v);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("FreeCoursesData", course);
        props.put("FreeCoursesMenu", menu);
        return inertia.renderUser(request, "Students/FreeCourseHome", props);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
