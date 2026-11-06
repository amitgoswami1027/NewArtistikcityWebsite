package com.artistikcity.payments;

import com.artistikcity.http.Auth;
import com.artistikcity.service.PaymentGateways;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import com.artistikcity.config.Settings;
import org.springframework.stereotype.Component;

/**
 * Who may simulate a payment.
 * <ul>
 *   <li>Local development: {@code PAYMENT_TEST_MODE=true} (every visitor, and only when a gateway has no keys).</li>
 *   <li>The live site: a signed-in account a super admin marked "Test buyer" ({@code users.test_buyer = 1}), while
 *       {@code app.payments.test-buyers.enabled} is on. Real customers never see the option.</li>
 * </ul>
 * Orders paid this way are stored with {@code is_test = 1}: they complete the whole flow (enrolment, e-mails, the
 * artwork marked sold) but stay out of revenue, the ledger, payouts and the payment reconciler.
 */
@Component
public class TestPayments {

    private final Db db;
    private final Auth auth;
    private final Settings settings;
    private final PaymentGateways gateways;

    public TestPayments(Db db, Auth auth, Settings settings, PaymentGateways gateways) {
        this.db = db;
        this.auth = auth;
        this.settings = settings;
        this.gateways = gateways;
    }

    /** The site-wide development switch (PAYMENT_TEST_MODE). */
    public boolean devMode() {
        return gateways.testMode();
    }

    public boolean testBuyersEnabled() {
        return settings.testBuyersEnabled();
    }

    /** The signed-in student is a test buyer (and the feature is on). */
    public boolean isTestBuyer(HttpServletRequest request) {
        if (!testBuyersEnabled()) return false;
        Long uid = auth.userId(request);
        if (uid == null) return false;
        Row u = db.first("select test_buyer from users where id = ? and status = 1", uid);
        return u != null && u.lng("test_buyer", 0) == 1;
    }

    /**
     * May this request simulate a payment? {@code gatewayLive} = the chosen gateway has real keys: development
     * mode only simulates where there are none; a test buyer may simulate even when the live gateway is configured.
     */
    public boolean allowed(HttpServletRequest request, boolean gatewayLive) {
        return (devMode() && !gatewayLive) || isTestBuyer(request);
    }

    /** Shown at checkout: the "Simulate payment" button for test buyers. */
    public boolean offerSimulation(HttpServletRequest request) {
        return isTestBuyer(request);
    }
}
