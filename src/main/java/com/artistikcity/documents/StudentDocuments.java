package com.artistikcity.documents;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Generated student PDFs (portfolio reports and course certificates). The PDF engine renders into memory; the bytes go
 * straight to private storage, which is the S3 bucket under {@code private/documents/...} in production
 * ({@code STORAGE_DRIVER=s3}) and {@code storage-private/documents/...} on a developer PC. Nothing is written to the
 * public storage folder, and a document is never reachable at /storage/**.
 *
 * <p>Each stored file is a row in {@code student_documents} with its storage key (the S3 object key), so it can be
 * opened again later. Students get short-lived links: a presigned S3 URL (default 10 minutes), or, with local storage,
 * the app streams the file after checking the owner ({@code GET /dashboard/documents/{id}/file}).
 */
@Service
public class StudentDocuments {

    private static final Logger log = LoggerFactory.getLogger(StudentDocuments.class);
    public static final String PORTFOLIO = "PORTFOLIO";
    public static final String CERTIFICATE = "CERTIFICATE";
    /** How many portfolio reports a student keeps; older ones are deleted when a new one is made. */
    static final int KEEP_PORTFOLIOS = 10;
    public static final Duration LINK_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Db db;
    private final Storage storage;

    public StudentDocuments(Db db, Storage storage) {
        this.db = db;
        this.storage = storage;
    }

    /** A stored document and the links to open it. */
    public record Link(String url, boolean direct) {
    }

    /** Stores a newly generated portfolio report; returns its row. Keeps the newest {@value #KEEP_PORTFOLIOS}. */
    public Row savePortfolio(long userId, byte[] pdf, String fileName, String options) {
        LocalDateTime now = LocalDateTime.now();
        String rel = "documents/portfolios/" + userId + "/" + now.format(STAMP) + "-" + token() + ".pdf";
        storage.putPrivate(rel, pdf);
        long id = db.insert("student_documents", Row.of("user_id", userId, "kind", PORTFOLIO, "storage_key", Storage.privateKey(rel),
                "file_name", fileName, "content_type", "application/pdf", "size_bytes", pdf.length, "options", options, "created_at", now));
        prune(userId);
        return db.find("student_documents", id);
    }

    /** Stores (or replaces) the PDF of an issued course certificate; returns its row. */
    public Row saveCertificate(long userId, long courseId, String certificateNo, byte[] pdf, String fileName) {
        String rel = "documents/certificates/" + certificateNo.replaceAll("[^A-Za-z0-9-]", "") + ".pdf";
        storage.putPrivate(rel, pdf);
        Row existing = certificate(certificateNo);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            db.updateById("student_documents", existing.get("id"), Row.of("storage_key", Storage.privateKey(rel), "file_name", fileName,
                    "size_bytes", pdf.length, "created_at", now));
            return db.find("student_documents", existing.get("id"));
        }
        long id = db.insert("student_documents", Row.of("user_id", userId, "kind", CERTIFICATE, "course_id", courseId, "certificate_no", certificateNo,
                "storage_key", Storage.privateKey(rel), "file_name", fileName, "content_type", "application/pdf", "size_bytes", pdf.length, "created_at", now));
        return db.find("student_documents", id);
    }

    public Row certificate(String certificateNo) {
        return db.first("select * from student_documents where kind = ? and certificate_no = ? limit 1", CERTIFICATE, certificateNo);
    }

    /** The document if it belongs to this student, else null. */
    public Row forOwner(long userId, long id) {
        return db.first("select * from student_documents where id = ? and user_id = ?", id, userId);
    }

    /** The student's portfolio reports, newest first. */
    public List<Row> portfolios(long userId) {
        return db.select("select id, file_name, size_bytes, options, created_at from student_documents where user_id = ? and kind = ?"
                + " order by created_at desc, id desc limit " + KEEP_PORTFOLIOS, userId, PORTFOLIO);
    }

    /** Is the stored file still there (it can be missing after a restore of an older database)? */
    public boolean exists(Row doc) {
        return storage.existsPrivate(relative(doc));
    }

    /**
     * How the browser opens a document: a presigned S3 URL (direct) or, with local storage, the app's own streaming URL.
     */
    public Link link(Row doc, boolean inline) {
        Optional<String> s3 = storage.privateLink(relative(doc), LINK_TTL, doc.str("file_name"), inline);
        return s3.map(u -> new Link(u, true))
                .orElseGet(() -> new Link("/dashboard/documents/" + doc.get("id") + "/file" + (inline ? "?inline=1" : ""), false));
    }

    /** Streams a document (local storage, or when a presigned link can't be used). */
    public Optional<Storage.PrivateFile> open(Row doc) {
        return storage.openPrivate(relative(doc));
    }

    static String relative(Row doc) {
        String key = doc.str("storage_key");
        return key.startsWith("private/") ? key.substring("private/".length()) : key;
    }

    private void prune(long userId) {
        List<Row> old = db.select("select id, storage_key from student_documents where user_id = ? and kind = ? order by created_at desc, id desc"
                + " limit 1000 offset " + KEEP_PORTFOLIOS, userId, PORTFOLIO);
        for (Row r : old) {
            try {
                storage.deletePrivate(relative(r));
            } catch (RuntimeException e) {
                log.warn("Could not delete old portfolio {}", r.get("storage_key"), e);
            }
            db.update("delete from student_documents where id = ?", r.get("id"));
        }
    }

    private static String token() {
        byte[] b = new byte[6];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
