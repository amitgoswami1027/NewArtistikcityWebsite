package com.artistikcity.landing;

import com.artistikcity.admin.AdminKit;
import com.artistikcity.admin.Audit;
import com.artistikcity.auth.LoginSettings;
import com.artistikcity.commission.CommissionPayments;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin › Landing Page Management › Login & verification (/admin/landing/login, capability landing.payments):
 * how buyers are verified when they pay (mobile code, e-mail code, either, or sign in first), simulated SMS / e-mail
 * codes for testing, who may pay with simulated Razorpay, the sign-in methods of the checkout, and every page's own
 * choice (set in the page editor's Checkout step). GET|PUT /admin/api/landing/login; every change is audited.
 */
@RestController
public class LandingLoginAdmin {

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final Audit audit;
    private final LoginSettings login;
    private final LandingPayments payments;
    private final CommissionPayments gateways;
    private final LandingCommerce commerce;
    private final LandingTestPayments testPayments;
    private final LandingBuyerVerify verify;

    @org.springframework.beans.factory.annotation.Autowired
    private LandingHooks hooks;

    @org.springframework.beans.factory.annotation.Autowired
    private com.artistikcity.service.Mailer mailer;

    @org.springframework.beans.factory.annotation.Autowired
    private com.artistikcity.view.Views views;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment env;

    public LandingLoginAdmin(AdminKit kit, Db db, Json json, Audit audit, LoginSettings login, LandingPayments payments, CommissionPayments gateways,
                             LandingCommerce commerce, LandingTestPayments testPayments, LandingBuyerVerify verify) {
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.audit = audit;
        this.login = login;
        this.payments = payments;
        this.gateways = gateways;
        this.commerce = commerce;
        this.testPayments = testPayments;
        this.verify = verify;
    }

    @GetMapping("/admin/api/landing/login")
    public ResponseEntity<String> get(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        return kit.ok(view(request));
    }

    @PutMapping("/admin/api/landing/login")
    public ResponseEntity<String> save(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        Input in = Input.of(request, json);
        LoginSettings.Values before = login.values();
        String verifyMode = in.get("checkoutVerify") == null ? before.checkoutVerify() : String.valueOf(in.get("checkoutVerify")).toUpperCase();
        if (!LoginSettings.VERIFY.contains(verifyMode)) return kit.invalid(Map.of("checkoutVerify", "Choose how buyers are verified."));
        LoginSettings.Values after = new LoginSettings.Values(verifyMode,
                in.get("simulatePhone") == null ? before.simulatePhone() : in.bool("simulatePhone"),
                in.get("simulateEmail") == null ? before.simulateEmail() : in.bool("simulateEmail"));
        List<String> changed = new ArrayList<>();
        if (!after.checkoutVerify().equals(before.checkoutVerify())) changed.add("verification " + before.checkoutVerify() + " -> " + after.checkoutVerify());
        if (after.simulatePhone() != before.simulatePhone()) changed.add("simulated SMS codes " + (after.simulatePhone() ? "on" : "off"));
        if (after.simulateEmail() != before.simulateEmail()) changed.add("simulated e-mail codes " + (after.simulateEmail() ? "on" : "off"));
        if (!changed.isEmpty()) {
            login.save(after);
            audit.change(admin, "landing_settings", 0, "update", "Login & verification: " + String.join(", ", changed), login.asMap(before), login.asMap(after));
        }
        Map<String, Object> body = view(request);
        body.put("message", changed.isEmpty() ? "Nothing changed." : "Saved. Every landing page's checkout uses this now.");
        return kit.ok(body);
    }

    // ------------------------------------------------------------------ the buyer's e-mail: preview and test send

    /** The latest paid landing order (the e-mail is previewed / test-sent with its real content), or null. */
    private Row sampleOrder(Long orderId) {
        return orderId != null
                ? db.first("select id, user_id from orders where id = ? and landing_page_id is not null", orderId)
                : db.first("select id, user_id from orders where landing_page_id is not null and payment_status = 1 order by id desc limit 1");
    }

    private String base(HttpServletRequest request) {
        String b = env.getProperty("app.url", "").trim();
        return (b.isEmpty() ? com.artistikcity.inertia.Inertia.baseUrl(request) : b).replaceAll("/+$", "");
    }

    /** GET /admin/landing/email-preview[?order=]: the thank-you e-mail and receipt as the buyer gets it (no live sign-in link). */
    @GetMapping("/admin/landing/email-preview")
    public ResponseEntity<String> preview(HttpServletRequest request, @org.springframework.web.bind.annotation.RequestParam(value = "order", required = false) Long orderId) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        Row o = sampleOrder(orderId);
        if (o == null) {
            return ResponseEntity.ok().contentType(org.springframework.http.MediaType.TEXT_HTML)
                    .body("<!doctype html><meta charset=utf-8><p style=\"font:16px sans-serif;padding:24px\">No paid landing page order yet: make a test purchase on one of the test pages first.</p>");
        }
        Row u = db.first("select email from users where id = ?", o.get("user_id"));
        Map<String, Object> model = hooks.thankYouModel(o.lng("id"), u == null ? "" : u.str("email", ""), base(request), false);
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.TEXT_HTML).header("Content-Security-Policy", "script-src 'none'")
                .header("X-Robots-Tag", "noindex").body(views.render("emails/landing-thank-you", model));
    }

    /**
     * {email, order?}: sends the thank-you e-mail and receipt of the latest paid landing order (or that order) to this
     * address, marked as a test in the subject. Its "Go to my course" button is the log-in link (a one-click sign-in
     * link only ever goes to the buyer's own address). Without a mail server the e-mail is only saved on the server.
     */
    @org.springframework.web.bind.annotation.PostMapping("/admin/api/landing/login/test-email")
    public ResponseEntity<String> testEmail(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        Input in = Input.of(request, json);
        String to = com.artistikcity.auth.AccountService.normalizeEmail(in.get("email"));
        if (to == null || to.length() > 255 || !to.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) return kit.invalid(Map.of("email", "Enter a valid e-mail address."));
        Row o = sampleOrder(in.lng("order"));
        if (o == null) return kit.invalid(Map.of("email", "There is no paid landing page order yet: make a test purchase on one of the test pages first."));
        Map<String, Object> model = hooks.thankYouModel(o.lng("id"), to, base(request), false);
        boolean ok = mailer.send(to, "[Test] " + LandingHooks.thankYouSubject(model), "emails/landing-thank-you", model);
        audit.log(admin, "landing_settings", 0, "update", "Test thank-you e-mail for order " + model.get("orderNumber") + " sent to " + to + (mailer.enabled() ? "" : " (no mail server: saved only)"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sent", ok && mailer.enabled());
        body.put("mailServer", mailer.enabled());
        body.put("order", model.get("orderNumber"));
        body.put("message", !ok ? "The mail server refused the e-mail. Check MAIL_HOST, MAIL_USERNAME and MAIL_PASSWORD in the server settings."
                : mailer.enabled() ? "Sent to " + to + " (order " + model.get("orderNumber") + "). It can take a minute; check the spam folder too."
                : "Not sent: this server has no mail server yet (MAIL_HOST), so the e-mail was only saved on the server. Use Preview to see it.");
        return kit.ok(body);
    }

    Map<String, Object> view(HttpServletRequest request) {
        LoginSettings.Values v = login.values();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("settings", login.asMap(v));
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("sms", login.smsReal());
        server.put("mail", login.mailServer());
        server.put("phoneReady", login.phoneReady(request));
        server.put("emailReady", login.emailReady(request));
        server.put("razorpayKeys", gateways.razorpayConfigured());
        server.put("simulateRazorpay", payments.simulateRazorpaySetting());
        server.put("simulateForEveryone", payments.simulateForEveryone());
        server.put("testMode", gateways.testMode());
        server.put("verifyColumn", commerce.verifyColumnReady());
        server.put("mailFrom", env.getProperty("app.mail.from", ""));
        m.put("server", server);
        m.put("signin", testPayments.signInView(request));
        m.put("simulatedAccounts", login.simulatedAccounts());
        List<Map<String, Object>> pages = new ArrayList<>();
        for (Row p : db.select("select id, name, slug, status from landing_pages where course_id is not null and status <> 'ARCHIVED' order by name")) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", p.get("id"));
            x.put("name", p.str("name"));
            x.put("slug", p.str("slug"));
            x.put("status", p.str("status"));
            x.put("verifyWith", commerce.checkoutView(p.lng("id")).get("verifyWith"));
            x.put("mode", verify.mode(p.lng("id")));
            x.put("channels", verify.channels(request, p.lng("id")));
            pages.add(x);
        }
        m.put("pages", pages);
        List<String> warnings = new ArrayList<>();
        if (!"SIGNIN".equals(v.checkoutVerify()) && !hasReadyChannel(request, v.checkoutVerify())) {
            warnings.add("The default needs " + ("EMAIL".equals(v.checkoutVerify()) ? "e-mail codes" : "PHONE".equals(v.checkoutVerify()) ? "SMS codes" : "SMS or e-mail codes")
                    + ", and this server can't send them yet. Until you set them up (or switch on a simulation below), buyers sign in before paying instead.");
        }
        if (v.simulatePhone() || v.simulateEmail()) {
            warnings.add("Simulated codes are on: the code is shown on the screen instead of being sent. Real accounts still need a real code; switch simulation off before you launch.");
        }
        if (payments.razorpaySimulated() && payments.simulateForEveryone()) {
            warnings.add("Every visitor can pay with simulated Razorpay: anybody can enrol without paying (as a test order). Switch it back to test buyers only when you finish testing.");
        }
        m.put("warnings", warnings);
        return m;
    }

    private boolean hasReadyChannel(HttpServletRequest request, String mode) {
        boolean phone = login.phoneReady(request);
        boolean email = login.emailReady(request);
        return switch (mode) {
            case "PHONE" -> phone;
            case "EMAIL" -> email;
            default -> phone || email;
        };
    }
}
