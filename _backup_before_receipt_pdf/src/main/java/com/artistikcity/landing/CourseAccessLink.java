package com.artistikcity.landing;

import com.artistikcity.auth.AccountService;
import com.artistikcity.auth.AuthEvents;
import com.artistikcity.auth.EmailTokenService;
import com.artistikcity.auth.SessionService;
import com.artistikcity.http.Auth;
import com.artistikcity.http.Flash;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import com.artistikcity.view.Views;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "Go to my course" in the landing thank-you e-mail: a single-use sign-in link (auth_tokens, purpose magic_link, kind
 * course_access, 72 hours, only the SHA-256 is stored) that signs the buyer in and opens the course.
 * <ul>
 *   <li>{@code GET /auth/course-access?t=} shows a small ArtistikCity page that continues by itself with JavaScript (a
 *       POST), so mail scanners and link previews that only fetch the link can't use it up; without JavaScript a
 *       button does the same.</li>
 *   <li>{@code POST /auth/course-access} {t}: consumes the link, signs in (method magic_link) and opens the course.
 *       Clicking proves the inbox, so an unverified e-mail becomes verified, with the same protection as the magic link
 *       (anything an earlier, unproven owner of that address set up is dropped).</li>
 *   <li>Used or expired: the log-in page (mobile or e-mail code), then the course.</li>
 * </ul>
 */
@RestController
public class CourseAccessLink {

    static final Duration TTL = Duration.ofHours(72);

    private final Db db;
    private final Auth auth;
    private final Views views;
    private final EmailTokenService tokens;
    private final AccountService accounts;
    private final SessionService sessions;
    private final AuthEvents events;

    public CourseAccessLink(Db db, Auth auth, Views views, EmailTokenService tokens, AccountService accounts, SessionService sessions, AuthEvents events) {
        this.db = db;
        this.auth = auth;
        this.views = views;
        this.tokens = tokens;
        this.accounts = accounts;
        this.sessions = sessions;
        this.events = events;
    }

    /** A link for the buyer of this order, or null when the e-mail isn't the account's own address. */
    public String issue(HttpServletRequest request, long orderId, String email) {
        Row o = db.first("select o.user_id, u.email from orders o join users u on u.id = o.user_id where o.id = ?", orderId);
        if (o == null || email == null || !email.equalsIgnoreCase(o.str("email", ""))) {
            return null;
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", "course_access");
        meta.put("order", orderId);
        meta.put("return_to", "/user/course/" + orderId);
        return "/auth/course-access?t=" + tokens.issue(request, EmailTokenService.MAGIC_LINK, o.str("email"), o.lng("user_id"), TTL, meta);
    }

    @GetMapping("/auth/course-access")
    public ResponseEntity<String> page(HttpServletRequest request, @RequestParam(value = "t", required = false) String t) {
        Row token = tokens.peek(EmailTokenService.MAGIC_LINK, t);
        if (token == null || !"course_access".equals(tokens.meta(token).get("kind"))) {
            return expired(request, null);
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("t", t);
        model.put("email", token.str("email"));
        return views.page(request, "landing/course-access", model);
    }

    @PostMapping("/auth/course-access")
    public ResponseEntity<String> consume(HttpServletRequest request, @RequestParam(value = "t", required = false) String t) {
        Row token = tokens.peek(EmailTokenService.MAGIC_LINK, t);
        if (token == null || !"course_access".equals(tokens.meta(token).get("kind"))) {
            return expired(request, null);
        }
        token = tokens.consume(EmailTokenService.MAGIC_LINK, t);
        if (token == null) {
            return expired(request, null);
        }
        Map<String, Object> meta = tokens.meta(token);
        String back = meta.get("return_to") instanceof String s && s.startsWith("/user/course/") && s.length() < 60 ? s : "/dashboard";
        Row user = accounts.findByEmail(token.str("email"));
        if (user == null || (token.get("user_id") != null && user.lng("id") != token.lng("user_id")) || user.lng("status", 1) == 0) {
            return expired(request, back);
        }
        long id = user.lng("id");
        if (user.get("email_verified_at") == null) {
            // first proof of this inbox: drop anything an earlier, unproven owner of the address set up
            db.updateById("users", id, Row.of("password_hash", null));
            sessions.revokeAll(id, null, "unverified_account_claimed");
            events.record(request, id, "unverified_account_claimed", "magic_link", true, token.str("email"));
            accounts.markEmailVerified(id);
        }
        auth.login(request, id, "magic_link", true);
        events.ok(request, id, "login", "course_access_link");
        return redirect(back);
    }

    private ResponseEntity<String> expired(HttpServletRequest request, String back) {
        if (auth.userId(request) != null) {
            return redirect(back == null ? "/dashboard" : back);
        }
        Flash.put(request, "status", "This course link has expired or was already used. Log in with your mobile number or e-mail and a one-time code to open your course.");
        return redirect("/login?return_to=" + java.net.URLEncoder.encode(back == null ? "/dashboard" : back, java.nio.charset.StandardCharsets.UTF_8));
    }

    private static ResponseEntity<String> redirect(String to) {
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", to).build();
    }
}
