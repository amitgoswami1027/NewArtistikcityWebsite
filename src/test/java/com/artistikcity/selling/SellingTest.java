package com.artistikcity.selling;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sell your art: who counts as certified, and the payout details checks and masking. */
class SellingTest {

    @Test
    void certifiedMeansEligibleOrCertificateIssued() {
        var creds = List.<Map<String, Object>>of(
                new HashMap<>(Map.of("course_id", 1, "eligible", true)),
                new HashMap<>(Map.of("course_id", 2, "eligible", false)),
                new HashMap<>(Map.of("course_id", 3, "eligible", false, "certificate", Map.of("certificate_no", "AC-1"))));
        assertEquals(java.util.Set.of(1L, 3L), StudentSellController.certifiedCourses(creds));
    }

    @Test
    void upiIsCheckedAndMasked() {
        Map<String, String> clean = new LinkedHashMap<>();
        assertTrue(PayoutDetails.validate(Map.of("method", "upi", "holderName", "Aarav Shah", "upiId", " Aarav.Shah@OKHDFCbank "), clean).isEmpty());
        assertEquals("aarav.shah@okhdfcbank", clean.get("upiId"));
        assertEquals("UPI ••••@okhdfcbank", PayoutDetails.mask(clean));
        assertTrue(PayoutDetails.validate(Map.of("method", "UPI", "holderName", "Aarav", "upiId", "no-at-sign"), new LinkedHashMap<>()).containsKey("upiId"));
    }

    @Test
    void bankAccountNeedsMatchingNumbersAndIfsc() {
        Map<String, String> clean = new LinkedHashMap<>();
        assertTrue(PayoutDetails.validate(Map.of("method", "BANK", "holderName", "Aarav Shah", "accountNumber", "1234 5678 9012",
                "accountNumberConfirm", "123456789012", "ifsc", "hdfc0001234"), clean).isEmpty());
        assertEquals("Bank account ••••9012 · HDFC0001234", PayoutDetails.mask(clean));
        var e = PayoutDetails.validate(Map.of("method", "BANK", "holderName", "A1", "accountNumber", "123456789012",
                "accountNumberConfirm", "123456789000", "ifsc", "HDFC1001234"), new LinkedHashMap<>());
        assertTrue(e.containsKey("accountNumberConfirm") && e.containsKey("ifsc") && e.containsKey("holderName"), e.toString());
    }

    @Test
    void paypalAndUnknownMethods() {
        Map<String, String> clean = new LinkedHashMap<>();
        assertTrue(PayoutDetails.validate(Map.of("method", "PAYPAL", "holderName", "Emily Clarke", "paypalEmail", "Emily@Example.com"), clean).isEmpty());
        assertEquals("PayPal e••••@example.com", PayoutDetails.mask(clean));
        assertTrue(PayoutDetails.validate(Map.of("method", "CRYPTO"), new LinkedHashMap<>()).containsKey("method"));
    }
}
