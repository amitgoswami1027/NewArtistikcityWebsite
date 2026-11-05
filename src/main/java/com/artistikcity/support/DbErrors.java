package com.artistikcity.support;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Recognises database errors that mean "another request got there first", whatever wraps them.
 *
 * <p>MySQL Connector/J 8 can throw its own unchecked {@code com.mysql.cj.exceptions.CJException} straight through
 * {@code JdbcTemplate} (seen for deadlock victims under SERIALIZABLE), so Spring never turns it into a
 * {@link ConcurrencyFailureException}. Code that turns a lost race into a clean 409 or a retry must ask here instead of
 * catching only Spring's types.</p>
 */
public final class DbErrors {

    private DbErrors() {
    }

    /** Deadlock victim (1213), lock wait timeout (1205) or serialization failure (SQLSTATE 40001). */
    public static boolean isConcurrencyConflict(Throwable t) {
        for (Throwable c : chain(t)) {
            if (c instanceof ConcurrencyFailureException) return true;
            String state = sqlState(c);
            int code = vendorCode(c);
            if ("40001".equals(state) || code == 1213 || code == 1205) return true;
        }
        return false;
    }

    /** A UNIQUE / primary key was hit (1062, SQLSTATE 23000 with that code). */
    public static boolean isDuplicateKey(Throwable t) {
        for (Throwable c : chain(t)) {
            if (c instanceof org.springframework.dao.DuplicateKeyException) return true;
            if (vendorCode(c) == 1062) return true;
            if (c instanceof DataIntegrityViolationException && c.getMessage() != null && c.getMessage().contains("Duplicate entry")) return true;
        }
        return false;
    }

    private static Iterable<Throwable> chain(Throwable t) {
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        java.util.List<Throwable> out = new java.util.ArrayList<>();
        for (Throwable c = t; c != null && !seen.containsKey(c); c = c.getCause()) {
            seen.put(c, true);
            out.add(c);
        }
        return out;
    }

    private static String sqlState(Throwable t) {
        if (t instanceof SQLException s) return s.getSQLState();
        Object v = call(t, "getSQLState");
        return v == null ? null : String.valueOf(v);
    }

    private static int vendorCode(Throwable t) {
        if (t instanceof SQLException s) return s.getErrorCode();
        Object v = call(t, "getVendorCode");
        return v instanceof Number n ? n.intValue() : 0;
    }

    /** The driver is a runtime-only dependency, so its exception type is read by reflection. */
    private static Object call(Throwable t, String method) {
        try {
            Method m = t.getClass().getMethod(method);
            return m.invoke(t);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
