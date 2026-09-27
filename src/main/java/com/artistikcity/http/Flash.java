package com.artistikcity.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Session flash data (Laravel's {@code ->with('key', value)} / {@code session()->flash()}),
 * validation errors and old input. Values flashed during a request are available on the next
 * request only; {@link #age(HttpServletRequest)} is called at the start of every request.
 */
public final class Flash {

    private static final String NEXT = "_flash.next";
    private static final String NEXT_ERRORS = "_flash.errors";
    private static final String NEXT_OLD = "_flash.old";

    private static final String NOW = Flash.class.getName() + ".now";
    private static final String NOW_ERRORS = Flash.class.getName() + ".errors";
    private static final String NOW_OLD = Flash.class.getName() + ".old";

    private Flash() {
    }

    /** Moves data flashed by the previous request into this request. */
    @SuppressWarnings("unchecked")
    public static void age(HttpServletRequest request) {
        if (request.getAttribute(NOW) != null) {
            return; // already aged (forwarded request)
        }
        HttpSession session = request.getSession(true);
        Object next = session.getAttribute(NEXT);
        Object errors = session.getAttribute(NEXT_ERRORS);
        Object old = session.getAttribute(NEXT_OLD);
        session.removeAttribute(NEXT);
        session.removeAttribute(NEXT_ERRORS);
        session.removeAttribute(NEXT_OLD);
        request.setAttribute(NOW, next instanceof Map ? next : new HashMap<String, Object>());
        request.setAttribute(NOW_ERRORS, errors instanceof Map ? errors : new HashMap<String, String>());
        request.setAttribute(NOW_OLD, old instanceof Map ? old : new HashMap<String, Object>());
    }

    /** Flash a value for the next request. */
    @SuppressWarnings("unchecked")
    public static void put(HttpServletRequest request, String key, Object value) {
        HttpSession session = request.getSession(true);
        HashMap<String, Object> next = (HashMap<String, Object>) session.getAttribute(NEXT);
        if (next == null) {
            next = new HashMap<>();
        }
        next.put(key, value);
        session.setAttribute(NEXT, next);
    }

    /** Value flashed by the previous request (or flashed "now" in this one). */
    public static Object get(HttpServletRequest request, String key) {
        return current(request).get(key);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> current(HttpServletRequest request) {
        Object m = request.getAttribute(NOW);
        return m instanceof Map ? (Map<String, Object>) m : Collections.emptyMap();
    }

    /** Make a value visible to the current request only. */
    @SuppressWarnings("unchecked")
    public static void now(HttpServletRequest request, String key, Object value) {
        Object m = request.getAttribute(NOW);
        Map<String, Object> map = m instanceof Map ? new HashMap<>((Map<String, Object>) m) : new HashMap<>();
        map.put(key, value);
        request.setAttribute(NOW, map);
    }

    public static void putErrors(HttpServletRequest request, Map<String, String> errors) {
        request.getSession(true).setAttribute(NEXT_ERRORS, new HashMap<>(errors));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> errors(HttpServletRequest request) {
        Object m = request.getAttribute(NOW_ERRORS);
        return m instanceof Map ? (Map<String, String>) m : Collections.emptyMap();
    }

    public static void putOld(HttpServletRequest request, Map<String, Object> old) {
        HashMap<String, Object> copy = new HashMap<>();
        old.forEach((k, v) -> {
            if (!k.contains("password") && !"_token".equals(k) && (v == null || v instanceof String || v instanceof Number)) {
                copy.put(k, v);
            }
        });
        request.getSession(true).setAttribute(NEXT_OLD, copy);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> old(HttpServletRequest request) {
        Object m = request.getAttribute(NOW_OLD);
        return m instanceof Map ? (Map<String, Object>) m : Collections.emptyMap();
    }
}
