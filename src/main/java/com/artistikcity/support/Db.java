package com.artistikcity.support;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Thin JDBC helper (the Java replacement for Laravel's query builder / Eloquent calls).
 *
 * All SQL in the application is written in the common subset understood by Microsoft SQL Server
 * and by H2 in MSSQLServer mode (used for the "dev" profile): TOP n, OFFSET/FETCH, [bracketed]
 * reserved words, no vendor specific functions.
 *
 * Rows come back as {@link Row} maps with lower-case column names. Temporal values are converted
 * to strings the way Laravel serialises them ("yyyy-MM-dd HH:mm:ss" / "yyyy-MM-dd") so that the
 * React pages receive exactly the same JSON as before.
 */
@Component
public class Db {

    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DataSource dataSource;

    public Db(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ------------------------------------------------------------------ reads

    public List<Row> select(String sql, Object... params) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        } catch (SQLException e) {
            throw new DbException(sql, e);
        }
    }

    /** First row or null. */
    public Row first(String sql, Object... params) {
        List<Row> rows = select(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** First column of the first row, or null. */
    public Object scalar(String sql, Object... params) {
        Row r = first(sql, params);
        if (r == null || r.isEmpty()) {
            return null;
        }
        return r.values().iterator().next();
    }

    public long count(String sql, Object... params) {
        return Values.toLong(scalar(sql, params), 0);
    }

    public Row find(String table, Object id) {
        return first("select * from " + table + " where id = ?", id);
    }

    public List<Row> all(String table) {
        return select("select * from " + table + " order by id");
    }

    // ----------------------------------------------------------------- writes

    public int update(String sql, Object... params) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException(sql, e);
        }
    }

    /**
     * Insert a row and return the generated id. created_at / updated_at are filled in
     * automatically (like Eloquent timestamps) unless they are present in {@code values}.
     */
    public long insert(String table, Map<String, ?> values) {
        Row v = new Row(values);
        LocalDateTime now = LocalDateTime.now();
        v.putIfAbsent("created_at", now);
        v.putIfAbsent("updated_at", now);
        StringJoiner cols = new StringJoiner(", ");
        StringJoiner marks = new StringJoiner(", ");
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> e : v.entrySet()) {
            cols.add(quote(e.getKey()));
            marks.add("?");
            params.add(e.getValue());
        }
        String sql = "insert into " + table + " (" + cols + ") values (" + marks + ")";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params.toArray());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            throw new DbException(sql, e);
        }
    }

    /** Insert without timestamps / generated key (pivot tables). */
    public void insertPlain(String table, Map<String, ?> values) {
        StringJoiner cols = new StringJoiner(", ");
        StringJoiner marks = new StringJoiner(", ");
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, ?> e : values.entrySet()) {
            cols.add(quote(e.getKey()));
            marks.add("?");
            params.add(e.getValue());
        }
        update("insert into " + table + " (" + cols + ") values (" + marks + ")", params.toArray());
    }

    /** Update columns of one row by id; updated_at is refreshed automatically. */
    public int updateById(String table, Object id, Map<String, ?> values) {
        return updateWhere(table, values, "id = ?", id);
    }

    public int updateWhere(String table, Map<String, ?> values, String where, Object... whereParams) {
        Row v = new Row(values);
        v.putIfAbsent("updated_at", LocalDateTime.now());
        StringJoiner sets = new StringJoiner(", ");
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> e : v.entrySet()) {
            sets.add(quote(e.getKey()) + " = ?");
            params.add(e.getValue());
        }
        for (Object p : whereParams) {
            params.add(p);
        }
        return update("update " + table + " set " + sets + " where " + where, params.toArray());
    }

    public int deleteById(String table, Object id) {
        return update("delete from " + table + " where id = ?", id);
    }

    /** "?, ?, ?" for an IN (...) list. */
    public static String marks(int n) {
        StringJoiner j = new StringJoiner(", ");
        for (int i = 0; i < n; i++) {
            j.add("?");
        }
        return j.length() == 0 ? "null" : j.toString();
    }

    // ---------------------------------------------------------------- helpers

    private static final java.util.Set<String> RESERVED = java.util.Set.of("primary", "like", "key", "order", "user");

    private static String quote(String column) {
        return RESERVED.contains(column.toLowerCase()) ? "[" + column + "]" : column;
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p instanceof Boolean b) {
                p = b ? 1 : 0;
            } else if (p instanceof LocalDateTime ldt) {
                p = Timestamp.valueOf(ldt);
            } else if (p instanceof LocalDate ld) {
                p = java.sql.Date.valueOf(ld);
            } else if (p instanceof Collection<?> || p instanceof Map<?, ?>) {
                throw new IllegalArgumentException("Cannot bind a collection as parameter " + (i + 1));
            }
            ps.setObject(i + 1, p);
        }
    }

    private static List<Row> readAll(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        String[] labels = new String[n];
        for (int i = 0; i < n; i++) {
            labels[i] = md.getColumnLabel(i + 1).toLowerCase();
        }
        List<Row> rows = new ArrayList<>();
        while (rs.next()) {
            Row r = new Row();
            for (int i = 0; i < n; i++) {
                r.put(labels[i], convert(rs.getObject(i + 1)));
            }
            rows.add(r);
        }
        return rows;
    }

    private static Object convert(Object v) throws SQLException {
        if (v == null) {
            return null;
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(DATE_TIME);
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.format(DATE_TIME);
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (v instanceof LocalDate ld) {
            return ld.toString();
        }
        if (v instanceof java.time.OffsetDateTime odt) {
            return odt.toLocalDateTime().format(DATE_TIME);
        }
        if (v instanceof Clob clob) {
            return clob.getSubString(1, (int) clob.length());
        }
        if (v instanceof Byte || v instanceof Short) {
            return ((Number) v).intValue();
        }
        if (v instanceof java.math.BigDecimal bd) {
            return bd.scale() <= 0 ? (Object) bd.longValue() : (Object) bd.doubleValue();
        }
        return v;
    }

    /** Unchecked wrapper so controllers don't have to deal with SQLException. */
    public static class DbException extends RuntimeException {
        public DbException(String sql, SQLException cause) {
            super(cause.getMessage() + " [SQL: " + sql + "]", cause);
        }
    }
}
