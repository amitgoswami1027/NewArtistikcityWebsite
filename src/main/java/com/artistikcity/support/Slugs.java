package com.artistikcity.support;

import org.springframework.stereotype.Component;

/** Unique slug generation (replaces cviebrock/eloquent-sluggable). */
@Component
public class Slugs {

    private final Db db;

    public Slugs(Db db) {
        this.db = db;
    }

    /** Returns a slug for {@code source} that is unique in {@code table.column} (ignoring row {@code exceptId}). */
    public String unique(String table, String column, String source, Long exceptId) {
        String base = Str.slug(source);
        if (base.isEmpty()) {
            base = "item";
        }
        String candidate = base;
        int i = 1;
        while (db.count("select count(*) as c from " + table + " where " + column + " = ? and id <> ?",
                candidate, exceptId == null ? -1L : exceptId) > 0) {
            candidate = base + "-" + i++;
        }
        return candidate;
    }
}
