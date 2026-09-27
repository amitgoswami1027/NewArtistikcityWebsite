package com.artistikcity.web;

import com.artistikcity.http.NotFoundException;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.CourseService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import com.artistikcity.support.Str;
import com.artistikcity.support.Values;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Course catalogue and course details (CoursesController.php). */
@Controller
public class CoursesController {

    private final Inertia inertia;
    private final Db db;
    private final CourseService courses;

    public CoursesController(Inertia inertia, Db db, CourseService courses) {
        this.inertia = inertia;
        this.db = db;
        this.courses = courses;
    }

    @GetMapping("/courses")
    public ResponseEntity<String> courses(HttpServletRequest request,
                                          @RequestParam(value = "type", required = false) String type,
                                          @RequestParam(value = "medium", required = false) String medium,
                                          @RequestParam(value = "genre", required = false) String genre,
                                          @RequestParam(value = "skill", required = false) String skill,
                                          @RequestParam(value = "category", required = false) String category,
                                          @RequestParam(value = "age", required = false) String age,
                                          @RequestParam(value = "q", required = false) String q) {
        // "all" (the catalog view) lists courses and workshops together; the medium / genre / skill / age / q
        // parameters are passed to the page as pre-selected filters so the visitor can refine or clear them
        // in the browser without a round trip.
        String t = type == null || type.isEmpty() ? "course" : type.toLowerCase();
        String typeSlug = "all".equals(t) ? null : t;
        var list = courses.withStats(courses.withRelations(courses.catalogue(typeSlug, null, null, null, Values.toLong(category)), true));
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("medium", medium == null ? "" : medium);
        filters.put("genre", genre == null ? "" : genre);
        filters.put("skill", skill == null ? "" : skill);
        filters.put("age", age == null ? "" : age);
        filters.put("q", q == null ? "" : q);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("courses", courses.collection(list));
        props.put("categories", db.select("select * from categories order by id"));
        props.put("course_types", db.select("select * from course_types order by id"));
        props.put("genres", db.select("select * from genres order by id"));
        props.put("mediums", db.select("select * from mediums order by id"));
        props.put("skills", db.select("select * from skills order by id"));
        props.put("type", Str.ucfirst(t));
        props.put("filters", filters);
        return inertia.render(request, "Courses", props);
    }

    @GetMapping("/course/{slug}")
    public ResponseEntity<String> courseDetails(HttpServletRequest request, @PathVariable("slug") String slug) {
        Row course = NotFoundException.orFail(db.first("select * from courses where slug = ?", slug));
        courses.withRelations(course, false);
        courses.withStats(new ArrayList<>(List.of(course)));
        long id = course.lng("id");

        // curriculum: units (modules) with their lessons
        List<Row> modules = db.select("select id, module_title, module_duration from course_modules"
                + " where course_id = ? and status = '1' order by id", id);
        List<Row> lessons = db.select("select id, module_id, lesson_title, lesson_description, case when lesson_pdf is null or lesson_pdf = ''"
                + " then 0 else 1 end as has_download from course_module_lessons where course_id = ? and status = '1' order by id", id);
        for (Row m : modules) {
            List<Row> own = new ArrayList<>();
            for (Row l : lessons) {
                if (m.lng("id").equals(l.lng("module_id"))) {
                    own.add(l);
                }
            }
            m.put("lessons", own);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("students", course.get("students"));
        stats.put("reviews", course.get("reviews"));
        stats.put("positive", course.get("positive"));
        stats.put("lessons", lessons.size());
        stats.put("downloads", lessons.stream().filter(l -> l.lng("has_download", 0) == 1).count());
        stats.put("units", modules.size());

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("course", courses.resource(course));
        props.put("curriculum", modules);
        props.put("projects", db.select("select id, project_title, project_description from course_projects"
                + " where course_id = ? and status = '1' order by id", id));
        props.put("reviews", db.select("select top 12 r.id, r.rating, r.review, r.created_at, u.name, u.slug, u.profile_photo, u.id as user_id"
                + " from course_reviews r left join users u on u.id = r.added_by_id and r.added_by_type = 'Student'"
                + " where r.course_id = ? and r.status = 1 order by r.id desc", id));
        props.put("gallery", db.select("select top 8 a.id, a.photo_name, a.user_id, u.name, u.slug from student_artworks a"
                + " join users u on u.id = a.user_id where a.status = 1 order by a.id desc"));
        props.put("videos", db.select("select top 1 video_title, video_url from course_videos where course_id = ? and status = 1 order by id", id));
        props.put("related", courses.withStats(courses.withRelations(db.select("select top 4 * from courses where status = 1 and id <> ?"
                + " and course_start_date >= ? order by case when medium_id = ? then 0 else 1 end, id", id, java.time.LocalDate.now(),
                course.get("medium_id")), true)));
        props.put("stats", stats);
        return inertia.render(request, "CourseDetails", props);
    }
}
