package com.artistikcity.lifecycle;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Request / response types for the Student Creative Lifecycle APIs. */
public final class LifecycleDtos {

    private LifecycleDtos() {
    }

    public static final List<String> REVIEW_STATUSES = List.of("Approved", "Rejected");

    /** PATCH /api/admin/submissions/{id}/review */
    public record ReviewRequest(String status, String notes) {
        public static ReviewRequest from(Map<String, Object> m) {
            return new ReviewRequest(str(m.get("status")), str(m.get("notes")));
        }
    }

    /** PUT /api/portfolio/marketplace/toggle */
    public record MarketplaceToggleRequest(Long submissionId, boolean isListedForSale, BigDecimal salePrice, Integer inventoryCount) {
        public static MarketplaceToggleRequest from(Map<String, Object> m) {
            return new MarketplaceToggleRequest(lng(m.get("submissionId")), Boolean.TRUE.equals(m.get("isListedForSale")),
                    dec(m.get("salePrice")), m.get("inventoryCount") == null ? null : (int) (long) lng(m.get("inventoryCount")));
        }
    }

    /** Response of POST /api/submissions/upload */
    public record SubmissionCreated(long id, String fileUrl, String status) {
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    static Long lng(Object o) {
        if (o instanceof Number n) return n.longValue();
        try {
            return o == null || String.valueOf(o).isBlank() ? null : Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static BigDecimal dec(Object o) {
        try {
            return o == null || String.valueOf(o).isBlank() ? null : new BigDecimal(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Thrown for business-rule violations; mapped to HTTP 4xx with the message shown to the user. */
    public static class LifecycleException extends RuntimeException {
        private final int status;

        public LifecycleException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
