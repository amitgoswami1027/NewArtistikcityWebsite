package com.artistikcity.selling;

import com.artistikcity.config.Settings;
import com.artistikcity.http.Auth;
import com.artistikcity.http.Csrf;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.lifecycle.CredentialsService;
import com.artistikcity.marketplace.ListingInput;
import com.artistikcity.marketplace.MarketplaceImages;
import com.artistikcity.marketplace.MarketplaceRepository;
import com.artistikcity.moderation.Moderation;
import com.artistikcity.security.AuditLog;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Slugs;
import com.artistikcity.support.UploadedFile;
import com.artistikcity.support.ValidationException;
import com.artistikcity.marketplace.MarketplaceException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * My studio > Sell your art (step 7 of the journey, {@code /dashboard/sell}). A student certified in a course can offer
 * the approved work they made for that course as an original in the marketplace: they write the listing and set the
 * price, it goes to the staff review queue (Admin > Sales > Marketplace, status PENDING_REVIEW) and goes live when
 * approved. Sales pay the student the artist share through the ledger (payee USER), to the payout details they keep
 * here (encrypted, {@link PayoutDetails}). Originals only: prints and downloads stay a staff decision.
 *
 * <p>JSON API (CSRF-checked, signed-in student): {@code POST /api/studio/sell/listings}, {@code PUT .../listings/{id}},
 * {@code POST .../listings/{id}/withdraw}, {@code POST /api/studio/sell/images}, {@code POST /api/studio/sell/payout}.
 */
@Controller
public class StudentSellController {

    /** Listing statuses a student can still change. */
    static final List<String> STUDENT_EDITABLE = List.of("DRAFT", "PENDING_REVIEW", "AVAILABLE");
    private static final Duration RECENT_AUTH = Duration.ofMinutes(30);

    private final Inertia inertia;
    private final Auth auth;
    private final Json json;
    private final JdbcTemplate jdbc;
    private final CredentialsService credentials;
    private final MarketplaceRepository market;
    private final MarketplaceImages images;
    private final Slugs slugs;
    private final Settings settings;
    private final PayoutDetails payouts;
    private final AuditLog auditLog;
    private final ObjectProvider<Moderation> moderation;

    public StudentSellController(Inertia inertia, Auth auth, Json json, JdbcTemplate jdbc, CredentialsService credentials, MarketplaceRepository market,
                                 MarketplaceImages images, Slugs slugs, Settings settings, PayoutDetails payouts, AuditLog auditLog,
                                 ObjectProvider<Moderation> moderation) {
        this.inertia = inertia;
        this.auth = auth;
        this.json = json;
        this.jdbc = jdbc;
        this.credentials = credentials;
        this.market = market;
        this.images = images;
        this.slugs = slugs;
        this.settings = settings;
        this.payouts = payouts;
        this.auditLog = auditLog;
        this.moderation = moderation;
    }

    // ================================================================ pages

    @GetMapping({"/dashboard/sell", "/dashboard/sell/"})
    public ResponseEntity<String> page(HttpServletRequest request) {
        long userId = auth.userId(request);
        List<Map<String, Object>> creds = credentials.credentials(userId);
        Set<Long> certified = certifiedCourses(creds);
        List<Map<String, Object>> listings = listings(userId);
        Set<Long> listedSubmissions = new HashSet<>();
        for (Map<String, Object> l : listings) {
            if (l.get("submission_id") != null && !"ARCHIVED".equals(l.get("stock_status"))) listedSubmissions.add(num(l.get("submission_id")));
        }
        List<Map<String, Object>> ready = new ArrayList<>();
        List<Map<String, Object>> locked = new ArrayList<>();
        for (Map<String, Object> s : approvedWork(userId)) {
            if (listedSubmissions.contains(num(s.get("id")))) continue;
            (certified.contains(num(s.get("course_id"))) ? ready : locked).add(s);
        }
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("stage", 7);
        props.put("certified", !certified.isEmpty());
        props.put("certifiedCourses", creds.stream().filter(c -> certified.contains(num(c.get("course_id"))))
                .map(c -> Map.of("id", c.get("course_id"), "title", String.valueOf(c.get("title")))).toList());
        props.put("ready", ready);
        props.put("locked", locked);
        props.put("listings", listings);
        props.put("earnings", earnings(userId));
        props.put("payout", payouts.summary(userId));
        props.put("canStorePayout", payouts.canStore());
        props.put("terms", terms());
        props.put("restriction", restriction(userId));
        return inertia.render(request, "Studio/Sell", props);
    }

    /** The listing form: a new listing for an approved piece (?submission=) or an existing listing. */
    @GetMapping({"/dashboard/sell/new", "/dashboard/sell/listings/{id}"})
    public ResponseEntity<String> editor(HttpServletRequest request, @PathVariable(value = "id", required = false) Long id) {
        long userId = auth.userId(request);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("terms", terms());
        props.put("restriction", restriction(userId));
        if (id != null) {
            Map<String, Object> p = ownListing(userId, id);
            if (p == null) return redirect("/dashboard/sell");
            Map<String, Object> l = new LinkedHashMap<>(p);
            l.put("images", market.images(id));
            props.put("listing", l);
            props.put("submission", submission(userId, num(p.get("submission_id"))));
        } else {
            Long sid = parseLong(request.getParameter("submission"));
            Map<String, Object> s = sid == null ? null : submission(userId, sid);
            if (s == null || !"Approved".equals(s.get("admin_status")) || !certifiedCourses(credentials.credentials(userId)).contains(num(s.get("course_id")))
                    || activeListingFor(sid) != null) {
                return redirect("/dashboard/sell");
            }
            props.put("listing", null);
            props.put("submission", s);
        }
        return inertia.render(request, "Studio/SellListing", props);
    }

    // ================================================================ API

    /** JSON: submissionId + the listing fields + intent (draft | submit). */
    @PostMapping("/api/studio/sell/listings")
    public ResponseEntity<String> create(HttpServletRequest request) throws IOException {
        Row user = student(request);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        long userId = user.lng("id");
        if (moderation.getIfAvailable() != null) moderation.getIfAvailable().assertAllowed(userId, "SELLING");
        Map<String, Object> b = body(request);
        Long sid = b.get("submissionId") instanceof Number n ? n.longValue() : parseLong(String.valueOf(b.get("submissionId")));
        Map<String, Object> s = sid == null ? null : submission(userId, sid);
        if (s == null) return error(HttpStatus.NOT_FOUND, "Choose one of your own approved artworks.");
        if (!"Approved".equals(s.get("admin_status"))) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Only artwork your instructor approved can be sold.");
        if (!certifiedCourses(credentials.credentials(userId)).contains(num(s.get("course_id")))) {
            return error(HttpStatus.FORBIDDEN, "Finish this course and earn its certificate first; then you can sell the work you made for it.");
        }
        if (activeListingFor(sid) != null) return error(HttpStatus.CONFLICT, "This artwork already has a listing. Open it from Your listings.");
        ListingInput in;
        try {
            in = ListingInput.from(withSubmissionImage(b, s));
        } catch (ListingInput.InvalidListingException e) {
            return invalid(e);
        }
        String status = "submit".equals(String.valueOf(b.get("intent"))) ? "PENDING_REVIEW" : "DRAFT";
        long id = market.create(in, slugs.unique("paintings", "slug", in.title(), null), status, "STUDENT",
                user.str("name", "ArtistikCity student"), null, userId, sid);
        market.syncStudentListing(id);
        audit(request, user, AuditLog.Type.CREATE, "listing created", id, status + " · " + in.title() + " · ₹" + in.finalPrice());
        return ok(Map.of("id", id, "status", status, "message", message(status, false)));
    }

    /** JSON: listing fields + version + intent. A live listing that is changed goes back to review. */
    @PutMapping("/api/studio/sell/listings/{id}")
    public ResponseEntity<String> update(HttpServletRequest request, @PathVariable("id") long id) throws IOException {
        Row user = student(request);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        long userId = user.lng("id");
        if (moderation.getIfAvailable() != null) moderation.getIfAvailable().assertAllowed(userId, "SELLING");
        Map<String, Object> p = ownListing(userId, id);
        if (p == null) return error(HttpStatus.NOT_FOUND, "Listing not found.");
        String current = String.valueOf(p.get("stock_status"));
        if (!STUDENT_EDITABLE.contains(current)) {
            return error(HttpStatus.CONFLICT, switch (current) {
                case "RESERVED" -> "A collector is paying for this piece right now, so it can't be changed.";
                case "SOLD" -> "This piece is sold. Congratulations!";
                default -> "This listing is archived. Ask us through the contact page if you want it back.";
            });
        }
        Map<String, Object> b = body(request);
        Map<String, Object> s = submission(userId, num(p.get("submission_id")));
        ListingInput in;
        try {
            in = ListingInput.from(s == null ? b : withSubmissionImage(b, s));
        } catch (ListingInput.InvalidListingException e) {
            return invalid(e);
        }
        boolean submit = "submit".equals(String.valueOf(b.get("intent"))) || "AVAILABLE".equals(current) || "PENDING_REVIEW".equals(current);
        String status = submit ? "PENDING_REVIEW" : "DRAFT";
        int version = b.get("version") instanceof Number n ? n.intValue() : -1;
        if (!market.update(id, version, in, status)) {
            return error(HttpStatus.CONFLICT, "This listing changed since you opened it (it was reviewed, or a collector reserved it). Reload to see the latest version.");
        }
        market.syncStudentListing(id);
        audit(request, user, AuditLog.Type.UPDATE, "listing updated", id, status + " · " + in.title() + " · ₹" + in.finalPrice());
        return ok(Map.of("id", id, "status", status, "message", message(status, "AVAILABLE".equals(current))));
    }

    /** Takes a listing off the marketplace (back to a private draft). */
    @PostMapping("/api/studio/sell/listings/{id}/withdraw")
    public ResponseEntity<String> withdraw(HttpServletRequest request, @PathVariable("id") long id) {
        Row user = student(request);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        Map<String, Object> p = ownListing(user.lng("id"), id);
        if (p == null) return error(HttpStatus.NOT_FOUND, "Listing not found.");
        // clears the last review note, so an old "Approved." is never shown as a change request
        if (!market.transition(id, List.of("PENDING_REVIEW", "AVAILABLE"), "DRAFT", null, "")) {
            return error(HttpStatus.CONFLICT, "RESERVED".equals(p.get("stock_status"))
                    ? "A collector is paying for this piece right now. Try again in 15 minutes." : "This listing can't be withdrawn now.");
        }
        audit(request, user, AuditLog.Type.UPDATE, "listing withdrawn", id, String.valueOf(p.get("title")));
        return ok(Map.of("id", id, "status", "DRAFT", "message", "Withdrawn. It is a private draft again; submit it whenever you're ready."));
    }

    /** multipart: file (JPG/PNG, long edge at least 800 px). Returns {url, width, height}. */
    @PostMapping("/api/studio/sell/images")
    public ResponseEntity<String> image(HttpServletRequest request) {
        Row user = student(request);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        if (moderation.getIfAvailable() != null) moderation.getIfAvailable().assertAllowed(user.lng("id"), "SELLING");
        UploadedFile f = Input.of(request, json).file("file");
        if (f == null || f.getContent() == null || f.getContent().length == 0) return error(HttpStatus.UNPROCESSABLE_ENTITY, "Choose a photo.");
        if (f.getContent().length > MarketplaceImages.MAX_BYTES) return error(HttpStatus.UNPROCESSABLE_ENTITY, "That photo is larger than 20 MB.");
        try {
            return ok(images.store(f.getContent()));
        } catch (MarketplaceException e) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    /** JSON: method (UPI | BANK | PAYPAL), holderName and the method's fields. Needs a recent sign-in. */
    @PostMapping("/api/studio/sell/payout")
    public ResponseEntity<String> payout(HttpServletRequest request) throws IOException {
        Row user = student(request);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        if (!recentlySignedIn(request)) {
            return json(HttpStatus.FORBIDDEN, Map.of("message", "For your security, please sign in again before changing where your earnings are paid.", "reauth", true));
        }
        if (!payouts.canStore()) return error(HttpStatus.SERVICE_UNAVAILABLE, "Payout details can't be saved right now. Please try again later.");
        Map<String, String> clean = new LinkedHashMap<>();
        Map<String, String> errors = PayoutDetails.validate(body(request), clean);
        if (!errors.isEmpty()) return json(HttpStatus.UNPROCESSABLE_ENTITY, Map.of("message", "Please check the highlighted fields.", "errors", errors));
        Map<String, Object> before = payouts.summary(user.lng("id"));
        payouts.save(user.lng("id"), clean);
        Map<String, Object> after = payouts.summary(user.lng("id"));
        auditLog.write(AuditLog.entry(AuditLog.Type.UPDATE, "payout details saved").request(request)
                .actor(AuditLog.Actor.student(user.lng("id"), user.str("email", null)))
                .resource("payout_details", user.lng("id"))
                .before(before == null ? null : Map.of("masked", String.valueOf(before.get("masked"))))
                .after(Map.of("masked", String.valueOf(after.get("masked")))));
        return ok(Map.of("message", "Saved. Your earnings will be paid to " + after.get("masked") + ".", "payout", after));
    }

    // ================================================================ data

    /** Courses the student is certified in: the certificate is issued, or every requirement for it is met. */
    static Set<Long> certifiedCourses(List<Map<String, Object>> creds) {
        Set<Long> out = new HashSet<>();
        for (Map<String, Object> c : creds) {
            if (Boolean.TRUE.equals(c.get("eligible")) || c.get("certificate") != null) out.add(num(c.get("course_id")));
        }
        return out;
    }

    private List<Map<String, Object>> approvedWork(long userId) {
        return jdbc.queryForList("select s.id, s.course_id, s.title, s.description, s.file_url, s.reviewed_at, c.title as course_title"
                + " from student_submissions s join courses c on c.id = s.course_id where s.user_id = ? and s.admin_status = 'Approved' order by s.id desc", userId);
    }

    private Map<String, Object> submission(long userId, long id) {
        List<Map<String, Object>> r = jdbc.queryForList("select s.id, s.course_id, s.title, s.description, s.file_url, s.admin_status, c.title as course_title"
                + " from student_submissions s join courses c on c.id = s.course_id where s.id = ? and s.user_id = ?", id, userId);
        return r.isEmpty() ? null : r.get(0);
    }

    private Map<String, Object> activeListingFor(long submissionId) {
        List<Map<String, Object>> r = jdbc.queryForList("select id from paintings where submission_id = ? and stock_status <> 'ARCHIVED' limit 1", submissionId);
        return r.isEmpty() ? null : r.get(0);
    }

    private Map<String, Object> ownListing(long userId, long id) {
        Map<String, Object> p = market.find(id);
        if (p == null || !"STUDENT".equals(p.get("source")) || p.get("artist_user_id") == null || num(p.get("artist_user_id")) != userId) return null;
        return p;
    }

    private List<Map<String, Object>> listings(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select p.id, p.slug, p.title, p.stock_status, p.final_price, p.base_price, p.medium, p.surface,"
                + " p.height_inches, p.width_inches, p.review_notes, p.reviewed_at, p.published_at, p.sold_at, p.updated_at, p.version, p.submission_id,"
                + " coalesce(p.offers_enabled, 0) as offers_enabled, p.offer_min_pct,"
                + " " + MarketplaceRepository.PRIMARY_IMAGE_SQL + " as image_url"
                + " from paintings p where p.source = 'STUDENT' and p.artist_user_id = ? order by p.updated_at desc, p.id desc", userId);
        BigDecimal share = artistShare();
        for (Map<String, Object> r : rows) {
            r.put("you_earn", dec(r.get("final_price")).multiply(share).setScale(0, RoundingMode.DOWN));
            // "changes requested": a reviewer sent it back to draft with notes
            r.put("changes_requested", "DRAFT".equals(r.get("stock_status")) && r.get("reviewed_at") != null && r.get("review_notes") != null
                    && !String.valueOf(r.get("review_notes")).isBlank());
        }
        return rows;
    }

    /** Earned, paid, payable now, still in the holding period, from the ledger; plus the latest sales. */
    private Map<String, Object> earnings(long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            Map<String, Object> t = jdbc.queryForMap("select coalesce(sum(case when entry_type = 'ARTIST_SHARE' then amount else 0 end), 0) as earned,"
                    + " coalesce(-sum(case when entry_type = 'PAYOUT' then amount else 0 end), 0) as paid,"
                    + " coalesce(sum(amount), 0) as balance,"
                    + " coalesce(sum(case when available_at > ? then amount else 0 end), 0) as holding"
                    + " from ledger_entries where account = 'ARTIST' and payee_type = 'USER' and payee_id = ?", now, userId);
            m.putAll(t);
            m.put("sales", jdbc.queryForList("select e.amount, e.memo, e.created_at, e.available_at from ledger_entries e"
                    + " where e.account = 'ARTIST' and e.payee_type = 'USER' and e.payee_id = ? and e.entry_type = 'ARTIST_SHARE' order by e.id desc limit 10", userId));
        } catch (RuntimeException e) {
            m.put("earned", 0); m.put("paid", 0); m.put("balance", 0); m.put("holding", 0); m.put("sales", List.of());
        }
        return m;
    }

    private Map<String, Object> terms() {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("artistPercent", BigDecimal.valueOf(100).subtract(settings.commissionPercent()).stripTrailingZeros().toPlainString());
        t.put("commissionPercent", settings.commissionPercent().stripTrailingZeros().toPlainString());
        t.put("payoutDays", settings.payoutAfterDays());
        t.put("minPrice", ListingInput.MIN_PRICE);
        return t;
    }

    private BigDecimal artistShare() {
        return BigDecimal.valueOf(100).subtract(settings.commissionPercent()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    private String restriction(long userId) {
        Moderation m = moderation.getIfAvailable();
        if (m == null) return null;
        try {
            m.assertAllowed(userId, "SELLING");
            return null;
        } catch (ValidationException e) {
            return e.getErrors().values().stream().findFirst().orElse("Your account can't offer work for sale right now.");
        }
    }

    /** The approved artwork's own photo is always the first image; extra photos come from uploads. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> withSubmissionImage(Map<String, Object> b, Map<String, Object> s) {
        Map<String, Object> m = new LinkedHashMap<>(b);
        List<Object> imgs = new ArrayList<>();
        String main = String.valueOf(s.get("file_url"));
        imgs.add(Map.of("url", main, "primary", true, "alt", String.valueOf(s.get("title"))));
        if (b.get("images") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> im && !main.equals(String.valueOf(im.get("url")))
                        && String.valueOf(im.get("url")).contains("/uploads/marketplace/")) {
                    imgs.add(Map.of("url", String.valueOf(im.get("url")), "primary", false, "alt", String.valueOf(((Map<String, Object>) im).getOrDefault("alt", ""))));
                }
            }
        }
        m.put("images", imgs);
        m.put("discountPercentage", 0);
        m.put("certificate", false);
        return m;
    }

    private boolean recentlySignedIn(HttpServletRequest request) {
        var c = auth.context(request);
        if (c == null) return false;
        List<Map<String, Object>> r = jdbc.queryForList("select created_at from auth_sessions where id = ?", c.sessionId());
        if (r.isEmpty()) return false;
        Object v = r.get(0).get("created_at");
        LocalDateTime at = v instanceof Timestamp ts ? ts.toLocalDateTime() : v instanceof LocalDateTime l ? l : LocalDateTime.parse(String.valueOf(v).replace(' ', 'T'));
        return at.plus(RECENT_AUTH).isAfter(LocalDateTime.now());
    }

    private void audit(HttpServletRequest request, Row user, AuditLog.Type type, String action, long listingId, String summary) {
        try {
            auditLog.write(AuditLog.entry(type, action).request(request).actor(AuditLog.Actor.student(user.lng("id"), user.str("email", null)))
                    .resource("painting", listingId).summary(summary));
        } catch (RuntimeException e) {
            // the security log never blocks the student
        }
    }

    private static String message(String status, boolean wasLive) {
        return "PENDING_REVIEW".equals(status)
                ? (wasLive ? "Saved. Changes to a live listing are checked again: it is back in review and returns to the marketplace once approved."
                        : "Sent for review. We usually check new listings within 2 working days; you'll see the result here.")
                : "Draft saved. It is private until you submit it for review.";
    }

    // ================================================================ helpers

    private Row student(HttpServletRequest request) {
        if (!Csrf.matches(request)) return null;
        return auth.user(request);
    }

    private Map<String, Object> body(HttpServletRequest request) throws IOException {
        Map<String, Object> m = json.decodeMap(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        return m == null ? Map.of() : m;
    }

    private ResponseEntity<String> invalid(ListingInput.InvalidListingException e) {
        return json(HttpStatus.UNPROCESSABLE_ENTITY, Map.of("message", e.getMessage(), "errors", e.errors(), "step", e.step()));
    }

    private ResponseEntity<String> ok(Object body) {
        return json(HttpStatus.OK, body);
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        return json(status, Map.of("message", message));
    }

    private ResponseEntity<String> json(HttpStatus status, Object body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    private static ResponseEntity<String> redirect(String to) {
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", to).build();
    }

    static long num(Object o) {
        return o instanceof Number n ? n.longValue() : o == null ? -1 : Long.parseLong(String.valueOf(o));
    }

    private static Long parseLong(String s) {
        try {
            return s == null ? null : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal dec(Object o) {
        return o instanceof BigDecimal b ? b : o == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(o));
    }
}
