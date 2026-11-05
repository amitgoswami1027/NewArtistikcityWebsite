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
    public ResponseEntity<String> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .body("<!DOCTYPE html><html><head><title>Not Found</title></head><body style=\"font-family:sans-serif;"
                        + "text-align:center;padding:60px\"><h1>404 | Not Found</h1><p><a href=\"/\">Back to home</a></p></body></html>");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> error(Exception e, HttpServletRequest request) {
        if (e instanceof org.springframework.web.ErrorResponse er) {
            // framework errors (404 no handler/resource, 405 method not allowed, ...) keep their status
            int code = er.getStatusCode().value();
            return ResponseEntity.status(code)
                    .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                    .body("<!DOCTYPE html><html><head><title>" + code + "</title></head><body style=\"font-family:sans-serif;"
                            + "text-align:center;padding:60px\"><h1>" + code + "</h1><p><a href=\"/\">Back to home</a></p></body></html>");
        }
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .body("<!DOCTYPE html><html><head><title>Server Error</title></head><body style=\"font-family:sans-serif;"
                        + "text-align:center;padding:60px\"><h1>500 | Server Error</h1></body></html>");
    }
}
