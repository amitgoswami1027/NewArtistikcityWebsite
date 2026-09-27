package com.artistikcity.web.admin;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Flash;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.http.Redirects;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Slugs;
import com.artistikcity.support.Storage;
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

import java.util.List;
import java.util.Map;

/** Free courses with their videos (Admin\FreeCourseController). */
@Controller
public class AdminFreeCourseController {

    private final Views views;
    private final Db db;
    private final Json json;
    private final Auth auth;
    private final Validator validator;
    private final Storage storage;
    private final Slugs slugs;
    private final Redirects redirect;

    public AdminFreeCourseController(Views views, Db db, Json json, Auth auth, Validator validator, Storage storage, Slugs slugs, Redirects redirect) {
        this.views = views;
        this.db = db;
        this.json = json;
        this.auth = auth;
        this.validator = validator;
        this.storage = storage;
        this.slugs = slugs;
        this.redirect = redirect;
    }

    @GetMapping("/admin/free-courses")
    public ResponseEntity<String> index(HttpServletRequest request) {
        return views.page(request, "admin/free_courses/index", Map.of("freeCourses", db.select("select * from free_courses order by id")));
    }

    @GetMapping("/admin/free-courses/create")
    public ResponseEntity<String> create(HttpServletRequest request) {
        return views.page(request, "admin/free_courses/create", Map.of());
    }

    @GetMapping("/admin/free-courses/{course_id}")
    public ResponseEntity<String> show(HttpServletRequest request, @PathVariable("course_id") Long id) {
        return redirect.route(request, "admin.free.courses.edit", id);
    }

    @PostMapping("/admin/free-courses/store")
    public ResponseEntity<String> store(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "title", "required");
        long id = db.insert("free_courses", Row.of("admin_id", auth.adminId(request), "title", in.get("title"),
                "slug", slugs.unique("free_courses", "slug", in.get("title"), null), "description", in.get("description"),
                "status", in.lng("status") == null ? 1 : in.lng("status")));
        saveRelated(in, id, false);
        return redirect.routeWith(request, "success", "The free courses has been added!", "admin.free.courses");
    }

    @GetMapping("/admin/free-courses/edit/{course_id}")
    public ResponseEntity<String> edit(HttpServletRequest request, @PathVariable("course_id") Long id) {
        Row c = NotFoundException.orFail(db.find("free_courses", id));
        List<Row> videos = db.select("select * from free_course_videos where free_course_id = ? order by id", id);
        c.put("free_course_videos", videos);
        c.put("video_count", videos.size());
        c.put("video_last_index", videos.size()); // rows are numbered from 1 in the template
        return views.page(request, "admin/free_courses/edit", Map.of("freeCourse", c));
    }

    @PatchMapping("/admin/free-courses/edit/{course_id}")
    public ResponseEntity<String> update(HttpServletRequest request, @PathVariable("course_id") Long id) {
        Input in = Input.of(request, json);
        validator.validate(in, "title", "required");
        NotFoundException.orFail(db.find("free_courses", id));
        db.updateById("free_courses", id, Row.of("admin_id", auth.adminId(request), "title", in.get("title"),
                "description", in.get("description"), "status", in.lng("status")));
        saveRelated(in, id, true);
        return redirect.routeWith(request, "success", "The free courses has been updated!", "admin.free.courses");
    }

    private void saveRelated(Input in, long id, boolean replaceVideos) {
        if (in.hasFile("photo")) {
            String name = storage.storeUpload(in.file("photo"), "uploads/free-courses/" + id);
            db.updateById("free_courses", id, Row.of("photo", name));
        }
        List<Map<String, Object>> videos = in.list("video");
        if (!videos.isEmpty()) {
            if (replaceVideos) {
                db.update("delete from free_course_videos where free_course_id = ?", id);
            }
            for (Map<String, Object> v : videos) {
                if (v.get("url") != null || v.get("title") != null) {
                    db.insert("free_course_videos", Row.of("free_course_id", id, "video_title", v.get("title"), "video_url", v.get("url"), "status", 1));
                }
            }
        }
    }

    @DeleteMapping("/admin/free-courses/delete/{course_id}")
    public ResponseEntity<String> destroy(HttpServletRequest request, @PathVariable("course_id") Long id) {
        db.update("delete from free_course_videos where free_course_id = ?", id);
        db.deleteById("free_courses", id);
        Flash.put(request, "success", "Free Course successfully deleted!");
        return redirect.back(request);
    }
}
