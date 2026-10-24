package com.artistikcity.admin;

import com.artistikcity.http.Redirects;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import com.artistikcity.support.Str;
import com.artistikcity.support.UploadedFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.YearMonth;
import java.util.Map;

/**
 * Small console helpers (slug availability, images inserted into rich text) and the redirects that keep the
 * old admin addresses working after the move to the console.
 */
@Controller
public class AdminUtilController {

    /** table -> [slug column, label] for the slug check; nothing else can be queried. */
    private static final Map<String, String[]> SLUGGED = Map.of(
            "courses", new String[]{"slug", "course"},
            "posts", new String[]{"slug", "post"},
            "free_courses", new String[]{"slug", "free lesson"},
            "admins", new String[]{"slug", "profile"});

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final Storage storage;
    private final Redirects redirect;

    public AdminUtilController(AdminKit kit, Db db, Json json, Storage storage, Redirects redirect) {
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.storage = storage;
        this.redirect = redirect;
    }

    /** {available, suggestion} for a slug in one of the slugged tables. */
    @GetMapping("/admin/api/slug")
    public ResponseEntity<String> slug(HttpServletRequest request) {
        String[] t = SLUGGED.get(String.valueOf(request.getParameter("table")));
        String slug = request.getParameter("slug") == null ? "" : request.getParameter("slug").trim().toLowerCase();
        if (t == null || slug.isEmpty()) {
            return kit.ok(Map.of("available", true));
        }
        long except = 0;
        try {
            except = Long.parseLong(String.valueOf(request.getParameter("except")));
        } catch (NumberFormatException ignored) {
            // new record
        }
        String table = String.valueOf(request.getParameter("table"));
        boolean taken = db.count("select count(*) from " + table + " where " + t[0] + " = ? and id <> ?", slug, except) > 0;
        if (!taken) {
            return kit.ok(Map.of("available", true));
        }
        String base = Str.slug(slug);
        for (int i = 2; i < 200; i++) {
            String s = base + "-" + i;
            if (db.count("select count(*) from " + table + " where " + t[0] + " = ? and id <> ?", s, except) == 0) {
                return kit.ok(Map.of("available", false, "suggestion", s));
            }
        }
        return kit.ok(Map.of("available", false));
    }

    /** Image uploaded from a rich text editor; answers {url}. */
    @PostMapping("/admin/api/media")
    public ResponseEntity<String> media(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "catalog") && !kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        Input in = Input.of(request, json);
        UploadedFile f = in.file("file");
        if (f == null) {
            return kit.invalid(Map.of("file", "Choose an image."));
        }
        String error = AdminKit.checkImage(f);
        if (error != null) {
            return kit.invalid(Map.of("file", error));
        }
        String dir = "uploads/editor/" + YearMonth.now();
        String name = storage.storeUpload(f, dir);
        return kit.ok(Map.of("url", "/storage/" + dir + "/" + name));
    }

    // ------------------------------------------------- old admin addresses

    @GetMapping({"/admin/mediums", "/admin/genres", "/admin/skills", "/admin/course_types",
            "/admin/mediums/create", "/admin/genres/create", "/admin/skills/create", "/admin/course_types/create"})
    public ResponseEntity<String> taxonomies(HttpServletRequest request) {
        String type = request.getRequestURI().replace("/admin/", "").replace("/create", "");
        return redirect.to(request, "/admin/catalog?type=" + type);
    }

    @GetMapping({"/admin/{type:mediums|genres|skills|course_types}/{id}/edit", "/admin/{type:mediums|genres|skills|course_types}/{id}"})
    public ResponseEntity<String> taxonomyEdit(HttpServletRequest request, @PathVariable("type") String type) {
        return redirect.to(request, "/admin/catalog?type=" + type);
    }

    @GetMapping({"/admin/blog/category", "/admin/blog/tag", "/admin/blog/category/create", "/admin/blog/tag/create",
            "/admin/blog/category/{id}/edit", "/admin/blog/tag/{id}/edit"})
    public ResponseEntity<String> blogTaxonomies(HttpServletRequest request) {
        return redirect.to(request, "/admin/blog/taxonomy?type=" + (request.getRequestURI().contains("/tag") ? "tags" : "categories"));
    }

    @GetMapping({"/admin/testimonials", "/admin/testimonials/create", "/admin/testimonials/{id}/edit"})
    public ResponseEntity<String> testimonials(HttpServletRequest request) {
        return redirect.to(request, "/admin/dashboard/testimonials");
    }

    @GetMapping({"/admin/home/artworks", "/admin/home/create-artwork", "/admin/home/edit-artwork/{id}",
            "/admin/students/artworks", "/admin/students/create-artwork", "/admin/students/edit-artwork/{id}"})
    public ResponseEntity<String> gallery(HttpServletRequest request) {
        return redirect.to(request, "/admin/dashboard/gallery");
    }

    @GetMapping({"/admin/students", "/admin/students/create", "/admin/students/edit/{id}", "/admin/students/details/{id}"})
    public ResponseEntity<String> students(HttpServletRequest request) {
        return redirect.to(request, "/admin/dashboard/people");
    }

    @GetMapping({"/admin/teachers", "/admin/teacher/create"})
    public ResponseEntity<String> teachers(HttpServletRequest request) {
        return redirect.to(request, "/admin/instructors");
    }

    @GetMapping("/admin/teacher/edit/{id}")
    public ResponseEntity<String> teacher(HttpServletRequest request, @PathVariable("id") long id) {
        return redirect.to(request, "/admin/instructors?edit=" + id);
    }

    @GetMapping("/admin/dashboard/classic")
    public ResponseEntity<String> classicDashboard(HttpServletRequest request) {
        return redirect.to(request, "/admin/dashboard");
    }
}
