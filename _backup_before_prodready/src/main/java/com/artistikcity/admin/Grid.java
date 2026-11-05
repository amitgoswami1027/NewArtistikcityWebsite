package com.artistikcity.admin;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Server-side data table query: search, filters, sorting and pagination from the request's query string
 * ({@code q, sort, dir, page, per} plus named filters). Column names never come from the request:
 * sort keys map to whitelisted SQL expressions and filters are bound parameters.
 *
 * <pre>
 * Map&lt;String, Object&gt; grid = Grid.from(db, request)
 *     .columns("o.*, u.name as student")
 *     .from("orders o left join users u on u.id = o.user_id")
 *     .search("o.order_number", "u.name", "u.email")
 *     .sort("date", "o.created_at").sort("amount", "cast(o.price as decimal(12,2))")
 *     .defaultSort("date", "desc")
 *     .filter("status", v -&gt; Grid.where("o.payment_status = ?", v))
 *     .page();
 * </pre>
 * The result is {@code {rows, meta: {page, perPage, total, pages, sort, dir, q, filters}}}, the shape
 * the console's DataTable component expects.
 */
public final class Grid {

    public record Clause(String sql, List<Object> params) {
    }

    public static Clause where(String sql, Object... params) {
        return new Clause(sql, List.of(params));
    }

    private static final Set<Integer> PER_PAGE = Set.of(10, 20, 50, 100);

    private final Db db;
    private final HttpServletRequest request;
    private String columns = "*";
    private String from;
    private final List<String> searchCols = new ArrayList<>();
    private final Map<String, String> sorts = new LinkedHashMap<>();
    private final Map<String, Function<String, Clause>> filters = new LinkedHashMap<>();
    private final List<Clause> fixed = new ArrayList<>();
    private String defaultSort;
    private String defaultDir = "desc";
    private String tieBreak;
    private int defaultPer = 20;
    private Function<Row, Row> map = r -> r;

    private Grid(Db db, HttpServletRequest request) {
        this.db = db;
        this.request = request;
    }

    public static Grid from(Db db, HttpServletRequest request) {
        return new Grid(db, request);
    }

    public Grid columns(String sql) {
        this.columns = sql;
        return this;
    }

    public Grid from(String sql) {
        this.from = sql;
        return this;
    }

    public Grid search(String... cols) {
        searchCols.addAll(List.of(cols));
        return this;
    }

    public Grid sort(String key, String expression) {
        sorts.put(key, expression);
        return this;
    }

    public Grid defaultSort(String key, String dir) {
        this.defaultSort = key;
        this.defaultDir = dir;
        return this;
    }

    /** Stable order for rows with equal sort values (e.g. "o.id desc"). */
    public Grid tieBreak(String orderBy) {
        this.tieBreak = orderBy;
        return this;
    }

    /** A named filter from the query string; ignored when empty or when the function returns null. */
    public Grid filter(String name, Function<String, Clause> clause) {
        filters.put(name, clause);
        return this;
    }

    /** A condition that always applies (scope). */
    public Grid where(Clause clause) {
        if (clause != null) {
            fixed.add(clause);
        }
        return this;
    }

    public Grid perPage(int n) {
        this.defaultPer = n;
        return this;
    }

    public Grid map(Function<Row, Row> f) {
        this.map = f;
        return this;
    }

    private String param(String name) {
        String v = request.getParameter(name);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private record Built(String where, List<Object> params, Map<String, String> active) {
    }

    private Built build() {
        List<String> parts = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        Map<String, String> active = new LinkedHashMap<>();
        for (Clause c : fixed) {
            parts.add("(" + c.sql() + ")");
            params.addAll(c.params());
        }
        String q = param("q");
        if (q != null && !searchCols.isEmpty()) {
            String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            List<String> ors = new ArrayList<>();
            for (String col : searchCols) {
                ors.add(col + " like ?");
                params.add(like);
            }
            parts.add("(" + String.join(" or ", ors) + ")");
        }
        for (Map.Entry<String, Function<String, Clause>> f : filters.entrySet()) {
            String v = param(f.getKey());
            if (v == null) {
                continue;
            }
            Clause c = f.getValue().apply(v);
            if (c != null) {
                parts.add("(" + c.sql() + ")");
                params.addAll(c.params());
                active.put(f.getKey(), v);
            }
        }
        return new Built(parts.isEmpty() ? "" : " where " + String.join(" and ", parts), params, active);
    }

    private String orderBy(String sortKey, String dir) {
        String expr = sorts.get(sortKey);
        return expr == null ? (tieBreak == null ? "" : " order by " + tieBreak)
                : " order by " + expr + " " + dir + (tieBreak == null ? "" : ", " + tieBreak);
    }

    /** One page of rows plus the paging / sorting state. */
    public Map<String, Object> page() {
        Built b = build();
        String sortKey = sorts.containsKey(param("sort")) ? param("sort") : defaultSort;
        String dir = "asc".equalsIgnoreCase(param("dir")) ? "asc" : "desc".equalsIgnoreCase(param("dir")) ? "desc" : defaultDir;
        int per = defaultPer;
        try {
            int asked = Integer.parseInt(param("per") == null ? "" : param("per"));
            if (PER_PAGE.contains(asked)) {
                per = asked;
            }
        } catch (NumberFormatException ignored) {
            // default page size
        }
        long total = db.count("select count(*) from " + from + b.where(), b.params().toArray());
        int pages = (int) Math.max(1, (total + per - 1) / per);
        int page = 1;
        try {
            page = Math.max(1, Integer.parseInt(param("page") == null ? "1" : param("page")));
        } catch (NumberFormatException ignored) {
            // first page
        }
        page = Math.min(page, pages);
        List<Object> params = new ArrayList<>(b.params());
        params.add(per);
        params.add((page - 1) * per);
        List<Row> rows = new ArrayList<>();
        for (Row r : db.select("select " + columns + " from " + from + b.where() + orderBy(sortKey, dir) + " limit ? offset ?", params.toArray())) {
            rows.add(map.apply(r));
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", page);
        meta.put("perPage", per);
        meta.put("total", total);
        meta.put("pages", pages);
        meta.put("sort", sortKey);
        meta.put("dir", dir);
        meta.put("q", param("q") == null ? "" : param("q"));
        meta.put("filters", b.active());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rows", rows);
        out.put("meta", meta);
        return out;
    }

    /** Every matching row (for CSV export), capped at {@code limit}. */
    public List<Row> all(int limit) {
        Built b = build();
        String sortKey = sorts.containsKey(param("sort")) ? param("sort") : defaultSort;
        String dir = "asc".equalsIgnoreCase(param("dir")) ? "asc" : "desc".equalsIgnoreCase(param("dir")) ? "desc" : defaultDir;
        List<Object> params = new ArrayList<>(b.params());
        params.add(limit);
        List<Row> rows = new ArrayList<>();
        for (Row r : db.select("select " + columns + " from " + from + b.where() + orderBy(sortKey, dir) + " limit ?", params.toArray())) {
            rows.add(map.apply(r));
        }
        return rows;
    }
}
