package com.artistikcity.orders;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * One purchase in "Your orders", whatever system it came from. A marketplace cart order with three artworks is three
 * entries sharing one {@code orderId}, so each can carry its own badge and actions.
 *
 * <pre>
 * interface UnifiedOrder {
 *   orderId: string; orderType: 'course' | 'marketplace' | 'commission';
 *   category: 'Digital Course' | 'Workshop' | 'Original Painting' | 'Canvas Print' | 'Poster Print' | 'Digital Download' | 'Commission';
 *   title: string; subtitle?: string; variant?: string; imageUrl?: string;
 *   purchaseDate: string;            // ISO 8601, e.g. 2026-10-25T14:05:00
 *   amount: number; currency: 'INR' | 'USD';
 *   status: 'pending' | 'processing' | 'shipped' | 'completed' | 'failed' | 'refunded'; statusLabel: string;
 *   trackingNumber?: string; trackingUrl?: string;      // shipped marketplace items
 *   accessUrl?: string;                                 // paid courses and workshops
 *   certificateUrl?: string; downloadUrl?: string; detailUrl?: string; quantity?: number; editionNo?: number;
 *   paymentMethod?: string;                             // "UPI / card (Razorpay)", "PayPal", "Free"
 *   address?: string; addressLabel?: 'Billed to' | 'Ship to';
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UnifiedOrder(
        String key,
        String orderId,
        String orderType,
        String category,
        String title,
        String subtitle,
        String variant,
        String imageUrl,
        String purchaseDate,
        BigDecimal amount,
        String currency,
        String status,
        String statusLabel,
        String trackingNumber,
        String trackingUrl,
        String accessUrl,
        String certificateUrl,
        String downloadUrl,
        String detailUrl,
        Integer quantity,
        Integer editionNo,
        String paymentMethod,
        String address,
        String addressLabel) {

    public static final String COURSE = "course";
    public static final String MARKETPLACE = "marketplace";
    public static final String COMMISSION = "commission";

    /** Builder-style copy helpers keep the sources readable. */
    public static Builder builder(String key, String orderId, String orderType, String category) {
        return new Builder(key, orderId, orderType, category);
    }

    public static final class Builder {
        private final String key;
        private final String orderId;
        private final String orderType;
        private final String category;
        private String title = "";
        private String subtitle;
        private String variant;
        private String imageUrl;
        private String purchaseDate = "";
        private BigDecimal amount = BigDecimal.ZERO;
        private String currency = "INR";
        private String status = "pending";
        private String statusLabel = "Pending";
        private String trackingNumber;
        private String trackingUrl;
        private String accessUrl;
        private String certificateUrl;
        private String downloadUrl;
        private String detailUrl;
        private Integer quantity;
        private Integer editionNo;
        private String paymentMethod;
        private String address;
        private String addressLabel;

        private Builder(String key, String orderId, String orderType, String category) {
            this.key = key;
            this.orderId = orderId;
            this.orderType = orderType;
            this.category = category;
        }

        public Builder title(String v) { this.title = v == null ? "" : v; return this; }
        public Builder subtitle(String v) { this.subtitle = v; return this; }
        public Builder variant(String v) { this.variant = v; return this; }
        public Builder imageUrl(String v) { this.imageUrl = v; return this; }
        public Builder purchaseDate(String v) { this.purchaseDate = v == null ? "" : v; return this; }
        public Builder amount(BigDecimal v) { this.amount = v == null ? BigDecimal.ZERO : v; return this; }
        public Builder currency(String v) { this.currency = v == null ? "INR" : v; return this; }
        public Builder status(String v, String label) { this.status = v; this.statusLabel = label; return this; }
        public Builder tracking(String number, String url) { this.trackingNumber = number; this.trackingUrl = url; return this; }
        public Builder accessUrl(String v) { this.accessUrl = v; return this; }
        public Builder certificateUrl(String v) { this.certificateUrl = v; return this; }
        public Builder downloadUrl(String v) { this.downloadUrl = v; return this; }
        public Builder detailUrl(String v) { this.detailUrl = v; return this; }
        public Builder quantity(Integer v) { this.quantity = v; return this; }
        public Builder editionNo(Integer v) { this.editionNo = v; return this; }
        public Builder paymentMethod(String v) { this.paymentMethod = v; return this; }
        public Builder address(String label, String v) { this.addressLabel = v == null ? null : label; this.address = v; return this; }

        public UnifiedOrder build() {
            return new UnifiedOrder(key, orderId, orderType, category, title, subtitle, variant, imageUrl, purchaseDate, amount, currency,
                    status, statusLabel, trackingNumber, trackingUrl, accessUrl, certificateUrl, downloadUrl, detailUrl, quantity, editionNo,
                    paymentMethod, address, addressLabel);
        }
    }
}
