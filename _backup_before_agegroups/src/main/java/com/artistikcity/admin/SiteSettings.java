package com.artistikcity.admin;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Site-wide settings from the settings table (meta_tag/meta_key/meta_value): SEO defaults, contact details and
 * social links. Read on every page, so the values are cached until the admin saves them.
 */
@Service
public class SiteSettings {

    public static final String DEFAULT_TITLE = "ArtistikCity - Live online art classes";
    public static final String DEFAULT_DESCRIPTION = "Learn to draw and paint online with live classes, personal feedback and certificates.";

    private final Db db;
    private volatile Map<String, Map<String, String>> cache;

    public SiteSettings(Db db) {
        this.db = db;
    }

    public Map<String, Map<String, String>> all() {
        Map<String, Map<String, String>> c = cache;
        if (c != null) {
            return c;
        }
        Map<String, Map<String, String>> m = new LinkedHashMap<>();
        try {
            for (Row r : db.select("select meta_tag, meta_key, meta_value from settings where meta_tag <> 'system' order by id")) {
                m.computeIfAbsent(r.str("meta_tag"), k -> new LinkedHashMap<>()).put(r.str("meta_key"), r.str("meta_value", ""));
            }
        } catch (Db.DbException e) {
            return Map.of();
        }
        cache = m;
        return m;
    }

    public String get(String tag, String key, String fallback) {
        String v = all().getOrDefault(tag, Map.of()).get(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    public void invalidate() {
        cache = null;
    }

    /** Values for the public page head (title, description, keywords, share image). */
    public Map<String, Object> seo(String siteUrl) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("title", get("seo", "site_title", DEFAULT_TITLE));
        s.put("description", get("seo", "meta_description", DEFAULT_DESCRIPTION));
        s.put("keywords", get("seo", "meta_keywords", ""));
        String og = get("seo", "og_image", "");
        s.put("image", og.isBlank() ? siteUrl + "/assets/images/logo.png" : og.startsWith("http") ? og : siteUrl + og);
        return s;
    }

    /** Contact details and social links shared with the React pages (footer, contact page). */
    public Map<String, Object> publicInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contact", all().getOrDefault("contact", Map.of()));
        m.put("social", all().getOrDefault("social", Map.of()));
        return m;
    }
}
