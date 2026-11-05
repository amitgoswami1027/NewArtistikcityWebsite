package com.artistikcity.lifecycle;

import com.artistikcity.http.Auth;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.documents.StudentDocuments;
import com.artistikcity.http.Csrf;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
 * Certificates & portfolio reports in the student studio. Every PDF is rendered in memory, stored once in private
 * storage (S3 in production, see {@link com.artistikcity.documents.StudentDocuments}) and recorded in
 * {@code student_documents}; the browser then opens it through a short-lived link.
 * <ul>
 *   <li>GET /dashboard/certificates - certificates and portfolio report builder (auth).</li>
 *   <li>POST /api/v1/studio/portfolio {approvedOnly, curriculum, comments, courses[]} - makes and stores a portfolio
 *       report; answers {document, viewUrl, downloadUrl} (presigned S3 links, 10 minutes).</li>
 *   <li>GET /api/v1/studio/documents/{id}/link?inline=1 - a fresh link to a stored report or certificate.</li>
 *   <li>GET /dashboard/documents/{id}/file - the owner's PDF: redirect to S3, or streamed with local storage.</li>
 *   <li>GET /dashboard/certificates/{courseId}/download - issues (first time), stores and opens the certificate PDF.</li>
 *   <li>GET /dashboard/portfolio/report - old direct link: makes a report and opens it.</li>
 *   <li>GET /certificates/verify/{number} - public verification page.</li>
 * </ul>
 */
@Controller
public class StudentCredentialsController {

    private final Inertia inertia;
    private final Auth auth;
    private final LifecycleRepository repo;
    private final CredentialsService credentials;
    private final StudentDocuments documents;
    private final Json json;

    public StudentCredentialsController(Inertia inertia, Auth auth, LifecycleRepository repo, CredentialsService credentials,
                                        StudentDocuments documents, Json json) {
        this.inertia = inertia;
        this.auth = auth;
        this.repo = repo;
        this.credentials = credentials;
        this.documents = documents;
        this.json = json;
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
        props.put("reports", documents.portfolios(userId).stream().map(StudentCredentialsController::docView).toList());
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
        String number = String.valueOf(cert.get("certificate_no"));
        Row doc = documents.certificate(number);
        if (doc == null || !documents.exists(doc)) {
            String verify = Inertia.baseUrl(request) + "/certificates/verify/" + number;
            byte[] pdf = credentials.certificatePdf(cert, verify);
            String file = "ArtistikCity-certificate-" + slug(String.valueOf(cert.get("course_title"))) + "-" + number + ".pdf";
            doc = documents.saveCertificate(userId, courseId, number, pdf, file);
        }
        return open(doc, inline != null);
    }

    /** JSON {approvedOnly, curriculum, comments, courses: [ids]}: renders, stores and links a new portfolio report. */
    @PostMapping("/api/v1/studio/portfolio")
    public ResponseEntity<String> createPortfolio(HttpServletRequest request) throws java.io.IOException {
        Long userId = auth.userId(request);
        if (userId == null) return jsonError(HttpStatus.UNAUTHORIZED, "Please log in again.");
        if (!Csrf.matches(request)) return jsonError(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
        Map<String, Object> b = json.decodeMap(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        if (b == null) b = Map.of();
        Set<Long> ids = new java.util.TreeSet<>();
        if (b.get("courses") instanceof List<?> l) {
            for (Object o : l) if (o instanceof Number n) ids.add(n.longValue());
        }
        boolean approvedOnly = !Boolean.FALSE.equals(b.get("approvedOnly"));
        boolean curriculum = !Boolean.FALSE.equals(b.get("curriculum"));
        boolean comments = !Boolean.FALSE.equals(b.get("comments"));
        Row doc = makePortfolio(request, userId, approvedOnly, curriculum, comments, ids);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("document", docView(doc));
        res.put("viewUrl", documents.link(doc, true).url());
        res.put("downloadUrl", documents.link(doc, false).url());
        res.put("expiresInSeconds", StudentDocuments.LINK_TTL.toSeconds());
        return jsonOk(res);
    }

    /** A fresh short-lived link to one of the student's stored documents. */
    @GetMapping("/api/v1/studio/documents/{id}/link")
    public ResponseEntity<String> link(HttpServletRequest request, @PathVariable("id") long id,
                                       @RequestParam(value = "inline", required = false) String inline) {
        Long userId = auth.userId(request);
        if (userId == null) return jsonError(HttpStatus.UNAUTHORIZED, "Please log in again.");
        Row doc = documents.forOwner(userId, id);
        if (doc == null) return jsonError(HttpStatus.NOT_FOUND, "This report isn't available any more.");
        return jsonOk(Map.of("url", documents.link(doc, inline != null).url(), "expiresInSeconds", StudentDocuments.LINK_TTL.toSeconds()));
    }

    /** The owner's PDF: a redirect to the presigned S3 URL, or the file itself with local storage. */
    @GetMapping("/dashboard/documents/{id}/file")
    public ResponseEntity<?> file(HttpServletRequest request, @PathVariable("id") long id,
                                  @RequestParam(value = "inline", required = false) String inline) {
        Row doc = documents.forOwner(auth.userId(request), id);
        if (doc == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN).body("Not found.");
        return open(doc, inline != null);
    }

    /** Old direct link (bookmarks, e-mails): makes a report with the given options and opens it. */
    @GetMapping("/dashboard/portfolio/report")
    public ResponseEntity<?> portfolio(HttpServletRequest request,
                                       @RequestParam(value = "approvedOnly", defaultValue = "1") String approvedOnly,
                                       @RequestParam(value = "curriculum", defaultValue = "1") String curriculum,
                                       @RequestParam(value = "comments", defaultValue = "1") String comments,
                                       @RequestParam(value = "courses", required = false) String courses,
                                       @RequestParam(value = "inline", required = false) String inline) {
        long userId = auth.userId(request);
        Set<Long> ids = courses == null || courses.isBlank() ? Set.of()
                : Arrays.stream(courses.split(",")).map(String::trim).filter(s -> s.matches("\\d+")).map(Long::valueOf).collect(Collectors.toSet());
        Row doc = makePortfolio(request, userId, "1".equals(approvedOnly), "1".equals(curriculum), "1".equals(comments), ids);
        return open(doc, inline != null);
    }

    private Row makePortfolio(HttpServletRequest request, long userId, boolean approvedOnly, boolean curriculum, boolean comments, Set<Long> ids) {
        repo.syncEnrollments(userId);
        byte[] pdf = credentials.portfolioPdf(userId, new CredentialsService.PortfolioOptions(approvedOnly, curriculum, comments, ids), Inertia.baseUrl(request));
        Row user = auth.user(request);
        String name = user == null ? "student" : slug(user.str("name", "student"));
        String options = (approvedOnly ? "approved" : "all") + (curriculum ? ",curriculum" : "") + (comments ? ",comments" : "")
                + (ids.isEmpty() ? "" : ",courses:" + ids.stream().map(String::valueOf).collect(Collectors.joining("+")));
        return documents.savePortfolio(userId, pdf, "ArtistikCity-portfolio-" + name + "-" + LocalDate.now() + ".pdf", options);
    }

    /** Sends the browser to the document: presigned S3 URL (redirect), or streamed from local private storage. */
    private ResponseEntity<?> open(Row doc, boolean inline) {
        StudentDocuments.Link link = documents.link(doc, inline);
        if (link.direct()) {
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, link.url()).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        Storage.PrivateFile f = documents.open(doc).orElse(null);
        if (f == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_PLAIN).body("This file is missing. Make a new one.");
        try (java.io.InputStream in = f.body()) {
            return pdf(in.readAllBytes(), doc.str("file_name"), inline);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static Map<String, Object> docView(Row d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.get("id"));
        m.put("fileName", d.get("file_name"));
        m.put("sizeBytes", d.get("size_bytes"));
        m.put("options", d.get("options"));
        m.put("createdAt", d.str("created_at"));
        return m;
    }

    private ResponseEntity<String> jsonOk(Object body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.CACHE_CONTROL, "no-store").body(json.encode(body));
    }

    private ResponseEntity<String> jsonError(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of("message", message)));
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
