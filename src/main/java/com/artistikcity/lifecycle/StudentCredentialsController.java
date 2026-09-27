package com.artistikcity.lifecycle;

import com.artistikcity.http.Auth;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Certificates & portfolio reports in the student studio.
 * <ul>
 *   <li>GET /dashboard/certificates - certificates and portfolio report builder (auth).</li>
 *   <li>GET /dashboard/certificates/{courseId}/download - issues (first time) and downloads the certificate PDF.</li>
 *   <li>GET /dashboard/portfolio/report - portfolio PDF (?approvedOnly, curriculum, comments, courses, inline).</li>
 *   <li>GET /certificates/verify/{number} - public verification page.</li>
 * </ul>
 */
@Controller
public class StudentCredentialsController {

    private final Inertia inertia;
    private final Auth auth;
    private final LifecycleRepository repo;
    private final CredentialsService credentials;

    public StudentCredentialsController(Inertia inertia, Auth auth, LifecycleRepository repo, CredentialsService credentials) {
        this.inertia = inertia;
        this.auth = auth;
        this.repo = repo;
        this.credentials = credentials;
    }

    @GetMapping("/dashboard/certificates")
    public ResponseEntity<String> page(HttpServletRequest request) {
        long userId = auth.userId(request);
        repo.syncEnrollments(userId);
        List<Map<String, Object>> creds = credentials.credentials(userId);
        List<Map<String, Object>> subs = repo.submissionsForUser(userId);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("credentials", creds);
        props.put("stats", Map.of(
                "approved", subs.stream().filter(s -> "Approved".equals(s.get("admin_status"))).count(),
                "total", subs.size(),
                "lessons", creds.stream().mapToLong(c -> ((Number) c.get("completed_lessons")).longValue()).sum()));
        props.put("stage", StudentStudioController.stage(creds, subs, creds));
        props.put("verifyBase", Inertia.baseUrl(request) + "/certificates/verify/");
        return inertia.render(request, "Studio/Certificates", props);
    }

    @GetMapping("/dashboard/certificates/{courseId}/download")
    public ResponseEntity<?> download(HttpServletRequest request, @PathVariable("courseId") long courseId,
                                      @RequestParam(value = "inline", required = false) String inline) {
        long userId = auth.userId(request);
        Map<String, Object> cert;
        try {
            cert = credentials.issue(userId, courseId);
        } catch (LifecycleDtos.LifecycleException e) {
            return ResponseEntity.status(e.status()).contentType(MediaType.TEXT_PLAIN).body(e.getMessage());
        }
        String verify = Inertia.baseUrl(request) + "/certificates/verify/" + cert.get("certificate_no");
        byte[] pdf = credentials.certificatePdf(cert, verify);
        String file = "ArtistikCity-certificate-" + slug(String.valueOf(cert.get("course_title"))) + "-" + cert.get("certificate_no") + ".pdf";
        return pdf(pdf, file, inline != null);
    }

    @GetMapping("/dashboard/portfolio/report")
    public ResponseEntity<byte[]> portfolio(HttpServletRequest request,
                                            @RequestParam(value = "approvedOnly", defaultValue = "1") String approvedOnly,
                                            @RequestParam(value = "curriculum", defaultValue = "1") String curriculum,
                                            @RequestParam(value = "comments", defaultValue = "1") String comments,
                                            @RequestParam(value = "courses", required = false) String courses,
                                            @RequestParam(value = "inline", required = false) String inline) {
        long userId = auth.userId(request);
        repo.syncEnrollments(userId);
        Set<Long> ids = courses == null || courses.isBlank() ? Set.of()
                : Arrays.stream(courses.split(",")).map(String::trim).filter(s -> s.matches("\\d+")).map(Long::valueOf).collect(Collectors.toSet());
        byte[] pdf = credentials.portfolioPdf(userId, new CredentialsService.PortfolioOptions(
                "1".equals(approvedOnly), "1".equals(curriculum), "1".equals(comments), ids), Inertia.baseUrl(request));
        Row user = auth.user(request);
        String name = user == null ? "student" : slug(user.str("name", "student"));
        return pdf(pdf, "ArtistikCity-portfolio-" + name + "-" + LocalDate.now() + ".pdf", inline != null);
    }

    @GetMapping("/certificates/verify/{number}")
    public ResponseEntity<String> verify(HttpServletRequest request, @PathVariable("number") String number) {
        Map<String, Object> cert = number.matches("[A-Za-z0-9-]{6,40}") ? credentials.certificate(number.toUpperCase()) : null;
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("number", number);
        if (cert != null) {
            Map<String, Object> pub = new LinkedHashMap<>();
            for (String k : List.of("certificate_no", "student_name", "course_title", "medium_name", "skill_name", "instructor_name", "duration", "course_type_id")) {
                pub.put(k, cert.get(k) == null ? null : String.valueOf(cert.get(k)));
            }
            pub.put("issued_on", CredentialsService.longDate(cert.get("issued_at")));
            props.put("certificate", pub);
        } else {
            props.put("certificate", null);
        }
        return inertia.render(request, "Studio/VerifyCertificate", props);
    }

    private static ResponseEntity<byte[]> pdf(byte[] bytes, String filename, boolean inline) {
        ContentDisposition cd = (inline ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(filename, StandardCharsets.UTF_8).build();
        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString()).header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(bytes);
    }

    private static String slug(String s) {
        String out = Objects.toString(s, "").toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return out.isEmpty() ? "file" : (out.length() > 50 ? out.substring(0, 50) : out);
    }
}
