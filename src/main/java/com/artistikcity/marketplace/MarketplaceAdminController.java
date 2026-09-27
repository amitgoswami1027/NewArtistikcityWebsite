package com.artistikcity.marketplace;

import com.artistikcity.http.Auth;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.lifecycle.AdminConsoleController;
import com.artistikcity.lifecycle.LifecycleRepository;
import com.artistikcity.lifecycle.Personas;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Marketplace console for staff personas (admin guard + CSRF from the web middleware on every path).
 * <ul>
 *   <li>Super admin: every listing, publish directly, review, holds, fulfilment and refunds.</li>
 *   <li>Gallery moderator: review queue (approve / request changes), unpublish, feature, holds, fulfilment.</li>
 *   <li>Instructor: creates and edits their own originals; every new or changed live listing goes to review;
 *       sees their own sales read-only.</li>
 * </ul>
 */
@Controller
public class MarketplaceAdminController {

    private final Inertia inertia;
    private final Auth auth;
    private final Json json;
    private final Slugs slugs;
    private final MarketplaceRepository repo;
    private final MarketplaceService market;
    private final MarketplaceImages images;
    private final LifecycleRepository lifecycle;

    public MarketplaceAdminController(Inertia inertia, Auth auth, Json json, Slugs slugs, MarketplaceRepository repo, MarketplaceService market,
                                      MarketplaceImages images, LifecycleRepository lifecycle) {
        this.inertia = inertia;
        this.auth = auth;
        this.json = json;
        this.slugs = slugs;
        this.repo = repo;
        this.market = market;
        this.images = images;
        this.lifecycle = lifecycle;
    }

    // ================================================================ pages

    @GetMapping("/admin/dashboard/marketplace")
    public ResponseEntity<String> console(HttpServletRequest request) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace")) return denied(request, admin);
        Long scope = ownScope(admin);
        Map<String, Object> p = base(admin);
        List<Map<String, Object>> rows = repo.adminListings(scope);
        List<Map<String, Object>> listings = rows.stream().map(r -> {
            Map<String, Object> m = MarketplaceViews.card(r);
            m.put("raw_status", r.get("stock_status"));
            m.put("review_notes", r.get("review_notes"));
            m.put("order_id", r.get("order_id"));
            m.put("image_count", r.get("image_count"));
            m.put("updated_at", MarketplaceViews.millis(r.get("updated_at")));
            m.put("mine", scope != null || (r.get("artist_admin_id") instanceof Number n && n.longValue() == admin.lng("id")));
            return m;
        }).toList();
        p.put("listings", listings);
        p.put("kpis", repo.kpis(scope));
        p.put("orders", repo.orders(scope).stream().map(o -> {
            Map<String, Object> m = MarketplaceController.orderView(o);
            m.put("source", o.get("source"));
            m.put("staff_notes", o.get("staff_notes"));
            m.put("updated_at", MarketplaceViews.millis(o.get("updated_at")));
            return m;
        }).toList());
        if (Personas.can(admin, "marketplace.publish")) {
            p.put("holds", repo.activeHolds().stream().map(h -> {
                Map<String, Object> m = new LinkedHashMap<>(h);
                m.put("expires_at", MarketplaceViews.millis(h.get("expires_at")));
                m.put("reserved_at", MarketplaceViews.millis(h.get("reserved_at")));
                m.put("guest", String.valueOf(h.get("session_or_user_id")).startsWith("S:"));
                m.remove("session_or_user_id");
                return m;
            }).toList());
        }
        p.put("orderFlow", MarketplaceService.ORDER_FLOW);
        p.put("serverNow", System.currentTimeMillis());
        p.put("tab", request.getParameter("tab"));
        return inertia.render(request, "Admin/Marketplace", p);
    }

    @GetMapping("/admin/dashboard/marketplace/new")
    public ResponseEntity<String> create(HttpServletRequest request) {
        Row admin = auth.admin(request);
        if (!canCreate(admin)) return denied(request, admin);
        Map<String, Object> p = base(admin);
        p.put("painting", null);
        p.put("artists", artists(admin));
        return inertia.render(request, "Admin/MarketplaceEditor", p);
    }

    @GetMapping("/admin/dashboard/marketplace/{id}/edit")
    public ResponseEntity<String> edit(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = auth.admin(request);
        Map<String, Object> row = repo.find(id);
        if (row == null) return denied(request, admin);
        if (!canEdit(admin, row)) return denied(request, admin);
        Map<String, Object> p = base(admin);
        Map<String, Object> painting = MarketplaceViews.detail(row, repo.images(id));
        painting.put("raw_status", row.get("stock_status"));
        painting.put("review_notes", row.get("review_notes"));
        p.put("painting", painting);
        p.put("artists", artists(admin));
        return inertia.render(request, "Admin/MarketplaceEditor", p);
    }

    // ================================================================ listing actions

    /** multipart "file" -> {url,width,height} */
    @PostMapping("/admin/console/api/marketplace/images")
    public ResponseEntity<String> upload(HttpServletRequest request) {
        Row admin = auth.admin(request);
        if (!canCreate(admin)) return error(HttpStatus.FORBIDDEN, "Your role can't upload listing photos.");
        UploadedFile f = Input.of(request, json).file("file");
        try {
            return ok(images.store(f == null ? null : f.getContent()));
        } catch (MarketplaceException e) {
            return fail(e);
        }
    }

    /** JSON: wizard fields + intent (draft | submit | publish) + artistAdminId / artistName (super admin only). */
    @PostMapping("/admin/console/api/marketplace/paintings")
    public ResponseEntity<String> store(HttpServletRequest request) throws IOException {
        Row admin = auth.admin(request);
        if (!canCreate(admin)) return error(HttpStatus.FORBIDDEN, "Your role can't create listings.");
        Map<String, Object> b = body(request);
        ListingInput in;
        try {
            in = ListingInput.from(b);
        } catch (ListingInput.InvalidListingException e) {
            return invalid(e);
        }
        String status = statusFor(admin, String.valueOf(b.get("intent")), null);
        Long artistAdminId = admin.lng("id");
        String artistName = admin.str("name", "ArtistikCity");
        if (Personas.can(admin, "marketplace.edit")) {
            Long chosen = b.get("artistAdminId") instanceof Number n ? n.longValue() : null;
            String guest = ListingInput.str(b.get("artistName"));
            if (chosen != null) {
                List<Map<String, Object>> a = repo.list("select id, name from admins where id = ?", chosen);
                if (a.isEmpty()) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Choose an artist from the list.");
                artistAdminId = chosen;
                artistName = String.valueOf(a.get(0).get("name"));
            } else if (guest.length() >= 2) {
                artistAdminId = null;
                artistName = guest.length() > 255 ? guest.substring(0, 255) : guest;
            }
        }
        long id = repo.create(in, slugs.unique("paintings", "slug", in.title(), null), status, "STUDIO", artistName, artistAdminId, null, null);
        return ok(Map.of("id", id, "status", status, "message", message(status)));
    }

    /** JSON: wizard fields + version + intent. */
    @PutMapping("/admin/console/api/marketplace/paintings/{id}")
    public ResponseEntity<String> update(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        Row admin = auth.admin(request);
        Map<String, Object> row = repo.find(id);
        if (row == null) return error(HttpStatus.NOT_FOUND, "Listing not found.");
        if (!canEdit(admin, row)) return error(HttpStatus.FORBIDDEN, "You can only edit your own listings.");
        Map<String, Object> b = body(request);
        ListingInput in;
        try {
            in = ListingInput.from(b);
        } catch (ListingInput.InvalidListingException e) {
            return invalid(e);
        }
        String current = String.valueOf(row.get("stock_status"));
        if (!MarketplaceRepository.EDITABLE.contains(current)) {
            return error(HttpStatus.CONFLICT, "RESERVED".equals(current) ? "A collector is holding this piece right now. Edit it after the hold ends." : "Sold listings can't be edited.");
        }
        String status = statusFor(admin, String.valueOf(b.get("intent")), current);
        int version = b.get("version") instanceof Number n ? n.intValue() : -1;
        if (!repo.update(id, version, in, status)) {
            return error(HttpStatus.CONFLICT, "This listing changed since you opened it (someone edited it, or a collector reserved it). Reload to see the latest version.");
        }
        return ok(Map.of("id", id, "status", status, "message", message(status)));
    }

    /** JSON: { decision: approve | reject, notes } */
    @PostMapping("/admin/console/api/marketplace/paintings/{id}/review")
    public ResponseEntity<String> review(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace.publish")) return error(HttpStatus.FORBIDDEN, "Your role can't review listings.");
        Map<String, Object> b = body(request);
        String decision = ListingInput.str(b.get("decision"));
        String notes = ListingInput.str(b.get("notes"));
        if ("reject".equals(decision) && notes.length() < 5) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Tell the artist what to change (at least a few words).");
        if (notes.length() > 4000) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Keep notes under 4000 characters.");
        boolean ok = "approve".equals(decision)
                ? repo.transition(id, List.of("PENDING_REVIEW", "DRAFT"), "AVAILABLE", admin.lng("id"), notes.isEmpty() ? "Approved." : notes)
                : "reject".equals(decision) && repo.transition(id, List.of("PENDING_REVIEW"), "DRAFT", admin.lng("id"), notes);
        if (!ok) return error(HttpStatus.CONFLICT, "This listing is no longer waiting for review.");
        return ok(Map.of("id", id, "status", "approve".equals(decision) ? "AVAILABLE" : "DRAFT"));
    }

    /** JSON: { status: AVAILABLE | ARCHIVED | DRAFT } - publish, unpublish/withdraw or archive. */
    @PostMapping("/admin/console/api/marketplace/paintings/{id}/status")
    public ResponseEntity<String> status(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        Row admin = auth.admin(request);
        Map<String, Object> row = repo.find(id);
        if (row == null) return error(HttpStatus.NOT_FOUND, "Listing not found.");
        String to = ListingInput.str(body(request).get("status"));
        boolean publisher = Personas.can(admin, "marketplace.publish");
        boolean owner = canEdit(admin, row);
        boolean ok;
        switch (to) {
            case "AVAILABLE" -> {
                if (!publisher) return error(HttpStatus.FORBIDDEN, "Only reviewers can put a listing live.");
                ok = repo.transition(id, List.of("DRAFT", "PENDING_REVIEW", "ARCHIVED"), "AVAILABLE", admin.lng("id"), null);
            }
            case "ARCHIVED" -> {
                if (!publisher && !owner) return error(HttpStatus.FORBIDDEN, "Not allowed.");
                ok = repo.transition(id, List.of("DRAFT", "PENDING_REVIEW", "AVAILABLE"), "ARCHIVED", null, null);
            }
            case "DRAFT" -> {
                if (!publisher && !owner) return error(HttpStatus.FORBIDDEN, "Not allowed.");
                ok = repo.transition(id, List.of("PENDING_REVIEW", "AVAILABLE", "ARCHIVED"), "DRAFT", null, null);
            }
            default -> {
                return error(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown status.");
            }
        }
        if (!ok) return error(HttpStatus.CONFLICT, "RESERVED".equals(row.get("stock_status"))
                ? "A collector is holding this piece. Release the hold first, or wait for it to end." : "That change isn't possible from the listing's current state.");
        return ok(Map.of("id", id, "status", to));
    }

    @PostMapping("/admin/console/api/marketplace/paintings/{id}/feature")
    public ResponseEntity<String> feature(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace.publish")) return error(HttpStatus.FORBIDDEN, "Not allowed.");
        boolean featured = Boolean.TRUE.equals(body(request).get("featured"));
        repo.setFeatured(id, featured);
        return ok(Map.of("id", id, "featured", featured));
    }

    @PostMapping("/admin/console/api/marketplace/paintings/{id}/release")
    public ResponseEntity<String> releaseHold(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace.publish")) return error(HttpStatus.FORBIDDEN, "Not allowed.");
        boolean released = market.forceRelease(id);
        repo.syncStudentListing(id);
        return released ? ok(Map.of("id", id, "released", true)) : error(HttpStatus.CONFLICT, "Nobody is holding this piece.");
    }

    /** JSON: { status, courier, tracking, notes, relist } */
    @PostMapping("/admin/console/api/marketplace/orders/{orderId}")
    public ResponseEntity<String> order(HttpServletRequest request, @PathVariable("orderId") String orderId) throws IOException {
        Row admin = auth.admin(request);
        if (!Personas.can(admin, "marketplace.orders")) return error(HttpStatus.FORBIDDEN, "Your role can't update orders.");
        Map<String, Object> b = body(request);
        try {
            Map<String, Object> o = market.advanceOrder(orderId, ListingInput.str(b.get("status")), ListingInput.str(b.get("courier")),
                    ListingInput.str(b.get("tracking")), ListingInput.str(b.get("notes")), Boolean.TRUE.equals(b.get("relist")));
            Map<String, Object> m = MarketplaceController.orderView(o);
            m.put("staff_notes", o.get("staff_notes"));
            return ok(m);
        } catch (MarketplaceException e) {
            return fail(e);
        }
    }

    // ================================================================ helpers

    private boolean canCreate(Row admin) {
        return Personas.can(admin, "marketplace.edit") || Personas.can(admin, "marketplace.own");
    }

    private boolean canEdit(Row admin, Map<String, Object> row) {
        if (Personas.can(admin, "marketplace.edit")) return true;
        return Personas.can(admin, "marketplace.own") && row.get("artist_admin_id") instanceof Number n && n.longValue() == admin.lng("id");
    }

    /** Instructors see only their own listings and sales. */
    private static Long ownScope(Row admin) {
        return Personas.can(admin, "marketplace.edit") || Personas.can(admin, "marketplace.publish") ? null : admin.lng("id");
    }

    /**
     * What the save button does for this persona. Only publishers can put a piece live; everyone else's
     * new listing, or change to a live listing, goes to the review queue.
     */
    private static String statusFor(Row admin, String intent, String current) {
        boolean publisher = Personas.can(admin, "marketplace.edit");
        return switch (intent) {
            case "publish" -> publisher ? "AVAILABLE" : "PENDING_REVIEW";
            case "submit" -> "PENDING_REVIEW";
            case "keep" -> current == null ? "DRAFT" : (!publisher && "AVAILABLE".equals(current) ? "PENDING_REVIEW" : current);
            default -> "DRAFT";
        };
    }

    private static String message(String status) {
        return switch (status) {
            case "AVAILABLE" -> "Published. It's live in the marketplace.";
            case "PENDING_REVIEW" -> "Sent for review. A moderator will check it before it goes live.";
            case "ARCHIVED" -> "Saved to the archive.";
            default -> "Draft saved.";
        };
    }

    private List<Map<String, Object>> artists(Row admin) {
        return Personas.can(admin, "marketplace.edit")
                ? repo.list("select id, name, admin_type from admins where admin_type in ('admin', 'teacher') order by name")
                : repo.list("select id, name, admin_type from admins where id = ?", admin.lng("id"));
    }

    private Map<String, Object> base(Row admin) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("admin", Personas.view(admin));
        p.put("badges", AdminConsoleController.badges(lifecycle));
        return p;
    }

    private ResponseEntity<String> denied(HttpServletRequest request, Row admin) {
        return inertia.render(request, "Admin/Denied", base(admin));
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

    @SuppressWarnings("unused")
    private static BigDecimal zero() {
        return BigDecimal.ZERO;
    }
}
