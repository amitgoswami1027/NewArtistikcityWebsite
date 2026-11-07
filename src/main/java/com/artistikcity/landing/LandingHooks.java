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

    public LandingHooks(Db db, LandingCoupons coupons, CommissionPayments gateways, AuditLog auditLog) {
        this.db = db;
        this.coupons = coupons;
        this.gateways = gateways;
        this.auditLog = auditLog;
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
