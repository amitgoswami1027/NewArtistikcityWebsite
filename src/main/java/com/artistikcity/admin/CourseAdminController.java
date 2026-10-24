package com.artistikcity.admin;

import com.artistikcity.http.NotFoundException;
import com.artistikcity.http.Redirects;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Courses and workshops (course_type_id 1 = course, 2 = workshop): list, editor and curriculum.
 * Pages: /admin/courses, /admin/workshop (+ /create, /edit/{id}). JSON API under /admin/api/courses/**.
 * Instructors (persona "teacher") only see and edit their own courses.
 */
@Controller
public class CourseAdminController {

    static final List<String> LISTS = List.of("course_highlights", "course_for", "course_not_for", "course_problems_solved", "course_learn", "course_deliverables");
    static final List<String> INCLUDES = List.of("certificate", "study_material_access", "medium", "age", "skills");
    private static final List<String> RICH = List.of("introduction_details", "summary_details", "schedule_details", "course_outcome", "course_prerequisites");
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern URL = Pattern.compile("(?i)^https?://\\S+$");

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final Storage storage;
    private final Slugs slugs;
    private final Redirects redirect;
    private final Audit audit;

    public CourseAdminController(AdminKit kit, Db db, Json json, Storage storage, Slugs slugs, Redirects redirect, Audit audit) {
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.storage = storage;
        this.slugs = slugs;
        this.redirect = redirect;
        this.audit = audit;
    }

    // ================================================================= pages

    @GetMapping({"/admin/courses", "/admin/courses/"})
    public ResponseEntity<String> courses(HttpServletRequest request) {
        return list(request, 1);
    }

    @GetMapping({"/admin/workshop", "/admin/workshop/", "/admin/workshops"})
    public ResponseEntity<String> workshops(HttpServletRequest request) {
        return list(request, 2);
    }

    @GetMapping("/admin/courses/archived/list")
    public ResponseEntity<String> archived(HttpServletRequest request) {
        return redirect.to(request, "/admin/courses?when=past");
    }

    private ResponseEntity<String> list(HttpServletRequest request, int type) {
        return kit.page(request, "catalog", "Admin/Courses/Index", () -> {
            Row admin = kit.admin(request);
            Long teacher = kit.teacherScope(admin);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("kind", type == 2 ? "workshop" : "course");
            p.put("mediums", db.select("select id as value, name as label from mediums order by name"));
            p.put("canChooseInstructor", teacher == null);
            Supplier<Object> grid = () -> grid(request, type, teacher).page();
            p.put("grid", grid);
            Supplier<Object> counts = () -> {
                String scope = " where course_type_id = ?" + (teacher == null ? "" : " and admin_id = " + teacher);
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("all", db.count("select count(*) from courses" + scope, type));
                c.put("upcoming", db.count("select count(*) from courses" + scope + " and status = 1 and (course_start_date is null or course_start_date >= ?)", type, LocalDate.now()));
                c.put("past", db.count("select count(*) from courses" + scope + " and course_start_date < ?", type, LocalDate.now()));
                c.put("hidden", db.count("select count(*) from courses" + scope + " and status = 0", type));
                return c;
            };
            p.put("counts", counts);
            return p;
        });
    }

    private Grid grid(HttpServletRequest request, int type, Long teacher) {
        return Grid.from(db, request)
                .columns("c.id, c.title, c.slug, c.status, c.course_start_date, c.duration, c.sessions, c.created_at, c.updated_at, c.is_free_course,"
                        + " m.name as medium, a.name as instructor,"
                        + " (select p.photo_name from course_photos p where p.course_id = c.id order by p.id limit 1) as photo,"
                        + " (select cp.price_inr from course_prices cp where cp.course_id = c.id order by cp.id limit 1) as price_inr,"
                        + " (select cp.price_usd from course_prices cp where cp.course_id = c.id order by cp.id limit 1) as price_usd,"
                        + " (select count(*) from orders o where o.course_id = c.id and o.payment_status = 1) as enrolled,"
                        + " (select count(*) from course_modules cm where cm.course_id = c.id) as modules,"
                        + " (select count(*) from course_module_lessons cl where cl.course_id = c.id) as lessons")
                .from("courses c left join mediums m on m.id = c.medium_id left join admins a on a.id = c.admin_id")
                .where(Grid.where("c.course_type_id = ?", type))
                .where(teacher == null ? null : Grid.where("c.admin_id = ?", teacher))
                .search("c.title", "c.slug", "c.sub_title")
                .sort("title", "c.title").sort("start", "c.course_start_date").sort("updated", "c.updated_at")
                .sort("enrolled", "enrolled").sort("price", "price_inr")
                .defaultSort("updated", "desc").tieBreak("c.id desc")
                .filter("status", v -> "live".equals(v) ? Grid.where("c.status = 1") : "hidden".equals(v) ? Grid.where("c.status = 0") : null)
                .filter("when", v -> "upcoming".equals(v) ? Grid.where("(c.course_start_date is null or c.course_start_date >= ?)", LocalDate.now())
                        : "past".equals(v) ? Grid.where("c.course_start_date < ?", LocalDate.now()) : null)
                .filter("medium", v -> v.matches("\\d+") ? Grid.where("c.medium_id = ?", Long.parseLong(v)) : null)
                .map(r -> {
                    r.put("image", r.get("photo") == null ? null : "/storage/uploads/courses/" + r.get("id") + "/" + r.get("photo"));
                    return r;
                });
    }

    @GetMapping({"/admin/courses/create", "/admin/workshop/create"})
    public ResponseEntity<String> create(HttpServletRequest request) {
        int type = request.getRequestURI().startsWith("/admin/workshop") ? 2 : 1;
        return kit.page(request, "catalog", "Admin/Courses/Editor", () -> editorProps(request, null, type));
    }

    @GetMapping({"/admin/courses/edit/{id}", "/admin/workshop/edit/{id}"})
    public ResponseEntity<String> edit(HttpServletRequest request, @PathVariable("id") long id) {
        Row course = owned(request, id);
        int type = (int) course.lng("course_type_id", 1);
        return kit.page(request, "catalog", "Admin/Courses/Editor", () -> editorProps(request, course, type));
    }

    @GetMapping("/admin/courses/{id:\\d+}")
    public ResponseEntity<String> show(HttpServletRequest request, @PathVariable("id") long id) {
        return redirect.to(request, "/admin/courses/edit/" + id);
    }

    private Map<String, Object> editorProps(HttpServletRequest request, Row course, int type) {
        Row admin = kit.admin(request);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("kind", type == 2 ? "workshop" : "course");
        p.put("course", course == null ? null : view(course));
        p.put("curriculum", course == null ? null : curriculum(course.lng("id")));
        p.put("mediums", db.select("select id as value, name as label from mediums order by name"));
        p.put("genres", db.select("select id as value, name as label from genres order by name"));
        p.put("skills", db.select("select id as value, name as label from skills order by id"));
        p.put("instructors", kit.teacherScope(admin) == null
                ? db.select("select id as value, name as label from admins where admin_type in ('teacher', 'admin') order by name") : List.of());
        p.put("me", admin.get("id"));
        return p;
    }

    /** The course as the editor expects it. */
    private Map<String, Object> view(Row c) {
        long id = c.lng("id");
        Map<String, Object> v = new LinkedHashMap<>();
        for (String k : List.of("id", "title", "slug", "sub_title", "age_group", "course_based", "duration", "sessions", "time_required",
                "mini_projects", "course_modules", "introduction", "summary", "schedule")) {
            v.put(k, c.get(k) == null ? "" : String.valueOf(c.get(k)));
        }
        for (String k : RICH) {
            v.put(k, c.str(k, ""));
        }
        v.put("kind", c.lng("course_type_id", 1) == 2 ? "workshop" : "course");
        v.put("status", c.lng("status", 1) == 1);
        v.put("is_free_course", c.lng("is_free_course", 0) == 1);
        v.put("admin_id", c.get("admin_id") == null ? "" : String.valueOf(c.get("admin_id")));
        v.put("medium_id", c.get("medium_id") == null ? "" : String.valueOf(c.get("medium_id")));
        v.put("skill_id", c.get("skill_id") == null ? "" : String.valueOf(c.get("skill_id")));
        List<String> genres = new ArrayList<>();
        for (String g : c.str("genre_id", "").split(",")) {
            if (!g.isBlank()) {
                genres.add(g.trim());
            }
        }
        v.put("genre_ids", genres);
        v.put("course_start_date", c.get("course_start_date") == null ? "" : String.valueOf(c.get("course_start_date")).substring(0, 10));
        Object inc = json.decode(c.str("course_includes"));
        Map<String, Object> includes = new LinkedHashMap<>();
        for (String k : INCLUDES) {
            includes.put(k, inc instanceof Map<?, ?> m && "yes".equals(String.valueOf(m.get(k))));
        }
        v.put("includes", includes);
        for (String k : LISTS) {
            Object l = json.decode(c.str(k));
            v.put(k, l instanceof List ? l : List.of());
        }
        Row price = db.first("select price_inr, price_usd from course_prices where course_id = ? order by id limit 1", id);
        v.put("price_inr", price == null || price.get("price_inr") == null ? "" : plain(price.get("price_inr")));
        v.put("price_usd", price == null || price.get("price_usd") == null ? "" : plain(price.get("price_usd")));
        List<Map<String, Object>> photos = new ArrayList<>();
        for (Row ph : db.select("select id, photo_name from course_photos where course_id = ? order by id", id)) {
            photos.add(Map.of("id", ph.get("id"), "name", ph.str("photo_name"), "url", "/storage/uploads/courses/" + id + "/" + ph.str("photo_name")));
        }
        v.put("photos", photos);
        v.put("schedule_pdf", c.str("schedule_pdf", "").isBlank() ? null
                : Map.of("name", c.str("schedule_pdf"), "url", "/storage/uploads/courses/schedule/" + id + "/" + c.str("schedule_pdf")));
        v.put("intro_video_url", c.str("intro_video_url", ""));
        v.put("enrolled", db.count("select count(*) from orders where course_id = ? and payment_status = 1", id));
        v.put("public_url", "/course/" + c.str("slug"));
        v.put("updated_at", c.get("updated_at"));
        return v;
    }

    private static String plain(Object n) {
        double d = ((Number) n).doubleValue();
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    // ============================================================ save course

    @PostMapping("/admin/api/courses")
    public ResponseEntity<String> store(HttpServletRequest request) {
        if (!kit.can(kit.admin(request), "catalog")) {
            return kit.forbidden();
        }
        return save(request, null);
    }

    @PostMapping("/admin/api/courses/{id}")
    public ResponseEntity<String> update(HttpServletRequest request, @PathVariable("id") long id) {
        owned(request, id);
        return save(request, id);
    }

    private ResponseEntity<String> save(HttpServletRequest request, Long id) {
        Row admin = kit.admin(request);
        Input in = Input.of(request, json);
        Map<String, Object> d = json.decodeMap(in.get("data"));
        if (d == null) {
            return kit.fail(HttpStatus.BAD_REQUEST, "The form data is missing.");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String title = AdminKit.trim(str(d, "title"), 255);
        if (AdminKit.blank(title)) {
            errors.put("title", "Give the " + kindLabel(d) + " a title.");
        }
        String slug = str(d, "slug").trim().toLowerCase();
        if (slug.isEmpty() && !AdminKit.blank(title)) {
            slug = com.artistikcity.support.Str.slug(title);
        }
        if (!slug.isEmpty() && !SLUG.matcher(slug).matches()) {
            errors.put("slug", "Use lowercase letters, numbers and single dashes only.");
        } else if (!slug.isEmpty() && db.count("select count(*) from courses where slug = ? and id <> ?", slug, id == null ? 0 : id) > 0) {
            errors.put("slug", "Another course already uses this URL.");
        }
        LocalDate start = null;
        String startText = str(d, "course_start_date");
        if (!startText.isBlank()) {
            try {
                start = LocalDate.parse(startText.substring(0, Math.min(10, startText.length())));
            } catch (DateTimeParseException e) {
                errors.put("course_start_date", "Enter a valid date.");
            }
        } else if (!bool(d, "is_free_course")) {
            errors.put("course_start_date", "Choose when the first session starts.");
        }
        boolean free = bool(d, "is_free_course");
        Double inr = number(d, "price_inr", errors);
        Double usd = number(d, "price_usd", errors);
        if (!free) {
            if (inr == null && !errors.containsKey("price_inr")) {
                errors.put("price_inr", "Enter the price in rupees.");
            }
            if (usd == null && !errors.containsKey("price_usd")) {
                errors.put("price_usd", "Enter the price in US dollars.");
            }
        }
        for (String k : List.of("duration", "sessions", "time_required", "mini_projects", "course_modules")) {
            String n = str(d, k).trim();
            if (!n.isEmpty() && !n.matches("\\d{1,4}")) {
                errors.put(k, "Enter a whole number.");
            }
        }
        Long medium = idOrNull(d, "medium_id");
        if (medium != null && db.find("mediums", medium) == null) {
            errors.put("medium_id", "Choose a medium from the list.");
        }
        Long skill = idOrNull(d, "skill_id");
        Long instructor = kit.teacherScope(admin) != null ? admin.lng("id") : idOrNull(d, "admin_id");
        if (instructor != null && db.find("admins", instructor) == null) {
            errors.put("admin_id", "Choose an instructor from the list.");
        }
        // the course's own trailer / landing video; lesson videos are saved separately with each lesson
        String introVideo = videoUrl(str(d, "intro_video_url"), "intro_video_url", errors);
        List<UploadedFile> photos = in.files("photos");
        for (UploadedFile f : photos) {
            String e = AdminKit.checkImage(f);
            if (e != null) {
                errors.put("photos", f.getClientOriginalName() + ": " + e);
            }
        }
        UploadedFile pdf = in.hasFile("schedule_pdf") ? in.file("schedule_pdf") : null;
        String pdfError = AdminKit.checkPdf(pdf);
        if (pdfError != null) {
            errors.put("schedule_pdf", pdfError);
        }
        if (!errors.isEmpty()) {
            return kit.invalid(errors);
        }

        Row c = new Row();
        c.put("title", title);
        c.put("slug", slug.isEmpty() ? slugs.unique("courses", "slug", title, id) : slug);
        c.put("sub_title", AdminKit.trim(str(d, "sub_title"), 500));
        c.put("course_type_id", "workshop".equals(str(d, "kind")) ? 2 : 1);
        c.put("status", bool(d, "status") ? 1 : 0);
        c.put("is_free_course", free ? 1 : 0);
        c.put("admin_id", instructor);
        c.put("medium_id", medium);
        c.put("skill_id", skill);
        List<String> genres = new ArrayList<>();
        if (d.get("genre_ids") instanceof List<?> gs) {
            for (Object g : gs) {
                if (String.valueOf(g).matches("\\d+")) {
                    genres.add(String.valueOf(g));
                }
            }
        }
        c.put("genre_id", String.join(",", genres));
        c.put("course_start_date", start);
        for (String k : List.of("age_group", "duration", "sessions", "time_required", "mini_projects", "course_modules")) {
            c.put(k, AdminKit.trim(str(d, k), 255));
        }
        c.put("course_based", "video_course".equals(str(d, "course_based")) ? "video_course" : "instructor_based");
        c.put("intro_video_url", introVideo);
        for (String k : List.of("introduction", "summary", "schedule")) {
            c.put(k, str(d, k).trim());
        }
        for (String k : RICH) {
            c.put(k, HtmlCleaner.clean(str(d, k)));
        }
        Map<String, Object> includes = new LinkedHashMap<>();
        if (d.get("includes") instanceof Map<?, ?> inc) {
            for (String k : INCLUDES) {
                if (Boolean.TRUE.equals(inc.get(k)) || "yes".equals(String.valueOf(inc.get(k)))) {
                    includes.put(k, "yes");
                }
            }
        }
        c.put("course_includes", includes.isEmpty() ? "" : json.encode(includes));
        for (String k : LISTS) {
            List<String> items = new ArrayList<>();
            if (d.get(k) instanceof List<?> l) {
                for (Object o : l) {
                    String t = o == null ? "" : String.valueOf(o).trim();
                    if (!t.isEmpty()) {
                        items.add(AdminKit.trim(t, 500));
                    }
                }
            }
            c.put(k, items.isEmpty() ? "" : json.encode(items));
        }

        long courseId;
        if (id == null) {
            courseId = db.insert("courses", c);
            audit.log(admin, "course", courseId, "created", title);
        } else {
            db.updateById("courses", id, c);
            courseId = id;
            audit.log(admin, "course", courseId, "updated", title);
        }
        // price: one row per course (the site reads the first row)
        Row price = db.first("select id from course_prices where course_id = ? order by id limit 1", courseId);
        Row pv = Row.of("price_inr", inr == null ? 0 : inr, "price_usd", usd == null ? 0 : usd);
        if (price == null) {
            pv.put("course_id", courseId);
            db.insert("course_prices", pv);
        } else {
            db.updateById("course_prices", price.get("id"), pv);
            db.update("delete from course_prices where course_id = ? and id <> ?", courseId, price.get("id"));
        }
        for (UploadedFile f : photos) {
            db.insert("course_photos", Row.of("course_id", courseId, "photo_name", storage.storeUpload(f, "uploads/courses/" + courseId)));
        }
        if (pdf != null) {
            db.updateById("courses", courseId, Row.of("schedule_pdf", storage.storeUpload(pdf, "uploads/courses/schedule/" + courseId)));
        }
        Row saved = db.find("courses", courseId);
        String base = saved.lng("course_type_id", 1) == 2 ? "/admin/workshop" : "/admin/courses";
        return kit.ok(Map.of("id", courseId, "course", view(saved), "editUrl", base + "/edit/" + courseId,
                "message", id == null ? "Created. Add modules and lessons next." : "Saved."));
    }

    private static String kindLabel(Map<String, Object> d) {
        return "workshop".equals(String.valueOf(d.get("kind"))) ? "workshop" : "course";
    }

    static String str(Map<String, Object> d, String k) {
        Object v = d.get(k);
        return v == null ? "" : String.valueOf(v);
    }

    static boolean bool(Map<String, Object> d, String k) {
        Object v = d.get(k);
        return Boolean.TRUE.equals(v) || "1".equals(String.valueOf(v)) || "true".equals(String.valueOf(v));
    }

    private static Long idOrNull(Map<String, Object> d, String k) {
        String v = str(d, k).trim();
        return v.matches("\\d+") ? Long.parseLong(v) : null;
    }

    private static Double number(Map<String, Object> d, String k, Map<String, String> errors) {
        String v = str(d, k).trim().replace(",", "");
        if (v.isEmpty()) {
            return null;
        }
        try {
            double n = Double.parseDouble(v);
            if (n < 0 || n > 10_000_000 || Double.isNaN(n)) {
                errors.put(k, "Enter an amount between 0 and 10,000,000.");
                return null;
            }
            return Math.round(n * 100) / 100.0;
        } catch (NumberFormatException e) {
            errors.put(k, "Enter a number, e.g. 4999.");
            return null;
        }
    }

    // ========================================================== quick actions

    @PostMapping("/admin/api/courses/{id}/status")
    public ResponseEntity<String> status(HttpServletRequest request, @PathVariable("id") long id) {
        owned(request, id);
        Input in = Input.of(request, json);
        boolean live = in.bool("status");
        db.updateById("courses", id, Row.of("status", live ? 1 : 0));
        audit.log(kit.admin(request), "course", id, live ? "published" : "hidden", null);
        return kit.ok(Map.of("id", id, "status", live ? 1 : 0, "message", live ? "Now visible on the site." : "Hidden from the site."));
    }

    @PostMapping("/admin/api/courses/{id}/duplicate")
    public ResponseEntity<String> duplicate(HttpServletRequest request, @PathVariable("id") long id) {
        Row main = owned(request, id);
        Row copy = new Row(main);
        copy.remove("id");
        copy.remove("created_at");
        copy.remove("updated_at");
        copy.put("main_course_id", id);
        copy.put("title", AdminKit.trim("Copy of " + main.str("title"), 255));
        copy.put("slug", slugs.unique("courses", "slug", main.str("title") + " copy", null));
        copy.put("status", 0);
        copy.put("course_start_date", LocalDate.now().plusDays(14));
        long newId = db.insert("courses", copy);
        for (Row p : db.select("select * from course_photos where course_id = ? order by id", id)) {
            db.insert("course_photos", Row.of("course_id", newId, "photo_name", p.get("photo_name")));
            storage.copy("uploads/courses/" + id + "/" + p.str("photo_name"), "uploads/courses/" + newId + "/" + p.str("photo_name"));
        }
        Row price = db.first("select * from course_prices where course_id = ? order by id limit 1", id);
        if (price != null) {
            db.insert("course_prices", Row.of("course_id", newId, "price_inr", price.get("price_inr"), "price_usd", price.get("price_usd")));
        }
        // copy the curriculum too, so a new batch starts with the same modules and lessons
        for (Row m : db.select("select * from course_modules where course_id = ? order by sort_order, id", id)) {
            long mid = db.insert("course_modules", Row.of("admin_id", m.get("admin_id"), "course_id", newId, "module_title", m.get("module_title"),
                    "module_duration", m.get("module_duration"), "sort_order", m.get("sort_order"), "status", m.get("status")));
            for (Row l : db.select("select * from course_module_lessons where module_id = ? order by sort_order, id", m.get("id"))) {
                long lid = db.insert("course_module_lessons", Row.of("admin_id", l.get("admin_id"), "course_id", newId, "module_id", mid,
                        "lesson_title", l.get("lesson_title"), "lesson_description", l.get("lesson_description"), "lesson_pdf", l.get("lesson_pdf"),
                        "video_url", l.get("video_url"), "sort_order", l.get("sort_order"), "status", l.get("status")));
                if (l.str("lesson_pdf") != null && !l.str("lesson_pdf").isBlank()) {
                    storage.copy("uploads/courses/lessons/" + l.get("id") + "/" + l.str("lesson_pdf"), "uploads/courses/lessons/" + lid + "/" + l.str("lesson_pdf"));
                }
                for (Row t : db.select("select * from course_module_lesson_tasks where lesson_id = ? order by id", l.get("id"))) {
                    db.insert("course_module_lesson_tasks", Row.of("admin_id", t.get("admin_id"), "course_id", newId, "module_id", mid, "lesson_id", lid,
                            "task_title", t.get("task_title"), "task_description", t.get("task_description"), "status", t.get("status")));
                }
            }
        }
        for (Row p : db.select("select * from course_projects where course_id = ? order by id", id)) {
            db.insert("course_projects", Row.of("admin_id", p.get("admin_id"), "course_id", newId, "project_title", p.get("project_title"),
                    "project_description", p.get("project_description"), "status", p.get("status")));
        }
        audit.log(kit.admin(request), "course", newId, "duplicated", "from #" + id);
        String base = main.lng("course_type_id", 1) == 2 ? "/admin/workshop" : "/admin/courses";
        return kit.ok(Map.of("id", newId, "editUrl", base + "/edit/" + newId, "message", "Copied as a hidden draft. Check the dates and publish it."));
    }

    @DeleteMapping("/admin/api/courses/{id}")
    public ResponseEntity<String> destroy(HttpServletRequest request, @PathVariable("id") long id) {
        Row c = owned(request, id);
        long orders = db.count("select count(*) from orders where course_id = ? and payment_status = 1", id);
        if (orders > 0) {
            return kit.fail(HttpStatus.CONFLICT, orders + (orders == 1 ? " student has" : " students have")
                    + " paid for this " + (c.lng("course_type_id", 1) == 2 ? "workshop" : "course") + ". Hide it instead of deleting it.");
        }
        for (Row l : db.select("select id from course_module_lessons where course_id = ?", id)) {
            db.update("delete from lesson_completions where lesson_id = ?", l.get("id"));
        }
        for (String t : List.of("course_module_lesson_tasks", "course_module_lessons", "course_modules", "course_projects", "course_photos",
                "course_prices", "course_videos", "cart_items")) {
            db.update("delete from " + t + " where course_id = ?", id);
        }
        db.update("delete from wishlist_items where item_type = 'course' and item_id = ?", id);
        db.deleteById("courses", id);
        audit.log(kit.admin(request), "course", id, "deleted", c.str("title"));
        return kit.ok(Map.of("message", "Deleted “" + c.str("title") + "”."));
    }

    @DeleteMapping("/admin/api/courses/{id}/photos/{photoId}")
    public ResponseEntity<String> deletePhoto(HttpServletRequest request, @PathVariable("id") long id, @PathVariable("photoId") long photoId) {
        owned(request, id);
        db.update("delete from course_photos where id = ? and course_id = ?", photoId, id);
        return kit.ok(Map.of("message", "Photo removed."));
    }

    @DeleteMapping("/admin/api/courses/{id}/schedule")
    public ResponseEntity<String> deleteSchedule(HttpServletRequest request, @PathVariable("id") long id) {
        owned(request, id);
        db.updateById("courses", id, Row.of("schedule_pdf", null));
        return kit.ok(Map.of("message", "Schedule PDF removed."));
    }

    // ============================================================= curriculum

    Map<String, Object> curriculum(long courseId) {
        List<Map<String, Object>> modules = new ArrayList<>();
        Map<Long, List<Map<String, Object>>> lessonsByModule = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> tasksByLesson = new LinkedHashMap<>();
        for (Row t : db.select("select id, lesson_id, task_title, task_description, status from course_module_lesson_tasks where course_id = ? order by id", courseId)) {
            Map<String, Object> tv = new LinkedHashMap<>();
            tv.put("id", t.get("id"));
            tv.put("title", t.str("task_title", ""));
            tv.put("description", t.str("task_description", ""));
            tv.put("status", "1".equals(t.str("status")));
            tasksByLesson.computeIfAbsent(t.lng("lesson_id"), k -> new ArrayList<>()).add(tv);
        }
        for (Row l : db.select("select id, module_id, lesson_title, lesson_description, lesson_pdf, video_url, status from course_module_lessons"
                + " where course_id = ? order by sort_order, id", courseId)) {
            Map<String, Object> lv = new LinkedHashMap<>();
            lv.put("id", l.get("id"));
            lv.put("title", l.str("lesson_title", ""));
            lv.put("description", l.str("lesson_description", ""));
            lv.put("pdf", l.str("lesson_pdf", "").isBlank() ? null
                    : Map.of("name", l.str("lesson_pdf"), "url", "/storage/uploads/courses/lessons/" + l.get("id") + "/" + l.str("lesson_pdf")));
            lv.put("video_url", l.str("video_url", ""));
            lv.put("status", "1".equals(l.str("status")));
            lv.put("tasks", tasksByLesson.getOrDefault(l.lng("id"), List.of()));
            lessonsByModule.computeIfAbsent(l.lng("module_id"), k -> new ArrayList<>()).add(lv);
        }
        for (Row m : db.select("select id, module_title, module_duration, status from course_modules where course_id = ? order by sort_order, id", courseId)) {
            Map<String, Object> mv = new LinkedHashMap<>();
            mv.put("id", m.get("id"));
            mv.put("title", m.str("module_title", ""));
            mv.put("duration", m.str("module_duration", ""));
            mv.put("status", "1".equals(m.str("status")));
            mv.put("lessons", lessonsByModule.getOrDefault(m.lng("id"), List.of()));
            modules.add(mv);
        }
        List<Map<String, Object>> projects = new ArrayList<>();
        for (Row p : db.select("select id, project_title, project_description, status from course_projects where course_id = ? order by id", courseId)) {
            projects.add(Map.of("id", p.get("id"), "title", p.str("project_title", ""), "description", p.str("project_description", ""),
                    "status", "1".equals(p.str("status"))));
        }
        return Map.of("modules", modules, "projects", projects);
    }

    private ResponseEntity<String> curriculumOk(long courseId, String message) {
        return kit.ok(Map.of("curriculum", curriculum(courseId), "message", message));
    }

    @PostMapping("/admin/api/courses/{id}/modules")
    public ResponseEntity<String> saveModule(HttpServletRequest request, @PathVariable("id") long courseId) {
        owned(request, courseId);
        Input in = Input.of(request, json);
        String title = AdminKit.trim(in.get("title"), 255);
        if (AdminKit.blank(title)) {
            return kit.invalid(Map.of("title", "Give the module a title."));
        }
        Row v = Row.of("module_title", title, "module_duration", AdminKit.trim(in.get("duration"), 255), "status", in.has("status") && !in.bool("status") ? "0" : "1");
        Long moduleId = in.lng("id");
        if (moduleId == null) {
            v.put("course_id", courseId);
            v.put("admin_id", kit.admin(request).get("id"));
            v.put("sort_order", db.count("select coalesce(max(sort_order), 0) from course_modules where course_id = ?", courseId) + 1);
            db.insert("course_modules", v);
        } else {
            moduleOf(courseId, moduleId);
            db.updateById("course_modules", moduleId, v);
        }
        return curriculumOk(courseId, moduleId == null ? "Module added." : "Module saved.");
    }

    @DeleteMapping("/admin/api/courses/{id}/modules/{moduleId}")
    public ResponseEntity<String> deleteModule(HttpServletRequest request, @PathVariable("id") long courseId, @PathVariable("moduleId") long moduleId) {
        owned(request, courseId);
        moduleOf(courseId, moduleId);
        for (Row l : db.select("select id from course_module_lessons where module_id = ?", moduleId)) {
            deleteLessonRows(l.lng("id"));
        }
        db.deleteById("course_modules", moduleId);
        audit.log(kit.admin(request), "course", courseId, "module deleted", "#" + moduleId);
        return curriculumOk(courseId, "Module deleted.");
    }

    @PostMapping("/admin/api/courses/{id}/modules/{moduleId}/move")
    public ResponseEntity<String> moveModule(HttpServletRequest request, @PathVariable("id") long courseId, @PathVariable("moduleId") long moduleId) {
        owned(request, courseId);
        moduleOf(courseId, moduleId);
        reorder("course_modules", "course_id = ?", courseId, moduleId, Input.of(request, json).get("direction"));
        return curriculumOk(courseId, "Order saved.");
    }

    @PostMapping("/admin/api/courses/{id}/lessons")
    public ResponseEntity<String> saveLesson(HttpServletRequest request, @PathVariable("id") long courseId) {
        owned(request, courseId);
        Input in = Input.of(request, json);
        Map<String, String> errors = new LinkedHashMap<>();
        String title = AdminKit.trim(in.get("title"), 255);
        if (AdminKit.blank(title)) {
            errors.put("title", "Give the lesson a title.");
        }
        Long moduleId = in.lng("module_id");
        if (moduleId == null || db.first("select id from course_modules where id = ? and course_id = ?", moduleId, courseId) == null) {
            errors.put("module_id", "Choose the module this lesson belongs to.");
        }
        UploadedFile pdf = in.hasFile("pdf") ? in.file("pdf") : null;
        String pdfError = AdminKit.checkPdf(pdf);
        if (pdfError != null) {
            errors.put("pdf", pdfError);
        }
        // this lesson's own video: only written to this lesson row, never to the course or other lessons
        String video = in.has("video_url") ? videoUrl(in.get("video_url"), "video_url", errors) : null;
        if (!errors.isEmpty()) {
            return kit.invalid(errors);
        }
        Row v = Row.of("lesson_title", title, "module_id", moduleId, "lesson_description", HtmlCleaner.clean(in.get("description")),
                "status", in.has("status") && !in.bool("status") ? "0" : "1");
        if (in.has("video_url")) {
            v.put("video_url", video);
        }
        Long lessonId = in.lng("id");
        if (lessonId == null) {
            v.put("course_id", courseId);
            v.put("admin_id", kit.admin(request).get("id"));
            v.put("sort_order", db.count("select coalesce(max(sort_order), 0) from course_module_lessons where module_id = ?", moduleId) + 1);
            lessonId = db.insert("course_module_lessons", v);
        } else {
            lessonOf(courseId, lessonId);
            db.updateById("course_module_lessons", lessonId, v);
            db.update("update course_module_lesson_tasks set module_id = ? where lesson_id = ?", moduleId, lessonId);
        }
        if (pdf != null) {
            db.updateById("course_module_lessons", lessonId, Row.of("lesson_pdf", storage.storeUpload(pdf, "uploads/courses/lessons/" + lessonId)));
        } else if (in.bool("remove_pdf")) {
            db.updateById("course_module_lessons", lessonId, Row.of("lesson_pdf", null));
        }
        return curriculumOk(courseId, in.lng("id") == null ? "Lesson added." : "Lesson saved.");
    }

    @DeleteMapping("/admin/api/courses/{id}/lessons/{lessonId}")
    public ResponseEntity<String> deleteLesson(HttpServletRequest request, @PathVariable("id") long courseId, @PathVariable("lessonId") long lessonId) {
        owned(request, courseId);
        lessonOf(courseId, lessonId);
        deleteLessonRows(lessonId);
        audit.log(kit.admin(request), "course", courseId, "lesson deleted", "#" + lessonId);
        return curriculumOk(courseId, "Lesson deleted.");
    }

    @PostMapping("/admin/api/courses/{id}/lessons/{lessonId}/move")
    public ResponseEntity<String> moveLesson(HttpServletRequest request, @PathVariable("id") long courseId, @PathVariable("lessonId") long lessonId) {
        owned(request, courseId);
        Row l = lessonOf(courseId, lessonId);
        reorder("course_module_lessons", "module_id = ?", l.lng("module_id"), lessonId, Input.of(request, json).get("direction"));
        return curriculumOk(courseId, "Order saved.");
    }

    private void deleteLessonRows(long lessonId) {
        db.update("delete from lesson_completions where lesson_id = ?", lessonId);
        db.update("delete from course_module_lesson_tasks where lesson_id = ?", lessonId);
        db.deleteById("course_module_lessons", lessonId);
    }

    @PostMapping("/admin/api/courses/{id}/tasks")
    public ResponseEntity<String> saveTask(HttpServletRequest request, @PathVariable("id") long courseId) {
        owned(request, courseId);
        Input in = Input.of(request, json);
        Map<String, String> errors = new LinkedHashMap<>();
        String title = AdminKit.trim(in.get("title"), 255);
        if (AdminKit.blank(title)) {
            errors.put("title", "Give the task a title.");
        }
        Long lessonId = in.lng("lesson_id");
        Row lesson = lessonId == null ? null : db.first("select id, module_id from course_module_lessons where id = ? and course_id = ?", lessonId, courseId);
        if (lesson == null) {
            errors.put("lesson_id", "Choose a lesson.");
        }
        if (!errors.isEmpty()) {
            return kit.invalid(errors);
        }
        Row v = Row.of("task_title", title, "task_description", AdminKit.trim(in.get("description"), 20000),
                "status", in.has("status") && !in.bool("status") ? "0" : "1");
        Long taskId = in.lng("id");
        if (taskId == null) {
            v.put("course_id", courseId);
            v.put("module_id", lesson.get("module_id"));
            v.put("lesson_id", lessonId);
            v.put("admin_id", kit.admin(request).get("id"));
            db.insert("course_module_lesson_tasks", v);
        } else {
            NotFoundException.orFail(db.first("select id from course_module_lesson_tasks where id = ? and course_id = ?", taskId, courseId));
            db.updateById("course_module_lesson_tasks", taskId, v);
        }
        return curriculumOk(courseId, taskId == null ? "Task added." : "Task saved.");
    }

    @DeleteMapping("/admin/api/courses/{id}/tasks/{taskId}")
    public ResponseEntity<String> deleteTask(HttpServletRequest request, @PathVariable("id") long courseId, @PathVariable("taskId") long taskId) {
        owned(request, courseId);
        long replies = db.count("select count(*) from student_tasks where task_id = ? and deleted_at is null", taskId);
        if (replies > 0) {
            return kit.fail(HttpStatus.CONFLICT, "Students have already answered this task. Switch it off instead of deleting it.");
        }
        db.update("delete from course_module_lesson_tasks where id = ? and course_id = ?", taskId, courseId);
        return curriculumOk(courseId, "Task deleted.");
    }

    @PostMapping("/admin/api/courses/{id}/projects")
    public ResponseEntity<String> saveProject(HttpServletRequest request, @PathVariable("id") long courseId) {
        owned(request, courseId);
        Input in = Input.of(request, json);
        String title = AdminKit.trim(in.get("title"), 255);
        if (AdminKit.blank(title)) {
            return kit.invalid(Map.of("title", "Give the project a title."));
        }
        Row v = Row.of("project_title", title, "project_description", HtmlCleaner.clean(in.get("description")),
                "status", in.has("status") && !in.bool("status") ? "0" : "1");
        Long projectId = in.lng("id");
        if (projectId == null) {
            v.put("course_id", courseId);
            v.put("admin_id", kit.admin(request).get("id"));
            db.insert("course_projects", v);
        } else {
            NotFoundException.orFail(db.first("select id from course_projects where id = ? and course_id = ?", projectId, courseId));
            db.updateById("course_projects", projectId, v);
        }
        return curriculumOk(courseId, projectId == null ? "Project added." : "Project saved.");
    }

    @DeleteMapping("/admin/api/courses/{id}/projects/{projectId}")
    public ResponseEntity<String> deleteProject(HttpServletRequest request, @PathVariable("id") long courseId, @PathVariable("projectId") long projectId) {
        owned(request, courseId);
        db.update("delete from course_projects where id = ? and course_id = ?", projectId, courseId);
        return curriculumOk(courseId, "Project deleted.");
    }

    // ================================================================ videos

    /** Largest video file accepted by the upload endpoint. */
    static final long MAX_VIDEO = 500L * 1024 * 1024;
    private static final Pattern STORED_VIDEO = Pattern.compile("^/storage/(uploads/courses/\\d+/videos/[A-Za-z0-9._-]+)$");
    private static final List<String> VIDEO_EXT = List.of("mp4", "m4v", "mov", "webm");

    /**
     * A video value from a form: empty (no video), a link (https://... YouTube, Vimeo or a video file),
     * or a file uploaded through {@link #uploadVideo}. Puts an error under {@code field} when it is neither.
     *
     * @return the value to store, or null for none
     */
    String videoUrl(String raw, String field, Map<String, String> errors) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            return null;
        }
        if (v.length() > 1000) {
            errors.put(field, "That link is too long (1000 characters at most).");
            return null;
        }
        java.util.regex.Matcher m = STORED_VIDEO.matcher(v);
        if (m.matches()) {
            if (!storage.exists(m.group(1))) {
                errors.put(field, "The uploaded video file is missing. Upload it again.");
            }
            return v;
        }
        if (!URL.matcher(v).matches()) {
            errors.put(field, "Enter a full link starting with https://, or upload a video file.");
        }
        return v;
    }

    /**
     * Uploads a video file (MP4, M4V, MOV or WebM, up to 500 MB) for a course's intro or one of its lessons.
     * The file is streamed to storage/uploads/courses/{id}/videos/ and the answer carries its URL; the form then
     * saves that URL in the field it belongs to (intro_video_url or the lesson's video_url).
     */
    @PostMapping("/admin/api/courses/{id}/videos")
    public ResponseEntity<String> uploadVideo(HttpServletRequest request, @PathVariable("id") long courseId,
                                              @org.springframework.web.bind.annotation.RequestParam(value = "file", required = false)
                                              org.springframework.web.multipart.MultipartFile file) {
        owned(request, courseId);
        if (file == null || file.isEmpty()) {
            return kit.invalid(Map.of("file", "Choose a video file to upload."));
        }
        String error = checkVideo(file);
        if (error != null) {
            return kit.invalid(Map.of("file", error));
        }
        String original = file.getOriginalFilename() == null ? "video" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = original.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        String base = com.artistikcity.support.Str.slug(dot > 0 ? original.substring(0, dot) : original);
        String name = (System.currentTimeMillis() / 1000) + "_" + (base == null || base.isBlank() ? "video" : AdminKit.trim(base, 80)) + "." + ext;
        String rel = "uploads/courses/" + courseId + "/videos/" + name;
        try (java.io.InputStream src = file.getInputStream()) {
            storage.put(rel, src, file.getSize());     // local disk or S3, streamed (never held in memory)
        } catch (java.io.IOException | java.io.UncheckedIOException e) {
            return kit.fail(HttpStatus.INTERNAL_SERVER_ERROR, "The video could not be saved. Try again.");
        }
        audit.log(kit.admin(request), "course", courseId, "video uploaded", name);
        return kit.ok(Map.of("url", "/storage/" + rel, "name", original, "size", file.getSize(), "message", "Video uploaded."));
    }

    /** @return an error text, or null when the upload looks like a playable video file. */
    static String checkVideo(org.springframework.web.multipart.MultipartFile f) {
        if (f.getSize() > MAX_VIDEO) {
            return "Video files can be up to 500 MB. Upload larger videos to YouTube or Vimeo and paste the link.";
        }
        String name = f.getOriginalFilename() == null ? "" : f.getOriginalFilename();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        byte[] b = new byte[12];
        int n = 0;
        try (java.io.InputStream in = f.getInputStream()) {
            n = in.readNBytes(b, 0, 12);
        } catch (java.io.IOException e) {
            return "The file could not be read.";
        }
        return VIDEO_EXT.contains(ext) && isVideo(b, n) ? null : "Upload an MP4, MOV or WebM video.";
    }

    /** MP4 / MOV / M4V ("....ftyp") or WebM / Matroska (EBML header). */
    static boolean isVideo(byte[] b, int n) {
        boolean iso = n >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
        boolean ebml = n >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45 && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3;
        return iso || ebml;
    }

    // ================================================================ helpers

    /** The course, if this admin may manage it (404 otherwise, so ids are not probed). */
    private Row owned(HttpServletRequest request, long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "catalog")) {
            throw new NotFoundException();
        }
        Row c = NotFoundException.orFail(db.find("courses", id));
        Long teacher = kit.teacherScope(admin);
        if (teacher != null && !teacher.equals(c.lng("admin_id"))) {
            throw new NotFoundException();
        }
        return c;
    }

    private Row moduleOf(long courseId, long moduleId) {
        return NotFoundException.orFail(db.first("select * from course_modules where id = ? and course_id = ?", moduleId, courseId));
    }

    private Row lessonOf(long courseId, long lessonId) {
        return NotFoundException.orFail(db.first("select * from course_module_lessons where id = ? and course_id = ?", lessonId, courseId));
    }

    /** Swaps an item with its neighbour ("up" / "down") inside its parent and renumbers 1..n. */
    private void reorder(String table, String parentWhere, Object parent, long itemId, String direction) {
        List<Row> items = db.select("select id from " + table + " where " + parentWhere + " order by sort_order, id", parent);
        int i = -1;
        for (int k = 0; k < items.size(); k++) {
            if (items.get(k).lng("id") == itemId) {
                i = k;
            }
        }
        int j = "up".equals(direction) ? i - 1 : i + 1;
        if (i >= 0 && j >= 0 && j < items.size()) {
            Row a = items.get(i);
            items.set(i, items.get(j));
            items.set(j, a);
        }
        for (int k = 0; k < items.size(); k++) {
            db.update("update " + table + " set sort_order = ? where id = ?", k + 1, items.get(k).get("id"));
        }
    }
}
