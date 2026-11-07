package com.artistikcity.shop;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Prices for every way an artwork can be bought. Pure functions over the price list (shop_price_options) and the
 * listing, so the browser preview, the cart and the order all come from the same numbers.
 *
 * <ul>
 *   <li>ORIGINAL: the listing's final price. Optional frame.</li>
 *   <li>CANVAS: limited-edition canvas print. Size row price, plus the size's {@code extra_inr} when stretched.</li>
 *   <li>POSTER: fine-art paper print, matte or glossy (same price).</li>
 *   <li>DIGITAL: high-resolution file, personal-use licence. No frame, no shipping.</li>
 * </ul>
 * A print keeps the artwork's proportions: its long side is the size's {@code long_side_in}, the short side follows
 * the original's ratio, rounded to the nearest half inch. Frames are priced per inch of outside perimeter.
 *
 * <p><b>Custom sizes</b> (canvas and poster): size code {@code C<n>} = a long side of n whole inches, between the limits in
 * {@link Custom} (Admin > Sales > Marketplace > Prices). The price follows the print's area along the preset sizes of that
 * format (straight line between the two presets around it, continued past the ends), then goes up to the next ₹x99 like
 * the presets; a custom size equal to a preset costs exactly the preset's price. {@code Components/Shop/shop.js} mirrors
 * this for the live preview.</p>
 */
public final class ShopPricing {

    public static final List<String> FORMATS = List.of("ORIGINAL", "CANVAS", "POSTER", "DIGITAL");
    public static final List<String> FRAMES = List.of("NONE", "BLACK_WOOD", "OAK", "WHITE_FLOAT");
    public static final List<String> PRINT_FORMATS = List.of("CANVAS", "POSTER", "DIGITAL");
    public static final int MAX_QTY = 10;

    private ShopPricing() {
    }

    /** One row of shop_price_options. */
    public record Option(String kind, String code, String label, BigDecimal longSide, BigDecimal price, BigDecimal extra, int sort, boolean active) {
    }

    /** Custom print sizes: whether they are offered and the shortest / longest long side in inches. */
    public record Custom(boolean enabled, BigDecimal min, BigDecimal max) {
        public static final int DEFAULT_MIN = 8;
        public static final int DEFAULT_MAX = 60;
        public static final int HARD_MIN = 4;
        public static final int HARD_MAX = 96;
    }

    static final java.util.regex.Pattern CUSTOM_CODE = java.util.regex.Pattern.compile("C(\\d{1,3})");

    /** The custom-size limits carried in the option list (two CUSTOM rows); off when they are missing. */
    public static Custom custom(List<Option> options) {
        Option min = null;
        Option max = null;
        for (Option o : options) {
            if (!"CUSTOM".equals(o.kind())) continue;
            if ("MIN".equals(o.code())) min = o;
            if ("MAX".equals(o.code())) max = o;
        }
        if (min == null || max == null || min.longSide() == null || max.longSide() == null) return new Custom(false, BigDecimal.ZERO, BigDecimal.ZERO);
        return new Custom(min.active() && max.active(), min.longSide(), max.longSide());
    }

    /** Long side of a custom size code ("C30" -> 30), or null when the code isn't one. */
    public static Integer customLongSide(String size) {
        if (size == null) return null;
        java.util.regex.Matcher m = CUSTOM_CODE.matcher(size);
        return m.matches() ? Integer.valueOf(m.group(1)) : null;
    }

    /**
     * Price and stretching extra for a custom print of {@code longSide} inches, from the format's presets (by print area).
     * Returns {price, extra}; null when the format has no presets.
     */
    static BigDecimal[] customPrice(Art art, List<Option> presets, int longSide) {
        List<double[]> pts = new ArrayList<>();          // {area, price, extra}
        for (Option o : presets) {
            if (o.longSide() == null) continue;
            BigDecimal[] wh = printSize(art, o.longSide());
            pts.add(new double[]{wh[0].doubleValue() * wh[1].doubleValue(), o.price().doubleValue(), o.extra() == null ? 0 : o.extra().doubleValue()});
        }
        if (pts.isEmpty()) return null;
        pts.sort((a, b) -> Double.compare(a[0], b[0]));
        BigDecimal[] wh = printSize(art, BigDecimal.valueOf(longSide));
        double area = wh[0].doubleValue() * wh[1].doubleValue();
        double price;
        double extra;
        if (pts.size() == 1) {
            price = pts.get(0)[1] * area / pts.get(0)[0];
            extra = pts.get(0)[2] * area / pts.get(0)[0];
        } else {
            int i = 0;
            while (i < pts.size() - 2 && area > pts.get(i + 1)[0]) i++;
            double[] a = pts.get(i);
            double[] b = pts.get(i + 1);
            double t = b[0] == a[0] ? 0 : (area - a[0]) / (b[0] - a[0]);
            price = a[1] + t * (b[1] - a[1]);
            extra = a[2] + t * (b[2] - a[2]);
            double smallest = pts.get(0)[1];
            price = Math.max(price, smallest * 0.6);
            extra = Math.max(extra, 0);
        }
        return new BigDecimal[]{BigDecimal.valueOf(roundPrice(price)), BigDecimal.valueOf(Math.round(extra / 100.0) * 100)};
    }

    /** Up to the next ₹x99 (₹1,250 -> ₹1,299; ₹4,999 stays), like the preset prices. */
    static long roundPrice(double v) {
        long r = (long) Math.ceil(v / 100.0 - 1e-9) * 100 - 1;
        return Math.max(99, r);
    }

    /** The facts about a listing that pricing needs. */
    public record Art(long id, String title, String artistName, String surface, String status, BigDecimal finalPrice,
                      BigDecimal heightIn, BigDecimal widthIn, boolean framed, Set<String> formats, boolean hasHires,
                      int editionSize, int editionsSold) {
        public int editionsLeft() {
            return Math.max(0, editionSize - editionsSold);
        }

        boolean onCanvas() {
            String s = surface == null ? "" : surface.toLowerCase(Locale.ROOT);
            return s.contains("canvas") || s.contains("panel") || s.contains("linen");
        }
    }

    /** What the buyer picked. Codes are upper case; blank strings mean "not applicable". */
    public record Selection(String format, String size, String finish, String frame, int qty) {
        public static Selection of(Object format, Object size, Object finish, Object frame, Object qty) {
            int q = 1;
            if (qty instanceof Number n) q = n.intValue();
            else if (qty != null) {
                try { q = Integer.parseInt(String.valueOf(qty).trim()); } catch (NumberFormatException e) { q = 1; }
            }
            return new Selection(code(format), code(size), code(finish), code(frame).isEmpty() ? "NONE" : code(frame), q);
        }

        private static String code(Object o) {
            return o == null ? "" : String.valueOf(o).trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "");
        }
    }

    /** A priced, normalised selection, or the reason it can't be bought. */
    public record Quote(Selection selection, boolean ok, String error, BigDecimal unitPrice, BigDecimal lineTotal,
                        BigDecimal widthIn, BigDecimal heightIn, String label, int maxQty, BigDecimal framePrice) {
    }

    // ================================================================ catalogue of choices

    /** Everything the purchase pages offer for one artwork: formats, sizes with their print dimensions, finishes and frames. */
    public static Map<String, Object> offer(Art art, List<Option> options, boolean originalAvailable, String originalReason) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> formats = new ArrayList<>();
        for (String f : FORMATS) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("format", f);
            List<Map<String, Object>> sizes = new ArrayList<>();
            boolean available;
            String reason = null;
            if ("ORIGINAL".equals(f)) {
                available = originalAvailable;
                reason = originalReason;
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("code", "");
                s.put("label", "Original size");
                s.put("width", scale(art.widthIn()));
                s.put("height", scale(art.heightIn()));
                s.put("price", art.finalPrice());
                s.put("extra", BigDecimal.ZERO);
                sizes.add(s);
                m.put("from", art.finalPrice());
            } else {
                boolean enabled = art.formats().contains(f) && !kind(options, f).isEmpty();
                if ("DIGITAL".equals(f) && !art.hasHires()) enabled = false;
                if ("CANVAS".equals(f) && art.editionsLeft() <= 0) {
                    enabled = false;
                    reason = "The canvas edition is sold out.";
                }
                available = enabled;
                if (!enabled && reason == null) reason = "Not offered for this artwork.";
                BigDecimal from = null;
                for (Option o : kind(options, f)) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("code", o.code());
                    s.put("label", o.label());
                    BigDecimal[] wh = "DIGITAL".equals(f) ? new BigDecimal[]{null, null} : printSize(art, o.longSide());
                    s.put("width", wh[0]);
                    s.put("height", wh[1]);
                    s.put("longSide", o.longSide());
                    s.put("price", o.price());
                    s.put("extra", o.extra());
                    sizes.add(s);
                    from = from == null || o.price().compareTo(from) < 0 ? o.price() : from;
                }
                m.put("from", from);
            }
            m.put("available", available);
            m.put("reason", available ? null : reason);
            m.put("sizes", sizes);
            m.put("finishes", finishes(f));
            m.put("frames", framesFor(f, art));
            m.put("maxQty", "CANVAS".equals(f) ? Math.min(MAX_QTY, art.editionsLeft()) : "POSTER".equals(f) ? MAX_QTY : 1);
            Custom c = custom(options);
            m.put("custom", ("CANVAS".equals(f) || "POSTER".equals(f)) && c.enabled() && !kind(options, f).isEmpty()
                    ? Map.of("min", c.min().intValue(), "max", c.max().intValue()) : null);
            formats.add(m);
        }
        out.put("formats", formats);
        List<Map<String, Object>> frames = new ArrayList<>();
        frames.add(Map.of("code", "NONE", "label", "No frame", "perInch", BigDecimal.ZERO));
        for (Option o : kind(options, "FRAME")) {
            frames.add(Map.of("code", o.code(), "label", o.label(), "perInch", o.price()));
        }
        out.put("frameRates", frames);
        out.put("edition", Map.of("size", art.editionSize(), "left", art.editionsLeft()));
        Map<String, Object> ship = new LinkedHashMap<>();
        ship.put("india", option(options, "SHIPPING", "PRINTS_INDIA") == null ? BigDecimal.ZERO : option(options, "SHIPPING", "PRINTS_INDIA").price());
        ship.put("international", option(options, "SHIPPING", "PRINTS_INTL") == null ? BigDecimal.ZERO : option(options, "SHIPPING", "PRINTS_INTL").price());
        out.put("shipping", ship);
        return out;
    }

    static List<Map<String, String>> finishes(String format) {
        return switch (format) {
            case "CANVAS" -> List.of(Map.of("code", "STRETCHED", "label", "Stretched & ready to hang"), Map.of("code", "ROLLED", "label", "Rolled in a tube"));
            case "POSTER" -> List.of(Map.of("code", "MATTE", "label", "Matte archival paper"), Map.of("code", "GLOSSY", "label", "Glossy photo paper"));
            default -> List.of();
        };
    }

    static List<String> framesFor(String format, Art art) {
        return switch (format) {
            case "CANVAS" -> FRAMES;
            case "POSTER" -> List.of("NONE", "BLACK_WOOD", "OAK");
            case "ORIGINAL" -> art.framed() ? List.of("NONE") : art.onCanvas() ? FRAMES : List.of("NONE", "BLACK_WOOD", "OAK");
            default -> List.of("NONE");
        };
    }

    // ================================================================ quoting

    public static Quote quote(Art art, List<Option> options, Selection sel) {
        String f = sel.format();
        if (!FORMATS.contains(f)) return fail(sel, "Choose how you'd like to buy this artwork.");
        String frame = FRAMES.contains(sel.frame()) ? sel.frame() : "NONE";
        if (!framesFor(f, art).contains(frame)) {
            return fail(sel, "DIGITAL".equals(f) ? "Digital files don't come framed." : "That frame isn't available for this format.");
        }
        BigDecimal w;
        BigDecimal h;
        BigDecimal unit;
        String size = "";
        String finish = "";
        int maxQty;
        StringBuilder label = new StringBuilder();
        switch (f) {
            case "ORIGINAL" -> {
                if (!List.of("AVAILABLE", "RESERVED").contains(art.status())) return fail(sel, "This original has already found its collector.");
                w = scale(art.widthIn());
                h = scale(art.heightIn());
                unit = art.finalPrice();
                maxQty = 1;
                label.append("Original painting · ").append(dims(w, h));
            }
            case "CANVAS", "POSTER", "DIGITAL" -> {
                if (!art.formats().contains(f) || ("DIGITAL".equals(f) && !art.hasHires())) return fail(sel, "This format isn't offered for this artwork.");
                if (!List.of("AVAILABLE", "RESERVED", "SOLD").contains(art.status())) return fail(sel, "This artwork isn't on sale right now.");
                Option o = option(options, f, sel.size());
                Integer customSide = "DIGITAL".equals(f) ? null : customLongSide(sel.size());
                boolean isCustom = false;
                if (customSide != null) {
                    Custom c = custom(options);
                    if (!c.enabled()) return fail(sel, "Custom sizes aren't available right now. Choose one of the sizes.");
                    if (customSide < c.min().intValue() || customSide > c.max().intValue()) {
                        return fail(sel, "Choose a long side between " + c.min().intValue() + " and " + c.max().intValue() + " inches.");
                    }
                    BigDecimal[] pe = customPrice(art, kind(options, f), customSide);
                    if (pe == null) return fail(sel, "Choose a size.");
                    o = new Option(f, "C" + customSide, "Custom size", BigDecimal.valueOf(customSide), pe[0], pe[1], 99, true);
                    isCustom = true;
                }
                if (o == null) {
                    List<Option> all = kind(options, f);
                    if ("DIGITAL".equals(f) && all.size() == 1) o = all.get(0);
                    else return fail(sel, "Choose a size.");
                }
                size = o.code();
                unit = o.price();
                if ("DIGITAL".equals(f)) {
                    w = null;
                    h = null;
                    maxQty = 1;
                    label.append("Digital download · ").append(o.label().substring(0, 1).toLowerCase(Locale.ROOT)).append(o.label().substring(1));
                } else {
                    BigDecimal[] wh = printSize(art, o.longSide());
                    w = wh[0];
                    h = wh[1];
                    if ("CANVAS".equals(f)) {
                        finish = "ROLLED".equals(sel.finish()) && "NONE".equals(frame) ? "ROLLED" : "STRETCHED";
                        if ("STRETCHED".equals(finish)) unit = unit.add(o.extra());
                        maxQty = Math.min(MAX_QTY, art.editionsLeft());
                        if (maxQty <= 0) return fail(sel, "The canvas edition is sold out.");
                        label.append("Limited edition canvas · ").append(dims(w, h)).append(isCustom ? " (custom)" : "").append(" · ").append("STRETCHED".equals(finish) ? "stretched" : "rolled");
                    } else {
                        finish = "GLOSSY".equals(sel.finish()) ? "GLOSSY" : "MATTE";
                        maxQty = MAX_QTY;
                        label.append("Fine art poster · ").append(dims(w, h)).append(isCustom ? " (custom)" : "").append(" · ").append(finish.toLowerCase(Locale.ROOT));
                    }
                }
            }
            default -> {
                return fail(sel, "Choose how you'd like to buy this artwork.");
            }
        }
        BigDecimal framePrice = BigDecimal.ZERO;
        if (!"NONE".equals(frame)) {
            Option fo = option(options, "FRAME", frame);
            if (fo == null) return fail(sel, "That frame isn't available right now.");
            BigDecimal perimeter = w.add(h).multiply(BigDecimal.valueOf(2));
            framePrice = fo.price().multiply(perimeter).setScale(0, RoundingMode.HALF_UP);
            unit = unit.add(framePrice);
            label.append(" · ").append(fo.label().substring(0, 1).toLowerCase(Locale.ROOT)).append(fo.label().substring(1));
        }
        int qty = Math.max(1, Math.min(sel.qty(), maxQty));
        unit = unit.setScale(2, RoundingMode.HALF_UP);
        Selection norm = new Selection(f, size, finish, frame, qty);
        return new Quote(norm, true, null, unit, unit.multiply(BigDecimal.valueOf(qty)), w, h, label.toString(), maxQty, framePrice);
    }

    private static Quote fail(Selection sel, String error) {
        return new Quote(sel, false, error, BigDecimal.ZERO, BigDecimal.ZERO, null, null, "", 0, BigDecimal.ZERO);
    }

    /** {width, height} in inches for a print whose long side is {@code longSide}, keeping the artwork's ratio. */
    static BigDecimal[] printSize(Art art, BigDecimal longSide) {
        double hIn = art.heightIn() == null ? 1 : art.heightIn().doubleValue();
        double wIn = art.widthIn() == null ? 1 : art.widthIn().doubleValue();
        double L = longSide == null ? 12 : longSide.doubleValue();
        double w;
        double h;
        if (hIn >= wIn) {
            h = L;
            w = halfInch(L * wIn / hIn);
        } else {
            w = L;
            h = halfInch(L * hIn / wIn);
        }
        return new BigDecimal[]{scale(BigDecimal.valueOf(w)), scale(BigDecimal.valueOf(h))};
    }

    private static double halfInch(double v) {
        return Math.max(1, Math.round(v * 2) / 2.0);
    }

    static BigDecimal scale(BigDecimal v) {
        if (v == null) return null;
        BigDecimal r = v.setScale(1, RoundingMode.HALF_UP);
        return r.stripTrailingZeros().scale() <= 0 ? r.setScale(0, RoundingMode.HALF_UP) : r;
    }

    static String dims(BigDecimal w, BigDecimal h) {
        return w.toPlainString() + " × " + h.toPlainString() + " in";
    }

    static List<Option> kind(List<Option> options, String kind) {
        return options.stream().filter(o -> o.active() && kind.equals(o.kind())).toList();
    }

    static Option option(List<Option> options, String kind, String code) {
        for (Option o : options) {
            if (o.active() && kind.equals(o.kind()) && o.code().equals(code)) return o;
        }
        return null;
    }

    // ================================================================ coupons

    /** A coupon row, as stored. */
    public record Coupon(long id, String code, String description, String type, BigDecimal value, Long paintingId, String artistName,
                         BigDecimal minSubtotal, Integer maxUses, int usedCount, java.time.LocalDateTime startsAt,
                         java.time.LocalDateTime expiresAt, boolean active) {
    }

    /** A line the coupon may apply to. */
    public record Line(long paintingId, String artistName, BigDecimal total) {
    }

    /** Discount per line (same order as {@code lines}) and the total, or an explanation when it doesn't apply. */
    public record Discount(BigDecimal total, List<BigDecimal> perLine, String error) {
    }

    public static Discount discount(Coupon c, List<Line> lines, java.time.LocalDateTime now) {
        List<BigDecimal> zero = lines.stream().map(l -> BigDecimal.ZERO).toList();
        if (c == null) return new Discount(BigDecimal.ZERO, zero, null);
        if (!c.active()) return new Discount(BigDecimal.ZERO, zero, "This code is no longer active.");
        if (c.startsAt() != null && now.isBefore(c.startsAt())) return new Discount(BigDecimal.ZERO, zero, "This code isn't valid yet.");
        if (c.expiresAt() != null && now.isAfter(c.expiresAt())) return new Discount(BigDecimal.ZERO, zero, "This code has expired.");
        if (c.maxUses() != null && c.usedCount() >= c.maxUses()) return new Discount(BigDecimal.ZERO, zero, "This code has been used up.");
        BigDecimal subtotal = lines.stream().map(Line::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (c.minSubtotal() != null && subtotal.compareTo(c.minSubtotal()) < 0) {
            return new Discount(BigDecimal.ZERO, zero, "This code needs an order of at least ₹" + c.minSubtotal().setScale(0, RoundingMode.HALF_UP).toPlainString() + ".");
        }
        List<Boolean> eligible = lines.stream().map(l -> (c.paintingId() == null || c.paintingId() == l.paintingId())
                && (c.artistName() == null || c.artistName().isBlank() || c.artistName().equalsIgnoreCase(l.artistName()))).toList();
        BigDecimal base = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) if (eligible.get(i)) base = base.add(lines.get(i).total());
        if (base.signum() <= 0) return new Discount(BigDecimal.ZERO, zero, "This code doesn't apply to the artworks in your cart.");
        BigDecimal total = "PERCENT".equals(c.type())
                ? base.multiply(c.value().min(BigDecimal.valueOf(90))).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                : c.value().min(base).setScale(0, RoundingMode.HALF_UP);
        // spread it over the eligible lines in proportion to their totals; the last eligible line takes the rounding
        List<BigDecimal> per = new ArrayList<>();
        BigDecimal left = total;
        int last = -1;
        for (int i = 0; i < lines.size(); i++) if (eligible.get(i)) last = i;
        for (int i = 0; i < lines.size(); i++) {
            if (!eligible.get(i)) {
                per.add(BigDecimal.ZERO);
            } else if (i == last) {
                per.add(left);
            } else {
                BigDecimal share = total.multiply(lines.get(i).total()).divide(base, 0, RoundingMode.DOWN);
                per.add(share);
                left = left.subtract(share);
            }
        }
        return new Discount(total.setScale(2, RoundingMode.HALF_UP), per, null);
    }
}
