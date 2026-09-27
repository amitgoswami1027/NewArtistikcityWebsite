package com.artistikcity.web;

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

    public BlogController(Inertia inertia, Db db, PostService posts) {
        this.inertia = inertia;
        this.db = db;
        this.posts = posts;
    }

    @GetMapping({"/blog/posts", "/blog/posts/{category}"})
    public ResponseEntity<String> index(HttpServletRequest request,
                                        @RequestParam(value = "id", required = false) String categoryId,
                                        @RequestParam(value = "page", required = false) String pageParam) {
        Long cat = Values.toLong(categoryId);
        String where = cat == null ? "" : " where exists (select 1 from category_post cp where cp.post_id = p.id and cp.category_id = ?)";
        Object[] params = cat == null ? new Object[0] : new Object[]{cat};
        long total = db.count("select count(*) from posts p" + where, params);
        int page = Paginator.page(pageParam);
        List<Row> rows = db.select("select p.* from posts p" + where + " order by p.created_at desc, p.id desc offset "
                + ((page - 1) * PER_PAGE) + " rows fetch next " + PER_PAGE + " rows only", params);
        posts.present(rows, true);
        String path = Inertia.baseUrl(request) + request.getRequestURI() + (cat == null ? "" : "?id=" + cat);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("posts", Paginator.paginate(rows, total, PER_PAGE, page, path));
        props.put("category_list", db.select("select * from categories order by id"));
        props.put("featured_post", posts.present(db.select("select * from posts where featured = 1 order by id"), false));
        return inertia.render(request, "Posts", props);
    }

    @GetMapping("/blog/post/{slug}")
    public ResponseEntity<String> postDetails(HttpServletRequest request, @PathVariable("slug") String slug) {
        Row post = NotFoundException.orFail(db.first("select * from posts where slug = ?", slug));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("post", posts.present(post, true));
        props.put("category_list", db.select("select * from categories order by id"));
        props.put("featured_post", posts.present(db.select("select * from posts where featured = 1 order by id"), false));
        return inertia.render(request, "PostDetails", props);
    }
}
