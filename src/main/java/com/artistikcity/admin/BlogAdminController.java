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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Studio Stories CMS (Admin > Content > Studio Stories, {@code /admin/content/stories}): a dashboard (views over 30 days,
 * week-on-week velocity, read time, top stories) over a searchable table with quick Draft / Publish / Archive actions,
 * and an editor (rich-text sheet, author, tags, categories, clean URL, workflow: Draft > In review > Scheduled >
 * Published > Archived). Public pages only show published stories ({@code posts.status = 1}).
 *
 * <p>Status codes: 0 draft, 1 published, 2 archived, 3 in review, 4 scheduled ({@code publish_at}, published by
 * {@link #publishDue()} within a minute). JSON API: {@code GET/POST /admin/api/content/stories},
 * {@code PUT /admin/api/content/stories/{id}}, {@code POST /admin/api/content/stories/{id}/workflow}.
 * The older {@code /admin/api/posts} endpoints stay for existing clients.
 */
@Controller
public class BlogAdminController {

    static final String CAP = "settings";
    static final Map<String, Integer> WORKFLOW = Map.of("draft", 0, "published", 1, "archived", 2, "review", 3, "scheduled", 4);
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

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

    public static String workflowName(long status) {
        return switch ((int) status) {
            case 1 -> "published";
            case 2 -> "archived";
            case 3 -> "review";
            case 4 -> "scheduled";
            default -> "draft";
        };
    }

    // ================================================================ pages

    @GetMapping({"/admin/content/stories", "/admin/content/stories/"})
    public ResponseEntity<String> index(HttpServletRequest request) {
        return kit.page(request, CAP, "Admin/Content/StudioStories", () -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("mode", "list");
            p.put("grid", (Supplier<Object>) () -> grid(request));
            p.put("analytics", (Supplier<Object>) this::analytics);
            p.put("counts", (Supplier<Object>) this::counts);
            p.put("categories", db.select("select id as value, name as label from categories order by name"));
            p.put("authors", authors());
            return p;
        });
    }

    /** Old addresses of the list. */
    @GetMapping({"/admin/blog/post", "/admin/blog/post/", "/admin/blog"})
    public ResponseEntity<String> legacyIndex(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", "/admin/content/stories").build();
    }

    @GetMapping({"/admin/content/stories/new", "/admin/blog/post/create"})
    public ResponseEntity<String> create(HttpServletRequest request) {
        return kit.page(request, CAP, "Admin/Content/StudioStories", () -> editor(null, kit.admin(request)));
    }

    @GetMapping({"/admin/content/stories/{id:\\d+}", "/admin/blog/post/{id:\\d+}/edit", "/admin/blog/post/{id:\\d+}"})
    public ResponseEntity<String> edit(HttpServletRequest request, @PathVariable("id") long id) {
        Row post = NotFoundException.orFail(db.find("posts", id));
        return kit.page(request, CAP, "Admin/Content/StudioStories", () -> editor(post, kit.admin(request)));
    }

    private Object grid(HttpServletRequest request) {
        return Grid.from(db, request)
                .columns("p.id, p.title, p.subtitle, p.slug, p.status, p.featured, p.image, p.views, p.read_minutes, p.publish_at, p.published_at,"
                        + " p.created_at, p.updated_at, coalesce(a.name, pb.name) as author,"
                        + " (select coalesce(sum(v.views), 0) from post_views v where v.post_id = p.id and v.day >= '" + LocalDate.now().minusDays(29) + "') as views_30d,"
                        + " (select group_concat(t.name order by t.name separator ', ') from post_tag pt join tags t on t.id = pt.tag_id where pt.post_id = p.id) as tags")
                .from("posts p left join admins a on a.id = p.author_id left join admins pb on pb.id = p.posted_by")
                .search("p.title", "p.subtitle", "p.slug")
                .sort("title", "p.title").sort("updated", "p.updated_at").sort("views", "p.views").sort("published", "p.published_at")
                .defaultSort("updated", "desc").tieBreak("p.id desc")
                .filter("status", v -> WORKFLOW.containsKey(v) ? (WORKFLOW.get(v) == 0 ? Grid.where("coalesce(p.status, 0) = 0") : Grid.where("p.status = ?", WORKFLOW.get(v))) : null)
                .filter("author", v -> v.matches("\\d+") ? Grid.where("coalesce(p.author_id, p.posted_by) = ?", Long.parseLong(v)) : null)
                .filter("tag", v -> v.isBlank() ? null : Grid.where("exists (select 1 from post_tag pt join tags t on t.id = pt.tag_id where pt.post_id = p.id and lower(t.name) = ?)", v.toLowerCase()))
                .filter("featured", v -> "1".equals(v) ? Grid.where("p.featured = 1") : null)
                .filter("category", v -> v.matches("\\d+") ? Grid.where("exists (select 1 from category_post cp where cp.post_id = p.id and cp.category_id = ?)", Long.parseLong(v)) : null)
                .map(r -> {
                    r.put("image_url", r.str("image", "").isBlank() ? null : "/storage/uploads/blog/" + r.str("image"));
                    r.put("workflow", workflowName(r.lng("status", 0)));
                    r.put("public_url", "/studio-stories/" + r.str("slug"));
                    return r;
                })
                .page();
    }

    private Map<String, Object> counts() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("all", db.count("select count(*) from posts"));
        m.put("draft", db.count("select count(*) from posts where coalesce(status, 0) = 0"));
        m.put("review", db.count("select count(*) from posts where status = 3"));
        m.put("scheduled", db.count("select count(*) from posts where status = 4"));
        m.put("published", db.count("select count(*) from posts where status = 1"));
        m.put("archived", db.count("select count(*) from posts where status = 2"));
        return m;
    }

    /**
     * The dashboard boxes: views in the last 30 days (and the 30 before), velocity (views per day this week against last
     * week), average read time of published stories, the daily series and the top stories.
     */
    Map<String, Object> analytics() {
        LocalDate today = LocalDate.now();
        long v30 = sum("select coalesce(sum(views), 0) from post_views where day >= ?", today.minusDays(29));
        long vPrev = sum("select coalesce(sum(views), 0) from post_views where day >= ? and day < ?", today.minusDays(59), today.minusDays(29));
        long w1 = sum("select coalesce(sum(views), 0) from post_views where day >= ?", today.minusDays(6));
        long w0 = sum("select coalesce(sum(views), 0) from post_views where day >= ? and day < ?", today.minusDays(13), today.minusDays(6));
        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        for (int i = 29; i >= 0; i--) byDay.put(today.minusDays(i), 0L);
        for (Row r : db.select("select day, sum(views) as v from post_views where day >= ? group by day", today.minusDays(29))) {
            Object d = r.get("day");
            LocalDate day = d instanceof java.sql.Date sd ? sd.toLocalDate() : LocalDate.parse(String.valueOf(d).substring(0, 10));
            byDay.put(day, r.lng("v", 0));
        }
        List<Map<String, Object>> series = new ArrayList<>();
        byDay.forEach((d, v) -> series.add(Map.of("day", d.toString(), "views", v)));
        Row rt = db.first("select avg(nullif(read_minutes, 0)) as m, sum(views) as total from posts where status = 1");
        List<Map<String, Object>> top = new ArrayList<>();
        for (Row r : db.select("select p.id, p.title, p.slug, p.read_minutes, coalesce(sum(v.views), 0) as views from posts p"
                + " join post_views v on v.post_id = p.id and v.day >= ? where p.status = 1 group by p.id, p.title, p.slug, p.read_minutes"
                + " order by views desc, p.id desc limit 5", today.minusDays(29))) {
            top.add(Map.of("id", r.get("id"), "title", r.str("title"), "slug", r.str("slug"), "views", r.lng("views", 0), "readMinutes", r.lng("read_minutes", 0)));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("views30", v30);
        m.put("views30Previous", vPrev);
        m.put("viewsTotal", rt == null ? 0 : rt.lng("total", 0));
        m.put("velocity", Math.round(w1 / 7.0 * 10) / 10.0);                 // views a day, last 7 days
        m.put("velocityPrevious", Math.round(w0 / 7.0 * 10) / 10.0);         // the 7 days before
        m.put("avgReadMinutes", rt == null || rt.get("m") == null ? 0 : Math.round(Double.parseDouble(String.valueOf(rt.get("m"))) * 10) / 10.0);
        m.put("published", db.count("select count(*) from posts where status = 1"));
        m.put("series", series);
        m.put("top", top);
        return m;
    }

    private long sum(String sql, Object... params) {
        Row r = db.first(sql.replace("coalesce(sum(views), 0)", "coalesce(sum(views), 0) as s"), params);
        return r == null ? 0 : r.lng("s", 0);
    }

    private List<Row> authors() {
        return db.select("select id as value, name as label from admins where admin_type in ('admin', 'teacher') order by name");
    }

    private Map<String, Object> editor(Row post, Row admin) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("mode", "edit");
        p.put("post", post == null ? null : view(post));
        p.put("categories", db.select("select id as value, name as label from categories order by name"));
        p.put("tags", db.select("select name from tags order by name").stream().map(r -> r.str("name")).toList());
        p.put("authors", authors());
        p.put("me", admin == null ? null : admin.get("id"));
        return p;
    }

    private Map<String, Object> view(Row post) {
        long id = post.lng("id");
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", id);
        for (String k : List.of("title", "subtitle", "slug", "body", "meta_title", "meta_description")) {
            v.put(k, post.str(k, ""));
        }
        long status = post.lng("status", 0);
        v.put("status", status == 1);
        v.put("workflow", workflowName(status));
        v.put("featured", post.lng("featured", 0) == 1);
        v.put("author_id", post.get("author_id") != null ? String.valueOf(post.get("author_id")) : post.get("posted_by") == null ? "" : String.valueOf(post.get("posted_by")));
        v.put("publish_at", post.get("publish_at"));
        v.put("published_at", post.get("published_at"));
        v.put("views", post.lng("views", 0));
        v.put("read_minutes", post.lng("read_minutes", 0));
        v.put("image", post.str("image", "").isBlank() ? null : Map.of("name", post.str("image"), "url", "/storage/uploads/blog/" + post.str("image")));
        v.put("category_ids", db.select("select category_id from category_post where post_id = ? order by category_id", id).stream()
                .map(r -> String.valueOf(r.get("category_id"))).toList());
        v.put("tags", db.select("select t.name from post_tag pt join tags t on t.id = pt.tag_id where pt.post_id = ? order by t.name", id).stream()
                .map(r -> r.str("name")).toList());
        v.put("created_at", post.get("created_at"));
        v.put("updated_at", post.get("updated_at"));
        v.put("public_url", "/studio-stories/" + post.str("slug"));
        return v;
    }

    // ================================================================ JSON API

    @GetMapping("/admin/api/content/stories")
    public ResponseEntity<String> list(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, CAP)) return kit.forbidden();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("grid", grid(request));
        m.put("analytics", analytics());
        m.put("counts", counts());
        return kit.ok(m);
    }

    @GetMapping("/admin/api/content/stories/{id:\\d+}")
    public ResponseEntity<String> one(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, CAP)) return kit.forbidden();
        Row post = db.find("posts", id);
        if (post == null) return kit.fail(HttpStatus.NOT_FOUND, "That story was deleted.");
        return kit.ok(Map.of("post", view(post)));
    }

    /** multipart (data = JSON of the fields, image = cover) or JSON {data: {...}}. */
    @PostMapping({"/admin/api/content/stories", "/admin/api/posts"})
    public ResponseEntity<String> store(HttpServletRequest request) throws IOException {
        return save(request, null);
    }

    @PostMapping({"/admin/api/content/stories/{id:\\d+}", "/admin/api/posts/{id:\\d+}"})
    public ResponseEntity<String> update(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        if (db.find("posts", id) == null) return kit.fail(HttpStatus.NOT_FOUND, "That story was deleted.");
        return save(request, id);
    }

    /** JSON {data: {...}} (no cover upload; send the image with POST). */
    @PutMapping("/admin/api/content/stories/{id:\\d+}")
    public ResponseEntity<String> put(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        if (db.find("posts", id) == null) return kit.fail(HttpStatus.NOT_FOUND, "That story was deleted.");
        return save(request, id);
    }

    private ResponseEntity<String> save(HttpServletRequest request, Long id) throws IOException {
        Row admin = kit.admin(request);
        if (!kit.can(admin, CAP)) return kit.forbidden();
        Map<String, Object> d;
        UploadedFile image = null;
        String type = String.valueOf(request.getContentType()).toLowerCase();
        if (type.startsWith("application/json")) {
            Map<String, Object> body = json.decodeMap(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            Object inner = body == null ? null : body.get("data");
            @SuppressWarnings("unchecked")
            Map<String, Object> m = inner instanceof Map<?, ?> ? (Map<String, Object>) inner : body;
            d = m;
        } else {
            Input in = Input.of(request, json);
            d = json.decodeMap(in.get("data"));
            image = in.hasFile("image") ? in.file("image") : null;
        }
        if (d == null) return kit.fail(HttpStatus.BAD_REQUEST, "The form data is missing.");
        return saveData(admin, id, d, image);
    }

    ResponseEntity<String> saveData(Row admin, Long id, Map<String, Object> d, UploadedFile image) {
        Row existing = id == null ? null : db.find("posts", id);
        Map<String, String> errors = new LinkedHashMap<>();
        String title = AdminKit.trim(CourseAdminController.str(d, "title"), 256);
        if (AdminKit.blank(title)) errors.put("title", "Give the story a title.");

        // workflow: "workflow" (draft|review|scheduled|published|archived), or the older boolean "status"
        String wf = CourseAdminController.str(d, "workflow").trim().toLowerCase();
        if (wf.isEmpty()) wf = d.containsKey("status") ? (CourseAdminController.bool(d, "status") ? "published" : "draft")
                : existing == null ? "draft" : workflowName(existing.lng("status", 0));
        if (!WORKFLOW.containsKey(wf)) errors.put("workflow", "Choose Draft, In review, Scheduled, Published or Archived.");
        boolean goingLive = "published".equals(wf) || "scheduled".equals(wf);

        String subtitle = AdminKit.trim(CourseAdminController.str(d, "subtitle"), 500);
        if (goingLive && AdminKit.blank(subtitle)) errors.put("subtitle", "Add a short summary; it shows in the story list.");
        String body = HtmlCleaner.clean(CourseAdminController.str(d, "body"));
        if (goingLive && HtmlCleaner.text(body, 20).isBlank()) errors.put("body", "Write the story before publishing it.");

        String slug = CourseAdminController.str(d, "slug").trim().toLowerCase();
        if (!slug.isEmpty() && (!slug.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$") || slug.length() > 100)) {
            errors.put("slug", "Use lowercase letters, numbers and single dashes (up to 100 characters).");
        } else if (!slug.isEmpty() && db.count("select count(*) from posts where slug = ? and id <> ?", slug, id == null ? 0 : id) > 0) {
            errors.put("slug", "Another story already uses this URL.");
        }
        String metaTitle = CourseAdminController.str(d, "meta_title").trim();
        if (metaTitle.length() > 255) errors.put("meta_title", "Keep it under 255 characters.");
        String metaDescription = CourseAdminController.str(d, "meta_description").trim();
        if (metaDescription.length() > 500) errors.put("meta_description", "Keep it under 500 characters.");

        Long authorId = null;
        String a = CourseAdminController.str(d, "author_id").trim();
        if (!a.isEmpty()) {
            if (!a.matches("\\d{1,18}") || db.find("admins", Long.parseLong(a)) == null) errors.put("author_id", "Choose an author from the list.");
            else authorId = Long.parseLong(a);
        }
        LocalDateTime publishAt = null;
        String pa = CourseAdminController.str(d, "publish_at").trim();
        if ("scheduled".equals(wf)) {
            try {
                publishAt = pa.isEmpty() ? null : LocalDateTime.parse(pa.length() == 16 ? pa + ":00" : pa.replace(' ', 'T'), LOCAL);
            } catch (DateTimeParseException e) {
                publishAt = null;
            }
            if (publishAt == null) errors.put("publish_at", "Pick the date and time to publish.");
            else if (publishAt.isBefore(LocalDateTime.now().minusMinutes(1))) errors.put("publish_at", "Pick a time in the future, or publish now.");
        }
        String imageError = AdminKit.checkImage(image);
        if (imageError != null) errors.put("image", imageError);
        if (!errors.isEmpty()) return kit.invalid(errors);

        if (slug.isEmpty()) {
            slug = slugs.unique("posts", "slug", title, id);
            if (slug.length() > 100) slug = slug.substring(0, 100).replaceAll("-+$", "");
        }
        int status = WORKFLOW.get(wf);
        LocalDateTime now = LocalDateTime.now();
        Row v = Row.of("title", title, "subtitle", subtitle == null ? "" : subtitle, "slug", slug, "body", body == null ? "" : body,
                "status", status, "featured", CourseAdminController.bool(d, "featured") ? 1 : 0,
                "meta_title", metaTitle.isEmpty() ? null : metaTitle, "meta_description", metaDescription.isEmpty() ? null : metaDescription,
                "author_id", authorId != null ? authorId : existing != null && existing.get("author_id") != null ? existing.get("author_id") : admin.get("id"),
                "publish_at", publishAt, "read_minutes", readMinutes(body));
        boolean wasPublished = existing != null && existing.lng("status", 0) == 1;
        if (status == 1 && !wasPublished) {
            v.put("published_at", now);
            v.put("created_at", now);                    // the public date is the publish date
        }
        v.put("archived_at", status == 2 ? (existing != null && existing.get("archived_at") != null ? existing.get("archived_at") : now) : null);
        Map<String, Object> before = existing == null ? null : audit.snapshot("posts", id);
        if (id == null) {
            v.put("posted_by", admin.get("id"));
            v.put("like", 0);
            v.put("dislike", 0);
            id = db.insert("posts", v);
        } else {
            db.updateById("posts", id, v);
        }
        if (image != null) {
            db.updateById("posts", id, Row.of("image", storage.storeUpload(image, "uploads/blog")));
        } else if (CourseAdminController.bool(d, "remove_image")) {
            db.updateById("posts", id, Row.of("image", null));
        }
        db.update("delete from category_post where post_id = ?", id);
        if (d.get("category_ids") instanceof List<?> cats) {
            for (Object c : new LinkedHashSet<>(cats)) {
                String cid = String.valueOf(c);
                if (cid.matches("\\d+") && db.find("categories", Long.parseLong(cid)) != null) {
                    db.insertPlain("category_post", Row.of("category_id", Long.parseLong(cid), "post_id", id, "created_at", now, "updated_at", now));
                }
            }
        }
        db.update("delete from post_tag where post_id = ?", id);
        if (d.get("tags") instanceof List<?> tags) {
            Set<String> seen = new LinkedHashSet<>();
            for (Object t : tags) {
                String name = AdminKit.trim(String.valueOf(t).replaceAll("\\s+", " "), 60);
                if (name == null || name.isBlank() || !seen.add(name.toLowerCase())) continue;
                Row tag = db.first("select id from tags where lower(name) = ?", name.toLowerCase());
                long tagId = tag != null ? tag.lng("id") : db.insert("tags", Row.of("name", name, "slug", slugs.unique("tags", "slug", name, null), "status", 1));
                db.insertPlain("post_tag", Row.of("post_id", id, "tag_id", tagId, "created_at", now, "updated_at", now));
            }
        }
        audit.change(admin, "post", id, existing == null ? "created (" + wf + ")" : "saved (" + wf + ")", title, before, audit.snapshot("posts", id));
        Row saved = db.find("posts", id);
        String message = switch (wf) {
            case "published" -> wasPublished ? "Saved. The live story is updated." : "Published.";
            case "scheduled" -> "Scheduled for " + publishAt.toString().replace('T', ' ').substring(0, 16) + ".";
            case "review" -> "Saved and sent for review.";
            case "archived" -> "Archived. It is no longer on the site.";
            default -> "Draft saved.";
        };
        return kit.ok(Map.of("id", id, "post", view(saved), "editUrl", "/admin/content/stories/" + id, "message", message));
    }

    /** Words / 200, at least 1 minute for any text. */
    static int readMinutes(String html) {
        String text = HtmlCleaner.text(html == null ? "" : html, Integer.MAX_VALUE);
        int words = text.isBlank() ? 0 : text.trim().split("\\s+").length;
        return words == 0 ? 0 : Math.max(1, (int) Math.ceil(words / 200.0));
    }

    /** Quick actions from the table: {status: draft|review|published|archived}. */
    @PostMapping({"/admin/api/content/stories/{id:\\d+}/workflow", "/admin/api/posts/{id:\\d+}/status"})
    public ResponseEntity<String> workflow(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, CAP)) return kit.forbidden();
        Row post = db.find("posts", id);
        if (post == null) return kit.fail(HttpStatus.NOT_FOUND, "That story was deleted.");
        Input in = Input.of(request, json);
        String wf = in.get("workflow");
        if (wf == null || wf.isBlank()) {
            String s = in.get("status");
            wf = s == null ? "" : WORKFLOW.containsKey(s) ? s : in.bool("status") ? "published" : "draft";   // older clients send a boolean
        }
        wf = wf.toLowerCase();
        if (!WORKFLOW.containsKey(wf) || "scheduled".equals(wf)) return kit.invalid(Map.of("workflow", "Choose Draft, In review, Published or Archived."));
        if ("published".equals(wf) && (post.str("subtitle", "").isBlank() || HtmlCleaner.text(post.str("body"), 20).isBlank())) {
            return kit.fail(HttpStatus.CONFLICT, "Add a summary and the story text before publishing.");
        }
        Map<String, Object> before = audit.snapshot("posts", id);
        LocalDateTime now = LocalDateTime.now();
        Row v = Row.of("status", WORKFLOW.get(wf), "publish_at", null, "archived_at", "archived".equals(wf) ? now : null);
        if ("published".equals(wf) && post.lng("status", 0) != 1) {
            v.put("created_at", now);
            v.put("published_at", now);
        }
        db.updateById("posts", id, v);
        audit.change(admin, "post", id, wf, post.str("title"), before, audit.snapshot("posts", id));
        String message = switch (wf) {
            case "published" -> "Published.";
            case "archived" -> "Archived.";
            case "review" -> "Sent for review.";
            default -> "Moved back to drafts.";
        };
        return kit.ok(Map.of("message", message, "workflow", wf, "counts", counts()));
    }

    @DeleteMapping({"/admin/api/content/stories/{id:\\d+}", "/admin/api/posts/{id:\\d+}"})
    public ResponseEntity<String> destroy(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, CAP)) return kit.forbidden();
        Row post = NotFoundException.orFail(db.find("posts", id));
        Map<String, Object> before = audit.snapshot("posts", id);
        db.update("delete from post_tag where post_id = ?", id);
        db.update("delete from category_post where post_id = ?", id);
        db.update("delete from post_views where post_id = ?", id);
        db.deleteById("posts", id);
        audit.change(admin, "post", id, "deleted", post.str("title"), before, null);
        return kit.ok(Map.of("message", "Deleted “" + post.str("title") + "”."));
    }

    /** Scheduled stories go live when their time comes. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void publishDue() {
        LocalDateTime now = LocalDateTime.now();
        try {
            for (Row p : db.select("select id, title from posts where status = 4 and publish_at is not null and publish_at <= ?", now)) {
                db.update("update posts set status = 1, published_at = ?, created_at = ?, publish_at = null where id = ? and status = 4", now, now, p.get("id"));
                audit.log(null, "post", p.lng("id"), "published (scheduled)", p.str("title"));
            }
            // stories written before read times were stored (and demo data) get theirs, a few at a time
            for (Row p : db.select("select id, body from posts where coalesce(read_minutes, 0) = 0 and body is not null and body <> '' limit 50")) {
                int m = readMinutes(p.str("body"));
                if (m > 0) db.update("update posts set read_minutes = ? where id = ?", m, p.get("id"));
            }
        } catch (RuntimeException e) {
            // the columns may not exist yet while a migration runs
        }
    }
}
