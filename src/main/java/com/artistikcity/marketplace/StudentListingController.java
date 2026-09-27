package com.artistikcity.marketplace;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Csrf;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.lifecycle.LifecycleRepository;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Slugs;
import com.artistikcity.support.UploadedFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Students sell approved coursework as a 1-of-1 original, through the same three-step wizard staff use.
 * Every new listing, and every change to a live one, goes to the moderator review queue first.
 * <ul>
 *   <li>Page (auth guard): /dashboard/portfolio/sell/{submissionId}</li>
 *   <li>API (session + CSRF checked here, /api/** skips the web middleware):
 *       POST /api/v1/marketplace/student/images, POST /api/v1/marketplace/student/paintings,
 *       PUT /api/v1/marketplace/student/paintings/{id}, POST /api/v1/marketplace/student/paintings/{id}/withdraw</li>
 * </ul>
 */
@Controller
public class StudentListingController {

    private final Inertia inertia;
    private final Auth auth;
    private final Json json;
    private final Slugs slugs;
    private final MarketplaceRepository repo;
    private final MarketplaceImages images;
    private final LifecycleRepository lifecycle;

    public StudentListingController(Inertia inertia, Auth auth, Json json, Slugs slugs, MarketplaceRepository repo,
                                    MarketplaceImages images, LifecycleRepository lifecycle) {
        this.inertia = inertia;
        this.auth = auth;
        this.json = json;
        this.slugs = slugs;
        this.repo = repo;
        this.images = images;
        this.lifecycle = lifecycle;
    }

    @GetMapping("/dashboard/portfolio/sell/{submissionId}")
    public ResponseEntity<String> page(HttpServletRequest request, @PathVariable("submissionId") long submissionId) {
        long userId = auth.userId(request);
        Row user = auth.user(request);
        Map<String, Object> sub = lifecycle.submission(submissionId);
        Map<String, Object> props = new LinkedHashMap<>();
        if (sub == null || !owns(sub, userId)) {
            props.put("blocked", "We couldn't find that artwork in your portfolio.");
        } else if (!"Approved".equals(sub.get("admin_status"))) {
            props.put("blocked", "Only artwork a reviewer has approved can be sold. This piece is " + String.valueOf(sub.get("admin_status")).toLowerCase() + ".");
        }
        props.put("submission", sub);
        props.put("artistName", user == null ? "" : user.str("name", ""));
        Map<String, Object> existing = repo.listingForSubmission(submissionId);
        if (existing != null && sub != null && owns(sub, userId)) {
            long id = ((Number) existing.get("id")).longValue();
            Map<String, Object> row = repo.find(id);
            Map<String, Object> painting = MarketplaceViews.detail(row, repo.images(id));
            painting.put("raw_status", row.get("stock_status"));
            painting.put("review_notes", row.get("review_notes"));
            props.put("painting", painting);
        } else {
            props.put("painting", null);
        }
        return inertia.render(request, "Studio/SellOriginal", props);
    }

    @PostMapping("/api/v1/marketplace/student/images")
    public ResponseEntity<String> upload(HttpServletRequest request) {
        ResponseEntity<String> denied = guard(request);
        if (denied != null) return denied;
        UploadedFile f = Input.of(request, json).file("file");
        try {
            return ok(images.store(f == null ? null : f.getContent()));
        } catch (MarketplaceException e) {
            return fail(e);
        }
    }

    /** JSON: { submissionId, intent: draft | submit, ...wizard fields } */
    @PostMapping("/api/v1/marketplace/student/paintings")
    public ResponseEntity<String> create(HttpServletRequest request) throws IOException {
        ResponseEntity<String> denied = guard(request);
        if (denied != null) return denied;
        long userId = auth.userId(request);
        Map<String, Object> b = body(request);
        Long submissionId = b.get("submissionId") instanceof Number n ? n.longValue() : null;
        Map<String, Object> sub = submissionId == null ? null : lifecycle.submission(submissionId);
        if (sub == null || !owns(sub, userId)) return error(HttpStatus.NOT_FOUND, "We couldn't find that artwork in your portfolio.");
        if (!"Approved".equals(sub.get("admin_status"))) return error(HttpStatus.CONFLICT, "Only approved artwork can be sold.");
        if (repo.listingForSubmission(submissionId) != null) return error(HttpStatus.CONFLICT, "This artwork already has a listing. Edit it instead.");
        ListingInput in;
        try {
            in = ListingInput.from(b);
        } catch (ListingInput.InvalidListingException e) {
            return invalid(e);
        }
        String status = "submit".equals(b.get("intent")) ? "PENDING_REVIEW" : "DRAFT";
        Row user = auth.user(request);
        long id = repo.create(in, slugs.unique("paintings", "slug", in.title(), null), status, "STUDENT",
                user == null ? String.valueOf(sub.get("student_name")) : user.str("name", String.valueOf(sub.get("student_name"))),
                null, userId, submissionId);
        repo.syncStudentListing(id);
        return ok(Map.of("id", id, "status", status, "message", message(status)));
    }

    @PutMapping("/api/v1/marketplace/student/paintings/{id}")
    public ResponseEntity<String> update(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        ResponseEntity<String> denied = guard(request);
        if (denied != null) return denied;
        Map<String, Object> row = repo.find(id);
        if (row == null || !ownsListing(row, auth.userId(request))) return error(HttpStatus.NOT_FOUND, "Listing not found.");
        String current = String.valueOf(row.get("stock_status"));
        if (!MarketplaceRepository.EDITABLE.contains(current)) {
            return error(HttpStatus.CONFLICT, "RESERVED".equals(current) ? "A collector is holding this piece right now. You can edit it after the hold ends." : "Sold listings can't be edited.");
        }
        Map<String, Object> b = body(request);
        ListingInput in;
        try {
            in = ListingInput.from(b);
        } catch (ListingInput.InvalidListingException e) {
            return invalid(e);
        }
        // students never publish directly: submitting (or changing a live listing) always goes back to review
        String status = "submit".equals(b.get("intent")) || "AVAILABLE".equals(current) ? "PENDING_REVIEW" : "DRAFT";
        int version = b.get("version") instanceof Number n ? n.intValue() : -1;
        if (!repo.update(id, version, in, status)) {
            return error(HttpStatus.CONFLICT, "This listing changed since you opened it. Reload to see the latest version.");
        }
        repo.syncStudentListing(id);
        return ok(Map.of("id", id, "status", status, "message", message(status)));
    }

    /** Takes a live or in-review listing back to draft (off the marketplace). */
    @PostMapping("/api/v1/marketplace/student/paintings/{id}/withdraw")
    public ResponseEntity<String> withdraw(HttpServletRequest request, @PathVariable("id") long id) {
        ResponseEntity<String> denied = guard(request);
        if (denied != null) return denied;
        Map<String, Object> row = repo.find(id);
        if (row == null || !ownsListing(row, auth.userId(request))) return error(HttpStatus.NOT_FOUND, "Listing not found.");
        if (!repo.transition(id, List.of("PENDING_REVIEW", "AVAILABLE"), "DRAFT", null, null)) {
            return error(HttpStatus.CONFLICT, "RESERVED".equals(row.get("stock_status"))
                    ? "A collector is holding this piece. You can withdraw it if the hold ends without a sale." : "This listing can't be withdrawn now.");
        }
        return ok(Map.of("id", id, "status", "DRAFT", "message", "Withdrawn. It's no longer in the marketplace."));
    }

    // ================================================================ helpers

    private ResponseEntity<String> guard(HttpServletRequest request) {
        if (auth.userId(request) == null) return error(HttpStatus.UNAUTHORIZED, "Please log in again.");
        if (!Csrf.matches(request)) return error(HttpStatus.FORBIDDEN, "Your session expired. Refresh the page and try again.");
        return null;
    }

    private static boolean owns(Map<String, Object> sub, long userId) {
        return sub.get("user_id") instanceof Number n && n.longValue() == userId;
    }

    private static boolean ownsListing(Map<String, Object> row, Long userId) {
        return userId != null && "STUDENT".equals(row.get("source")) && row.get("artist_user_id") instanceof Number n && n.longValue() == userId;
    }

    private static String message(String status) {
        return "PENDING_REVIEW".equals(status)
                ? "Sent for review. A moderator checks every original before it goes live - usually within 2 working days."
                : "Draft saved. Submit it for review when you're ready.";
    }

    private Map<String, Object> body(HttpServletRequest request) throws IOException {
        Map<String, Object> m = json.decodeMap(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        return m == null ? Map.of() : m;
    }

    private ResponseEntity<String> invalid(ListingInput.InvalidListingException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                .body(json.encode(Map.of("message", e.getMessage(), "errors", e.errors(), "step", e.step())));
    }

    private ResponseEntity<String> fail(MarketplaceException e) {
        return ResponseEntity.status(e.status()).contentType(MediaType.APPLICATION_JSON).body(json.encode(e.body()));
    }

    private ResponseEntity<String> ok(Object body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(Map.of("message", message)));
    }
}
