package com.artistikcity.lifecycle;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data access for enrollments, lesson progress, submissions and the portfolio marketplace.
 * JdbcTemplate + TransactionTemplate keep multi-statement changes atomic.
 */
@Repository
public class LifecycleRepository {

    private static final String PHOTO = "(select top 1 p.photo_name from course_photos p where p.course_id = c.id order by p.id)";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public LifecycleRepository(JdbcTemplate jdbc, PlatformTransactionManager tm) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(tm);
    }

    private static Timestamp now() {
        return Timestamp.valueOf(LocalDateTime.now());
    }

    // =========================================================== enrollments

    /** Creates enrollments for any paid course orders that don't have one yet. */
    public void syncEnrollments(long userId) {
        jdbc.update("insert into enrollments (user_id, course_id, progress_percentage, status, created_at, updated_at)"
                + " select distinct o.user_id, o.course_id, 0, 'Active', ?, ? from orders o where o.user_id = ? and o.payment_status = 1"
                + " and not exists (select 1 from enrollments e where e.user_id = o.user_id and e.course_id = o.course_id)", now(), now(), userId);
    }

    public List<Map<String, Object>> enrollments(long userId) {
        return jdbc.queryForList("select e.id, e.course_id, e.progress_percentage, e.status, e.created_at, c.title, c.slug, c.sub_title,"
                + " c.course_type_id, c.course_start_date, c.medium_id, " + PHOTO + " as photo,"
                + " (select count(*) from course_module_lessons l where l.course_id = c.id and l.status = '1') as total_lessons,"
                + " (select count(*) from lesson_completions lc where lc.enrollment_id = e.id) as completed_lessons,"
                + " (select count(*) from student_submissions s where s.user_id = e.user_id and s.course_id = e.course_id) as submissions"
                + " from enrollments e join courses c on c.id = e.course_id where e.user_id = ? order by e.id desc", userId);
    }

    public Map<String, Object> enrollment(long userId, long courseId) {
        List<Map<String, Object>> r = jdbc.queryForList("select * from enrollments where user_id = ? and course_id = ?", userId, courseId);
        return r.isEmpty() ? null : r.get(0);
    }

    public Map<String, Object> course(long courseId) {
        List<Map<String, Object>> r = jdbc.queryForList("select c.id, c.title, c.slug, c.sub_title, c.introduction, c.course_type_id,"
                + " c.admin_id, " + PHOTO + " as photo, a.name as teacher_name from courses c left join admins a on a.id = c.admin_id where c.id = ?", courseId);
        return r.isEmpty() ? null : r.get(0);
    }

    public List<Map<String, Object>> modules(long courseId) {
        return jdbc.queryForList("select id, module_title, module_duration from course_modules where course_id = ? and status = '1' order by id", courseId);
    }

    public List<Map<String, Object>> lessons(long courseId) {
        return jdbc.queryForList("select id, module_id, lesson_title, lesson_description, lesson_pdf from course_module_lessons"
                + " where course_id = ? and status = '1' order by module_id, id", courseId);
    }

    public List<Long> completedLessonIds(long enrollmentId) {
        return jdbc.queryForList("select lesson_id from lesson_completions where enrollment_id = ?", Long.class, enrollmentId);
    }

    public List<Map<String, Object>> videos(long courseId) {
        return jdbc.queryForList("select video_title, video_url from course_videos where course_id = ? and status = 1 order by id", courseId);
    }

    /** Marks a lesson complete (idempotent) and recalculates the enrollment progress. Returns the new progress %. */
    public int completeLesson(long enrollmentId, long courseId, long lessonId) {
        Integer result = tx.execute(s -> {
            Integer belongs = jdbc.queryForObject("select count(*) from course_module_lessons where id = ? and course_id = ?", Integer.class, lessonId, courseId);
            if (belongs == null || belongs == 0) {
                throw new LifecycleDtos.LifecycleException(404, "Lesson not found in this course.");
            }
            try {
                jdbc.update("insert into lesson_completions (enrollment_id, lesson_id, completed_at) values (?, ?, ?)", enrollmentId, lessonId, now());
            } catch (DuplicateKeyException ignored) {
                // already completed
            }
            int total = jdbc.queryForObject("select count(*) from course_module_lessons where course_id = ? and status = '1'", Integer.class, courseId);
            int done = jdbc.queryForObject("select count(*) from lesson_completions where enrollment_id = ?", Integer.class, enrollmentId);
            int pct = total == 0 ? 0 : Math.min(100, Math.round(done * 100f / total));
            jdbc.update("update enrollments set progress_percentage = ?, status = ?, updated_at = ? where id = ?",
                    pct, pct >= 100 ? "Completed" : "Active", now(), enrollmentId);
            return pct;
        });
        return result == null ? 0 : result;
    }

    // =========================================================== submissions

    public long createSubmission(long userId, long courseId, String title, String description, String fileUrl) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        Timestamp now = now();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("insert into student_submissions (user_id, course_id, title, description, file_url,"
                    + " admin_status, created_at, updated_at) values (?, ?, ?, ?, ?, 'Pending Review', ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setLong(2, courseId);
            ps.setString(3, title);
            ps.setString(4, description);
            ps.setString(5, fileUrl);
            ps.setTimestamp(6, now);
            ps.setTimestamp(7, now);
            return ps;
        }, keys);
        Number k = keys.getKeys() != null && keys.getKeys().get("id") instanceof Number n ? n : keys.getKey();
        return k == null ? 0 : k.longValue();
    }

    private static final String SUBMISSION_SELECT = "select s.id, s.user_id, s.course_id, s.title, s.description, s.file_url, s.admin_status,"
            + " s.reviewer_feedback, s.reviewed_at, s.created_at, c.title as course_title, c.admin_id as course_teacher_id,"
            + " u.name as student_name, u.email as student_email, u.profile_photo as student_photo, a.name as reviewer_name,"
            + " m.is_listed_for_sale, m.sale_price, m.currency, m.inventory_count"
            + " from student_submissions s join courses c on c.id = s.course_id join users u on u.id = s.user_id"
            + " left join admins a on a.id = s.reviewed_by left join portfolio_marketplace m on m.submission_id = s.id";

    public List<Map<String, Object>> submissionsForUser(long userId) {
        return jdbc.queryForList(SUBMISSION_SELECT + " where s.user_id = ? order by s.id desc", userId);
    }

    /** Review queue; instructors (teacherId != null) only see work from their own courses. */
    public List<Map<String, Object>> submissionsForReview(String status, Long teacherId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SUBMISSION_SELECT + " where 1 = 1");
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            sql.append(" and s.admin_status = ?");
            args.add(status);
        }
        if (teacherId != null) {
            sql.append(" and c.admin_id = ?");
            args.add(teacherId);
        }
        sql.append(" order by case when s.admin_status = 'Pending Review' then 0 else 1 end, s.id desc");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> submission(long id) {
        List<Map<String, Object>> r = jdbc.queryForList(SUBMISSION_SELECT + " where s.id = ?", id);
        return r.isEmpty() ? null : r.get(0);
    }

    /**
     * Applies an admin review. Approved work gets an (unlisted) marketplace entry so the student can
     * choose to sell it; rejected work is withdrawn from sale. Only Pending Review items can be decided,
     * except that an admin may re-open a decision by reviewing again.
     */
    public void review(long submissionId, String status, String notes, long adminId) {
        tx.executeWithoutResult(s -> {
            List<Map<String, Object>> rows = jdbc.queryForList("select id, user_id from student_submissions where id = ?", submissionId);
            if (rows.isEmpty()) {
                throw new LifecycleDtos.LifecycleException(404, "Submission not found.");
            }
            long userId = ((Number) rows.get(0).get("user_id")).longValue();
            jdbc.update("update student_submissions set admin_status = ?, reviewer_feedback = ?, reviewed_by = ?, reviewed_at = ?, updated_at = ?"
                    + " where id = ?", status, notes, adminId, now(), now(), submissionId);
            if ("Approved".equals(status)) {
                Integer exists = jdbc.queryForObject("select count(*) from portfolio_marketplace where submission_id = ?", Integer.class, submissionId);
                if (exists == null || exists == 0) {
                    jdbc.update("insert into portfolio_marketplace (submission_id, user_id, is_listed_for_sale, sale_price, currency, inventory_count,"
                            + " created_at, updated_at) values (?, ?, ?, null, 'INR', 1, ?, ?)", submissionId, userId, false, now(), now());
                }
            } else {
                jdbc.update("update portfolio_marketplace set is_listed_for_sale = ?, updated_at = ? where submission_id = ?", false, now(), submissionId);
            }
        });
    }

    // =========================================================== marketplace

    /**
     * Updates listing visibility / price. The UPDATE itself re-checks that the submission belongs to the
     * user and is Approved, so the rule holds even under concurrent re-reviews.
     */
    public void updateListing(long userId, long submissionId, boolean listed, BigDecimal price, int inventory) {
        tx.executeWithoutResult(s -> {
            List<Map<String, Object>> rows = jdbc.queryForList("select admin_status from student_submissions where id = ? and user_id = ?", submissionId, userId);
            if (rows.isEmpty()) {
                throw new LifecycleDtos.LifecycleException(404, "Artwork not found in your portfolio.");
            }
            if (!"Approved".equals(rows.get(0).get("admin_status"))) {
                throw new LifecycleDtos.LifecycleException(403, "Only artwork approved by a reviewer can be listed for sale.");
            }
            int changed = jdbc.update("update portfolio_marketplace set is_listed_for_sale = ?, sale_price = ?, inventory_count = ?, updated_at = ?"
                            + " where submission_id = ? and user_id = ? and exists (select 1 from student_submissions ss where ss.id = ? and ss.admin_status = 'Approved')",
                    listed, price, inventory, now(), submissionId, userId, submissionId);
            if (changed == 0) {
                jdbc.update("insert into portfolio_marketplace (submission_id, user_id, is_listed_for_sale, sale_price, currency, inventory_count,"
                        + " created_at, updated_at) values (?, ?, ?, ?, 'INR', ?, ?, ?)", submissionId, userId, listed, price, inventory, now(), now());
            }
        });
    }

    public List<Map<String, Object>> publicListings() {
        return jdbc.queryForList("select m.id, m.sale_price, m.currency, m.inventory_count, s.id as submission_id, s.title, s.description,"
                + " s.file_url, c.title as course_title, u.name as artist_name, u.slug as artist_slug from portfolio_marketplace m"
                + " join student_submissions s on s.id = m.submission_id join users u on u.id = m.user_id join courses c on c.id = s.course_id"
                + " where m.is_listed_for_sale = ? and s.admin_status = 'Approved' and m.inventory_count > 0 and m.sale_price is not null order by m.updated_at desc", true);
    }

    public List<Map<String, Object>> allListings() {
        return jdbc.queryForList("select m.id, m.is_listed_for_sale, m.sale_price, m.currency, m.inventory_count, m.updated_at, s.id as submission_id,"
                + " s.title, s.file_url, u.name as artist_name, u.email as artist_email, c.title as course_title from portfolio_marketplace m"
                + " join student_submissions s on s.id = m.submission_id join users u on u.id = m.user_id join courses c on c.id = s.course_id order by m.updated_at desc");
    }

    public void adminUnlist(long listingId) {
        jdbc.update("update portfolio_marketplace set is_listed_for_sale = ?, updated_at = ? where id = ?", false, now(), listingId);
    }

    // =========================================================== admin overview

    public long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    public List<Map<String, Object>> list(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    public LocalDate today() {
        return LocalDate.now();
    }
}
