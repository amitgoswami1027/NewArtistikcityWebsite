package com.artistikcity.web;

import com.artistikcity.commission.CommissionPayments;
import com.artistikcity.http.Auth;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.http.Redirects;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.CourseService;
import com.artistikcity.service.Mailer;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Values;
import com.artistikcity.view.Views;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Course checkout (one page): course page "Enroll now" -> /add-to-cart -> /checkout
 * (1 account, 2 billing details, 3 payment) -> Razorpay (INR) or PayPal (USD) -> /payment/success | /payment/failed.
 *
 * <p>Security: prices always come from course_prices (never from the browser). A payment is only accepted for the
 * gateway order created for this order (orders.gateway_ref): Razorpay with its checkout signature (HMAC-SHA256),
 * PayPal by capturing the order and checking amount and currency. An order is marked paid only once.
 */
@Controller
public class CheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);
    private static final String CART = "cart";
    private static final String ORDER_ID = "orderId";

    private final Views views;
    private final Inertia inertia;
    private final Db db;
    private final Json json;
    private final Auth auth;
    private final CourseService courses;
    private final CommissionPayments gateways;
    private final Mailer mailer;
    private final Redirects redirect;
    private final org.springframework.core.env.Environment env;

    public CheckoutController(Views views, Inertia inertia, Db db, Json json, Auth auth, CourseService courses,
                              CommissionPayments gateways, Mailer mailer, Redirects redirect, org.springframework.core.env.Environment env) {
        this.env = env;
        this.views = views;
        this.inertia = inertia;
        this.db = db;
        this.json = json;
        this.auth = auth;
        this.courses = courses;
        this.gateways = gateways;
        this.mailer = mailer;
        this.redirect = redirect;
    }

    // ------------------------------------------------------------------- cart

    /** "Enroll now" on a course page: remembers the course (only its id - the price is looked up here). */
    @PostMapping("/add-to-cart")
    public ResponseEntity<String> addToCart(HttpServletRequest request) {
        Input in = Input.of(request, json);
        Long courseId = in.lng("course_id");
        Row course = courseId == null ? null : db.first("select id from courses where id = ? and status = 1", courseId);
        if (course == null) {
            return redirect.route(request, "courses");
        }
        putCart(request, courseId);
        return redirect.to(request, "/checkout");
    }

    private void putCart(HttpServletRequest request, long courseId) {
        HashMap<String, Object> item = new HashMap<>();
        item.put("course_id", courseId);
        HttpSession s = request.getSession(true);
        Object previous = s.getAttribute(CART);
        if (!(previous instanceof Map<?, ?> p) || !String.valueOf(p.get("course_id")).equals(String.valueOf(courseId))) {
            s.removeAttribute(ORDER_ID);
        }
        s.setAttribute(CART, item);
    }

    private Long cartCourseId(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        Object c = s == null ? null : s.getAttribute(CART);
        return c instanceof Map<?, ?> m ? Values.toLong(m.get("course_id")) : null;
    }

    /** [inr, usd] from course_prices (first row), 0 when missing. */
    private BigDecimal[] prices(long courseId) {
        Row p = db.first("select price_inr, price_usd from course_prices where course_id = ? order by id limit 1", courseId);
        BigDecimal inr = p == null || p.get("price_inr") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(p.get("price_inr")));
        BigDecimal usd = p == null || p.get("price_usd") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(p.get("price_usd")));
        return new BigDecimal[]{inr.setScale(2, RoundingMode.HALF_UP), usd.setScale(2, RoundingMode.HALF_UP)};
    }

    static String money(double v) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(v);
    }

    /** Older templates (certificates, e-mails) use these display values. */
    static Row present(Row course) {
        Object photos = course.get("photos");
        String photo = photos instanceof List<?> l && !l.isEmpty() ? ((Row) l.get(0)).str("photo_name") : null;
        course.put("photo_url", photo == null ? "/assets/images/checkout-img1.jpg" : "/storage/uploads/courses/" + course.get("id") + "/" + photo);
        Object prices = course.get("prices");
        Row price = prices instanceof List<?> pl && !pl.isEmpty() ? (Row) pl.get(0) : new Row();
        double inr = Values.toDouble(price.get("price_inr")) == null ? 0 : Values.toDouble(price.get("price_inr"));
        course.put("price_inr_fmt", money(inr));
        course.put("price_inr_paise", Math.round(inr * 100));
        course.put("price_usd", price.get("price_usd"));
        return course;
    }

    private Map<String, Object> courseView(long courseId) {
        Row c = db.first("select c.*, (select name from mediums m where m.id = c.medium_id) as medium_name,"
                + " (select a.name from admins a where a.id = c.admin_id) as teacher_name, " + CourseService.photoSubquery("c.id")
                + " from courses c where c.id = ?", courseId);
        if (c == null) {
            return null;
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", c.get("id"));
        v.put("title", c.get("title"));
        v.put("sub_title", c.get("sub_title"));
        v.put("slug", c.get("slug"));
        v.put("kind", c.lng("course_type_id", 1) == CourseService.TYPE_WORKSHOP ? "Workshop" : "Live course");
        v.put("image", c.get("photo") == null ? null : "/storage/uploads/courses/" + c.get("id") + "/" + c.get("photo"));
        v.put("medium", c.get("medium_name"));
        v.put("teacher", c.get("teacher_name"));
        v.put("age_group", c.get("age_group"));
        v.put("duration", c.get("duration"));
        v.put("sessions", c.get("sessions"));
        v.put("hours_per_week", c.get("time_required"));
        v.put("start_date", CourseService.formatStartDate(c.str("course_start_date")));
        Object inc = json.decode(c.str("course_includes"));
        Map<?, ?> includes = inc instanceof Map<?, ?> m ? m : Map.of();
        v.put("certificate", "yes".equalsIgnoreCase(String.valueOf(includes.get("certificate"))));
        v.put("materials", "yes".equalsIgnoreCase(String.valueOf(includes.get("study_material_access"))));
        return v;
    }

    // --------------------------------------------------------------- the page

    @GetMapping("/checkout")
    public ResponseEntity<String> checkout(HttpServletRequest request, @RequestParam(value = "course", required = false) String slug) {
        if (slug != null && !slug.isBlank()) {
            // links from the login page carry the course, so checkout survives a sign-in on another device or browser
            Row c = db.first("select id from courses where slug = ? and status = 1", slug);
            if (c != null) {
                putCart(request, c.lng("id"));
            }
        }
        Long courseId = cartCourseId(request);
        Map<String, Object> course = courseId == null ? null : courseView(courseId);
        if (course == null) {
            return redirect.route(request, "courses");
        }
        BigDecimal[] price = prices(courseId);
        Long uid = auth.userId(request);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("course", course);
        props.put("prices", Map.of("inr", price[0], "usd", price[1]));
        boolean free = price[0].signum() == 0 && price[1].signum() == 0;
        Map<String, Object> methods = new LinkedHashMap<>();
        methods.put("razorpay", gateways.razorpayConfigured() || gateways.testMode());
        methods.put("paypal", gateways.paypalConfigured() || gateways.testMode());
        methods.put("testMode", gateways.testMode() && !(gateways.razorpayConfigured() && gateways.paypalConfigured()));
        methods.put("free", free);
        props.put("methods", methods);
        if (uid != null) {
            Row paid = db.first("select id from orders where user_id = ? and course_id = ? and payment_status = 1 order by id desc limit 1", uid, courseId);
            props.put("enrolled", paid == null ? null : Map.of("orderId", paid.get("id"), "classroom", "/dashboard/classroom/" + courseId));
            props.put("addresses", db.select("select id, address_name, address, city, state, zipcode, country, `primary` from user_addresses"
                    + " where user_id = ? and status = 1 order by `primary` desc, id desc limit 6", uid));
            Row order = pendingOrder(request, uid, courseId);
            props.put("order", order == null ? null : Row.of("id", order.get("id"), "billing_name", order.get("billing_name"),
                    "billing_address", order.get("billing_address"), "billing_city", order.get("billing_city"),
                    "billing_state", order.get("billing_state"), "billing_zipcode", order.get("billing_zipcode"),
                    "billing_country", order.get("billing_country")));
        }
        props.put("returnTo", "/checkout?course=" + course.get("slug"));
        props.put("providers", SocialController.enabledProviders(env));
        props.put("notice", request.getParameter("payment"));
        return inertia.render(request, "Checkout/Course", props);
    }

    /** The open (unpaid) order of this student for this course, newest first. */
    private Row pendingOrder(HttpServletRequest request, long uid, long courseId) {
        Object sid = request.getSession(true).getAttribute(ORDER_ID);
        Row o = sid == null ? null : db.first("select * from orders where id = ? and user_id = ? and course_id = ? and payment_status <> 1", sid, uid, courseId);
        if (o == null) {
            o = db.first("select * from orders where user_id = ? and course_id = ? and payment_status <> 1 order by id desc limit 1", uid, courseId);
        }
        return o;
    }

    // --------------------------------------------------------------- step 2: billing details

    @PostMapping("/checkout/billing")
    public ResponseEntity<String> billing(HttpServletRequest request) {
        Row user = auth.user(request);
        if (user == null) {
            return error(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        }
        Long courseId = cartCourseId(request);
        if (courseId == null) {
            return error(HttpStatus.GONE, "Your checkout has expired. Choose the course again.");
        }
        Input in = Input.of(request, json);
        Map<String, Object> errors = new LinkedHashMap<>();
        for (String[] f : new String[][]{{"billing_name", "Enter your full name."}, {"billing_address", "Enter your street address."},
                {"billing_city", "Enter your city."}, {"billing_zipcode", "Enter your PIN / postal code."}, {"billing_country", "Choose your country."}}) {
            String v = in.get(f[0]);
            if (v == null || v.isBlank()) {
                errors.put(f[0], List.of(f[1]));
            } else if (v.length() > (f[0].equals("billing_address") ? 500 : 190)) {
                errors.put(f[0], List.of("That is too long."));
            }
        }
        if (!errors.isEmpty()) {
            return json(HttpStatus.UNPROCESSABLE_ENTITY, Map.of("message", "Please check the highlighted fields.", "errors", errors));
        }
        long uid = user.lng("id");
        if (db.count("select count(*) from orders where user_id = ? and course_id = ? and payment_status = 1", uid, courseId) > 0) {
            return json(HttpStatus.CONFLICT, Map.of("message", "You're already enrolled in this course.", "enrolled", true));
        }
        Row billing = Row.of("billing_name", in.get("billing_name").trim(), "billing_email", user.get("email"),
                "billing_address", in.get("billing_address").trim(), "billing_city", in.get("billing_city").trim(),
                "billing_state", in.get("billing_state") == null ? null : in.get("billing_state").trim(),
                "billing_zipcode", in.get("billing_zipcode").trim(), "billing_country", in.get("billing_country").trim());
        Row order = pendingOrder(request, uid, courseId);
        long orderId;
        if (order == null) {
            Row values = new Row(billing);
            values.put("user_id", uid);
            values.put("course_id", courseId);
            values.put("session_id", request.getSession().getId());
            orderId = db.insert("orders", values);
            db.updateById("orders", orderId, Row.of("order_number", "ORD" + String.format("%05d", orderId)));
        } else {
            orderId = order.lng("id");
            db.updateById("orders", orderId, billing);
        }
        request.getSession().setAttribute(ORDER_ID, orderId);
        if (in.bool("save_address") && db.count("select count(*) from user_addresses where user_id = ? and address = ? and zipcode = ?",
                uid, billing.get("billing_address"), billing.get("billing_zipcode")) == 0) {
            boolean first = db.count("select count(*) from user_addresses where user_id = ?", uid) == 0;
            db.insert("user_addresses", Row.of("user_id", uid, "address_name", billing.get("billing_name"), "address", billing.get("billing_address"),
                    "city", billing.get("billing_city"), "state", billing.get("billing_state"), "zipcode", billing.get("billing_zipcode"),
                    "country", billing.get("billing_country"), "primary", first ? 1 : 0, "status", 1));
        }
        return json(HttpStatus.OK, Map.of("orderId", orderId));
    }

    // --------------------------------------------------------------- step 3: payment

    /** {orderId, method: razorpay|paypal|free} -> what the page needs to open the payment. */
    @PostMapping("/checkout/pay")
    public ResponseEntity<String> pay(HttpServletRequest request) {
        Row user = auth.user(request);
        if (user == null) {
            return error(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        }
        Input in = Input.of(request, json);
        Row order = ownOpenOrder(user, in.lng("orderId"));
        if (order == null) {
            return error(HttpStatus.GONE, "This order is no longer open. Please start the checkout again.");
        }
        long courseId = order.lng("course_id");
        if (db.count("select count(*) from orders where user_id = ? and course_id = ? and payment_status = 1", user.get("id"), courseId) > 0) {
            return json(HttpStatus.CONFLICT, Map.of("message", "You're already enrolled in this course.", "enrolled", true));
        }
        BigDecimal[] price = prices(courseId);
        String method = String.valueOf(in.get("method"));
        Map<String, Object> course = courseView(courseId);
        try {
            if ("free".equals(method)) {
                if (price[0].signum() != 0 || price[1].signum() != 0) {
                    return error(HttpStatus.UNPROCESSABLE_ENTITY, "This course isn't free.");
                }
                db.updateById("orders", order.lng("id"), Row.of("price_type", "inr", "price", "0", "payment_option", "Free"));
                return json(HttpStatus.OK, Map.of("gateway", "done", "redirect", markPaid(request, user, order.lng("id"), "Free", "free_" + order.get("id"), Map.of("free", true))));
            }
            if ("razorpay".equals(method)) {
                db.updateById("orders", order.lng("id"), Row.of("price_type", "inr", "price", price[0].toPlainString(), "payment_option", "RazorPay"));
                if (!gateways.razorpayConfigured()) {
                    return gateways.testMode() ? json(HttpStatus.OK, Map.of("gateway", "test", "amount", price[0], "currency", "INR"))
                            : error(HttpStatus.SERVICE_UNAVAILABLE, "Card, UPI and NetBanking payments aren't available right now. Please try PayPal or contact us.");
                }
                String rzpOrder = gateways.razorpayCreateOrder(order.str("order_number"), price[0],
                        Map.of("order_id", String.valueOf(order.get("id")), "course_id", String.valueOf(courseId)));
                db.updateById("orders", order.lng("id"), Row.of("gateway_ref", rzpOrder));
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("gateway", "razorpay");
                body.put("key", gateways.razorpayKey());
                body.put("razorpayOrderId", rzpOrder);
                body.put("amount", price[0].movePointRight(2).longValueExact());
                body.put("currency", "INR");
                body.put("name", "ArtistikCity");
                body.put("description", course.get("title"));
                body.put("prefill", Row.of("name", order.get("billing_name"), "email", user.get("email"), "contact", user.get("phone")));
                return json(HttpStatus.OK, body);
            }
            if ("paypal".equals(method)) {
                db.updateById("orders", order.lng("id"), Row.of("price_type", "usd", "price", price[1].toPlainString(), "payment_option", "PayPal"));
                if (!gateways.paypalConfigured()) {
                    return gateways.testMode() ? json(HttpStatus.OK, Map.of("gateway", "test", "amount", price[1], "currency", "USD"))
                            : error(HttpStatus.SERVICE_UNAVAILABLE, "PayPal isn't available right now. Please pay by card or UPI, or contact us.");
                }
                String base = Inertia.baseUrl(request);
                String[] created = gateways.paypalCreateOrder(order.str("order_number"), price[1], String.valueOf(course.get("title")),
                        base + "/checkout/paypal/return", base + "/checkout?payment=cancelled");
                if (created[1] == null) {
                    return error(HttpStatus.BAD_GATEWAY, "PayPal didn't respond. Please try again.");
                }
                db.updateById("orders", order.lng("id"), Row.of("gateway_ref", created[0]));
                return json(HttpStatus.OK, Map.of("gateway", "paypal", "approveUrl", created[1]));
            }
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "Choose a payment method.");
        } catch (Exception e) {
            log.error("Starting {} payment failed for order {}", method, order.get("id"), e);
            return error(HttpStatus.BAD_GATEWAY, "We couldn't reach the payment provider. No money was taken. Please try again.");
        }
    }

    private Row ownOpenOrder(Row user, Long orderId) {
        return orderId == null ? null : db.first("select * from orders where id = ? and user_id = ? and payment_status <> 1", orderId, user.get("id"));
    }

    /** Razorpay Checkout success handler: verify the signature for the Razorpay order created for this order. */
    @PostMapping("/checkout/razorpay/verify")
    public ResponseEntity<String> razorpayVerify(HttpServletRequest request) {
        Row user = auth.user(request);
        if (user == null) {
            return error(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        }
        Input in = Input.of(request, json);
        Row order = ownOpenOrder(user, in.lng("orderId"));
        String rzpOrder = in.get("razorpay_order_id");
        String paymentId = in.get("razorpay_payment_id");
        String signature = in.get("razorpay_signature");
        if (order == null || rzpOrder == null || paymentId == null || !rzpOrder.equals(order.str("gateway_ref"))
                || !gateways.razorpayCheckoutSignatureValid(rzpOrder, paymentId, signature)) {
            log.warn("Razorpay verification failed for order {} (payment {})", in.get("orderId"), paymentId);
            return error(HttpStatus.BAD_REQUEST, "We couldn't verify this payment. If money was taken, it will be refunded automatically - or contact us with your order number.");
        }
        return json(HttpStatus.OK, Map.of("redirect", markPaid(request, user, order.lng("id"), "RazorPay", paymentId,
                Map.of("razorpay_order_id", rzpOrder, "razorpay_payment_id", paymentId))));
    }

    /**
     * Razorpay webhook for course orders (payment.captured / order.paid), signed with X-Razorpay-Signature.
     * It completes an order when the student paid but closed the page before the browser could confirm.
     * Configure it in the Razorpay dashboard: https://YOUR-SITE/api/v1/courses/razorpay-webhook (RAZORPAY_WEBHOOK_SECRET).
     */
    @PostMapping("/api/v1/courses/razorpay-webhook")
    @SuppressWarnings("unchecked")
    public ResponseEntity<String> razorpayWebhook(HttpServletRequest request) throws java.io.IOException {
        String raw = new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (!gateways.razorpayWebhookValid(raw, request.getHeader("X-Razorpay-Signature"))) {
            log.warn("Rejected course Razorpay webhook with an invalid signature");
            return error(HttpStatus.UNAUTHORIZED, "Invalid signature.");
        }
        Map<String, Object> event = json.decodeMap(raw);
        String type = event == null ? null : String.valueOf(event.get("event"));
        if (!"payment.captured".equals(type) && !"order.paid".equals(type)) {
            return json(HttpStatus.OK, Map.of("ignored", String.valueOf(type)));
        }
        Object payload = event.get("payload");
        Object paymentWrap = payload instanceof Map<?, ?> pm ? pm.get("payment") : null;
        Object entity = paymentWrap instanceof Map<?, ?> pw ? pw.get("entity") : null;
        if (!(entity instanceof Map<?, ?> payment)) {
            return json(HttpStatus.OK, Map.of("ignored", "no payment entity"));
        }
        Row order = db.first("select * from orders where gateway_ref = ?", String.valueOf(payment.get("order_id")));
        if (order == null) {
            return json(HttpStatus.OK, Map.of("ignored", "not a course order"));
        }
        long expected = prices(order.lng("course_id"))[0].movePointRight(2).longValueExact();
        long paid = payment.get("amount") instanceof Number n ? n.longValue() : -1;
        if (expected != paid || !"INR".equalsIgnoreCase(String.valueOf(payment.get("currency")))) {
            log.warn("Course Razorpay amount mismatch for order {}: expected {} got {}", order.get("id"), expected, paid);
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "Amount mismatch.");
        }
        Row user = db.first("select email from users where id = ?", order.get("user_id"));
        markPaid(request, order.lng("user_id"), user == null ? null : user.str("email"), order.lng("id"), "RazorPay",
                String.valueOf(payment.get("id")), Map.of("razorpay_order_id", String.valueOf(payment.get("order_id")), "via", "webhook"));
        return json(HttpStatus.OK, Map.of("orderId", order.get("id")));
    }

    /** Simulated payment, only when PAYMENT_TEST_MODE is on and the chosen gateway has no keys (local development). */
    @PostMapping("/checkout/test-pay")
    public ResponseEntity<String> testPay(HttpServletRequest request) {
        Row user = auth.user(request);
        Input in = Input.of(request, json);
        Row order = user == null ? null : ownOpenOrder(user, in.lng("orderId"));
        boolean usd = order != null && "usd".equals(order.str("price_type"));
        boolean allowed = gateways.testMode() && (usd ? !gateways.paypalConfigured() : !gateways.razorpayConfigured());
        if (order == null || !allowed) {
            return error(HttpStatus.FORBIDDEN, "Test payments are not available.");
        }
        return json(HttpStatus.OK, Map.of("redirect", markPaid(request, user, order.lng("id"), usd ? "PayPal (test)" : "RazorPay (test)",
                "test_" + order.get("id") + "_" + System.currentTimeMillis(), Map.of("test", true))));
    }

    /** PayPal sends the buyer back here (?token = PayPal order id) after approving. */
    @GetMapping("/checkout/paypal/return")
    public ResponseEntity<String> paypalReturn(HttpServletRequest request, @RequestParam(value = "token", required = false) String token) {
        Row user = auth.user(request);
        Row order = user == null || token == null ? null
                : db.first("select * from orders where gateway_ref = ? and user_id = ?", token, user.get("id"));
        if (order == null) {
            return redirect.to(request, "/checkout?payment=failed");
        }
        if (order.lng("payment_status", 0) == 1) {
            return redirect.to(request, "/payment/success?order_id=" + order.get("id"));
        }
        try {
            String[] cap = gateways.paypalCapture(token);
            BigDecimal expected = prices(order.lng("course_id"))[1];
            boolean ok = "COMPLETED".equals(cap[0]) && "USD".equals(cap[3]) && cap[2] != null && new BigDecimal(cap[2]).compareTo(expected) == 0;
            if (ok) {
                return redirect.to(request, markPaid(request, user, order.lng("id"), "PayPal", cap[1], Map.of("paypal_order_id", token, "capture_id", cap[1])));
            }
            log.warn("PayPal capture for order {} not accepted: {}", order.get("id"), String.join(",", cap));
        } catch (Exception e) {
            log.error("PayPal capture failed for order {}", order.get("id"), e);
        }
        db.updateById("orders", order.lng("id"), Row.of("payment_status", 2));
        return redirect.to(request, "/payment/failed?order_id=" + order.get("id"));
    }

    /** Marks the order paid exactly once, records the payment and sends the confirmation. Returns the success URL. */
    private String markPaid(HttpServletRequest request, Row user, long orderId, String option, String paymentId, Map<String, ?> details) {
        String url = markPaid(request, user.lng("id"), user.str("email"), orderId, option, paymentId, details);
        HttpSession s = request.getSession(true);
        s.removeAttribute(CART);
        s.removeAttribute(ORDER_ID);
        return url;
    }

    private String markPaid(HttpServletRequest request, long userId, String email, long orderId, String option, String paymentId, Map<String, ?> details) {
        int changed = db.update("update orders set payment_status = 1, payment_option = ?, payment_id = ?, payment_details = ?, updated_at = ?"
                + " where id = ? and payment_status <> 1", option, paymentId, json.encode(details), LocalDateTime.now(), orderId);
        if (changed == 1) {
            Row order = db.find("orders", orderId);
            logPayment(userId, orderId, order.lng("course_id"), option, paymentId, 1, json.encode(details));
            sendOrderConfirmation(request, orderId, email != null ? email : order.str("billing_email"));
        }
        return "/payment/success?order_id=" + orderId;
    }

    /** payment_logs "upsert" keyed by (user_id, order_id). */
    private void logPayment(long userId, long orderId, Long courseId, String option, String paymentId, int status, String details) {
        Row values = Row.of("payment_option", option, "payment_id", paymentId, "payment_status", status, "payment_details", details);
        if (db.updateWhere("payment_logs", values, "user_id = ? and order_id = ?", userId, orderId) == 0) {
            values.put("user_id", userId);
            values.put("order_id", orderId);
            values.put("course_id", courseId);
            db.insert("payment_logs", values);
        }
    }

    private Row orderMailData(long orderId) {
        Row o = db.first("select c.title, o.order_number, o.billing_name, o.billing_address, o.billing_city, o.billing_state,"
                + " o.billing_zipcode, o.billing_country, o.price_type, o.price, o.created_at, c.id as course_id, c.course_type_id, c.schedule_pdf, "
                + CourseService.photoSubquery("o.course_id") + " from orders o join courses c on c.id = o.course_id where o.id = ?", orderId);
        if (o == null) {
            return null;
        }
        String created = o.str("created_at");
        return Row.of("order_number", o.get("order_number"), "course_title", o.get("title"), "billing_name", o.get("billing_name"),
                "billing_address", o.get("billing_address"), "billing_city", o.get("billing_city"), "billing_state", o.get("billing_state"),
                "billing_zipcode", o.get("billing_zipcode"), "billing_country", o.get("billing_country"),
                "price_type", o.str("price_type") == null ? "" : o.str("price_type").toUpperCase(), "price", o.get("price"),
                "created_at", created, "order_date", created == null ? "" : LocalDateTime.parse(created.replace(' ', 'T'))
                        .format(DateTimeFormatter.ofPattern("dd-MM-yyyy")), "schedule_pdf", o.get("schedule_pdf"));
    }

    private void sendOrderConfirmation(HttpServletRequest request, long orderId, String email) {
        Row data = orderMailData(orderId);
        if (data != null && email != null) {
            mailer.send(email, "Order Confirmation - " + data.str("order_number"), "emails/order-confirmation",
                    Row.of("orderData", data, "baseUrl", Inertia.baseUrl(request)));
        }
    }

    /** /payment/ordermail - preview of the order confirmation e-mail for the latest order of the user. */
    @GetMapping("/payment/ordermail")
    public ResponseEntity<String> orderMail(HttpServletRequest request) {
        Long uid = auth.userId(request);
        Row o = uid == null ? null : db.first("select id from orders where user_id = ? order by id desc limit 1", uid);
        if (o == null) {
            throw new NotFoundException();
        }
        return ResponseEntity.ok().header("Content-Type", "text/html;charset=UTF-8")
                .body(views.render("emails/order-confirmation", Row.of("orderData", orderMailData(o.lng("id")), "baseUrl", Inertia.baseUrl(request))));
    }

    // --------------------------------------------------------------- result pages

    @GetMapping("/payment/success")
    public ResponseEntity<String> paymentSuccess(HttpServletRequest request, @RequestParam(value = "order_id", required = false) Long orderId) {
        return result(request, orderId, true);
    }

    @GetMapping("/payment/failed")
    public ResponseEntity<String> paymentFailed(HttpServletRequest request, @RequestParam(value = "order_id", required = false) Long orderId) {
        return result(request, orderId, false);
    }

    private ResponseEntity<String> result(HttpServletRequest request, Long orderId, boolean success) {
        Long uid = auth.userId(request);
        Row order = orderId == null || uid == null ? null : db.first("select * from orders where id = ? and user_id = ?", orderId, uid);
        if (order == null) {
            return redirect.to(request, uid == null ? "/login" : "/user/orders");
        }
        boolean paid = order.lng("payment_status", 0) == 1;
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("paid", paid);
        props.put("order", Row.of("id", order.get("id"), "number", order.get("order_number"), "amount", order.get("price"),
                "currency", order.str("price_type") == null ? "INR" : order.str("price_type").toUpperCase(Locale.ROOT),
                "method", order.get("payment_option"), "date", order.get("updated_at") != null ? order.get("updated_at") : order.get("created_at"),
                "email", order.get("billing_email")));
        props.put("course", courseView(order.lng("course_id")));
        if (!paid) {
            putCart(request, order.lng("course_id"));
        }
        return inertia.render(request, "Checkout/Result", props);
    }

    // --------------------------------------------------------------- old addresses

    /** The old two-page checkout: everything is on /checkout now. */
    @GetMapping({"/checkout/payment", "/paywithpaypal", "/paypal"})
    public ResponseEntity<String> legacy(HttpServletRequest request) {
        return redirect.to(request, "/checkout");
    }

    private ResponseEntity<String> json(HttpStatus status, Object body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(json.encode(body));
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        return json(status, Map.of("message", message));
    }
}
