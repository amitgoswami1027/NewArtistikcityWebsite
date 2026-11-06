package com.artistikcity.http;

import com.artistikcity.inertia.Inertia;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exception handling (app/Exceptions/Handler.php). */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Json json;

    public GlobalExceptionHandler(Json json) {
        this.json = json;
    }

    /**
     * Failed validation: JSON 422 for AJAX/API calls, otherwise redirect back with the errors and
     * old input flashed to the session (Inertia shows them through the shared "errors" prop).
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<String> validation(ValidationException e, HttpServletRequest request) {
        boolean ajax = !Inertia.isInertia(request)
                && ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
                || (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json"))
                || request.getRequestURI().startsWith("/api/"));
        if (ajax) {
            Map<String, Object> errors = new LinkedHashMap<>();
            e.getErrors().forEach((k, v) -> errors.put(k, List.of(v)));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", e.getMessage());
            body.put("errors", errors);
            return ResponseEntity.status(422).contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
        }
        Flash.putErrors(request, e.getErrors());
        Flash.putOld(request, Input.of(request, json).all());
        HttpStatus status = Inertia.isInertia(request) && !"POST".equals(request.getMethod())
                ? HttpStatus.SEE_OTHER : HttpStatus.FOUND;
        return ResponseEntity.status(status).header(HttpHeaders.LOCATION, Redirects.previous(request)).body("");
    }

    /** findOrFail() equivalent. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> notFound(NotFoundException e, HttpServletRequest request) {
        if (wantsJson(request)) return jsonError(HttpStatus.NOT_FOUND, "We couldn't find that. It may have been moved or removed.", null);
        return page(404, "Page not found", "We couldn't find that page. It may have been moved or removed.", null);
    }

    /** Auth API called without a login: 401 JSON. */
    @ExceptionHandler(com.artistikcity.web.AuthApiController.UnauthorizedException.class)
    public ResponseEntity<String> unauthorized(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body(json.encode(Map.of("message", "Please sign in again.", "reauth", true)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> error(Exception e, HttpServletRequest request) {
        if (e instanceof org.springframework.web.ErrorResponse er) {
            // framework errors (404 no handler/resource, 405 method not allowed, ...) keep their status
            int code = er.getStatusCode().value();
            String uri = request.getRequestURI();
            if (code == 413 && (uri.startsWith("/admin/api/") || uri.startsWith("/api/"))) {
                // too-large upload on a JSON endpoint: a message the form can show
                String reason = e instanceof org.springframework.web.server.ResponseStatusException rse && rse.getReason() != null
                        ? rse.getReason() : "That file is too large.";
                return ResponseEntity.status(code).contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of("message", reason)));
            }
            String msg = code == 404 ? "We couldn't find that page. It may have been moved or removed."
                    : code == 405 ? "That action isn't available here." : "That request couldn't be handled.";
            if (wantsJson(request)) return jsonError(HttpStatus.valueOf(code), msg, null);
            return page(code, code == 404 ? "Page not found" : "Something isn't right", msg, null);
        }
        String ref = Long.toString(System.nanoTime() & 0xFFFFFFFFFL, 36).toUpperCase();
        if (e instanceof org.springframework.dao.DataIntegrityViolationException) {
            // a delete or change that other records still depend on: say so instead of failing
            log.warn("Refused by the database on {} {} (ref {}): {}", request.getMethod(), request.getRequestURI(), ref, rootMessage(e));
            String msg = "This can't be changed because other records still depend on it.";
            if (wantsJson(request)) return jsonError(HttpStatus.CONFLICT, msg, ref);
            return page(409, "That couldn't be saved", msg, ref);
        }
        log.error("Unhandled error on {} {} (ref {})", request.getMethod(), request.getRequestURI(), ref, e);
        String msg = "Something went wrong on our side. Please try again in a moment; if it keeps happening, contact us and quote reference " + ref + ".";
        if (wantsJson(request)) return jsonError(HttpStatus.INTERNAL_SERVER_ERROR, msg, ref);
        return page(500, "Something went wrong", msg, ref);
    }

    /** API, admin API, XHR or "Accept: application/json" calls get JSON errors; page visits get a page. */
    static boolean wantsJson(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = String.valueOf(request.getHeader("Accept"));
        return !Inertia.isInertia(request) && (uri.startsWith("/api/") || uri.startsWith("/admin/api/")
                || "XMLHttpRequest".equals(request.getHeader("X-Requested-With")) || accept.contains("application/json"));
    }

    private ResponseEntity<String> jsonError(HttpStatus status, String message, String ref) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        if (ref != null) body.put("reference", ref);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return String.valueOf(t.getMessage());
    }

    /** A small branded error page (no app shell, so it works even when the page props can't be built). */
    static ResponseEntity<String> page(int code, String title, String message, String ref) {
        String html = """
                <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="robots" content="noindex"><title>%s · ArtistikCity</title>
                <style>*{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;padding:16px;font-family:'Plus Jakarta Sans','Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#f7f7fb;color:#0e1a3a}
                main{width:100%%;max-width:460px;background:#fff;border:1px solid #e3e6ef;border-radius:20px;padding:32px 28px;text-align:center}img{height:40px;width:auto}
                .code{margin:18px 0 0;font-size:13px;font-weight:800;letter-spacing:.12em;color:#e5156b}h1{font-size:24px;margin:6px 0 8px;letter-spacing:-.02em}p{margin:0 0 22px;color:#5b6478;line-height:1.55}
                a.btn{display:inline-flex;align-items:center;justify-content:center;height:48px;padding:0 26px;border-radius:999px;background:#e5156b;color:#fff;font-weight:800;text-decoration:none}
                a.alt{display:block;margin-top:14px;color:#0e1a3a;font-weight:700}</style></head><body><main>
                <img src="/assets/images/logo.png" alt="ArtistikCity"><p class="code">ERROR %d</p><h1>%s</h1><p>%s</p>
                <a class="btn" href="/">Back to the home page</a><a class="alt" href="/contact">Contact us</a></main></body></html>
                """.formatted(esc(title), code, esc(title), esc(message));
        return ResponseEntity.status(code).contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8)).body(html);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
