package com.artistikcity.lifecycle;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Csrf;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Slugs;
import com.artistikcity.support.Storage;
import com.artistikcity.support.UploadedFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The new admin console (React/Inertia), replacing the old /admin/dashboard screen.
 * Pages sit under /admin/dashboard/** (admin guard). Actions are JSON endpoints under
 * /admin/console/api/** (admin guard + CSRF via the web middleware), plus the spec'd
 * PATCH /api/admin/submissions/{id}/review which checks admin + CSRF itself.
 * Every page and action is filtered by the signed-in admin's persona (see {@link Personas}).
 */
@Controller
public class AdminConsoleController {

    private static final List<String> COMMISSION_FLOW = List.of("PAYMENT_PENDING", "QUEUED", "PROOF_SENT", "IN_PRODUCTION", "SHIPPED", "COMPLETED");

    private final Inertia inertia;
    private final Auth auth;
    private final Json json;
    private final Storage storage;
    private final Slugs slugs;
    private final LifecycleRepository repo;

    public AdminConsoleController(Inertia inertia, Auth auth, Json json, Storage storage, Slugs slugs, LifecycleRepository repo) {
        this.inertia = inertia;
        this.auth = auth;
        this.json = json;
        this.storage = storage;
        this.slugs = slugs;
        this.repo = repo;
    }

    // ================================================================ pages

    @GetMapping({"/admin/dashboard", "/admin/dashboard/"})
    public ResponseEntity<String> overview(HttpServletRequest request) {
        Row admin = auth.admin(request);
        Long teacher = teacherScope(admin);
        Map<String, Object> p = base(admin);
        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("students", teacher == null ? repo.count("select count(*) from users")
                : repo.count("select count(distinct e.user_id) from enrollments e join courses c on c.id = e.course_id where c.admin_id = ?", teacher));
        kpi.put("activeEnrollments", teacher == null ? repo.count("select count(*) from enrollments where status = 'Active'")
                : repo.count("select count(*) from enrollments e join courses c on c.id = e.course_id where e.status = 'Active' and c.admin_id = ?", teacher));
        kpi.put("pendingReviews", teacher == null ? repo.count("select count(*) from student_submissions where admin_status = 'Pending Review'")
                : repo.count("select count(*) from student_submissions s join courses c on c.id = s.course_id where s.admin_status = 'Pending Review' and c.admin_id = ?", teacher));
        kpi.put("listedArtworks", repo.count("select count(*) from portfolio_marketplace where is_listed_for_sale = ?", true));
        kpi.put("openCommissions", repo.count("select count(*) from commission_orders where order_status in ('QUEUED','PROOF_SENT','IN_PRODUCTION')"));
        kpi.put("courses", teacher == null ? repo.count("select count(*) from courses where status = 1")
                : repo.count("select count(*) from courses where status = 1 and admin_id = ?", teacher));
        p.put("kpi", kpi);
        if (Personas.can(admin, "revenue")) {
            p.put("revenue", revenue());
        }
        p.put("pipeline", pipeline(teacher));
        p.put("reviewQueue", repo.submissionsForReview("Pending Review", teacher).stream().limit(5).toList());
        p.put("recentStudents", teacher == null
                ? repo.list("select top 6 id, name, email, profile_photo, created_at from users order by id desc")
                : repo.list("select distinct top 6 u.id, u.name, u.email, u.profile_photo, u.created_at from users u join enrollments e on e.user_id = u.id"
                + " join courses c on c.id = e.course_id where c.admin_id = ? order by u.id desc", teacher));
        if (Personas.can(admin, "commissions")) {
            p.put("recentCommissions", repo.list("select top 5 o.order_id, o.customer_name, o.order_status, o.total_price, o.currency, o.created_at,"
                    + " i.artwork_theme from commission_orders o left join commission_artwork_items i on i.order_id = o.order_id"
                    + " where o.order_status <> 'PAYMENT_PENDING' order by o.created_at desc"));
        }
        return inertia.render(request, "Admin/Overview", p);
    }

    @GetMapping("/admin/dashboard/submissions")
    public ResponseEntity<String> submissions(HttpServletRequest request) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "submissions")) return denied(request, admin);
        String status = request.getParameter("status") == null ? "Pending Review" : request.getParameter("status");
        Map<String, Object> p = base(admin);
        p.put("status", status);
        p.put("submissions", repo.submissionsForReview(status, teacherScope(admin)));
        p.put("counts", Map.of(
                "Pending Review", repo.count("select count(*) from student_submissions where admin_status = 'Pending Review'"),
                "Approved", repo.count("select count(*) from student_submissions where admin_status = 'Approved'"),
                "Rejected", repo.count("select count(*) from student_submissions where admin_status = 'Rejected'")));
        return inertia.render(request, "Admin/Submissions", p);
    }

    @GetMapping("/admin/dashboard/commissions")
    public ResponseEntity<String> commissions(HttpServletRequest request) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "commissions")) return denied(request, admin);
        Map<String, Object> p = base(admin);
        p.put("orders", repo.list("select o.*, i.artwork_theme, i.width_inches, i.height_inches, i.frame_material, i.has_matting"
                + " from commission_orders o left join commission_artwork_items i on i.order_id = o.order_id order by o.created_at desc"));
        p.put("flow", COMMISSION_FLOW);
        return inertia.render(request, "Admin/Commissions", p);
    }

    @GetMapping("/admin/dashboard/marketplace")
    public ResponseEntity<String> marketplace(HttpServletRequest request) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace")) return denied(request, admin);
        Map<String, Object> p = base(admin);
        p.put("listings", repo.allListings());
        return inertia.render(request, "Admin/Marketplace", p);
    }

    @GetMapping("/admin/dashboard/people")
    public ResponseEntity<String> people(HttpServletRequest request) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "people")) return denied(request, admin);
        Long teacher = teacherScope(admin);
        Map<String, Object> p = base(admin);
        String studentSql = "select u.id, u.name, u.email, u.phone, u.location, u.status, u.provider, u.created_at, u.profile_photo,"
                + " (select count(*) from enrollments e where e.user_id = u.id) as enrollments,"
                + " (select count(*) from student_submissions s where s.user_id = u.id) as submissions,"
                + " (select count(*) from portfolio_marketplace m where m.user_id = u.id and m.is_listed_for_sale = ?) as listed from users u";
        p.put("students", teacher == null ? repo.list(studentSql + " order by u.id desc", true)
                : repo.list(studentSql + " where exists (select 1 from enrollments e join courses c on c.id = e.course_id where e.user_id = u.id and c.admin_id = ?) order by u.id desc", true, teacher));
        p.put("staff", teacher == null ? repo.list("select id, name, email, admin_type, location, profile_photo, created_at from admins order by id") : List.of());
        p.put("personas", Personas.ALL.stream().map(x -> Map.of("id", x, "label", Personas.label(x))).toList());
        return inertia.render(request, "Admin/People", p);
    }

    @GetMapping("/admin/dashboard/testimonials")
    public ResponseEntity<String> testimonials(HttpServletRequest request) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "testimonials")) return denied(request, admin);
        Map<String, Object> p = base(admin);
        p.put("testimonials", repo.list("select id, name, title, photo, description, status, created_at from testimonials order by id desc"));
        return inertia.render(request, "Admin/Testimonials", p);
    }

    @GetMapping("/admin/dashboard/gallery")
    public ResponseEntity<String> gallery(HttpServletRequest request) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "gallery")) return denied(request, admin);
        Map<String, Object> p = base(admin);
        p.put("homeArtworks", repo.list("select id, photo_name, comments, status, created_at from home_artworks order by id desc"));
        p.put("studentArtworks", repo.list("select a.id, a.photo_name, a.comments, a.status, a.created_at, u.name as student_name"
                + " from student_artworks a left join users u on u.id = a.user_id order by a.id desc"));
        p.put("approvedSubmissions", repo.list("select top 24 s.id, s.title, s.file_url, u.name as student_name from student_submissions s"
                + " join users u on u.id = s.user_id where s.admin_status = 'Approved' order by s.reviewed_at desc"));
        return inertia.render(request, "Admin/Gallery", p);
    }

    // ================================================================ submission review (spec endpoint)

    @PatchMapping("/api/admin/submissions/{id}/review")
    public ResponseEntity<String> review(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        Row admin = auth.admin(request);
        if (admin == null || !Csrf.matches(request)) return error(HttpStatus.UNAUTHORIZED, "Please sign in to the admin console again.");
        if (!Personas.can(admin, "submissions")) return error(HttpStatus.FORBIDDEN, "Your role can't review submissions.");
        LifecycleDtos.ReviewRequest req = LifecycleDtos.ReviewRequest.from(body(request));
        if (!LifecycleDtos.REVIEW_STATUSES.contains(req.status())) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Status must be Approved or Rejected.");
        if (req.notes().length() < 3) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Add review notes for the student (at least a few words).");
        if (req.notes().length() > 4000) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Keep notes under 4000 characters.");
        Map<String, Object> s = repo.submission(id);
        if (s == null) return error(HttpStatus.NOT_FOUND, "Submission not found.");
        Long teacher = teacherScope(admin);
        if (teacher != null && !String.valueOf(teacher).equals(String.valueOf(s.get("course_teacher_id")))) {
            return error(HttpStatus.FORBIDDEN, "You can only review work from your own courses.");
        }
        try {
            repo.review(id, req.status(), req.notes(), admin.lng("id"));
        } catch (LifecycleDtos.LifecycleException e) {
            return error(HttpStatus.valueOf(e.status()), e.getMessage());
        }
        return ok(repo.submission(id));
    }

    // ================================================================ console actions

    @PostMapping("/admin/console/api/commissions/{orderId}/status")
    public ResponseEntity<String> commissionStatus(HttpServletRequest request, @PathVariable("orderId") String orderId) throws IOException {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "commissions")) return error(HttpStatus.FORBIDDEN, "Not allowed.");
        String status = LifecycleDtos.str(body(request).get("status"));
        if (!COMMISSION_FLOW.contains(status) || "PAYMENT_PENDING".equals(status)) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown status.");
        int changed = repo.update("update commission_orders set order_status = ?, updated_at = ? where order_id = ? and order_status <> 'PAYMENT_PENDING'",
                status, Timestamp.valueOf(LocalDateTime.now()), orderId);
        if (changed == 0) return error(HttpStatus.CONFLICT, "Unpaid orders can't be moved forward.");
        return ok(Map.of("orderId", orderId, "status", status));
    }

    @PostMapping("/admin/console/api/marketplace/{id}/unlist")
    public ResponseEntity<String> unlist(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace")) return error(HttpStatus.FORBIDDEN, "Not allowed.");
        repo.adminUnlist(id);
        return ok(Map.of("id", id, "listed", false));
    }

    @PostMapping("/admin/console/api/students/{id}/status")
    public ResponseEntity<String> studentStatus(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "people.manage")) return error(HttpStatus.FORBIDDEN, "Only super admins can change accounts.");
        int status = Boolean.TRUE.equals(body(request).get("active")) ? 1 : 0;
        repo.update("update users set status = ?, updated_at = ? where id = ?", status, Timestamp.valueOf(LocalDateTime.now()), id);
        return ok(Map.of("id", id, "status", status));
    }

    @PostMapping("/admin/console/api/staff/{id}/persona")
    public ResponseEntity<String> staffPersona(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "people.manage")) return error(HttpStatus.FORBIDDEN, "Only super admins can change roles.");
        if (admin.lng("id") == id) return error(HttpStatus.CONFLICT, "You can't change your own role.");
        String persona = LifecycleDtos.str(body(request).get("persona"));
        if (!Personas.ALL.contains(persona)) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown role.");
        if (!"admin".equals(persona) && repo.count("select count(*) from admins where admin_type = 'admin' and id <> ?", id) == 0) {
            return error(HttpStatus.CONFLICT, "Keep at least one super admin.");
        }
        repo.update("update admins set admin_type = ?, updated_at = ? where id = ?", persona, Timestamp.valueOf(LocalDateTime.now()), id);
        return ok(Map.of("id", id, "persona", persona));
    }

    /** multipart: id (optional, for edit), name, title, description, status (1/0), photo (optional image). */
    @PostMapping("/admin/console/api/testimonials")
    public ResponseEntity<String> saveTestimonial(HttpServletRequest request) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "testimonials")) return error(HttpStatus.FORBIDDEN, "Not allowed.");
        Input in = Input.of(request, json);
        String name = LifecycleDtos.str(in.get("name"));
        String description = LifecycleDtos.str(in.get("description"));
        if (name.length() < 2) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Enter the student's name.");
        if (description.length() < 10) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Write the testimonial (at least 10 characters).");
        Long id = LifecycleDtos.lng(in.get("id"));
        int status = "0".equals(in.get("status")) ? 0 : 1;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        if (id == null) {
            repo.update("insert into testimonials (name, slug, title, description, status, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
                    name, slugs.unique("testimonials", "slug", name, null), LifecycleDtos.str(in.get("title")), description, status, now, now);
            id = repo.count("select max(id) from testimonials");
        } else {
            repo.update("update testimonials set name = ?, title = ?, description = ?, status = ?, updated_at = ? where id = ?",
                    name, LifecycleDtos.str(in.get("title")), description, status, now, id);
        }
        UploadedFile photo = in.file("photo");
        if (photo != null && photo.getContent() != null && photo.getContent().length > 0) {
            String ext = sniff(photo.getContent());
            if (ext == null) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Photo must be a JPG or PNG.");
            String file = "photo-" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
            storage.put("uploads/testimonials/" + id + "/" + file, photo.getContent());
            repo.update("update testimonials set photo = ? where id = ?", file, id);
        }
        return ok(repo.list("select id, name, title, photo, description, status, created_at from testimonials where id = ?", id).get(0));
    }

    @DeleteMapping("/admin/console/api/testimonials/{id}")
    public ResponseEntity<String> deleteTestimonial(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "testimonials")) return error(HttpStatus.FORBIDDEN, "Not allowed.");
        repo.update("delete from testimonials where id = ?", id);
        return ok(Map.of("deleted", id));
    }

    /** multipart: kind = home|student, photo (image), comments. Home gallery uploads only. */
    @PostMapping("/admin/console/api/gallery")
    public ResponseEntity<String> uploadGallery(HttpServletRequest request) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "gallery")) return error(HttpStatus.FORBIDDEN, "Not allowed.");
        Input in = Input.of(request, json);
        UploadedFile photo = in.file("photo");
        if (photo == null || photo.getContent() == null || photo.getContent().length == 0) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Choose an image.");
        String ext = sniff(photo.getContent());
        if (ext == null) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Upload a JPG or PNG image.");
        return ok(addHomeArtwork(admin, photo.getContent(), ext, LifecycleDtos.str(in.get("comments"))));
    }

    /** Copies an approved student submission into the home-page gallery. */
    @PostMapping("/admin/console/api/gallery/from-submission/{id}")
    public ResponseEntity<String> promote(HttpServletRequest request, @PathVariable("id") long submissionId) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "gallery")) return error(HttpStatus.FORBIDDEN, "Not allowed.");
        Map<String, Object> s = repo.submission(submissionId);
        if (s == null || !"Approved".equals(s.get("admin_status"))) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Only approved submissions can be featured.");
        String rel = String.valueOf(s.get("file_url")).replaceFirst("^/storage/", "");
        byte[] bytes = storage.read(rel);
        String ext = rel.endsWith(".png") ? "png" : "jpg";
        return ok(addHomeArtwork(admin, bytes, ext, s.get("title") + " by " + s.get("student_name")));
    }

    private Map<String, Object> addHomeArtwork(Row admin, byte[] bytes, String ext, String comments) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        repo.update("insert into home_artworks (admin_id, comments, status, created_at, updated_at) values (?, ?, 1, ?, ?)", admin.lng("id"), comments, now, now);
        long id = repo.count("select max(id) from home_artworks");
        String file = "artwork-" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
        storage.put("uploads/home-artworks/" + id + "/" + file, bytes);
        repo.update("update home_artworks set photo_name = ? where id = ?", file, id);
        return repo.list("select id, photo_name, comments, status, created_at from home_artworks where id = ?", id).get(0);
    }

    @PostMapping("/admin/console/api/gallery/{kind}/{id}/status")
    public ResponseEntity<String> galleryStatus(HttpServletRequest request, @PathVariable("kind") String kind, @PathVariable("id") long id) throws IOException {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "gallery")) return error(HttpStatus.FORBIDDEN, "Not allowed.");
        String table = galleryTable(kind);
        if (table == null) return error(HttpStatus.NOT_FOUND, "Unknown gallery.");
        int status = Boolean.TRUE.equals(body(request).get("published")) ? 1 : 0;
        repo.update("update " + table + " set status = ?, updated_at = ? where id = ?", status, Timestamp.valueOf(LocalDateTime.now()), id);
        return ok(Map.of("id", id, "status", status));
    }

    @DeleteMapping("/admin/console/api/gallery/{kind}/{id}")
    public ResponseEntity<String> galleryDelete(HttpServletRequest request, @PathVariable("kind") String kind, @PathVariable("id") long id) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "gallery")) return error(HttpStatus.FORBIDDEN, "Not allowed.");
        String table = galleryTable(kind);
        if (table == null) return error(HttpStatus.NOT_FOUND, "Unknown gallery.");
        repo.update("delete from " + table + " where id = ?", id);
        return ok(Map.of("deleted", id));
    }

    // ================================================================ helpers

    private static String galleryTable(String kind) {
        return switch (kind) {
            case "home" -> "home_artworks";
            case "student" -> "student_artworks";
            default -> null;
        };
    }

    private Map<String, Object> base(Row admin) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("admin", Personas.view(admin));
        p.put("badges", Map.of(
                "submissions", repo.count("select count(*) from student_submissions where admin_status = 'Pending Review'"),
                "commissions", repo.count("select count(*) from commission_orders where order_status = 'QUEUED'")));
        return p;
    }

    /** Instructors only see their own courses; other personas see everything (null). */
    private static Long teacherScope(Row admin) {
        return "teacher".equals(Personas.of(admin)) ? admin.lng("id") : null;
    }

    private ResponseEntity<String> denied(HttpServletRequest request, Row admin) {
        Map<String, Object> p = base(admin);
        return inertia.render(request, "Admin/Denied", p);
    }

    private List<Map<String, Object>> revenue() {
        LocalDate from = YearMonth.now().minusMonths(11).atDay(1);
        Map<String, double[]> byMonth = new LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) byMonth.put(YearMonth.now().minusMonths(i).toString(), new double[]{0, 0, 0});
        for (Map<String, Object> r : repo.list("select price, price_type, created_at from orders where payment_status = 1 and created_at >= ?", Timestamp.valueOf(from.atStartOfDay()))) {
            String key = month(r.get("created_at"));
            double[] v = byMonth.get(key);
            if (v == null) continue;
            try {
                double amt = Double.parseDouble(String.valueOf(r.get("price")));
                if ("usd".equalsIgnoreCase(String.valueOf(r.get("price_type")))) v[1] += amt; else v[0] += amt;
            } catch (NumberFormatException ignored) {
                // non numeric legacy price
            }
        }
        for (Map<String, Object> r : repo.list("select total_price, currency, created_at from commission_orders where order_status <> 'PAYMENT_PENDING' and created_at >= ?",
                Timestamp.valueOf(from.atStartOfDay()))) {
            double[] v = byMonth.get(month(r.get("created_at")));
            if (v != null && r.get("total_price") instanceof Number n) {
                if ("USD".equals(r.get("currency"))) v[1] += n.doubleValue(); else v[2] += n.doubleValue();
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        byMonth.forEach((k, v) -> out.add(Map.of("month", k, "coursesInr", Math.round(v[0]), "usd", Math.round(v[1]), "commissionsInr", Math.round(v[2]))));
        return out;
    }

    private static String month(Object ts) {
        String s = String.valueOf(ts);
        return s.length() >= 7 ? s.substring(0, 7) : s;
    }

    private Map<String, Object> pipeline(Long teacher) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("signedUp", teacher == null ? repo.count("select count(*) from users") : null);
        m.put("enrolled", teacher == null ? repo.count("select count(distinct user_id) from enrollments")
                : repo.count("select count(distinct e.user_id) from enrollments e join courses c on c.id = e.course_id where c.admin_id = ?", teacher));
        m.put("learning", teacher == null ? repo.count("select count(distinct e.user_id) from enrollments e where exists (select 1 from lesson_completions lc where lc.enrollment_id = e.id)")
                : repo.count("select count(distinct e.user_id) from enrollments e join courses c on c.id = e.course_id where c.admin_id = ? and exists (select 1 from lesson_completions lc where lc.enrollment_id = e.id)", teacher));
        m.put("submitted", teacher == null ? repo.count("select count(distinct user_id) from student_submissions")
                : repo.count("select count(distinct s.user_id) from student_submissions s join courses c on c.id = s.course_id where c.admin_id = ?", teacher));
        m.put("approved", teacher == null ? repo.count("select count(distinct user_id) from student_submissions where admin_status = 'Approved'")
                : repo.count("select count(distinct s.user_id) from student_submissions s join courses c on c.id = s.course_id where s.admin_status = 'Approved' and c.admin_id = ?", teacher));
        m.put("selling", repo.count("select count(distinct user_id) from portfolio_marketplace where is_listed_for_sale = ?", true));
        return m;
    }

    private Map<String, Object> body(HttpServletRequest request) throws IOException {
        Map<String, Object> m = json.decodeMap(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        return m == null ? Map.of() : m;
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
