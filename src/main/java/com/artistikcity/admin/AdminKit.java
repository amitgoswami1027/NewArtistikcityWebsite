package com.artistikcity.admin;

import com.artistikcity.http.Auth;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.lifecycle.AdminConsoleController;
import com.artistikcity.lifecycle.LifecycleRepository;
import com.artistikcity.lifecycle.Personas;
import com.artistikcity.support.Db;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.UploadedFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Shared plumbing for the admin console screens (React pages under Pages/Admin).
 * <ul>
 *   <li>{@link #page}: renders a console page with the shared props (signed-in admin, sidebar badges).</li>
 *   <li>{@link #ok}, {@link #invalid}, {@link #fail}: JSON answers for the console's axios calls.
 *       Validation errors are always {@code 422 {message, errors: {field: "text"}}} so forms can show them inline.</li>
 *   <li>{@link #image} / {@link #pdf}: upload checks (type by content, size) before anything is stored.</li>
 * </ul>
 * Access is by persona capability ({@link Personas}); a page the admin may not use renders {@code Admin/Denied}
 * and an API call answers 403.
 */
@Component
public class AdminKit {

    public static final long MAX_IMAGE = 8L * 1024 * 1024;
    public static final long MAX_PDF = 15L * 1024 * 1024;

    private final Inertia inertia;
    private final Auth auth;
    private final Json json;
    private final Db db;
    private final LifecycleRepository repo;

    public AdminKit(Inertia inertia, Auth auth, Json json, Db db, LifecycleRepository repo) {
        this.inertia = inertia;
        this.auth = auth;
        this.json = json;
        this.db = db;
        this.repo = repo;
    }

    public Row admin(HttpServletRequest request) {
        return auth.admin(request);
    }

    public boolean can(Row admin, String capability) {
        return Personas.can(admin, capability);
    }

    /** Instructors only manage their own courses; other personas see everything (null). */
    public Long teacherScope(Row admin) {
        return "teacher".equals(Personas.of(admin)) ? admin.lng("id") : null;
    }

    /** Props every console page gets: the admin, the sidebar badges and the inbox counters. */
    public Map<String, Object> base(Row admin) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("admin", Personas.view(admin));
        Supplier<Object> badges = () -> AdminConsoleController.badges(repo);
        p.put("badges", badges);
        return p;
    }

    public ResponseEntity<String> page(HttpServletRequest request, String component, Map<String, ?> props) {
        Map<String, Object> p = base(admin(request));
        p.putAll(props);
        return inertia.render(request, component, p);
    }

    /** Renders the page if the admin has the capability, otherwise the "no access" page. */
    public ResponseEntity<String> page(HttpServletRequest request, String capability, String component, Supplier<Map<String, ?>> props) {
        Row admin = admin(request);
        if (!can(admin, capability)) {
            return inertia.render(request, "Admin/Denied", base(admin));
        }
        return page(request, component, props.get());
    }

    // ------------------------------------------------------------------ JSON

    public ResponseEntity<String> ok(Object body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    public ResponseEntity<String> fail(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of("message", message)));
    }

    public ResponseEntity<String> forbidden() {
        return fail(HttpStatus.FORBIDDEN, "You don't have access to this.");
    }

    public ResponseEntity<String> invalid(Map<String, String> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", errors.size() == 1 ? errors.values().iterator().next() : "Please fix the highlighted fields.");
        body.put("errors", errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    public ResponseEntity<String> csv(String fileName, List<String> header, List<List<Object>> rows) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(csvLine(header.stream().map(h -> (Object) h).toList()));
        for (List<Object> r : rows) {
            sb.append(csvLine(r));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString());
    }

    private static String csvLine(List<Object> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String v = cells.get(i) == null ? "" : String.valueOf(cells.get(i));
            // neutralise spreadsheet formulas (CSV injection)
            if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
                v = "'" + v;
            }
            sb.append('"').append(v.replace("\"", "\"\"")).append('"');
        }
        return sb.append("\r\n").toString();
    }

    // --------------------------------------------------------------- uploads

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    /** @return an error text, or null when the file is an acceptable image. */
    public static String checkImage(UploadedFile f) {
        if (f == null) {
            return null;
        }
        if (f.getSize() > MAX_IMAGE) {
            return "Images can be up to 8 MB.";
        }
        String ext = ext(f.getClientOriginalName());
        byte[] b = f.getContent();
        boolean magic = b.length > 12 && ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8                      // jpeg
                || (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'                             // png
                || b[0] == 'G' && b[1] == 'I' && b[2] == 'F'                                                       // gif
                || b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E');         // webp
        return IMAGE_EXT.contains(ext) && magic ? null : "Upload a JPG, PNG, WebP or GIF image.";
    }

    /** @return an error text, or null when the file is a PDF. */
    public static String checkPdf(UploadedFile f) {
        if (f == null) {
            return null;
        }
        if (f.getSize() > MAX_PDF) {
            return "PDFs can be up to 15 MB.";
        }
        byte[] b = f.getContent();
        boolean magic = b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
        return "pdf".equals(ext(f.getClientOriginalName())) && magic ? null : "Upload a PDF file.";
    }

    private static String ext(String name) {
        int i = name == null ? -1 : name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------- helpers

    public static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
