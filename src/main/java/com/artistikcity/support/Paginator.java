package com.artistikcity.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JSON produced by Laravel's paginate() / simplePaginate() so the React pagination
 * components receive the same structure (data, links, current_page, ...).
 */
public final class Paginator {

    private Paginator() {
    }

    /** LengthAwarePaginator::toArray(). {@code path} is the URL without query string. */
    public static Map<String, Object> paginate(List<?> pageItems, long total, int perPage, int page, String path) {
        int lastPage = (int) Math.max(1, (total + perPage - 1) / perPage);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("current_page", page);
        m.put("data", pageItems);
        m.put("first_page_url", url(path, 1));
        m.put("from", pageItems.isEmpty() ? null : (page - 1) * perPage + 1);
        m.put("last_page", lastPage);
        m.put("last_page_url", url(path, lastPage));
        List<Map<String, Object>> links = new ArrayList<>();
        links.add(link(page > 1 ? url(path, page - 1) : null, "&laquo; Previous", false));
        for (int i = 1; i <= lastPage; i++) {
            links.add(link(url(path, i), String.valueOf(i), i == page));
        }
        links.add(link(page < lastPage ? url(path, page + 1) : null, "Next &raquo;", false));
        m.put("links", links);
        m.put("next_page_url", page < lastPage ? url(path, page + 1) : null);
        m.put("path", path);
        m.put("per_page", perPage);
        m.put("prev_page_url", page > 1 ? url(path, page - 1) : null);
        m.put("to", pageItems.isEmpty() ? null : (page - 1) * perPage + pageItems.size());
        m.put("total", total);
        return m;
    }

    /** Paginator (simplePaginate) ::toArray(). {@code hasMore} tells whether a next page exists. */
    public static Map<String, Object> simple(List<?> pageItems, boolean hasMore, int perPage, int page, String path) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("current_page", page);
        m.put("data", pageItems);
        m.put("first_page_url", url(path, 1));
        m.put("from", pageItems.isEmpty() ? null : (page - 1) * perPage + 1);
        m.put("next_page_url", hasMore ? url(path, page + 1) : null);
        m.put("path", path);
        m.put("per_page", perPage);
        m.put("prev_page_url", page > 1 ? url(path, page - 1) : null);
        m.put("to", pageItems.isEmpty() ? null : (page - 1) * perPage + pageItems.size());
        return m;
    }

    /** Current page from the ?page= query parameter. */
    public static int page(String value) {
        Long l = Values.toLong(value);
        return l == null || l < 1 ? 1 : l.intValue();
    }

    private static String url(String path, int page) {
        return path + (path.contains("?") ? "&" : "?") + "page=" + page;
    }

    private static Map<String, Object> link(String url, String label, boolean active) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("url", url);
        l.put("label", label);
        l.put("active", active);
        return l;
    }

    /** Laravel's {{ $paginator->links() }} (Bootstrap 4 markup) used by the admin tables. */
    public static String links(long total, int perPage, int page, String path) {
        int last = (int) Math.max(1, (total + perPage - 1) / perPage);
        if (last <= 1) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<nav><ul class=\"pagination\" role=\"navigation\">");
        sb.append(page > 1 ? item(url(path, page - 1), "&lsaquo;", false) : disabled("&lsaquo;"));
        for (int i = 1; i <= last; i++) {
            sb.append(i == page ? "<li class=\"page-item active\" aria-current=\"page\"><span class=\"page-link\">" + i + "</span></li>"
                    : item(url(path, i), String.valueOf(i), false));
        }
        sb.append(page < last ? item(url(path, page + 1), "&rsaquo;", false) : disabled("&rsaquo;"));
        return sb.append("</ul></nav>").toString();
    }

    private static String item(String url, String label, boolean active) {
        return "<li class=\"page-item\"><a class=\"page-link\" href=\"" + url + "\">" + label + "</a></li>";
    }

    private static String disabled(String label) {
        return "<li class=\"page-item disabled\" aria-disabled=\"true\"><span class=\"page-link\">" + label + "</span></li>";
    }
}
