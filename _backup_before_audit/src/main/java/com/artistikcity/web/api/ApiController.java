package com.artistikcity.web.api;

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

    public ApiController(Db db, Json json, Validator validator, Storage storage) {
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
    @PostMapping("/api/save_cert_img")
    public ResponseEntity<String> saveCertImg(HttpServletRequest request) {
        Input in = Input.of(request, json);
        String data = in.get("imgBase64");
        String name = in.get("imgName");
        if (data == null || name == null || !name.matches("[A-Za-z0-9._-]+")) {
            return ok(Row.of("status", "error"));
        }
        byte[] image = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
        storage.put("certificates/" + name, image);
        // the public URL (it used to be the server's file path, which leaked the install folder)
        return ok(Row.of("status", "success", "imgPath", Storage.url("certificates/" + name)));
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
