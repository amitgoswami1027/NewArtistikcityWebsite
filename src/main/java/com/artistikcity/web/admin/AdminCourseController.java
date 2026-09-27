package com.artistikcity.web.admin;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Flash;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.http.Redirects;
import com.artistikcity.service.CourseService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Paginator;
import com.artistikcity.support.Row;
import com.artistikcity.support.Slugs;
import com.artistikcity.support.Storage;
import com.artistikcity.support.UploadedFile;
import com.artistikcity.support.Validator;
import com.artistikcity.view.Views;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Course & workshop management (Admin\CourseController). course_type_id 1 = course, 2 = workshop. */
@Controller
public class AdminCourseController {

    private static final int PER_PAGE = 5;

    private final Views views;
    private final Db db;
    private final Json json;
    private final Auth auth;
    private final Validator validator;
    private final Storage storage;
    private final Slugs slugs;
    private final CourseService courses;
    private final Redirects redirect;

    public AdminCourseController(Views views, Db db, Json json, Auth auth, Validator validator, Storage storage, Slugs slugs,
                                 CourseService courses, Redirects redirect) {
        this.views = views;
        this.db = db;
        this.json = json;
        this.auth = auth;
        this.validator = validator;
        this.storage = storage;
        this.slugs = slugs;
        this.courses = courses;
        this.redirect = redirect;
    }

    // ------------------------------------------------------------------ lists

    @GetMapping("/admin/courses")
    public ResponseEntity<String> index(HttpServletRequest request, @RequestParam(value = "page", required = false) String pageParam) {
        int page = Paginator.page(pageParam);
        String where = " where course_type_id = 1 and course_start_date >= ?";
        long total = db.count("select count(*) from courses" + where, LocalDate.now());
        List<Row> list = courses.withRelations(db.select("select * from courses" + where + " order by id desc offset "
                + ((page - 1) * PER_PAGE) + " rows fetch next " + PER_PAGE + " rows only", LocalDate.now()), false);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("courses", list);
        m.put("courses_count", list.size());
        m.put("pagination", Paginator.links(total, PER_PAGE, page, request.getRequestURI()));
        return views.page(request, "admin/courses/index", m);
    }

    @GetMapping("/admin/courses/archived/list")
    public ResponseEntity<String> archived(HttpServletRequest request) {
        List<Row> list = courses.withRelations(db.select("select * from courses where course_type_id = 1 and course_start_date < ? order by id desc",
                LocalDate.now()), false);
        return views.page(request, "admin/courses/archived", Map.of("courses", list, "courses_count", list.size()));
    }

    @GetMapping("/admin/workshop")
    public ResponseEntity<String> workshops(HttpServletRequest request) {
        List<Row> list = courses.withRelations(db.select("select * from courses where course_type_id = 2 order by id desc"), false);
        return views.page(request, "admin/workshop/index", Map.of("courses", list, "courses_count", list.size()));
    }

    private Map<String, Object> lookups() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("categories", db.select("select * from categories order by id"));
        m.put("course_types", db.select("select * from course_types order by id"));
        m.put("genres", db.select("select * from genres order by id"));
        m.put("mediums", db.select("select * from mediums order by id"));
        m.put("skills", db.select("select * from skills order by id"));
        return m;
    }

    @GetMapping("/admin/courses/create")
    public ResponseEntity<String> create(HttpServletRequest request) {
        return views.page(request, "admin/courses/create", lookups());
    }

    @GetMapping("/admin/workshop/create")
    public ResponseEntity<String> createWorkshop(HttpServletRequest request) {
        return views.page(request, "admin/workshop/create", lookups());
    }

    @GetMapping("/admin/courses/{course_id}")
    public ResponseEntity<String> show(HttpServletRequest request, @PathVariable("course_id") Long id) {
        return redirect.route(request, "admin.courses.edit", id);
    }

    // ------------------------------------------------------------------- edit

    private Map<String, Object> editModel(Long id) {
        Row course = NotFoundException.orFail(db.find("courses", id));
        courses.withRelations(course, false);
        Map<String, Object> m = lookups();
        m.put("course", course);
        List<?> prices = (List<?>) course.get("prices");
        m.put("course_prices", prices.isEmpty() ? new Row() : prices.get(0));
        m.put("course_photos", course.get("photos"));
        m.put("course_videos", db.select("select * from course_videos where course_id = ? order by id", id));
        m.put("genre_arr", course.str("genre_id", ""));
        Object includes = json.decode(course.str("course_includes"));
        m.put("course_includes", includes instanceof Map ? includes : new Row());
        for (String k : List.of("course_highlights", "course_for", "course_not_for", "course_problems_solved", "course_learn", "course_deliverables")) {
            Object v = json.decode(course.str(k));
            m.put(k, v instanceof List ? v : new ArrayList<>());
        }
        return m;
    }

    @GetMapping("/admin/courses/edit/{course_id}")
    public ResponseEntity<String> edit(HttpServletRequest request, @PathVariable("course_id") Long id) {
        return views.page(request, "admin/courses/edit", editModel(id));
    }

    @GetMapping("/admin/workshop/edit/{course_id}")
    public ResponseEntity<String> editWorkshop(HttpServletRequest request, @PathVariable("course_id") Long id) {
        return views.page(request, "admin/workshop/edit", editModel(id));
    }

    // ----------------------------------------------------------- store/update

    private String jsonList(Input in, String key) {
        List<String> values = new ArrayList<>();
        for (String v : in.array(key)) {
            if (v != null && !v.isBlank()) {
                values.add(v);
            }
        }
        return values.isEmpty() ? "" : json.encode(values);
    }

    /** Course columns from the form (shared by store and update). */
    private Row fields(Input in) {
        Row c = new Row();
        c.put("title", in.get("title"));
        c.put("sub_title", in.get("sub_title"));
        c.put("course_start_date", in.get("course_start_date") == null ? null : LocalDate.parse(in.get("course_start_date").substring(0, 10)));
        c.put("duration", in.get("duration"));
        c.put("sessions", in.get("sessions"));
        c.put("status", in.lng("status") == null ? 1 : in.lng("status"));
        c.put("medium_id", in.lng("medium"));
        c.put("genre_id", String.join(",", in.array("genre")));
        c.put("skill_id", in.lng("skill"));
        c.put("age_group", in.get("age_group"));
        c.put("course_type_id", in.lng("course_type_id") == null ? 1 : in.lng("course_type_id"));
        for (String k : List.of("introduction", "introduction_details", "summary", "summary_details", "schedule", "schedule_details",
                "mini_projects", "course_modules", "time_required", "course_outcome", "course_prerequisites")) {
            c.put(k, in.get(k));
        }
        Map<String, Object> includes = in.map("course_includes");
        if (includes != null) {
            includes = new LinkedHashMap<>(includes);
            includes.remove("");
        }
        c.put("course_includes", includes == null || includes.isEmpty() ? "" : json.encode(includes));
        for (String k : List.of("course_highlights", "course_for", "course_not_for", "course_problems_solved", "course_learn", "course_deliverables")) {
            c.put(k, jsonList(in, k));
        }
        c.put("course_based", in.get("course_based", "instructor_based"));
        return c;
    }

    @PostMapping("/admin/courses/store")
    public ResponseEntity<String> store(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "title", "required");
        Row c = fields(in);
        c.put("admin_id", auth.adminId(request));
        c.put("slug", slugs.unique("courses", "slug", in.get("title"), null));
        c.put("is_free_course", in.lng("is_free_course") == null ? 0 : in.lng("is_free_course"));
        long id = db.insert("courses", c);
        saveRelated(in, id);
        return redirect.routeWith(request, "success", "The courses has been added!", redirectRoute(in));
    }

    @PatchMapping("/admin/courses/edit/{course_id}")
    public ResponseEntity<String> update(HttpServletRequest request, @PathVariable("course_id") Long id) {
        Input in = Input.of(request, json);
        validator.validate(in, "title", "required");
        NotFoundException.orFail(db.find("courses", id));
        db.updateById("courses", id, fields(in));
        saveRelated(in, id);
        return redirect.routeWith(request, "success", "The courses has been updated!", redirectRoute(in));
    }

    private static String redirectRoute(Input in) {
        return Long.valueOf(2).equals(in.lng("course_type_id")) ? "admin.workshop" : "admin.courses";
    }

    /** Price, photos, schedule PDF and videos. */
    private void saveRelated(Input in, long id) {
        if (in.has("price_inr") || in.has("price_usd")) {
            db.insert("course_prices", Row.of("course_id", id, "price_inr", orZero(in.dbl("price_inr")), "price_usd", orZero(in.dbl("price_usd"))));
        }
        for (UploadedFile f : in.files("photos")) {
            String name = storage.storeUpload(f, "uploads/courses/" + id);
            db.insert("course_photos", Row.of("course_id", id, "photo_name", name));
        }
        if (in.hasFile("schedule_pdf")) {
            String name = storage.storeUpload(in.file("schedule_pdf"), "uploads/courses/schedule/" + id);
            db.updateById("courses", id, Row.of("schedule_pdf", name));
        }
        List<Map<String, Object>> videos = in.list("video");
        List<Row> rows = new ArrayList<>();
        for (Map<String, Object> v : videos) {
            if (v.get("url") != null || v.get("title") != null) {
                rows.add(Row.of("course_id", id, "video_title", v.get("title"), "video_url", v.get("url"), "status", 1));
            }
        }
        if (!rows.isEmpty()) {
            db.update("delete from course_videos where course_id = ?", id);
            for (Row r : rows) {
                db.insert("course_videos", r);
            }
        }
    }

    private static double orZero(Double d) {
        return d == null ? 0 : d;
    }

    @DeleteMapping("/admin/courses/delete/{course_id}")
    public ResponseEntity<String> destroy(HttpServletRequest request, @PathVariable("course_id") Long id) {
        NotFoundException.orFail(db.find("courses", id));
        try {
            db.update("delete from course_photos where course_id = ?", id);
            db.update("delete from course_prices where course_id = ?", id);
            db.update("delete from course_videos where course_id = ?", id);
            db.deleteById("courses", id);
            Flash.put(request, "success", "Course successfully deleted!");
        } catch (Db.DbException e) {
            Flash.put(request, "error", "This course has modules, lessons or certificates and cannot be deleted.");
        }
        return redirect.back(request);
    }

    /** Copies a course (with photos and prices) starting 5 days from today. */
    @GetMapping("/admin/courses/duplicate/{course_id}")
    public ResponseEntity<String> duplicate(HttpServletRequest request, @PathVariable("course_id") Long mainId) {
        Row main = NotFoundException.orFail(db.find("courses", mainId));
        Row copy = new Row(main);
        copy.remove("id");
        copy.remove("created_at");
        copy.remove("updated_at");
        copy.put("main_course_id", mainId);
        copy.put("slug", slugs.unique("courses", "slug", main.str("title"), null));
        copy.put("course_start_date", LocalDate.now().plusDays(5));
        long id = db.insert("courses", copy);
        for (Row p : db.select("select * from course_photos where course_id = ?", mainId)) {
            db.insert("course_photos", Row.of("course_id", id, "photo_name", p.get("photo_name")));
            storage.copy("uploads/courses/" + mainId + "/" + p.str("photo_name"), "uploads/courses/" + id + "/" + p.str("photo_name"));
        }
        for (Row p : db.select("select * from course_prices where course_id = ?", mainId)) {
            db.insert("course_prices", Row.of("course_id", id, "price_inr", p.get("price_inr"), "price_usd", p.get("price_usd")));
        }
        Flash.put(request, "success", "The course has been copied, please change your details.");
        return redirect.route(request, "admin.courses.edit", id);
    }
}
