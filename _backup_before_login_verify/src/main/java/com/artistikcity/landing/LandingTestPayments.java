package com.artistikcity.landing;

import com.artistikcity.admin.AdminKit;
import com.artistikcity.admin.Audit;
import com.artistikcity.auth.AccountService;
import com.artistikcity.commission.CommissionPayments;
import com.artistikcity.payments.TestPayments;
import com.artistikcity.seats.SeatService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.web.CheckoutController;
import com.artistikcity.web.QuickSignInController;
import org.springframework.web.bind.annotation.PutMapping;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin › Landing Page Management › Payments & checkout › Test payments (capability landing.payments).
 * <ul>
 *   <li>Test buyers: accounts allowed to simulate payments (users.test_buyer). Add an existing account by e-mail, or
 *       create a passwordless test account (it signs in on a landing page with an e-mail code).</li>
 *   <li>Simulator: runs a landing order end to end on the server for a test buyer - price (with an optional coupon),
 *       the page's payment option, order, seat, then the chosen outcome (paid / declined / waiting) - through the same
 *       code as a real payment, and reports every step. Works for drafts too, so a page can be tested before it goes live.</li>
 *   <li>Recent test orders, and "Undo" (a paid test order is marked refunded and the course access and seat are
 *       released, like a confirmed refund; nothing is deleted).</li>
 * </ul>
 * Every simulated order has is_test = 1: it stays out of revenue, analytics, the ledger, payouts and the payment
 * reconciler, and it never uses up a coupon. Real customers never see any of this.
 */
@RestController
public class LandingTestPayments {

    private final AdminKit kit;
    private final Db db;
    private final Json json;
    private final Audit audit;
    private final TestPayments testPayments;
    private final LandingCommerce commerce;
    private final LandingPayments payments;
    private final SeatService seats;
    private final CheckoutController courseCheckout;
    private final CommissionPayments gateways;
    private final AccountService accounts;
    private final QuickSignInController signIn;

    public LandingTestPayments(AdminKit kit, Db db, Json json, Audit audit, TestPayments testPayments, LandingCommerce commerce, LandingPayments payments,
                               SeatService seats, CheckoutController courseCheckout, CommissionPayments gateways, AccountService accounts,
                               QuickSignInController signIn) {
        this.signIn = signIn;
        this.kit = kit;
        this.db = db;
        this.json = json;
        this.audit = audit;
        this.testPayments = testPayments;
        this.commerce = commerce;
        this.payments = payments;
        this.seats = seats;
        this.courseCheckout = courseCheckout;
        this.gateways = gateways;
        this.accounts = accounts;
    }

    @GetMapping("/admin/api/landing/test-payments")
    public ResponseEntity<String> overview(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        return kit.ok(view());
    }

    private Map<String, Object> view() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", testPayments.testBuyersEnabled());
        m.put("devMode", testPayments.devMode());
        m.put("simulateRazorpay", payments.simulateRazorpaySetting());
        m.put("razorpaySimulated", payments.razorpaySimulated());
        m.put("razorpayKeys", gateways.razorpayConfigured());
        m.put("gateways", Map.of(
                "razorpay", gateways.razorpayConfigured() ? (gateways.razorpayKeyMode() == null ? "unknown" : gateways.razorpayKeyMode())
                        : payments.razorpaySimulated() ? "simulated" : "none",
                "paypal", gateways.paypalConfigured() ? gateways.paypalMode() : "none"));
        m.put("buyers", db.select("select u.id, u.name, u.email, u.phone_number as phone,"
                + " (select count(*) from orders o where o.user_id = u.id and o.is_test = 1 and o.landing_page_id is not null) as test_orders"
                + " from users u where u.test_buyer = 1 order by u.name"));
        List<Map<String, Object>> pages = new ArrayList<>();
        for (Row p : db.select("select p.id, p.name, p.slug, p.status, p.course_id, c.title as course from landing_pages p join courses c on c.id = p.course_id"
                + " where p.status <> 'ARCHIVED' and p.course_id is not null order by p.name")) {
            Map<String, Object> x = new LinkedHashMap<>(p);
            LandingPricing.Quote q = commerce.quote(p);
            x.put("total", LandingView.inr(q.total()));
            x.put("free", q.free());
            x.put("methods", payments.choices(payments.forPage(p.lng("id")), q.totalUsd() != null).stream()
                    .map(c -> Map.of("key", c.key(), "label", c.label())).toList());
            pages.add(x);
        }
        m.put("pages", pages);
        m.put("recent", db.select("select o.id, o.order_number as number, o.payment_status as status, o.price, o.price_type as currency, o.payment_method as method,"
                + " o.coupon_code as coupon, o.created_at, u.name as buyer, u.email, p.name as page, p.slug"
                + " from orders o join users u on u.id = o.user_id join landing_pages p on p.id = o.landing_page_id"
                + " where o.is_test = 1 order by o.id desc limit 15"));
        return m;
    }

    // ------------------------------------------------------------------ sign-in at checkout

    /** Payments & checkout › Sign-in at checkout: each method's switch and whether this server can run it. */
    @GetMapping("/admin/api/landing/signin")
    public ResponseEntity<String> signInSettings(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        return kit.ok(signInView(request));
    }

    @PutMapping("/admin/api/landing/signin")
    public ResponseEntity<String> saveSignIn(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        Input in = Input.of(request, json);
        Map<String, Boolean> before = signIn.switches();
        Map<String, Boolean> on = new LinkedHashMap<>();
        for (String k : QuickSignInController.METHODS) on.put(k, in.get(k) == null ? before.get(k) : in.bool(k));
        if (on.values().stream().noneMatch(Boolean::booleanValue)) return kit.invalid(Map.of("password", "Keep at least one way to sign in."));
        signIn.saveSwitches(on);
        audit.change(admin, "landing_settings", 0, "update", "Sign-in at checkout", before, on);
        Map<String, Object> body = signInView(request);
        body.put("message", "Saved. The checkout on every landing page uses these now.");
        return kit.ok(body);
    }

    private Map<String, Object> signInView(HttpServletRequest request) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("switches", signIn.switches());
        m.put("readiness", signIn.readiness(request));
        m.put("offered", signIn.options(request));
        m.put("offered", new LinkedHashMap<>((Map<String, Object>) m.get("offered")));
        ((Map<String, Object>) m.get("offered")).computeIfPresent("google", (k, v) -> true);
        return m;
    }

    /**
     * {on}: "Simulate Razorpay". With no Razorpay keys on the server, landing pages can then offer Razorpay's methods and
     * test buyers pay in a simulated Razorpay window (no account, no keys, no money); everybody else is told payment opens
     * soon. Ignored as soon as real Razorpay keys are set.
     */
    @PostMapping("/admin/api/landing/test-payments/simulate-razorpay")
    public ResponseEntity<String> simulateRazorpay(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        boolean on = Input.of(request, json).bool("on");
        boolean before = payments.simulateRazorpaySetting();
        payments.saveSimulateRazorpay(on);
        audit.change(admin, "landing_settings", 0, "update", on ? "Simulated Razorpay switched on" : "Simulated Razorpay switched off",
                Map.of("simulate_razorpay", before), Map.of("simulate_razorpay", on));
        Map<String, Object> body = view();
        body.put("message", on ? (gateways.razorpayConfigured() ? "Saved. Razorpay keys are set on this server, so real Razorpay is used until they are removed."
                : "Razorpay is simulated now: test buyers can pay on your landing pages with no Razorpay account. Nobody else can pay until you connect Razorpay.")
                : "Simulated Razorpay is off.");
        return kit.ok(body);
    }

    /** {email, name?, on}: marks an account as a test buyer (or creates a passwordless test account when none has that e-mail). */
    @PostMapping("/admin/api/landing/test-payments/buyers")
    public ResponseEntity<String> buyer(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        Input in = Input.of(request, json);
        boolean on = in.get("on") == null || in.bool("on");
        Row user = null;
        if (in.lng("id") != null) user = db.first("select id, name, email from users where id = ?", in.lng("id"));
        String email = AccountService.normalizeEmail(in.get("email"));
        if (user == null && email != null) user = accounts.findByEmail(email);
        if (user == null) {
            if (!on) return kit.fail(HttpStatus.NOT_FOUND, "Account not found.");
            if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) return kit.invalid(Map.of("email", "Enter the e-mail address of the test account."));
            String name = AdminKit.trim(in.get("name"), 120);
            long id = accounts.create(name == null || name.isBlank() ? "Test buyer" : name, email, true, null, false, null, null);
            user = db.first("select id, name, email from users where id = ?", id);
            audit.log(admin, "user", id, "create", "Test buyer account created for landing page testing: " + email);
        }
        long id = user.lng("id");
        Row before = audit.snapshot("users", id);
        db.update("update users set test_buyer = ?, updated_at = ? where id = ?", on ? 1 : 0, LocalDateTime.now(), id);
        audit.change(admin, "user", id, on ? "test buyer on" : "test buyer off",
                user.str("name", "") + " <" + user.str("email", "") + ">" + (on ? " may now simulate payments" : " pays normally again"), before, audit.snapshot("users", id));
        Map<String, Object> body = view();
        body.put("message", on ? user.str("name") + " is a test buyer now." : user.str("name") + " is no longer a test buyer.");
        return kit.ok(body);
    }

    /**
     * {pageId, buyerId, method, outcome: success|failed|pending, coupon?}: one simulated order, end to end. Answers the
     * steps ({label, ok, detail}) and the order.
     */
    @PostMapping("/admin/api/landing/test-payments/run")
    public ResponseEntity<String> run(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        Input in = Input.of(request, json);
        Map<String, String> errors = new LinkedHashMap<>();
        Row page = in.lng("pageId") == null ? null
                : db.first("select * from landing_pages where id = ? and course_id is not null and status <> 'ARCHIVED'", in.lng("pageId"));
        if (page == null) errors.put("pageId", "Choose a course landing page.");
        Row buyer = in.lng("buyerId") == null ? null : db.first("select id, name, email, phone_number, test_buyer from users where id = ? and status = 1", in.lng("buyerId"));
        if (buyer == null) errors.put("buyerId", "Choose a test buyer.");
        else if (buyer.lng("test_buyer", 0) != 1) errors.put("buyerId", "Only test buyers can be used for simulated payments.");
        String outcome = String.valueOf(in.get("outcome"));
        if (!List.of("success", "failed", "pending").contains(outcome)) errors.put("outcome", "Choose what the payment should do.");
        if (!errors.isEmpty()) return kit.invalid(errors);

        List<Map<String, Object>> steps = new ArrayList<>();
        long pageId = page.lng("id");
        long courseId = page.lng("course_id");
        long uid = buyer.lng("id");
        Row paid = db.first("select id, order_number from orders where user_id = ? and course_id = ? and payment_status = 1 order by id desc limit 1", uid, courseId);
        if (paid != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(json.encode(Map.of(
                    "message", buyer.str("name") + " is already enrolled in this course (order " + paid.str("order_number") + "). Undo that test order first, or pick another test buyer.",
                    "orderId", paid.get("id"))));
        }
        step(steps, "Test buyer", true, buyer.str("name") + " <" + buyer.str("email", "no e-mail") + ">");
        step(steps, "Page", true, page.str("name") + " (" + page.str("status").toLowerCase() + ") · /landing/" + page.str("slug"));

        // 1. the price, from the page's rules (the same calculation the checkout and the gateway use)
        String coupon = LandingCoupons.normalizeCode(in.get("coupon"));
        LandingCommerce.Priced priced = commerce.quote(page, coupon, uid);
        LandingPricing.Quote q = priced.quote();
        if (!coupon.isEmpty() && priced.couponError() != null) {
            step(steps, "Coupon " + coupon, false, priced.couponError());
            return done(steps, null, "The coupon was refused, so no order was created.");
        }
        step(steps, "Price", true, "Total " + LandingView.inr(q.total()) + (q.discount() != null && q.discount().signum() > 0 ? " after " + LandingView.inr(q.discount()) + " off" : "")
                + (q.couponCode() != null ? " (coupon " + q.couponCode() + "; test orders don't use up a coupon)" : ""));

        // 2. the payment option must be one this page offers
        String method = String.valueOf(in.get("method"));
        LandingPayments.Choice choice = payments.choices(payments.forPage(pageId), q.totalUsd() != null).stream().filter(c -> c.key().equals(method)).findFirst().orElse(null);
        if (!q.free() && choice == null) {
            step(steps, "Payment option", false, "This page doesn't offer \"" + method + "\" right now. Check its payment settings.");
            return done(steps, null, "No order was created.");
        }
        boolean usd = choice != null && "USD".equals(choice.currency());
        step(steps, "Payment option", true, q.free() ? "Free course: no payment" : choice.label() + " via " + choice.provider() + " (simulated: nothing is charged)");

        // 3. the order, exactly as the checkout writes it, marked as a test
        LocalDateTime now = LocalDateTime.now();
        BigDecimal amount = usd ? q.totalUsd() : q.total();
        Row values = Row.of("user_id", uid, "course_id", courseId, "landing_page_id", pageId, "billing_name", buyer.str("name"), "billing_email", buyer.get("email"),
                "billing_phone", buyer.get("phone_number"), "billing_country", "India", "coupon_code", q.couponCode(), "list_amount", q.list(), "discount_amount", q.discount(),
                "tax_amount", q.tax(), "price_type", usd ? "usd" : "inr", "price", amount.toPlainString(), "payment_option", "Simulated (test)", "payment_provider", "test",
                "payment_method", q.free() ? "free" : choice.key(), "utm_source", "admin-test", "is_test", 1, "payment_status", 0, "session_id", "admin-test-" + request.getSession(true).getId(),
                "created_at", now, "updated_at", now);
        long orderId = db.insert("orders", values);
        db.updateById("orders", orderId, Row.of("order_number", "ORD" + String.format("%05d", orderId)));
        Row order = db.find("orders", orderId);
        step(steps, "Order created", true, order.str("order_number") + " · " + (usd ? LandingView.usd(amount) : LandingView.inr(amount)) + " · marked as a test order");

        // 4. a seat, when the course has a limit
        try {
            seats.hold(courseId, uid, buyer.str("email"), orderId, null);
            SeatService.Seats s = seats.seats(courseId);
            step(steps, "Seat", true, s.limit() == null ? "No seat limit on this course" : "Held for 15 minutes (" + s.taken() + " of " + s.limit() + " taken)");
        } catch (SeatService.SoldOutException e) {
            db.updateById("orders", orderId, Row.of("payment_status", 2, "updated_at", LocalDateTime.now()));
            step(steps, "Seat", false, "Sold out: a real buyer would see \"the last seat was just taken\". The order was closed as failed.");
            return done(steps, orderId, "Sold out.");
        }

        // 5. the outcome
        String summary;
        switch (outcome) {
            case "success" -> {
                String url = courseCheckout.completeFromGateway(orderId, "Simulated (test)", "test_" + orderId + "_" + System.currentTimeMillis(),
                        Map.of("test", true, "simulatedBy", admin.get("id"), "method", q.free() ? "free" : choice.key()));
                Row after = db.find("orders", orderId);
                long st = after.lng("payment_status", 0);
                step(steps, "Payment", st == 1, st == 1 ? "Confirmed (simulated) and recorded as paid" : st == 4 ? "Recorded, but the order needs a refund: " + url : "Not confirmed");
                if (st == 1) {
                    step(steps, "Course access", true, "Open at /user/course/" + orderId + " for " + buyer.str("name"));
                    step(steps, "Confirmation e-mail", true, "Sent to " + buyer.str("email", "(no e-mail on the account)"));
                    step(steps, "Landing follow-up", true, "Order credited to the page (left out of its revenue and analytics because it is a test)");
                }
                summary = st == 1 ? "Paid: the full success path ran." : "The payment did not complete.";
            }
            case "failed" -> {
                db.updateById("orders", orderId, Row.of("payment_status", 2, "updated_at", LocalDateTime.now()));
                seats.release(courseId, uid);
                step(steps, "Payment", true, "Declined (simulated): the order is failed, no access, the seat went back. The buyer could pay again.");
                summary = "Declined: the failure path ran.";
            }
            default -> {
                db.updateById("orders", orderId, Row.of("gateway_ref", "test_pending_" + orderId, "updated_at", LocalDateTime.now()));
                step(steps, "Payment", true, "Waiting for the bank (simulated): the checkout shows \"Confirming your payment…\". Test orders are never picked up by the payment reconciler; use Undo to close it.");
                summary = "Waiting: the pending path ran.";
            }
        }
        audit.log(admin, "order", orderId, "update", "Simulated landing payment (" + outcome + ") on page " + page.str("name") + " for test buyer " + buyer.str("email"));
        return done(steps, orderId, summary);
    }

    /** Closes a test order: paid -> refunded (access and seat released, like a confirmed refund); open / waiting -> failed. */
    @PostMapping("/admin/api/landing/test-payments/orders/{id}/undo")
    public ResponseEntity<String> undo(HttpServletRequest request, @PathVariable("id") long id) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, "landing.payments")) return kit.forbidden();
        Row o = db.first("select * from orders where id = ? and is_test = 1 and landing_page_id is not null", id);
        if (o == null) return kit.fail(HttpStatus.NOT_FOUND, "Only test orders from landing pages can be undone here.");
        LocalDateTime now = LocalDateTime.now();
        long st = o.lng("payment_status", 0);
        Row before = audit.snapshot("orders", id);
        if (st == 1 || st == 4) {
            db.update("update orders set payment_status = 3, updated_at = ? where id = ?", now, id);
            if (db.count("select count(*) from orders where user_id = ? and course_id = ? and payment_status = 1", o.get("user_id"), o.get("course_id")) == 0) {
                db.update("update enrollments set status = 'Refunded', updated_at = ? where user_id = ? and course_id = ?", now, o.get("user_id"), o.get("course_id"));
                seats.release(o.lng("course_id"), o.lng("user_id"));
            }
        } else if (st == 0 || st == 2) {
            db.update("update orders set payment_status = 2, gateway_ref = null, updated_at = ? where id = ?", now, id);
            seats.release(o.lng("course_id"), o.lng("user_id"));
        } else {
            return kit.fail(HttpStatus.CONFLICT, "This test order is already closed.");
        }
        audit.change(admin, "order", id, "update", "Test order " + o.str("order_number") + " undone", before, audit.snapshot("orders", id));
        Map<String, Object> body = view();
        body.put("message", "Test order " + o.str("order_number") + (st == 1 || st == 4 ? " marked refunded; the buyer's access was removed." : " closed."));
        return kit.ok(body);
    }

    private static void step(List<Map<String, Object>> steps, String label, boolean ok, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("ok", ok);
        m.put("detail", detail);
        steps.add(m);
    }

    private ResponseEntity<String> done(List<Map<String, Object>> steps, Long orderId, String summary) {
        Map<String, Object> body = view();
        body.put("steps", steps);
        body.put("summary", summary);
        body.put("ok", steps.stream().allMatch(s -> Boolean.TRUE.equals(s.get("ok"))));
        if (orderId != null) {
            Row o = db.first("select id, order_number as number, payment_status as status from orders where id = ?", orderId);
            body.put("order", o);
        }
        return kit.ok(body);
    }
}
