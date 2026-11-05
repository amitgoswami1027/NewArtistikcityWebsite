package com.artistikcity.orders;

import com.artistikcity.marketplace.MarketplaceRepository;
import com.artistikcity.service.CourseService;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The four order systems, each mapped to {@link UnifiedOrder}:
 * <ul>
 *   <li>{@link Courses}: course and workshop enrolments ({@code orders})</li>
 *   <li>{@link Shop}: marketplace cart orders ({@code shop_orders} / {@code shop_order_items}), one entry per artwork line</li>
 *   <li>{@link LegacyMarketplace}: originals bought through the single-artwork checkout ({@code marketplace_orders})</li>
 *   <li>{@link Commissions}: commissioned artworks ({@code commission_orders})</li>
 * </ul>
 * Abandoned payment attempts (still waiting, expired or cancelled before paying) are not orders and are left out,
 * except course orders, which the course checkout always created as "payment pending" rows the student can retry.
 */
public final class OrderSources {

    private OrderSources() {
    }

    /** Public certificate of authenticity URL for a marketplace line. */
    static String certificateUrl(String orderId, Object itemKey) {
        return "/marketplace/certificates/" + orderId + "/" + itemKey;
    }

    // ================================================================ courses & workshops

    @Component
    @Order(1)
    public static class Courses implements OrderSource {
        private final Db db;

        public Courses(Db db) {
            this.db = db;
        }

        @Override
        public List<UnifiedOrder> forUser(long userId) {
            List<UnifiedOrder> out = new ArrayList<>();
            for (Row o : db.select("select o.id, o.order_number, o.created_at, o.price_type, o.price, o.payment_status, o.payment_option,"
                    + " o.billing_name, o.billing_address, o.billing_city, o.billing_state, o.billing_zipcode, o.billing_country,"
                    + " c.id as course_id, c.title, c.sub_title, c.slug, c.course_type_id, " + CourseService.photoSubquery("o.course_id")
                    + " from orders o join courses c on c.id = o.course_id where o.user_id = ?", userId)) {
                boolean workshop = o.lng("course_type_id", 1) == CourseService.TYPE_WORKSHOP;
                int ps = (int) o.lng("payment_status", 0);
                String[] st = switch (ps) {
                    case 1 -> new String[]{"completed", "Paid · access open"};
                    case 2 -> new String[]{"failed", "Payment failed"};
                    case 3 -> new String[]{"refunded", "Refunded"};
                    default -> new String[]{"pending", "Payment pending"};
                };
                String number = o.str("order_number") == null ? "C-" + o.get("id") : o.str("order_number");
                out.add(UnifiedOrder.builder("c" + o.get("id"), number, UnifiedOrder.COURSE, workshop ? "Workshop" : "Digital Course")
                        .title(o.str("title"))
                        .subtitle(o.str("sub_title"))
                        .imageUrl(o.get("photo") == null ? null : "/storage/uploads/courses/" + o.get("course_id") + "/" + o.get("photo"))
                        .purchaseDate(OrderFormat.iso(o.get("created_at")))
                        .amount(OrderFormat.money(o.get("price")))
                        .currency(o.str("price_type") == null ? "INR" : o.str("price_type").toUpperCase(java.util.Locale.ROOT))
                        .status(st[0], st[1])
                        .accessUrl(ps == 1 ? "/user/course/" + o.get("id") : null)
                        .detailUrl(ps == 1 ? null : "/course/" + o.get("slug"))
                        .paymentMethod(OrderFormat.str(o.get("payment_option")))
                        .address("Billed to", OrderFormat.address(o.get("billing_name"), o.get("billing_address"), o.get("billing_city"),
                                o.get("billing_state"), o.get("billing_zipcode"), o.get("billing_country")))
                        .build());
            }
            return out;
        }
    }

    // ================================================================ marketplace cart orders

    @Component
    @Order(2)
    public static class Shop implements OrderSource {
        private final Db db;

        public Shop(Db db) {
            this.db = db;
        }

        @Override
        public List<UnifiedOrder> forUser(long userId) {
            List<UnifiedOrder> out = new ArrayList<>();
            for (Row i : db.select("select i.id as item_id, i.format, i.quantity, i.title, i.artist_name, i.image_url, i.variant_label, i.line_total_inr,"
                    + " i.discount_inr, i.edition_no, i.status as item_status, i.download_token, i.download_expires_at,"
                    + " o.order_id, o.created_at, o.paid_at, o.status, o.currency, o.gateway, o.courier, o.tracking_number, o.needs_shipping, p.has_certificate,"
                    + " o.buyer_name, o.address_line1, o.address_line2, o.city, o.state, o.postal_code, o.country"
                    + " from shop_order_items i join shop_orders o on o.order_id = i.order_id join paintings p on p.id = i.painting_id"
                    + " where o.user_id = ? and o.status not in ('PAYMENT_PENDING', 'EXPIRED', 'CANCELLED')", userId)) {
                String format = i.str("format");
                String category = switch (format == null ? "" : format) {
                    case "ORIGINAL" -> "Original Painting";
                    case "CANVAS" -> "Canvas Print";
                    case "POSTER" -> "Poster Print";
                    case "DIGITAL" -> "Digital Download";
                    default -> "Artwork";
                };
                String orderStatus = i.str("status");
                String[] st = OrderFormat.marketplaceStatus(orderStatus);
                String itemStatus = i.str("item_status");
                if ("REFUND_REQUIRED".equals(itemStatus)) st = new String[]{"refunded", "Not available · refund in progress"};
                if ("REFUNDED".equals(itemStatus)) st = new String[]{"refunded", "Refunded"};
                boolean ok = "OK".equals(itemStatus);
                boolean paid = List.of("PAID", "PACKED", "SHIPPED", "DELIVERED").contains(orderStatus);
                boolean digital = "DIGITAL".equals(format);
                if (digital && ok && paid) st = new String[]{"completed", "Ready to download"};
                LocalDateTime exp = i.get("download_expires_at") instanceof LocalDateTime l ? l
                        : i.get("download_expires_at") instanceof java.sql.Timestamp t ? t.toLocalDateTime() : null;
                boolean downloadable = digital && ok && paid && i.get("download_token") != null && (exp == null || exp.isAfter(LocalDateTime.now()));
                String tracking = OrderFormat.str(i.get("tracking_number"));
                boolean shipped = !digital && ("SHIPPED".equals(orderStatus) || "DELIVERED".equals(orderStatus)) && tracking != null;
                boolean coa = ok && paid && (("ORIGINAL".equals(format) && Boolean.TRUE.equals(asBool(i.get("has_certificate")))) || "CANVAS".equals(format));
                BigDecimal amount = OrderFormat.money(i.get("line_total_inr")).subtract(OrderFormat.money(i.get("discount_inr")));
                Integer qty = i.get("quantity") instanceof Number n ? n.intValue() : 1;
                out.add(UnifiedOrder.builder("s" + i.get("item_id"), i.str("order_id"), UnifiedOrder.MARKETPLACE, category)
                        .title(i.str("title"))
                        .subtitle(i.str("artist_name") == null ? null : "by " + i.str("artist_name"))
                        .variant(i.str("variant_label"))
                        .imageUrl(i.str("image_url"))
                        .purchaseDate(OrderFormat.iso(i.get("created_at")))
                        .amount(amount)
                        .currency("INR")
                        .status(st[0], st[1])
                        .tracking(shipped ? tracking : null, shipped ? OrderFormat.trackingUrl(i.str("courier"), tracking) : null)
                        .certificateUrl(coa ? certificateUrl(i.str("order_id"), i.get("item_id")) : null)
                        .downloadUrl(downloadable ? "/shop/download/" + i.get("download_token") : null)
                        .detailUrl("/purchase/orders/" + i.str("order_id"))
                        .quantity(qty)
                        .editionNo(i.get("edition_no") instanceof Number n ? n.intValue() : null)
                        .paymentMethod(OrderFormat.gateway(i.str("gateway"), i.str("currency")))
                        .address("Ship to", digital ? null : OrderFormat.address(i.get("buyer_name"), i.get("address_line1"), i.get("address_line2"),
                                i.get("city"), i.get("state"), i.get("postal_code"), i.get("country")))
                        .build());
            }
            return out;
        }
    }

    // ================================================================ marketplace single-original checkout (before the cart)

    @Component
    @Order(3)
    public static class LegacyMarketplace implements OrderSource {
        private final Db db;

        public LegacyMarketplace(Db db) {
            this.db = db;
        }

        @Override
        public List<UnifiedOrder> forUser(long userId) {
            List<UnifiedOrder> out = new ArrayList<>();
            for (Row o : db.select("select o.order_id, o.created_at, o.paid_at, o.status, o.currency, o.amount, o.courier, o.tracking_number, o.gateway,"
                    + " o.buyer_name, o.address_line1, o.address_line2, o.city, o.state, o.postal_code, o.country,"
                    + " p.title, p.artist_name, p.has_certificate, p.height_inches, p.width_inches, " + MarketplaceRepository.PRIMARY_IMAGE_SQL + " as image_url"
                    + " from marketplace_orders o join paintings p on p.id = o.painting_id"
                    + " where o.user_id = ? and o.status not in ('PAYMENT_PENDING', 'EXPIRED', 'CANCELLED')", userId)) {
                String status = o.str("status");
                String[] st = OrderFormat.marketplaceStatus(status);
                boolean paid = List.of("PAID", "PACKED", "SHIPPED", "DELIVERED").contains(status);
                String tracking = OrderFormat.str(o.get("tracking_number"));
                boolean shipped = ("SHIPPED".equals(status) || "DELIVERED".equals(status)) && tracking != null;
                out.add(UnifiedOrder.builder("m" + o.get("order_id"), o.str("order_id"), UnifiedOrder.MARKETPLACE, "Original Painting")
                        .title(o.str("title"))
                        .subtitle(o.str("artist_name") == null ? null : "by " + o.str("artist_name"))
                        .variant("Original painting · " + plain(o.get("height_inches")) + " × " + plain(o.get("width_inches")) + " in")
                        .imageUrl(o.str("image_url"))
                        .purchaseDate(OrderFormat.iso(o.get("created_at")))
                        .amount(OrderFormat.money(o.get("amount")))
                        .currency(o.str("currency") == null ? "INR" : o.str("currency"))
                        .status(st[0], st[1])
                        .tracking(shipped ? tracking : null, shipped ? OrderFormat.trackingUrl(o.str("courier"), tracking) : null)
                        .certificateUrl(paid && Boolean.TRUE.equals(asBool(o.get("has_certificate"))) ? certificateUrl(o.str("order_id"), "original") : null)
                        .detailUrl("/marketplace/orders/" + o.str("order_id"))
                        .quantity(1)
                        .paymentMethod(OrderFormat.gateway(o.str("gateway"), o.str("currency")))
                        .address("Ship to", OrderFormat.address(o.get("buyer_name"), o.get("address_line1"), o.get("address_line2"),
                                o.get("city"), o.get("state"), o.get("postal_code"), o.get("country")))
                        .build());
            }
            return out;
        }
    }

    // ================================================================ commissions

    @Component
    @Order(4)
    public static class Commissions implements OrderSource {
        private final Db db;

        public Commissions(Db db) {
            this.db = db;
        }

        @Override
        public List<UnifiedOrder> forUser(long userId) {
            List<UnifiedOrder> out = new ArrayList<>();
            for (Row o : db.select("select order_id, created_at, order_status, currency, total_price, fulfillment_type, uploaded_photo_url, payment_method,"
                    + " customer_name, shipping_address_line1, shipping_city, shipping_postal_code, shipping_country"
                    + " from commission_orders where user_id = ? and order_status <> 'PAYMENT_PENDING'", userId)) {
                String status = o.str("order_status");
                String[] st = switch (status == null ? "" : status) {
                    case "QUEUED" -> new String[]{"processing", "In the artist's queue"};
                    case "PROOF_SENT" -> new String[]{"processing", "Proof sent for your approval"};
                    case "IN_PRODUCTION" -> new String[]{"processing", "Being painted"};
                    case "SHIPPED" -> new String[]{"shipped", "Shipped"};
                    case "COMPLETED" -> new String[]{"completed", "Completed"};
                    default -> new String[]{"pending", OrderFormat.nice(status)};
                };
                out.add(UnifiedOrder.builder("k" + o.get("order_id"), o.str("order_id"), UnifiedOrder.COMMISSION, "Commission")
                        .title("Commissioned artwork")
                        .subtitle(switch (o.str("fulfillment_type") == null ? "" : o.str("fulfillment_type")) {
                            case "DIGITAL_ONLY" -> "Digital artwork from your photo";
                            case "PHYSICAL_PRINT" -> "Printed artwork from your photo";
                            case "ORIGINAL_PAINTING" -> "Original painting from your photo";
                            default -> OrderFormat.nice(o.str("fulfillment_type"));
                        })
                        .imageUrl(OrderFormat.str(o.get("uploaded_photo_url")))
                        .purchaseDate(OrderFormat.iso(o.get("created_at")))
                        .amount(OrderFormat.money(o.get("total_price")))
                        .currency(o.str("currency") == null ? "INR" : o.str("currency"))
                        .status(st[0], st[1])
                        .quantity(1)
                        .paymentMethod(OrderFormat.nice(o.str("payment_method")))
                        .address("Ship to", OrderFormat.str(o.get("shipping_address_line1")) == null ? null
                                : OrderFormat.address(o.get("customer_name"), o.get("shipping_address_line1"), o.get("shipping_city"),
                                o.get("shipping_postal_code"), o.get("shipping_country")))
                        .build());
            }
            return out;
        }
    }

    static Boolean asBool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        return o == null ? null : "1".equals(String.valueOf(o)) || "true".equalsIgnoreCase(String.valueOf(o));
    }

    static String plain(Object v) {
        BigDecimal b = OrderFormat.money(v);
        return b.stripTrailingZeros().scale() <= 0 ? b.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString() : b.stripTrailingZeros().toPlainString();
    }
}
