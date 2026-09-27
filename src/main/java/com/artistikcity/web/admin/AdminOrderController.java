package com.artistikcity.web.admin;

import com.artistikcity.http.Flash;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.http.Redirects;
import com.artistikcity.service.CertificateService;
import com.artistikcity.service.CourseService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Paginator;
import com.artistikcity.support.Row;
import com.artistikcity.view.Views;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/** Orders / certificates, newsletter subscribers and site settings (Admin\OrderController, NewsletterController, SettingController). */
@Controller
public class AdminOrderController {

    private final Views views;
    private final Db db;
    private final Json json;
    private final CourseService courses;
    private final CertificateService certificates;
    private final Redirects redirect;

    public AdminOrderController(Views views, Db db, Json json, CourseService courses, CertificateService certificates, Redirects redirect) {
        this.views = views;
        this.db = db;
        this.json = json;
        this.courses = courses;
        this.certificates = certificates;
        this.redirect = redirect;
    }

    private Row withCourseAndUser(Row o, boolean full) {
        Row course = db.find("courses", o.get("course_id"));
        if (course != null && full) {
            courses.withRelations(course, false);
        }
        o.put("course", course);
        Row user = db.find("users", o.get("user_id"));
        if (user != null) {
            user = user.without("password", "remember_token");
            if (full) {
                user.put("addresses", db.select("select * from user_addresses where user_id = ? order by id", user.get("id")));
            }
        }
        o.put("user", user);
        return o;
    }

    @GetMapping("/admin/orders")
    public ResponseEntity<String> index(HttpServletRequest request, @RequestParam(value = "page", required = false) String pageParam) {
        int perPage = 10;
        int page = Paginator.page(pageParam);
        long total = db.count("select count(*) from orders");
        List<Row> orders = db.select("select * from orders order by id desc offset " + ((page - 1) * perPage) + " rows fetch next " + perPage + " rows only");
        for (Row o : orders) {
            withCourseAndUser(o, false);
        }
        return views.page(request, "admin/orders/index", Map.of("orders", orders,
                "pagination", Paginator.links(total, perPage, page, request.getRequestURI())));
    }

    /** Loaded into the order details modal (?view_type=popup). */
    @GetMapping("/admin/order/details/{order_id}")
    public ResponseEntity<String> details(HttpServletRequest request, @PathVariable("order_id") Long id) {
        Row order = withCourseAndUser(NotFoundException.orFail(db.find("orders", id)), true);
        return views.page(request, "admin/orders/order_details_popup", Map.of("order", order));
    }

    @GetMapping("/admin/order/issue-certificate/{order_id}")
    public ResponseEntity<String> issueCertificate(HttpServletRequest request, @PathVariable("order_id") Long id) {
        Row cert = certificates.issue(id);
        if (cert == null) {
            Flash.put(request, "failed", "Something went wrong!");
            return redirect.route(request, "admin.orders");
        }
        Flash.put(request, "success", "Certificate issued to student.");
        Flash.put(request, "certificate_success", "Certificate issued to student success.");
        Flash.put(request, "certificate_order_id", id);
        return redirect.back(request);
    }

    // -------------------------------------------------------------- newsletter

    @GetMapping("/admin/newsletter")
    public ResponseEntity<String> newsletter(HttpServletRequest request) {
        return views.page(request, "admin/newsletter/index", Map.of("newsletter", db.select("select * from newsletters order by id")));
    }

    @DeleteMapping("/admin/newsletter/delete/{id}")
    public ResponseEntity<String> deleteNewsletter(HttpServletRequest request, @PathVariable("id") Long id) {
        db.deleteById("newsletters", id);
        return redirect.backWith(request, "success", "Newsletter email successfully deleted!");
    }

    // ---------------------------------------------------------------- settings

    @GetMapping("/admin/settings")
    public ResponseEntity<String> settings(HttpServletRequest request) {
        return views.page(request, "admin/settings/index", Map.of("settings", db.select("select * from settings order by id")));
    }

    @PostMapping("/admin/settings/update")
    public ResponseEntity<String> updateSettings(HttpServletRequest request) {
        Input in = Input.of(request, json);
        String tag = in.get("meta_tag");
        for (Map.Entry<String, Object> e : in.except("_token", "meta_tag", "save_setting").entrySet()) {
            String value = e.getValue() == null ? null : String.valueOf(e.getValue());
            if (db.updateWhere("settings", Row.of("meta_value", value), "meta_tag = ? and meta_key = ?", tag, e.getKey()) == 0) {
                db.insert("settings", Row.of("meta_tag", tag, "meta_key", e.getKey(), "meta_value", value));
            }
        }
        return redirect.routeWith(request, "success", "The setting has been saved!", "admin.settings");
    }
}
