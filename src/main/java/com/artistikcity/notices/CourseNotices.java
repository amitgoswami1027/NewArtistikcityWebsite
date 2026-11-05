package com.artistikcity.notices;

import com.artistikcity.payments.RefundQueue;
import com.artistikcity.security.AuditLog;
import com.artistikcity.service.Mailer;
import com.artistikcity.support.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Messages to the students of a course, and the two big changes to a live run:
 * <ul>
 *   <li>{@link #message}: a notice in every enrolled student's studio, plus an e-mail when chosen; one delivery row
 *       per student records e-mail status and when they read it.</li>
 *   <li>{@link #reschedule}: new start date, every student told (old → new date).</li>
 *   <li>{@link #cancel}: the course is hidden from the catalogue, every paid order is put in the refunds queue
 *       (reason COURSE_CANCELLED - staff still confirm each refund), the waitlist is closed and open priority links
 *       revoked, and every student told.</li>
 * </ul>
 */
@Service
public class CourseNotices {

    private static final Logger log = LoggerFactory.getLogger(CourseNotices.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final JdbcTemplate jdbc;
    private final Mailer mailer;
    private final RefundQueue refunds;
    private final AuditLog auditLog;
    private final Environment env;

    public CourseNotices(JdbcTemplate jdbc, Mailer mailer, RefundQueue refunds, AuditLog auditLog, Environment env) {
        this.jdbc = jdbc;
        this.mailer = mailer;
        this.refunds = refunds;
        this.auditLog = auditLog;
        this.env = env;
    }

    public record Sent(long messageId, int recipients, int emailed, int failed) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("messageId", messageId);
            m.put("recipients", recipients);
            m.put("emailed", emailed);
            m.put("failed", failed);
            return m;
        }
    }

    /** Students with a paid order for the course (one per student). */
    public List<Map<String, Object>> recipients(long courseId) {
        return jdbc.queryForList("select u.id, u.name, coalesce(u.email, max(o.billing_email)) as email from orders o join users u on u.id = o.user_id"
                + " where o.course_id = ? and o.payment_status = 1 group by u.id, u.name, u.email order by u.id", courseId);
    }

    public Sent message(long courseId, Row admin, String kind, String subject, String body, boolean sendEmail) {
        Map<String, Object> course = jdbc.queryForMap("select id, title, slug from courses where id = ?", courseId);
        List<Map<String, Object>> to = recipients(courseId);
        LocalDateTime now = LocalDateTime.now();
        org.springframework.jdbc.support.GeneratedKeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into course_messages (course_id, admin_id, kind, subject, body, send_email, recipients, created_at)"
                    + " values (?, ?, ?, ?, ?, ?, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, courseId);
            ps.setObject(2, admin == null ? null : admin.get("id"));
            ps.setString(3, kind);
            ps.setString(4, subject);
            ps.setString(5, body);
            ps.setBoolean(6, sendEmail);
            ps.setInt(7, to.size());
            ps.setTimestamp(8, Timestamp.valueOf(now));
            return ps;
        }, keys);
        long id = keys.getKey().longValue();
        int emailed = 0;
        int failed = 0;
        String base = env.getProperty("app.url", "");
        for (Map<String, Object> u : to) {
            String email = u.get("email") == null ? null : String.valueOf(u.get("email"));
            String status = "SKIPPED";
            String error = null;
            if (sendEmail && email != null && !email.isBlank()) {
                Map<String, Object> model = new LinkedHashMap<>();
                model.put("title", subject);
                model.put("lines", lines("Hi " + firstName(u.get("name")) + ",", body));
                model.put("url", base + "/dashboard");
                model.put("at", now.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")));
                try {
                    boolean ok = mailer.send(email, subject + " · " + course.get("title"), "emails/alert", model);
                    status = ok ? "SENT" : "FAILED";
                    if (!ok) error = "The mail server did not accept the message";
                } catch (RuntimeException e) {
                    status = "FAILED";
                    error = e.getMessage();
                }
                if ("SENT".equals(status)) emailed++;
                else failed++;
            }
            jdbc.update("insert ignore into course_message_deliveries (message_id, user_id, email, email_status, error, created_at) values (?, ?, ?, ?, ?, ?)",
                    id, u.get("id"), email, status, error == null ? null : error.length() > 300 ? error.substring(0, 300) : error, Timestamp.valueOf(now));
        }
        jdbc.update("update course_messages set emailed = ?, failed = ? where id = ?", emailed, failed, id);
        auditLog.write(AuditLog.entry(AuditLog.Type.CREATE, "students messaged").actor(admin == null ? AuditLog.Actor.system() : AuditLog.Actor.admin(admin))
                .resource("course", courseId).summary(kind + " · " + subject + " · " + to.size() + " students, " + emailed + " e-mailed" + (failed > 0 ? ", " + failed + " failed" : "")));
        return new Sent(id, to.size(), emailed, failed);
    }

    private static List<String> lines(String greeting, String body) {
        List<String> l = new ArrayList<>();
        l.add(greeting);
        l.addAll(Arrays.stream(body.split("\\R\\s*\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        return l;
    }

    private static String firstName(Object name) {
        String n = name == null ? "" : String.valueOf(name).trim();
        return n.isEmpty() ? "there" : n.split("\\s+")[0];
    }

    /** Moves the start date and tells every student. */
    public Sent reschedule(long courseId, Row admin, LocalDate newStart, String note) {
        Map<String, Object> c = jdbc.queryForMap("select title, course_start_date from courses where id = ?", courseId);
        Object old = c.get("course_start_date");
        String oldText = old == null ? "the planned date" : LocalDate.parse(String.valueOf(old).substring(0, 10)).format(DAY);
        jdbc.update("update courses set course_start_date = ?, updated_at = ? where id = ?", java.sql.Date.valueOf(newStart), Timestamp.valueOf(LocalDateTime.now()), courseId);
        String body = c.get("title") + " now starts on " + newStart.format(DAY) + " instead of " + oldText + "."
                + (note == null || note.isBlank() ? "" : "\n\n" + note.trim())
                + "\n\nYour place is kept. If the new date doesn't work for you, reply to this e-mail and we'll sort out a refund.";
        return message(courseId, admin, "RESCHEDULED", "New start date: " + newStart.format(DAY), body);
    }

    private Sent message(long courseId, Row admin, String kind, String subject, String body) {
        return message(courseId, admin, kind, subject, body, true);
    }

    /** Cancels the run: hidden, refunds queued for every paid order, waitlist closed, students told. */
    public Map<String, Object> cancel(long courseId, Row admin, String reason, boolean queueRefunds) {
        return cancel(courseId, admin, reason, queueRefunds, 100);
    }

    /** As above; {@code refundPercent} (1-100) of each payment is queued for refund (e.g. 50 when half the classes ran). */
    public Map<String, Object> cancel(long courseId, Row admin, String reason, boolean queueRefunds, int refundPercent) {
        int pct = Math.max(1, Math.min(100, refundPercent));
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> c = jdbc.queryForMap("select title from courses where id = ?", courseId);
        jdbc.update("update courses set status = 0, cancelled_at = ?, cancel_reason = ?, updated_at = ? where id = ?",
                Timestamp.valueOf(now), reason, Timestamp.valueOf(now), courseId);
        int queued = 0;
        if (queueRefunds) {
            for (Map<String, Object> o : jdbc.queryForList("select id, user_id, payment_id, payment_option, price, price_type, billing_email from orders"
                    + " where course_id = ? and payment_status = 1", courseId)) {
                String option = String.valueOf(o.get("payment_option"));
                if (option.startsWith("Free")) continue;
                String gateway = option.startsWith("PayPal") ? (option.contains("test") ? "TEST" : "PAYPAL")
                        : option.startsWith("RazorPay") ? (option.contains("test") ? "TEST" : "RAZORPAY") : "MANUAL";
                BigDecimal amount = RefundQueue.dec(o.get("price")).multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                if (amount.signum() <= 0) continue;
                if (refunds.flag(new RefundQueue.Flag("COURSE", String.valueOf(o.get("id")), o.get("payment_id") == null ? "order-" + o.get("id") : String.valueOf(o.get("payment_id")),
                        gateway, amount, "usd".equalsIgnoreCase(String.valueOf(o.get("price_type"))) ? "USD" : "INR", "COURSE_CANCELLED", reason,
                        o.get("billing_email") == null ? null : String.valueOf(o.get("billing_email")), ((Number) o.get("user_id")).longValue()))) {
                    queued++;
                }
            }
        }
        jdbc.update("update course_waitlist set status = 'EXPIRED', updated_at = ? where course_id = ? and status in ('WAITING', 'INVITED')", Timestamp.valueOf(now), courseId);
        jdbc.update("update access_grants set revoked_at = ? where course_id = ? and used_at is null and revoked_at is null", Timestamp.valueOf(now), courseId);
        String body = "We're sorry: " + c.get("title") + " has been cancelled."
                + (reason == null || reason.isBlank() ? "" : "\n\n" + reason.trim())
                + (queueRefunds ? "\n\nYou'll get " + (pct == 100 ? "a full refund" : "a refund of " + pct + "% of what you paid") + " to the card or account you paid with. Our team confirms it within 2 working days, and the bank usually shows it within 5-7 days after that." : "");
        Sent sent = message(courseId, admin, "CANCELLED", "Cancelled: " + c.get("title"), body);
        Map<String, Object> out = new LinkedHashMap<>(sent.toMap());
        out.put("refundsQueued", queued);
        log.info("Course {} cancelled: {} refunds queued, {} students told", courseId, queued, sent.recipients());
        return out;
    }

    // ------------------------------------------------------------------ students

    /** The student's notices, newest first (studio). */
    public List<Map<String, Object>> forStudent(long userId, int limit) {
        return jdbc.queryForList("select d.id, d.read_at, m.kind, m.subject, m.body, m.created_at, c.title as course_title, c.id as course_id"
                + " from course_message_deliveries d join course_messages m on m.id = d.message_id join courses c on c.id = m.course_id"
                + " where d.user_id = ? order by d.id desc limit " + Math.max(1, Math.min(limit, 50)), userId);
    }

    public long unread(long userId) {
        Long n = jdbc.queryForObject("select count(*) from course_message_deliveries where user_id = ? and read_at is null", Long.class, userId);
        return n == null ? 0 : n;
    }

    public boolean markRead(long userId, long deliveryId) {
        return jdbc.update("update course_message_deliveries set read_at = ? where id = ? and user_id = ? and read_at is null",
                Timestamp.valueOf(LocalDateTime.now()), deliveryId, userId) > 0;
    }

    /** Message history of a course for staff, with delivery numbers. */
    public List<Map<String, Object>> history(long courseId) {
        return jdbc.queryForList("select m.id, m.kind, m.subject, m.body, m.send_email, m.recipients, m.emailed, m.failed, m.created_at, a.name as admin_name,"
                + " (select count(*) from course_message_deliveries d where d.message_id = m.id and d.read_at is not null) as read_count"
                + " from course_messages m left join admins a on a.id = m.admin_id where m.course_id = ? order by m.id desc limit 50", courseId);
    }

    public List<Map<String, Object>> deliveries(long messageId) {
        return jdbc.queryForList("select d.email, d.email_status, d.error, d.read_at, u.name from course_message_deliveries d left join users u on u.id = d.user_id"
                + " where d.message_id = ? order by d.id", messageId);
    }
}
