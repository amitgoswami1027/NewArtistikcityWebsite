package com.artistikcity.web;

import com.artistikcity.support.Db;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * /sitemap.xml: the public pages search engines should know about: home, the course list, every live course, published
 * landing pages whose live version has "List in the sitemap" and "Show in search engines" on (Landing Page Management >
 * SEO & marketing), the journal and its published stories, and the marketplace. A landing page that replaces a course
 * page (same address) decides for that address. The host comes from the request (CloudFrontFilter already set https).
 */
@RestController
public class SitemapController {

    private final Db db;
    private final Json json;

    public SitemapController(Db db, Json json) {
        this.db = db;
        this.json = json;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap(HttpServletRequest request) {
        String base = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort());
        Map<String, String> urls = new LinkedHashMap<>();   // path -> last modified (yyyy-MM-dd or "")
        urls.put("/", "");
        urls.put("/courses", "");
        for (Row c : db.select("select slug, updated_at from courses where status = '1' order by id")) {
            urls.put("/course/" + c.str("slug"), day(c.get("updated_at")));
        }
        for (Row p : db.select("select p.slug, p.published_at, v.config from landing_pages p join landing_page_versions v on v.id = p.published_version_id"
                + " where p.status = 'PUBLISHED' and p.course_id is not null order by p.id")) {
            Map<String, Object> seo = seo(p.str("config"));
            String path = "/course/" + p.str("slug");
            boolean listed = !Boolean.FALSE.equals(seo.get("sitemap")) && !Boolean.FALSE.equals(seo.get("index"));
            String canonical = seo.get("canonical") == null ? "" : String.valueOf(seo.get("canonical")).trim();
            if (listed && (canonical.isEmpty() || canonical.equals(path) || canonical.equals(base + path))) {
                urls.put(path, day(p.get("published_at")));
            } else {
                urls.remove(path);       // a hidden landing page also hides the course page it replaces
            }
        }
        urls.put("/studio-stories", "");
        for (Row s : db.select("select slug, updated_at from posts where status = 1 and slug is not null and slug <> '' order by id")) {
            urls.put("/studio-stories/" + s.str("slug"), day(s.get("updated_at")));
        }
        urls.put("/marketplace", "");
        urls.put("/how-it-works", "");
        urls.put("/commission/step-1", "");
        // a landing page replacing a site page: its last publish date, or left out when it is hidden from search engines
        for (Row p : db.select("select p.site_route, p.published_at, v.config from landing_pages p join landing_page_versions v on v.id = p.published_version_id"
                + " where p.status = 'PUBLISHED' and p.kind = 'SITE' order by p.id")) {
            com.artistikcity.landing.LandingCatalog.SiteRoute route = com.artistikcity.landing.LandingCatalog.siteRoute(p.str("site_route", ""));
            if (route == null) continue;
            Map<String, Object> seo = seo(p.str("config"));
            boolean listed = !Boolean.FALSE.equals(seo.get("sitemap")) && !Boolean.FALSE.equals(seo.get("index"));
            if (listed) urls.put(route.path(), day(p.get("published_at")));
            else urls.remove(route.path());
        }

        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        urls.forEach((path, mod) -> {
            x.append("  <url><loc>").append(escape(base + path)).append("</loc>");
            if (!mod.isEmpty()) x.append("<lastmod>").append(mod).append("</lastmod>");
            x.append("</url>\n");
        });
        x.append("</urlset>\n");
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).contentType(MediaType.APPLICATION_XML).body(x.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> seo(String config) {
        Map<String, Object> c = json.decodeMap(config);
        Object s = c == null ? null : c.get("seo");
        return s instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String day(Object o) {
        String s = o == null ? "" : String.valueOf(o);
        return s.length() >= 10 ? s.substring(0, 10) : "";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
