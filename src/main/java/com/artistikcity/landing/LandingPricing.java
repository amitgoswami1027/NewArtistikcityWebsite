package com.artistikcity.landing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one price calculation for a landing page. The public page shows {@link #quote}, the checkout charges the same
 * {@link #quote} worked out again on the server (never the browser's figure), and the gateway is told exactly its total.
 *
 * <p>Order of rules: the page's own price (else the course price) → a promotional price while its window is open → an
 * early-bird price until its deadline (the lowest of the open offers wins) → coupon → tax ("ADDED" puts it on top,
 * "INCLUDED" only shows the share). The USD price for PayPal follows the same discounts in proportion.
 */
public final class LandingPricing {

    private LandingPricing() {
    }

    /** Admin-configured pricing for one page (all optional). */
    public record Rules(BigDecimal listPrice, BigDecimal price, BigDecimal priceUsd, String promoLabel, BigDecimal promoPrice,
                        LocalDateTime promoStarts, LocalDateTime promoEnds, BigDecimal earlyPrice, LocalDateTime earlyEnds,
                        boolean couponsEnabled, String taxMode, BigDecimal taxRate, String taxLabel) {
        public static Rules none() {
            return new Rules(null, null, null, null, null, null, null, null, null, true, "INCLUDED", BigDecimal.ZERO, null);
        }
    }

    /** A coupon already validated for this page and buyer. */
    public record Discount(String code, String kind, BigDecimal value) {
    }

    public record Quote(BigDecimal list, BigDecimal base, BigDecimal offerPrice, String offerLabel, LocalDateTime offerEnds,
                        BigDecimal subtotal, String couponCode, BigDecimal discount, BigDecimal tax, String taxMode, String taxLabel,
                        BigDecimal total, BigDecimal totalUsd, boolean couponsEnabled) {

        public boolean free() {
            return total.signum() == 0 && (totalUsd == null || totalUsd.signum() == 0);
        }

        /** You save (list price minus what is paid before tax). */
        public BigDecimal saving() {
            return list.subtract(subtotal.subtract(discount)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        public Map<String, Object> view() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("currency", "INR");
            m.put("list", list);
            m.put("base", base);
            m.put("offerPrice", offerPrice);
            m.put("offerLabel", offerLabel);
            m.put("offerEnds", offerEnds == null ? null : offerEnds.toString());
            m.put("subtotal", subtotal);
            m.put("coupon", couponCode);
            m.put("discount", discount);
            m.put("tax", tax);
            m.put("taxMode", taxMode);
            m.put("taxLabel", taxLabel);
            m.put("total", total);
            m.put("totalUsd", totalUsd);
            m.put("saving", saving());
            m.put("couponsEnabled", couponsEnabled);
            m.put("free", free());
            return m;
        }
    }

    public static Quote quote(Rules r, BigDecimal courseInr, BigDecimal courseUsd, LocalDateTime now, Discount coupon) {
        Rules rules = r == null ? Rules.none() : r;
        BigDecimal base = money(rules.price() != null ? rules.price() : courseInr);
        BigDecimal list = money(rules.listPrice() != null ? rules.listPrice().max(base) : base);
        BigDecimal usdBase = rules.priceUsd() != null ? money(rules.priceUsd()) : courseUsd == null ? null : money(courseUsd);

        // genuine offers only: a window that is open now, never a fake countdown
        BigDecimal offer = null;
        String label = null;
        LocalDateTime ends = null;
        if (rules.promoPrice() != null && rules.promoPrice().compareTo(base) < 0
                && (rules.promoStarts() == null || !now.isBefore(rules.promoStarts())) && (rules.promoEnds() == null || now.isBefore(rules.promoEnds()))) {
            offer = money(rules.promoPrice());
            label = rules.promoLabel() == null || rules.promoLabel().isBlank() ? "Limited-time offer" : rules.promoLabel().trim();
            ends = rules.promoEnds();
        }
        if (rules.earlyPrice() != null && rules.earlyEnds() != null && now.isBefore(rules.earlyEnds()) && rules.earlyPrice().compareTo(base) < 0
                && (offer == null || rules.earlyPrice().compareTo(offer) < 0)) {
            offer = money(rules.earlyPrice());
            label = "Early-bird price";
            ends = rules.earlyEnds();
        }
        BigDecimal subtotal = offer != null ? offer : base;

        BigDecimal discount = BigDecimal.ZERO;
        String code = null;
        if (coupon != null && rules.couponsEnabled() && subtotal.signum() > 0) {
            discount = "PERCENT".equals(coupon.kind())
                    ? subtotal.multiply(coupon.value().min(BigDecimal.valueOf(100))).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).setScale(2)   // whole rupees
                    : money(coupon.value());
            discount = discount.min(subtotal).max(BigDecimal.ZERO);
            code = coupon.code();
        }
        BigDecimal net = subtotal.subtract(discount);

        BigDecimal rate = rules.taxRate() == null ? BigDecimal.ZERO : rules.taxRate().max(BigDecimal.ZERO).min(BigDecimal.valueOf(50));
        String mode = "ADDED".equals(rules.taxMode()) ? "ADDED" : "INCLUDED";
        BigDecimal tax;
        BigDecimal total;
        if ("ADDED".equals(mode)) {
            tax = net.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            total = net.add(tax);
        } else {
            // the share of tax already inside the price: net - net / (1 + rate)
            tax = rate.signum() == 0 ? BigDecimal.ZERO
                    : net.subtract(net.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(100).add(rate), 2, RoundingMode.HALF_UP));
            total = net;
        }
        BigDecimal totalUsd = null;
        if (usdBase != null) {
            BigDecimal factor = base.signum() == 0 ? BigDecimal.ONE : total.divide(base, 8, RoundingMode.HALF_UP);
            totalUsd = base.signum() == 0 ? usdBase : money(usdBase.multiply(factor));
        }
        return new Quote(list, base, offer, label, ends, subtotal, code, discount, tax, mode,
                rules.taxLabel() == null || rules.taxLabel().isBlank() ? "GST" : rules.taxLabel().trim(), money(total), totalUsd, rules.couponsEnabled());
    }

    static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
