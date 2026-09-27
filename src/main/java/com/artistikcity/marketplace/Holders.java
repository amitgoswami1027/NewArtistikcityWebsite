package com.artistikcity.marketplace;

import com.artistikcity.http.Auth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Who is holding a painting. A signed-in collector is "U:{userId}"; a guest is "S:{random token}" kept in
 * the HTTP session. Both keys count as "you", so a hold made as a guest survives signing in mid-checkout.
 */
public final class Holders {

    private static final String SESSION_KEY = "marketplace.holder";

    private Holders() {
    }

    /** The key new holds are stored under. Creates the guest token (and session) on first use. */
    public static String primary(HttpServletRequest request, Auth auth) {
        Long userId = auth.userId(request);
        return userId != null ? "U:" + userId : "S:" + guestToken(request, true);
    }

    /** Every key that identifies the current visitor (never empty once a session exists). */
    public static List<String> all(HttpServletRequest request, Auth auth) {
        List<String> keys = new ArrayList<>(2);
        Long userId = auth.userId(request);
        if (userId != null) keys.add("U:" + userId);
        String token = guestToken(request, false);
        if (token != null) keys.add("S:" + token);
        return keys;
    }

    private static String guestToken(HttpServletRequest request, boolean create) {
        HttpSession s = request.getSession(create);
        if (s == null) return null;
        Object t = s.getAttribute(SESSION_KEY);
        if (t == null && create) {
            t = UUID.randomUUID().toString();
            s.setAttribute(SESSION_KEY, t);
        }
        return t == null ? null : String.valueOf(t);
    }
}
