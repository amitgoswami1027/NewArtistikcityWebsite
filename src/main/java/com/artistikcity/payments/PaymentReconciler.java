package com.artistikcity.payments;

import com.artistikcity.commission.CommissionPayments;
import com.artistikcity.config.Settings;
import com.artistikcity.marketplace.MarketplaceController;
import com.artistikcity.marketplace.MarketplaceService;
import com.artistikcity.security.AuditLog;
import com.artistikcity.service.Alerts;
import com.artistikcity.shop.PurchaseController;
import com.artistikcity.shop.ShopOrderService;
import com.artistikcity.web.CheckoutController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Payment reconciliation: every {@code app.payments.reconcile.every-minutes} the site asks Razorpay / PayPal about
 * orders that are still waiting for a confirmation (older than {@code min-age-minutes}, younger than
 * {@code max-age-days}). A payment whose browser callback <em>and</em> webhook were both lost is still found and the
 * order completed through the same idempotent code as the webhook. A match later than {@code alert-after-minutes}
 * alerts the staff. Each pass also queues marketplace / shop refunds and finds courses that were paid twice.
 *
 * <p>Only verified gateway answers complete an order: a captured payment for exactly the expected amount and
 * currency. An authorized-but-not-captured Razorpay payment is captured first; an APPROVED PayPal order is captured.</p>
 */
@Service
public class PaymentReconciler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciler.class);

    private final JdbcTemplate jdbc;
    private final CommissionPayments gateways;
    private final Settings settings;
    private final RefundQueue refunds;
    private final AuditLog auditLog;
    private final Alerts alerts;
    private final ObjectProvider<CheckoutController> checkout;
    private final ObjectProvider<MarketplaceService> market;
    private final ObjectProvider<MarketplaceController> marketController;
    private final ObjectProvider<ShopOrderService> shop;
    private final ObjectProvider<PurchaseController> purchase;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile LocalDateTime lastRun = LocalDateTime.MIN;

    public PaymentReconciler(JdbcTemplate jdbc, CommissionPayments gateways, Settings settings, RefundQueue refunds, AuditLog auditLog, Alerts alerts,
                             ObjectProvider<CheckoutController> checkout, ObjectProvider<MarketplaceService> market,
                             ObjectProvider<MarketplaceController> marketController, ObjectProvider<ShopOrderService> shop,
                             ObjectProvider<PurchaseController> purchase) {
        this.jdbc = jdbc;
        this.gateways = gateways;
        this.settings = settings;
        this.refunds = refunds;
        this.auditLog = auditLog;
        this.alerts = alerts;
        this.checkout = checkout;
        this.market = market;
        this.marketController = marketController;
        this.shop = shop;
        this.purchase = purchase;
    }

    /** Summary of one pass. */
    public record Result(int checked, int completed, int refundsQueued, int duplicates, List<String> notes) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("checked", checked);
            m.put("completed", completed);
            m.put("refundsQueued", refundsQueued);
            m.put("duplicates", duplicates);
            m.put("notes", notes);
            return m;
        }
    }

    /** Ticks every minute; does a pass when the configured interval has passed. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void tick() {
        if (!settings.reconcileEnabled()) return;
        if (Duration.between(lastRun, LocalDateTime.now()).toMinutes() < settings.reconcileEveryMinutes()) return;
        try {
            run();
        } catch (RuntimeException e) {
            log.error("Payment reconciliation pass failed", e);
        }
    }

    /** One pass (also from Admin > Refunds "Check gateways now"). Never runs twice at once. */
    public Result run() {
        if (!running.compareAndSet(false, true)) return new Result(0, 0, 0, 0, List.of("A check is already running."));
        lastRun = LocalDateTime.now();
        int[] n = new int[2];
        List<String> notes = new ArrayList<>();
        try {
            LocalDateTime youngest = LocalDateTime.now().minusMinutes(settings.reconcileMinAgeMinutes());
            LocalDateTime oldest = LocalDateTime.now().minusDays(settings.reconcileMaxAgeDays());
            courseOrders(youngest, oldest, n, notes);
            marketplaceOrders(youngest, oldest, n, notes);
            shopOrders(youngest, oldest, n, notes);
            int dup = duplicateCourses(notes);
            int queued = refunds.syncFromOrders();
            if (n[1] > 0 || queued > 0 || dup > 0) log.info("Reconciliation: {} checked, {} completed, {} refunds queued, {} duplicates", n[0], n[1], queued, dup);
            return new Result(n[0], n[1], queued, dup, notes);
        } finally {
            running.set(false);
        }
    }

    // ------------------------------------------------------------------ course orders

    private void courseOrders(LocalDateTime youngest, LocalDateTime oldest, int[] n, List<String> notes) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, order_number, gateway_ref, price_type, price, created_at from orders"
                + " where payment_status in (0, 2) and is_test = 0 and gateway_ref is not null and created_at <= ? and created_at >= ? order by id limit 200", ts(youngest), ts(oldest));
        for (Map<String, Object> o : rows) {
            n[0]++;
            String ref = String.valueOf(o.get("gateway_ref"));
            long id = ((Number) o.get("id")).longValue();
            boolean usd = "usd".equalsIgnoreCase(String.valueOf(o.get("price_type")));
            BigDecimal expected = RefundQueue.dec(o.get("price"));
            try {
                Match m = usd ? paypal(ref, expected, "USD") : razorpay(ref, expected);
                if (m == null) continue;
                CheckoutController c = checkout.getIfAvailable();
                if (c == null) continue;
                c.completeFromGateway(id, usd ? "PayPal" : "RazorPay", m.paymentId(), Map.of("via", "reconciler", "gateway_ref", ref));
                record("COURSE", String.valueOf(id), usd ? "PAYPAL" : "RAZORPAY", m.paymentId(), "PAID", o.get("created_at"), null);
                n[1]++;
                notes.add("Course order " + o.get("order_number") + " completed (" + m.paymentId() + ")");
            } catch (Exception e) {
                record("COURSE", String.valueOf(id), usd ? "PAYPAL" : "RAZORPAY", null, "ERROR", o.get("created_at"), e.getMessage());
                log.warn("Reconcile course order {} failed: {}", id, e.toString());
            }
        }
    }

    // ------------------------------------------------------------------ marketplace / shop

    private void marketplaceOrders(LocalDateTime youngest, LocalDateTime oldest, int[] n, List<String> notes) {
        MarketplaceService svc = market.getIfAvailable();
        if (svc == null) return;
        for (Map<String, Object> o : jdbc.queryForList("select order_id, gateway, provider_ref, amount, currency, created_at from marketplace_orders"
                + " where status in ('PAYMENT_PENDING', 'EXPIRED', 'CANCELLED') and transaction_id is null and provider_ref is not null and gateway in ('RAZORPAY', 'PAYPAL')"
                + " and created_at <= ? and created_at >= ? limit 200", ts(youngest), ts(oldest))) {
            n[0]++;
            String orderId = String.valueOf(o.get("order_id"));
            try {
                Match m = match(o);
                if (m == null) continue;
                MarketplaceService.Outcome out = svc.finalizeSale(orderId, m.paymentId());
                MarketplaceController mc = marketController.getIfAvailable();
                if (mc != null) mc.afterSale(orderId, out);
                record("MARKETPLACE", orderId, String.valueOf(o.get("gateway")), m.paymentId(), out.name(), o.get("created_at"), null);
                n[1]++;
                notes.add("Marketplace order " + orderId + ": " + out.name());
            } catch (Exception e) {
                record("MARKETPLACE", orderId, String.valueOf(o.get("gateway")), null, "ERROR", o.get("created_at"), e.getMessage());
                log.warn("Reconcile marketplace order {} failed: {}", orderId, e.toString());
            }
        }
    }

    private void shopOrders(LocalDateTime youngest, LocalDateTime oldest, int[] n, List<String> notes) {
        ShopOrderService svc = shop.getIfAvailable();
        if (svc == null) return;
        for (Map<String, Object> o : jdbc.queryForList("select order_id, gateway, provider_ref, amount, currency, created_at from shop_orders"
                + " where status in ('PAYMENT_PENDING', 'EXPIRED') and transaction_id is null and provider_ref is not null and gateway in ('RAZORPAY', 'PAYPAL')"
                + " and created_at <= ? and created_at >= ? limit 200", ts(youngest), ts(oldest))) {
            n[0]++;
            String orderId = String.valueOf(o.get("order_id"));
            try {
                Match m = match(o);
                if (m == null) continue;
                ShopOrderService.Outcome out = svc.finalizeSale(orderId, m.paymentId());
                PurchaseController pc = purchase.getIfAvailable();
                if (pc != null) pc.afterSale(null, orderId, out);
                record("SHOP", orderId, String.valueOf(o.get("gateway")), m.paymentId(), out.name(), o.get("created_at"), null);
                n[1]++;
                notes.add("Shop order " + orderId + ": " + out.name());
            } catch (Exception e) {
                record("SHOP", orderId, String.valueOf(o.get("gateway")), null, "ERROR", o.get("created_at"), e.getMessage());
                log.warn("Reconcile shop order {} failed: {}", orderId, e.toString());
            }
        }
    }

    private Match match(Map<String, Object> o) throws Exception {
        String ref = String.valueOf(o.get("provider_ref"));
        BigDecimal amount = RefundQueue.dec(o.get("amount"));
        return "PAYPAL".equals(o.get("gateway")) ? paypal(ref, amount, String.valueOf(o.get("currency"))) : razorpay(ref, amount);
    }

    // ------------------------------------------------------------------ gateways

    record Match(String paymentId) {
    }

    /** A captured Razorpay payment for exactly this amount (an authorized one is captured first). */
    Match razorpay(String razorpayOrderId, BigDecimal amountInr) throws Exception {
        if (!gateways.razorpayConfigured()) return null;
        long paise = amountInr.movePointRight(2).longValue();
        for (CommissionPayments.RazorpayPayment p : gateways.razorpayOrderPayments(razorpayOrderId)) {
            if (p.amountPaise() != paise || !"INR".equalsIgnoreCase(p.currency())) {
                if ("captured".equals(p.status())) {
                    record("GATEWAY", razorpayOrderId, "RAZORPAY", p.id(), "AMOUNT_MISMATCH", null, "expected " + paise + " got " + p.amountPaise());
                }
                continue;
            }
            if ("captured".equals(p.status())) return new Match(p.id());
            if ("authorized".equals(p.status()) && gateways.razorpayCapture(p.id(), paise)) return new Match(p.id());
        }
        return null;
    }

    /** A completed PayPal order for this amount (an APPROVED one is captured now). */
    Match paypal(String paypalOrderId, BigDecimal amount, String currency) throws Exception {
        if (!gateways.paypalConfigured()) return null;
        String[] o = gateways.paypalOrder(paypalOrderId);
        String status = o[0];
        if ("APPROVED".equals(status)) {
            String[] cap = gateways.paypalCapture(paypalOrderId);
            if (!"COMPLETED".equals(cap[0])) return null;
            o = new String[]{cap[0], cap[1], cap[2] == null ? o[2] : cap[2], cap[3] == null ? o[3] : cap[3]};
            status = cap[0];
        }
        if (!"COMPLETED".equals(status) || o[2] == null) return null;
        if (new BigDecimal(o[2]).compareTo(amount) != 0 || !currency.equalsIgnoreCase(o[3])) {
            record("GATEWAY", paypalOrderId, "PAYPAL", o[1], "AMOUNT_MISMATCH", null, "expected " + amount + " " + currency + " got " + o[2] + " " + o[3]);
            return null;
        }
        return new Match(o[1] == null ? paypalOrderId : o[1]);
    }

    // ------------------------------------------------------------------ courses paid twice

    /**
     * A student with two paid orders for the same course (two tabs, two payments that both went through): the
     * newer order becomes "refund due" (4) and its payment is queued for refund. Free and manual orders are skipped.
     */
    int duplicateCourses(List<String> notes) {
        int found = 0;
        for (Map<String, Object> d : jdbc.queryForList("select user_id, course_id, min(id) as keep_id from orders where payment_status = 1"
                + " group by user_id, course_id having count(*) > 1 limit 100")) {
            for (Map<String, Object> o : jdbc.queryForList("select o.id, o.order_number, o.payment_id, o.payment_option, o.price, o.price_type, o.billing_email"
                    + " from orders o where o.user_id = ? and o.course_id = ? and o.payment_status = 1 and o.id <> ?", d.get("user_id"), d.get("course_id"), d.get("keep_id"))) {
                String option = String.valueOf(o.get("payment_option"));
                if (option.startsWith("Free") || option.startsWith("Manual")) continue;
                CheckoutController c = checkout.getIfAvailable();
                if (c != null && c.flagDuplicate(((Number) o.get("id")).longValue(), ((Number) d.get("keep_id")).longValue())) {
                    found++;
                    notes.add("Course order " + o.get("order_number") + " was a second payment for the same course: refund queued");
                }
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ log + alert

    private void record(String source, String ref, String gateway, String paymentRef, String outcome, Object createdAt, String detail) {
        int late = 0;
        if (createdAt != null) {
            LocalDateTime c = createdAt instanceof Timestamp t ? t.toLocalDateTime() : createdAt instanceof LocalDateTime l ? l : null;
            if (c != null) late = (int) Math.max(0, Duration.between(c, LocalDateTime.now()).toMinutes());
        }
        try {
            jdbc.update("insert into payment_reconciliations (source, source_ref, gateway, payment_ref, outcome, minutes_late, detail, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                    source, ref, gateway, paymentRef, outcome, late, detail == null ? null : detail.length() > 500 ? detail.substring(0, 500) : detail, ts(LocalDateTime.now()));
        } catch (RuntimeException e) {
            log.warn("Could not record reconciliation of {} {}", source, ref, e);
        }
        if (!"ERROR".equals(outcome)) {
            auditLog.write(AuditLog.entry(AuditLog.Type.UPDATE, "payment reconciled").actor(AuditLog.Actor.system()).resource("order:" + source, ref)
                    .summary(outcome + " via " + gateway + (paymentRef == null ? "" : " " + paymentRef) + (late > 0 ? " · " + late + " min after the order" : "")
                            + (detail == null ? "" : " · " + detail)).outcome("AMOUNT_MISMATCH".equals(outcome) ? "FAIL" : "OK"));
        }
        if ("AMOUNT_MISMATCH".equals(outcome) || (!"ERROR".equals(outcome) && late >= settings.reconcileAlertAfterMinutes())) {
            alerts.send("reconcile:" + source + ":" + ref, "AMOUNT_MISMATCH".equals(outcome) ? "A payment did not match its order" : "A payment was matched late",
                    List.of(source + " order " + ref + " · " + gateway + (paymentRef == null ? "" : " " + paymentRef),
                            "Outcome: " + outcome + (late > 0 ? ", " + late + " minutes after the order was placed" : ""),
                            detail == null ? "The browser callback and the webhook were both missed; the scheduled check found it." : detail),
                    "/admin/payments/refunds?tab=reconcile");
        }
    }

    private static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }
}
