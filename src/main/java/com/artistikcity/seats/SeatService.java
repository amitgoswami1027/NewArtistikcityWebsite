package com.artistikcity.seats;

import com.artistikcity.config.Settings;
import com.artistikcity.security.AuditLog;
import com.artistikcity.service.Mailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seat caps for live courses and workshops ({@code courses.seat_limit}).
 *
 * <p>A seat is taken by a paid order, by a student who is paying right now (a 15-minute {@code seat_holds} row) and by
 * an open priority access link ({@code access_grants}, e.g. a waitlist invitation). Every decision about the last
 * seats runs in a transaction that first locks the course row ({@code SELECT ... FOR UPDATE}), so two students can
 * never both get the last seat: the loser gets {@link SoldOutException} (HTTP 409 {@code SOLD_OUT}).</p>
 *
 * <p>When a seat frees up (a hold expires, an invitation lapses, an order is refunded) the first student on the
 * waitlist gets a 24-hour priority link by e-mail.</p>
 */
@Service
public class SeatService {

    private static final Logger log = LoggerFactory.getLogger(SeatService.class);
    public static final int INVITE_HOURS = 24;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Settings settings;
    private final Mailer mailer;
    private final Environment env;
    private final AuditLog auditLog;

    public SeatService(JdbcTemplate jdbc, PlatformTransactionManager tm, Settings settings, Mailer mailer, Environment env, AuditLog auditLog) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(tm);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.settings = settings;
        this.mailer = mailer;
        this.env = env;
        this.auditLog = auditLog;
    }

    /** The course is full for this student. */
    public static class SoldOutException extends RuntimeException {
        private final long courseId;

        public SoldOutException(long courseId) {
            super("SOLD_OUT");
            this.courseId = courseId;
        }

        public long courseId() {
            return courseId;
        }
    }

    /** Seat state of one course; limit null = no cap. */
    public record Seats(Integer limit, int taken, Integer left, boolean soldOut) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("limit", limit);
            m.put("taken", taken);
            m.put("left", left);
            m.put("soldOut", soldOut);
            return m;
        }
    }

    /**
     * Seats taken, counted once per student: paid orders, held or paid seat rows that have not expired, and open
     * seat-keeping access links (a link sent to an e-mail without an account counts as its own seat).
     */
    static final String TAKEN = "select count(*) from ("
            + " select user_id as k from orders where course_id = ? and payment_status = 1"
            + " union select user_id from seat_holds where course_id = ? and (status = 'PAID' or (status = 'HELD' and expires_at > ?))"
            + " union select coalesce(user_id, -id) from access_grants where course_id = ? and holds_seat = 1 and used_at is null and revoked_at is null and expires_at > ?"
            + ") x where k <> ?";

    int taken(long courseId, long exceptUserId) {
        Timestamp now = ts(LocalDateTime.now());
        Integer n = jdbc.queryForObject(TAKEN, Integer.class, courseId, courseId, now, courseId, now, exceptUserId);
        return n == null ? 0 : n;
    }

    public Seats seats(long courseId) {
        List<Map<String, Object>> c = jdbc.queryForList("select seat_limit from courses where id = ?", courseId);
        Integer limit = c.isEmpty() || c.get(0).get("seat_limit") == null ? null : ((Number) c.get(0).get("seat_limit")).intValue();
        if (limit == null) return new Seats(null, 0, null, false);
        int taken = taken(courseId, 0);
        int left = Math.max(0, limit - taken);
        return new Seats(limit, taken, left, left == 0);
    }

    /** seat_limit / seats_left / sold_out on catalogue rows, for the badges (one query per capped course). */
    public void annotate(List<? extends Map<String, Object>> courses) {
        for (Map<String, Object> c : courses) {
            Object limit = c.get("seat_limit");
            if (!(limit instanceof Number) || c.get("id") == null) continue;
            Seats s = seats(((Number) c.get("id")).longValue());
            c.put("seats_left", s.left());
            c.put("sold_out", s.soldOut());
        }
    }

    /** What one student may do: their own seat (hold or link) counts as available. */
    public Map<String, Object> forStudent(long courseId, Long userId) {
        Seats s = seats(courseId);
        Map<String, Object> m = s.toMap();
        if (s.limit() != null && userId != null) {
            boolean mine = hasOwnSeat(courseId, userId);
            m.put("mine", mine);
            m.put("soldOut", s.soldOut() && !mine);
            List<Map<String, Object>> w = jdbc.queryForList("select status from course_waitlist where course_id = ? and user_id = ?", courseId, userId);
            m.put("waitlist", w.isEmpty() ? null : w.get(0).get("status"));
            if (!w.isEmpty() && "WAITING".equals(w.get(0).get("status"))) {
                Integer pos = jdbc.queryForObject("select count(*) from course_waitlist where course_id = ? and status = 'WAITING' and id <= (select id from course_waitlist where course_id = ? and user_id = ?)",
                        Integer.class, courseId, courseId, userId);
                m.put("position", pos);
            }
        }
        return m;
    }

    private boolean hasOwnSeat(long courseId, long userId) {
        Timestamp now = ts(LocalDateTime.now());
        Integer n = jdbc.queryForObject("select (select count(*) from seat_holds where course_id = ? and user_id = ? and (status = 'PAID' or (status = 'HELD' and expires_at > ?)))"
                + " + (select count(*) from access_grants where course_id = ? and user_id = ? and holds_seat = 1 and used_at is null and revoked_at is null and expires_at > ?)",
                Integer.class, courseId, userId, now, courseId, userId, now);
        return n != null && n > 0;
    }

    /**
     * Keeps a seat for this student while they pay ({@code app.seats.hold-minutes}). Pressing Pay again renews their
     * own hold. A valid priority link of theirs ({@code grantToken}) lets them in even when the course looks full,
     * because that seat was already counted for them.
     *
     * @throws SoldOutException when every seat is taken by someone else
     */
    public void hold(long courseId, long userId, String email, Long orderId, String grantToken) {
        tx.executeWithoutResult(st -> {
            List<Map<String, Object>> c = jdbc.queryForList("select seat_limit from courses where id = ? for update", courseId);
            if (c.isEmpty() || c.get(0).get("seat_limit") == null) return;
            int limit = ((Number) c.get(0).get("seat_limit")).intValue();
            LocalDateTime now = LocalDateTime.now();
            Long grantId = grantToken == null ? null : claimGrant(courseId, userId, email, grantToken);
            if (grantId == null && !hasOwnSeat(courseId, userId) && taken(courseId, userId) >= limit) {
                throw new SoldOutException(courseId);
            }
            LocalDateTime until = now.plusMinutes(settings.seatHoldMinutes());
            int updated = jdbc.update("update seat_holds set status = case when status = 'PAID' then 'PAID' else 'HELD' end, order_id = coalesce(?, order_id),"
                    + " grant_id = coalesce(?, grant_id), expires_at = case when status = 'PAID' then expires_at else ? end, updated_at = ? where course_id = ? and user_id = ?",
                    orderId, grantId, ts(until), ts(now), courseId, userId);
            if (updated == 0) {
                jdbc.update("insert into seat_holds (course_id, user_id, order_id, grant_id, status, expires_at, created_at, updated_at) values (?, ?, ?, ?, 'HELD', ?, ?, ?)",
                        courseId, userId, orderId, grantId, ts(until), ts(now), ts(now));
            }
        });
    }

    /** Ties a valid link (matched by token, for this account or its e-mail) to the student. Returns its id, or null. */
    private Long claimGrant(long courseId, long userId, String email, String token) {
        List<Map<String, Object>> g = jdbc.queryForList("select id, user_id, email from access_grants where token_hash = ? and course_id = ? and used_at is null"
                + " and revoked_at is null and expires_at > ? for update", sha256(token), courseId, ts(LocalDateTime.now()));
        if (g.isEmpty()) return null;
        Map<String, Object> row = g.get(0);
        Object owner = row.get("user_id");
        boolean forMe = owner instanceof Number n ? n.longValue() == userId : email != null && email.equalsIgnoreCase(String.valueOf(row.get("email")));
        if (!forMe) return null;
        long id = ((Number) row.get("id")).longValue();
        if (owner == null) jdbc.update("update access_grants set user_id = ? where id = ?", userId, id);
        return id;
    }

    /**
     * A verified payment arrived: turns the student's seat into a paid one. False when the course filled up meanwhile
     * (their hold ran out while paying and someone else took the last seat): the caller refunds that payment.
     */
    public boolean confirm(long courseId, long userId) {
        Boolean ok = tx.execute(st -> {
            List<Map<String, Object>> c = jdbc.queryForList("select seat_limit from courses where id = ? for update", courseId);
            if (c.isEmpty() || c.get(0).get("seat_limit") == null) return true;
            int limit = ((Number) c.get(0).get("seat_limit")).intValue();
            LocalDateTime now = LocalDateTime.now();
            if (!hasOwnSeat(courseId, userId) && taken(courseId, userId) >= limit) return false;
            int updated = jdbc.update("update seat_holds set status = 'PAID', updated_at = ? where course_id = ? and user_id = ?", ts(now), courseId, userId);
            if (updated == 0) {
                jdbc.update("insert into seat_holds (course_id, user_id, status, expires_at, created_at, updated_at) values (?, ?, 'PAID', ?, ?, ?)",
                        courseId, userId, ts(now), ts(now), ts(now));
            }
            jdbc.update("update access_grants set used_at = ? where course_id = ? and user_id = ? and used_at is null and revoked_at is null", ts(now), courseId, userId);
            jdbc.update("update course_waitlist set status = 'JOINED', updated_at = ? where course_id = ? and user_id = ? and status in ('WAITING', 'INVITED')", ts(now), courseId, userId);
            return true;
        });
        return Boolean.TRUE.equals(ok);
    }

    /** An order was refunded or cancelled: the seat goes back (and the next waiting student is invited). */
    public void release(long courseId, long userId) {
        jdbc.update("update seat_holds set status = 'RELEASED', updated_at = ? where course_id = ? and user_id = ? and status in ('HELD', 'PAID')",
                ts(LocalDateTime.now()), courseId, userId);
        inviteWaiting(courseId);
    }

    // ------------------------------------------------------------------ waitlist

    public enum Join { JOINED, ALREADY, NOT_FULL, ENROLLED, NO_CAP }

    public Join joinWaitlist(long courseId, long userId, String email) {
        Seats s = seats(courseId);
        if (s.limit() == null) return Join.NO_CAP;
        if (jdbc.queryForObject("select count(*) from orders where course_id = ? and user_id = ? and payment_status = 1", Integer.class, courseId, userId) > 0) return Join.ENROLLED;
        if (!s.soldOut()) return Join.NOT_FULL;
        LocalDateTime now = LocalDateTime.now();
        int n = jdbc.update("update course_waitlist set status = 'WAITING', created_at = case when status in ('LEFT', 'EXPIRED') then ? else created_at end, updated_at = ?"
                + " where course_id = ? and user_id = ? and status in ('LEFT', 'EXPIRED')", ts(now), ts(now), courseId, userId);
        if (n > 0) return Join.JOINED;
        try {
            jdbc.update("insert into course_waitlist (course_id, user_id, email, status, created_at, updated_at) values (?, ?, ?, 'WAITING', ?, ?)",
                    courseId, userId, email, ts(now), ts(now));
            return Join.JOINED;
        } catch (RuntimeException e) {
            if (com.artistikcity.support.DbErrors.isDuplicateKey(e)) return Join.ALREADY;
            throw e;
        }
    }

    public boolean leaveWaitlist(long courseId, long userId) {
        return jdbc.update("update course_waitlist set status = 'LEFT', updated_at = ? where course_id = ? and user_id = ? and status = 'WAITING'",
                ts(LocalDateTime.now()), courseId, userId) > 0;
    }

    /** While seats are free and students wait, the first in line gets a 24-hour priority link. Returns how many were invited. */
    public int inviteWaiting(long courseId) {
        int invited = 0;
        for (int guard = 0; guard < 50; guard++) {
            Map<String, Object> next = tx.execute(st -> {
                List<Map<String, Object>> c = jdbc.queryForList("select id, title, slug, seat_limit from courses where id = ? for update", courseId);
                if (c.isEmpty() || c.get(0).get("seat_limit") == null) return null;
                int limit = ((Number) c.get(0).get("seat_limit")).intValue();
                if (taken(courseId, 0) >= limit) return null;
                List<Map<String, Object>> w = jdbc.queryForList("select id, user_id, email from course_waitlist where course_id = ? and status = 'WAITING' order by id limit 1 for update", courseId);
                if (w.isEmpty()) return null;
                Map<String, Object> entry = w.get(0);
                String token = newToken();
                LocalDateTime now = LocalDateTime.now();
                long userId = ((Number) entry.get("user_id")).longValue();
                long grantId = insertGrant(courseId, userId, String.valueOf(entry.get("email")), "WAITLIST", token, now.plusHours(INVITE_HOURS), null, "Waitlist invitation");
                jdbc.update("update course_waitlist set status = 'INVITED', grant_id = ?, updated_at = ? where id = ?", grantId, ts(now), entry.get("id"));
                Map<String, Object> out = new LinkedHashMap<>(c.get(0));
                out.put("email", entry.get("email"));
                out.put("token", token);
                out.put("user_id", userId);
                return out;
            });
            if (next == null) break;
            invited++;
            sendInvite(next, INVITE_HOURS, false);
        }
        return invited;
    }

    /** Staff send a priority link by hand (a seat kept for that person until it expires). Returns the raw token. */
    public String issuePriority(long courseId, String email, Long userId, int hours, boolean holdsSeat, Long adminId, String note) {
        String token = newToken();
        LocalDateTime until = LocalDateTime.now().plusHours(Math.max(1, Math.min(hours, 24 * 30)));
        insertGrant(courseId, userId, email, "PRIORITY", token, until, adminId, note, holdsSeat);
        Map<String, Object> c = jdbc.queryForMap("select id, title, slug from courses where id = ?", courseId);
        c.put("email", email);
        c.put("token", token);
        sendInvite(c, hours, true);
        return token;
    }

    public boolean revoke(long grantId) {
        boolean done = jdbc.update("update access_grants set revoked_at = ? where id = ? and used_at is null and revoked_at is null", ts(LocalDateTime.now()), grantId) > 0;
        if (done) {
            jdbc.update("update course_waitlist set status = 'EXPIRED', updated_at = ? where grant_id = ? and status = 'INVITED'", ts(LocalDateTime.now()), grantId);
            Long courseId = jdbc.queryForObject("select course_id from access_grants where id = ?", Long.class, grantId);
            if (courseId != null) inviteWaiting(courseId);
        }
        return done;
    }

    private long insertGrant(long courseId, Long userId, String email, String kind, String token, LocalDateTime until, Long adminId, String note) {
        return insertGrant(courseId, userId, email, kind, token, until, adminId, note, true);
    }

    private long insertGrant(long courseId, Long userId, String email, String kind, String token, LocalDateTime until, Long adminId, String note, boolean holdsSeat) {
        org.springframework.jdbc.support.GeneratedKeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into access_grants (course_id, user_id, email, kind, token_hash, holds_seat, expires_at, created_by, note, created_at)"
                    + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, courseId);
            ps.setObject(2, userId);
            ps.setString(3, email);
            ps.setString(4, kind);
            ps.setString(5, sha256(token));
            ps.setBoolean(6, holdsSeat);
            ps.setTimestamp(7, ts(until));
            ps.setObject(8, adminId);
            ps.setString(9, note);
            ps.setTimestamp(10, ts(now));
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    private void sendInvite(Map<String, Object> course, int hours, boolean priority) {
        String base = env.getProperty("app.url", "");
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String url = base + "/checkout?course=" + course.get("slug") + "&access=" + course.get("token");
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("title", priority ? "Your priority place: " + course.get("title") : "A seat opened up: " + course.get("title"));
        model.put("lines", List.of(
                priority ? "You have priority access to " + course.get("title") + "." : "Good news - a seat in " + course.get("title") + " is free and it's yours first.",
                "We keep it for you for " + hours + " hours. After that it goes to the next person in line.",
                "Use the button below while signed in with this e-mail address."));
        model.put("url", url);
        model.put("at", LocalDateTime.now().toString().replace('T', ' ').substring(0, 16));
        try {
            mailer.send(String.valueOf(course.get("email")), String.valueOf(model.get("title")), "emails/alert", model);
        } catch (RuntimeException e) {
            log.warn("Could not send the seat invitation for course {}", course.get("id"), e);
        }
        auditLog.write(AuditLog.entry(AuditLog.Type.CREATE, priority ? "priority access sent" : "waitlist invitation sent").actor(AuditLog.Actor.system())
                .resource("course", course.get("id")).summary(String.valueOf(course.get("email"))));
    }

    // ------------------------------------------------------------------ janitor

    /** Every minute: lapsed holds and links free their seats, lapsed invitations pass to the next student. */
    @Scheduled(cron = "30 * * * * *")
    public void expire() {
        Timestamp now = ts(LocalDateTime.now());
        try {
            List<Long> courses = new java.util.ArrayList<>(jdbc.queryForList("select distinct course_id from seat_holds where status = 'HELD' and expires_at <= ?", Long.class, now));
            jdbc.update("update seat_holds set status = 'EXPIRED', updated_at = ? where status = 'HELD' and expires_at <= ?", now, now);
            courses.addAll(jdbc.queryForList("select distinct g.course_id from access_grants g join course_waitlist w on w.grant_id = g.id"
                    + " where w.status = 'INVITED' and g.used_at is null and g.expires_at <= ?", Long.class, now));
            jdbc.update("update course_waitlist w join access_grants g on g.id = w.grant_id set w.status = 'EXPIRED', w.updated_at = ?"
                    + " where w.status = 'INVITED' and g.used_at is null and g.expires_at <= ?", now, now);
            courses.addAll(jdbc.queryForList("select distinct course_id from course_waitlist where status = 'WAITING'", Long.class));
            for (Long c : new java.util.LinkedHashSet<>(courses)) inviteWaiting(c);
        } catch (RuntimeException e) {
            log.debug("Seat janitor skipped: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------ helpers

    static String newToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }
}
