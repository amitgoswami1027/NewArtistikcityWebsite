package com.artistikcity.web;

import com.artistikcity.auth.AccountService;
import com.artistikcity.auth.AuthEvents;
import com.artistikcity.auth.AuthMail;
import com.artistikcity.auth.EmailTokenService;
import com.artistikcity.auth.RateLimiter;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Redirects;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.CourseService;
import com.artistikcity.service.Mailer;
import com.artistikcity.service.PostService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Slugs;
import com.artistikcity.support.Str;
import com.artistikcity.support.Validator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Home page (WelcomeController.php). */
@Controller
public class WelcomeController {

    public static final String INSTRUCTOR_SLUG = "vaishali-j-goswami";

    private final Inertia inertia;
    private final Db db;
    private final CourseService courses;
    private final PostService posts;
    private final Validator validator;
    private final Json json;
    private final Auth auth;
    private final Slugs slugs;
    private final Mailer mailer;
    private final Redirects redirect;
    private final AccountService accounts;
    private final EmailTokenService emailTokens;
    private final AuthMail authMail;
    private final RateLimiter limiter;

    public WelcomeController(Inertia inertia, Db db, CourseService courses, PostService posts, Validator validator, Json json,
                             Auth auth, Slugs slugs, Mailer mailer, Redirects redirect, AccountService accounts,
                             EmailTokenService emailTokens, AuthMail authMail, RateLimiter limiter) {
        this.accounts = accounts;
        this.emailTokens = emailTokens;
        this.authMail = authMail;
        this.limiter = limiter;
        this.inertia = inertia;
        this.db = db;
        this.courses = courses;
        this.posts = posts;
        this.validator = validator;
        this.json = json;
        this.auth = auth;
        this.slugs = slugs;
        this.mailer = mailer;
        this.redirect = redirect;
    }

    @GetMapping("/")
    public ResponseEntity<String> welcome(HttpServletRequest request) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("canLogin", true);
        props.put("canRegister", true);
        props.put("laravelVersion", "Spring Boot");
        props.put("phpVersion", System.getProperty("java.version"));
        props.put("mediums", db.select("select * from mediums order by id"));
        props.put("open_courses", courses.collection(courses.withStats(courses.withRelations(courses.upcoming(8, CourseService.TYPE_COURSE, CourseService.TYPE_WORKSHOP), true))));
        props.put("open_workshops", courses.collection(courses.withRelations(courses.upcoming(6, CourseService.TYPE_WORKSHOP), true)));
        props.put("openHomeText", homeCourseWorkshopText());
        props.put("home_artworks", db.select("select id, photo_name from home_artworks where status = 1 order by id desc"));
        props.put("blog_posts", posts.present(db.select("select id, title, slug, image, created_at from posts where status = 1 order by id limit 3"), false));
        props.put("testimonials", db.select("select id, name, slug, title, description, photo from testimonials where status = 1 order by id limit 3"));
        props.put("instructor", instructor());
        return inertia.render(request, "Welcome", props);
    }

    /** Admin::where('slug', 'vaishali-j-goswami')->first() (falls back to the first teacher). */
    private Row instructor() {
        Row i = db.first("select * from admins where slug = ?", INSTRUCTOR_SLUG);
        if (i == null) {
            i = db.first("select * from admins order by case when admin_type = 'teacher' then 0 else 1 end, id limit 1");
        }
        return i == null ? new Row() : i.without("password", "remember_token");
    }

    /** helpers.php getHomeCourseWorkshopText(). */
    private Map<String, Object> homeCourseWorkshopText() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("courseText", db.select("select * from settings where meta_key in ('course_text_1', 'course_text_2', 'course_text_3') order by id"));
        m.put("workshopText", db.select("select * from settings where meta_key in ('workshop_text_1', 'workshop_text_2', 'workshop_text_3') order by id"));
        return m;
    }

    /**
     * "Get free courses" form on the home page: creates a passwordless account and e-mails a one-time
     * sign-in link (no password is ever generated or e-mailed).
     */
    @PostMapping("/get-free-courses")
    public ResponseEntity<String> getFreeCourses(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "name", "required|max:255", "email", "required|email|max:255|unique:users");
        if (!limiter.hit("freecourses:ip:" + AuthEvents.ip(request), 5, 3600).allowed()) {
            return redirect.routeWith(request, "message", "Too many sign-ups from your network. Please try again later.", "welcome");
        }
        String type = in.get("free_course_type", "default");
        if (!List.of("color", "black-white", "default").contains(type)) {
            type = "default";
        }
        String email = AccountService.normalizeEmail(in.get("email"));
        long id = accounts.create(in.get("name"), email, false, null, false, null, null);
        db.updateById("users", id, Row.of("free_courses", 1, "free_course_type", type));
        String token = emailTokens.issue(request, EmailTokenService.MAGIC_LINK, email, id, AuthMail.MAGIC_LINK_TTL,
                Map.of("return_to", "/user/free-courses"));
        Map<String, Object> mail = Row.of("userData", Row.of("name", in.get("name"), "email", email),
                "loginUrl", authMail.baseUrl(request) + "/auth/magic-link?token=" + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8),
                "expire", AuthMail.MAGIC_LINK_TTL.toMinutes(), "baseUrl", authMail.baseUrl(request));
        boolean sent = mailer.send(email, "Your free ArtistikCity courses", "emails/free-courses-notify", mail);
        return redirect.routeWith(request, "message", sent
                ? "Thanks for registering! Check your e-mail for your sign-in link."
                : "We couldn't send the e-mail right now. Please try again later.", "welcome");
    }
}
