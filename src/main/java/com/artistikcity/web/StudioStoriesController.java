package com.artistikcity.web;

import com.artistikcity.http.Auth;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.StudioStoriesService;
import com.artistikcity.support.Db;
import com.artistikcity.support.HtmlCleaner;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Studio Stories, the ArtistikCity journal: every story written in Admin > Content > Studio Stories is published here.
 * {@code /studio-stories} lists the published stories (featured first, categories, tags, search), {@code /studio-stories/{slug}}
 * shows one (drafts only for signed-in admins, as a preview). The old blog addresses ({@code /blog/posts}, {@code /blog/post/{slug}})
 * redirect here permanently. Stories from the Studio Vaishali Arts WordPress blog are offered below as "More from the studio blog".
 */
@Controller
public class StudioStoriesController {

    static final String BASE = "/studio-stories";
    private static final String CARD_COLUMNS = "p.id, p.title, p.subtitle, p.slug, p.image, p.featured, p.views, p.read_minutes,"
            + " coalesce(p.published_at, p.created_at) as published, p.updated_at, coalesce(p.author_id, p.posted_by) as author";

    private final Inertia inertia;
    private final Db db;
    private final Auth auth;
    private final StudioStoriesService external;
    private final Json json;

    public StudioStoriesController(Inertia inertia, Db db, Auth auth, StudioStoriesService external, Json json) {
        this.inertia = inertia;
        this.db = db;
        this.auth = auth;
        this.external = external;
        this.json = json;
    }

    // ================================================================ pages

    @GetMapping({BASE, BASE + "/"})
    public ResponseEntity<String> index(HttpServletRequest request) {
        List<Map<String, Object>> stories = cards(db.select("select " + CARD_COLUMNS + " from posts p where p.status = 1"
                + " order by coalesce(p.published_at, p.created_at) desc, p.id desc"));
        Map<String, Object> seo = new LinkedHashMap<>();
        seo.put("title", "Studio Stories · The ArtistikCity journal");
        seo.put("description", "Notes from the easel: techniques, materials, the artist's mindset and stories from the ArtistikCity studio.");
        if (!stories.isEmpty() && stories.get(0).get("image") != null) seo.put("image", Inertia.baseUrl(request) + stories.get(0).get("image"));
        request.setAttribute(Inertia.SEO_ATTRIBUTE, seo);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("stories", stories);
        props.put("categories", db.select("select c.id, c.name, c.slug, count(*) as stories from categories c join category_post cp on cp.category_id = c.id"
                + " join posts p on p.id = cp.post_id and p.status = 1 group by c.id, c.name, c.slug order by count(*) desc, c.name"));
        props.put("studioBlogUrl", external.blogUrl());
        return inertia.render(request, "StudioStories", props);
    }

    @GetMapping(BASE + "/{slug:[A-Za-z0-9\\-_]+}")
    public ResponseEntity<String> show(HttpServletRequest request, @PathVariable("slug") String slug) {
        Row post = NotFoundException.orFail(db.first("select p.*, coalesce(p.published_at, p.created_at) as published, coalesce(p.author_id, p.posted_by) as author"
                + " from posts p where p.slug = ?", slug));
        boolean published = post.lng("status", 0) == 1;
        if (!published && auth.admin(request) == null) throw new NotFoundException();   // drafts: admin preview only
        long id = post.lng("id");

        Map<String, Object> seo = new LinkedHashMap<>();
        seo.put("title", post.str("meta_title", "").isBlank() ? post.str("title") + " · Studio Stories" : post.str("meta_title"));
        seo.put("description", post.str("meta_description", "").isBlank() ? post.str("subtitle", "") : post.str("meta_description"));
        String img = imageUrl(post.str("image", ""));
        if (img != null) seo.put("image", img.startsWith("http") ? img : Inertia.baseUrl(request) + img);
        request.setAttribute(Inertia.SEO_ATTRIBUTE, seo);
        if (published) countView(request, id);

        Map<String, Object> story = card(post);
        story.put("body", HtmlCleaner.clean(post.str("body", "")));
        story.put("status", published ? "published" : "draft");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("story", story);
        props.put("shareUrl", Inertia.baseUrl(request) + BASE + "/" + slug);
        if (published) {
            Object when = post.get("published");
            props.put("newer", one(db.first("select " + CARD_COLUMNS + " from posts p where p.status = 1 and (coalesce(p.published_at, p.created_at) > ?"
                    + " or (coalesce(p.published_at, p.created_at) = ? and p.id > ?)) order by coalesce(p.published_at, p.created_at) asc, p.id asc limit 1", when, when, id)));
            props.put("older", one(db.first("select " + CARD_COLUMNS + " from posts p where p.status = 1 and (coalesce(p.published_at, p.created_at) < ?"
                    + " or (coalesce(p.published_at, p.created_at) = ? and p.id < ?)) order by coalesce(p.published_at, p.created_at) desc, p.id desc limit 1", when, when, id)));
        }
        // related: same category first, then the latest
        props.put("related", cards(db.select("select " + CARD_COLUMNS + ", (select count(*) from category_post a join category_post b on a.category_id = b.category_id"
                + " where a.post_id = p.id and b.post_id = ?) as shared from posts p where p.status = 1 and p.id <> ?"
                + " order by shared desc, coalesce(p.published_at, p.created_at) desc limit 3", id, id)));
        return inertia.render(request, "StoryDetails", props);
    }

    // ================================================================ old blog addresses

    @GetMapping({"/blog/posts", "/blog/posts/{category}", "/blog", "/blog/"})
    public ResponseEntity<String> oldList(@RequestParam(value = "id", required = false) String categoryId) {
        String to = BASE;
        if (categoryId != null && categoryId.matches("\\d{1,18}")) {
            Row c = db.first("select slug from categories where id = ?", Long.parseLong(categoryId));
            if (c != null && !c.str("slug", "").isBlank()) to += "?category=" + URLEncoder.encode(c.str("slug"), StandardCharsets.UTF_8);
        }
        return moved(to);
    }

    @GetMapping("/blog/post/{slug}")
    public ResponseEntity<String> oldPost(@PathVariable("slug") String slug) {
        return moved(BASE + "/" + URLEncoder.encode(slug, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    // ================================================================ JSON for the home page strip

    /** Latest published stories; topped up from the studio blog while there are fewer than {@code limit}. */
    @GetMapping("/api/studio-stories")
    public ResponseEntity<String> api(@RequestParam(value = "limit", defaultValue = "6") int limit) {
        int n = Math.max(1, Math.min(limit, 30));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : cards(db.select("select " + CARD_COLUMNS + " from posts p where p.status = 1"
                + " order by coalesce(p.published_at, p.created_at) desc, p.id desc limit " + n))) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("title", c.get("title"));
            s.put("url", c.get("url"));
            s.put("image", c.get("image"));
            s.put("excerpt", c.get("subtitle"));
            s.put("date", c.get("date"));
            List<?> cats = (List<?>) c.get("categories");
            s.put("category", cats.isEmpty() ? "Studio Stories" : ((Map<?, ?>) cats.get(0)).get("name"));
            s.put("external", false);
            out.add(s);
        }
        if (out.size() < n) {
            for (Map<String, Object> e : external.latest(n - out.size())) {
                Map<String, Object> s = new LinkedHashMap<>(e);
                s.put("external", true);
                out.add(s);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stories", out);
        body.put("indexUrl", BASE);
        body.put("blogUrl", external.blogUrl());
        return json(body);
    }

    /** Stories from the Studio Vaishali Arts blog (fetched lazily by the page, so a slow blog never slows Studio Stories). */
    @GetMapping("/api/studio-stories/studio-blog")
    public ResponseEntity<String> studioBlog(@RequestParam(value = "limit", defaultValue = "6") int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stories", external.latest(Math.max(1, Math.min(limit, 12))));
        body.put("blogUrl", external.blogUrl());
        return json(body);
    }

    // ================================================================ helpers

    private List<Map<String, Object>> cards(List<Row> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (rows.isEmpty()) return out;
        List<Object> ids = rows.stream().map(r -> r.get("id")).toList();
        String in = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Map<Long, List<Map<String, Object>>> cats = new LinkedHashMap<>();
        for (Row c : db.select("select cp.post_id, c.name, c.slug from category_post cp join categories c on c.id = cp.category_id where cp.post_id in (" + in + ") order by c.name", ids.toArray())) {
            cats.computeIfAbsent(c.lng("post_id"), k -> new ArrayList<>()).add(Map.of("name", c.str("name"), "slug", c.str("slug", "")));
        }
        Map<Long, List<String>> tags = new LinkedHashMap<>();
        for (Row t : db.select("select pt.post_id, t.name from post_tag pt join tags t on t.id = pt.tag_id where pt.post_id in (" + in + ") order by t.name", ids.toArray())) {
            tags.computeIfAbsent(t.lng("post_id"), k -> new ArrayList<>()).add(t.str("name"));
        }
        Map<Long, Map<String, Object>> authors = new LinkedHashMap<>();
        for (Row r : rows) {
            Map<String, Object> c = card(r, cats.getOrDefault(r.lng("id"), List.of()), tags.getOrDefault(r.lng("id"), List.of()), authors);
            out.add(c);
        }
        return out;
    }

    private Map<String, Object> one(Row r) {
        if (r == null) return null;
        List<Map<String, Object>> c = cards(List.of(r));
        return c.isEmpty() ? null : c.get(0);
    }

    private Map<String, Object> card(Row r) {
        return cards(List.of(r)).get(0);
    }

    private Map<String, Object> card(Row r, List<Map<String, Object>> cats, List<String> tags, Map<Long, Map<String, Object>> authors) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("slug", r.str("slug"));
        m.put("url", BASE + "/" + r.str("slug"));
        m.put("title", r.str("title"));
        m.put("subtitle", r.str("subtitle", ""));
        m.put("image", imageUrl(r.str("image", "")));
        m.put("date", r.get("published"));
        m.put("featured", r.lng("featured", 0) == 1);
        m.put("views", r.lng("views", 0));
        m.put("readMinutes", r.lng("read_minutes", 0));
        m.put("categories", cats);
        m.put("tags", tags);
        Long aid = r.get("author") == null ? null : r.lng("author");
        m.put("author", aid == null ? null : authors.computeIfAbsent(aid, this::author));
        return m;
    }

    private Map<String, Object> author(long id) {
        Row a = db.first("select id, name, designation, profile_photo, profile_title from admins where id = ?", id);
        if (a == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", a.str("name", "ArtistikCity"));
        m.put("role", a.str("designation", "").isBlank() ? a.str("profile_title", "") : a.str("designation"));
        m.put("photo", a.str("profile_photo", "").isBlank() ? null : "/storage/uploads/teachers/" + id + "/" + a.str("profile_photo"));
        return m;
    }

    /** Cover image address: a stored file name lives under /storage/uploads/blog/; full addresses are kept. */
    static String imageUrl(String image) {
        if (image == null || image.isBlank()) return null;
        if (image.startsWith("http://") || image.startsWith("https://") || image.startsWith("/")) return image;
        return "/storage/uploads/blog/" + image;
    }

    /**
     * One view per visitor session and story (bots and admins previewing are not counted): the total on the post and a
     * daily row in post_views, for the Admin > Content > Studio Stories dashboard.
     */
    private void countView(HttpServletRequest request, long postId) {
        if (auth.admin(request) != null) return;
        String ua = String.valueOf(request.getHeader("User-Agent")).toLowerCase(Locale.ROOT);
        if (ua.equals("null") || ua.matches(".*(bot|crawl|spider|slurp|preview|monitor|curl|wget|python-requests).*")) return;
        HttpSession session = request.getSession(true);
        @SuppressWarnings("unchecked")
        Set<Long> seen = (Set<Long>) session.getAttribute("blog.viewed");
        if (seen == null) seen = new HashSet<>();
        if (!seen.add(postId)) return;
        session.setAttribute("blog.viewed", seen);
        try {
            db.update("update posts set views = views + 1 where id = ?", postId);
            db.update("insert into post_views (post_id, day, views) values (?, ?, 1) on duplicate key update views = views + 1", postId, LocalDate.now());
        } catch (RuntimeException e) {
            // counting is best effort: the page still renders
        }
    }

    private static ResponseEntity<String> moved(String to) {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY).header("Location", to).build();
    }

    private ResponseEntity<String> json(Object body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }
}
