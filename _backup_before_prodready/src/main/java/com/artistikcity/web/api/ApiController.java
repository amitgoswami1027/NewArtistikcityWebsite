package com.artistikcity.web.api;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Csrf;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import com.artistikcity.support.Str;
import com.artistikcity.support.Validator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON API (routes/api.php): Api\GeneralController, Api\NewsletterController, Api\ModuleController
 * and Api\TaskController. Used by the React footer/newsletter and by the admin course editor.
 */
@RestController
public class ApiController {

    private final Db db;
    private final Json json;
    private final Validator validator;
    private final Storage storage;
    private final Auth auth;

    public ApiController(Db db, Json json, Validator validator, Storage storage, Auth auth) {
        this.auth = auth;
        this.db = db;
        this.json = json;
        this.validator = validator;
        this.storage = storage;
    }

    private ResponseEntity<String> ok(Object body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    /** $validator->errors() returned with HTTP 200, as the original API did. */
    private ResponseEntity<String> errors(Map<String, String> errors) {
        Map<String, Object> m = new LinkedHashMap<>();
        errors.forEach((k, v) -> m.put(k, List.of(v)));
        return ok(m);
    }

    // ------------------------------------------------------------ general

    @GetMapping("/api/get_mediums")
    public ResponseEntity<String> mediums() {
        return ok(db.select("select * from mediums order by id"));
    }

    @GetMapping("/api/get_footer_data")
    public ResponseEntity<String> footerData() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("latestCourses", db.select("select id, title, slug from courses where course_type_id = 1 and course_start_date >= ? order by id", LocalDate.now()));
        m.put("latestWorkshop", db.select("select id, title, slug from courses where course_type_id = 2 and course_start_date >= ? order by id", LocalDate.now()));
        return ok(m);
    }

    /** Saves a certificate image (base64 data URL) generated in the browser. */
    /** Largest share image the certificate page may store (a 1200x900 PNG of the certificate is ~1-2 MB). */
    static final int MAX_CERT_IMAGE = 5 * 1024 * 1024;

    /**
     * The certificate share page renders the certificate to an image in the browser and stores it here, so social
     * networks can show it. Only the signed-in owner may store the image of their own certificate: the file name must
     * be that certificate's image name ({@code <certificate>.jpeg}), the request must carry the CSRF token, and the
     * bytes must be a PNG or JPEG of at most 5 MB. (It used to accept any name and any bytes from anyone, which let
     * strangers overwrite other students' share images or the public certificate PDFs.)
     */
    @PostMapping("/api/save_cert_img")
    public ResponseEntity<String> saveCertImg(HttpServletRequest request) {
        Long userId = auth.userId(request);
        if (userId == null) {
            return status(401, Row.of("status", "error", "message", "Please sign in."));
        }
        if (!Csrf.matches(request)) {
            return status(419, Row.of("status", "error", "message", "Your session expired. Refresh the page and try again."));
        }
        Input in = Input.of(request, json);
        String data = in.get("imgBase64");
        String name = in.get("imgName");
        if (data == null || name == null || !name.matches("[A-Za-z0-9._-]{1,120}\\.jpe?g")) {
            return status(422, Row.of("status", "error", "message", "Bad image name."));
        }
        // the name must be the image of a certificate this student owns
        boolean owns = false;
        for (Row c : db.select("select certificate_name from certificates where user_id = ?", userId)) {
            String base = c.str("certificate_name", "");
            if (!base.isBlank() && Str.replaceExtension(base, "jpeg").equals(name)) {
                owns = true;
                break;
            }
        }
        if (!owns) {
            return status(403, Row.of("status", "error", "message", "That isn't your certificate."));
        }
        if (data.length() > MAX_CERT_IMAGE / 3 * 4 + 64) {
            return status(413, Row.of("status", "error", "message", "The image is too large."));
        }
        byte[] image;
        try {
            image = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
        } catch (IllegalArgumentException e) {
            return status(422, Row.of("status", "error", "message", "Not an image."));
        }
        boolean png = image.length > 8 && (image[0] & 0xFF) == 0x89 && image[1] == 'P' && image[2] == 'N' && image[3] == 'G';
        boolean jpeg = image.length > 3 && (image[0] & 0xFF) == 0xFF && (image[1] & 0xFF) == 0xD8 && (image[2] & 0xFF) == 0xFF;
        if (!(png || jpeg) || image.length > MAX_CERT_IMAGE) {
            return status(422, Row.of("status", "error", "message", "Not a PNG or JPEG image."));
        }
        storage.put("certificates/" + name, image);
        return ok(Row.of("status", "success", "imgPath", Storage.url("certificates/" + name)));
    }

    private ResponseEntity<String> status(int code, Object body) {
        return ResponseEntity.status(code).contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    // --------------------------------------------------------- newsletter

    @PostMapping("/api/newsletter")
    public ResponseEntity<String> newsletter(HttpServletRequest request) {
        Input in = Input.of(request, json);
        String email = in.get("email") == null ? "" : in.get("email").trim().toLowerCase();
        if (email.isEmpty() || email.length() > 255 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return ok(Row.of("error", Map.of("email", List.of("Please enter a valid e-mail address."))));
        }
        Row existing = db.first("select id, unsubscribed_at from newsletters where lower(email) = ?", email);
        if (existing != null && existing.get("unsubscribed_at") == null) {
            return ok(Row.of("error", Map.of("email", List.of("You're already subscribed."))));
        }
        if (existing != null) {
            db.updateById("newsletters", existing.get("id"), Row.of("unsubscribed_at", null, "source", "footer"));
        } else {
            db.insert("newsletters", Row.of("email", email, "source", "footer"));
        }
        return ok(Row.of("message", "success"));
    }

    // The old admin modals used /api/modules/**, /api/project/** and /api/modules/lessons/task/**; the admin console
    // replaced them with authenticated endpoints under /admin/api/courses/** and /admin/api/tasks/**.
}
