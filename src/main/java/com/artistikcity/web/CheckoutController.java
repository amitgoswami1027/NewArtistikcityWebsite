package com.artistikcity.web;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Flash;
import com.artistikcity.http.NotFoundException;
import com.artistikcity.http.Redirects;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.service.CourseService;
import com.artistikcity.service.Mailer;
import com.artistikcity.service.PaymentGateways;
import com.artistikcity.support.Db;
import com.artistikcity.support.Input;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Validator;
import com.artistikcity.support.Values;
import com.artistikcity.view.Views;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Cart, checkout, payment (CartController, OrderController, PaymentController and PaypalController).
 * Flow: course page "Buy Now" -> /add-to-cart -> /checkout (login + billing) -> /checkout/payment
 * -> Razorpay (INR) or PayPal (USD) -> /payment/success | /payment/failed.
 */
@Controller
public class CheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);
    private static final String CART = "cart";
    private static final String ORDER_ID = "orderId";

    private final Views views;
    private final Db db;
    private final Json json;
    private final Auth auth;
    private final Validator validator;
    private final CourseService courses;
    private final PaymentGateways gateways;
    private final Mailer mailer;
    private final Redirects redirect;

    public CheckoutController(Views views, Db db, Json json, Auth auth, Validator validator, CourseService courses,
                              PaymentGateways gateways, Mailer mailer, Redirects redirect) {
        this.views = views;
        this.db = db;
        this.json = json;
        this.auth = auth;
        this.validator = validator;
        this.courses = courses;
        this.gateways = gateways;
        this.mailer = mailer;
        this.redirect = redirect;
    }

    // ------------------------------------------------------------------- cart

    /** CartController@addToCart: stores the chosen course in the session. */
    @PostMapping("/add-to-cart")
    public ResponseEntity<String> addToCart(HttpServletRequest request) {
        Input in = Input.of(request, json);
        validator.validate(in, "course_id", "required");
        HashMap<String, Object> item = new HashMap<>();
        item.put("user_id", auth.userId(request));
        item.put("course_id", in.lng("course_id"));
        item.put("price_type", in.get("price_type", "inr"));
        item.put("price", in.get("price"));
        request.getSession(true).setAttribute(CART, item);
        return redirect.route(request, "course.checkout");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cart(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        Object c = s == null ? null : s.getAttribute(CART);
        return c instanceof Map ? (Map<String, Object>) c : null;
    }

    /** OrderController@getCartItems: the course in the cart with first photo and prices. */
    private Row cartCourse(HttpServletRequest request) {
        Map<String, Object> cart = cart(request);
        if (cart == null) {
            return null;
        }
        Row c = db.find("courses", cart.get("course_id"));
        return c == null ? null : present(courses.withRelations(c, true));
    }

    /** Adds the display values used by the checkout templates. */
    static Row present(Row course) {
        Object photos = course.get("photos");
        String photo = photos instanceof java.util.List<?> l && !l.isEmpty() ? ((Row) l.get(0)).str("photo_name") : null;
        course.put("photo_url", photo == null ? "/assets/images/checkout-img1.jpg" : "/storage/uploads/courses/" + course.get("id") + "/" + photo);
        Row price = (Row) ((java.util.List<?>) course.get("prices")).get(0);
        double inr = Values.toDouble(price.get("price_inr")) == null ? 0 : Values.toDouble(price.get("price_inr"));
        course.put("price_inr_fmt", money(inr));
        course.put("price_inr_paise", Math.round(inr * 100));
        course.put("price_usd", price.get("price_usd"));
        return course;
    }

    static String money(double v) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(v);
    }

    // --------------------------------------------------------------- checkout

    @GetMapping("/checkout")
    public ResponseEntity<String> checkout(HttpServletRequest request) {
        Row course = cartCourse(request);
        if (course == null) {
            return redirect.route(request, "courses");
        }
        Row address = null;
        Long uid = auth.userId(request);
        if (uid != null) {
            address = db.first("select top 1 * from user_addresses where [primary] = 1 and user_id = ? order by id", uid);
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("course", course);
        model.put("primaryAddress", address);
        String country = address == null ? null : address.str("country");
        model.put("countryIndia", "india".equalsIgnoreCase(country));
        model.put("countryUsa", "usa".equalsIgnoreCase(country));
        return views.page(request, "orders/checkout", model);
    }

    @PostMapping("/checkout/billing")
    public ResponseEntity<String> billing(HttpServletRequest request) {
        Long uid = auth.userId(request);
        if (uid == null) {
            return redirect.route(request, "course.checkout");
        }
        Input in = Input.of(request, json);
        validator.validate(in, "billing_name", "required", "billing_address", "required");
        Map<String, Object> cart = cart(request);
        if (cart == null) {
            return redirect.route(request, "courses");
        }
        String sessionId = request.getSession().getId();
        Long courseId = in.lng("course_id") != null ? in.lng("course_id") : Values.toLong(cart.get("course_id"));
        Row billing = billingFields(in);

        // Order::updateOrCreate(all billing attributes, ...)
        StringBuilder where = new StringBuilder("user_id = ? and course_id = ? and session_id = ?");
        java.util.List<Object> params = new java.util.ArrayList<>(java.util.List.of(uid, courseId, sessionId));
        for (Map.Entry<String, Object> e : billing.entrySet()) {
            where.append(" and ").append(e.getKey()).append(e.getValue() == null ? " is null" : " = ?");
            if (e.getValue() != null) {
                params.add(e.getValue());
            }
        }
        Row order = db.first("select top 1 * from orders where " + where + " order by id desc", params.toArray());
        long orderId;
        if (order == null) {
            Row values = new Row(billing);
            values.put("user_id", uid);
            values.put("course_id", courseId);
            values.put("session_id", sessionId);
            orderId = db.insert("orders", values);
        } else {
            orderId = order.lng("id");
        }
        request.getSession().setAttribute(ORDER_ID, orderId);

        if (db.count("select count(*) from cart_items where session_id = ? and user_id = ? and course_id = ? and order_id = ?",
                sessionId, uid, courseId, orderId) == 0) {
            db.insert("cart_items", Row.of("session_id", sessionId, "user_id", uid, "course_id", courseId, "order_id", orderId,
                    "price_type", cart.get("price_type"), "price", Values.str(cart.get("price"))));
        }
        db.updateById("orders", orderId, Row.of("order_number", "ORD0000" + orderId,
                "price_type", cart.get("price_type"), "price", Values.str(cart.get("price"))));

        if (in.get("saved_address") == null && db.count("select count(*) from user_addresses where user_id = ? and address_name = ? and address = ?",
                uid, in.get("billing_name"), in.get("billing_address")) == 0) {
            boolean first = db.count("select count(*) from user_addresses where user_id = ?", uid) == 0;
            db.insert("user_addresses", Row.of("user_id", uid, "address_name", in.get("billing_name"), "address", in.get("billing_address"),
                    "city", in.get("billing_city"), "state", in.get("billing_state"), "zipcode", in.get("billing_zipcode"),
                    "country", in.get("billing_country"), "primary", first ? 1 : 0, "status", 1));
        }
        return redirect.route(request, "course.checkout.payment");
    }

    private static Row billingFields(Input in) {
        return Row.of("billing_name", in.get("billing_name"), "billing_email", in.get("billing_email"),
                "billing_address", in.get("billing_address"), "billing_city", in.get("billing_city"),
                "billing_state", in.get("billing_state"), "billing_zipcode", in.get("billing_zipcode"),
                "billing_country", in.get("billing_country"));
    }

    @PatchMapping("/checkout/billing/{order_id}")
    public ResponseEntity<String> billingUpdate(HttpServletRequest request, @PathVariable("order_id") Long orderId) {
        Long uid = auth.userId(request);
        if (uid == null) {
            return redirect.route(request, "course.checkout");
        }
        Input in = Input.of(request, json);
        validator.validate(in, "billing_name", "required", "billing_address", "required");
        db.updateWhere("orders", billingFields(in), "id = ? and user_id = ?", orderId, uid);
        return redirect.route(request, "course.checkout.payment");
    }

    @GetMapping("/checkout/payment")
    public ResponseEntity<String> payment(HttpServletRequest request) {
        Long uid = auth.userId(request);
        if (uid == null) {
            return redirect.route(request, "course.checkout");
        }
        Object orderId = request.getSession().getAttribute(ORDER_ID);
        Row order = orderId == null ? null : db.first("select * from orders where id = ? and user_id = ?", orderId, uid);
        Row course = cartCourse(request);
        if (order == null || course == null) {
            return redirect.route(request, "course.checkout");
        }
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("order", order);
        model.put("course", course);
        model.put("countryIndia", "india".equalsIgnoreCase(order.str("billing_country")));
        model.put("countryUsa", "usa".equalsIgnoreCase(order.str("billing_country")));
        model.put("razorpayKey", gateways.razorpayConfigured() ? gateways.razorpayKey() : "");
        model.put("paymentTestMode", gateways.testMode());
        return views.page(request, "orders/payment", model);
    }

    // ---------------------------------------------------------------- Razorpay

    /** PaymentController@store: Razorpay Checkout posts razorpay_payment_id here. */
    @PostMapping("/checkout/payment")
    public ResponseEntity<String> paymentStore(HttpServletRequest request) {
        Row user = auth.user(request);
        if (user == null) {
            Flash.put(request, "error", "User is not authorize, please check!");
            return redirect.back(request);
        }
        Input in = Input.of(request, json);
        Long orderId = in.lng("order_id");
        String paymentId = in.get("razorpay_payment_id");
        Row order = orderId == null ? null : db.first("select * from orders where id = ? and user_id = ?", orderId, user.get("id"));
        if (order == null || paymentId == null) {
            Flash.put(request, "error", "Payment could not be verified.");
            return redirect.route(request, "course.checkout.payment");
        }
        try {
            Map<String, Object> details;
            boolean captured;
            if (!gateways.razorpayConfigured() && gateways.testMode()) {
                details = Row.of("id", paymentId + "_" + orderId, "status", "captured", "test", true);
                captured = true;
            } else {
                Map<String, Object> payment = gateways.razorpayFetch(paymentId);
                if (Boolean.TRUE.equals(payment.get("captured"))) {
                    details = payment;
                } else {
                    details = gateways.razorpayCapture(paymentId, payment.get("amount"), payment.get("currency"));
                }
                captured = "captured".equals(details.get("status")) || Boolean.TRUE.equals(details.get("captured"));
            }
            String pid = String.valueOf(details.get("id"));
            int status = captured ? 1 : 2;
            db.updateById("orders", orderId, Row.of("payment_option", "RazorPay", "payment_id", pid, "payment_status", status,
                    "payment_details", json.encode(details)));
            logPayment(user.lng("id"), orderId, order.lng("course_id"), "RazorPay", pid, status, json.encode(details));
            if (!captured) {
                return redirect.route(request, "course.payment.failed", Map.of("order_id", orderId));
            }
            sendOrderConfirmation(request, orderId, user.str("email"));
            Flash.put(request, "success", "Payment successful!");
            return redirect.route(request, "course.payment.success", Map.of("order_id", orderId));
        } catch (Exception e) {
            log.error("Razorpay payment failed for order {}", orderId, e);
            db.updateById("orders", orderId, Row.of("payment_option", "RazorPay", "payment_status", 0, "payment_details", json.encode(e.getMessage())));
            Flash.put(request, "error", e.getMessage());
            return redirect.route(request, "course.payment.failed", Map.of("order_id", orderId));
        }
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
        if (data != null) {
            mailer.send(email, "Order Confirmation - " + data.str("order_number"), "emails/order-confirmation",
                    Row.of("orderData", data, "baseUrl", Inertia.baseUrl(request)));
        }
    }

    /** /payment/ordermail - preview of the order confirmation e-mail for the latest paid order of the user. */
    @GetMapping("/payment/ordermail")
    public ResponseEntity<String> orderMail(HttpServletRequest request) {
        Long uid = auth.userId(request);
        Row o = uid == null ? null : db.first("select top 1 id from orders where user_id = ? order by id desc", uid);
        if (o == null) {
            throw new NotFoundException();
        }
        return ResponseEntity.ok().header("Content-Type", "text/html;charset=UTF-8")
                .body(views.render("emails/order-confirmation", Row.of("orderData", orderMailData(o.lng("id")), "baseUrl", Inertia.baseUrl(request))));
    }

    @GetMapping("/payment/success")
    public ResponseEntity<String> paymentSuccess(HttpServletRequest request, @RequestParam(value = "order_id", required = false) Long orderId) {
        return result(request, orderId, "orders/payment-success");
    }

    @GetMapping("/payment/failed")
    public ResponseEntity<String> paymentFailed(HttpServletRequest request, @RequestParam(value = "order_id", required = false) Long orderId) {
        return result(request, orderId, "orders/payment-failed");
    }

    private ResponseEntity<String> result(HttpServletRequest request, Long orderId, String view) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (orderId != null) {
            Row order = NotFoundException.orFail(db.find("orders", orderId));
            details.put("order", order);
            Row course = db.find("courses", order.get("course_id"));
            if (course != null) {
                details.put("course", present(courses.withRelations(course, true)));
            }
        }
        HttpSession s = request.getSession(true);
        s.removeAttribute(CART);
        s.removeAttribute(ORDER_ID);
        return views.page(request, view, Map.of("orderDetails", details));
    }

    // ------------------------------------------------------------------ PayPal

    @GetMapping("/paywithpaypal")
    public ResponseEntity<String> payWithPaypal(HttpServletRequest request) {
        return views.page(request, "orders/paypal", Map.of());
    }

    /** PaypalController@postPaymentWithpaypal: creates the PayPal payment and redirects to PayPal. */
    @PostMapping("/paypal")
    public ResponseEntity<String> postPaymentWithPaypal(HttpServletRequest request) {
        Input in = Input.of(request, json);
        Long orderId = in.lng("order_id");
        Row data = orderId == null ? null : db.first("select c.title, cp.price_usd from orders o join courses c on c.id = o.course_id"
                + " join course_prices cp on cp.course_id = c.id where o.id = ? order by cp.id desc", orderId);
        if (data == null) {
            Flash.put(request, "error", "No Order Found!");
            return redirect.route(request, "course.checkout.payment");
        }
        if (!gateways.paypalConfigured()) {
            Flash.put(request, "error", "PayPal is not configured (set PAYPAL_CLIENT_ID and PAYPAL_SECRET).");
            return redirect.route(request, "course.checkout.payment");
        }
        try {
            String amount = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(Values.toDouble(data.get("price_usd")));
            String status = Inertia.baseUrl(request) + redirect.routes().path("status");
            String[] created = gateways.paypalCreate(data.str("title"), amount, status, status);
            request.getSession().setAttribute("paypal_order_id", orderId);
            request.getSession().setAttribute("paypal_payment_id", created[0]);
            if (created[1] != null) {
                return redirect.to(request, created[1]);
            }
            Flash.put(request, "error", "Unknown error occurred");
        } catch (Exception e) {
            log.error("PayPal create payment failed", e);
            Flash.put(request, "error", "Some error occur, sorry for inconvenient");
        }
        return redirect.route(request, "course.checkout.payment");
    }

    /** PaypalController@getPaymentStatus: PayPal redirects back here. */
    @GetMapping("/paypal")
    public ResponseEntity<String> paypalStatus(HttpServletRequest request,
                                               @RequestParam(value = "PayerID", required = false) String payerId,
                                               @RequestParam(value = "token", required = false) String token) {
        HttpSession s = request.getSession(true);
        Object paymentId = s.getAttribute("paypal_payment_id");
        Long orderId = Values.toLong(s.getAttribute("paypal_order_id"));
        s.removeAttribute("paypal_payment_id");
        s.removeAttribute("paypal_order_id");
        Row user = auth.user(request);
        if (orderId == null) {
            return redirect.route(request, "courses");
        }
        if (payerId == null || token == null || paymentId == null) {
            db.updateById("orders", orderId, Row.of("payment_option", "PayPal", "payment_id", Values.str(paymentId), "payment_status", 2));
            Flash.put(request, "error", "Payment failed");
            return redirect.route(request, "course.payment.failed", Map.of("order_id", orderId));
        }
        try {
            String state = gateways.paypalExecute(String.valueOf(paymentId), payerId);
            if ("approved".equals(state)) {
                db.updateById("orders", orderId, Row.of("payment_option", "PayPal", "payment_id", String.valueOf(paymentId), "payment_status", 1));
                if (user != null) {
                    Row order = db.find("orders", orderId);
                    logPayment(user.lng("id"), orderId, order.lng("course_id"), "PayPal", String.valueOf(paymentId), 1, null);
                    sendOrderConfirmation(request, orderId, user.str("email"));
                }
                Flash.put(request, "success", "Payment success !!");
                return redirect.route(request, "course.payment.success", Map.of("order_id", orderId));
            }
        } catch (Exception e) {
            log.error("PayPal execute failed", e);
        }
        db.updateById("orders", orderId, Row.of("payment_option", "PayPal", "payment_id", String.valueOf(paymentId), "payment_status", 2));
        Flash.put(request, "error", "Payment failed !!");
        return redirect.route(request, "course.payment.failed", Map.of("order_id", orderId));
    }
}
