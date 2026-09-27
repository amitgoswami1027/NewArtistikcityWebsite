package com.artistikcity.marketplace;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validated input from the three-step listing wizard (Visuals &amp; story, Structural ledger, Pricing).
 * The same record serves super admins, instructors and students; who may set which status is decided
 * by the controllers.
 */
public record ListingInput(
        String title, String description, String artistNotes, String medium, String surface, String subject, String styleTags,
        BigDecimal heightInches, BigDecimal widthInches, BigDecimal depthInches, BigDecimal weightKg, int yearCreated,
        boolean framed, String frameDetails, boolean signed, boolean certificate,
        BigDecimal basePrice, BigDecimal discountPercentage, List<ImageInput> images) {

    public static final BigDecimal MIN_PRICE = new BigDecimal("500");
    public static final BigDecimal MAX_PRICE = new BigDecimal("50000000");
    public static final BigDecimal MAX_DISCOUNT = new BigDecimal("90");

    public record ImageInput(String url, boolean primary, String alt) {
    }

    /** Thrown with every problem found, first one first, so the wizard can jump to the right step. */
    public static class InvalidListingException extends RuntimeException {
        private final List<String> errors;
        private final int step;

        public InvalidListingException(List<String> errors, int step) {
            super(errors.get(0));
            this.errors = errors;
            this.step = step;
        }

        public List<String> errors() {
            return errors;
        }

        public int step() {
            return step;
        }
    }

    /** The one place a final price is calculated (the database CHECK constraint enforces the same formula). */
    public static BigDecimal finalPrice(BigDecimal base, BigDecimal discount) {
        return base.multiply(new BigDecimal(100).subtract(discount)).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal finalPrice() {
        return finalPrice(basePrice, discountPercentage);
    }

    @SuppressWarnings("unchecked")
    public static ListingInput from(Map<String, Object> m) {
        List<String> errors = new ArrayList<>();
        int step = 4;

        String title = str(m.get("title"));
        String description = str(m.get("description"));
        String notes = str(m.get("artistNotes"));
        String medium = str(m.get("medium"));
        String surface = str(m.get("surface"));
        List<ImageInput> images = new ArrayList<>();
        if (m.get("images") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> im) {
                    String url = str(((Map<String, Object>) im).get("url"));
                    if (url.startsWith("/storage/uploads/") || url.startsWith("/assets/images/")) {
                        images.add(new ImageInput(url, Boolean.TRUE.equals(im.get("primary")), cap(str(((Map<String, Object>) im).get("alt")), 255)));
                    }
                }
            }
        }
        if (title.length() < 3 || title.length() > 255) { errors.add("Give the painting a title (3–255 characters)."); step = Math.min(step, 1); }
        if (description.length() < 20) { errors.add("Describe the painting in at least 20 characters."); step = Math.min(step, 1); }
        if (description.length() > 6000) { errors.add("Keep the description under 6000 characters."); step = Math.min(step, 1); }
        if (notes.length() > 6000) { errors.add("Keep the artist's notes under 6000 characters."); step = Math.min(step, 1); }
        if (images.isEmpty()) { errors.add("Upload at least one high-resolution photo of the painting."); step = Math.min(step, 1); }
        if (images.size() > 12) { errors.add("Use up to 12 photos."); step = Math.min(step, 1); }
        if (images.stream().noneMatch(ImageInput::primary) && !images.isEmpty()) {
            ImageInput first = images.get(0);
            images.set(0, new ImageInput(first.url(), true, first.alt()));
        }

        if (medium.length() < 2 || medium.length() > 100) { errors.add("Choose the medium (for example Oil or Watercolour)."); step = Math.min(step, 2); }
        if (surface.length() < 2 || surface.length() > 100) { errors.add("Describe the surface (for example Stretched cotton canvas)."); step = Math.min(step, 2); }
        BigDecimal h = dec(m.get("heightInches"));
        BigDecimal w = dec(m.get("widthInches"));
        BigDecimal d = dec(m.get("depthInches"));
        BigDecimal kg = dec(m.get("weightKg"));
        if (h == null || h.compareTo(new BigDecimal("1")) < 0 || h.compareTo(new BigDecimal("240")) > 0) { errors.add("Height must be between 1 and 240 inches."); step = Math.min(step, 2); }
        if (w == null || w.compareTo(new BigDecimal("1")) < 0 || w.compareTo(new BigDecimal("240")) > 0) { errors.add("Width must be between 1 and 240 inches."); step = Math.min(step, 2); }
        if (d != null && (d.signum() < 0 || d.compareTo(new BigDecimal("24")) > 0)) { errors.add("Depth must be between 0 and 24 inches."); step = Math.min(step, 2); }
        if (kg != null && (kg.signum() < 0 || kg.compareTo(new BigDecimal("200")) > 0)) { errors.add("Weight must be between 0 and 200 kg."); step = Math.min(step, 2); }
        Integer year = m.get("yearCreated") == null ? null : intOrNull(m.get("yearCreated"));
        int thisYear = Year.now().getValue();
        if (year == null || year < 1900 || year > thisYear) { errors.add("Year created must be between 1900 and " + thisYear + "."); step = Math.min(step, 2); }
        boolean framed = Boolean.TRUE.equals(m.get("framed"));
        String frameDetails = str(m.get("frameDetails"));
        if (framed && frameDetails.length() < 3) { errors.add("Describe the frame (material and glazing)."); step = Math.min(step, 2); }

        BigDecimal base = dec(m.get("basePrice"));
        BigDecimal discount = dec(m.get("discountPercentage"));
        if (discount == null) discount = BigDecimal.ZERO;
        if (base == null || base.compareTo(MIN_PRICE) < 0 || base.compareTo(MAX_PRICE) > 0) { errors.add("Set a base price between ₹500 and ₹5,00,00,000."); step = Math.min(step, 3); }
        if (discount.signum() < 0 || discount.compareTo(MAX_DISCOUNT) > 0) { errors.add("Discount must be between 0% and 90%."); step = Math.min(step, 3); }

        if (!errors.isEmpty()) {
            throw new InvalidListingException(errors, step);
        }
        return new ListingInput(title, description, notes.isEmpty() ? null : notes, medium, surface,
                nullIfEmpty(cap(str(m.get("subject")), 100)), nullIfEmpty(cap(str(m.get("styleTags")), 255)),
                h.setScale(2, RoundingMode.HALF_UP), w.setScale(2, RoundingMode.HALF_UP),
                d == null ? BigDecimal.ZERO.setScale(2) : d.setScale(2, RoundingMode.HALF_UP),
                kg == null ? null : kg.setScale(2, RoundingMode.HALF_UP), year,
                framed, framed ? cap(frameDetails, 255) : null,
                !Boolean.FALSE.equals(m.get("signed")), !Boolean.FALSE.equals(m.get("certificate")),
                base.setScale(2, RoundingMode.HALF_UP), discount.setScale(2, RoundingMode.HALF_UP), images);
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String cap(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    static BigDecimal dec(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intOrNull(Object o) {
        try {
            return new BigDecimal(String.valueOf(o).trim()).intValueExact();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
