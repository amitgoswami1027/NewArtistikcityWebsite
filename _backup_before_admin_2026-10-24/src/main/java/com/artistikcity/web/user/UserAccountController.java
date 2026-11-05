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

    /** The old Order History address now opens Your orders. */
    @GetMapping("/user/order-history")
    public ResponseEntity<String> orderHistory(HttpServletRequest request) {
        return redirect.to(request, "/user/orders");
    }

    /** My account > Your orders: courses / workshops, original artworks and commissions in one list (newest first). */
    @GetMapping("/user/orders")
    public ResponseEntity<String> orders(HttpServletRequest request) {
        long uid = auth.userId(request);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Row o : db.select("select o.id, o.order_number, o.created_at, o.payment_option, o.billing_name, o.billing_email, o.billing_address,"
                + " o.billing_city, o.billing_state, o.billing_zipcode, o.billing_country, o.price_type, o.price, o.payment_status,"
                + " c.id as course_id, c.title, c.sub_title, c.slug, c.course_type_id, " + CourseService.photoSubquery("o.course_id")
                + " from orders o join courses c on c.id = o.course_id where o.user_id = ?", uid)) {
            int ps = (int) o.lng("payment_status", 0);
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("key", "c" + o.get("id"));
            m.put("kind", o.lng("course_type_id", 1) == CourseService.TYPE_WORKSHOP ? "workshop" : "course");
            m.put("number", o.get("order_number"));
            m.put("title", o.get("title"));
            m.put("subtitle", o.get("sub_title"));
            m.put("image", o.get("photo") == null ? null : "/storage/uploads/courses/" + o.get("course_id") + "/" + o.get("photo"));
            m.put("amount", o.get("price"));
            m.put("currency", o.str("price_type") == null ? "INR" : o.str("price_type").toUpperCase(java.util.Locale.ROOT));
            m.put("date", o.get("created_at"));
            m.put("status", ps == 1 ? "Paid" : ps == 2 ? "Payment failed" : "Payment pending");
            m.put("payment", o.get("payment_option"));
            m.put("billing", java.util.stream.Stream.of(o.str("billing_name"), o.str("billing_address"), o.str("billing_city"), o.str("billing_state"),
                    o.str("billing_zipcode"), o.str("billing_country")).filter(x -> x != null && !x.isBlank()).collect(java.util.stream.Collectors.joining(", ")));
            m.put("link", ps == 1 ? "/dashboard/classroom/" + o.get("course_id") : "/course/" + o.get("slug"));
            m.put("linkLabel", ps == 1 ? "Go to course" : "View course");
            out.add(m);
        }
        for (Row o : db.select("select o.order_id, o.created_at, o.status, o.currency, o.amount, o.courier, o.tracking_number, o.gateway,"
                + " o.buyer_name, o.address_line1, o.address_line2, o.city, o.state, o.postal_code, o.country,"
                + " p.title, p.slug, p.artist_name, " + com.artistikcity.marketplace.MarketplaceRepository.PRIMARY_IMAGE_SQL + " as image_url"
                + " from marketplace_orders o join paintings p on p.id = o.painting_id"
                + " where o.user_id = ? and o.status not in ('PAYMENT_PENDING', 'EXPIRED')", uid)) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("key", "m" + o.get("order_id"));
            m.put("kind", "original");
            m.put("number", o.get("order_id"));
            m.put("title", o.get("title"));
            m.put("subtitle", o.get("artist_name") == null ? null : "by " + o.get("artist_name"));
            m.put("image", o.get("image_url"));
            m.put("amount", o.get("amount"));
            m.put("currency", o.get("currency"));
            m.put("date", o.get("created_at"));
            m.put("status", nice(o.str("status")));
            m.put("payment", nice(o.str("gateway")));
            m.put("billing", java.util.stream.Stream.of(o.str("buyer_name"), o.str("address_line1"), o.str("address_line2"), o.str("city"), o.str("state"),
                    o.str("postal_code"), o.str("country")).filter(x -> x != null && !x.isBlank()).collect(java.util.stream.Collectors.joining(", ")));
            m.put("tracking", o.get("tracking_number") == null ? null : (o.str("courier") == null ? "" : o.str("courier") + " ") + o.get("tracking_number"));
            m.put("link", "/marketplace/orders/" + o.get("order_id"));
            m.put("linkLabel", "View order");
            out.add(m);
        }
        for (Row o : db.select("select order_id, created_at, order_status, currency, total_price, fulfillment_type, payment_method"
                + " from commission_orders where user_id = ? and order_status <> 'PAYMENT_PENDING'", uid)) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("key", "k" + o.get("order_id"));
            m.put("kind", "commission");
            m.put("number", o.get("order_id"));
            m.put("title", "Commissioned artwork");
            m.put("subtitle", nice(o.str("fulfillment_type")));
            m.put("image", null);
            m.put("amount", o.get("total_price"));
            m.put("currency", o.get("currency"));
            m.put("date", o.get("created_at"));
            m.put("status", nice(o.str("order_status")));
            m.put("payment", nice(o.str("payment_method")));
            m.put("link", null);
            out.add(m);
        }
        out.sort((a, b) -> String.valueOf(b.get("date")).compareTo(String.valueOf(a.get("date"))));
        return inertia.renderUser(request, "Account/Orders", Map.of("orders", out));
    }

    /** "PROOF_SENT" -> "Proof sent". */
    private static String nice(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String t = v.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
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
