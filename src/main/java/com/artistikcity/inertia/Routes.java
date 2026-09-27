package com.artistikcity.inertia;

import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The application's named routes - the same names and URIs as the Laravel routes/web.php,
 * routes/auth.php and routes/api.php files.
 *
 * The table serves two purposes:
 * <ol>
 *   <li>it is published to the browser as the Ziggy configuration, so the React code's
 *       {@code route('course.details', {slug})} calls keep working unchanged;</li>
 *   <li>Java code builds URLs from route names, like Laravel's {@code route()} helper.</li>
 * </ol>
 * The Spring MVC controller mappings implement exactly these URIs.
 */
@Component
public class Routes {

    private static final Pattern PARAM = Pattern.compile("\\{([a-zA-Z_]+)(\\?)?}");

    /** name -> route definition. */
    private final Map<String, Route> routes = new LinkedHashMap<>();

    public record Route(String uri, List<String> methods) {
    }

    public Routes() {
        // ------------------------------------------------------------ public
        get("welcome", "/");
        post("getfreecourses", "get-free-courses");
        get("student.profile", "student/{slug}");
        get("student.feedback", "student-feedback");
        get("teacher.profile", "teacher/{slug}");
        get("instructor", "instructor");
        get("home", "home");
        get("customer.support", "customer-support");
        get("faq", "faq");
        get("contact", "contact");
        post("contact.store", "contact");
        get("terms.conditions", "terms-conditions");
        get("privacy.policy", "privacy-policy");
        get("artistikcity.vision", "artistikcity-vision");
        get("how.it.works", "how-it-works");
        get("studio.stories", "studio-stories");
        get("studio.home", "dashboard");
        get("studio.classroom", "dashboard/classroom/{id}");
        get("studio.submissions", "dashboard/submissions");
        get("studio.portfolio", "dashboard/portfolio");
        get("student.shop", "student-shop");
        get("admin.console", "admin/dashboard");
        get("admin.console.submissions", "admin/dashboard/submissions");
        get("admin.console.commissions", "admin/dashboard/commissions");
        get("admin.console.marketplace", "admin/dashboard/marketplace");
        get("admin.console.people", "admin/dashboard/people");
        get("admin.console.testimonials", "admin/dashboard/testimonials");
        get("admin.console.gallery", "admin/dashboard/gallery");
        get("commission.step1", "commission/step-1");
        get("commission.step2", "commission/step-2");
        get("commission.step3", "commission/step-3");
        get("commission.step4", "commission/step-4");
        get("commission.preview", "commission/preview");
        get("commission.checkout", "commission/checkout");
        get("commission.thankyou", "commission/thank-you");
        get("join", "join");
        post("user.login.post", "user-login");
        post("user.register.post", "user-registration");
        get("courses", "courses");
        get("course.details", "course/{slug}");

        // --------------------------------------------------- cart / checkout
        post("course.add.cart.post", "add-to-cart");
        get("course.checkout", "checkout");
        post("course.checkout.billing", "checkout/billing");
        add("course.checkout.billing.update", "checkout/billing/{order_id}", "PATCH");
        get("course.checkout.payment", "checkout/payment");
        post("course.checkout.payment.store", "checkout/payment");
        get("course.payment.success", "payment/success");
        get("course.payment.failed", "payment/failed");
        get("paywithpaypal", "paywithpaypal");
        post("paypal", "paypal");
        get("status", "paypal");

        // -------------------------------------------------------------- blog
        get("blog.posts", "blog/posts/{category?}");
        get("blog.post.details", "blog/post/{slug}");

        // ------------------------------------------------------ student area
        get("user.dashboard", "user/dashboard");
        get("user.courses", "user/my-courses");
        get("user.workshop", "user/my-workshop");
        get("user.course.home", "user/course/{order_id}");
        get("user.course.modules", "user/course/modules/{id}");
        get("user.course.syllabus", "user/course/syllabus/{id}");
        get("user.course.module.lesson", "user/course/module/lesson/{module_id}");
        get("user.course.module.lesson.task", "user/course/module/lesson/task/{task_id}");
        post("user.course.module.lesson.task.save", "user/course/module/lesson/task/save");
        get("user.workshop.home", "user/workshop/{id}");
        get("user.free.courses", "user/free-courses");
        get("user.free.course.home", "user/free-course/{id}");
        get("user.help", "user/help");
        get("user.account", "user/my-account");
        post("user.account.save", "user/my-account");
        get("user.change.password", "user/change-password");
        post("user.change.password.save", "user/change-password");
        get("user.notifications", "user/notifications");
        get("user.my.address", "user/my-address");
        get("user.order.history", "user/order-history");
        get("user.certificate.download", "user/certificate/download");
        get("user.certificate.share", "user/certificate/share");
        get("user.certificate.social.share", "user/certificate/social_share");

        // -------------------------------------------------------------- auth
        get("register", "register");
        get("login", "login");
        get("password.request", "forgot-password");
        post("password.email", "forgot-password");
        get("password.reset", "reset-password/{token}");
        post("password.update", "reset-password");
        get("verification.notice", "verify-email");
        get("verification.verify", "verify-email/{id}/{hash}");
        post("verification.send", "email/verification-notification");
        get("password.confirm", "confirm-password");
        post("logout", "logout");
        get("social.login", "social-login/{provider}");

        // ------------------------------------------------------------- admin
        get("admin.login", "admin/login");
        post("admin.login.request", "admin/login");
        post("admin.logout", "admin/logout");
        get("admin.password.request", "admin/password/reset");
        post("admin.password.email", "admin/password/email");
        get("admin.password.reset", "admin/password/reset/{token}");
        post("admin.password.update", "admin/password/reset");
        get("admin.dashboard", "admin/dashboard");

        get("admin.teachers", "admin/teachers");
        get("admin.teacher.create", "admin/teacher/create");
        post("admin.teacher.store", "admin/teacher/create");
        get("admin.teacher.edit", "admin/teacher/edit/{teacher_id}");
        add("admin.teacher.update", "admin/teacher/edit/{teacher_id}", "PATCH");
        get("admin.teacher.destroy", "admin/teacher/delete/{teacher_id}");

        get("admin.courses", "admin/courses");
        get("admin.courses.create", "admin/courses/create");
        post("admin.courses.store", "admin/courses/store");
        get("admin.courses.edit", "admin/courses/edit/{course_id}");
        add("admin.courses.update", "admin/courses/edit/{course_id}", "PATCH");
        get("admin.courses.show", "admin/courses/{course_id}");
        add("admin.courses.destroy", "admin/courses/delete/{course_id}", "DELETE");
        get("admin.courses.archived.list", "admin/courses/archived/list");
        get("admin.courses.create.duplicate", "admin/courses/duplicate/{course_id}");

        get("admin.free.courses", "admin/free-courses");
        get("admin.free.courses.create", "admin/free-courses/create");
        post("admin.free.courses.store", "admin/free-courses/store");
        get("admin.free.courses.edit", "admin/free-courses/edit/{course_id}");
        add("admin.free.courses.update", "admin/free-courses/edit/{course_id}", "PATCH");
        get("admin.free.courses.show", "admin/free-courses/{course_id}");
        add("admin.free.courses.destroy", "admin/free-courses/delete/{course_id}", "DELETE");

        get("admin.workshop", "admin/workshop");
        get("admin.workshop.create", "admin/workshop/create");
        get("admin.workshop.edit", "admin/workshop/edit/{course_id}");

        get("admin.students", "admin/students");
        get("admin.students.create", "admin/students/create");
        post("admin.students.store", "admin/students/store");
        get("admin.students.edit", "admin/students/edit/{student_id}");
        add("admin.students.update", "admin/students/edit/{student_id}", "PATCH");
        get("admin.students.show", "admin/students/details/{student_id}");
        add("admin.students.destroy", "admin/students/delete/{student_id}", "DELETE");
        get("admin.students.artworks", "admin/students/artworks");
        get("admin.students.create.artwork", "admin/students/create-artwork");
        post("admin.students.store.artwork", "admin/students/store-artwork");
        get("admin.students.edit.artwork", "admin/students/edit-artwork/{artwork_id}");
        add("admin.students.update.artwork", "admin/students/edit-artwork/{artwork_id}", "PATCH");
        add("admin.students.destroy.artwork", "admin/students/delete-artwork/{artwork_id}", "DELETE");

        get("admin.home.artworks", "admin/home/artworks");
        get("admin.home.create.artwork", "admin/home/create-artwork");
        post("admin.home.store.artwork", "admin/home/store-artwork");
        get("admin.home.edit.artwork", "admin/home/edit-artwork/{artwork_id}");
        add("admin.home.update.artwork", "admin/home/edit-artwork/{artwork_id}", "PATCH");
        add("admin.home.destroy.artwork", "admin/home/delete-artwork/{artwork_id}", "DELETE");

        get("admin.public.profile", "admin/profile");
        post("admin.public.profile.save", "admin/save-profile");
        get("admin.change.password", "admin/change-password");
        post("admin.update.password", "admin/update-password");

        get("admin.orders", "admin/orders");
        get("admin.order.details", "admin/order/details/{order_id}");
        get("admin.order.issue.certificate", "admin/order/issue-certificate/{order_id}");

        get("admin.newsletter", "admin/newsletter");
        add("admin.newsletter.destroy", "admin/newsletter/delete/{course_id}", "DELETE");
        get("admin.settings", "admin/settings");
        post("admin.settings.update", "admin/settings/update");
        get("admin.tasks", "admin/tasks");

        resource("admin.mediums", "admin/mediums", "medium");
        resource("admin.genres", "admin/genres", "genre");
        resource("admin.skills", "admin/skills", "skill");
        resource("admin.course_types", "admin/course_types", "course_type");
        resource("admin.testimonials", "admin/testimonials", "testimonial");
        resource("admin.blog.category", "admin/blog/category", "category");
        resource("admin.blog.tag", "admin/blog/tag", "tag");
        resource("admin.blog.post", "admin/blog/post", "post");

        // --------------------------------------------------------------- api
        get("modules", "api/modules");
        get("course.modules", "api/modules/{course_id}");
        post("course.module.store", "api/modules/create");
        post("course.module.lesson.store", "api/modules/lesson/create");
        get("course.module.lessons", "api/modules/lessons/{course_id}");
        get("course.project", "api/project/{course_id}");
        post("course.project.store", "api/project/create");
        post("newsletter.save", "api/newsletter");
        post("course.module.lesson.task.store", "api/modules/lesson/task/create");
        get("course.module.lessons.task", "api/modules/lessons/task/{lesson_id}");
        post("course.module.lesson.task.reply", "api/modules/lessons/task/reply");
        post("course.module.lesson.task.changestatus", "api/modules/lessons/task/change-status");
    }

    private void get(String name, String uri) {
        add(name, uri, "GET", "HEAD");
    }

    private void post(String name, String uri) {
        add(name, uri, "POST");
    }

    private void add(String name, String uri, String... methods) {
        routes.put(name, new Route(uri, List.of(methods)));
    }

    /** Route::resource() names and URIs. */
    private void resource(String name, String uri, String param) {
        get(name + ".index", uri);
        get(name + ".create", uri + "/create");
        post(name + ".store", uri);
        get(name + ".show", uri + "/{" + param + "}");
        get(name + ".edit", uri + "/{" + param + "}/edit");
        add(name + ".update", uri + "/{" + param + "}", "PUT", "PATCH");
        add(name + ".destroy", uri + "/{" + param + "}", "DELETE");
    }

    public Map<String, Route> all() {
        return routes;
    }

    /**
     * Relative URL for a named route, e.g. {@code path("course.details", slug)}.
     * Extra parameters (name/value pairs given as a Map) are appended as a query string.
     */
    public String path(String name, Object... params) {
        Route r = routes.get(name);
        if (r == null) {
            throw new IllegalArgumentException("Route [" + name + "] not defined.");
        }
        Map<String, Object> query = new LinkedHashMap<>();
        List<Object> positional = new ArrayList<>();
        for (Object p : params) {
            if (p instanceof Map<?, ?> m) {
                m.forEach((k, v) -> query.put(String.valueOf(k), v));
            } else {
                positional.add(p);
            }
        }
        Matcher m = PARAM.matcher(r.uri());
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (m.find()) {
            Object value = null;
            if (query.containsKey(m.group(1))) {
                value = query.remove(m.group(1));
            } else if (i < positional.size()) {
                value = positional.get(i++);
            }
            if (value == null && m.group(2) == null) {
                throw new IllegalArgumentException("Missing parameter " + m.group(1) + " for route " + name);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : encode(String.valueOf(value))));
        }
        m.appendTail(sb);
        String path = sb.toString().replaceAll("/+$", "");
        path = path.startsWith("/") ? path : "/" + path;
        if (!query.isEmpty()) {
            StringBuilder q = new StringBuilder();
            query.forEach((k, v) -> q.append(q.length() == 0 ? "?" : "&").append(encode(k)).append('=')
                    .append(v == null ? "" : encode(String.valueOf(v))));
            path += q;
        }
        return path;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
