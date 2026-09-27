package com.artistikcity.service;

import com.artistikcity.support.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Studio Stories": the latest articles from the Studio Vaishali Arts blog (a WordPress site).
 * Posts are read from the public WordPress REST API, cached in memory and fall back to a
 * built-in list when the blog cannot be reached, so the page always renders.
 */
@Service
public class StudioStoriesService {

    private static final Logger log = LoggerFactory.getLogger(StudioStoriesService.class);
    private static final long CACHE_MILLIS = 60 * 60 * 1000L;

    private final Json json;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private volatile List<Map<String, Object>> cache;
    private volatile long cachedAt;

    public StudioStoriesService(Json json, Environment env) {
        this.json = json;
        this.baseUrl = env.getProperty("app.studio-blog-url", "https://studiovaishaliarts.com").replaceAll("/+$", "");
    }

    public String blogUrl() {
        return baseUrl + "/blog/";
    }

    public List<Map<String, Object>> latest(int limit) {
        List<Map<String, Object>> all = all();
        return all.size() > limit ? all.subList(0, limit) : all;
    }

    public List<Map<String, Object>> all() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> c = cache;
        if (c != null && now - cachedAt < CACHE_MILLIS) {
            return c;
        }
        List<Map<String, Object>> fresh = fetch();
        if (!fresh.isEmpty()) {
            cache = fresh;
            cachedAt = now;
            return fresh;
        }
        return c != null ? c : fallback();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetch() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/wp-json/wp/v2/posts?per_page=30&_embed=1"))
                    .timeout(Duration.ofSeconds(8)).header("Accept", "application/json")
                    .header("User-Agent", "ArtistikCity/1.0").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("Studio Stories feed returned HTTP {}", res.statusCode());
                return out;
            }
            List<Object> posts = json.decodeList(res.body());
            if (posts == null) {
                return out;
            }
            for (Object o : posts) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> p = (Map<String, Object>) o;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", p.get("id"));
                item.put("title", clean(rendered(p.get("title"))));
                item.put("url", p.get("link"));
                item.put("date", String.valueOf(p.getOrDefault("date", "")).split("T")[0]);
                item.put("excerpt", trim(clean(rendered(p.get("excerpt"))), 180));
                item.put("image", null);
                item.put("category", null);
                Object emb = p.get("_embedded");
                if (emb instanceof Map) {
                    Map<String, Object> e = (Map<String, Object>) emb;
                    Object media = e.get("wp:featuredmedia");
                    if (media instanceof List && !((List<Object>) media).isEmpty() && ((List<Object>) media).get(0) instanceof Map) {
                        item.put("image", ((Map<String, Object>) ((List<Object>) media).get(0)).get("source_url"));
                    }
                    Object terms = e.get("wp:term");
                    if (terms instanceof List && !((List<Object>) terms).isEmpty() && ((List<Object>) terms).get(0) instanceof List) {
                        List<Object> cats = (List<Object>) ((List<Object>) terms).get(0);
                        if (!cats.isEmpty() && cats.get(0) instanceof Map) {
                            item.put("category", clean(String.valueOf(((Map<String, Object>) cats.get(0)).get("name"))));
                        }
                    }
                }
                out.add(item);
            }
        } catch (Exception e) {
            log.warn("Could not load Studio Stories from {}: {}", baseUrl, e.getMessage());
        }
        return out;
    }

    private static String rendered(Object field) {
        if (field instanceof Map) {
            Object r = ((Map<?, ?>) field).get("rendered");
            return r == null ? "" : String.valueOf(r);
        }
        return field == null ? "" : String.valueOf(field);
    }

    /** Strips tags and decodes the few HTML entities WordPress emits in titles / excerpts. */
    private static String clean(String html) {
        String s = html.replaceAll("(?s)<[^>]*>", " ")
                .replace("&#8217;", "’").replace("&#8216;", "‘").replace("&#8220;", "“")
                .replace("&#8221;", "”").replace("&#8230;", "…").replace("&#8211;", "–")
                .replace("&#8212;", "—").replace("&hellip;", "…").replace("&nbsp;", " ")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("[&hellip;]", "")
                .replace("[…]", "…");
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String trim(String s, int max) {
        if (s.length() <= max) return s;
        int cut = s.lastIndexOf(' ', max);
        return s.substring(0, cut > 0 ? cut : max) + "…";
    }

    /** Used when the blog is unreachable (offline development, firewall, etc.). */
    private List<Map<String, Object>> fallback() {
        List<Map<String, Object>> l = new ArrayList<>();
        l.add(post(62, "Can you imagine a world without art??", "can-you-imagine-a-world-without-art/", "2022-08-16",
                "/wp-content/uploads/2022/08/Img1.jpeg", "Why art quietly shapes every ordinary moment of our day."));
        l.add(post(60, "Artists Block", "artists-block/", "2022-08-16",
                "/wp-content/uploads/2022/08/Img2.jpg", "What creative block really is, and gentle ways to get your hands moving again."));
        l.add(post(58, "If you want to be an art professional, you have to think this first….",
                "if-you-want-to-be-an-art-professional-you-have-to-think-this-firs/", "2022-08-16",
                "/wp-content/uploads/2022/08/WhatsApp-Image-2023-02-15-at-13.26.19.jpeg", "Planning and direction matter more than chasing every new trend."));
        l.add(post(0, "Want to come out of anxiety and depression?", "want-to-come-out-of-anxiety-and-depression/", "",
                "/wp-content/uploads/2022/08/WhatsApp-Image-2023-02-15-at-13.31.07.jpeg", "How a regular art practice can become a calm, mindful space."));
        return l;
    }

    private Map<String, Object> post(int id, String title, String path, String date, String image, String excerpt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("url", baseUrl + "/" + path);
        m.put("date", date);
        m.put("excerpt", excerpt);
        m.put("image", baseUrl + image);
        m.put("category", null);
        return m;
    }
}
