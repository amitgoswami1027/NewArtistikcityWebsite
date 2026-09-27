package com.artistikcity.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One database row (column name -> value), in column order.
 * It is a plain Map so it serialises to JSON for Inertia props exactly like a
 * Laravel model's toArray(), and Mustache templates can read it directly.
 */
public class Row extends LinkedHashMap<String, Object> {

    public Row() {
        super();
    }

    public Row(Map<String, ?> source) {
        super(source);
    }

    public static Row of(Object... keyValues) {
        Row r = new Row();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            r.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return r;
    }

    /** Fluent put. */
    public Row with(String key, Object value) {
        put(key, value);
        return this;
    }

    public String str(String key) {
        Object v = get(key);
        return v == null ? null : String.valueOf(v);
    }

    public String str(String key, String def) {
        String s = str(key);
        return s == null ? def : s;
    }

    public Long lng(String key) {
        return Values.toLong(get(key));
    }

    public long lng(String key, long def) {
        Long v = lng(key);
        return v == null ? def : v;
    }

    public Integer integer(String key) {
        Long v = lng(key);
        return v == null ? null : v.intValue();
    }

    /** PHP-style truthiness: null, "", "0", 0, false are false. */
    public boolean truthy(String key) {
        return Values.truthy(get(key));
    }

    /** Copy without the given keys (e.g. password). */
    public Row without(String... keys) {
        Row r = new Row(this);
        for (String k : keys) {
            r.remove(k);
        }
        return r;
    }

    /** Copy with only the given keys. */
    public Row only(String... keys) {
        Row r = new Row();
        for (String k : keys) {
            if (containsKey(k)) {
                r.put(k, get(k));
            }
        }
        return r;
    }
}
