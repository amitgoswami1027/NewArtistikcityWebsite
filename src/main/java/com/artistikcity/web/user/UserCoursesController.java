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

    public UserCoursesController(Inertia inertia, Db db, Auth auth) {
        this.inertia = inertia;
        this.db = db;
        this.auth = auth;
    }

    /** Paid orders of the current student joined with their course (optionally one course type). */
    private List<Row> enrolled(HttpServletRequest request, Integer courseType) {
        return db.select("select c.title, c.sub_title, o.id as order_id, c.id as course_id, c.course_type_id, c.schedule_pdf, c.age_group, c.duration, "
                        + CourseService.photoSubquery("o.course_id") + ","
                        + " (select top 1 name from mediums where id = c.medium_id) as medium,"
                        + " (select top 1 name from skills where id = c.skill_id) as skill"
                        + " from orders o join courses c on c.id = o.course_id"
                        + " where o.user_id = ? and o.payment_status = 1" + (courseType == null ? "" : " and c.course_type_id = " + courseType)
                        + " order by o.id desc",
                auth.userId(request));
    }

    @GetMapping("/user/dashboard")
    public ResponseEntity<String> dashboard(HttpServletRequest request) {
        return inertia.renderUser(request, "Dashboard", Map.of("enrolledCourses", enrolled(request, null)));
    }

    @GetMapping("/user/my-courses")
    public ResponseEntity<String> courses(HttpServletRequest request) {
        return inertia.renderUser(request, "Students/MyCourses", Map.of("myCourses", enrolled(request, CourseService.TYPE_COURSE)));
    }

    @GetMapping("/user/my-workshop")
    public ResponseEntity<String> workshops(HttpServletRequest request) {
        return inertia.renderUser(request, "Students/MyWorkshops", Map.of("myWorkshops", enrolled(request, CourseService.TYPE_WORKSHOP)));
    }

    /** Course home for a paid order. */
    @GetMapping("/user/course/{order_id}")
    public ResponseEntity<String> courseHome(HttpServletRequest request, @PathVariable("order_id") Long orderId) {
        Row data = NotFoundException.orFail(db.first(
                "select c.title, c.sub_title, c.duration, c.mini_projects, c.course_modules, o.id as order_id, c.id as course_id,"
                        + " c.course_type_id, o.issue_certificate, " + CourseService.photoSubquery("o.course_id")
                        + " from orders o join courses c on c.id = o.course_id"
                        + " where o.user_id = ? and o.id = ? and o.payment_status = 1", auth.userId(request), orderId));
        String host = Inertia.baseUrl(request);
        String encodedOrder = Base64.getEncoder().encodeToString(String.valueOf(orderId).getBytes(StandardCharsets.UTF_8));
        String text = "I am feeling proud to share my certification of completion.";
        data.put("certificate_facebook_share_url", enc(host + "/user/certificate/social_share?order_id=" + encodedOrder));
        data.put("certificate_twitter_share_url", enc(host + "/user/certificate/social_share?type=certificate&order_id=" + encodedOrder));
        data.put("certificate_linkedin_share_url", enc(host + "/user/certificate/social_share?order_id=" + encodedOrder));
        data.put("certificate_text", text);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("coursesData", data);
        details.put("courseModules", modulesWithLessons(data.lng("course_id")));
        details.put("courseProject", db.select("select * from course_projects where course_id = ? order by id", data.get("course_id")));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("CourseDetails", details);
        props.put("CourseNav", courseNavigation(data.lng("course_id")));
        return inertia.renderUser(request, "Students/CourseHome", props);
    }

    private List<Row> modulesWithLessons(Long courseId) {
        List<Row> modules = db.select("select * from course_modules where course_id = ? order by id", courseId);
        Map<Long, List<Row>> lessons = CourseService.groupBy(
                db.select("select * from course_module_lessons where course_id = ? order by id", courseId), "module_id");
        for (Row m : modules) {
            m.put("lessons", lessons.getOrDefault(m.lng("id"), new ArrayList<>()));
        }
        return modules;
    }

    @GetMapping("/user/course/modules/{id}")
    public ResponseEntity<String> courseModule(HttpServletRequest request, @PathVariable("id") Long id) {
        Row module = NotFoundException.orFail(db.find("course_modules", id));
        module.put("lessons", db.select("select * from course_module_lessons where module_id = ? order by id", id));
        module.put("course", db.find("courses", module.get("course_id")));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("CourseModule", module);
        props.put("CourseNav", courseNavigation(module.lng("course_id")));
        return inertia.renderUser(request, "Students/CourseModule", props);
    }

    @GetMapping("/user/course/syllabus/{id}")
    public ResponseEntity<String> syllabus(HttpServletRequest request, @PathVariable("id") Long courseId) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("CourseModule", modulesWithLessons(courseId));
        props.put("CourseNav", courseNavigation(courseId));
        return inertia.renderUser(request, "Students/CourseSyllabus", props);
    }

    /** One lesson per page (simplePaginate(1)) with its module, course and tasks. */
    @GetMapping("/user/course/module/lesson/{module_id}")
    public ResponseEntity<String> lesson(HttpServletRequest request, @PathVariable("module_id") Long moduleId,
                                         @RequestParam(value = "page", required = false) String pageParam) {
        int page = Paginator.page(pageParam);
        List<Row> rows = db.select("select * from course_module_lessons where module_id = ? order by id offset "
                + (page - 1) + " rows fetch next 2 rows only", moduleId);
        boolean more = rows.size() > 1;
        List<Row> items = rows.isEmpty() ? rows : new ArrayList<>(rows.subList(0, 1));
        for (Row l : items) {
            l.put("module", db.first("select id, module_title from course_modules where id = ?", l.get("module_id")));
            l.put("course", db.first("select id, title from courses where id = ?", l.get("course_id")));
            l.put("tasks", db.select("select * from course_module_lesson_tasks where lesson_id = ? order by id", l.get("id")));
        }
        String path = Inertia.baseUrl(request) + request.getRequestURI();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("lessonDetails", Paginator.simple(items, more, 1, page, path));
        props.put("lessonNav", moduleLessonNavigation(moduleId));
        return inertia.renderUser(request, "Students/CourseLesson", props);
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
        for (Row l : db.select("select id, lesson_title from course_module_lessons where module_id = ? order by id", moduleId)) {
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
        for (Row m : db.select("select id, module_title from course_modules where course_id = ? order by id", courseId)) {
            modules.add(Row.of("module_id", m.get("id"), "module_title", m.get("module_title")));
        }
        nav.put("course_module", modules);
        return nav;
    }

    @GetMapping("/user/workshop/{id}")
    public ResponseEntity<String> workshopHome(HttpServletRequest request, @PathVariable("id") Long courseId) {
        // The original rendered Students/CourseHome with a different prop shape; send the student to the
        // regular course home for their paid order of this workshop instead.
        Row order = db.first("select top 1 id from orders where user_id = ? and course_id = ? and payment_status = 1 order by id desc",
                auth.userId(request), courseId);
        if (order == null) {
            throw new NotFoundException();
        }
        return courseHome(request, order.lng("id"));
    }

    // ------------------------------------------------------------ free courses

    @GetMapping("/user/free-courses")
    public ResponseEntity<String> freeCourses(HttpServletRequest request, @RequestParam(value = "page", required = false) String pageParam) {
        int perPage = 5;
        int page = Paginator.page(pageParam);
        long total = db.count("select count(*) from free_courses where status = 1");
        List<Row> rows = db.select("select id, title, photo, description from free_courses where status = 1 order by id desc offset "
                + ((page - 1) * perPage) + " rows fetch next " + perPage + " rows only");
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
