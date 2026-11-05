package com.artistikcity.web.admin;

import com.artistikcity.http.Flash;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.http.Redirects;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import com.artistikcity.support.Str;
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
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;
import java.util.Map;

/**
 * The simple Route::resource() CRUD screens: mediums, genres, skills, course types, testimonials,
 * blog categories and blog tags (Admin\MediumController, GenreController, SkillController,
 * CourseTypeController, TestimonialController, Blog\CategoryController, Blog\TagController).
 */
@Controller
public class AdminResourceController {

    /**
     * @param path      URL prefix after /admin/
     * @param table     database table
     * @param listVar   template variable of the list / @param itemVar of the edited row
     * @param slugCol   slug column filled from "name"
     * @param photoDir  upload folder for the photo (null = no photo)
     * @param extra     additional text columns taken from the form
     * @param label     name used in flash messages
     */
    record Resource(String path, String table, String view, String listVar, String itemVar, String slugCol,
                    String photoDir, List<String> extra, String label) {
    }

    private static final Map<String, Resource> RESOURCES = Map.of(
            "mediums", new Resource("mediums", "mediums", "admin/mediums", "mediums", "medium", "medium_slug", "uploads/mediums", List.of(), "medium"),
            "genres", new Resource("genres", "genres", "admin/genres", "genres", "genre", "genre_slug", null, List.of(), "genre"),
            "skills", new Resource("skills", "skills", "admin/skills", "skills", "skill", "skill_slug", null, List.of(), "skill"),
            "course_types", new Resource("course_types", "course_types", "admin/course_types", "course_types", "course_type", "course_type_slug", null, List.of(), "course type"),
            "testimonials", new Resource("testimonials", "testimonials", "admin/testimonials", "testimonials", "testimonial", "slug", "uploads/testimonials", List.of("title", "description"), "testimonial"),
            "blog/category", new Resource("blog/category", "categories", "admin/blog/category", "categories", "category", "slug", null, List.of(), "category"),
            "blog/tag", new Resource("blog/tag", "tags", "admin/blog/tag", "tags", "tag", "slug", null, List.of(), "tag"));

    private final Views views;
    private final Db db;
    private final Json json;
    private final Validator validator;
    private final Storage storage;
    private final Redirects redirect;

    public AdminResourceController(Views views, Db db, Json json, Validator validator, Storage storage, Redirects redirect) {
        this.views = views;
        this.db = db;
        this.json = json;
        this.validator = validator;
        this.storage = storage;
        this.redirect = redirect;
    }

    private static Resource resource(String name) {
        Resource r = RESOURCES.get(name);
        if (r == null) {
            throw new NotFoundException();
        }
        return r;
    }

    // ------------------------------------------------------ /admin/{resource}

    @GetMapping("/admin/{resource}")
    public ResponseEntity<String> index(HttpServletRequest request, @PathVariable("resource") String name) {
        return doIndex(request, resource(name));
    }

    @GetMapping("/admin/{resource}/create")
    public ResponseEntity<String> create(HttpServletRequest request, @PathVariable("resource") String name) {
        return views.page(request, resource(name).view() + "/create", Map.of());
    }

    @PostMapping("/admin/{resource}")
    public ResponseEntity<String> store(HttpServletRequest request, @PathVariable("resource") String name) {
        return doStore(request, resource(name));
    }

    @GetMapping("/admin/{resource}/{id}")
    public ResponseEntity<String> show(HttpServletRequest request, @PathVariable("resource") String name, @PathVariable("id") Long id) {
        return redirect.to(request, "/admin/" + resource(name).path() + "/" + id + "/edit");
    }

    @GetMapping("/admin/{resource}/{id}/edit")
    public ResponseEntity<String> edit(HttpServletRequest request, @PathVariable("resource") String name, @PathVariable("id") Long id) {
        return doEdit(request, resource(name), id);
    }

    @PutMapping("/admin/{resource}/{id}")
    public ResponseEntity<String> put(HttpServletRequest request, @PathVariable("resource") String name, @PathVariable("id") Long id) {
        return doUpdate(request, resource(name), id);
    }

    @PatchMapping("/admin/{resource}/{id}")
    public ResponseEntity<String> patch(HttpServletRequest request, @PathVariable("resource") String name, @PathVariable("id") Long id) {
        return doUpdate(request, resource(name), id);
    }

    @DeleteMapping("/admin/{resource}/{id}")
    public ResponseEntity<String> destroy(HttpServletRequest request, @PathVariable("resource") String name, @PathVariable("id") Long id) {
        return doDestroy(request, resource(name), id);
    }

    // ------------------------------------------------- /admin/blog/{resource}

    @GetMapping("/admin/blog/{resource}")
    public ResponseEntity<String> blogIndex(HttpServletRequest request, @PathVariable("resource") String name) {
        return doIndex(request, resource("blog/" + name));
    }

    @GetMapping("/admin/blog/{resource}/create")
    public ResponseEntity<String> blogCreate(HttpServletRequest request, @PathVariable("resource") String name) {
        return views.page(request, resource("blog/" + name).view() + "/create", Map.of());
    }

    @PostMapping("/admin/blog/{resource}")
    public ResponseEntity<String> blogStore(HttpServletRequest request, @PathVariable("resource") String name) {
        return doStore(request, resource("blog/" + name));
    }

    @GetMapping("/admin/blog/{resource}/{id}/edit")
    public ResponseEntity<String> blogEdit(HttpServletRequest request, @PathVariable("resource") String name, @PathVariable("id") Long id) {
        return doEdit(request, resource("blog/" + name), id);
    }

    @PutMapping("/admin/blog/{resource}/{id}")
    public ResponseEntity<String> blogPut(HttpServletRequest request, @PathVariable("resource") String name, @PathVariable("id") Long id) {
        return doUpdate(request, resource("blog/" + name), id);
    }

    @PatchMapping("/admin/blog/{resource}/{id}")
    public ResponseEntity<String> blogPatch(HttpServletRequest request, @PathVariable("resource") String name, @PathVariable("id") Long id) {
        return doUpdate(request, resource("blog/" + name), id);
    }

    @DeleteMapping("/admin/blog/{resource}/{id}")
    public ResponseEntity<String> blogDestroy(HttpServletRequest request, @PathVariable("resource") String name, @PathVariable("id") Long id) {
        return doDestroy(request, resource("blog/" + name), id);
    }

    // ---------------------------------------------------------- implementation

    private ResponseEntity<String> doIndex(HttpServletRequest request, Resource r) {
        return views.page(request, r.view() + "/index", Map.of(r.listVar(), db.select("select * from " + r.table() + " order by id")));
    }

    private ResponseEntity<String> doEdit(HttpServletRequest request, Resource r, Long id) {
        Row row = NotFoundException.orFail(db.find(r.table(), id));
        return views.page(request, r.view() + "/edit", Map.of(r.itemVar(), row));
    }

    private Row values(Input in, Resource r) {
        Row v = Row.of("name", in.get("name"), r.slugCol(), Str.slug(in.get("name")));
        for (String col : r.extra()) {
            v.put(col, in.get(col));
        }
        return v;
    }

    private void savePhoto(Input in, Resource r, long id) {
        if (r.photoDir() == null) {
            return;
        }
        UploadedFile f = in.hasFile("photo") ? in.file("photo") : in.file("photos");
        if (f != null) {
            String name = storage.storeUpload(f, r.photoDir() + "/" + id);
            db.updateById(r.table(), id, Row.of("photo", name));
        }
    }

    private ResponseEntity<String> doStore(HttpServletRequest request, Resource r) {
        Input in = Input.of(request, json);
        validator.validate(in, "name", "required");
        Row v = values(in, r);
        v.put("status", 1);
        long id = db.insert(r.table(), v);
        savePhoto(in, r, id);
        if ("testimonials".equals(r.path())) {
            return redirect.backWith(request, "success", "Testimonial added successfully!");
        }
        return redirect.routeWith(request, "success", "The " + r.label() + " has been added!", "admin." + r.path().replace('/', '.') + ".index");
    }

    private ResponseEntity<String> doUpdate(HttpServletRequest request, Resource r, Long id) {
        Input in = Input.of(request, json);
        validator.validate(in, "name", "required");
        NotFoundException.orFail(db.find(r.table(), id));
        db.updateById(r.table(), id, values(in, r));
        savePhoto(in, r, id);
        if ("testimonials".equals(r.path())) {
            return redirect.backWith(request, "success", "Testimonial updated successfully!");
        }
        return redirect.routeWith(request, "success", "The " + r.label() + " has been updated!", "admin." + r.path().replace('/', '.') + ".index");
    }

    private ResponseEntity<String> doDestroy(HttpServletRequest request, Resource r, Long id) {
        try {
            if ("categories".equals(r.table())) {
                db.update("delete from category_post where category_id = ?", id);
            }
            if ("tags".equals(r.table())) {
                db.update("delete from post_tag where tag_id = ?", id);
            }
            db.deleteById(r.table(), id);
            Flash.put(request, "success", Str.ucfirst(r.label()) + " successfully deleted!");
        } catch (Db.DbException e) {
            Flash.put(request, "error", "This " + r.label() + " is in use and cannot be deleted.");
        }
        return redirect.route(request, "admin." + r.path().replace('/', '.') + ".index");
    }
}
