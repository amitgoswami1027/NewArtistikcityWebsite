package com.artistikcity.commission;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The wizard state posted to /api/commissions/initialize-checkout, normalised and validated.
 * Use {@link #from(Map)}; it throws {@link InvalidCommissionException} listing every problem.
 */
public record CommissionRequest(
        String theme, String fulfillmentType, String size, String orientation, String frameMaterial, boolean hasMatting,
        String photoUrl, String instructions, String name, String email, String phone,
        String addressLine1, String city, String postalCode, String country, String paymentMethod) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHOTO = Pattern.compile("^/storage/uploads/commissions/[A-Za-z0-9-]+\\.(jpg|png)$");
    public static final List<String> PAYMENT_METHODS = List.of("CREDIT_CARD", "RAZORPAY_UPI", "PAYPAL");

    public boolean digital() {
        return "DIGITAL_ONLY".equals(fulfillmentType);
    }

    /** Width x height in inches, honouring the chosen orientation. */
    public int[] dimensions() {
        String[] p = size.split("x");
        int a = Integer.parseInt(p[0]);
        int b = Integer.parseInt(p[1]);
        return "LANDSCAPE".equals(orientation) ? new int[]{Math.max(a, b), Math.min(a, b)} : new int[]{Math.min(a, b), Math.max(a, b)};
    }

    public static CommissionRequest from(Map<String, Object> in) {
        Map<String, Object> contact = map(in.get("contact"));
        Map<String, Object> shipping = map(in.get("shipping"));
        String fulfillment = up(in.get("fulfillmentType"));
        boolean digital = "DIGITAL_ONLY".equals(fulfillment);
        CommissionRequest r = new CommissionRequest(
                up(in.get("theme")), fulfillment, str(in.get("size")), up(in.get("orientation")).isEmpty() ? "PORTRAIT" : up(in.get("orientation")),
                digital ? "NONE" : (up(in.get("frameMaterial")).isEmpty() ? "NONE" : up(in.get("frameMaterial"))),
                !digital && Boolean.TRUE.equals(in.get("hasMatting")),
                str(in.get("photoUrl")), limit(str(in.get("instructions")), 4000),
                str(contact.get("name")), str(contact.get("email")).toLowerCase(), str(contact.get("phone")),
                digital ? null : str(shipping.get("line1")), digital ? null : str(shipping.get("city")),
                digital ? null : str(shipping.get("postalCode")), digital ? null : str(shipping.get("country")),
                up(in.get("paymentMethod")));

        List<String> errors = new ArrayList<>();
        if (!CommissionPricing.THEMES.contains(r.theme)) errors.add("Choose an artwork theme.");
        if (!CommissionPricing.FULFILLMENTS.contains(r.fulfillmentType)) errors.add("Choose a delivery option.");
        if (!CommissionPricing.SIZES.contains(r.size)) errors.add("Choose a size.");
        if (!List.of("PORTRAIT", "LANDSCAPE").contains(r.orientation)) errors.add("Choose an orientation.");
        if (!CommissionPricing.FRAMES.contains(r.frameMaterial)) errors.add("Choose a frame.");
        if (!PHOTO.matcher(r.photoUrl).matches()) errors.add("Upload your reference photo.");
        if (r.name.length() < 2 || r.name.length() > 255) errors.add("Enter your full name.");
        if (!EMAIL.matcher(r.email).matches() || r.email.length() > 255) errors.add("Enter a valid email address.");
        if (r.phone.length() > 50 || !r.phone.matches("^[0-9+()\\-\\s]{0,50}$")) errors.add("Enter a valid phone number.");
        if (!digital) {
            if (r.addressLine1.isEmpty() || r.addressLine1.length() > 255) errors.add("Enter your street address.");
            if (r.city.isEmpty() || r.city.length() > 100) errors.add("Enter your city.");
            if (r.postalCode.isEmpty() || r.postalCode.length() > 20) errors.add("Enter your postal code.");
            if (r.country.isEmpty() || r.country.length() > 100) errors.add("Enter your country.");
        }
        if (!PAYMENT_METHODS.contains(r.paymentMethod)) errors.add("Choose a payment method.");
        if (!errors.isEmpty()) {
            throw new InvalidCommissionException(errors);
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String up(Object o) {
        return str(o).toUpperCase();
    }

    private static String limit(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    public static class InvalidCommissionException extends RuntimeException {
        private final List<String> errors;

        public InvalidCommissionException(List<String> errors) {
            super(String.join(" ", errors));
            this.errors = errors;
        }

        public List<String> errors() {
            return errors;
        }
    }
}
