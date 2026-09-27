package com.artistikcity.support;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/** Small conversion helpers that mimic PHP's loose typing where the original code relied on it. */
public final class Values {

    private Values() {
    }

    public static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1L : 0L;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static long toLong(Object v, long def) {
        Long l = toLong(v);
        return l == null ? def : l;
    }

    public static Double toDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** PHP truthiness. */
    public static boolean truthy(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.doubleValue() != 0;
        }
        if (v instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (v instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        String s = String.valueOf(v);
        return !s.isEmpty() && !"0".equals(s);
    }

    public static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    public static String strOr(Object v, String def) {
        return v == null ? def : String.valueOf(v);
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Loose equality used by templates, e.g. status == 1 where status may be "1" or 1. */
    public static boolean looseEquals(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a instanceof Number || b instanceof Number) {
            Double x = toDouble(a);
            Double y = toDouble(b);
            return x != null && x.equals(y);
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }
}
