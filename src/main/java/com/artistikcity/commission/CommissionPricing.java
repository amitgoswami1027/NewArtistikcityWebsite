package com.artistikcity.commission;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for commission prices. The wizard receives {@link #table()} to display
 * prices, but the server always recalculates the total with {@link #quote(CommissionRequest)}.
 * Base prices are in USD; Razorpay (India) charges the INR equivalent.
 */
@Component
public class CommissionPricing {

    public static final List<String> THEMES = List.of("PORTRAIT", "LANDSCAPE", "WATERCOLOR", "CHARCOAL");
    public static final List<String> FULFILLMENTS = List.of("DIGITAL_ONLY", "PHYSICAL_PRINT", "ORIGINAL_PAINTING");
    public static final List<String> SIZES = List.of("8x10", "12x16", "16x20", "24x36");
    public static final List<String> FRAMES = List.of("NONE", "MATTE_BLACK", "GALLERY_WHITE", "WARM_WALNUT");

    private static final Map<String, int[]> BASE = Map.of(          // per size, in SIZES order
            "DIGITAL_ONLY", new int[]{49, 69, 89, 129},
            "PHYSICAL_PRINT", new int[]{89, 129, 169, 249},
            "ORIGINAL_PAINTING", new int[]{249, 399, 549, 949});
    private static final Map<String, BigDecimal> THEME_FACTOR = Map.of(
            "PORTRAIT", new BigDecimal("1.00"), "LANDSCAPE", new BigDecimal("1.00"),
            "WATERCOLOR", new BigDecimal("0.90"), "CHARCOAL", new BigDecimal("0.80"));
    private static final Map<String, int[]> FRAME_PRICE = Map.of(
            "NONE", new int[]{0, 0, 0, 0},
            "MATTE_BLACK", new int[]{39, 59, 79, 119},
            "GALLERY_WHITE", new int[]{39, 59, 79, 119},
            "WARM_WALNUT", new int[]{49, 69, 99, 149});
    private static final int[] MATTING = {15, 20, 25, 35};
    private static final int SHIPPING_PHYSICAL = 15;

    private final BigDecimal inrPerUsd;

    public CommissionPricing(Environment env) {
        this.inrPerUsd = new BigDecimal(env.getProperty("commission.inr-per-usd", "84"));
    }

    /** Price table sent to the wizard so it can show live totals. */
    public Map<String, Object> table() {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("sizes", SIZES);
        t.put("base", BASE);
        t.put("themeFactor", THEME_FACTOR);
        t.put("frames", FRAME_PRICE);
        t.put("matting", MATTING);
        t.put("shipping", SHIPPING_PHYSICAL);
        t.put("inrPerUsd", inrPerUsd);
        return t;
    }

    /** Validated, server-side quote. */
    public Quote quote(CommissionRequest r) {
        int s = SIZES.indexOf(r.size());
        BigDecimal base = BigDecimal.valueOf(BASE.get(r.fulfillmentType())[s]).multiply(THEME_FACTOR.get(r.theme()))
                .setScale(2, RoundingMode.HALF_UP);
        boolean physical = !"DIGITAL_ONLY".equals(r.fulfillmentType());
        BigDecimal frame = physical ? BigDecimal.valueOf(FRAME_PRICE.get(r.frameMaterial())[s]) : BigDecimal.ZERO;
        BigDecimal mat = physical && r.hasMatting() ? BigDecimal.valueOf(MATTING[s]) : BigDecimal.ZERO;
        BigDecimal ship = physical ? BigDecimal.valueOf(SHIPPING_PHYSICAL) : BigDecimal.ZERO;
        BigDecimal usd = base.add(frame).add(mat).add(ship).setScale(2, RoundingMode.HALF_UP);
        BigDecimal inr = usd.multiply(inrPerUsd).divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).multiply(BigDecimal.TEN)
                .setScale(2, RoundingMode.HALF_UP);
        return new Quote(base, frame, mat, ship, usd, inr);
    }

    public record Quote(BigDecimal artwork, BigDecimal frame, BigDecimal matting, BigDecimal shipping,
                        BigDecimal totalUsd, BigDecimal totalInr) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("artwork", artwork);
            m.put("frame", frame);
            m.put("matting", matting);
            m.put("shipping", shipping);
            m.put("totalUsd", totalUsd);
            m.put("totalInr", totalInr);
            return m;
        }
    }
}
