package com.artistikcity.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window rate limiter for authentication endpoints (login, OTP, magic links, passkeys).
 * <p>
 * Each key (for example {@code "login:ip:1.2.3.4"} or {@code "otp:phone:+9198..."}) keeps the timestamps of
 * its recent hits. {@link #hit} records an attempt and tells whether it is still within the limit.
 * <p>
 * This is per application instance and in memory. SMS sending is additionally limited from the
 * {@code auth_otps} table, so those limits survive restarts and hold across instances. To scale out
 * horizontally, back this class with Redis (INCR + EXPIRE) without changing its callers.
 */
@Component
public class RateLimiter {

    /** Result of a check: allowed, or blocked with the number of seconds until the oldest hit expires. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /** Records one attempt for {@code key}; at most {@code limit} attempts per {@code windowSeconds}. */
    public Decision hit(String key, int limit, long windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000;
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() >= windowMs) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                long retry = Math.max(1, (windowMs - (now - q.peekFirst()) + 999) / 1000);
                return new Decision(false, retry);
            }
            q.addLast(now);
            return new Decision(true, 0);
        }
    }

    /** Checks without recording (used to block before doing expensive work). */
    public Decision peek(String key, int limit, long windowSeconds) {
        Deque<Long> q = hits.get(key);
        if (q == null) {
            return new Decision(true, 0);
        }
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000;
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() >= windowMs) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                return new Decision(false, Math.max(1, (windowMs - (now - q.peekFirst()) + 999) / 1000));
            }
            return new Decision(true, 0);
        }
    }

    /** Clears a key, e.g. failed-login counters after a successful login. */
    public void reset(String key) {
        hits.remove(key);
    }

    /** Drops idle keys so the map does not grow without bound. */
    @Scheduled(fixedDelay = 600_000)
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - 24 * 3600_000L;
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast() < cutoff;
            }
        });
    }
}
