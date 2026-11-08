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
     * The thank-you e-mail of a landing page purchase: the ArtistikCity logo, congratulations, the course, the payment,
     * and "Go to my course" (the sign-in page, then the course: a code to the buyer's mobile or e-mail, no password).
     * Returns false when the order isn't a landing order (the caller then sends the standard confirmation).
     */
    public boolean sendThankYou(long orderId, String email, String baseUrl) {
        Row o = db.first("select o.id, o.order_number, o.course_id, o.price, o.price_type, o.payment_method, o.payment_option, o.is_test, o.billing_name,"
                + " o.list_amount, o.discount_amount, o.coupon_code, o.created_at, u.name as user_name, u.phone_number"
                + " from orders o left join users u on u.id = o.user_id where o.id = ? and o.landing_page_id is not null", orderId);
        if (o == null) {
            return false;
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
        String courseUrl = base + "/login?return_to=" + java.net.URLEncoder.encode(back, java.nio.charset.StandardCharsets.UTF_8);
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
        model.put("studioUrl", base + "/login?return_to=%2Fdashboard");
        model.put("email", email);
        model.put("phone", o.str("phone_number") == null ? null : com.artistikcity.auth.PhoneNumbers.mask(o.str("phone_number")));
        model.put("year", String.valueOf(java.time.Year.now().getValue()));
        return mailer.send(email, "Congratulations! You're enrolled in " + course.get("title"), "emails/landing-thank-you", model);
    }

    public void onPaid(long orderId) {
        Row o = db.first("select id, landing_page_id, landing_visitor, payment_id, payment_option, payment_method, payment_provider, utm_source, utm_medium, utm_campaign"
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
        if (visitor != null && visitor.length() == 32) {
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
