package com.artistikcity.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Typed access to the settings in the central configuration file {@code config/artistikcity.yml}. Every value has a
 * default there and an environment-variable override; the getters here only give them names and types, so features
 * never read raw property strings of their own.
 */
@Component
public class Settings {

    private final Environment env;

    public Settings(Environment env) {
        this.env = env;
    }

    private int integer(String key, int fallback) {
        String v = env.getProperty(key);
        try {
            return v == null || v.isBlank() ? fallback : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean flag(String key, boolean fallback) {
        String v = env.getProperty(key);
        return v == null || v.isBlank() ? fallback : Boolean.parseBoolean(v.trim());
    }

    private String text(String key, String fallback) {
        String v = env.getProperty(key);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    // ---- payments
    public boolean reconcileEnabled() { return flag("app.payments.reconcile.enabled", true); }
    public int reconcileEveryMinutes() { return Math.max(1, integer("app.payments.reconcile.every-minutes", 5)); }
    public int reconcileMinAgeMinutes() { return Math.max(1, integer("app.payments.reconcile.min-age-minutes", 10)); }
    public int reconcileMaxAgeDays() { return Math.max(1, integer("app.payments.reconcile.max-age-days", 7)); }
    public int reconcileAlertAfterMinutes() { return Math.max(1, integer("app.payments.reconcile.alert-after-minutes", 30)); }
    public String razorpayApiBase() { return stripSlash(text("app.payments.razorpay.api-base", "https://api.razorpay.com")); }
    /** Empty = derived from paypal.mode (sandbox / live). */
    public String paypalApiBase() { return stripSlash(text("app.payments.paypal.api-base", "")); }
    /** Where alerts go: app.alerts.email, else the admin e-mail. */
    public String alertEmail() { return text("app.alerts.email", text("app.admin-email", "")); }

    // ---- seats
    public int seatHoldMinutes() { return Math.max(1, integer("app.seats.hold-minutes", 15)); }

    // ---- ledger
    public BigDecimal commissionPercent() {
        try {
            BigDecimal p = new BigDecimal(text("app.ledger.commission-percent", "30"));
            return p.max(BigDecimal.ZERO).min(new BigDecimal("100"));
        } catch (NumberFormatException e) {
            return new BigDecimal("30");
        }
    }
    public int payoutAfterDays() { return Math.max(0, integer("app.ledger.payout-after-days", 14)); }
    public boolean payStudioArtists() { return flag("app.ledger.pay-studio-artists", false); }

    // ---- security
    public int refusalsBeforeRevoke() { return Math.max(1, integer("app.security.refusals-before-revoke", 5)); }
    public int refusalWindowMinutes() { return Math.max(1, integer("app.security.refusal-window-minutes", 10)); }
    public int auditExportMaxRows() { return Math.max(100, integer("app.security.audit-export-max-rows", 50000)); }
    public int newsletterSignupsPerMinute() { return Math.max(1, integer("app.newsletter.signups-per-minute", 5)); }

    // ---- uploads
    public boolean stripUploadMetadata() { return flag("app.uploads.strip-metadata", true); }
    public float jpegQuality() {
        try {
            float q = Float.parseFloat(text("app.uploads.jpeg-quality", "0.92"));
            return Math.max(0.5f, Math.min(1f, q));
        } catch (NumberFormatException e) {
            return 0.92f;
        }
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
