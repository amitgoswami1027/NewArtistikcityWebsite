package com.artistikcity.web.user;

import com.artistikcity.http.Auth;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.http.Redirects;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.lifecycle.LifecycleRepository;
import com.artistikcity.service.CourseService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The student's course, in two focused pages:
 * <ul>
 *   <li>{@code /user/course/{orderId}} - overview: header, progress, syllabus, "Start / Resume" (Pages/Course/Overview)</li>
 *   <li>{@code /user/course/modules/{moduleId}?lesson={lessonId}} - learning view: lesson content with a collapsible
 *       module/lesson sidebar and previous / next (Pages/Course/Learn)</li>
 * </ul>
 * Every page checks that the student has a paid order for the course. Older addresses (syllabus, paginated lesson pages,
 * workshop home, studio classroom) redirect here, so every link ends up in one place.
 */
@Controller
public class CourseLearningController {

    private static final Pattern SCRIPT = Pattern.compile("(?is)<\\s*(script|style)[^>]*>.*?<\\s*/\\s*\\1\\s*>");
    private static final Pattern ON_ATTR = Pattern.compile("(?i)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JS_URL = Pattern.compile("(?i)(href|src)\\s*=\\s*([\"']?)\\s*javascript:[^\"'>\\s]*\\2");

    private final Inertia inertia;
    private final Db db;
    private final Auth auth;
    private final LifecycleRepository lifecycle;
    private final Redirects redirect;

    public CourseLearningController(Inertia inertia, Db db, Auth auth, LifecycleRepository lifecycle, Redirects redirect) {
        this.inertia = inertia;
        this.db = db;
        this.auth = auth;
        this.lifecycle = lifecycle;
        this.redirect = redirect;
    }

    // ------------------------------------------------------------------ access

    /** The student's paid order for a course, or null. */
    private Row paidOrder(long userId, long courseId) {
        return db.first("select id, issue_certificate from orders where user_id = ? and course_id = ? and payment_status = 1 order by id desc limit 1",
                userId, courseId);
    }

    /** Lesson completion state (enrollment rows are created from paid orders). */
    private Set<String> completed(long userId, long courseId) {
        lifecycle.syncEnrollments(userId);
        Map<String, Object> e = lifecycle.enrollment(userId, courseId);
        Set<String> done = new HashSet<>();
        if (e != null) {
            for (Object id : lifecycle.completedLessonIds(((Number) e.get("id")).longValue())) {
                done.add(String.valueOf(id));
            }
        }
        return done;
    }

    /** Modules with their (published) lessons in order, each lesson flagged done / with its task count. */
    private List<Map<String, Object>> syllabus(long courseId, Set<String> done) {
        List<Row> modules = db.select("select id, module_title, module_duration from course_modules where course_id = ? and status = '1' order by sort_order, id", courseId);
        List<Row> lessons = db.select("select l.id, l.module_id, l.lesson_title, (select count(*) from course_module_lesson_tasks t where t.lesson_id = l.id"
                + " and t.status = '1') as tasks from course_module_lessons l where l.course_id = ? and l.status = '1' order by l.sort_order, l.id", courseId);
        List<Map<String, Object>> out = new ArrayList<>();
        int number = 0;
        for (Row m : modules) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", m.get("id"));
            mm.put("title", m.get("module_title"));
            mm.put("duration", m.get("module_duration"));
            List<Map<String, Object>> own = new ArrayList<>();
            for (Row l : lessons) {
                if (String.valueOf(l.get("module_id")).equals(String.valueOf(m.get("id")))) {
                    number++;
                    Map<String, Object> ll = new LinkedHashMap<>();
                    ll.put("id", l.get("id"));
                    ll.put("module_id", m.get("id"));
                    ll.put("title", l.get("lesson_title"));
                    ll.put("number", number);
                    ll.put("tasks", l.get("tasks"));
                    ll.put("done", done.contains(String.valueOf(l.get("id"))));
                    own.add(ll);
                }
            }
            mm.put("lessons", own);
            mm.put("done", !own.isEmpty() && own.stream().allMatch(x -> Boolean.TRUE.equals(x.get("done"))));
            out.add(mm);
        }
        return out;
    }

    private static List<Map<String, Object>> flat(List<Map<String, Object>> syllabus) {
        List<Map<String, Object>> all = new ArrayList<>();
        for (Map<String, Object> m : syllabus) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ls = (List<Map<String, Object>>) m.get("lessons");
            all.addAll(ls);
        }
        return all;
    }

    /** First lesson not yet completed (or the first lesson when all are done / none started). */
    private static Map<String, Object> resumeLesson(List<Map<String, Object>> lessons) {
        for (Map<String, Object> l : lessons) {
            if (!Boolean.TRUE.equals(l.get("done"))) {
                return l;
            }
        }
        return lessons.isEmpty() ? null : lessons.get(0);
    }

    private Map<String, Object> courseInfo(long courseId, Object orderId) {
        Row c = NotFoundException.orFail(db.first("select c.*, (select name from mediums m where m.id = c.medium_id) as medium_name,"
                + " (select a.name from admins a where a.id = c.admin_id) as teacher_name, " + CourseService.photoSubquery("c.id")
                + " from courses c where c.id = ?", courseId));
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", c.get("id"));
        v.put("orderId", orderId);
        v.put("title", c.get("title"));
        v.put("sub_title", c.get("sub_title"));
        v.put("slug", c.get("slug"));
        v.put("kind", c.lng("course_type_id", 1) == CourseService.TYPE_WORKSHOP ? "Workshop" : "Course");
        v.put("image", c.get("photo") == null ? null : "/storage/uploads/courses/" + c.get("id") + "/" + c.get("photo"));
        v.put("medium", c.get("medium_name"));
        v.put("teacher", c.get("teacher_name"));
        v.put("duration", c.get("duration"));
        v.put("sessions", c.get("sessions"));
        v.put("hours_per_week", c.get("time_required"));
        v.put("start_date", CourseService.formatStartDate(c.str("course_start_date")));
        v.put("schedule_pdf", c.get("schedule_pdf") == null ? null : "/storage/uploads/courses/schedule/" + c.get("id") + "/" + c.get("schedule_pdf"));
        return v;
    }

    private static Map<String, Object> progress(List<Map<String, Object>> lessons) {
        long done = lessons.stream().filter(l -> Boolean.TRUE.equals(l.get("done"))).count();
        int pct = lessons.isEmpty() ? 0 : (int) Math.round(done * 100.0 / lessons.size());
        return Map.of("done", done, "total", lessons.size(), "percent", pct);
    }

    static String safeHtml(String html) {
        if (html == null) {
            return null;
        }
        String s = SCRIPT.matcher(html).replaceAll("");
        s = ON_ATTR.matcher(s).replaceAll("");
        return JS_URL.matcher(s).replaceAll("$1=\"#\"");
    }

    // ------------------------------------------------------------------ overview

    @GetMapping("/user/course/{order_id}")
    public ResponseEntity<String> overview(HttpServletRequest request, @PathVariable("order_id") long orderId) {
        long userId = auth.userId(request);
        Row order = NotFoundException.orFail(db.first("select id, course_id, issue_certificate from orders where id = ? and user_id = ? and payment_status = 1",
                orderId, userId));
        long courseId = order.lng("course_id");
        List<Map<String, Object>> syllabus = syllabus(courseId, completed(userId, courseId));
        List<Map<String, Object>> lessons = flat(syllabus);
        Map<String, Object> resume = resumeLesson(lessons);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("course", courseInfo(courseId, orderId));
        props.put("syllabus", syllabus);
        props.put("progress", progress(lessons));
        props.put("resume", resume == null ? null : Map.of("lessonId", resume.get("id"), "moduleId", resume.get("module_id"), "title", resume.get("title"),
                "number", resume.get("number"), "started", lessons.stream().anyMatch(l -> Boolean.TRUE.equals(l.get("done")))));
        props.put("projects", db.select("select id, project_title, project_description from course_projects where course_id = ? and status = '1' order by id", courseId));
        props.put("certificate", Map.of("issued", order.lng("issue_certificate", 0) == 1,
                "download", "/user/certificate/download?id=" + orderId));
        return inertia.render(request, "Course/Overview", props);
    }

    /** "Resume" from anywhere: the next lesson to do (or the overview when the course has no lessons yet). */
    @GetMapping("/user/course/{order_id}/resume")
    public ResponseEntity<String> resume(HttpServletRequest request, @PathVariable("order_id") long orderId) {
        long userId = auth.userId(request);
        Row order = NotFoundException.orFail(db.first("select id, course_id from orders where id = ? and user_id = ? and payment_status = 1", orderId, userId));
        Map<String, Object> next = resumeLesson(flat(syllabus(order.lng("course_id"), completed(userId, order.lng("course_id")))));
        return redirect.to(request, next == null ? "/user/course/" + orderId
                : "/user/course/modules/" + next.get("module_id") + "?lesson=" + next.get("id"));
    }

    // ------------------------------------------------------------------ learning view

    @GetMapping("/user/course/modules/{id}")
    public ResponseEntity<String> learn(HttpServletRequest request, @PathVariable("id") long moduleId,
                                        @RequestParam(value = "lesson", required = false) Long lessonId) {
        long userId = auth.userId(request);
        Row module = NotFoundException.orFail(db.first("select id, course_id, module_title from course_modules where id = ?", moduleId));
        long courseId = module.lng("course_id");
        Row order = paidOrder(userId, courseId);
        if (order == null) {
            Row c = db.first("select slug from courses where id = ?", courseId);
            return redirect.to(request, c == null ? "/user/my-courses" : "/course/" + c.get("slug"));
        }
        List<Map<String, Object>> syllabus = syllabus(courseId, completed(userId, courseId));
        List<Map<String, Object>> lessons = flat(syllabus);
        int index = -1;
        for (int i = 0; i < lessons.size(); i++) {
            Map<String, Object> l = lessons.get(i);
            boolean inModule = String.valueOf(l.get("module_id")).equals(String.valueOf(moduleId));
            if (inModule && (lessonId == null || String.valueOf(l.get("id")).equals(String.valueOf(lessonId)))) {
                index = i;
                break;
            }
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("course", courseInfo(courseId, order.get("id")));
        props.put("syllabus", syllabus);
        props.put("progress", progress(lessons));
        props.put("module", Map.of("id", module.get("id"), "title", module.get("module_title")));
        if (index >= 0) {
            Map<String, Object> cur = lessons.get(index);
            Row l = db.first("select id, lesson_title, lesson_description, lesson_pdf, video_url from course_module_lessons where id = ?", cur.get("id"));
            Map<String, Object> lesson = new LinkedHashMap<>(cur);
            lesson.put("description", safeHtml(l.str("lesson_description")));
            lesson.put("pdf", l.get("lesson_pdf") == null || l.str("lesson_pdf").isBlank() ? null
                    : "/storage/uploads/courses/lesson/" + l.get("id") + "/" + l.get("lesson_pdf"));
            List<Map<String, Object>> tasks = new ArrayList<>();
            for (Row t : db.select("select id, task_title, task_description from course_module_lesson_tasks where lesson_id = ? and status = '1' order by id", l.get("id"))) {
                tasks.add(Map.of("id", t.get("id"), "title", t.str("task_title") == null ? "Task" : t.str("task_title"),
                        "description", String.valueOf(safeHtml(t.str("task_description")))));
            }
            lesson.put("tasks", tasks);
            // the lesson's own video (set per lesson in Admin > Courses > Curriculum)
            String video = l.str("video_url", "").trim();
            lesson.put("video", video.isEmpty() ? null : Map.of("title", l.str("lesson_title", ""), "url", video));
            lesson.put("total", lessons.size());
            props.put("lesson", lesson);
            props.put("prev", index > 0 ? link(lessons.get(index - 1)) : null);
            props.put("next", index < lessons.size() - 1 ? link(lessons.get(index + 1)) : null);
        } else {
            props.put("lesson", null);
        }
        return inertia.render(request, "Course/Learn", props);
    }

    private static Map<String, Object> link(Map<String, Object> l) {
        return Map.of("lessonId", l.get("id"), "moduleId", l.get("module_id"), "title", l.get("title"), "number", l.get("number"));
    }

    // ------------------------------------------------------------------ older addresses

    /** Paginated lesson page (one lesson per ?page=n) -> the same lesson in the learning view. */
    @GetMapping("/user/course/module/lesson/{module_id}")
    public ResponseEntity<String> oldLesson(HttpServletRequest request, @PathVariable("module_id") long moduleId,
                                            @RequestParam(value = "page", required = false) Integer page) {
        List<Row> ls = db.select("select id from course_module_lessons where module_id = ? and status = '1' order by sort_order, id", moduleId);
        int i = page == null || page < 1 ? 0 : Math.min(page - 1, Math.max(ls.size() - 1, 0));
        return redirect.to(request, "/user/course/modules/" + moduleId + (ls.isEmpty() ? "" : "?lesson=" + ls.get(i).get("id")));
    }

    @GetMapping("/user/course/syllabus/{id}")
    public ResponseEntity<String> oldSyllabus(HttpServletRequest request, @PathVariable("id") long courseId) {
        Row order = paidOrder(auth.userId(request), courseId);
        return redirect.to(request, order == null ? "/user/my-courses" : "/user/course/" + order.get("id") + "#syllabus");
    }

    @GetMapping("/user/workshop/{id}")
    public ResponseEntity<String> workshop(HttpServletRequest request, @PathVariable("id") long courseId) {
        Row order = NotFoundException.orFail(paidOrder(auth.userId(request), courseId));
        return redirect.to(request, "/user/course/" + order.get("id"));
    }
}
