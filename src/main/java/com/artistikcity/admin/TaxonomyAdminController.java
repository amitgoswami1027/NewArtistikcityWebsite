package com.artistikcity.admin;

import com.artistikcity.http.NotFoundException;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import com.artistikcity.support.Str;
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

/**
 * Lookup lists: course mediums, genres, skill levels and course types (/admin/catalog) and blog
 * categories and tags (/admin/blog/taxonomy). Each shows where it is used; items in use by courses can't be deleted.
 */
@Controller
public class TaxonomyAdminController {

    /**
     * @param usage   SQL counting the rows that use an item (one ? = item id)
     * @param detach  statements run before deleting an item that is in use (null = deleting is blocked while in use)
     * @param locked  ids the site depends on (never deleted)
     */
    record Type(String key, String group, String table, String slugCol, String label, String plural, String photoDir, String usage,
                List<String> detach, List<Long> locked, String hint) {
    }

    private static final Map<String, Type> TYPES = new LinkedHashMap<>();

    static {
        add(new Type("mediums", "catalog", "mediums", "medium_slug", "medium", "Mediums", "uploads/mediums",
                "select count(*) from courses where medium_id = ?", null, List.of(), "Watercolour, acrylic, pencil… Used to filter courses."));
        add(new Type("genres", "catalog", "genres", "genre_slug", "genre", "Genres", null,
                "select count(*) from courses where find_in_set(?, genre_id) > 0", null, List.of(), "Landscape, portrait, abstract… A course can have several."));
        add(new Type("skills", "catalog", "skills", "skill_slug", "skill level", "Skill levels", null,
                "select count(*) from courses where skill_id = ?", null, List.of(), "Beginner, intermediate, advanced."));
        add(new Type("course_types", "catalog", "course_types", "course_type_slug", "course type", "Course types", null,
                "select count(*) from courses where course_type_id = ?", null, List.of(1L, 2L), "Course and Workshop drive the site's menus and can't be removed."));
        add(new Type("categories", "blog", "categories", "slug", "category", "Categories", null,
                "select count(*) from category_post where category_id = ?", List.of("delete from category_post where category_id = ?"), List.of(), "Main topics of the blog. A post can be in several."));
        add(new Type("tags", "blog", "tags", "slug", "tag", "Tags", null,
                "select count(*) from post_tag where tag_id = ?", List.of("delete from post_tag where tag_id = ?"), List.of(), "Finer keywords; you can also create tags while writing a post."));
    }

    private static void add(Type t) {
        TYPES.put(t.key(), t);
    }

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final Storage storage;
    private final Audit audit;

    public TaxonomyAdminController(AdminKit kit, Db db, Json json, Storage storage, Audit audit) {
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.storage = storage;
        this.audit = audit;
    }

    private static Type type(String key) {
        Type t = TYPES.get(key);
        if (t == null) {
            throw new NotFoundException();
        }
        return t;
    }

    @GetMapping("/admin/catalog")
    public ResponseEntity<String> catalog(HttpServletRequest request) {
        return page(request, "catalog", "mediums");
    }

    @GetMapping("/admin/blog/taxonomy")
    public ResponseEntity<String> blog(HttpServletRequest request) {
        return page(request, "blog", "categories");
    }

    private ResponseEntity<String> page(HttpServletRequest request, String group, String fallback) {
        return kit.page(request, "settings", "Admin/Taxonomies", () -> {
            String asked = request.getParameter("type");
            Type current = asked != null && TYPES.containsKey(asked) && TYPES.get(asked).group().equals(group) ? TYPES.get(asked) : TYPES.get(fallback);
            List<Map<String, Object>> tabs = new ArrayList<>();
            for (Type t : TYPES.values()) {
                if (t.group().equals(group)) {
                    tabs.add(Map.of("key", t.key(), "label", t.plural(), "count", db.count("select count(*) from " + t.table())));
                }
            }
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("group", group);
            p.put("tabs", tabs);
            p.put("type", meta(current));
            p.put("items", items(current));
            return p;
        });
    }

    private static Map<String, Object> meta(Type t) {
        return Map.of("key", t.key(), "label", t.label(), "plural", t.plural(), "hasPhoto", t.photoDir() != null, "hint", t.hint(),
                "detachable", t.detach() != null, "locked", t.locked());
    }

    private List<Row> items(Type t) {
        List<Row> out = new ArrayList<>();
        for (Row r : db.select("select * from " + t.table() + " order by name")) {
            Row v = Row.of("id", r.get("id"), "name", r.str("name", ""), "slug", r.str(t.slugCol(), ""), "status", r.lng("status", 1) == 1,
                    "usage", db.count(t.usage(), "genres".equals(t.key()) ? String.valueOf(r.get("id")) : r.get("id")),
                    "updated_at", r.get("updated_at"));
            if (t.photoDir() != null) {
                v.put("photo_url", r.str("photo", "").isBlank() ? null : "/storage/" + t.photoDir() + "/" + r.get("id") + "/" + r.str("photo"));
            }
            out.add(v);
        }
        return out;
    }

    @PostMapping("/admin/api/taxonomy/{type}")
    public ResponseEntity<String> save(HttpServletRequest request, @PathVariable("type") String key) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        Type t = type(key);
        Input in = Input.of(request, json);
        Long id = in.lng("id");
        if (id != null) {
            NotFoundException.orFail(db.find(t.table(), id));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String name = AdminKit.trim(in.get("name"), 100);
        if (AdminKit.blank(name)) {
            errors.put("name", "Enter a name.");
        } else if (db.count("select count(*) from " + t.table() + " where lower(name) = ? and id <> ?", name.toLowerCase(), id == null ? 0 : id) > 0) {
            errors.put("name", "There is already a " + t.label() + " with this name.");
        }
        String slug = in.get("slug") == null ? "" : in.get("slug").trim().toLowerCase();
        if (slug.isEmpty() && name != null) {
            slug = Str.slug(name);
        }
        if (!slug.isEmpty() && !slug.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$")) {
            errors.put("slug", "Use lowercase letters, numbers and single dashes only.");
        } else if (!slug.isEmpty() && db.count("select count(*) from " + t.table() + " where " + t.slugCol() + " = ? and id <> ?", slug, id == null ? 0 : id) > 0) {
            errors.put("slug", "Another " + t.label() + " already uses this URL name.");
        }
        UploadedFile photo = t.photoDir() != null && in.hasFile("photo") ? in.file("photo") : null;
        String photoError = AdminKit.checkImage(photo);
        if (photoError != null) {
            errors.put("photo", photoError);
        }
        if (!errors.isEmpty()) {
            return kit.invalid(errors);
        }
        Row v = Row.of("name", name, t.slugCol(), slug, "status", in.has("status") && !in.bool("status") ? 0 : 1);
        if (id == null) {
            id = db.insert(t.table(), v);
            audit.log(admin, t.key(), id, "created", name);
        } else {
            db.updateById(t.table(), id, v);
            audit.log(admin, t.key(), id, "updated", name);
        }
        if (photo != null) {
            db.updateById(t.table(), id, Row.of("photo", storage.storeUpload(photo, t.photoDir() + "/" + id)));
        }
        return kit.ok(Map.of("items", items(t), "message", in.lng("id") == null ? "Added “" + name + "”." : "Saved."));
    }

    @DeleteMapping("/admin/api/taxonomy/{type}/{id}")
    public ResponseEntity<String> destroy(HttpServletRequest request, @PathVariable("type") String key, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        Type t = type(key);
        Row row = NotFoundException.orFail(db.find(t.table(), id));
        if (t.locked().contains(id)) {
            return kit.fail(HttpStatus.CONFLICT, "“" + row.str("name") + "” is used by the site itself and can't be deleted. You can rename it.");
        }
        long used = db.count(t.usage(), "genres".equals(t.key()) ? String.valueOf(id) : id);
        if (used > 0 && t.detach() == null) {
            return kit.fail(HttpStatus.CONFLICT, used + (used == 1 ? " course uses" : " courses use") + " “" + row.str("name") + "”. Change "
                    + (used == 1 ? "that course" : "those courses") + " first, or switch it off instead.");
        }
        if (t.detach() != null) {
            for (String sql : t.detach()) {
                db.update(sql, id);
            }
        }
        db.deleteById(t.table(), id);
        audit.log(admin, t.key(), id, "deleted", row.str("name"));
        return kit.ok(Map.of("items", items(t), "message", "Deleted “" + row.str("name") + "”."));
    }
}
