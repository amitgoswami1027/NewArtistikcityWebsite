package com.artistikcity.lifecycle;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Csrf;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import com.artistikcity.support.UploadedFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Student Creative Lifecycle - the student side.
 * Pages (auth guard): /dashboard, /dashboard/classroom/{id}, /dashboard/submissions (/dashboard/portfolio redirects to /dashboard).
 * Public page: /student-shop (approved work listed for sale).
 * APIs: POST /api/submissions/upload,
 *       POST /api/classroom/{courseId}/lessons/{lessonId}/complete.
 */
@Controller
public class StudentStudioController {

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.artistikcity.moderation.Moderation> moderation;


    @org.springframework.beans.factory.annotation.Autowired
    private com.artistikcity.notices.CourseNotices courseNotices;


    private static final long MAX_BYTES = 15L * 1024 * 1024;

    private final Inertia inertia;
    private final Auth auth;
    private final Json json;
    private final Storage storage;
    private final LifecycleRepository repo;
    private final CredentialsService credentials;
    private final com.artistikcity.marketplace.MarketplaceRepository market;

    public StudentStudioController(Inertia inertia, Auth auth, Json json, Storage storage, LifecycleRepository repo, CredentialsService credentials,
                                   com.artistikcity.marketplace.MarketplaceRepository market) {
        this.market = market;
        this.inertia = inertia;
        this.auth = auth;
        this.json = json;
        this.storage = storage;
        this.repo = repo;
        this.credentials = credentials;
    }

    // ================================================================ pages

    @GetMapping({"/dashboard", "/dashboard/"})
    public ResponseEntity<String> studio(HttpServletRequest request) {
        long userId = auth.userId(request);
        repo.syncEnrollments(userId);
        List<Map<String, Object>> subs = repo.submissionsForUser(userId);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("enrollments", repo.enrollments(userId));
        props.put("submissions", subs);
        List<Map<String, Object>> creds = credentials.credentials(userId);
        props.put("stage", stage(repo.enrollments(userId), subs, creds));
        props.put("certificatesReady", creds.stream().filter(c -> Boolean.TRUE.equals(c.get("eligible")) || c.get("certificate") != null).count());
        // step 7: what the student has in the marketplace (My studio > Sell your art)
        props.put("selling", Map.of(
                "live", repo.count("select count(*) from paintings where source = 'STUDENT' and artist_user_id = ? and stock_status in ('AVAILABLE', 'RESERVED')", userId),
                "inReview", repo.count("select count(*) from paintings where source = 'STUDENT' and artist_user_id = ? and stock_status = 'PENDING_REVIEW'", userId),
                "sold", repo.count("select count(*) from paintings where source = 'STUDENT' and artist_user_id = ? and stock_status = 'SOLD'", userId)));
        // messages from the course team (Admin > course > Students > Message students; cancellations; new dates)
        try {
            props.put("notices", courseNotices.forStudent(userId, 10));
        } catch (RuntimeException e) {
            props.put("notices", List.of());
        }
        return inertia.render(request, "Studio/Home", props);
    }

    @GetMapping("/dashboard/classroom/{id}")
    public ResponseEntity<String> classroom(HttpServletRequest request, @PathVariable("id") long courseId) {
        long userId = auth.userId(request);
        // one learning view for every course: the course pages under /user/course (overview, lessons, tasks)
        Object paidOrder = repo.paidOrderId(userId, courseId);
        if (paidOrder != null) {
            return ResponseEntity.status(302).header("Location", "/user/course/" + paidOrder + "/resume").build();
        }
        repo.syncEnrollments(userId);
        Map<String, Object> course = NotFoundException.orFail(repo.course(courseId));
        Map<String, Object> enrollment = repo.enrollment(userId, courseId);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("course", course);
        props.put("enrolled", enrollment != null);
        // the paid order behind this course: its "Schedule, modules & tasks" page lives at /user/course/{orderId}
        props.put("orderId", repo.paidOrderId(userId, courseId));
        if (enrollment != null) {
            long enrollmentId = ((Number) enrollment.get("id")).longValue();
            List<Map<String, Object>> modules = repo.modules(courseId);
            List<Map<String, Object>> lessons = repo.lessons(courseId);
            for (Map<String, Object> m : modules) {
                List<Map<String, Object>> own = new ArrayList<>();
                for (Map<String, Object> l : lessons) {
                    if (String.valueOf(m.get("id")).equals(String.valueOf(l.get("module_id")))) own.add(l);
                }
                m.put("lessons", own);
            }
            props.put("modules", modules);
            props.put("completed", repo.completedLessonIds(enrollmentId));
            props.put("progress", enrollment.get("progress_percentage"));
            props.put("videos", repo.videos(courseId));
            props.put("submissions", repo.submissionsForUser(userId).stream()
                    .filter(s -> String.valueOf(s.get("course_id")).equals(String.valueOf(courseId))).toList());
        }
        return inertia.render(request, "Studio/Classroom", props);
    }

    @GetMapping("/dashboard/submissions")
    public ResponseEntity<String> submissions(HttpServletRequest request) {
        long userId = auth.userId(request);
        repo.syncEnrollments(userId);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("enrollments", repo.enrollments(userId));
        props.put("submissions", repo.submissionsForUser(userId));
        props.put("selectedCourse", request.getParameter("course"));
        return inertia.render(request, "Studio/Submissions", props);
    }

    /** The artwork section (portfolio & selling originals) was removed from the studio; old links land on the studio home. */
    @GetMapping({"/dashboard/portfolio", "/dashboard/portfolio/", "/dashboard/portfolio/sell/{submissionId}"})
    public ResponseEntity<String> portfolio(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", "/dashboard").build();
    }

    // ================================================================ APIs

    /** multipart/form-data: courseId, title, description (optional), file (.jpg/.png, max 15 MB). */
    @PostMapping("/api/submissions/upload")
    public ResponseEntity<String> upload(HttpServletRequest request) {
        Long userId = guard(request);
        if (userId == null) return error(HttpStatus.UNAUTHORIZED, "Please log in again.");
        if (moderation.getIfAvailable() != null) moderation.getIfAvailable().assertAllowed(userId, "POSTING");
        Input in = Input.of(request, json);
        Long courseId = LifecycleDtos.lng(in.get("courseId"));
        String title = LifecycleDtos.str(in.get("title"));
        String description = LifecycleDtos.str(in.get("description"));
        UploadedFile file = in.file("file");
        if (courseId == null || repo.enrollment(userId, courseId) == null) {
            return error(HttpStatus.FORBIDDEN, "Choose one of your enrolled courses.");
        }
        if (title.length() < 2 || title.length() > 200) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Give your artwork a title (2–200 characters).");
        if (description.length() > 4000) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Keep the description under 4000 characters.");
        if (file == null || file.getContent() == null || file.getContent().length == 0) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Attach a photo of your artwork.");
        if (file.getContent().length > MAX_BYTES) return error(HttpStatus.UNPROCESSABLE_ENTITY, "That file is larger than 15 MB.");
        String ext = sniff(file.getContent());
        if (ext == null) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Upload a JPG or PNG image.");
        String name = UUID.randomUUID() + "." + ext;
        storage.put("uploads/submissions/" + name, file.getContent());
        String url = "/storage/uploads/submissions/" + name;
        long id = repo.createSubmission(userId, courseId, title, description.isEmpty() ? null : description, url);
        return ok(new LifecycleDtos.SubmissionCreated(id, url, "Pending Review"));
    }

    @PostMapping("/api/classroom/{courseId}/lessons/{lessonId}/complete")
    public ResponseEntity<String> complete(HttpServletRequest request, @PathVariable("courseId") long courseId, @PathVariable("lessonId") long lessonId) {
        Long userId = guard(request);
        if (userId == null) return error(HttpStatus.UNAUTHORIZED, "Please log in again.");
        Map<String, Object> e = repo.enrollment(userId, courseId);
        if (e == null) return error(HttpStatus.FORBIDDEN, "You're not enrolled in this course.");
        try {
            int pct = repo.completeLesson(((Number) e.get("id")).longValue(), courseId, lessonId);
            return ok(Map.of("progress", pct, "completed", repo.completedLessonIds(((Number) e.get("id")).longValue())));
        } catch (LifecycleDtos.LifecycleException ex) {
            return error(HttpStatus.valueOf(ex.status()), ex.getMessage());
        }
    }

    // ================================================================ helpers

    /** /api routes skip the web middleware, so auth + CSRF are checked here. */
    private Long guard(HttpServletRequest request) {
        if (!Csrf.matches(request)) return null;
        return auth.userId(request);
    }

    /** The next step in the lifecycle: 3 choose a course, 4 learn, 5 submit, 6 certify, 7 sell (certified: My studio > Sell your art). */
    static int stage(List<Map<String, Object>> enrollments, List<Map<String, Object>> subs) {
        return stage(enrollments, subs, List.of());
    }

    static int stage(List<Map<String, Object>> enrollments, List<Map<String, Object>> subs, List<Map<String, Object>> credentials) {
        boolean certified = credentials.stream().anyMatch(c -> Boolean.TRUE.equals(c.get("eligible")) || c.get("certificate") != null);
        if (certified) return 7;
        if (subs.stream().anyMatch(s -> "Approved".equals(s.get("admin_status")))) return 6;
        if (!subs.isEmpty()) return 5;
        if (!enrollments.isEmpty()) return 4;
        return 3;
    }

    private static String sniff(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        return null;
    }

    private ResponseEntity<String> ok(Object body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of("message", message)));
    }
}
