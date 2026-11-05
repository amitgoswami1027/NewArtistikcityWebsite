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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Free lessons (free_courses + free_course_videos) shown to students after they join. Page: /admin/free-courses. */
@Controller
public class FreeCourseAdminController {

    private static final Pattern URL = Pattern.compile("(?i)^https?://\\S+$");

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final Storage storage;
    private final Slugs slugs;
    private final Redirects redirect;
    private final Audit audit;

    public FreeCourseAdminController(AdminKit kit, Db db, Json json, Storage storage, Slugs slugs, Redirects redirect, Audit audit) {
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.storage = storage;
        this.slugs = slugs;
        this.redirect = redirect;
        this.audit = audit;
    }

    @GetMapping({"/admin/free-courses", "/admin/free-courses/"})
    public ResponseEntity<String> index(HttpServletRequest request) {
        return kit.page(request, "settings", "Admin/FreeCourses", () -> {
            Map<String, Object> p = new LinkedHashMap<>();
            Supplier<Object> grid = () -> Grid.from(db, request)
                    .columns("f.*, (select count(*) from free_course_videos v where v.free_course_id = f.id) as videos")
                    .from("free_courses f")
                    .search("f.title", "f.slug")
                    .sort("title", "f.title").sort("updated", "f.updated_at").defaultSort("updated", "desc").tieBreak("f.id desc")
                    .filter("status", v -> "live".equals(v) ? Grid.where("f.status = 1") : "hidden".equals(v) ? Grid.where("f.status = 0") : null)
                    .map(this::view)
                    .page();
            p.put("grid", grid);
            return p;
        });
    }

    @GetMapping({"/admin/free-courses/create", "/admin/free-courses/edit/{id}", "/admin/free-courses/{id:\\d+}"})
    public ResponseEntity<String> legacy(HttpServletRequest request) {
        String[] parts = request.getRequestURI().split("/");
        String last = parts[parts.length - 1];
        return redirect.to(request, "/admin/free-courses" + (last.matches("\\d+") ? "?edit=" + last : "?new=1"));
    }

    private Row view(Row f) {
        long id = f.lng("id");
        f.put("photo_url", f.str("photo", "").isBlank() ? null : "/storage/uploads/free-courses/" + id + "/" + f.str("photo"));
        List<Map<String, Object>> videos = new ArrayList<>();
        for (Row v : db.select("select video_title, video_url from free_course_videos where free_course_id = ? order by id", id)) {
            videos.add(Map.of("title", v.str("video_title", ""), "url", v.str("video_url", "")));
        }
        f.put("video_list", videos);
        return f;
    }

    @PostMapping("/admin/api/free-courses")
    public ResponseEntity<String> save(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        Input in = Input.of(request, json);
        Map<String, Object> d = json.decodeMap(in.get("data"));
        if (d == null) {
            return kit.fail(HttpStatus.BAD_REQUEST, "The form data is missing.");
        }
        Long id = CourseAdminController.str(d, "id").matches("\\d+") ? Long.parseLong(CourseAdminController.str(d, "id")) : null;
        if (id != null) {
            NotFoundException.orFail(db.find("free_courses", id));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String title = AdminKit.trim(CourseAdminController.str(d, "title"), 255);
        if (AdminKit.blank(title)) {
            errors.put("title", "Give the lesson a title.");
        }
        String slug = CourseAdminController.str(d, "slug").trim().toLowerCase();
        if (!slug.isEmpty() && !slug.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$")) {
            errors.put("slug", "Use lowercase letters, numbers and single dashes only.");
        } else if (!slug.isEmpty() && db.count("select count(*) from free_courses where slug = ? and id <> ?", slug, id == null ? 0 : id) > 0) {
            errors.put("slug", "Another free lesson already uses this URL.");
        }
        List<Row> videos = new ArrayList<>();
        if (d.get("videos") instanceof List<?> vs) {
            int i = 0;
            for (Object o : vs) {
                i++;
                if (o instanceof Map<?, ?> m) {
                    String t = m.get("title") == null ? "" : String.valueOf(m.get("title")).trim();
                    String u = m.get("url") == null ? "" : String.valueOf(m.get("url")).trim();
                    if (t.isEmpty() && u.isEmpty()) {
                        continue;
                    }
                    if (!URL.matcher(u).matches()) {
                        errors.put("videos", "Video " + i + ": enter a full link starting with https://");
                    }
                    videos.add(Row.of("video_title", AdminKit.trim(t, 255), "video_url", AdminKit.trim(u, 1000)));
                }
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
        Row v = Row.of("title", title, "slug", slug.isEmpty() ? slugs.unique("free_courses", "slug", title, id) : slug,
                "description", HtmlCleaner.clean(CourseAdminController.str(d, "description")),
                "status", CourseAdminController.bool(d, "status") ? 1 : 0);
        if (id == null) {
            v.put("admin_id", admin.get("id"));
            id = db.insert("free_courses", v);
            audit.log(admin, "free_course", id, "created", title);
        } else {
            db.updateById("free_courses", id, v);
            audit.log(admin, "free_course", id, "updated", title);
        }
        if (photo != null) {
            db.updateById("free_courses", id, Row.of("photo", storage.storeUpload(photo, "uploads/free-courses/" + id)));
        }
        db.update("delete from free_course_videos where free_course_id = ?", id);
        for (Row vd : videos) {
            vd.put("free_course_id", id);
            vd.put("status", 1);
            db.insert("free_course_videos", vd);
        }
        return kit.ok(Map.of("item", view(db.find("free_courses", id)), "message", "Saved."));
    }

    @DeleteMapping("/admin/api/free-courses/{id}")
    public ResponseEntity<String> destroy(HttpServletRequest request, @PathVariable("id") long id) {
        if (!kit.can(kit.admin(request), "settings")) {
            return kit.forbidden();
        }
        Row f = NotFoundException.orFail(db.find("free_courses", id));
        db.update("delete from free_course_videos where free_course_id = ?", id);
        db.deleteById("free_courses", id);
        audit.log(kit.admin(request), "free_course", id, "deleted", f.str("title"));
        return kit.ok(Map.of("message", "Deleted “" + f.str("title") + "”."));
    }
}
