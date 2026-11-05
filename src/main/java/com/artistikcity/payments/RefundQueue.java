package com.artistikcity.payments;

import com.artistikcity.commission.CommissionPayments;
import com.artistikcity.security.AuditLog;
import com.artistikcity.service.Alerts;
import com.artistikcity.support.DbErrors;
import com.artistikcity.support.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * The refunds queue ({@code payment_refunds}). Payments that must be given back are flagged here exactly once
 * (unique per source order + payment); staff then confirm each one in Admin > Sales > Refunds, which calls the
 * gateway's refund API. Nothing is refunded without a person pressing the button.
 *
 * <p>Sources: COURSE (a course paid twice, or a cancelled live course), MARKETPLACE (an original that sold to someone
 * else first: {@code marketplace_orders.status = REFUND_REQUIRED}), SHOP (lines that could not be supplied:
 * {@code shop_orders.refund_due_inr > 0} or the whole order REFUND_REQUIRED). {@link #syncFromOrders()} picks the
 * marketplace and shop ones up from their tables, so those services need no change.</p>
 */
@Service
public class RefundQueue {

    private static final Logger log = LoggerFactory.getLogger(RefundQueue.class);

    public static final List<String> REASONS = List.of("DUPLICATE_PAYMENT", "SOLD_OUT", "SOLD_TO_OTHER", "NOT_SUPPLIED", "COURSE_CANCELLED", "OTHER");

    private final JdbcTemplate jdbc;
    private final CommissionPayments gateways;
    private final AuditLog auditLog;
    private final Alerts alerts;

    public RefundQueue(JdbcTemplate jdbc, CommissionPayments gateways, AuditLog auditLog, Alerts alerts) {
        this.jdbc = jdbc;
        this.gateways = gateways;
        this.auditLog = auditLog;
        this.alerts = alerts;
    }

    public record Flag(String source, String sourceRef, String paymentRef, String gateway, BigDecimal amount, String currency,
                       String reason, String detail, String buyerEmail, Long userId) {
    }

    /** Adds a refund to the queue (no-op when this payment is already queued). Returns true when it is new. */
    public boolean flag(Flag f) {
        try {
            jdbc.update("insert into payment_refunds (source, source_ref, payment_ref, gateway, amount, currency, reason, detail, buyer_email, user_id,"
                            + " status, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)",
                    f.source(), f.sourceRef(), f.paymentRef() == null ? "" : f.paymentRef(), f.gateway(), f.amount().setScale(2, RoundingMode.HALF_UP),
                    f.currency().toUpperCase(), f.reason(), cut(f.detail(), 500), f.buyerEmail(), f.userId(), ts(LocalDateTime.now()));
        } catch (RuntimeException e) {
            if (DbErrors.isDuplicateKey(e)) return false;
            log.error("Could not queue refund for {} {}", f.source(), f.sourceRef(), e);
            return false;
        }
        auditLog.write(AuditLog.entry(AuditLog.Type.CREATE, "refund queued").actor(AuditLog.Actor.system()).resource("refund:" + f.source(), f.sourceRef())
                .summary(f.reason() + " · " + f.currency() + " " + f.amount() + (f.detail() == null ? "" : " · " + f.detail())));
        alerts.send("refund:" + f.source() + ":" + f.sourceRef(), "A refund is waiting for a decision",
                List.of(f.source() + " order " + f.sourceRef() + ": " + f.currency() + " " + f.amount(),
                        "Reason: " + f.reason().replace('_', ' ').toLowerCase() + (f.detail() == null ? "" : " (" + f.detail() + ")"),
                        "Buyer: " + (f.buyerEmail() == null ? "unknown" : f.buyerEmail()),
                        "Nothing has been refunded yet. Open the refunds queue to confirm or dismiss it."),
                "/admin/payments/refunds");
        return true;
    }

    /** Queues the marketplace and shop refunds their services marked; returns how many were new. */
    public int syncFromOrders() {
        int added = 0;
        for (Map<String, Object> o : jdbc.queryForList("select order_id, gateway, transaction_id, amount, currency, buyer_email, user_id from marketplace_orders"
                + " where status = 'REFUND_REQUIRED' and transaction_id is not null")) {
            if (flag(new Flag("MARKETPLACE", str(o.get("order_id")), str(o.get("transaction_id")), str(o.get("gateway")), dec(o.get("amount")),
                    str(o.get("currency")), "SOLD_TO_OTHER", "The original was sold to another collector first", str(o.get("buyer_email")), lng(o.get("user_id"))))) added++;
        }
        for (Map<String, Object> o : jdbc.queryForList("select order_id, gateway, transaction_id, amount, currency, total_inr, refund_due_inr, status, buyer_email, user_id"
                + " from shop_orders where transaction_id is not null and (status = 'REFUND_REQUIRED' or refund_due_inr > 0)")) {
            BigDecimal amount = dec(o.get("amount"));
            String detail = "The whole order could not be supplied";
            if (!"REFUND_REQUIRED".equals(o.get("status"))) {
                // part of the order: the share of what was paid, in the currency it was paid in
                BigDecimal total = dec(o.get("total_inr"));
                BigDecimal due = dec(o.get("refund_due_inr"));
                amount = total.signum() == 0 ? due : amount.multiply(due).divide(total, 2, RoundingMode.HALF_UP);
                detail = "Lines that could not be supplied (₹" + due.toPlainString() + ")";
            }
            if (flag(new Flag("SHOP", str(o.get("order_id")), str(o.get("transaction_id")), str(o.get("gateway")), amount, str(o.get("currency")),
                    "NOT_SUPPLIED", detail, str(o.get("buyer_email")), lng(o.get("user_id"))))) added++;
        }
        return added;
    }

    /** Result of a staff decision. */
    public record Decision(boolean ok, String status, String message) {
    }

    /**
     * Staff confirmed: refund through the gateway, then mark the source order refunded. A refund already being
     * processed (double click, two staff at once) is refused: the row is claimed with a conditional update first.
     */
    public Decision refund(long id, Row admin, String note) {
        Map<String, Object> r = one(id);
        if (r == null) return new Decision(false, "MISSING", "That refund does not exist.");
        int claimed = jdbc.update("update payment_refunds set status = 'PROCESSING', decided_by = ?, decided_at = ?, note = ? where id = ? and status in ('PENDING', 'FAILED')",
                admin.get("id"), ts(LocalDateTime.now()), cut(note, 500), id);
        if (claimed == 0) return new Decision(false, str(r.get("status")), "This refund was already handled (" + str(r.get("status")).toLowerCase() + ").");
        String gateway = str(r.get("gateway"));
        String paymentRef = str(r.get("payment_ref"));
        BigDecimal amount = dec(r.get("amount"));
        String refundId;
        try {
            refundId = switch (gateway) {
                case "RAZORPAY" -> gateways.razorpayRefund(paymentRef, amount.movePointRight(2).longValueExact(),
                        Map.of("source", str(r.get("source")), "order", str(r.get("source_ref")), "reason", str(r.get("reason"))));
                case "PAYPAL" -> gateways.paypalRefund(paymentRef, amount, str(r.get("currency")), "Refund for ArtistikCity order " + r.get("source_ref"))[0];
                case "TEST", "MANUAL" -> {
                    if (!"MANUAL".equals(gateway) && !gateways.testMode()) throw new IllegalStateException("Test payments can only be refunded in test mode.");
                    yield ("MANUAL".equals(gateway) ? "manual-" : "test-refund-") + id;
                }
                default -> throw new IllegalStateException("Unknown gateway " + gateway);
            };
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            jdbc.update("update payment_refunds set status = 'FAILED', error = ? where id = ?", cut(msg, 500), id);
            auditLog.write(AuditLog.entry(AuditLog.Type.UPDATE, "refund failed").actor(AuditLog.Actor.admin(admin)).resource("refund", id)
                    .summary(cut(msg, 300)).outcome("FAIL"));
            log.warn("Refund {} failed at {}: {}", id, gateway, msg);
            return new Decision(false, "FAILED", "The payment provider refused the refund: " + cut(msg, 200));
        }
        Map<String, Object> before = one(id);
        jdbc.update("update payment_refunds set status = 'REFUNDED', gateway_refund_id = ?, error = null where id = ?", refundId, id);
        settleSource(str(r.get("source")), str(r.get("source_ref")), note);
        auditLog.write(AuditLog.entry(AuditLog.Type.UPDATE, "refund sent").actor(AuditLog.Actor.admin(admin)).resource("refund", id)
                .summary(r.get("source") + " " + r.get("source_ref") + " · " + r.get("currency") + " " + amount + " · " + refundId)
                .before(before).after(one(id)));
        return new Decision(true, "REFUNDED", "Refund sent: " + r.get("currency") + " " + amount.toPlainString() + " (" + refundId + ").");
    }

    /** Staff decided no money goes back (e.g. refunded by hand elsewhere, or the buyer agreed to a swap). */
    public Decision dismiss(long id, Row admin, String note) {
        if (note == null || note.isBlank()) return new Decision(false, "NOTE", "Say why no refund is sent.");
        int n = jdbc.update("update payment_refunds set status = 'DISMISSED', decided_by = ?, decided_at = ?, note = ? where id = ? and status in ('PENDING', 'FAILED')",
                admin.get("id"), ts(LocalDateTime.now()), cut(note, 500), id);
        if (n == 0) return new Decision(false, "DONE", "This refund was already handled.");
        auditLog.write(AuditLog.entry(AuditLog.Type.UPDATE, "refund dismissed").actor(AuditLog.Actor.admin(admin)).resource("refund", id).summary(note));
        return new Decision(true, "DISMISSED", "Marked as not refunded.");
    }

    /** The source order follows the refund: course order -> refunded (3), original -> REFUNDED, shop lines -> REFUNDED. */
    private void settleSource(String source, String ref, String note) {
        Timestamp now = ts(LocalDateTime.now());
        switch (source) {
            case "COURSE" -> {
                jdbc.update("update orders set payment_status = 3, updated_at = ? where id = ? and payment_status in (1, 4)", now, ref);
                // no paid order left for this course: the seat goes back (the waitlist is invited by the seat janitor)
                jdbc.update("update seat_holds h join orders o on o.id = ? set h.status = 'RELEASED', h.updated_at = ?"
                        + " where h.course_id = o.course_id and h.user_id = o.user_id and h.status = 'PAID'"
                        + " and not exists (select 1 from orders p where p.course_id = o.course_id and p.user_id = o.user_id and p.payment_status = 1)", ref, now);
                jdbc.update("update enrollments e join orders o on o.id = ? set e.status = 'Refunded', e.updated_at = ?"
                        + " where e.course_id = o.course_id and e.user_id = o.user_id"
                        + " and not exists (select 1 from orders p where p.course_id = o.course_id and p.user_id = o.user_id and p.payment_status = 1)", ref, now);
            }
            case "MARKETPLACE" -> jdbc.update("update marketplace_orders set status = 'REFUNDED', updated_at = ? where order_id = ? and status = 'REFUND_REQUIRED'", now, ref);
            case "SHOP" -> {
                jdbc.update("update shop_orders set status = case when status = 'REFUND_REQUIRED' then 'REFUNDED' else status end, refund_due_inr = 0,"
                        + " staff_notes = coalesce(?, staff_notes), updated_at = ? where order_id = ?", note == null || note.isBlank() ? null : note, now, ref);
                jdbc.update("update shop_order_items set status = 'REFUNDED', download_token = null where order_id = ? and status = 'REFUND_REQUIRED'", ref);
            }
            default -> { }
        }
    }

    public Map<String, Object> one(long id) {
        List<Map<String, Object>> r = jdbc.queryForList("select * from payment_refunds where id = ?", id);
        return r.isEmpty() ? null : r.get(0);
    }

    static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }

    static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    static Long lng(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }

    public static BigDecimal dec(Object o) {
        if (o instanceof BigDecimal b) return b;
        if (o == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String cut(String s, int n) {
        return s == null ? null : s.length() > n ? s.substring(0, n) : s;
    }
}
