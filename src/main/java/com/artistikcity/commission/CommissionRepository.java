package com.artistikcity.commission;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Persistence for commission_orders / commission_artwork_items. Uses Spring's JdbcTemplate with a
 * TransactionTemplate so the order + item insert is atomic, and status changes are single conditional
 * UPDATEs (compare-and-set), which makes them safe when a webhook and the browser confirm the same
 * payment at the same moment.
 */
@Repository
public class CommissionRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public CommissionRepository(JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
    }

    public void createPending(String orderId, String itemId, Long userId, CommissionRequest r, String currency, BigDecimal total) {
        int[] d = r.dimensions();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        tx.executeWithoutResult(status -> {
            jdbc.update("insert into commission_orders (order_id, user_id, customer_name, customer_email, customer_phone, fulfillment_type,"
                            + " special_instructions, uploaded_photo_url, shipping_address_line1, shipping_city, shipping_postal_code,"
                            + " shipping_country, payment_method, order_status, currency, total_price, created_at, updated_at)"
                            + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PAYMENT_PENDING', ?, ?, ?, ?)",
                    orderId, userId, r.name(), r.email(), r.phone().isEmpty() ? null : r.phone(), r.fulfillmentType(),
                    r.instructions().isEmpty() ? null : r.instructions(), r.photoUrl(), r.addressLine1(), r.city(), r.postalCode(),
                    r.country(), r.paymentMethod(), currency, total, now, now);
            jdbc.update("insert into commission_artwork_items (item_id, order_id, artwork_theme, width_inches, height_inches,"
                            + " frame_material, has_matting) values (?, ?, ?, ?, ?, ?, ?)",
                    itemId, orderId, r.theme(), d[0], d[1], r.frameMaterial(), r.hasMatting());
        });
    }

    public void setProviderRef(String orderId, String ref) {
        jdbc.update("update commission_orders set payment_provider_ref = ?, updated_at = ? where order_id = ?",
                ref, Timestamp.valueOf(LocalDateTime.now()), orderId);
    }

    /**
     * Atomically moves PAYMENT_PENDING -> QUEUED. Returns true only for the caller that made the change;
     * a repeated webhook or a second confirmation is a harmless no-op.
     */
    public boolean markQueued(String orderId, String transactionId) {
        return Boolean.TRUE.equals(tx.execute(status -> jdbc.update(
                "update commission_orders set order_status = 'QUEUED', payment_transaction_id = ?, updated_at = ?"
                        + " where order_id = ? and order_status = 'PAYMENT_PENDING'",
                transactionId, Timestamp.valueOf(LocalDateTime.now()), orderId) == 1));
    }

    public Map<String, Object> find(String orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select o.*, i.item_id, i.artwork_theme, i.width_inches, i.height_inches, i.frame_material, i.has_matting"
                        + " from commission_orders o left join commission_artwork_items i on i.order_id = o.order_id where o.order_id = ?", orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> findByProviderRef(String ref) {
        List<Map<String, Object>> rows = jdbc.queryForList("select order_id from commission_orders where payment_provider_ref = ?", ref);
        return rows.isEmpty() ? null : find(String.valueOf(rows.get(0).get("order_id")));
    }
}
