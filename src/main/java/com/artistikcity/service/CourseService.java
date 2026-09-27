package com.artistikcity.service;

import com.artistikcity.support.Db;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Values;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Course queries and the JSON shapes produced by the Laravel resources:
 * {@code CourseCollection} ({"data": [...]}) and {@code CourseResource} ({"data": {...}, "status": "success"}).
 * Relations (medium, teacher, genre, course_type, skill, category, photos, prices) are loaded in batches,
 * like Eloquent's {@code with()}.
 */
@Service
public class CourseService {

    public static final int TYPE_COURSE = 1;
    public static final int TYPE_WORKSHOP = 2;

    private final Db db;
    private final Json json;

    public CourseService(Db db, Json json) {
        this.db = db;
        this.json = json;
    }

    /** Upcoming (start date today or later) courses of the given types, newest first. */
    public List<Row> upcoming(int limit, Integer... types) {
        StringBuilder sql = new StringBuilder("select top " + limit + " * from courses where course_start_date >= ?");
        List<Object> params = new ArrayList<>();
        params.add(LocalDate.now());
        if (types.length > 0) {
            sql.append(" and course_type_id in (").append(Db.marks(types.length)).append(")");
            params.addAll(List.of(types));
        }
        sql.append(" order by id");
        return db.select(sql.toString(), params.toArray());
    }

    /** Filtered catalogue used by /courses (status = 1, upcoming, type slug and optional filters). */
    public List<Row> catalogue(String typeSlug, Long mediumId, Long genreId, Long skillId, Long categoryId) {
        StringBuilder sql = new StringBuilder(
                "select c.* from courses c join course_types ct on ct.id = c.course_type_id"
                        + " join mediums m on m.id = c.medium_id join skills s on s.id = c.skill_id"
                        + " where c.status = 1 and c.course_start_date >= ?");
        List<Object> params = new ArrayList<>();
        params.add(LocalDate.now());
        if (typeSlug != null && !typeSlug.isEmpty()) {
            sql.append(" and ct.course_type_slug = ?");
            params.add(typeSlug);
        }
        if (mediumId != null) {
            sql.append(" and c.medium_id = ?");
            params.add(mediumId);
        }
        if (skillId != null) {
            sql.append(" and c.skill_id = ?");
            params.add(skillId);
        }
        if (categoryId != null) {
            sql.append(" and c.category_id = ?");
            params.add(categoryId);
        }
        sql.append(" order by c.id");
        List<Row> rows = db.select(sql.toString(), params.toArray());
        if (genreId != null) {
            rows.removeIf(r -> !genreIds(r.str("genre_id")).contains(genreId));
        }
        return rows;
    }

    public static List<Long> genreIds(String csv) {
        List<Long> ids = new ArrayList<>();
        if (csv == null) {
            return ids;
        }
        for (String p : csv.split(",")) {
            Long l = Values.toLong(p.trim());
            if (l != null) {
                ids.add(l);
            }
        }
        return ids;
    }

    /**
     * Attaches the relations to each course row (in place).
     *
     * @param firstPhotoOnly Laravel code did {@code $query->photos->take(1)}
     */
    public List<Row> withRelations(List<Row> courses, boolean firstPhotoOnly) {
        if (courses.isEmpty()) {
            return courses;
        }
        Map<Long, Row> mediums = byId(db.select("select * from mediums"));
        Map<Long, Row> genres = byId(db.select("select * from genres"));
        Map<Long, Row> types = byId(db.select("select * from course_types"));
        Map<Long, Row> skills = byId(db.select("select * from skills"));
        Map<Long, Row> categories = byId(db.select("select * from categories"));
        Map<Long, Row> teachers = new HashMap<>();
        for (Row a : db.select("select * from admins")) {
            teachers.put(a.lng("id"), a.without("password", "remember_token"));
        }
        List<Object> ids = new ArrayList<>();
        for (Row c : courses) {
            ids.add(c.lng("id"));
        }
        Map<Long, List<Row>> photos = groupBy(db.select(
                "select * from course_photos where course_id in (" + Db.marks(ids.size()) + ") order by id", ids.toArray()), "course_id");
        Map<Long, List<Row>> prices = groupBy(db.select(
                "select * from course_prices where course_id in (" + Db.marks(ids.size()) + ") order by id", ids.toArray()), "course_id");
        for (Row c : courses) {
            long id = c.lng("id");
            c.put("medium", mediums.get(c.lng("medium_id")));
            c.put("teacher", teachers.get(c.lng("admin_id")));
            List<Long> g = genreIds(c.str("genre_id"));
            c.put("genre", g.isEmpty() ? null : genres.get(g.get(0)));
            c.put("course_type", types.get(c.lng("course_type_id")));
            c.put("skill", skills.get(c.lng("skill_id")));
            c.put("category", categories.get(c.lng("category_id")));
            List<Row> p = photos.getOrDefault(id, new ArrayList<>());
            c.put("photos", firstPhotoOnly && p.size() > 1 ? new ArrayList<>(p.subList(0, 1)) : p);
            c.put("prices", pricesOrDefault(prices.get(id)));
        }
        return courses;
    }

    /** Guarantees prices[0] exists (the React pages read prices[0].price_inr unconditionally). */
    private static List<Row> pricesOrDefault(List<Row> p) {
        if (p != null && !p.isEmpty()) {
            // the admin "update" action inserts a new price row each time; the newest is the current price
            List<Row> sorted = new ArrayList<>(p);
            java.util.Collections.reverse(sorted);
            return sorted;
        }
        List<Row> def = new ArrayList<>();
        def.add(Row.of("price_inr", 0, "price_usd", 0));
        return def;
    }

    public Row withRelations(Row course, boolean firstPhotoOnly) {
        List<Row> l = new ArrayList<>();
        l.add(course);
        withRelations(l, firstPhotoOnly);
        return course;
    }

    /**
     * Adds catalogue statistics to each course row: "students" (distinct paying students),
     * "reviews" (approved review count) and "positive" (% of reviews rated 4 or 5).
     */
    public List<Row> withStats(List<Row> courses) {
        if (courses.isEmpty()) {
            return courses;
        }
        Map<Long, Long> students = new java.util.HashMap<>();
        for (Row r : db.select("select course_id, count(distinct user_id) as c from orders where payment_status = 1 group by course_id")) {
            students.put(r.lng("course_id"), r.lng("c", 0));
        }
        Map<Long, long[]> reviews = new java.util.HashMap<>();
        for (Row r : db.select("select course_id, count(*) as c, sum(case when rating >= 4 then 1 else 0 end) as p"
                + " from course_reviews where status = 1 group by course_id")) {
            reviews.put(r.lng("course_id"), new long[]{r.lng("c", 0), r.lng("p", 0)});
        }
        for (Row c : courses) {
            Long id = c.lng("id");
            long[] rv = reviews.getOrDefault(id, new long[]{0, 0});
            c.put("students", students.getOrDefault(id, 0L));
            c.put("reviews", rv[0]);
            c.put("positive", rv[0] == 0 ? null : Math.round(rv[1] * 100.0 / rv[0]));
        }
        return courses;
    }

    /** new CourseCollection($courses). */
    public Map<String, Object> collection(List<Row> courses) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", courses);
        return m;
    }

    /** new CourseResource($course) as serialised by Inertia: {"data": {...}, "status": "success"}. */
    public Map<String, Object> resource(Row c) {
        Row d = new Row();
        d.put("id", c.get("id"));
        d.put("title", c.get("title"));
        d.put("medium_id", c.get("medium_id"));
        d.put("genre_id", c.get("genre_id"));
        d.put("slug", c.get("slug"));
        d.put("sub_title", c.get("sub_title"));
        d.put("introduction", c.get("introduction"));
        d.put("introduction_details", c.get("introduction_details"));
        d.put("summary", c.get("summary"));
        d.put("summary_details", c.get("summary_details"));
        d.put("schedule", c.get("schedule"));
        d.put("schedule_details", c.get("schedule_details"));
        d.put("age_group", c.get("age_group"));
        d.put("duration", c.get("duration"));
        d.put("sessions", c.get("sessions"));
        d.put("mini_projects", c.get("mini_projects"));
        d.put("course_modules", c.get("course_modules"));
        d.put("time_required", c.get("time_required"));
        d.put("course_includes", jsonOrEmpty(c.str("course_includes")));
        d.put("course_highlights", jsonOrEmpty(c.str("course_highlights")));
        d.put("course_for", jsonOrEmpty(c.str("course_for")));
        d.put("course_not_for", jsonOrEmpty(c.str("course_not_for")));
        d.put("course_problems_solved", jsonOrEmpty(c.str("course_problems_solved")));
        d.put("course_learn", jsonOrEmpty(c.str("course_learn")));
        d.put("course_outcome", c.get("course_outcome"));
        d.put("course_deliverables", jsonOrEmpty(c.str("course_deliverables")));
        d.put("course_prerequisites", c.get("course_prerequisites"));
        d.put("course_start_date", formatStartDate(c.str("course_start_date")));
        d.put("medium", orEmpty(c.get("medium")));
        d.put("teacher", orEmpty(c.get("teacher")));
        d.put("prices", c.get("prices"));
        d.put("genre", c.get("genre"));
        d.put("course_type", c.get("course_type"));
        d.put("skill", orEmpty(c.get("skill")));
        d.put("category", c.get("category"));
        d.put("photos", c.get("photos"));
        d.put("status", c.get("status"));
        d.put("created_at", c.get("created_at"));
        d.put("updated_at", c.get("updated_at"));
        d.put("course_based", c.get("course_based"));
        d.put("course_type_id", c.get("course_type_id"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", d);
        m.put("status", "success");
        return m;
    }

    /** date('d M, Y', strtotime(...)). */
    public static String formatStartDate(String ymd) {
        if (ymd == null || ymd.length() < 10) {
            return ymd;
        }
        return LocalDate.parse(ymd.substring(0, 10)).format(DateTimeFormatter.ofPattern("dd MMM, yyyy", Locale.ENGLISH));
    }

    /**
     * json_decode(..., true) - the React page reads e.g. course_includes.certificate, so an empty object
     * is returned instead of null for missing values (null crashed the original page).
     */
    private Object jsonOrEmpty(String s) {
        Object o = json.decode(s);
        return o == null ? new LinkedHashMap<String, Object>() : o;
    }

    private static Object orEmpty(Object o) {
        return o == null ? new LinkedHashMap<String, Object>() : o;
    }

    public static Map<Long, Row> byId(List<Row> rows) {
        Map<Long, Row> m = new HashMap<>();
        for (Row r : rows) {
            m.put(r.lng("id"), r);
        }
        return m;
    }

    public static Map<Long, List<Row>> groupBy(List<Row> rows, String key) {
        Map<Long, List<Row>> m = new LinkedHashMap<>();
        for (Row r : rows) {
            m.computeIfAbsent(r.lng(key), k -> new ArrayList<>()).add(r);
        }
        return m;
    }

    /** First photo name of a course (the addSelect(['photo' => CoursePhoto::select('photo_name')...]) sub-query). */
    public static String photoSubquery(String courseIdColumn) {
        return "(select top 1 photo_name from course_photos cp where cp.course_id = " + courseIdColumn + " order by cp.id) as photo";
    }
}
