package com.artistikcity.admin;

import com.artistikcity.http.NotFoundException;
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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Blog CMS: posts list (/admin/blog/post) and editor (rich text with preview, slug, draft / published,
 * featured, categories, tags, cover image, SEO fields). Public pages only show published posts.
 */
@Controller
public class BlogAdminController {

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final Storage storage;
    private final Slugs slugs;
    private final Audit audit;

    public BlogAdminController(AdminKit kit, Db db, Json json, Storage storage, Slugs slugs, Audit audit) {
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.storage = storage;
        this.slugs = slugs;
        this.audit = audit;
    }

    @GetMapping({"/admin/blog/post", "/admin/blog/post/", "/admin/blog"})
    public ResponseEntity<String> index(HttpServletRequest request) {
        return kit.page(request, "settings", "Admin/Blog/Index", () -> {
            Map<String, Object> p = new LinkedHashMap<>();
            Supplier<Object> grid = () -> Grid.from(db, request)
                    .columns("p.id, p.title, p.subtitle, p.slug, p.status, p.featured, p.image, p.created_at, p.updated_at, a.name as author,"
                            + " (select group_concat(c.name order by c.name separator ', ') from category_post cp join categories c on c.id = cp.category_id where cp.post_id = p.id) as categories")
                    .from("posts p left join admins a on a.id = p.posted_by")
                    .search("p.title", "p.subtitle", "p.slug")
                    .sort("title", "p.title").sort("date", "p.created_at").sort("updated", "p.updated_at")
                    .defaultSort("updated", "desc").tieBreak("p.id desc")
                    .filter("status", v -> "published".equals(v) ? Grid.where("p.status = 1") : "draft".equals(v) ? Grid.where("coalesce(p.status, 0) = 0") : null)
                    .filter("featured", v -> "1".equals(v) ? Grid.where("p.featured = 1") : null)
                    .filter("category", v -> v.matches("\\d+") ? Grid.where("exists (select 1 from category_post cp where cp.post_id = p.id and cp.category_id = ?)", Long.parseLong(v)) : null)
                    .map(r -> {
                        r.put("image_url", r.str("image", "").isBlank() ? null : "/storage/uploads/blog/" + r.str("image"));
                        return r;
                    })
                    .page();
            p.put("grid", grid);
            Supplier<Object> counts = () -> Map.of(
                    "all", db.count("select count(*) from posts"),
                    "published", db.count("select count(*) from posts where status = 1"),
                    "draft", db.count("select count(*) from posts where coalesce(status, 0) = 0"));
            p.put("counts", counts);
            p.put("categories", db.select("select id as value, name as label from categories order by name"));
            return p;
        });
    }

    @GetMapping("/admin/blog/post/create")
    public ResponseEntity<String> create(HttpServletRequest request) {
        return kit.page(request, "settings", "Admin/Blog/Editor", () -> editor(null));
    }

    @GetMapping({"/admin/blog/post/{id}/edit", "/admin/blog/post/{id:\\d+}"})
    public ResponseEntity<String> edit(HttpServletRequest request, @PathVariable("id") long id) {
        Row post = NotFoundException.orFail(db.find("posts", id));
        return kit.page(request, "settings", "Admin/Blog/Editor", () -> editor(post));
    }

    private Map<String, Object> editor(Row post) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("post", post == null ? null : view(post));
        p.put("categories", db.select("select id as value, name as label from categories order by name"));
        p.put("tags", db.select("select name from tags order by name").stream().map(r -> r.str("name")).toList());
        return p;
    }

    private Map<String, Object> view(Row post) {
        long id = post.lng("id");
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", id);
        for (String k : List.of("title", "subtitle", "slug", "body", "meta_title", "meta_description")) {
            v.put(k, post.str(k, ""));
        }
        v.put("status", post.lng("status", 0) == 1);
        v.put("featured", post.lng("featured", 0) == 1);
        v.put("image", post.str("image", "").isBlank() ? null : Map.of("name", post.str("image"), "url", "/storage/uploads/blog/" + post.str("image")));
        v.put("category_ids", db.select("select category_id from category_post where post_id = ? order by category_id", id).stream()
                .map(r -> String.valueOf(r.get("category_id"))).toList());
        v.put("tags", db.select("select t.name from post_tag pt join tags t on t.id = pt.tag_id where pt.post_id = ? order by t.name", id).stream()
                .map(r -> r.str("name")).toList());
        v.put("created_at", post.get("created_at"));
        v.put("updated_at", post.get("updated_at"));
        v.put("public_url", "/blog/post/" + post.str("slug"));
        return v;
    }

    @PostMapping("/admin/api/posts")
    public ResponseEntity<String> store(HttpServletRequest request) {
        return save(request, null);
    }

    @PostMapping("/admin/api/posts/{id}")
    public ResponseEntity<String> update(HttpServletRequest request, @PathVariable("id") long id) {
        NotFoundException.orFail(db.find("posts", id));
        return save(request, id);
    }

    private ResponseEntity<String> save(HttpServletRequest request, Long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        Input in = Input.of(request, json);
        Map<String, Object> d = json.decodeMap(in.get("data"));
        if (d == null) {
            return kit.fail(HttpStatus.BAD_REQUEST, "The form data is missing.");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String title = AdminKit.trim(CourseAdminController.str(d, "title"), 256);
        if (AdminKit.blank(title)) {
            errors.put("title", "Give the post a title.");
        }
        String subtitle = AdminKit.trim(CourseAdminController.str(d, "subtitle"), 500);
        boolean publish = CourseAdminController.bool(d, "status");
        if (publish && AdminKit.blank(subtitle)) {
            errors.put("subtitle", "Add a short summary; it shows in the blog list.");
        }
        String body = HtmlCleaner.clean(CourseAdminController.str(d, "body"));
        if (publish && HtmlCleaner.text(body, 20).isBlank()) {
            errors.put("body", "Write the post before publishing it.");
        }
        String slug = CourseAdminController.str(d, "slug").trim().toLowerCase();
        if (!slug.isEmpty() && (!slug.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$") || slug.length() > 100)) {
            errors.put("slug", "Use lowercase letters, numbers and single dashes (up to 100 characters).");
        } else if (!slug.isEmpty() && db.count("select count(*) from posts where slug = ? and id <> ?", slug, id == null ? 0 : id) > 0) {
            errors.put("slug", "Another post already uses this URL.");
        }
        String metaTitle = CourseAdminController.str(d, "meta_title").trim();
        if (metaTitle.length() > 255) {
            errors.put("meta_title", "Keep it under 255 characters.");
        }
        String metaDescription = CourseAdminController.str(d, "meta_description").trim();
        if (metaDescription.length() > 500) {
            errors.put("meta_description", "Keep it under 500 characters.");
        }
        UploadedFile image = in.hasFile("image") ? in.file("image") : null;
        String imageError = AdminKit.checkImage(image);
        if (imageError != null) {
            errors.put("image", imageError);
        }
        if (!errors.isEmpty()) {
            return kit.invalid(errors);
        }
        if (slug.isEmpty()) {
            slug = slugs.unique("posts", "slug", title, id);
            if (slug.length() > 100) {
                slug = slug.substring(0, 100).replaceAll("-+$", "");
            }
        }
        Row v = Row.of("title", title, "subtitle", subtitle == null ? "" : subtitle, "slug", slug, "body", body == null ? "" : body,
                "status", publish ? 1 : 0, "featured", CourseAdminController.bool(d, "featured") ? 1 : 0,
                "meta_title", metaTitle.isEmpty() ? null : metaTitle, "meta_description", metaDescription.isEmpty() ? null : metaDescription);
        boolean wasPublished = id != null && db.find("posts", id).lng("status", 0) == 1;
        if (id == null) {
            v.put("posted_by", admin.get("id"));
            v.put("like", 0);
            v.put("dislike", 0);
            id = db.insert("posts", v);
            audit.log(admin, "post", id, "created", title);
        } else {
            if (publish && !wasPublished) {
                v.put("created_at", LocalDateTime.now()); // the public date is the publish date
            }
            db.updateById("posts", id, v);
            audit.log(admin, "post", id, publish ? "saved (published)" : "saved (draft)", title);
        }
        if (image != null) {
            db.updateById("posts", id, Row.of("image", storage.storeUpload(image, "uploads/blog")));
        } else if (CourseAdminController.bool(d, "remove_image")) {
            db.updateById("posts", id, Row.of("image", null));
        }
        // categories (ids that exist)
        db.update("delete from category_post where post_id = ?", id);
        if (d.get("category_ids") instanceof List<?> cats) {
            for (Object c : new LinkedHashSet<>(cats)) {
                String cid = String.valueOf(c);
                if (cid.matches("\\d+") && db.find("categories", Long.parseLong(cid)) != null) {
                    db.insertPlain("category_post", Row.of("category_id", Long.parseLong(cid), "post_id", id, "created_at", LocalDateTime.now(), "updated_at", LocalDateTime.now()));
                }
            }
        }
        // tags by name; new names become tags
        db.update("delete from post_tag where post_id = ?", id);
        if (d.get("tags") instanceof List<?> tags) {
            Set<String> seen = new LinkedHashSet<>();
            for (Object t : tags) {
                String name = AdminKit.trim(String.valueOf(t).replaceAll("\\s+", " "), 60);
                if (name == null || name.isBlank() || !seen.add(name.toLowerCase())) {
                    continue;
                }
                Row tag = db.first("select id from tags where lower(name) = ?", name.toLowerCase());
                long tagId = tag != null ? tag.lng("id") : db.insert("tags", Row.of("name", name, "slug", slugs.unique("tags", "slug", name, null), "status", 1));
                db.insertPlain("post_tag", Row.of("post_id", id, "tag_id", tagId, "created_at", LocalDateTime.now(), "updated_at", LocalDateTime.now()));
            }
        }
        Row saved = db.find("posts", id);
        return kit.ok(Map.of("id", id, "post", view(saved), "editUrl", "/admin/blog/post/" + id + "/edit",
                "message", publish ? (wasPublished ? "Saved. The live post is updated." : "Published.") : "Draft saved."));
    }

    @PostMapping("/admin/api/posts/{id}/status")
    public ResponseEntity<String> status(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        Row post = NotFoundException.orFail(db.find("posts", id));
        boolean publish = Input.of(request, json).bool("status");
        if (publish && (post.str("subtitle", "").isBlank() || HtmlCleaner.text(post.str("body"), 20).isBlank())) {
            return kit.fail(HttpStatus.CONFLICT, "Add a summary and the post text before publishing.");
        }
        Row v = Row.of("status", publish ? 1 : 0);
        if (publish && post.lng("status", 0) != 1) {
            v.put("created_at", LocalDateTime.now());
        }
        db.updateById("posts", id, v);
        audit.log(admin, "post", id, publish ? "published" : "unpublished", post.str("title"));
        return kit.ok(Map.of("message", publish ? "Published." : "Moved back to drafts."));
    }

    @DeleteMapping("/admin/api/posts/{id}")
    public ResponseEntity<String> destroy(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "settings")) {
            return kit.forbidden();
        }
        Row post = NotFoundException.orFail(db.find("posts", id));
        db.update("delete from post_tag where post_id = ?", id);
        db.update("delete from category_post where post_id = ?", id);
        db.deleteById("posts", id);
        audit.log(admin, "post", id, "deleted", post.str("title"));
        return kit.ok(Map.of("message", "Deleted “" + post.str("title") + "”."));
    }
}
