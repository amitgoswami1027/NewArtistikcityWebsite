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
    private final com.artistikcity.auth.AccountService accounts;
    private final com.artistikcity.auth.SessionService sessions;
    private final com.artistikcity.auth.AuthMail authMail;
    private final com.artistikcity.auth.PasskeyView passkeys;
    private final org.springframework.core.env.Environment env;

    public UserAccountController(Inertia inertia, Views views, Db db, Json json, Auth auth, Validator validator, Storage storage,
                                 CertificateService certificates, Redirects redirect,
                                 com.artistikcity.auth.AccountService accounts, com.artistikcity.auth.SessionService sessions,
                                 com.artistikcity.auth.AuthMail authMail, com.artistikcity.auth.PasskeyView passkeys,
                                 org.springframework.core.env.Environment env) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.authMail = authMail;
        this.passkeys = passkeys;
        this.env = env;
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
        validator.validate(in, "name", "required|max:255", "email", "email|max:255");
        Long id = auth.userId(request);
        Row before = db.find("users", id);
        String email = com.artistikcity.auth.AccountService.normalizeEmail(in.get("email"));
        if (email == null && before.get("email") != null) {
            throw ValidationException.withMessage("email", "Please keep an e-mail address on your account.");
        }
        if (email != null && db.count("select count(*) from users where email = ? and id <> ?", email, id) > 0) {
            throw ValidationException.withMessage("email", "The email has already been taken.");
        }
        Row changes = Row.of("name", in.get("name"), "email", email,
                "profile_title", in.get("profile_title"), "profile_description", in.get("profile_description"),
                "location", in.get("location"));
        boolean emailChanged = email != null && !email.equals(before.get("email"));
        if (emailChanged) {
            changes.put("email_verified_at", null); // a new address must be confirmed again
        }
        // the phone number is managed (and verified by SMS) on the Login & security page; a typed number
        // is only stored when the account has no verified one yet
        if (before.get("phone_verified_at") == null && in.has("phone")) {
            String phone = com.artistikcity.auth.PhoneNumbers.normalize(in.get("phone"),
                    env.getProperty("app.auth.default-country-code", "91"));
            if (in.get("phone") != null && phone == null) {
                throw ValidationException.withMessage("phone", "Enter a valid mobile number.");
            }
            if (phone != null && db.count("select count(*) from users where phone_number = ? and id <> ?", phone, id) > 0) {
                throw ValidationException.withMessage("phone", "This phone number is used by another account.");
            }
            changes.put("phone_number", phone);
        }
        db.updateById("users", id, changes);
        if (emailChanged) {
            authMail.sendVerification(request, id, email, in.get("name"));
        }
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
        Row u = db.first("select password_hash, email, name from users where id = ?", auth.userId(request));
        if (u.get("password_hash") == null || !auth.check(in.get("password"), u.str("password_hash"))) {
            throw ValidationException.withMessage("password", "The current password is incorrect.");
        }
        String problem = com.artistikcity.auth.PasswordPolicy.problem(in.get("new_password"), u.str("email"), u.str("name"));
        if (problem != null) {
            throw ValidationException.withMessage("new_password", problem);
        }
        db.updateById("users", auth.userId(request), Row.of("password_hash", auth.hash(in.get("new_password"))));
        var ctx = auth.context(request);
        sessions.revokeAll(auth.userId(request), ctx == null ? null : ctx.sessionId(), "password_changed");
        return redirect.route(request, "user.account");
    }

    @GetMapping("/user/notifications")
    public ResponseEntity<String> notifications(HttpServletRequest request) {
        return inertia.renderUser(request, "Students/Notifications", Map.of());
    }

    /** Login & security: sign-in methods, connected accounts, passkeys, devices. */
    @GetMapping("/user/linked-accounts")
    public ResponseEntity<String> linkedAccounts(HttpServletRequest request) {
        long id = auth.userId(request);
        Row u = db.find("users", id);
        var ctx = auth.context(request);
        Map<String, Object> security = new java.util.LinkedHashMap<>();
        security.put("email", u.get("email"));
        security.put("emailVerified", u.get("email_verified_at") != null);
        security.put("phone", u.get("phone_number"));
        security.put("phoneMasked", com.artistikcity.auth.PhoneNumbers.mask(u.str("phone_number")));
        security.put("phoneVerified", u.get("phone_verified_at") != null);
        security.put("hasPassword", u.get("password_hash") != null);
        security.put("linked", accounts.linkedAccounts(id));
        security.put("providers", com.artistikcity.web.SocialController.enabledProviders(env));
        security.put("passkeys", passkeys.list(id));
        List<Row> active = sessions.active(id);
        for (Row s : active) {
            s.put("current", ctx != null && s.lng("id") == ctx.sessionId());
            s.put("device", com.artistikcity.auth.PasskeyView.describeAgent(s.str("user_agent")));
            s.remove("user_agent");
        }
        security.put("sessions", active);
        security.put("events", db.select("select event, method, success, ip_address, created_at from auth_events where user_id = ?"
                + " order by id desc limit 12", id));
        security.put("countryCode", env.getProperty("app.auth.default-country-code", "91"));
        Map<String, Object> props = new java.util.LinkedHashMap<>();
        props.put("security", security);
        props.put("success", com.artistikcity.http.Flash.get(request, "success"));
        props.put("error", com.artistikcity.http.Flash.get(request, "error"));
        return inertia.renderUser(request, "Students/LinkedAccounts", props);
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
            Row cert = db.first("select certificate_name from certificates where order_id = ? order by id desc limit 1", orderId);
            if (cert != null) {
                image = Inertia.baseUrl(request) + "/storage/certificates/" + cert.str("certificate_name").replace(".pdf", ".jpeg");
            }
        }
        String q = request.getQueryString();
        return views.page(request, "certificates/share-certificate",
                Row.of("pageUrl", Inertia.baseUrl(request) + request.getRequestURI() + (q == null ? "" : "?" + q), "imageUrl", image));
    }
}
