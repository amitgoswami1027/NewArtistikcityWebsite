package com.artistikcity.selling;

import com.artistikcity.config.panel.PanelConfig;
import com.artistikcity.config.panel.SecretBox;
import com.artistikcity.support.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where a student artist wants their marketplace earnings sent: UPI, an Indian bank account or PayPal (outside India).
 * Stored in {@code student_payout_details}: the details encrypted with the server key ({@link SecretBox}, the same key as
 * the Configuration Panel), plus a masked summary ("UPI ••••@okhdfc") that is safe to show. Only staff with the
 * {@code revenue} capability can read the full details, and each read is in the security log.
 */
@Service
public class PayoutDetails {

    public static final List<String> METHODS = List.of("UPI", "BANK", "PAYPAL");

    private final JdbcTemplate jdbc;
    private final PanelConfig panel;
    private final Json json;

    public PayoutDetails(JdbcTemplate jdbc, PanelConfig panel, Json json) {
        this.jdbc = jdbc;
        this.panel = panel;
        this.json = json;
    }

    public boolean canStore() {
        return panel.secretBox() != null;
    }

    /** Checks and normalises the form; returns field -> message for every problem (empty = fine). */
    public static Map<String, String> validate(Map<String, Object> in, Map<String, String> clean) {
        Map<String, String> errors = new LinkedHashMap<>();
        String method = str(in.get("method")).toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) {
            errors.put("method", "Choose UPI, bank account or PayPal.");
            return errors;
        }
        clean.put("method", method);
        String holder = str(in.get("holderName")).replaceAll("\\s+", " ");
        if (holder.length() < 2 || holder.length() > 100 || !holder.matches("[\\p{L} .'-]+")) {
            errors.put("holderName", "Enter the account holder's name as the bank or app shows it.");
        }
        clean.put("holderName", holder);
        switch (method) {
            case "UPI" -> {
                String upi = str(in.get("upiId")).toLowerCase(Locale.ROOT).replace(" ", "");
                if (!upi.matches("^[a-z0-9.\\-_]{2,256}@[a-z][a-z0-9]{1,63}$")) errors.put("upiId", "Enter a UPI ID like yourname@okhdfcbank.");
                clean.put("upiId", upi);
            }
            case "BANK" -> {
                String acc = str(in.get("accountNumber")).replaceAll("[\\s-]", "");
                String confirm = str(in.get("accountNumberConfirm")).replaceAll("[\\s-]", "");
                String ifsc = str(in.get("ifsc")).toUpperCase(Locale.ROOT).replace(" ", "");
                if (!acc.matches("^\\d{9,18}$")) errors.put("accountNumber", "An Indian account number is 9 to 18 digits.");
                else if (!acc.equals(confirm)) errors.put("accountNumberConfirm", "The two account numbers don't match.");
                if (!ifsc.matches("^[A-Z]{4}0[A-Z0-9]{6}$")) errors.put("ifsc", "An IFSC code looks like HDFC0001234 (11 characters, fifth is 0).");
                clean.put("accountNumber", acc);
                clean.put("ifsc", ifsc);
            }
            default -> {
                String email = str(in.get("paypalEmail")).toLowerCase(Locale.ROOT);
                if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$") || email.length() > 190) errors.put("paypalEmail", "Enter the e-mail address of your PayPal account.");
                clean.put("paypalEmail", email);
            }
        }
        return errors;
    }

    /** "UPI ••••@okhdfcbank", "Bank ••••1234 · HDFC0001234", "PayPal j••••@gmail.com". */
    public static String mask(Map<String, String> d) {
        return switch (d.get("method")) {
            case "UPI" -> "UPI ••••" + d.get("upiId").substring(d.get("upiId").indexOf('@'));
            case "BANK" -> "Bank account ••••" + d.get("accountNumber").substring(d.get("accountNumber").length() - 4) + " · " + d.get("ifsc");
            default -> {
                String e = d.get("paypalEmail");
                yield "PayPal " + e.charAt(0) + "••••" + e.substring(e.indexOf('@'));
            }
        };
    }

    public void save(long userId, Map<String, String> clean) {
        SecretBox box = panel.secretBox();
        if (box == null) throw new IllegalStateException("No encryption key on the server.");
        String enc = box.seal(json.encode(clean));
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        int n = jdbc.update("update student_payout_details set method = ?, holder_name = ?, details_enc = ?, masked = ?, updated_at = ? where user_id = ?",
                clean.get("method"), clean.get("holderName"), enc, mask(clean), now, userId);
        if (n == 0) {
            jdbc.update("insert into student_payout_details (user_id, method, holder_name, details_enc, masked, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
                    userId, clean.get("method"), clean.get("holderName"), enc, mask(clean), now, now);
        }
    }

    /** What the student (and staff lists) may see: method, holder, masked summary, last change. Null when none. */
    public Map<String, Object> summary(long userId) {
        try {
            List<Map<String, Object>> r = jdbc.queryForList("select method, holder_name, masked, updated_at from student_payout_details where user_id = ?", userId);
            return r.isEmpty() ? null : r.get(0);
        } catch (RuntimeException e) {
            return null;   // before the migration ran
        }
    }

    /** Full details for staff paying out; null when none or unreadable. */
    public Map<String, Object> full(long userId) {
        SecretBox box = panel.secretBox();
        List<Map<String, Object>> r = jdbc.queryForList("select details_enc, updated_at from student_payout_details where user_id = ?", userId);
        if (r.isEmpty()) return null;
        String plain = box == null ? null : box.open(String.valueOf(r.get(0).get("details_enc")));
        if (plain == null) return Map.of("unreadable", true);
        Map<String, Object> m = new LinkedHashMap<>(json.decodeMap(plain));
        m.put("updated_at", r.get(0).get("updated_at"));
        return m;
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
