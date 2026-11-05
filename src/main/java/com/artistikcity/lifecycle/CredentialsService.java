package com.artistikcity.lifecycle;

import com.artistikcity.support.Storage;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Student credentials: course certificates (eligibility, issuing, verification) and the
 * printable PDF portfolio. PDFs are rendered with OpenHTMLtoPDF from generated XHTML.
 *
 * Certificate rule: every lesson of the course is complete (when the course has lessons)
 * AND at least one assignment milestone for that course has been Approved by a reviewer.
 */
@Service
public class CredentialsService {

    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    // light document palette: charcoal text on white, pale grey panels and hairlines, one brand accent used sparingly
    static final String INK = "#2C3E50";       // headings, names, key figures
    static final String SOFT = "#34495E";      // body text, secondary headings
    static final String MUTED = "#6C7A89";     // captions, labels
    static final String RULE = "#DDE3E8";      // hairlines and borders
    static final String PANEL = "#F8F9FA";     // panels, cover background
    static final String PAPER = "#FFFFFF";     // certificate sheet
    static final String TINT = "#ECF0F1";      // table stripes, progress track
    static final String ACCENT = "#C2185B";    // ArtistikCity rose, for small markers only
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LifecycleRepository repo;
    private final Storage storage;

    public CredentialsService(LifecycleRepository repo, Storage storage) {
        this.repo = repo;
        this.storage = storage;
    }

    // ================================================================ eligibility

    /** One entry per enrollment with requirement progress and the issued certificate (if any). */
    public List<Map<String, Object>> credentials(long userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> e : repo.enrollments(userId)) {
            long enrollmentId = num(e.get("id"));
            long courseId = num(e.get("course_id"));
            long total = num(e.get("total_lessons"));
            long done = num(e.get("completed_lessons"));
            long approved = repo.count("select count(*) from student_submissions where user_id = ? and course_id = ? and admin_status = 'Approved'", userId, courseId);
            long pending = repo.count("select count(*) from student_submissions where user_id = ? and course_id = ? and admin_status = 'Pending Review'", userId, courseId);
            boolean lessonsOk = total == 0 || done >= total;
            boolean milestoneOk = approved > 0;
            Map<String, Object> c = new LinkedHashMap<>(e);
            c.put("approved_works", approved);
            c.put("pending_works", pending);
            c.put("lessons_ok", lessonsOk);
            c.put("milestone_ok", milestoneOk);
            c.put("eligible", lessonsOk && milestoneOk);
            List<Map<String, Object>> cert = repo.list("select certificate_no, issued_at from course_certificates where enrollment_id = ?", enrollmentId);
            c.put("certificate", cert.isEmpty() ? null : cert.get(0));
            out.add(c);
        }
        return out;
    }

    /** Issues (or returns the existing) certificate for an eligible enrollment. */
    public Map<String, Object> issue(long userId, long courseId) {
        Map<String, Object> cred = credentials(userId).stream()
                .filter(c -> num(c.get("course_id")) == courseId).findFirst()
                .orElseThrow(() -> new LifecycleDtos.LifecycleException(404, "You're not enrolled in this course."));
        if (cred.get("certificate") != null) {
            return certificate(String.valueOf(((Map<?, ?>) cred.get("certificate")).get("certificate_no")));
        }
        if (!Boolean.TRUE.equals(cred.get("eligible"))) {
            throw new LifecycleDtos.LifecycleException(403, "Complete all lessons and get one assignment approved to earn this certificate.");
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            String no = newNumber();
            try {
                repo.update("insert into course_certificates (enrollment_id, user_id, course_id, certificate_no, issued_at) values (?, ?, ?, ?, ?)",
                        num(cred.get("id")), userId, courseId, no, Timestamp.valueOf(LocalDateTime.now()));
                return certificate(no);
            } catch (DuplicateKeyException ex) {
                List<Map<String, Object>> existing = repo.list("select certificate_no from course_certificates where enrollment_id = ?", num(cred.get("id")));
                if (!existing.isEmpty()) return certificate(String.valueOf(existing.get(0).get("certificate_no")));
            }
        }
        throw new IllegalStateException("Could not allocate a certificate number");
    }

    /** Public certificate record by number (null if unknown). */
    public Map<String, Object> certificate(String number) {
        List<Map<String, Object>> r = repo.list("select cc.certificate_no, cc.issued_at, cc.user_id, cc.course_id, u.name as student_name,"
                + " c.title as course_title, c.duration, c.time_required, c.sessions, c.course_type_id, c.age_group, m.name as medium_name,"
                + " s.name as skill_name, a.name as instructor_name, a.profile_title as instructor_title"
                + " from course_certificates cc join users u on u.id = cc.user_id join courses c on c.id = cc.course_id"
                + " left join mediums m on m.id = c.medium_id left join skills s on s.id = c.skill_id left join admins a on a.id = c.admin_id"
                + " where cc.certificate_no = ?", number);
        return r.isEmpty() ? null : r.get(0);
    }

    private static String newNumber() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("AC-").append(LocalDate.now().getYear()).append('-');
        for (int i = 0; i < 8; i++) sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return sb.toString();
    }

    // ================================================================ certificate PDF

    public byte[] certificatePdf(Map<String, Object> cert, String verifyUrl) {
        boolean workshop = "2".equals(String.valueOf(cert.get("course_type_id")));
        String duration = cert.get("duration") == null ? "" : cert.get("duration") + (workshop ? " days" : " weeks");
        String details = String.join("  |  ", List.of(
                        duration, cert.get("medium_name") == null ? "" : String.valueOf(cert.get("medium_name")),
                        cert.get("skill_name") == null ? "" : String.valueOf(cert.get("skill_name")) + " level")
                .stream().filter(x -> !x.isBlank()).toList());
        String html = "<html><head><style>"
                + "@page { size: A4 landscape; margin: 0; }"
                + "body { margin: 0; font-family: Helvetica, Arial, sans-serif; color: " + INK + "; }"
                + ".sheet { position: relative; width: 297mm; height: 209mm; background: " + PAPER + "; }"
                + ".frame { position: absolute; top: 10mm; left: 10mm; right: 10mm; bottom: 10mm; border: 0.6mm solid " + INK + "; }"
                + ".inner { position: absolute; top: 13mm; left: 13mm; right: 13mm; bottom: 13mm; border: 0.3mm solid " + RULE + "; }"
                + ".band { position: absolute; top: 13mm; bottom: 13mm; left: 13mm; width: 12mm; background: " + PANEL + "; border-right: 0.3mm solid " + RULE + "; }"
                + ".band div { position: absolute; left: 5.4mm; top: 0; bottom: 0; width: 1.2mm; background: " + ACCENT + "; }"
                + ".content { position: absolute; top: 26mm; left: 40mm; right: 30mm; }"
                + ".logo { height: 16mm; }"
                + ".kicker { margin-top: 12mm; font-size: 10.5pt; letter-spacing: 3pt; text-transform: uppercase; color: " + SOFT + "; font-weight: bold; }"
                + "h1 { font-size: 34pt; margin: 3mm 0 0; letter-spacing: -0.5pt; color: " + INK + "; }"
                + ".presented { margin-top: 8mm; font-size: 12pt; color: " + MUTED + "; }"
                + ".name { font-size: 30pt; font-weight: bold; margin-top: 2mm; border-bottom: 0.4mm solid " + RULE + "; padding-bottom: 2mm; color: " + INK + "; }"
                + ".for { margin-top: 6mm; font-size: 12pt; color: " + MUTED + "; }"
                + ".course { font-size: 18pt; font-weight: bold; margin-top: 1mm; color: " + SOFT + "; }"
                + ".details { margin-top: 2mm; font-size: 10.5pt; color: " + MUTED + "; }"
                + ".footer { position: absolute; left: 40mm; right: 30mm; bottom: 24mm; }"
                + ".col { display: inline-block; width: 31%; vertical-align: bottom; margin-right: 2%; }"
                + ".line { border-top: 0.3mm solid " + SOFT + "; padding-top: 1.5mm; font-size: 10pt; color: " + INK + "; }"
                + ".muted { color: " + MUTED + "; font-size: 8.5pt; }"
                + ".sig { font-size: 16pt; font-style: italic; margin-bottom: 1mm; color: " + SOFT + "; }"
                + ".seal { position: absolute; right: 30mm; top: 26mm; width: 34mm; height: 34mm; border-radius: 17mm; background: " + PANEL + "; border: 0.6mm solid " + RULE + "; color: " + INK + "; text-align: center; }"
                + ".seal div { padding-top: 10mm; font-size: 8pt; letter-spacing: 1.5pt; font-weight: bold; color: " + SOFT + "; }"
                + ".seal b { display: block; font-size: 15pt; color: " + INK + "; letter-spacing: 0; margin-top: 1mm; }"
                + "</style></head><body><div class=\"sheet\"><div class=\"frame\"></div><div class=\"inner\"></div><div class=\"band\"><div></div></div>"
                + "<div class=\"seal\"><div>ARTISTIKCITY<b>" + LocalDateTime.parse(ts(cert.get("issued_at"))).getYear() + "</b>CERTIFIED</div></div>"
                + "<div class=\"content\">" + logoImg("logo")
                + "<div class=\"kicker\">Certificate of completion</div>"
                + "<h1>" + esc(workshop ? "Workshop Certificate" : "Course Certificate") + "</h1>"
                + "<div class=\"presented\">This certifies that</div>"
                + "<div class=\"name\">" + esc(cert.get("student_name")) + "</div>"
                + "<div class=\"for\">has successfully completed all lessons and an instructor-approved project in</div>"
                + "<div class=\"course\">" + esc(cert.get("course_title")) + "</div>"
                + (details.isBlank() ? "" : "<div class=\"details\">" + esc(details) + "</div>")
                + "</div><div class=\"footer\">"
                + "<div class=\"col\"><div class=\"sig\">" + esc(cert.get("instructor_name")) + "</div><div class=\"line\">" + esc(cert.get("instructor_name"))
                + "<br/><span class=\"muted\">" + esc(cert.get("instructor_title") == null ? "Instructor" : cert.get("instructor_title")) + "</span></div></div>"
                + "<div class=\"col\"><div class=\"sig\">&#160;</div><div class=\"line\">" + esc(longDate(cert.get("issued_at")))
                + "<br/><span class=\"muted\">Date of issue</span></div></div>"
                + "<div class=\"col\"><div class=\"sig\">&#160;</div><div class=\"line\">" + esc(cert.get("certificate_no"))
                + "<br/><span class=\"muted\">Verify at " + esc(verifyUrl) + "</span></div></div>"
                + "</div></div></body></html>";
        return render(html);
    }

    // ================================================================ portfolio PDF

    public record PortfolioOptions(boolean approvedOnly, boolean curriculum, boolean comments, Set<Long> courseIds) {
    }

    public byte[] portfolioPdf(long userId, PortfolioOptions opt, String baseUrl) {
        Map<String, Object> user = repo.list("select id, name, email, location, profile_title, profile_description, created_at from users where id = ?", userId).get(0);
        List<Map<String, Object>> creds = credentials(userId).stream()
                .filter(c -> opt.courseIds() == null || opt.courseIds().isEmpty() || opt.courseIds().contains(num(c.get("course_id"))))
                .toList();
        List<Map<String, Object>> subs = repo.submissionsForUser(userId);
        long totalLessons = creds.stream().mapToLong(c -> num(c.get("completed_lessons"))).sum();
        long approvedCount = subs.stream().filter(s -> "Approved".equals(s.get("admin_status"))).count();
        long certCount = creds.stream().filter(c -> c.get("certificate") != null).count();

        StringBuilder h = new StringBuilder();
        // The cover is ordinary content on page 1 (@page :first has no margins and no footer), so the PDF never opens on an
        // empty page. (A named page for the cover forced a page break before it, which left page 1 blank.)
        h.append("<html><head><style>")
                .append("@page { size: A4; margin: 18mm 16mm 20mm 16mm; @bottom-left { content: 'ArtistikCity portfolio  |  ").append(css(user.get("name")))
                .append("'; font-family: Helvetica; font-size: 8pt; color: ").append(MUTED).append("; } @bottom-right { content: 'Page ' counter(page) ' of ' counter(pages); font-family: Helvetica; font-size: 8pt; color: ").append(MUTED).append("; } }")
                .append("@page :first { margin: 0; @bottom-left { content: none; } @bottom-right { content: none; } }")
                .append("body { margin: 0; font-family: Helvetica, Arial, sans-serif; color: ").append(SOFT).append("; font-size: 10pt; line-height: 1.45; }")
                .append(".cover { width: 210mm; height: 296mm; background: ").append(PANEL).append("; color: ").append(INK).append("; position: relative; page-break-after: always; }")
                .append(".cover .band { position: absolute; left: 0; top: 0; bottom: 0; width: 10mm; background: ").append(TINT).append("; border-right: 0.3mm solid ").append(RULE).append("; }")
                .append(".cover .mark { position: absolute; left: 4.4mm; top: 40mm; width: 1.2mm; height: 60mm; background: ").append(ACCENT).append("; }")
                .append(".cover .inner { position: absolute; left: 30mm; right: 22mm; top: 40mm; }")
                .append(".cover .kicker { font-size: 10pt; letter-spacing: 3pt; text-transform: uppercase; color: ").append(MUTED).append("; font-weight: bold; margin-top: 30mm; }")
                .append(".cover h1 { font-size: 40pt; line-height: 1.05; margin: 4mm 0 0; color: ").append(INK).append("; }")
                .append(".cover .who { font-size: 13pt; color: ").append(SOFT).append("; margin-top: 6mm; }")
                .append(".cover .rule { margin-top: 10mm; width: 40mm; border-top: 0.6mm solid ").append(INK).append("; }")
                .append(".cover .stats { position: absolute; left: 30mm; right: 22mm; bottom: 36mm; }")
                .append(".cover .stat { display: inline-block; width: 22%; margin-right: 3%; border-top: 0.4mm solid ").append(RULE).append("; padding-top: 3mm; vertical-align: top; }")
                .append(".cover .stat b { display: block; font-size: 22pt; color: ").append(INK).append("; } .cover .stat span { font-size: 7.5pt; letter-spacing: 1pt; text-transform: uppercase; color: ").append(MUTED).append("; }")
                .append(".cover .date { position: absolute; left: 30mm; bottom: 20mm; font-size: 8.5pt; color: ").append(MUTED).append("; }")
                .append(".page { padding: 0; }")
                .append("h2 { font-size: 20pt; margin: 0 0 2mm; letter-spacing: -0.3pt; color: ").append(INK).append("; }")
                .append("h3 { font-size: 12pt; margin: 6mm 0 2mm; color: ").append(INK).append("; }")
                .append(".eyebrow { font-size: 8pt; letter-spacing: 2pt; text-transform: uppercase; color: ").append(MUTED).append("; font-weight: bold; }")
                .append(".eyebrow:before { content: ''; display: inline-block; width: 3mm; height: 0.8mm; background: ").append(ACCENT).append("; margin-right: 2mm; vertical-align: middle; }")
                .append(".muted { color: ").append(MUTED).append("; } .small { font-size: 8.5pt; }")
                .append(".course { page-break-before: always; }")
                .append(".meta { width: 100%; border-collapse: collapse; margin: 3mm 0; } .meta td { padding: 1.6mm 2mm; border-bottom: 0.2mm solid ").append(RULE).append("; font-size: 9pt; vertical-align: top; } .meta td.k { width: 32%; color: ").append(MUTED).append("; background: ").append(PANEL).append("; }")
                .append(".chip { display: inline-block; padding: 0.6mm 2.2mm; border-radius: 2mm; font-size: 8pt; font-weight: bold; border: 0.2mm solid ").append(RULE).append("; }")
                .append(".ok { background: #E8F6EE; color: #1E7B4F; } .wait { background: #FDF6E3; color: #8A6D1F; } .no { background: #FBECEC; color: #A23B3B; } .ink { background: ").append(TINT).append("; color: ").append(INK).append("; }")
                .append(".unit { margin: 3mm 0 1mm; font-weight: bold; font-size: 9.5pt; color: ").append(INK).append("; }")
                .append(".lesson { padding: 1.2mm 0 1.2mm 7mm; border-bottom: 0.2mm dotted ").append(RULE).append("; font-size: 9pt; position: relative; }")
                .append(".lesson .box { position: absolute; left: 0; top: 1.8mm; width: 3.2mm; height: 3.2mm; border: 0.3mm solid ").append(MUTED).append("; }")
                .append(".lesson .box.done { background: ").append(SOFT).append("; border-color: ").append(SOFT).append("; }")
                .append(".work { page-break-inside: avoid; margin: 5mm 0; border: 0.3mm solid ").append(RULE).append("; background: #FFFFFF; }")
                .append(".work img { width: 100%; max-height: 120mm; }")
                .append(".work .body { padding: 3mm 4mm; background: ").append(PANEL).append("; border-top: 0.3mm solid ").append(RULE).append("; }")
                .append(".comment { margin-top: 2mm; padding: 2.5mm 3mm; background: #FFFFFF; border-left: 0.8mm solid ").append(RULE).append("; font-size: 9pt; }")
                .append(".toc td { padding: 1.8mm 0; border-bottom: 0.2mm solid ").append(RULE).append("; }")
                .append(".bar { height: 2mm; background: ").append(TINT).append("; margin-top: 1mm; } .bar div { height: 2mm; background: ").append(SOFT).append("; }")
                .append("</style></head><body>");

        // ---- cover (page 1)
        h.append("<div class=\"cover\"><div class=\"band\"></div><div class=\"mark\"></div><div class=\"inner\">").append(logoImg("logo"))
                .append("<div class=\"kicker\">Artist portfolio</div><h1>").append(esc(user.get("name"))).append("</h1>")
                .append("<div class=\"who\">").append(esc(user.get("profile_title") == null ? "ArtistikCity student artist" : user.get("profile_title")))
                .append(user.get("location") == null ? "" : " &#183; " + esc(user.get("location"))).append("</div><div class=\"rule\"></div></div>")
                .append("<div class=\"stats\">")
                .append(stat(creds.size(), "Courses")).append(stat(totalLessons, "Lessons done"))
                .append(stat(approvedCount, "Approved works")).append(stat(certCount, "Certificates")).append("</div>")
                .append("<div class=\"date\">Generated ").append(esc(LocalDate.now().format(LONG))).append(" &#183; artistikcity.com</div></div>");

        // ---- summary + contents
        h.append("<div class=\"eyebrow\">Overview</div><h2>About the artist</h2>");
        h.append("<p>").append(esc(user.get("profile_description") == null || String.valueOf(user.get("profile_description")).isBlank()
                ? user.get("name") + " is a student artist at ArtistikCity, learning through live, instructor-led courses with reviewed assignment milestones."
                : strip(String.valueOf(user.get("profile_description"))))).append("</p>");
        h.append("<p class=\"muted small\">Member since ").append(esc(longDate(user.get("created_at")))).append(". Every artwork marked Approved has been reviewed by an ArtistikCity instructor.</p>");
        h.append("<h3>Contents</h3><table class=\"toc\" style=\"width:100%; border-collapse:collapse\">");
        int i = 1;
        for (Map<String, Object> c : creds) {
            long works = subs.stream().filter(s -> num(s.get("course_id")) == num(c.get("course_id")) && (!opt.approvedOnly() || "Approved".equals(s.get("admin_status")))).count();
            h.append("<tr><td style=\"width:8mm\" class=\"muted\">").append(String.format("%02d", i++)).append("</td><td><b>").append(esc(c.get("title")))
                    .append("</b></td><td class=\"small muted\" style=\"text-align:right\">").append(works).append(" work").append(works == 1 ? "" : "s")
                    .append(c.get("certificate") != null ? " &#183; certified" : "").append("</td></tr>");
        }
        if (creds.isEmpty()) h.append("<tr><td class=\"muted\">No courses yet.</td></tr>");
        h.append("</table>");

        // ---- per course
        int n = 1;
        for (Map<String, Object> c : creds) {
            long courseId = num(c.get("course_id"));
            Map<String, Object> course = repo.list("select c.*, m.name as medium_name, s.name as skill_name, a.name as teacher_name, a.profile_title as teacher_title"
                    + " from courses c left join mediums m on m.id = c.medium_id left join skills s on s.id = c.skill_id left join admins a on a.id = c.admin_id where c.id = ?", courseId).get(0);
            boolean workshop = "2".equals(String.valueOf(course.get("course_type_id")));
            long pct = num(c.get("progress_percentage"));
            h.append("<div class=\"course\"><div class=\"eyebrow\">").append(String.format("%02d", n++)).append(" &#183; ").append(workshop ? "Workshop" : "Course").append("</div>")
                    .append("<h2>").append(esc(course.get("title"))).append("</h2>");
            if (course.get("sub_title") != null) h.append("<p class=\"muted\">").append(esc(course.get("sub_title"))).append("</p>");
            h.append("<table class=\"meta\">")
                    .append(row("Instructor", course.get("teacher_name") + (course.get("teacher_title") == null ? "" : ", " + course.get("teacher_title"))))
                    .append(row("Medium / level", nz(course.get("medium_name")) + (course.get("skill_name") == null ? "" : " / " + course.get("skill_name"))))
                    .append(row("Duration", course.get("duration") == null ? "" : course.get("duration") + (workshop ? " days" : " weeks")
                            + (course.get("time_required") == null ? "" : ", " + course.get("time_required") + " hrs per session")))
                    .append(row("Enrolled", longDate(c.get("created_at"))))
                    .append("<tr><td class=\"k\">Progress</td><td>").append(pct).append("% &#183; ").append(num(c.get("completed_lessons"))).append(" of ")
                    .append(num(c.get("total_lessons"))).append(" lessons<div class=\"bar\"><div style=\"width:").append(Math.min(100, pct)).append("%\"></div></div></td></tr>")
                    .append("<tr><td class=\"k\">Certificate</td><td>");
            if (c.get("certificate") != null) {
                Map<?, ?> cert = (Map<?, ?>) c.get("certificate");
                h.append("<span class=\"chip ink\">").append(esc(cert.get("certificate_no"))).append("</span> <span class=\"small muted\">issued ")
                        .append(esc(longDate(cert.get("issued_at")))).append(" &#183; verify at ").append(esc(baseUrl + "/certificates/verify/" + cert.get("certificate_no"))).append("</span>");
            } else if (Boolean.TRUE.equals(c.get("eligible"))) {
                h.append("<span class=\"chip ok\">Eligible</span> <span class=\"small muted\">download it from My Studio</span>");
            } else {
                h.append("<span class=\"chip wait\">In progress</span>");
            }
            h.append("</td></tr></table>");

            if (course.get("introduction") != null) h.append("<p>").append(esc(strip(String.valueOf(course.get("introduction"))))).append("</p>");

            if (opt.curriculum()) {
                List<Map<String, Object>> modules = repo.modules(courseId);
                List<Map<String, Object>> lessons = repo.lessons(courseId);
                Map<String, Object> enr = repo.enrollment(userId, courseId);
                Set<String> done = enr == null ? Set.of() : repo.completedLessonIds(num(enr.get("id"))).stream().map(String::valueOf).collect(Collectors.toSet());
                h.append("<h3>Curriculum</h3>");
                if (modules.isEmpty()) {
                    String summary = course.get("summary_details") == null ? "" : strip(String.valueOf(course.get("summary_details")));
                    h.append("<p class=\"muted\">").append(esc(summary.isBlank() ? "Detailed curriculum not published for this batch." : summary)).append("</p>");
                }
                int u = 1;
                for (Map<String, Object> m : modules) {
                    h.append("<div class=\"unit\">Unit ").append(u++).append(" &#183; ").append(esc(m.get("module_title")))
                            .append(m.get("module_duration") == null ? "" : " <span class=\"muted small\">(" + esc(m.get("module_duration")) + ")</span>").append("</div>");
                    for (Map<String, Object> l : lessons) {
                        if (!String.valueOf(l.get("module_id")).equals(String.valueOf(m.get("id")))) continue;
                        boolean isDone = done.contains(String.valueOf(l.get("id")));
                        String desc = strip(String.valueOf(l.get("lesson_description") == null ? "" : l.get("lesson_description")));
                        h.append("<div class=\"lesson\"><span class=\"box").append(isDone ? " done" : "").append("\"></span><b>").append(esc(l.get("lesson_title"))).append("</b>")
                                .append(desc.isBlank() ? "" : " <span class=\"muted\">&#8212; " + esc(desc) + "</span>").append("</div>");
                    }
                }
            }

            List<Map<String, Object>> works = subs.stream().filter(s -> num(s.get("course_id")) == courseId && (!opt.approvedOnly() || "Approved".equals(s.get("admin_status")))).toList();
            h.append("<h3>Artworks</h3>");
            if (works.isEmpty()) h.append("<p class=\"muted\">No ").append(opt.approvedOnly() ? "approved " : "").append("artworks for this course yet.</p>");
            for (Map<String, Object> w : works) {
                String st = String.valueOf(w.get("admin_status"));
                h.append("<div class=\"work\">").append(imageTag(String.valueOf(w.get("file_url")), esc(w.get("title"))))
                        .append("<div class=\"body\"><b style=\"font-size:11pt\">").append(esc(w.get("title"))).append("</b> ")
                        .append("<span class=\"chip ").append("Approved".equals(st) ? "ok" : "Rejected".equals(st) ? "no" : "wait").append("\">")
                        .append("Rejected".equals(st) ? "Changes requested" : esc(st)).append("</span>")
                        .append("<div class=\"small muted\">Submitted ").append(esc(longDate(w.get("created_at"))))
                        .append(w.get("reviewed_at") == null ? "" : " &#183; reviewed " + esc(longDate(w.get("reviewed_at"))))
                        .append("</div>");
                if (w.get("description") != null) h.append("<p style=\"margin:2mm 0 0\">").append(esc(w.get("description"))).append("</p>");
                if (opt.comments() && w.get("reviewer_feedback") != null) {
                    h.append("<div class=\"comment\"><b>Instructor comments").append(w.get("reviewer_name") == null ? "" : " &#183; " + esc(w.get("reviewer_name")))
                            .append(":</b> ").append(esc(w.get("reviewer_feedback"))).append("</div>");
                }
                h.append("</div></div>");
            }
            h.append("</div>");
        }

        h.append("<div style=\"page-break-before: always\"><div class=\"eyebrow\">About</div><h2>ArtistikCity</h2>")
                .append("<p>ArtistikCity runs live, instructor-led art courses and workshops for learners of every age. Students learn in guided classrooms, submit assignment milestones for personal review, and build a portfolio of approved work.</p>")
                .append("<p class=\"muted\">Certificates listed in this portfolio can be verified online using their certificate number at ")
                .append(esc(baseUrl + "/certificates/verify/")).append("&lt;number&gt;.</p></div>");
        h.append("</body></html>");
        return render(h.toString());
    }

    // ================================================================ helpers

    private String stat(long v, String label) {
        return "<div class=\"stat\"><b>" + v + "</b><span>" + esc(label) + "</span></div>";
    }

    private static String row(String k, Object v) {
        String s = v == null ? "" : String.valueOf(v);
        return s.isBlank() || "null".equals(s) ? "" : "<tr><td class=\"k\">" + esc(k) + "</td><td>" + esc(s) + "</td></tr>";
    }

    private static String nz(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** Embeds a stored image as a (down-scaled) JPEG data URI so the PDF is self-contained. */
    private String imageTag(String url, String alt) {
        try {
            String rel = Storage.pathOf(url).orElse(null);
            if (rel == null || !storage.exists(rel)) return "";
            return "<img src=\"data:image/jpeg;base64," + Base64.getEncoder().encodeToString(scaled(storage.read(rel), 1400)) + "\" alt=\"" + alt + "\"/>";
        } catch (Exception e) {
            return "";
        }
    }

    private String logoImg(String variant) {
        try (InputStream in = new ClassPathResource("static/assets/images/" + ("logo-white".equals(variant) ? "footerlogo.png" : "logo.png")).getInputStream()) {
            byte[] b = in.readAllBytes();
            return "<img class=\"logo\" style=\"height:14mm\" src=\"data:image/png;base64," + Base64.getEncoder().encodeToString(b) + "\" alt=\"ArtistikCity\"/>";
        } catch (Exception e) {
            return "<div style=\"font-size:18pt; font-weight:bold\">ArtistikCity</div>";
        }
    }

    private static byte[] scaled(byte[] bytes, int max) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
        if (src == null) throw new IllegalArgumentException("not an image");
        double k = Math.min(1.0, (double) max / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * k)), hgt = Math.max(1, (int) Math.round(src.getHeight() * k));
        BufferedImage out = new BufferedImage(w, hgt, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w, hgt);
        g.drawImage(src, 0, 0, w, hgt, null);
        g.dispose();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(out, "jpg", os);
        return os.toByteArray();
    }

    private static byte[] render(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not render PDF", e);
        }
    }

    static String esc(Object o) {
        if (o == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char ch : String.valueOf(o).toCharArray()) {
            switch (ch) {
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '&' -> sb.append("&amp;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> {
                    if (ch < 0x20 && ch != '\n' && ch != '\t') continue;
                    sb.append(ch > 0x7e ? "&#" + (int) ch + ";" : String.valueOf(ch));
                }
            }
        }
        return sb.toString();
    }

    private static String css(Object o) {
        return o == null ? "" : String.valueOf(o).replaceAll("[\\\\'\"<>&]", "");
    }

    private static String strip(String html) {
        return html.replaceAll("(?s)<[^>]*>", " ").replace("&nbsp;", " ").replace("&amp;", "&").replaceAll("\\s+", " ").trim();
    }

    private static String ts(Object o) {
        String s = String.valueOf(o).replace(' ', 'T');
        return s.length() > 19 ? s.substring(0, 19) : s;
    }

    static String longDate(Object o) {
        if (o == null) return "";
        try {
            return LocalDateTime.parse(ts(o)).format(LONG);
        } catch (Exception e) {
            return String.valueOf(o).length() >= 10 ? String.valueOf(o).substring(0, 10) : String.valueOf(o);
        }
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : o == null ? 0 : Long.parseLong(String.valueOf(o));
    }
}
