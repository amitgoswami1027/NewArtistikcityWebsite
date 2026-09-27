package com.artistikcity.web.user;

import com.artistikcity.http.Auth;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.http.Redirects;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.CertificateService;
import com.artistikcity.service.CourseService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Storage;
import com.artistikcity.support.ValidationException;
import com.artistikcity.support.Validator;
import com.artistikcity.support.Values;
import com.artistikcity.view.Views;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Student account pages and certificates (User\AccountController). */
@Controller
public class UserAccountController {

    private final Inertia inertia;
    private final Views views;
    private final Db db;
    private final Json json;
    private final Auth auth;
    private final Validator validator;
    private final Storage storage;
    private final CertificateService certificates;
    private final Redirects redirect;

    public UserAccountController(Inertia inertia, Views views, Db db, Json json, Auth auth, Validator validator, Storage storage,
                                 CertificateService certificates, Redirects redirect) {
        this.inertia = inertia;
        this.views = views;
        this.db = db;
        this.json = json;
        this.auth = auth;
        this.validator = validator;
        this.storage = storage;
        this.certificates = certificates;
        this.redirect = redirect;
    }

    @GetMapping("/user/my-account")
    public ResponseEntity<String> account(HttpServletRequest request) {
        return inertia.renderUser(request, "Students/MyAccount", Map.of());
    }

    @PostMapping("/user/my-account")
    public ResponseEntity<String> saveAccount(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "name", "required", "email", "required|email");
        Long id = auth.userId(request);
        if (db.count("select count(*) from users where email = ? and id <> ?", in.get("email"), id) > 0) {
            throw ValidationException.withMessage("email", "The email has already been taken.");
        }
        db.updateById("users", id, Row.of("name", in.get("name"), "email", in.get("email"), "phone", in.get("phone"),
                "profile_title", in.get("profile_title"), "profile_description", in.get("profile_description"),
                "location", in.get("location")));
        if (in.hasFile("tmp_profile_photo")) {
            String name = storage.storeUpload(in.file("tmp_profile_photo"), "uploads/students/" + id);
            db.updateById("users", id, Row.of("profile_photo", name));
        }
        auth.refresh(request);
        return redirect.route(request, "user.account");
    }

    @GetMapping("/user/change-password")
    public ResponseEntity<String> changePassword(HttpServletRequest request) {
        return inertia.renderUser(request, "Students/ChangePassword", Map.of());
    }

    @PostMapping("/user/change-password")
    public ResponseEntity<String> saveChangePassword(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "password", "required", "new_password", "required", "confirm_new_password", "same:new_password");
        Row u = db.first("select password from users where id = ?", auth.userId(request));
        if (!auth.check(in.get("password"), u.str("password"))) {
            throw ValidationException.withMessage("password", "The current password is incorrect.");
        }
        db.updateById("users", auth.userId(request), Row.of("password", auth.hash(in.get("new_password"))));
        return redirect.route(request, "user.account");
    }

    @GetMapping("/user/notifications")
    public ResponseEntity<String> notifications(HttpServletRequest request) {
        return inertia.renderUser(request, "Students/Notifications", Map.of());
    }

    @GetMapping("/user/linked-accounts")
    public ResponseEntity<String> linkedAccounts(HttpServletRequest request) {
        return inertia.renderUser(request, "Students/LinkedAccounts", Map.of());
    }

    @GetMapping("/user/language-preference")
    public ResponseEntity<String> languagePreference(HttpServletRequest request) {
        return inertia.renderUser(request, "Students/LanguagePreference", Map.of());
    }

    @GetMapping("/user/help")
    public ResponseEntity<String> help(HttpServletRequest request) {
        return inertia.renderUser(request, "Students/Help", Map.of());
    }

    @GetMapping("/user/my-address")
    public ResponseEntity<String> myAddress(HttpServletRequest request) {
        return inertia.renderUser(request, "Students/MyAddress",
                Map.of("myAddress", db.select("select * from user_addresses where user_id = ? and status = 1 order by id", auth.userId(request))));
    }

    @GetMapping("/user/order-history")
    public ResponseEntity<String> orderHistory(HttpServletRequest request) {
        List<Row> orders = db.select("select c.title, c.sub_title, o.id, o.order_number, o.created_at as order_created_at, c.id as course_id,"
                + " c.course_type_id, c.schedule_pdf, o.payment_option, o.billing_name, o.billing_email, o.billing_address, o.billing_city,"
                + " o.billing_state, o.billing_zipcode, o.billing_country, o.price_type, o.price, o.payment_status, o.issue_certificate, "
                + CourseService.photoSubquery("o.course_id")
                + " from orders o join courses c on c.id = o.course_id where o.user_id = ? order by o.id", auth.userId(request));
        for (Row o : orders) {
            // DATE_FORMAT(orders.created_at, "%d-%b-%Y")
            String created = (String) o.remove("order_created_at");
            o.put("order_date", created == null ? null : java.time.LocalDate.parse(created.substring(0, 10))
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.ENGLISH)));
        }
        return inertia.renderUser(request, "Students/OrderHistory", Map.of("orderHistory", orders));
    }

    // ------------------------------------------------------------ certificates

    @GetMapping("/user/certificate/download")
    public ResponseEntity<byte[]> downloadCertificate(HttpServletRequest request, @RequestParam("id") Long orderId) {
        Row cert = NotFoundException.orFail(certificates.forOrder(auth.userId(request), orderId));
        String name = cert.str("certificate_name");
        byte[] pdf = name != null && storage.exists("certificates/" + name)
                ? storage.read("certificates/" + name) : certificates.render(certificates.model(cert));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(pdf);
    }

    /** HTML certificate that renders itself to an image (for social sharing). */
    @GetMapping("/user/certificate/share")
    public ResponseEntity<String> shareCertificate(HttpServletRequest request, @RequestParam("id") Long orderId) {
        Row cert = NotFoundException.orFail(certificates.forOrder(auth.userId(request), orderId));
        return views.page(request, "certificates/certificate-image", certificates.model(cert));
    }

    /** Page with Open Graph tags used when a certificate is shared on Facebook/Twitter/LinkedIn. */
    @GetMapping("/user/certificate/social_share")
    public ResponseEntity<String> socialShare(HttpServletRequest request, @RequestParam(value = "order_id", required = false) String encoded) {
        String image = null;
        Long orderId = null;
        try {
            orderId = encoded == null ? null : Values.toLong(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ignored) {
            // invalid id
        }
        if (orderId != null) {
            Row cert = db.first("select top 1 certificate_name from certificates where order_id = ? order by id desc", orderId);
            if (cert != null) {
                image = Inertia.baseUrl(request) + "/storage/certificates/" + cert.str("certificate_name").replace(".pdf", ".jpeg");
            }
        }
        String q = request.getQueryString();
        return views.page(request, "certificates/share-certificate",
                Row.of("pageUrl", Inertia.baseUrl(request) + request.getRequestURI() + (q == null ? "" : "?" + q), "imageUrl", image));
    }
}
