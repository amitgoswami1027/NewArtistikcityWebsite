package com.artistikcity.landing;

import com.artistikcity.commission.CommissionPayments;
import com.artistikcity.security.AuditLog;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Called by the course checkout once a payment is verified (browser verification, webhook or reconciler: exactly once per
 * order). For orders from a landing page: counts the coupon, records the purchase in the page's funnel and stores the
 * payment method the gateway reports. A method that the page does not allow (only possible by tampering with the
 * payment sheet) is written to the security log for staff to look at; the paid order is kept, money is never refused.
 */
@Component
public class LandingHooks {

    private static final Logger log = LoggerFactory.getLogger(LandingHooks.class);

    private final Db db;
    private final LandingCoupons coupons;
    private final CommissionPayments gateways;
    private final AuditLog auditLog;

    @org.springframework.beans.factory.annotation.Autowired
    private com.artistikcity.service.Mailer mailer;

    @org.springframework.beans.factory.annotation.Autowired
    private LandingView view;

    public LandingHooks(Db db, LandingCoupons coupons, CommissionPayments gateways, AuditLog auditLog) {
        this.db = db;
        this.coupons = coupons;
        this.gateways = gateways;
        this.auditLog = auditLog;
    }

    /**
     * The thank-you e-mail and receipt of a landing page purchase: the ArtistikCity logo, congratulations, the course, a
     * receipt (number, date, billed to, price, discount, tax, total, method, payment reference) and "Go to my course",
     * which signs the buyer straight in and opens the course ({@link CourseAccessLink}: single use, 72 hours); after
     * that the log-in page (a code to the buyer's mobile or e-mail, no password).
     * Returns false when the order isn't a landing order (the caller then sends the standard confirmation).
     */
    public boolean sendThankYou(long orderId, String email, String baseUrl) {
        Map<String, Object> model = thankYouModel(orderId, email, baseUrl, true);
        if (model == null) {
            return false;
        }
        return mailer.send(email, thankYouSubject(model), "emails/landing-thank-you", model);
    }

    public static String thankYouSubject(Map<String, Object> model) {
        return "Congratulations! You're enrolled in " + model.get("course") + " · Receipt " + model.get("orderNumber");
    }

    /**
     * Everything the thank-you e-mail shows, or null when the order isn't a landing order. {@code withLink} = create the
     * one-click course link (only for the real e-mail to the buyer; previews and test sends get the log-in link).
     */
    public Map<String, Object> thankYouModel(long orderId, String email, String baseUrl, boolean withLink) {
        Row o = db.first("select o.id, o.order_number, o.course_id, o.price, o.price_type, o.payment_method, o.payment_option, o.is_test, o.billing_name,"
                + " o.list_amount, o.discount_amount, o.tax_amount, o.coupon_code, o.created_at, o.updated_at, o.payment_id, o.billing_email, o.billing_phone,"
                + " o.billing_address, o.billing_city, o.billing_state, o.billing_zipcode, o.billing_country, u.name as user_name, u.phone_number"
                + " from orders o left join users u on u.id = o.user_id where o.id = ? and o.landing_page_id is not null", orderId);
        if (o == null) {
            return null;
        }
        Map<String, Object> course = view.course(o.lng("course_id"));
        Row artist = course.get("adminId") == null ? null : db.first("select name from admins where id = ?", course.get("adminId"));
        String base = baseUrl == null ? "" : baseUrl;
        java.math.BigDecimal price = new java.math.BigDecimal(o.str("price", "0"));
        String amount = "usd".equals(o.str("price_type")) ? LandingView.usd(price) : LandingView.inr(price);
        String method = o.str("payment_method", "");
        String paidWith = switch (method) {
            case "upi" -> "UPI";
            case "card" -> "Card";
            case "netbanking" -> "Net banking";
            case "wallet" -> "Wallet";
            case "emi" -> "EMI";
            case "paypal" -> "PayPal";
            case "free" -> "Free enrolment";
            default -> o.str("payment_option", "Online payment");
        };
        if (o.lng("is_test", 0) == 1) paidWith += " (test payment, no money taken)";
        String name = o.str("billing_name", o.str("user_name", "")).trim();
        String first = name.isEmpty() ? "there" : name.split("\\s+")[0];
        String back = "/user/course/" + orderId;
        String loginUrl = base + "/login?return_to=" + java.net.URLEncoder.encode(back, java.nio.charset.StandardCharsets.UTF_8);
        // the button signs the buyer straight in and opens the course (single use, 72 hours); after that, the log-in page
        String access = null;
        try {
            access = !withLink || courseAccess == null ? null : courseAccess.issue(currentRequest(), orderId, email);
        } catch (RuntimeException e) {
            log.warn("Course access link for order {} not created: {}", orderId, e.toString());
        }
        String courseUrl = access == null ? loginUrl : base + access;
        boolean usd = "usd".equals(o.str("price_type"));
        java.util.function.Function<String, String> money = k -> {
            String v = o.str(k);
            if (v == null || v.isBlank()) return null;
            java.math.BigDecimal d = new java.math.BigDecimal(v);
            return d.signum() == 0 ? null : usd ? LandingView.usd(d) : LandingView.inr(d);
        };
        String paidAt = o.str("updated_at", o.str("created_at", ""));
        java.time.format.DateTimeFormatter shown = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", java.util.Locale.ENGLISH);
        String paidOn;
        try {
            paidOn = java.time.LocalDateTime.parse(paidAt.replace(' ', 'T')).format(shown) + " IST";
        } catch (RuntimeException e) {
            paidOn = paidAt;
        }
        String address = java.util.stream.Stream.of(o.str("billing_address"), o.str("billing_city"), o.str("billing_state"), o.str("billing_zipcode"), o.str("billing_country"))
                .filter(x -> x != null && !x.isBlank()).collect(java.util.stream.Collectors.joining(", "));
        Map<String, Object> model = new java.util.LinkedHashMap<>();
        model.put("baseUrl", base);
        model.put("first", first);
        model.put("course", course.get("title"));
        model.put("kind", course.get("kind"));
        model.put("artist", artist == null ? null : artist.str("name"));
        model.put("starts", course.get("starts"));
        model.put("duration", course.get("duration"));
        model.put("orderNumber", o.str("order_number"));
        model.put("amount", amount);
        model.put("coupon", o.str("coupon_code"));
        model.put("paidWith", paidWith);
        model.put("courseUrl", courseUrl);
        model.put("autoLogin", access != null);
        model.put("loginUrl", loginUrl);
        model.put("paidOn", paidOn);
        model.put("billedTo", name.isEmpty() ? email : name);
        model.put("billedEmail", email);
        model.put("billedAddress", address.isEmpty() ? null : address);
        model.put("listPrice", money.apply("list_amount"));
        model.put("discount", money.apply("discount_amount"));
        model.put("tax", money.apply("tax_amount"));
        model.put("paymentRef", o.str("payment_id"));
        model.put("test", o.lng("is_test", 0) == 1);
        model.put("studioUrl", base + "/login?return_to=%2Fdashboard");
        model.put("email", email);
        model.put("phone", o.str("phone_number") == null ? null : com.artistikcity.auth.PhoneNumbers.mask(o.str("phone_number")));
        model.put("year", String.valueOf(java.time.Year.now().getValue()));
        return model;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private CourseAccessLink courseAccess;

    private static jakarta.servlet.http.HttpServletRequest currentRequest() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        return attrs instanceof org.springframework.web.context.request.ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    public void onPaid(long orderId) {
        Row o = db.first("select id, landing_page_id, landing_visitor, payment_id, payment_option, payment_method, payment_provider, utm_source, utm_medium, utm_campaign, is_test"
                + " from orders where id = ? and landing_page_id is not null", orderId);
        if (o == null) {
            return;
        }
        try {
            coupons.redeem(orderId);
        } catch (RuntimeException e) {
            log.warn("Coupon redemption for order {} failed: {}", orderId, e.toString());
        }
        String chosen = o.str("payment_method");
        String reported = null;
        String paymentId = o.str("payment_id", "");
        if ("razorpay".equals(o.str("payment_provider")) && gateways.razorpayConfigured() && paymentId.startsWith("pay_")) {
            try {
                reported = gateways.razorpayPaymentMethod(paymentId);
            } catch (Exception e) {
                log.info("Could not read the payment method of {}: {}", paymentId, e.toString());
            }
        }
        if (reported != null) {
            String mapped = switch (reported) {
                case "cardless_emi", "emi" -> "emi";
                default -> reported;
            };
            if (chosen != null && !LandingPayments.razorpayMethodsFor(chosen).contains(reported)) {
                auditLog.write(AuditLog.entry(AuditLog.Type.UPDATE, "landing payment method outside the page's settings").actor(AuditLog.Actor.system())
                        .resource("order", orderId).summary("Chosen " + chosen + ", Razorpay reports " + reported + " (" + paymentId + ")").outcome("FAIL"));
            }
            db.update("update orders set payment_method = ? where id = ?", mapped, orderId);
            chosen = mapped;
        }
        String visitor = o.str("landing_visitor");
        // simulated payments on a real server (test buyers, simulated Razorpay) stay out of the funnel, like they stay out of
        // revenue; a development server's own test payments still count, so its analytics screens have data
        if (visitor != null && visitor.length() == 32 && !"Simulated (test)".equals(o.str("payment_option"))) {
            db.insertReturningId("landing_events", Row.of("landing_page_id", o.get("landing_page_id"), "event", "PURCHASE", "visitor", visitor, "method", chosen,
                    "provider", o.get("payment_provider"), "order_id", orderId, "utm_source", o.get("utm_source"), "utm_medium", o.get("utm_medium"),
                    "utm_campaign", o.get("utm_campaign"), "device", null, "created_at", LocalDateTime.now()));
        }
    }

    /** The amount the gateway must have charged for a landing order: what was fixed on the order when payment started. */
    public static java.math.BigDecimal expected(Map<String, Object> order) {
        Object p = order.get("price");
        return p == null ? null : new java.math.BigDecimal(String.valueOf(p)).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
