package com.artistikcity.marketplace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The exclusive-hold engine and the sale state machine for 1-of-1 originals.
 *
 * <h3>Reserving (15-minute hold)</h3>
 * Runs in a SERIALIZABLE transaction and relies on three independent guards, so a painting can never be
 * held by two collectors even across several app servers:
 * <ol>
 *   <li>{@code UPDATE paintings SET stock_status='RESERVED', version=version+1 WHERE id=? AND stock_status='AVAILABLE' AND version=?}
 *       - optimistic lock on the version read in the same transaction;</li>
 *   <li>the UNIQUE constraint on {@code cart_reservations.painting_id};</li>
 *   <li>serializable isolation (a deadlock victim or serialization failure is reported as "just taken").</li>
 * </ol>
 *
 * <h3>Selling</h3>
 * A verified payment (Razorpay signature / webhook, PayPal capture / webhook) marks the order PAID and the
 * painting SOLD atomically. A payment that arrives after its hold expired still wins if nobody else is
 * holding the piece; if another collector holds or bought it, the order becomes REFUND_REQUIRED so staff
 * can refund - the exclusive-hold promise to the other collector is always kept.
 *
 * <h3>Janitor</h3>
 * {@link #releaseExpiredHolds()} runs every minute and returns abandoned holds to AVAILABLE.
 */
@Service
public class MarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);

    public static final int HOLD_MINUTES = 15;
    /** When payment starts with less than this left, the hold is topped up once so the gateway can finish. */
    public static final int PAYMENT_GRACE_MINUTES = 5;
    public static final List<String> PAID_STATES = List.of("PAID", "PACKED", "SHIPPED", "DELIVERED");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate serializable;
    private final TransactionTemplate tx;
    private final MarketplaceRepository repo;

    public MarketplaceService(JdbcTemplate jdbc, PlatformTransactionManager tm, MarketplaceRepository repo) {
        this.jdbc = jdbc;
        this.repo = repo;
        this.tx = new TransactionTemplate(tm);
        this.serializable = new TransactionTemplate(tm);
        this.serializable.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
    }

    public record Hold(long paintingId, LocalDateTime expiresAt, boolean created) {
        public long expiresAtMillis() {
            return expiresAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
    }

    private static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }

    private static String inList(List<String> keys) {
        return String.join(",", keys.stream().map(k -> "?").toList());
    }

    // ================================================================ reserve / release

    /**
     * Places (or returns) the visitor's exclusive hold on a painting.
     * @param switchHold release the visitor's hold on another painting first (one hold at a time)
     */
    public Hold reserve(long paintingId, String holderKey, List<String> myKeys, Long userId, boolean switchHold) {
        try {
            return serializable.execute(s -> doReserve(paintingId, holderKey, myKeys, userId, switchHold));
        } catch (MarketplaceException e) {
            throw e;
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            log.info("Reservation race lost for painting {}: {}", paintingId, e.getClass().getSimpleName());
            throw new MarketplaceException(409, "JUST_TAKEN", "Another collector reserved this piece a moment ago.");
        }
    }

    private Hold doReserve(long paintingId, String holderKey, List<String> myKeys, Long userId, boolean switchHold) {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> existing = jdbc.queryForList("select id, session_or_user_id, expires_at from cart_reservations where painting_id = ?", paintingId);
        if (!existing.isEmpty()) {
            Map<String, Object> r = existing.get(0);
            LocalDateTime exp = ((Timestamp) r.get("expires_at")).toLocalDateTime();
            if (exp.isAfter(now)) {
                if (myKeys.contains(String.valueOf(r.get("session_or_user_id")))) {
                    return new Hold(paintingId, exp, false);
                }
                throw new MarketplaceException(409, "HELD_BY_OTHER", "Another collector is holding this piece right now.",
                        Map.of("reservedUntil", exp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
            }
            // an expired hold the janitor hasn't swept yet
            releaseRow(((Number) r.get("id")).longValue(), paintingId, String.valueOf(r.get("session_or_user_id")));
        }

        List<Map<String, Object>> p = jdbc.queryForList("select stock_status, version from paintings where id = ?", paintingId);
        if (p.isEmpty()) throw new MarketplaceException(404, "NOT_FOUND", "This painting is no longer listed.");
        String status = String.valueOf(p.get(0).get("stock_status"));
        int version = ((Number) p.get(0).get("version")).intValue();
        if ("RESERVED".equals(status)) {
            // RESERVED with no live hold row (e.g. a crash between steps): heal it before reserving
            if (jdbc.update("update paintings set stock_status = 'AVAILABLE', version = version + 1 where id = ? and stock_status = 'RESERVED'", paintingId) > 0) {
                status = "AVAILABLE";
                version++;
            }
        }
        if ("SOLD".equals(status)) throw new MarketplaceException(410, "SOLD", "This original has already found its collector.");
        if (!"AVAILABLE".equals(status)) throw new MarketplaceException(409, "UNAVAILABLE", "This piece isn't for sale right now.");

        List<Object> args = new java.util.ArrayList<>(myKeys);
        args.add(ts(now));
        args.add(paintingId);
        List<Map<String, Object>> mine = myKeys.isEmpty() ? List.of() : jdbc.queryForList(
                "select r.id, r.painting_id, r.session_or_user_id, p.title, p.slug from cart_reservations r join paintings p on p.id = r.painting_id"
                        + " where r.session_or_user_id in (" + inList(myKeys) + ") and r.expires_at > ? and r.painting_id <> ?", args.toArray());
        if (!mine.isEmpty()) {
            if (!switchHold) {
                Map<String, Object> h = mine.get(0);
                throw new MarketplaceException(409, "HOLD_LIMIT", "You're already holding “" + h.get("title") + "”. Finish that checkout or release it to reserve this piece.",
                        Map.of("heldSlug", h.get("slug"), "heldTitle", h.get("title")));
            }
            for (Map<String, Object> h : mine) {
                releaseRow(((Number) h.get("id")).longValue(), ((Number) h.get("painting_id")).longValue(), String.valueOf(h.get("session_or_user_id")));
            }
        }

        int flipped = jdbc.update("update paintings set stock_status = 'RESERVED', version = version + 1, updated_at = ?"
                + " where id = ? and stock_status = 'AVAILABLE' and version = ?", ts(now), paintingId, version);
        if (flipped == 0) {
            throw new MarketplaceException(409, "JUST_TAKEN", "Another collector reserved this piece a moment ago.");
        }
        LocalDateTime expires = now.plusMinutes(HOLD_MINUTES);
        jdbc.update("insert into cart_reservations (session_or_user_id, painting_id, user_id, reserved_at, expires_at) values (?, ?, ?, ?, ?)",
                holderKey, paintingId, userId, ts(now), ts(expires));
        return new Hold(paintingId, expires, true);
    }

    /** Deletes one hold row and puts the painting back on sale; pending payment attempts for it expire. */
    private void releaseRow(long reservationId, long paintingId, String holder) {
        int deleted = jdbc.update("delete from cart_reservations where id = ?", reservationId);
        if (deleted == 0) return;
        jdbc.update("update paintings set stock_status = 'AVAILABLE', version = version + 1, updated_at = ? where id = ? and stock_status = 'RESERVED'",
                ts(LocalDateTime.now()), paintingId);
        jdbc.update("update marketplace_orders set status = 'EXPIRED', updated_at = ? where painting_id = ? and holder_key = ? and status = 'PAYMENT_PENDING'",
                ts(LocalDateTime.now()), paintingId, holder);
    }

    /** The visitor gives the piece back. Returns false when they weren't holding it. */
    public boolean release(long paintingId, List<String> myKeys) {
        if (myKeys.isEmpty()) return false;
        Boolean done = tx.execute(s -> {
            List<Object> args = new java.util.ArrayList<>();
            args.add(paintingId);
            args.addAll(myKeys);
            List<Map<String, Object>> rows = jdbc.queryForList("select id, session_or_user_id from cart_reservations where painting_id = ? and session_or_user_id in ("
                    + inList(myKeys) + ")", args.toArray());
            if (rows.isEmpty()) return false;
            releaseRow(((Number) rows.get(0).get("id")).longValue(), paintingId, String.valueOf(rows.get(0).get("session_or_user_id")));
            return true;
        });
        if (Boolean.TRUE.equals(done)) repo.syncStudentListing(paintingId);
        return Boolean.TRUE.equals(done);
    }

    /** Staff override (admin console): release whoever is holding the piece. */
    public boolean forceRelease(long paintingId) {
        Boolean done = tx.execute(s -> {
            List<Map<String, Object>> rows = jdbc.queryForList("select id, session_or_user_id from cart_reservations where painting_id = ?", paintingId);
            if (rows.isEmpty()) {
                // heal a RESERVED painting that lost its hold row
                return jdbc.update("update paintings set stock_status = 'AVAILABLE', version = version + 1, updated_at = ? where id = ? and stock_status = 'RESERVED'",
                        ts(LocalDateTime.now()), paintingId) > 0;
            }
            releaseRow(((Number) rows.get(0).get("id")).longValue(), paintingId, String.valueOf(rows.get(0).get("session_or_user_id")));
            return true;
        });
        return Boolean.TRUE.equals(done);
    }

    /** The visitor's active hold on this painting, or null. */
    public Hold activeHold(long paintingId, List<String> myKeys) {
        if (myKeys.isEmpty()) return null;
        List<Object> args = new java.util.ArrayList<>();
        args.add(paintingId);
        args.add(ts(LocalDateTime.now()));
        args.addAll(myKeys);
        List<Map<String, Object>> rows = jdbc.queryForList("select expires_at from cart_reservations where painting_id = ? and expires_at > ? and session_or_user_id in ("
                + inList(myKeys) + ")", args.toArray());
        return rows.isEmpty() ? null : new Hold(paintingId, ((Timestamp) rows.get(0).get("expires_at")).toLocalDateTime(), false);
    }

    /** All of the visitor's live holds (for the site-wide "held for you" banner). */
    public List<Map<String, Object>> myHolds(List<String> myKeys) {
        if (myKeys.isEmpty()) return List.of();
        List<Object> args = new java.util.ArrayList<>();
        args.add(ts(LocalDateTime.now()));
        args.addAll(myKeys);
        return jdbc.queryForList("select r.painting_id, r.expires_at, p.slug, p.title, p.final_price, " + MarketplaceRepository.PRIMARY_IMAGE + " as image_url"
                + " from cart_reservations r join paintings p on p.id = r.painting_id where r.expires_at > ? and r.session_or_user_id in (" + inList(myKeys) + ")", args.toArray());
    }

    /** Runs at second 0 of every minute: returns abandoned holds to the gallery. */
    @Scheduled(cron = "0 */1 * * * *")
    public void releaseExpiredHolds() {
        List<Map<String, Object>> expired = jdbc.queryForList("select id, painting_id, session_or_user_id from cart_reservations where expires_at < ?", ts(LocalDateTime.now()));
        int released = 0;
        for (Map<String, Object> r : expired) {
            long paintingId = ((Number) r.get("painting_id")).longValue();
            try {
                Boolean ok = tx.execute(s -> {
                    int stillExpired = jdbc.update("delete from cart_reservations where id = ? and expires_at < ?", r.get("id"), ts(LocalDateTime.now()));
                    if (stillExpired == 0) return false;
                    jdbc.update("update paintings set stock_status = 'AVAILABLE', version = version + 1, updated_at = ? where id = ? and stock_status = 'RESERVED'",
                            ts(LocalDateTime.now()), paintingId);
                    jdbc.update("update marketplace_orders set status = 'EXPIRED', updated_at = ? where painting_id = ? and holder_key = ? and status = 'PAYMENT_PENDING'",
                            ts(LocalDateTime.now()), paintingId, r.get("session_or_user_id"));
                    return true;
                });
                if (Boolean.TRUE.equals(ok)) {
                    released++;
                    repo.syncStudentListing(paintingId);
                }
            } catch (RuntimeException e) {
                log.warn("Could not release expired hold {} on painting {}", r.get("id"), paintingId, e);
            }
        }
        if (released > 0) log.info("Marketplace janitor released {} expired hold(s)", released);
        int healed = jdbc.update("update paintings set stock_status = 'AVAILABLE', version = version + 1, updated_at = ? where stock_status = 'RESERVED'"
                + " and not exists (select 1 from cart_reservations r where r.painting_id = paintings.id)", ts(LocalDateTime.now()));
        if (healed > 0) log.info("Marketplace janitor healed {} reserved painting(s) with no hold", healed);
    }

    // ================================================================ orders

    public record NewOrder(String orderId, long paintingId, Long userId, String holderKey, Map<String, String> buyer,
                           String gateway, String currency, BigDecimal amount, BigDecimal priceInr) {
    }

    /** Stores a PAYMENT_PENDING order for the visitor's current hold, cancelling any earlier attempt. */
    public void createPendingOrder(NewOrder o) {
        tx.executeWithoutResult(s -> {
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("update marketplace_orders set status = 'CANCELLED', updated_at = ? where painting_id = ? and holder_key = ? and status = 'PAYMENT_PENDING'",
                    ts(now), o.paintingId(), o.holderKey());
            Map<String, String> b = o.buyer();
            jdbc.update("insert into marketplace_orders (order_id, painting_id, user_id, holder_key, buyer_name, buyer_email, buyer_phone, address_line1, address_line2,"
                            + " city, state, postal_code, country, gateway, currency, amount, price_inr, status, created_at, updated_at)"
                            + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PAYMENT_PENDING', ?, ?)",
                    o.orderId(), o.paintingId(), o.userId(), o.holderKey(), b.get("name"), b.get("email"), b.get("phone"), b.get("line1"), b.get("line2"),
                    b.get("city"), b.get("state"), b.get("postal"), b.get("country"), o.gateway(), o.currency(), o.amount(), o.priceInr(), ts(now), ts(now));
            // top the hold up once so a slow UPI / PayPal approval can finish inside it
            jdbc.update("update cart_reservations set expires_at = ? where painting_id = ? and session_or_user_id = ? and expires_at < ?",
                    ts(now.plusMinutes(PAYMENT_GRACE_MINUTES)), o.paintingId(), o.holderKey(), ts(now.plusMinutes(PAYMENT_GRACE_MINUTES)));
        });
    }

    public void setProviderRef(String orderId, String ref) {
        jdbc.update("update marketplace_orders set provider_ref = ?, updated_at = ? where order_id = ?", ref, ts(LocalDateTime.now()), orderId);
    }

    public Map<String, Object> order(String orderId) {
        List<Map<String, Object>> r = jdbc.queryForList("select o.*, p.title, p.slug, p.artist_name, p.medium, p.surface, p.height_inches, p.width_inches,"
                + " p.has_certificate, p.is_framed, " + MarketplaceRepository.PRIMARY_IMAGE + " as image_url"
                + " from marketplace_orders o join paintings p on p.id = o.painting_id where o.order_id = ?", orderId);
        return r.isEmpty() ? null : r.get(0);
    }

    public Map<String, Object> orderByProviderRef(String ref) {
        List<Map<String, Object>> r = jdbc.queryForList("select * from marketplace_orders where provider_ref = ?", ref);
        return r.isEmpty() ? null : r.get(0);
    }

    public enum Outcome { SOLD, ALREADY_PAID, REFUND_REQUIRED, NOT_FOUND, IGNORED }

    /**
     * Applies a verified payment. Idempotent: webhooks, the browser callback and PayPal's return can all
     * arrive for the same order.
     */
    public Outcome finalizeSale(String orderId, String transactionId) {
        Outcome[] result = new Outcome[1];
        long[] paintingId = new long[1];
        try {
            serializable.executeWithoutResult(s -> {
                List<Map<String, Object>> rows = jdbc.queryForList("select painting_id, holder_key, status from marketplace_orders where order_id = ?", orderId);
                if (rows.isEmpty()) { result[0] = Outcome.NOT_FOUND; return; }
                Map<String, Object> o = rows.get(0);
                String st = String.valueOf(o.get("status"));
                if (PAID_STATES.contains(st)) { result[0] = Outcome.ALREADY_PAID; return; }
                if (!List.of("PAYMENT_PENDING", "EXPIRED", "CANCELLED").contains(st)) { result[0] = Outcome.IGNORED; return; }
                long pid = ((Number) o.get("painting_id")).longValue();
                paintingId[0] = pid;
                String holder = String.valueOf(o.get("holder_key"));
                LocalDateTime now = LocalDateTime.now();

                String pst = jdbc.queryForObject("select stock_status from paintings where id = ?", String.class, pid);
                List<Map<String, Object>> res = jdbc.queryForList("select session_or_user_id, expires_at from cart_reservations where painting_id = ?", pid);
                boolean heldByOther = !res.isEmpty() && !holder.equals(String.valueOf(res.get(0).get("session_or_user_id")))
                        && ((Timestamp) res.get(0).get("expires_at")).toLocalDateTime().isAfter(now);
                boolean canSell = ("AVAILABLE".equals(pst) || "RESERVED".equals(pst)) && !heldByOther;
                if (!canSell) {
                    jdbc.update("update marketplace_orders set status = 'REFUND_REQUIRED', transaction_id = ?, paid_at = ?, updated_at = ? where order_id = ?",
                            transactionId, ts(now), ts(now), orderId);
                    result[0] = Outcome.REFUND_REQUIRED;
                    return;
                }
                int sold = jdbc.update("update paintings set stock_status = 'SOLD', sold_at = ?, version = version + 1, updated_at = ? where id = ? and stock_status in ('AVAILABLE', 'RESERVED')",
                        ts(now), ts(now), pid);
                if (sold == 0) {
                    jdbc.update("update marketplace_orders set status = 'REFUND_REQUIRED', transaction_id = ?, paid_at = ?, updated_at = ? where order_id = ?",
                            transactionId, ts(now), ts(now), orderId);
                    result[0] = Outcome.REFUND_REQUIRED;
                    return;
                }
                jdbc.update("delete from cart_reservations where painting_id = ?", pid);
                jdbc.update("update marketplace_orders set status = 'PAID', transaction_id = ?, paid_at = ?, updated_at = ? where order_id = ?",
                        transactionId, ts(now), ts(now), orderId);
                jdbc.update("update marketplace_orders set status = 'CANCELLED', updated_at = ? where painting_id = ? and order_id <> ? and status = 'PAYMENT_PENDING'",
                        ts(now), pid, orderId);
                result[0] = Outcome.SOLD;
            });
        } catch (ConcurrencyFailureException e) {
            // a simultaneous webhook + browser confirmation: let the other one win, then report what happened
            log.info("Concurrent finalisation for {} - re-reading", orderId);
            Map<String, Object> o = order(orderId);
            return o != null && PAID_STATES.contains(String.valueOf(o.get("status"))) ? Outcome.ALREADY_PAID : finalizeSale(orderId, transactionId);
        }
        if (result[0] == Outcome.SOLD) {
            repo.syncStudentListing(paintingId[0]);
            log.info("Marketplace sale {} completed for painting {}", orderId, paintingId[0]);
        } else if (result[0] == Outcome.REFUND_REQUIRED) {
            log.warn("Marketplace order {} was paid but the painting was no longer available - refund required", orderId);
        }
        return result[0];
    }

    /** Fulfilment and refunds from the admin console. */
    public static final Map<String, List<String>> ORDER_FLOW = Map.of(
            "PAID", List.of("PACKED", "SHIPPED", "REFUNDED"),
            "PACKED", List.of("SHIPPED", "REFUNDED"),
            "SHIPPED", List.of("DELIVERED"),
            "REFUND_REQUIRED", List.of("REFUNDED"));

    public Map<String, Object> advanceOrder(String orderId, String next, String courier, String tracking, String notes, boolean relist) {
        Map<String, Object>[] out = new Map[1];
        tx.executeWithoutResult(s -> {
            List<Map<String, Object>> rows = jdbc.queryForList("select status, painting_id from marketplace_orders where order_id = ?", orderId);
            if (rows.isEmpty()) throw new MarketplaceException(404, "NOT_FOUND", "Order not found.");
            String cur = String.valueOf(rows.get(0).get("status"));
            if (!ORDER_FLOW.getOrDefault(cur, List.of()).contains(next)) {
                throw new MarketplaceException(409, "BAD_TRANSITION", "An order that is " + cur.replace('_', ' ').toLowerCase() + " can't move to " + next.replace('_', ' ').toLowerCase() + ".");
            }
            if ("SHIPPED".equals(next) && (tracking == null || tracking.isBlank())) {
                throw new MarketplaceException(422, "TRACKING", "Add the courier tracking number before marking it shipped.");
            }
            LocalDateTime now = LocalDateTime.now();
            int changed = jdbc.update("update marketplace_orders set status = ?, courier = coalesce(?, courier), tracking_number = coalesce(?, tracking_number),"
                            + " staff_notes = coalesce(?, staff_notes), updated_at = ? where order_id = ? and status = ?",
                    next, blankToNull(courier), blankToNull(tracking), blankToNull(notes), ts(now), orderId, cur);
            if (changed == 0) throw new MarketplaceException(409, "STALE", "Someone else just updated this order. Refresh and try again.");
            long pid = ((Number) rows.get(0).get("painting_id")).longValue();
            if ("REFUNDED".equals(next) && !"REFUND_REQUIRED".equals(cur)) {
                // a cancelled sale: the painting goes back on sale or into the archive
                jdbc.update("update paintings set stock_status = ?, sold_at = null, version = version + 1, updated_at = ? where id = ? and stock_status = 'SOLD'",
                        relist ? "AVAILABLE" : "ARCHIVED", ts(now), pid);
            }
            out[0] = new LinkedHashMap<>(order(orderId));
        });
        if (out[0] != null && out[0].get("painting_id") instanceof Number n) repo.syncStudentListing(n.longValue());
        return out[0];
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
