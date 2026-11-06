package com.artistikcity.ledger;

import com.artistikcity.config.Settings;
import com.artistikcity.security.AuditLog;
import com.artistikcity.service.Alerts;
import com.artistikcity.support.DbErrors;
import com.artistikcity.support.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The escrow ledger ({@code ledger_entries}, append-only). Money a buyer pays sits in ESCROW; each sale is split into
 * the platform's commission (PLATFORM, {@code app.ledger.commission-percent}) and the artist's share (ARTIST), which
 * becomes payable {@code app.ledger.payout-after-days} after the sale. Refunds and lost disputes post reversing rows;
 * a payout posts a negative ARTIST row. Nothing is ever edited: every posting has an {@code idem_key}, so running
 * {@link #sync()} again never double-counts.
 *
 * <p>Payees: a student artist ({@code paintings.artist_user_id}, source STUDENT) is always paid; a studio artist
 * ({@code artist_admin_id}) only with {@code app.ledger.pay-studio-artists}; otherwise the whole sale is platform
 * revenue. Course sales are platform revenue. An open dispute on an order keeps its artist share out of payouts.</p>
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);
    private static final List<String> PAID_STATES = List.of("PAID", "PACKED", "SHIPPED", "DELIVERED");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Settings settings;
    private final AuditLog auditLog;
    private final Alerts alerts;

    public LedgerService(JdbcTemplate jdbc, PlatformTransactionManager tm, Settings settings, AuditLog auditLog, Alerts alerts) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(tm);
        this.settings = settings;
        this.auditLog = auditLog;
        this.alerts = alerts;
    }

    // ------------------------------------------------------------------ posting

    record Payee(String type, Long id, String name) {
        static final Payee PLATFORM = new Payee(null, null, null);

        boolean platform() {
            return type == null;
        }
    }

    private boolean post(String idem, String type, String account, String source, String ref, Payee payee, BigDecimal amount,
                         LocalDateTime availableAt, Long payoutId, String memo, Long createdBy) {
        if (amount.signum() == 0) return false;
        try {
            jdbc.update("insert into ledger_entries (idem_key, entry_type, account, source, source_ref, payee_type, payee_id, payee_name, amount, available_at,"
                            + " payout_id, memo, created_by, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    idem, type, account, source, ref, payee.type(), payee.id(), payee.name(), amount.setScale(2, RoundingMode.HALF_UP),
                    availableAt == null ? null : ts(availableAt), payoutId, memo == null ? null : memo.length() > 300 ? memo.substring(0, 300) : memo,
                    createdBy, ts(LocalDateTime.now()));
            return true;
        } catch (RuntimeException e) {
            if (DbErrors.isDuplicateKey(e)) return false;
            throw e;
        }
    }

    /** A sale: gross into escrow, split into commission and artist share (all on one idem prefix). Returns true when new. */
    private boolean postSale(String prefix, String source, String ref, Payee payee, BigDecimal gross, LocalDateTime soldAt, String memo) {
        if (gross.signum() <= 0) return false;
        boolean added = post(prefix + ":SALE", "SALE", "ESCROW", source, ref, Payee.PLATFORM, gross, null, null, memo, null);
        if (!added) return false;
        BigDecimal commission = payee.platform() ? gross
                : gross.multiply(settings.commissionPercent()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        post(prefix + ":COMMISSION", "COMMISSION", "PLATFORM", source, ref, Payee.PLATFORM, commission, null, null,
                payee.platform() ? "Platform sale" : settings.commissionPercent().stripTrailingZeros().toPlainString() + "% commission", null);
        if (!payee.platform()) {
            post(prefix + ":ARTIST", "ARTIST_SHARE", "ARTIST", source, ref, payee, gross.subtract(commission),
                    (soldAt == null ? LocalDateTime.now() : soldAt).plusDays(settings.payoutAfterDays()), null, memo, null);
        }
        return true;
    }

    /** Reverses everything posted under a prefix (refund or lost dispute), once per kind. */
    private boolean reverse(String prefix, String kind, String type, String memo, Long by) {
        List<Map<String, Object>> rows = jdbc.queryForList("select entry_type, account, source, source_ref, payee_type, payee_id, payee_name, amount from ledger_entries"
                + " where idem_key in (?, ?, ?)", prefix + ":SALE", prefix + ":COMMISSION", prefix + ":ARTIST");
        boolean any = false;
        for (Map<String, Object> r : rows) {
            Payee p = r.get("payee_type") == null ? Payee.PLATFORM
                    : new Payee(String.valueOf(r.get("payee_type")), ((Number) r.get("payee_id")).longValue(), (String) r.get("payee_name"));
            any |= post(prefix + ":" + kind + ":" + r.get("account"), type, String.valueOf(r.get("account")), String.valueOf(r.get("source")),
                    String.valueOf(r.get("source_ref")), p, ((BigDecimal) r.get("amount")).negate(), LocalDateTime.now(), null, memo, by);
        }
        return any;
    }

    private Payee payeeOf(Map<String, Object> painting) {
        Object user = painting.get("artist_user_id");
        String name = painting.get("artist_name") == null ? null : String.valueOf(painting.get("artist_name"));
        if ("STUDENT".equals(painting.get("source")) && user instanceof Number n) return new Payee("USER", n.longValue(), name);
        if (user instanceof Number n) return new Payee("USER", n.longValue(), name);
        if (settings.payStudioArtists() && painting.get("artist_admin_id") instanceof Number a) return new Payee("ADMIN", a.longValue(), name);
        return Payee.PLATFORM;
    }

    /** Posts every sale and refund not yet in the ledger. Idempotent. Returns how many postings were new. */
    public int sync() {
        int n = 0;
        // originals
        for (Map<String, Object> o : jdbc.queryForList("select o.order_id, o.price_inr, o.paid_at, o.status, p.title, p.source, p.artist_user_id, p.artist_admin_id, p.artist_name"
                + " from marketplace_orders o join paintings p on p.id = o.painting_id where o.status in ('PAID', 'PACKED', 'SHIPPED', 'DELIVERED', 'REFUNDED') and o.is_test = 0")) {
            String id = String.valueOf(o.get("order_id"));
            String prefix = "MKT:" + id;
            if ("REFUNDED".equals(o.get("status"))) {
                // only a sale that was posted is reversed (a payment refunded because the piece sold elsewhere never was one)
                if (ledgerHas(prefix + ":SALE") && reverse(prefix, "REFUND", "REFUND", "Refunded", null)) n++;
            } else if (postSale(prefix, "MARKETPLACE", id, payeeOf(o), dec(o.get("price_inr")), ldt(o.get("paid_at")), "Original: " + o.get("title"))) {
                n++;
            }
        }
        // shop lines (prints, editions, downloads and originals bought through the cart)
        for (Map<String, Object> i : jdbc.queryForList("select i.id, i.order_id, i.status, i.title, i.format, i.line_total_inr, i.discount_inr, o.paid_at, o.status as order_status,"
                + " p.source, p.artist_user_id, p.artist_admin_id, p.artist_name from shop_order_items i join shop_orders o on o.order_id = i.order_id"
                + " join paintings p on p.id = i.painting_id where i.status in ('OK', 'REFUNDED') and o.paid_at is not null and o.is_test = 0")) {
            String prefix = "SHOP:" + i.get("order_id") + ":" + i.get("id");
            boolean refunded = "REFUNDED".equals(i.get("status"));
            BigDecimal net = dec(i.get("line_total_inr")).subtract(dec(i.get("discount_inr")));
            // a line refunded at checkout (never supplied) was never a sale: post nothing for it
            if (!refunded || ledgerHas(prefix + ":SALE")) {
                if (!refunded && postSale(prefix, "SHOP", String.valueOf(i.get("order_id")), payeeOf(i), net, ldt(i.get("paid_at")), i.get("format") + ": " + i.get("title"))) n++;
                if (refunded && reverse(prefix, "REFUND", "REFUND", "Refunded", null)) n++;
            }
        }
        // course sales: platform revenue
        for (Map<String, Object> o : jdbc.queryForList("select o.id, o.order_number, o.price, o.price_type, o.payment_status, o.updated_at, c.title from orders o"
                + " left join courses c on c.id = o.course_id where o.payment_status in (1, 3) and o.is_test = 0 and coalesce(o.price, '') not in ('', '0') and lower(coalesce(o.price_type, 'inr')) = 'inr'")) {
            String prefix = "COURSE:" + o.get("id");
            if (((Number) o.get("payment_status")).intValue() == 1) {
                if (postSale(prefix, "COURSE", String.valueOf(o.get("id")), Payee.PLATFORM, dec(o.get("price")), ldt(o.get("updated_at")), "Course: " + o.get("title"))) n++;
            } else if (ledgerHas(prefix + ":SALE") && reverse(prefix, "REFUND", "REFUND", "Refunded", null)) {
                n++;
            }
        }
        return n;
    }

    private boolean ledgerHas(String idem) {
        Integer c = jdbc.queryForObject("select count(*) from ledger_entries where idem_key = ?", Integer.class, idem);
        return c != null && c > 0;
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void scheduledSync() {
        try {
            int n = sync();
            if (n > 0) log.info("Ledger: {} new posting(s)", n);
        } catch (RuntimeException e) {
            log.debug("Ledger sync skipped: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------ balances & payouts

    /** Payable per artist as of a time: ARTIST rows available by then, without orders under an open dispute. */
    public List<Map<String, Object>> payable(LocalDateTime asOf) {
        return jdbc.queryForList("select e.payee_type, e.payee_id, max(e.payee_name) as payee_name, sum(e.amount) as amount, count(*) as entries"
                + " from ledger_entries e where e.account = 'ARTIST' and (e.available_at is null or e.available_at <= ?)"
                + " and not exists (select 1 from ledger_disputes d where d.status = 'OPEN' and d.source = e.source and d.source_ref = e.source_ref)"
                + " group by e.payee_type, e.payee_id having sum(e.amount) > 0 order by sum(e.amount) desc", ts(asOf));
    }

    /** Every artist's position: earned, paid, payable now, still in the holding period, on hold for a dispute. */
    public List<Map<String, Object>> balances() {
        Timestamp now = ts(LocalDateTime.now());
        return jdbc.queryForList("select e.payee_type, e.payee_id, max(e.payee_name) as payee_name,"
                + " sum(case when e.entry_type = 'ARTIST_SHARE' then e.amount else 0 end) as earned,"
                + " -sum(case when e.entry_type = 'PAYOUT' then e.amount else 0 end) as paid,"
                + " sum(case when e.entry_type in ('REFUND', 'DISPUTE_LOSS') then e.amount else 0 end) as reversed,"
                + " sum(e.amount) as balance,"
                + " sum(case when e.available_at > ? then e.amount else 0 end) as holding,"
                + " sum(case when exists (select 1 from ledger_disputes d where d.status = 'OPEN' and d.source = e.source and d.source_ref = e.source_ref) then e.amount else 0 end) as disputed"
                + " from ledger_entries e where e.account = 'ARTIST' group by e.payee_type, e.payee_id order by balance desc", now);
    }

    /** Weekly: Monday 06:15, a draft batch of everything payable (staff pay it and mark it paid). */
    @Scheduled(cron = "0 15 6 * * MON")
    public void weeklyBatch() {
        try {
            sync();
            Long open = jdbc.queryForObject("select count(*) from payout_batches where status = 'DRAFT'", Long.class);
            if (open != null && open > 0) return;
            Map<String, Object> b = createBatch(null);
            if (b != null) {
                alerts.send("payouts:batch:" + b.get("id"), "This week's artist payouts are ready",
                        List.of(b.get("line_count") + " artist(s), ₹" + b.get("total") + " in total.", "Pay them, then mark the batch paid with the bank reference."),
                        "/admin/finance");
            }
        } catch (RuntimeException e) {
            log.warn("Weekly payout batch failed", e);
        }
    }

    /** A DRAFT batch of what is payable now; null when nothing is due (or a draft already exists). */
    public Map<String, Object> createBatch(Row admin) {
        return tx.execute(st -> {
            Long drafts = jdbc.queryForObject("select count(*) from payout_batches where status = 'DRAFT' for update", Long.class);
            if (drafts != null && drafts > 0) return null;
            LocalDateTime end = LocalDateTime.now();
            List<Map<String, Object>> due = payable(end);
            if (due.isEmpty()) return null;
            BigDecimal total = BigDecimal.ZERO;
            org.springframework.jdbc.support.GeneratedKeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(con -> {
                var ps = con.prepareStatement("insert into payout_batches (period_end, status, created_at) values (?, 'DRAFT', ?)", java.sql.Statement.RETURN_GENERATED_KEYS);
                ps.setTimestamp(1, ts(end));
                ps.setTimestamp(2, ts(LocalDateTime.now()));
                return ps;
            }, keys);
            long id = keys.getKey().longValue();
            for (Map<String, Object> d : due) {
                BigDecimal amount = dec(d.get("amount"));
                total = total.add(amount);
                jdbc.update("insert into payout_lines (batch_id, payee_type, payee_id, payee_name, payee_email, amount, entries) values (?, ?, ?, ?, ?, ?, ?)",
                        id, d.get("payee_type"), d.get("payee_id"), d.get("payee_name"), emailOf(String.valueOf(d.get("payee_type")), ((Number) d.get("payee_id")).longValue()),
                        amount, d.get("entries"));
            }
            jdbc.update("update payout_batches set total = ?, line_count = ? where id = ?", total, due.size(), id);
            auditLog.write(AuditLog.entry(AuditLog.Type.CREATE, "payout batch created").actor(admin == null ? AuditLog.Actor.system() : AuditLog.Actor.admin(admin))
                    .resource("payout_batch", id).summary(due.size() + " artists · ₹" + total.toPlainString()));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("total", total);
            out.put("line_count", due.size());
            return out;
        });
    }

    private String emailOf(String type, long id) {
        List<String> r = jdbc.queryForList("USER".equals(type) ? "select email from users where id = ?" : "select email from admins where id = ?", String.class, id);
        return r.isEmpty() ? null : r.get(0);
    }

    /**
     * Staff paid the batch (bank transfer outside the site): the lines are re-checked against what is payable as of
     * the batch date (a dispute opened since then lowers or removes a line), then a PAYOUT row per artist is posted.
     */
    public Map<String, Object> markPaid(long batchId, Row admin, String reference) {
        return tx.execute(st -> {
            List<Map<String, Object>> b = jdbc.queryForList("select * from payout_batches where id = ? for update", batchId);
            if (b.isEmpty() || !"DRAFT".equals(b.get(0).get("status"))) return null;
            LocalDateTime end = ldt(b.get(0).get("period_end"));
            Map<String, BigDecimal> now = new LinkedHashMap<>();
            for (Map<String, Object> d : payable(end)) now.put(d.get("payee_type") + ":" + d.get("payee_id"), dec(d.get("amount")));
            BigDecimal total = BigDecimal.ZERO;
            int lines = 0;
            for (Map<String, Object> l : jdbc.queryForList("select * from payout_lines where batch_id = ?", batchId)) {
                String key = l.get("payee_type") + ":" + l.get("payee_id");
                BigDecimal amount = dec(l.get("amount")).min(now.getOrDefault(key, BigDecimal.ZERO));
                if (amount.signum() <= 0) {
                    jdbc.update("update payout_lines set amount = 0 where id = ?", l.get("id"));
                    continue;
                }
                if (amount.compareTo(dec(l.get("amount"))) != 0) jdbc.update("update payout_lines set amount = ? where id = ?", amount, l.get("id"));
                post("PAYOUT:" + batchId + ":" + key, "PAYOUT", "ARTIST", "PAYOUT", String.valueOf(batchId),
                        new Payee(String.valueOf(l.get("payee_type")), ((Number) l.get("payee_id")).longValue(), (String) l.get("payee_name")),
                        amount.negate(), end, batchId, "Payout batch #" + batchId + (reference == null ? "" : " · " + reference), admin.lng("id"));
                total = total.add(amount);
                lines++;
            }
            jdbc.update("update payout_batches set status = 'PAID', total = ?, line_count = ?, reference = ?, paid_by = ?, paid_at = ? where id = ?",
                    total, lines, reference, admin.get("id"), ts(LocalDateTime.now()), batchId);
            auditLog.write(AuditLog.entry(AuditLog.Type.UPDATE, "payout batch paid").actor(AuditLog.Actor.admin(admin)).resource("payout_batch", batchId)
                    .summary(lines + " artists · ₹" + total.toPlainString() + (reference == null ? "" : " · " + reference)));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("total", total);
            out.put("lines", lines);
            return out;
        });
    }

    public boolean voidBatch(long batchId, Row admin) {
        boolean ok = jdbc.update("update payout_batches set status = 'VOID' where id = ? and status = 'DRAFT'", batchId) > 0;
        if (ok) auditLog.write(AuditLog.entry(AuditLog.Type.DELETE, "payout batch voided").actor(AuditLog.Actor.admin(admin)).resource("payout_batch", batchId));
        return ok;
    }

    // ------------------------------------------------------------------ disputes

    /** Finds which order a gateway payment belongs to: [source, ref], or null. */
    public String[] orderOfPayment(String paymentId) {
        for (String[] q : new String[][]{
                {"MARKETPLACE", "select order_id from marketplace_orders where transaction_id = ?"},
                {"SHOP", "select order_id from shop_orders where transaction_id = ?"},
                {"COURSE", "select cast(id as char) from orders where payment_id = ?"}}) {
            List<String> r = jdbc.queryForList(q[1], String.class, paymentId);
            if (!r.isEmpty()) return new String[]{q[0], r.get(0)};
        }
        return null;
    }

    /** Opens a dispute (gateway webhook or staff). Payouts for that order stop until it is closed. */
    public Long openDispute(String source, String ref, String paymentRef, String gatewayDisputeId, String reason, BigDecimal amount, Row admin) {
        try {
            org.springframework.jdbc.support.GeneratedKeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(con -> {
                var ps = con.prepareStatement("insert into ledger_disputes (source, source_ref, payment_ref, gateway_dispute_id, reason, amount, status, opened_by, created_at)"
                        + " values (?, ?, ?, ?, ?, ?, 'OPEN', ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, source);
                ps.setString(2, ref);
                ps.setString(3, paymentRef);
                ps.setString(4, gatewayDisputeId);
                ps.setString(5, reason == null ? null : reason.length() > 300 ? reason.substring(0, 300) : reason);
                ps.setBigDecimal(6, amount == null ? BigDecimal.ZERO : amount);
                ps.setObject(7, admin == null ? null : admin.get("id"));
                ps.setTimestamp(8, ts(LocalDateTime.now()));
                return ps;
            }, keys);
            long id = keys.getKey().longValue();
            auditLog.write(AuditLog.entry(AuditLog.Type.CREATE, "dispute opened").actor(admin == null ? AuditLog.Actor.system() : AuditLog.Actor.admin(admin))
                    .resource("order:" + source, ref).summary((gatewayDisputeId == null ? "manual" : gatewayDisputeId) + " · " + reason));
            alerts.send("dispute:" + id, "A payment dispute was opened", List.of(source + " order " + ref + (paymentRef == null ? "" : " · payment " + paymentRef),
                    "Reason: " + (reason == null ? "not given" : reason), "The artist's share of this order is held out of payouts until the dispute is closed."), "/admin/finance?tab=disputes");
            return id;
        } catch (RuntimeException e) {
            if (DbErrors.isDuplicateKey(e)) return null;
            throw e;
        }
    }

    /** WON: payouts resume. LOST: the sale is reversed (the gateway took the money back). */
    public boolean closeDispute(long id, String outcome, Row admin, String note) {
        if (!List.of("WON", "LOST").contains(outcome)) return false;
        List<Map<String, Object>> d = jdbc.queryForList("select * from ledger_disputes where id = ? and status = 'OPEN'", id);
        if (d.isEmpty()) return false;
        Map<String, Object> row = d.get(0);
        jdbc.update("update ledger_disputes set status = ?, closed_by = ?, closed_at = ?, note = coalesce(?, note) where id = ? and status = 'OPEN'",
                outcome, admin == null ? null : admin.get("id"), ts(LocalDateTime.now()), note, id);
        if ("LOST".equals(outcome)) {
            String source = String.valueOf(row.get("source"));
            String ref = String.valueOf(row.get("source_ref"));
            List<String> prefixes = new ArrayList<>();
            if ("MARKETPLACE".equals(source)) prefixes.add("MKT:" + ref);
            else if ("COURSE".equals(source)) prefixes.add("COURSE:" + ref);
            else prefixes.addAll(jdbc.queryForList("select concat('SHOP:', order_id, ':', id) from shop_order_items where order_id = ?", String.class, ref));
            for (String p : prefixes) reverse(p, "DISPUTE" + id, "DISPUTE_LOSS", "Dispute lost", admin == null ? null : admin.lng("id"));
        }
        auditLog.write(AuditLog.entry(AuditLog.Type.UPDATE, "dispute " + outcome.toLowerCase()).actor(admin == null ? AuditLog.Actor.system() : AuditLog.Actor.admin(admin))
                .resource("order:" + row.get("source"), row.get("source_ref")).summary(note));
        return true;
    }

    /** Razorpay payment.dispute.* webhook events (created / won / lost / closed). */
    @SuppressWarnings("unchecked")
    public String onRazorpayDispute(String type, Map<String, Object> event) {
        Object payload = event.get("payload");
        Object wrap = payload instanceof Map<?, ?> pm ? pm.get("dispute") : null;
        Object entity = wrap instanceof Map<?, ?> w ? w.get("entity") : null;
        if (!(entity instanceof Map<?, ?> dm)) return "no dispute entity";
        Map<String, Object> dispute = (Map<String, Object>) dm;
        String disputeId = String.valueOf(dispute.get("id"));
        String paymentId = String.valueOf(dispute.get("payment_id"));
        if ("payment.dispute.created".equals(type)) {
            String[] order = orderOfPayment(paymentId);
            if (order == null) return "unknown payment";
            BigDecimal amount = dispute.get("amount") instanceof Number n ? new BigDecimal(n.longValue()).movePointLeft(2) : BigDecimal.ZERO;
            Long id = openDispute(order[0], order[1], paymentId, disputeId, String.valueOf(dispute.getOrDefault("reason_description", dispute.get("reason_code"))), amount, null);
            return id == null ? "already open" : "opened " + id;
        }
        List<Long> ids = jdbc.queryForList("select id from ledger_disputes where gateway_dispute_id = ? and status = 'OPEN'", Long.class, disputeId);
        if (ids.isEmpty()) return "no open dispute";
        String outcome = "payment.dispute.won".equals(type) ? "WON" : "payment.dispute.lost".equals(type) ? "LOST"
                : "lost".equals(String.valueOf(dispute.get("status"))) ? "LOST" : "WON";
        closeDispute(ids.get(0), outcome, null, "Razorpay: " + type);
        return outcome.toLowerCase();
    }

    // ------------------------------------------------------------------ report

    /** Month by month: gross, refunds, commission (net), artist share, payouts. */
    public List<Map<String, Object>> monthly(int months) {
        return jdbc.queryForList("select date_format(created_at, '%Y-%m') as month,"
                + " sum(case when entry_type = 'SALE' then amount else 0 end) as gross,"
                + " -sum(case when account = 'ESCROW' and entry_type in ('REFUND', 'DISPUTE_LOSS') then amount else 0 end) as refunds,"
                + " sum(case when account = 'PLATFORM' then amount else 0 end) as commission,"
                + " sum(case when account = 'ARTIST' and entry_type <> 'PAYOUT' then amount else 0 end) as artist_share,"
                + " -sum(case when entry_type = 'PAYOUT' then amount else 0 end) as payouts"
                + " from ledger_entries where created_at >= ? group by date_format(created_at, '%Y-%m') order by month desc",
                ts(LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay().minusMonths(Math.max(0, months - 1))));
    }

    /** Ledger invariant check: escrow in = commission + artist share + reversals (the books balance). */
    public Map<String, Object> totals() {
        Map<String, Object> t = jdbc.queryForMap("select coalesce(sum(case when account = 'ESCROW' then amount else 0 end), 0) as escrow,"
                + " coalesce(sum(case when account = 'PLATFORM' then amount else 0 end), 0) as platform,"
                + " coalesce(sum(case when account = 'ARTIST' and entry_type <> 'PAYOUT' then amount else 0 end), 0) as artist_earned,"
                + " coalesce(-sum(case when entry_type = 'PAYOUT' then amount else 0 end), 0) as paid_out,"
                + " coalesce(sum(case when account = 'ARTIST' then amount else 0 end), 0) as owed from ledger_entries");
        BigDecimal escrow = dec(t.get("escrow"));
        BigDecimal split = dec(t.get("platform")).add(dec(t.get("artist_earned")));
        t.put("balanced", escrow.compareTo(split) == 0);
        return t;
    }

    // ------------------------------------------------------------------ helpers

    static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }

    static LocalDateTime ldt(Object o) {
        if (o instanceof Timestamp t) return t.toLocalDateTime();
        if (o instanceof LocalDateTime l) return l;
        return LocalDateTime.now();
    }

    static BigDecimal dec(Object o) {
        if (o instanceof BigDecimal b) return b;
        if (o == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
