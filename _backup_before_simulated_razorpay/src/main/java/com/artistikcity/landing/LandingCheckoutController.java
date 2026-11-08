package com.artistikcity.landing;

import com.artistikcity.commission.CommissionPayments;
import com.artistikcity.http.Auth;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.payments.TestPayments;
import com.artistikcity.seats.SeatService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.web.CheckoutController;
import com.artistikcity.web.QuickSignInController;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checkout for a landing page. It runs inside the landing page itself (a dialog opened by static/js/landing.js:
 * quick sign-in, details, payment, result), so the visitor never leaves the page; /landing/{slug}/enroll and
 * /enroll/done only send the browser back to the page with the dialog open. GET /landing/checkout/{pageId}/state gives
 * the dialog everything it shows, for guests too. Prices, coupons and the payment options are worked out here from
 * the page's settings; the browser only says which option was picked. Payment verification reuses the course checkout
 * (/checkout/razorpay/verify, /checkout/test-pay, /checkout/paypal/return, the webhook and the reconciler), which check
 * the gateway's answer against the amount fixed on the order here. Every POST here is outside /api, so the global CSRF
 * check applies.
 */
@RestController
public class LandingCheckoutController {

    private static final Logger log = LoggerFactory.getLogger(LandingCheckoutController.class);

    private final Db db;
    private final Json json;
    private final Auth auth;
    private final Inertia inertia;
    private final LandingPages pages;
    private final LandingCommerce commerce;
    private final LandingPayments payments;
    private final LandingAnalytics analytics;
    private final LandingView view;
    private final CommissionPayments gateways;
    private final SeatService seats;
    private final TestPayments testPayments;
    private final CheckoutController courseCheckout;
    private final QuickSignInController quickSignIn;

    public LandingCheckoutController(Db db, Json json, Auth auth, Inertia inertia, LandingPages pages, LandingCommerce commerce, LandingPayments payments,
                                     LandingAnalytics analytics, LandingView view, CommissionPayments gateways, SeatService seats, TestPayments testPayments,
                                     CheckoutController courseCheckout, QuickSignInController quickSignIn) {
        this.quickSignIn = quickSignIn;
        this.db = db;
        this.json = json;
        this.auth = auth;
        this.inertia = inertia;
        this.pages = pages;
        this.commerce = commerce;
        this.payments = payments;
        this.analytics = analytics;
        this.view = view;
        this.gateways = gateways;
        this.seats = seats;
        this.testPayments = testPayments;
        this.courseCheckout = courseCheckout;
    }

    // ------------------------------------------------------------------------------------------------ the page

    @org.springframework.beans.factory.annotation.Autowired
    private LandingPublic publicView;

    /** The public landing page: its own address, generated from the page's slug (Admin › Landing Page Management). */
    @GetMapping({"/landing/{slug}", "/landing/{slug}/"})
    public ResponseEntity<String> landing(HttpServletRequest request, @PathVariable("slug") String slug) {
        Row page = pages.published(slug);
        if (page == null) {
            throw new com.artistikcity.http.NotFoundException();
        }
        return publicView.render(request, page);
    }

    /** The checkout opens on the landing page itself: the page with its checkout dialog open (utm_* and notices kept). */
    @GetMapping("/landing/{slug}/enroll")
    public ResponseEntity<String> page(HttpServletRequest request, @PathVariable("slug") String slug) {
        Row page = pages.published(slug);
        if (page == null) return redirect("/courses?type=all");
        if (page.get("course_id") == null) return redirect(LandingPages.publicPath(page));     // an information page sells nothing
        return redirect(LandingPages.publicPath(page) + query(request, null) + "#enroll");
    }

    /** Where payments come back to (PayPal, the course checkout's success/failure links): the page, showing the result. */
    @GetMapping("/landing/{slug}/enroll/done")
    public ResponseEntity<String> done(HttpServletRequest request, @PathVariable("slug") String slug, @RequestParam(value = "order_id", required = false) Long orderId) {
        Row page = pages.published(slug);
        if (page == null) return redirect("/courses?type=all");
        return redirect(LandingPages.publicPath(page) + query(request, orderId) + "#enroll");
    }

    /** Checkout links of the previous release (/course/{slug}/enroll): sent on to the landing page's checkout, or to the course. */
    @GetMapping({"/course/{slug}/enroll", "/course/{slug}/enroll/done"})
    public ResponseEntity<String> legacy(HttpServletRequest request, @PathVariable("slug") String slug) {
        String q = request.getQueryString() == null ? "" : "?" + request.getQueryString();
        if (pages.published(slug) != null) {
            return redirect(LandingPages.enrollPath(slug) + (request.getRequestURI().endsWith("/done") ? "/done" : "") + q);
        }
        return redirect("/course/" + slug);
    }

    /** utm_*, payment notices and the paid order (as ?order=) carried over to the page. */
    private static String query(HttpServletRequest request, Long orderId) {
        List<String> parts = new ArrayList<>();
        for (String k : List.of("utm_source", "utm_medium", "utm_campaign", "payment", "duplicate", "soldout")) {
            String v = request.getParameter(k);
            if (v != null && !v.isBlank() && v.length() < 120) parts.add(k + "=" + java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (orderId != null) parts.add("order=" + orderId);
        return parts.isEmpty() ? "" : "?" + String.join("&", parts);
    }

    /**
     * Everything the checkout dialog shows: the course, the price, the payment options, the quick sign-in methods, and
     * for a signed-in visitor their details, whether they are already enrolled, and (with ?order=) that order's result.
     */
    @GetMapping("/landing/checkout/{pageId}/state")
    public ResponseEntity<String> state(HttpServletRequest request, @PathVariable("pageId") long pageId, @RequestParam(value = "order", required = false) Long orderId) {
        Row page = livePage(pageId);
        if (page == null) return error(HttpStatus.GONE, "This page is no longer available.");
        Row user = auth.user(request);
        long courseId = page.lng("course_id");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("page", Map.of("id", page.get("id"), "slug", page.str("slug"), "name", page.str("name")));
        Map<String, Object> course = view.course(courseId);
        Row artist = db.first("select name from admins where id = ?", course.get("adminId"));
        Map<String, Object> c = new LinkedHashMap<>();
        for (String k : List.of("title", "kind", "starts", "duration")) c.put(k, course.get(k));
        c.put("artist", artist == null ? null : artist.str("name"));
        c.put("thumb", course.get("image") == null ? null : LandingImages.sized((String) course.get("image"), 320));
        props.put("course", c);
        LandingPricing.Quote q = commerce.quote(page);
        props.put("quote", view.price(q));
        LandingPayments.Plan plan = payments.forPage(pageId);
        List<Map<String, Object>> choices = new ArrayList<>();
        for (LandingPayments.Choice ch : payments.choices(plan, q.totalUsd() != null)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", ch.key());
            m.put("label", ch.label());
            m.put("hint", ch.hint());
            m.put("currency", ch.currency());
            m.put("logos", ch.logos());
            m.put("amount", "USD".equals(ch.currency()) ? LandingView.usd(q.totalUsd()) : LandingView.inr(q.total()));
            choices.add(m);
        }
        props.put("choices", choices);
        props.put("checkout", commerce.checkoutView(pageId));
        props.put("testMode", gateways.testMode() && !gateways.razorpayConfigured());
        props.put("signIn", user == null ? quickSignIn.options(request) : null);
        props.put("user", user == null ? null : QuickSignInController.summary(user));
        props.put("simulate", user != null && !q.free() && testPayments.offerSimulation(request));
        if (user != null) {
            long uid = user.lng("id");
            props.put("address", db.first("select address, city, state, zipcode, country from user_addresses where user_id = ? and status = 1 order by `primary` desc, id desc limit 1", uid));
            Row paid = db.first("select id from orders where user_id = ? and course_id = ? and payment_status = 1 order by id desc limit 1", uid, courseId);
            props.put("enrolled", paid == null ? null : Map.of("orderId", paid.get("id"), "classroom", "/user/course/" + paid.get("id")));
            if (orderId != null) {
                Row o = db.first("select id, order_number, payment_status, gateway_ref, price, price_type, payment_method from orders where id = ? and user_id = ? and landing_page_id = ?",
                        orderId, uid, pageId);
                props.put("result", o == null ? null : result(o, request));
            }
        }
        if (orderId == null && !LandingPublic.isBot(request) && auth.admin(request) == null) {
            String v = visitor(request);
            if (v != null) analytics.record(pageId, "CHECKOUT", v, null, null, null, LandingPublic.utm(request), LandingAnalytics.device(request.getHeader("User-Agent")));
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").contentType(MediaType.APPLICATION_JSON).body(json.encode(props));
    }

    private Map<String, Object> result(Row o, HttpServletRequest request) {
        int s = (int) o.lng("payment_status", 0);
        String state = s == 1 ? "success" : s == 2 ? "failed" : s == 4 ? "refund" : s == 3 ? "refunded" : o.get("gateway_ref") != null ? "pending" : "open";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.get("id"));
        m.put("number", o.get("order_number"));
        m.put("state", state);
        m.put("amount", "usd".equals(o.str("price_type")) ? LandingView.usd(new BigDecimal(o.str("price", "0"))) : LandingView.inr(new BigDecimal(o.str("price", "0"))));
        m.put("duplicate", "1".equals(request.getParameter("duplicate")));
        m.put("soldout", "1".equals(request.getParameter("soldout")));
        m.put("classroom", s == 1 ? "/user/course/" + o.get("id") : null);
        return m;
    }

    // ------------------------------------------------------------------------------------------------ JSON

    /** Coupon preview: {coupon} -> the quote with it, or why it doesn't apply. */
    @PostMapping("/landing/checkout/{pageId}/quote")
    public ResponseEntity<String> quote(HttpServletRequest request, @PathVariable("pageId") long pageId) {
        Row user = auth.user(request);
        Row page = livePage(pageId);
        if (user == null || page == null) {
            return error(user == null ? HttpStatus.UNAUTHORIZED : HttpStatus.GONE, user == null ? "Please sign in to continue." : "This page is no longer available.");
        }
        Input in = Input.of(request, json);
        LandingCommerce.Priced p = commerce.quote(page, in.get("coupon"), user.lng("id"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("quote", view.price(p.quote()));
        body.put("couponError", p.couponError());
        return ok(body);
    }

    /** Contact / billing details and consents -> an open order with the price fixed on it. */
    @PostMapping("/landing/checkout/{pageId}/order")
    public ResponseEntity<String> order(HttpServletRequest request, @PathVariable("pageId") long pageId) {
        Row user = auth.user(request);
        Row page = livePage(pageId);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        if (page == null) return error(HttpStatus.GONE, "This page is no longer available.");
        Input in = Input.of(request, json);
        Map<String, Object> cfg = commerce.checkoutView(pageId);
        Map<String, String> errors = new LinkedHashMap<>();
        String name = trim(in.get("name"), 190);
        if (name.isEmpty()) errors.put("name", "Enter your full name.");
        String phone = trim(in.get("phone"), 20).replaceAll("[\\s()-]", "");
        if ((boolean) cfg.get("requirePhone") && !phone.matches("^\\+?[0-9]{8,15}$")) errors.put("phone", "Enter a phone number we can reach you on.");
        if (!phone.isEmpty() && !phone.matches("^\\+?[0-9]{8,15}$")) errors.put("phone", "Use digits only, with the country code if outside India.");
        String address = trim(in.get("address"), 500);
        String city = trim(in.get("city"), 190);
        String state = trim(in.get("state"), 190);
        String zip = trim(in.get("zipcode"), 20);
        String country = trim(in.get("country"), 100);
        if (country.isEmpty()) country = "India";
        if ((boolean) cfg.get("requireAddress")) {
            if (address.isEmpty()) errors.put("address", "Enter your street address.");
            if (city.isEmpty()) errors.put("city", "Enter your city.");
            if (zip.isEmpty()) errors.put("zipcode", "Enter your PIN / postal code.");
        }
        if ((boolean) cfg.get("requireTerms") && !in.bool("terms")) errors.put("terms", "Please accept the terms to continue.");
        if ((boolean) cfg.get("requirePrivacy") && !in.bool("privacy")) errors.put("privacy", "Please accept the privacy policy to continue.");
        if (!errors.isEmpty()) {
            return json(HttpStatus.UNPROCESSABLE_ENTITY, Map.of("message", "Please check the highlighted fields.", "errors", errors));
        }
        long uid = user.lng("id");
        long courseId = page.lng("course_id");
        if (db.count("select count(*) from orders where user_id = ? and course_id = ? and payment_status = 1", uid, courseId) > 0) {
            return json(HttpStatus.CONFLICT, Map.of("message", "You're already enrolled in this course.", "enrolled", true));
        }
        // a passwordless account placing its first order (the quick sign-in names a new account after its e-mail address)
        // takes the name typed here, so receipts and the certificate carry the real name
        if (!name.equals(user.str("name")) && !Boolean.TRUE.equals(user.get("has_password"))
                && db.count("select count(*) from orders where user_id = ?", uid) == 0) {
            db.update("update users set name = ? where id = ?", name, uid);
        }
        String coupon = (boolean) cfg.get("showCoupon") ? LandingCoupons.normalizeCode(in.get("coupon")) : "";
        LandingCommerce.Priced priced = commerce.quote(page, coupon, uid);
        if (!coupon.isEmpty() && priced.couponError() != null) {
            return json(HttpStatus.UNPROCESSABLE_ENTITY, Map.of("message", priced.couponError(), "errors", Map.of("coupon", priced.couponError())));
        }
        LandingPricing.Quote q = priced.quote();
        LandingAnalytics.Utm utm = LandingPublic.utm(request);
        String visitor = visitor(request);
        Row values = Row.of("billing_name", name, "billing_email", user.get("email"), "billing_address", address.isEmpty() ? null : address,
                "billing_city", city.isEmpty() ? null : city, "billing_state", state.isEmpty() ? null : state, "billing_zipcode", zip.isEmpty() ? null : zip,
                "billing_country", country, "billing_phone", phone.isEmpty() ? null : phone, "landing_page_id", pageId, "coupon_code", q.couponCode(), "list_amount", q.list(), "discount_amount", q.discount(),
                "tax_amount", q.tax(), "price_type", "inr", "price", q.total().toPlainString(), "utm_source", utm.source(), "utm_medium", utm.medium(),
                "utm_campaign", utm.campaign(), "landing_visitor", visitor == null ? null : LandingAnalytics.visitorHash(visitor), "updated_at", LocalDateTime.now());
        Row open = db.first("select id from orders where user_id = ? and course_id = ? and landing_page_id = ? and payment_status in (0, 2) order by id desc limit 1", uid, courseId, pageId);
        long orderId;
        if (open == null) {
            values.put("user_id", uid);
            values.put("course_id", courseId);
            values.put("session_id", request.getSession(true).getId());
            values.put("created_at", LocalDateTime.now());
            orderId = db.insert("orders", values);
            db.updateById("orders", orderId, Row.of("order_number", "ORD" + String.format("%05d", orderId)));
        } else {
            orderId = open.lng("id");
            values.put("payment_status", 0);
            values.put("gateway_ref", null);
            db.updateById("orders", orderId, values);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("quote", view.price(q));
        return ok(body);
    }

    /** {orderId, method} -> what the page needs to open the payment. The method must be one this page offers right now. */
    @PostMapping("/landing/checkout/{pageId}/pay")
    public ResponseEntity<String> pay(HttpServletRequest request, @PathVariable("pageId") long pageId) {
        Row user = auth.user(request);
        Row page = livePage(pageId);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        if (page == null) return error(HttpStatus.GONE, "This page is no longer available.");
        Input in = Input.of(request, json);
        Row order = db.first("select * from orders where id = ? and user_id = ? and landing_page_id = ? and payment_status in (0, 2)", in.lng("orderId"), user.get("id"), pageId);
        if (order == null) return error(HttpStatus.GONE, "This order is no longer open. Please start again.");
        long courseId = page.lng("course_id");
        if (db.count("select count(*) from orders where user_id = ? and course_id = ? and payment_status = 1", user.get("id"), courseId) > 0) {
            return json(HttpStatus.CONFLICT, Map.of("message", "You're already enrolled in this course.", "enrolled", true));
        }
        // the price again, from the server's rules (an offer may have ended, a coupon may be used up)
        LandingCommerce.Priced priced = commerce.quote(page, order.str("coupon_code"), user.lng("id"));
        if (order.str("coupon_code") != null && priced.couponError() != null) {
            db.updateById("orders", order.lng("id"), Row.of("coupon_code", null, "discount_amount", BigDecimal.ZERO));
            return json(HttpStatus.CONFLICT, Map.of("message", priced.couponError() + " The price has been updated.", "repriced", true, "quote", view.price(priced.quote())));
        }
        LandingPricing.Quote q = priced.quote();
        if (q.total().compareTo(new BigDecimal(order.str("price", "0"))) != 0 && "inr".equals(order.str("price_type"))) {
            db.updateById("orders", order.lng("id"), Row.of("price", q.total().toPlainString(), "list_amount", q.list(), "discount_amount", q.discount(), "tax_amount", q.tax()));
            return json(HttpStatus.CONFLICT, Map.of("message", "The price changed since you opened this page. Please check the new total.", "repriced", true, "quote", view.price(q)));
        }
        String method = String.valueOf(in.get("method"));
        LandingPayments.Plan plan = payments.forPage(pageId);
        boolean simulated = "test".equals(method);
        LandingPayments.Choice choice = payments.choices(plan, q.totalUsd() != null).stream().filter(c -> c.key().equals(method)).findFirst().orElse(null);
        if (!q.free() && !simulated && choice == null) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "That payment method isn't available for this course. Please choose another.");
        }
        if (simulated && !testPayments.offerSimulation(request)) {
            return error(HttpStatus.FORBIDDEN, "Simulated payments are only for test accounts.");
        }
        try {
            seats.hold(courseId, user.lng("id"), user.str("email"), order.lng("id"), null);
        } catch (SeatService.SoldOutException e) {
            return json(HttpStatus.CONFLICT, Map.of("code", "SOLD_OUT", "message", "Sorry - the last seat was just taken. No money was taken."));
        }
        long orderId = order.lng("id");
        String visitor = visitor(request);
        try {
            if (q.free()) {
                db.updateById("orders", orderId, Row.of("price_type", "inr", "price", "0", "payment_option", "Free", "payment_provider", "free", "payment_method", "free"));
                String url = courseCheckout.completeFromGateway(orderId, "Free", "free_" + orderId, Map.of("free", true, "landing_page", pageId));
                return ok(Map.of("gateway", "done", "redirect", doneUrl(page, orderId, url)));
            }
            if (simulated) {
                db.updateById("orders", orderId, Row.of("price_type", "inr", "price", q.total().toPlainString(), "payment_option", "Simulated (test)", "is_test", 1,
                        "payment_provider", "test", "payment_method", "test"));
                return ok(Map.of("gateway", "test", "simulated", true, "amount", q.total(), "currency", "INR"));
            }
            analytics.record(pageId, "PAY_START", visitor, choice.key(), choice.provider(), orderId, LandingPublic.utm(request), LandingAnalytics.device(request.getHeader("User-Agent")));
            if ("paypal".equals(choice.provider())) {
                BigDecimal usd = q.totalUsd();
                db.updateById("orders", orderId, Row.of("price_type", "usd", "price", usd.toPlainString(), "payment_option", "PayPal", "payment_provider", "paypal",
                        "payment_method", "paypal"));
                if (!gateways.paypalConfigured()) {
                    return gateways.testMode() ? ok(Map.of("gateway", "test", "amount", usd, "currency", "USD"))
                            : error(HttpStatus.SERVICE_UNAVAILABLE, "PayPal isn't available right now. Please choose another way to pay.");
                }
                String base = Inertia.baseUrl(request);
                String[] created = gateways.paypalCreateOrder(order.str("order_number"), usd, String.valueOf(view.course(courseId).get("title")),
                        base + "/checkout/paypal/return", base + LandingPages.enrollPath(page.str("slug")) + "?payment=cancelled");
                if (created[1] == null) {
                    return error(HttpStatus.BAD_GATEWAY, "PayPal didn't respond. Please try again.");
                }
                db.updateById("orders", orderId, Row.of("gateway_ref", created[0]));
                return ok(Map.of("gateway", "paypal", "approveUrl", created[1]));
            }
            db.updateById("orders", orderId, Row.of("price_type", "inr", "price", q.total().toPlainString(), "payment_option", "RazorPay", "payment_provider", "razorpay",
                    "payment_method", choice.key()));
            if (!gateways.razorpayConfigured()) {
                return gateways.testMode() ? ok(Map.of("gateway", "test", "amount", q.total(), "currency", "INR"))
                        : error(HttpStatus.SERVICE_UNAVAILABLE, "Card, UPI and net banking payments aren't available right now. Please contact us.");
            }
            String rzp = gateways.razorpayCreateOrder(order.str("order_number"), q.total(),
                    Map.of("order_id", String.valueOf(orderId), "course_id", String.valueOf(courseId), "landing_page_id", String.valueOf(pageId)));
            db.updateById("orders", orderId, Row.of("gateway_ref", rzp));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("gateway", "razorpay");
            body.put("key", gateways.razorpayKey());
            body.put("razorpayOrderId", rzp);
            body.put("amount", q.total().movePointRight(2).longValueExact());
            body.put("currency", "INR");
            body.put("name", "ArtistikCity");
            body.put("description", view.course(courseId).get("title"));
            body.put("prefill", Row.of("name", order.get("billing_name"), "email", user.get("email"), "contact", user.get("phone")));
            body.put("config", payments.razorpayDisplay(plan, choice.key()));
            return ok(body);
        } catch (Exception e) {
            log.error("Starting landing payment failed for order {}", orderId, e);
            return error(HttpStatus.BAD_GATEWAY, "We couldn't reach the payment provider. No money was taken. Please try again.");
        }
    }

    /** The payment sheet was closed or the provider declined: remembered for the funnel; the order can be paid again. */
    @PostMapping("/landing/checkout/{pageId}/failed")
    public ResponseEntity<String> failed(HttpServletRequest request, @PathVariable("pageId") long pageId) {
        Row user = auth.user(request);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        Input in = Input.of(request, json);
        Row order = db.first("select id, payment_method, payment_provider from orders where id = ? and user_id = ? and landing_page_id = ? and payment_status = 0",
                in.lng("orderId"), user.get("id"), pageId);
        if (order != null) {
            boolean declined = "declined".equals(in.get("reason"));
            if (declined) {
                db.update("update orders set payment_status = 2, updated_at = ? where id = ? and payment_status = 0", LocalDateTime.now(), order.get("id"));
            }
            analytics.record(pageId, "PAY_FAIL", visitor(request), order.str("payment_method"), order.str("payment_provider"), order.lng("id"),
                    LandingPublic.utm(request), LandingAnalytics.device(request.getHeader("User-Agent")));
        }
        return ok(Map.of("ok", true));
    }

    /** Polled by the "payment pending" screen. */
    @GetMapping("/landing/checkout/order/{orderId}/status")
    public ResponseEntity<String> status(HttpServletRequest request, @PathVariable("orderId") long orderId) {
        Row user = auth.user(request);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        Row o = db.first("select id, order_number, payment_status, gateway_ref, price, price_type, payment_method from orders where id = ? and user_id = ? and landing_page_id is not null",
                orderId, user.get("id"));
        if (o == null) return error(HttpStatus.NOT_FOUND, "Order not found.");
        return ok(result(o, request));
    }

    /** Events from the page's script: CTA clicks and the payment option picked at checkout. */
    @PostMapping("/landing/e")
    public ResponseEntity<String> event(HttpServletRequest request) {
        Input in = Input.of(request, json);
        String event = String.valueOf(in.get("event"));
        Long pageId = in.lng("page");
        if (pageId == null || !("CTA".equals(event) || "METHOD".equals(event) || "LEAD".equals(event)) || LandingPublic.isBot(request) || auth.admin(request) != null) {
            return ResponseEntity.noContent().build();
        }
        if (db.count("select count(*) from landing_pages where id = ? and status = 'PUBLISHED'", pageId) == 0) {
            return ResponseEntity.noContent().build();
        }
        String method = "METHOD".equals(event) ? String.valueOf(in.get("method")) : null;
        if (method != null && !method.matches("^(upi|card|netbanking|wallet|emi|paypal)$")) {
            return ResponseEntity.noContent().build();
        }
        String visitor = visitor(request);
        if (visitor != null) {
            analytics.record(pageId, event, visitor, method, null, null, LandingPublic.utm(request), LandingAnalytics.device(request.getHeader("User-Agent")));
        }
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private Row livePage(long pageId) {
        return db.first("select * from landing_pages where id = ? and status = 'PUBLISHED' and course_id is not null", pageId);
    }

    /** Where a paid landing order lands: the page's confirmation screen, keeping the course checkout's flags. */
    public static String doneUrl(Row page, long orderId, String courseUrl) {
        String flags = courseUrl != null && courseUrl.contains("duplicate=1") ? "&duplicate=1" : courseUrl != null && courseUrl.contains("soldout=1") ? "&soldout=1" : "";
        return LandingPages.enrollPath(page.str("slug")) + "/done?order_id=" + orderId + flags;
    }

    private static String visitor(HttpServletRequest request) {
        String v = LandingPublic.cookie(request, LandingPublic.VISITOR);
        return v != null && v.matches("[a-f0-9-]{36}") ? v : null;
    }

    private static String trim(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private ResponseEntity<String> ok(Object body) {
        return json(HttpStatus.OK, body);
    }

    private ResponseEntity<String> json(HttpStatus status, Object body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        return json(status, Map.of("message", message));
    }

    private static ResponseEntity<String> redirect(String to) {
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", to).build();
    }
}
