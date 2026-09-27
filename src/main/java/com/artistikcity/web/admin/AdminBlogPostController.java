package com.artistikcity.web.admin;

import com.artistikcity.http.Auth;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.http.Redirects;
import com.artistikcity.service.PostService;
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
import org.springframework.web.bind.annotation.PutMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Blog posts (Admin\Blog\PostController - missing from the original repository, implemented from its views). */
@Controller
public class AdminBlogPostController {

    private final Views views;
    private final Db db;
    private final Json json;
    private final Auth auth;
    private final Validator validator;
    private final Storage storage;
    private final Slugs slugs;
    private final Redirects redirect;

    public AdminBlogPostController(Views views, Db db, Json json, Auth auth, Validator validator, Storage storage, Slugs slugs, Redirects redirect) {
        this.views = views;
        this.db = db;
        this.json = json;
        this.auth = auth;
        this.validator = validator;
        this.storage = storage;
        this.slugs = slugs;
        this.redirect = redirect;
    }

    @GetMapping("/admin/blog/post")
    public ResponseEntity<String> index(HttpServletRequest request) {
        List<Row> posts = db.select("select * from posts order by id desc");
        for (Row p : posts) {
            p.put("created_at", PostService.formatDate(p.str("created_at")));
        }
        return views.page(request, "admin/blog/post/index", Map.of("posts", posts));
    }

    @GetMapping("/admin/blog/post/create")
    public ResponseEntity<String> create(HttpServletRequest request) {
        return views.page(request, "admin/blog/post/create", Map.of("tags", db.select("select * from tags order by id"),
                "categories", db.select("select * from categories order by id")));
    }

    @PostMapping("/admin/blog/post")
    public ResponseEntity<String> store(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "title", "required", "subtitle", "required", "body", "required");
        long id = db.insert("posts", Row.of("title", in.get("title"), "subtitle", in.get("subtitle"),
                "slug", slugs.unique("posts", "slug", in.get("title"), null), "body", in.get("body"),
                "status", in.bool("status") ? 1 : 0, "featured", in.bool("featured") ? 1 : 0, "posted_by", auth.adminId(request),
                "like", 0, "dislike", 0));
        saveRelated(in, id);
        return redirect.routeWith(request, "success", "The post has been added!", "admin.blog.post.index");
    }

    @GetMapping("/admin/blog/post/{id}/edit")
    public ResponseEntity<String> edit(HttpServletRequest request, @PathVariable("id") Long id) {
        Row post = NotFoundException.orFail(db.find("posts", id));
        post.put("tag_ids", String.join(",", ids("select tag_id as id from post_tag where post_id = ?", id)));
        post.put("category_ids", String.join(",", ids("select category_id as id from category_post where post_id = ?", id)));
        return views.page(request, "admin/blog/post/edit", Map.of("post", post, "tags", db.select("select * from tags order by id"),
                "categories", db.select("select * from categories order by id")));
    }

    private List<String> ids(String sql, Object id) {
        List<String> out = new ArrayList<>();
        for (Row r : db.select(sql, id)) {
            out.add(r.str("id"));
        }
        return out;
    }

    @PutMapping("/admin/blog/post/{id}")
    public ResponseEntity<String> put(HttpServletRequest request, @PathVariable("id") Long id) {
        return update(request, id);
    }

    @PatchMapping("/admin/blog/post/{id}")
    public ResponseEntity<String> update(HttpServletRequest request, @PathVariable("id") Long id) {
        Input in = Input.of(request, json);
        validator.validate(in, "title", "required", "subtitle", "required", "body", "required");
        NotFoundException.orFail(db.find("posts", id));
        db.updateById("posts", id, Row.of("title", in.get("title"), "subtitle", in.get("subtitle"),
                "slug", slugs.unique("posts", "slug", in.get("title"), id), "body", in.get("body"),
                "status", in.bool("status") ? 1 : 0, "featured", in.bool("featured") ? 1 : 0));
        saveRelated(in, id);
        return redirect.routeWith(request, "success", "The post has been updated!", "admin.blog.post.index");
    }

    private void saveRelated(Input in, long id) {
        if (in.hasFile("image")) {
            String name = storage.storeUpload(in.file("image"), "uploads/blog");
            db.updateById("posts", id, Row.of("image", name));
        }
        db.update("delete from post_tag where post_id = ?", id);
        for (String t : in.array("tags")) {
            db.insertPlain("post_tag", Row.of("post_id", id, "tag_id", Long.valueOf(t), "created_at", java.time.LocalDateTime.now()));
        }
        db.update("delete from category_post where post_id = ?", id);
        for (String c : in.array("categories")) {
            db.insertPlain("category_post", Row.of("category_id", Long.valueOf(c), "post_id", id, "created_at", java.time.LocalDateTime.now()));
        }
    }

    @DeleteMapping("/admin/blog/post/{id}")
    public ResponseEntity<String> destroy(HttpServletRequest request, @PathVariable("id") Long id) {
        db.update("delete from post_tag where post_id = ?", id);
        db.update("delete from category_post where post_id = ?", id);
        db.deleteById("posts", id);
        return redirect.routeWith(request, "success", "Post successfully deleted!", "admin.blog.post.index");
    }
}
