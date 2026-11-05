package com.artistikcity.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Request input, the equivalent of Laravel's {@code $request->input()/all()/file()}.
 *
 * Merges query string, form fields (urlencoded or multipart) and JSON bodies (Inertia posts JSON),
 * and understands PHP style array names such as {@code genre[]} and {@code video[0][title]}.
 * Like Laravel's TrimStrings + ConvertEmptyStringsToNull middleware, strings are trimmed and empty
 * strings become null.
 */
public class Input {

    private static final String ATTR = Input.class.getName();

    private final Map<String, Object> data = new LinkedHashMap<>();
    private final Map<String, List<UploadedFile>> files = new LinkedHashMap<>();

    private Input() {
    }

    /** Parses (once per request) and returns the request input. */
    public static Input of(HttpServletRequest request, Json json) {
        Object cached = request.getAttribute(ATTR);
        if (cached instanceof Input in) {
            return in;
        }
        Input in = new Input();
        in.parse(request, json);
        request.setAttribute(ATTR, in);
        return in;
    }

    @SuppressWarnings("unchecked")
    private void parse(HttpServletRequest request, Json json) {
        String contentType = request.getContentType() == null ? "" : request.getContentType().toLowerCase();
        // query string + urlencoded/multipart text fields
        for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            for (String v : e.getValue()) {
                put(e.getKey(), clean(v));
            }
        }
        if (contentType.startsWith("application/json")) {
            try (InputStream is = request.getInputStream()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                Object parsed = json.decode(body);
                if (parsed instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        data.put(String.valueOf(e.getKey()), cleanDeep(e.getValue()));
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read request body", e);
            }
        }
        if (contentType.startsWith("multipart/")) {
            try {
                for (Part part : request.getParts()) {
                    if (part.getSubmittedFileName() == null) {
                        continue;
                    }
                    byte[] bytes;
                    try (InputStream is = part.getInputStream()) {
                        bytes = is.readAllBytes();
                    }
                    if (bytes.length == 0 && part.getSubmittedFileName().isEmpty()) {
                        continue; // empty <input type=file>
                    }
                    String name = part.getName();
                    String base = name.contains("[") ? name.substring(0, name.indexOf('[')) : name;
                    files.computeIfAbsent(base, k -> new ArrayList<>())
                            .add(new UploadedFile(name, part.getSubmittedFileName(), part.getContentType(), bytes));
                }
            } catch (Exception e) {
                throw new IllegalStateException("Cannot read multipart request", e);
            }
        }
    }

    private static String clean(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    @SuppressWarnings("unchecked")
    private static Object cleanDeep(Object v) {
        if (v instanceof String s) {
            return clean(s);
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), cleanDeep(e.getValue()));
            }
            return out;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) {
                out.add(cleanDeep(o));
            }
            return out;
        }
        return v;
    }

    /** Stores a value under a PHP style name: a, a[], a[b], a[0][c] ... */
    @SuppressWarnings("unchecked")
    private void put(String name, Object value) {
        int br = name.indexOf('[');
        if (br < 0) {
            data.put(name, value);
            return;
        }
        List<String> keys = new ArrayList<>();
        keys.add(name.substring(0, br));
        String rest = name.substring(br);
        while (rest.startsWith("[")) {
            int end = rest.indexOf(']');
            if (end < 0) {
                break;
            }
            keys.add(rest.substring(1, end));
            rest = rest.substring(end + 1);
        }
        Object container = data;
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            boolean last = i == keys.size() - 1;
            String nextKey = last ? null : keys.get(i + 1);
            Object child;
            if (container instanceof Map<?, ?>) {
                Map<String, Object> map = (Map<String, Object>) container;
                if (last) {
                    map.put(k, value);
                    return;
                }
                child = map.get(k);
                if (child == null) {
                    child = "".equals(nextKey) ? new ArrayList<>() : new LinkedHashMap<String, Object>();
                    map.put(k, child);
                }
            } else {
                List<Object> list = (List<Object>) container;
                if (last) {
                    list.add(value);
                    return;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                list.add(m);
                child = m;
            }
            container = child;
        }
    }

    // ------------------------------------------------------------------ access

    public Object raw(String key) {
        return data.get(key);
    }

    /** String value (arrays/objects are not returned). */
    public String get(String key) {
        Object v = data.get(key);
        if (v == null || v instanceof Map || v instanceof List) {
            return null;
        }
        if (v instanceof Double d && d == Math.rint(d)) {
            return String.valueOf(d.longValue());
        }
        return String.valueOf(v);
    }

    public String get(String key, String def) {
        String v = get(key);
        return v == null ? def : v;
    }

    public Long lng(String key) {
        return Values.toLong(data.get(key));
    }

    public Double dbl(String key) {
        return Values.toDouble(data.get(key));
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    /** Present and not empty (Laravel's filled()). */
    public boolean filled(String key) {
        Object v = data.get(key);
        return v != null && !(v instanceof String s && s.isEmpty())
                && !(v instanceof List<?> l && l.isEmpty()) && !(v instanceof Map<?, ?> m && m.isEmpty());
    }

    public boolean bool(String key) {
        Object v = data.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return v != null && Set.of("1", "true", "on", "yes").contains(String.valueOf(v).toLowerCase());
    }

    /** Values of an array field (genre[] / JSON array) as strings. */
    public List<String> array(String key) {
        Object v = data.get(key);
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        } else if (v instanceof Map<?, ?> m) {
            for (Object o : m.values()) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        } else if (v != null) {
            out.add(String.valueOf(v));
        }
        return out;
    }

    /** Nested structure (e.g. video[0][title]) as a list of maps. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> list(String key) {
        Object v = data.get(key);
        List<Map<String, Object>> out = new ArrayList<>();
        Iterable<?> items = v instanceof Map<?, ?> m ? m.values() : v instanceof List<?> l ? l : Collections.emptyList();
        for (Object o : items) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    /** A nested object value (e.g. JSON object or a[b] fields). */
    @SuppressWarnings("unchecked")
    public Map<String, Object> map(String key) {
        Object v = data.get(key);
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    public UploadedFile file(String key) {
        List<UploadedFile> l = files.get(key);
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    public List<UploadedFile> files(String key) {
        return files.getOrDefault(key, Collections.emptyList());
    }

    public boolean hasFile(String key) {
        return file(key) != null;
    }

    /** All non-file input. */
    public Map<String, Object> all() {
        return new LinkedHashMap<>(data);
    }

    public Map<String, Object> except(String... keys) {
        Map<String, Object> m = all();
        for (String k : keys) {
            m.remove(k);
        }
        return m;
    }
}
