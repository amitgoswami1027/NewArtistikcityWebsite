package com.artistikcity.service;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import com.artistikcity.support.Str;
import com.artistikcity.view.Views;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Course completion certificates (Admin\OrderController@order_issue_certificate and the student download),
 * rendered to PDF with OpenHTMLtoPDF instead of dompdf. The PDF is rendered in memory and stored privately
 * (S3 key {@code private/documents/certificates/orders/<name>} in production, storage-private/ locally); the key is
 * kept in {@code certificates.certificate_path}. Only the share image for social networks stays public.
 */
@Service
public class CertificateService {

    private static final DateTimeFormatter M_D_Y = DateTimeFormatter.ofPattern("MMM dd yyyy", Locale.ENGLISH);

    private final Db db;
    private final Views views;
    private final Storage storage;

    public CertificateService(Db db, Views views, Storage storage) {
        this.db = db;
        this.views = views;
        this.storage = storage;
    }

    /** Issues a certificate for a paid order; returns the certificate row. */
    public Row issue(long orderId) {
        Row order = db.find("orders", orderId);
        if (order == null) {
            return null;
        }
        Row user = db.find("users", order.get("user_id"));
        Row course = db.find("courses", order.get("course_id"));
        Row teacher = db.find("admins", course.get("admin_id"));
        String name = orderId + "-" + Str.slug(user.str("name")) + "-" + user.get("id") + "-certificate.pdf";
        long certId = db.insert("certificates", Row.of("user_id", user.get("id"), "course_id", course.get("id"), "order_id", orderId,
                "admin_id", teacher.get("id"), "user_name", user.get("name"), "course_title", course.get("title"),
                "teacher_name", teacher.get("name"), "certificate_name", name, "status", 1));
        String number = "CERT-" + (System.currentTimeMillis() / 1000) + "-" + certId;
        db.updateById("certificates", certId, Row.of("certificate_number", number));
        Row cert = db.find("certificates", certId);
        byte[] pdf = render(model(cert));
        String rel = privatePath(name);
        storage.putPrivate(rel, pdf);
        db.updateById("certificates", certId, Row.of("certificate_path", Storage.privateKey(rel)));
        db.updateById("orders", orderId, Row.of("issue_certificate", 1, "certificate_name", name));
        return db.find("certificates", certId);
    }

    /** Private storage path of an order certificate's PDF. */
    public static String privatePath(String fileName) {
        return "documents/certificates/orders/" + fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * The certificate PDF: the stored copy (private storage, or the old public copy for certificates issued before),
     * rendered again if neither exists - and then stored privately.
     */
    public byte[] pdf(Row cert) {
        String name = cert.str("certificate_name");
        if (name != null) {
            String rel = privatePath(name);
            java.util.Optional<Storage.PrivateFile> f = storage.openPrivate(rel);
            if (f.isPresent()) {
                try (java.io.InputStream in = f.get().body()) {
                    return in.readAllBytes();
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }
            if (storage.exists("certificates/" + name)) return storage.read("certificates/" + name);
        }
        byte[] pdf = render(model(cert));
        if (name != null) {
            storage.putPrivate(privatePath(name), pdf);
            db.updateById("certificates", cert.get("id"), Row.of("certificate_path", Storage.privateKey(privatePath(name))));
        }
        return pdf;
    }

    /** A presigned S3 link to the certificate PDF (S3 storage only; empty locally). */
    public java.util.Optional<String> link(Row cert) {
        String name = cert.str("certificate_name");
        if (name == null || !storage.existsPrivate(privatePath(name))) return java.util.Optional.empty();
        return storage.privateLink(privatePath(name), java.time.Duration.ofMinutes(10), name, false);
    }

    /** Certificate of a student's order joined with the order (null if none). */
    public Row forOrder(long userId, long orderId) {
        return db.first("select c.* from orders o join certificates c on c.order_id = o.id"
                + " where o.user_id = ? and o.id = ? order by c.id desc limit 1", userId, orderId);
    }

    /** View data used by both the PDF and the HTML certificate pages. */
    public Map<String, Object> model(Row cert) {
        String created = cert.str("created_at");
        String date = created == null ? "" : LocalDateTime.parse(created.replace(' ', 'T')).format(M_D_Y);
        return Row.of("name", cert.get("user_name"), "course_title", cert.get("course_title"),
                "certificate_number", cert.get("certificate_number"), "signature", cert.get("teacher_name"),
                "certificate_date", date, "certificate_name_img", Str.replaceExtension(cert.str("certificate_name"), "jpeg"));
    }

    public byte[] render(Map<String, Object> model) {
        String html = views.render("certificates/certificate-pdf", model);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not render certificate PDF", e);
        }
    }
}
