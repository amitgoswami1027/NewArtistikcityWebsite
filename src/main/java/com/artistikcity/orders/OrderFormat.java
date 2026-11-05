package com.artistikcity.orders;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Small shared helpers for the order sources: dates, money, courier tracking links. */
final class OrderFormat {

    private OrderFormat() {
    }

    private static final DateTimeFormatter LARAVEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** Any date value from the database as ISO 8601 local date-time ("2026-10-25T14:05:00"), or "" when missing. */
    static String iso(Object v) {
        if (v == null) return "";
        if (v instanceof LocalDateTime l) return l.format(ISO);
        if (v instanceof Timestamp t) return iso(t.toLocalDateTime());
        String s = String.valueOf(v).trim();
        try {
            return iso(LocalDateTime.parse(s.length() > 19 ? s.substring(0, 19) : s, LARAVEL));
        } catch (RuntimeException e) {
            return s.replace(' ', 'T');
        }
    }

    static BigDecimal money(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    /** "IN_PRODUCTION" -> "In production". */
    static String nice(String v) {
        if (v == null || v.isBlank()) return "";
        String s = v.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static final Map<String, String> COURIERS = Map.ofEntries(
            Map.entry("bluedart", "https://www.bluedart.com/tracking?trackFor=0&trackNo=%s"),
            Map.entry("blue dart", "https://www.bluedart.com/tracking?trackFor=0&trackNo=%s"),
            Map.entry("delhivery", "https://www.delhivery.com/track/package/%s"),
            Map.entry("dtdc", "https://www.dtdc.in/tracking.asp?strCnno=%s"),
            Map.entry("india post", "https://www.indiapost.gov.in/_layouts/15/dop.portal.tracking/trackconsignment.aspx?consignment=%s"),
            Map.entry("speed post", "https://www.indiapost.gov.in/_layouts/15/dop.portal.tracking/trackconsignment.aspx?consignment=%s"),
            Map.entry("ekart", "https://ekartlogistics.com/shipmenttrack/%s"),
            Map.entry("xpressbees", "https://www.xpressbees.com/shipment/tracking?awbNo=%s"),
            Map.entry("fedex", "https://www.fedex.com/fedextrack/?trknbr=%s"),
            Map.entry("dhl", "https://www.dhl.com/in-en/home/tracking.html?tracking-id=%s"),
            Map.entry("ups", "https://www.ups.com/track?tracknum=%s"),
            Map.entry("aramex", "https://www.aramex.com/track/results?ShipmentNumber=%s"));

    /** A courier's public tracking page for this consignment; a web search when the courier isn't one we know. */
    static String trackingUrl(String courier, String number) {
        if (number == null || number.isBlank()) return null;
        String enc = URLEncoder.encode(number.trim(), StandardCharsets.UTF_8);
        if (courier != null) {
            String c = courier.trim().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> e : COURIERS.entrySet()) {
                if (c.contains(e.getKey())) return String.format(e.getValue(), enc);
            }
        }
        String q = ((courier == null ? "" : courier.trim() + " ") + "tracking " + number.trim()).trim();
        return "https://www.google.com/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
    }

    /** "12 MG Road, Pune 411001, India" from the parts that are filled in, or null. */
    static String address(Object... parts) {
        StringBuilder b = new StringBuilder();
        for (Object o : parts) {
            String v = str(o);
            if (v == null) continue;
            if (b.length() > 0) b.append(", ");
            b.append(v);
        }
        return b.length() == 0 ? null : b.toString();
    }

    /** How a marketplace order was paid. */
    static String gateway(String g, String currency) {
        if (g == null) return null;
        return switch (g.toUpperCase(Locale.ROOT)) {
            case "RAZORPAY" -> "UPI / card / NetBanking (Razorpay)";
            case "PAYPAL" -> "PayPal" + ("USD".equalsIgnoreCase(currency) ? " (USD)" : "");
            case "TEST" -> "Test payment";
            default -> nice(g);
        };
    }

    /** Marketplace order status (shop_orders / marketplace_orders) -> unified status and a label. */
    static String[] marketplaceStatus(String st) {
        return switch (st == null ? "" : st) {
            case "PAID" -> new String[]{"processing", "Paid · being prepared"};
            case "PACKED" -> new String[]{"processing", "Packed"};
            case "SHIPPED" -> new String[]{"shipped", "Shipped"};
            case "DELIVERED" -> new String[]{"completed", "Delivered"};
            case "REFUND_REQUIRED" -> new String[]{"refunded", "Refund in progress"};
            case "REFUNDED" -> new String[]{"refunded", "Refunded"};
            case "PAYMENT_PENDING" -> new String[]{"pending", "Waiting for payment"};
            default -> new String[]{"failed", nice(st)};
        };
    }
}
