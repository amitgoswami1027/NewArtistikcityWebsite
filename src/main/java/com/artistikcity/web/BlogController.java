package com.artistikcity.web;

import com.artistikcity.http.Auth;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.PostService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Paginator;
import com.artistikcity.support.Row;
import com.artistikcity.support.Values;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Blog (BlogController.php): posts list (2 per page, optional category filter ?id=) and post details. */
@Controller
public class BlogController {

    private static final int PER_PAGE = 2;

    private final Inertia inertia;
    private final Db db;
    private final PostService posts;
    private final Auth auth;

    public BlogController(Inertia inertia, Db db, PostService posts, Auth auth) {
        this.inertia = inertia;
        this.db = db;
        this.posts = posts;
        this.auth = auth;
    }

    @GetMapping({"/blog/posts", "/blog/posts/{category}"})
    public ResponseEntity<String> index(HttpServletRequest request,
                                        @RequestParam(value = "id", required = false) String categoryId,
                                        @RequestParam(value = "page", required = false) String pageParam) {
        Long cat = Values.toLong(categoryId);
        // only published posts are public (status 1); drafts stay in the admin
        String where = " where p.status = 1" + (cat == null ? "" : " and exists (select 1 from category_post cp where cp.post_id = p.id and cp.category_id = ?)");
        Object[] params = cat == null ? new Object[0] : new Object[]{cat};
        long total = db.count("select count(*) from posts p" + where, params);
        int page = Paginator.page(pageParam);
        List<Row> rows = db.select("select p.* from posts p" + where + " order by p.created_at desc, p.id desc limit " + PER_PAGE + " offset " + ((page - 1) * PER_PAGE), params);
        posts.present(rows, true);
        String path = Inertia.baseUrl(request) + request.getRequestURI() + (cat == null ? "" : "?id=" + cat);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("posts", Paginator.paginate(rows, total, PER_PAGE, page, path));
        props.put("category_list", db.select("select * from categories order by id"));
        props.put("featured_post", posts.present(db.select("select * from posts where featured = 1 and status = 1 order by id"), false));
        return inertia.render(request, "Posts", props);
    }

    @GetMapping("/blog/post/{slug}")
    public ResponseEntity<String> postDetails(HttpServletRequest request, @PathVariable("slug") String slug) {
        Row post = NotFoundException.orFail(db.first("select * from posts where slug = ?", slug));
        // drafts can be previewed by a signed-in admin only
        if (post.lng("status", 0) != 1 && auth.admin(request) == null) {
            throw new NotFoundException();
        }
        Map<String, Object> seo = new LinkedHashMap<>();
        seo.put("title", post.str("meta_title", "").isBlank() ? post.str("title") : post.str("meta_title"));
        seo.put("description", post.str("meta_description", "").isBlank() ? post.str("subtitle") : post.str("meta_description"));
        if (!post.str("image", "").isBlank()) {
            seo.put("image", Inertia.baseUrl(request) + "/storage/uploads/blog/" + post.str("image"));
        }
        request.setAttribute(Inertia.SEO_ATTRIBUTE, seo);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("post", posts.present(post, true));
        props.put("category_list", db.select("select * from categories order by id"));
        props.put("featured_post", posts.present(db.select("select * from posts where featured = 1 and status = 1 order by id"), false));
        return inertia.render(request, "PostDetails", props);
    }
}
