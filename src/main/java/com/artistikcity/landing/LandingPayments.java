package com.artistikcity.landing;

import com.artistikcity.commission.CommissionPayments;
import com.artistikcity.support.Db;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Payment settings per landing page, on top of the global gateway setup (keys stay in the Configuration Panel).
 * <ul>
 *   <li><b>Global defaults</b> (settings tag {@code landing}, key {@code payments}): used by every page in mode GLOBAL.</li>
 *   <li><b>Custom</b> (landing_payment_settings / _providers / _methods): this page only; other pages never change.</li>
 * </ul>
 * Four layers decide what a buyer sees (the admin screen shows all four): the method exists in {@link LandingCatalog#METHODS};
 * a provider switched on for the page supports it; it is switched on for the page; and that provider is connected right now
 * (keys set, or test mode). Only then does {@link #choices} offer it, and {@link #razorpayDisplay} tells Razorpay Checkout to
 * show only those instruments. The server, not the browser, builds that list.
 */
@Service
public class LandingPayments {

    private final Db db;
    private final Json json;
    private final CommissionPayments gateways;

    public LandingPayments(Db db, Json json, CommissionPayments gateways) {
        this.db = db;
        this.json = json;
        this.gateways = gateways;
    }

    // ------------------------------------------------------------------------------------------------ the plan

    public record ProviderSetting(String key, boolean enabled, int priority) {
    }

    public record Plan(String mode, String defaultProvider, List<ProviderSetting> providers, List<String> enabledMethods, List<String> order) {
        public boolean providerOn(String key) {
            return providers.stream().anyMatch(p -> p.key().equals(key) && p.enabled());
        }
    }

    public Plan defaults() {
        Row r = db.first("select meta_value from settings where meta_tag = 'landing_pages' and meta_key = 'payments' order by id desc limit 1");
        Map<String, Object> m = r == null ? null : json.decodeMap(r.str("meta_value"));
        if (m == null) {
            return new Plan("GLOBAL", "razorpay", List.of(new ProviderSetting("razorpay", true, 1), new ProviderSetting("paypal", true, 2)),
                    LandingCatalog.DEFAULT_METHODS, LandingCatalog.METHODS.stream().map(LandingCatalog.Method::key).toList());
        }
        return fromMap("GLOBAL", m);
    }

    public Plan forPage(long pageId) {
        Row s = db.first("select mode, default_provider from landing_payment_settings where landing_page_id = ?", pageId);
        if (s == null || !"CUSTOM".equals(s.str("mode"))) {
            Plan d = defaults();
            return new Plan("GLOBAL", d.defaultProvider(), d.providers(), d.enabledMethods(), d.order());
        }
        List<ProviderSetting> providers = new ArrayList<>();
        for (Row p : db.select("select provider, enabled, priority from landing_payment_providers where landing_page_id = ? order by priority, id", pageId)) {
            providers.add(new ProviderSetting(p.str("provider"), p.lng("enabled", 0) == 1, (int) p.lng("priority", 1)));
        }
        List<String> enabled = new ArrayList<>();
        List<String> order = new ArrayList<>();
        for (Row m : db.select("select method, enabled from landing_payment_methods where landing_page_id = ? order by sort_order, id", pageId)) {
            order.add(m.str("method"));
            if (m.lng("enabled", 0) == 1) {
                enabled.add(m.str("method"));
            }
        }
        return new Plan("CUSTOM", s.str("default_provider", "razorpay"), providers, enabled, completeOrder(order));
    }

    private static List<String> completeOrder(List<String> order) {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        for (String k : order) {
            if (LandingCatalog.METHOD_KEYS.contains(k)) {
                all.add(k);
            }
        }
        LandingCatalog.METHODS.forEach(m -> all.add(m.key()));
        return new ArrayList<>(all);
    }

    @SuppressWarnings("unchecked")
    private Plan fromMap(String mode, Map<String, Object> m) {
        List<ProviderSetting> providers = new ArrayList<>();
        for (Object o : LandingConfig.list(m.get("providers"))) {
            Map<String, Object> p = LandingConfig.map(o);
            providers.add(new ProviderSetting(LandingConfig.str(p.get("key")), LandingConfig.bool(p.get("enabled"), false), num(p.get("priority"), 1)));
        }
        List<String> enabled = new ArrayList<>();
        List<String> order = new ArrayList<>();
        for (Object o : LandingConfig.list(m.get("methods"))) {
            Map<String, Object> x = LandingConfig.map(o);
            String k = LandingConfig.str(x.get("key"));
            order.add(k);
            if (LandingConfig.bool(x.get("enabled"), false)) {
                enabled.add(k);
            }
        }
        return new Plan(mode, LandingConfig.str(m.get("defaultProvider")), providers, enabled, completeOrder(order));
    }

    private static int num(Object o, int def) {
        try {
            return Integer.parseInt(String.valueOf(o).replace(".0", ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ------------------------------------------------------------------------------------------- provider state

    // --------------------------------------------------------------------------- simulated Razorpay (no keys needed)

    private volatile Boolean simulated;
    private volatile long simulatedAt;

    /**
     * Admin › Landing › Payments & checkout › Test payments › "Simulate Razorpay": with no Razorpay keys on the server,
     * Razorpay counts as connected for landing pages, but every payment is simulated and only test buyers can make one
     * (everybody else is told online payment isn't open yet). Settings tag landing_pages, key simulate_razorpay.
     */
    public boolean simulateRazorpaySetting() {
        if (simulated == null || System.currentTimeMillis() - simulatedAt > 10_000) {
            Row r = db.first("select meta_value from settings where meta_tag = 'landing_pages' and meta_key = 'simulate_razorpay' order by id desc limit 1");
            simulated = r != null && "1".equals(r.str("meta_value"));
            simulatedAt = System.currentTimeMillis();
        }
        return simulated;
    }

    /** Razorpay is simulated right now: the switch is on and the server has no Razorpay keys (keys always win). */
    public boolean razorpaySimulated() {
        return !gateways.razorpayConfigured() && !gateways.testMode() && simulateRazorpaySetting();
    }

    public void saveSimulateRazorpay(boolean on) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int n = db.update("update settings set meta_value = ?, updated_at = ? where meta_tag = 'landing_pages' and meta_key = 'simulate_razorpay'", on ? "1" : "0", now);
        if (n == 0) {
            db.insert("settings", Row.of("meta_tag", "landing_pages", "meta_key", "simulate_razorpay", "meta_value", on ? "1" : "0", "created_at", now, "updated_at", now));
        }
        simulated = on;
        simulatedAt = System.currentTimeMillis();
    }

    private volatile Boolean everyone;
    private volatile long everyoneAt;

    /**
     * Who may pay with simulated Razorpay: false = test buyers only (default), true = every visitor (testing a whole
     * landing page as a new buyer; their orders are test orders too, and they do get the course).
     */
    public boolean simulateForEveryone() {
        if (everyone == null || System.currentTimeMillis() - everyoneAt > 10_000) {
            Row r = db.first("select meta_value from settings where meta_tag = 'landing_pages' and meta_key = 'simulate_razorpay_all' order by id desc limit 1");
            everyone = r != null && "1".equals(r.str("meta_value"));
            everyoneAt = System.currentTimeMillis();
        }
        return everyone;
    }

    public void saveSimulateForEveryone(boolean on) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int n = db.update("update settings set meta_value = ?, updated_at = ? where meta_tag = 'landing_pages' and meta_key = 'simulate_razorpay_all'", on ? "1" : "0", now);
        if (n == 0) {
            db.insert("settings", Row.of("meta_tag", "landing_pages", "meta_key", "simulate_razorpay_all", "meta_value", on ? "1" : "0", "created_at", now, "updated_at", now));
        }
        everyone = on;
        everyoneAt = System.currentTimeMillis();
    }

    /** connected = can take a payment right now (keys set, or simulated in test mode). */
    public boolean connected(String provider) {
        return switch (provider) {
            case "razorpay" -> gateways.razorpayConfigured() || gateways.testMode() || simulateRazorpaySetting();
            case "paypal" -> gateways.paypalConfigured() || gateways.testMode();
            default -> false;
        };
    }

    public String status(String provider) {
        LandingCatalog.Provider p = LandingCatalog.provider(provider);
        if (p == null || !p.integrated()) {
            return "Not connected (adapter not built yet)";
        }
        return switch (provider) {
            case "razorpay" -> gateways.razorpayConfigured()
                    ? ("live".equals(gateways.razorpayKeyMode()) ? "Connected · live keys" : "Connected · test keys")
                    : gateways.testMode() ? "Test mode · payments are simulated"
                    : simulateRazorpaySetting() ? (simulateForEveryone() ? "Simulated · every visitor, no real payments" : "Simulated · test buyers and simulated-code accounts, no real payments") : "Keys not set";
            case "paypal" -> gateways.paypalConfigured() ? "Connected · " + gateways.paypalMode()
                    : gateways.testMode() ? "Test mode · payments are simulated" : "Keys not set";
            default -> "Not connected";
        };
    }

    /** null when the provider answers; otherwise what is wrong (no secrets in the text). */
    public String test(String provider) {
        return switch (provider) {
            case "razorpay" -> gateways.razorpayConfigured() ? gateways.razorpayPing() : gateways.testMode() || simulateRazorpaySetting() ? null : gateways.razorpayPing();
            case "paypal" -> gateways.paypalConfigured() ? gateways.paypalPing() : gateways.testMode() ? null : gateways.paypalPing();
            default -> "This provider is not connected yet: its adapter is planned.";
        };
    }

    // ----------------------------------------------------------------------------------------- admin screen view

    /** Everything the payment settings screen shows, with the four layers per method. */
    public Map<String, Object> view(Plan plan) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("mode", plan.mode());
        v.put("defaultProvider", plan.defaultProvider());
        List<Map<String, Object>> providers = new ArrayList<>();
        for (LandingCatalog.Provider p : LandingCatalog.PROVIDERS) {
            ProviderSetting s = plan.providers().stream().filter(x -> x.key().equals(p.key())).findFirst().orElse(new ProviderSetting(p.key(), false, 9));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", p.key());
            m.put("label", p.label());
            m.put("currency", p.currency());
            m.put("integrated", p.integrated());
            m.put("enabled", s.enabled() && p.integrated());
            m.put("priority", s.priority());
            m.put("connected", connected(p.key()));
            m.put("status", status(p.key()));
            m.put("note", p.note());
            m.put("methods", p.methods());
            providers.add(m);
        }
        providers.sort((a, b) -> Integer.compare((int) a.get("priority"), (int) b.get("priority")));
        v.put("providers", providers);
        List<Map<String, Object>> methods = new ArrayList<>();
        for (String key : plan.order()) {
            LandingCatalog.Method meth = LandingCatalog.METHODS.stream().filter(x -> x.key().equals(key)).findFirst().orElse(null);
            if (meth == null) {
                continue;
            }
            List<String> supportedBy = LandingCatalog.PROVIDERS.stream().filter(p -> p.methods().contains(key)).map(LandingCatalog.Provider::key).toList();
            List<String> onFor = supportedBy.stream().filter(plan::providerOn).toList();
            boolean enabled = plan.enabledMethods().contains(key);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("label", meth.label());
            m.put("group", meth.group());
            m.put("hint", meth.hint());
            m.put("supportedBy", supportedBy);
            m.put("supportedForPage", !onFor.isEmpty());
            m.put("enabled", enabled);
            m.put("live", enabled && onFor.stream().anyMatch(this::connected));
            methods.add(m);
        }
        v.put("methods", methods);
        return v;
    }

    // ---------------------------------------------------------------------------------------------- validation

    /** Cleans and checks a plan sent by the admin screen. Returns field -> message; empty when it can be saved. */
    public Map<String, String> validate(Map<String, Object> in, List<ProviderSetting> providersOut, List<String[]> methodsOut) {
        Map<String, String> errors = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object o : LandingConfig.list(in.get("providers"))) {
            Map<String, Object> p = LandingConfig.map(o);
            String key = LandingConfig.str(p.get("key"));
            LandingCatalog.Provider prov = LandingCatalog.provider(key);
            if (prov == null || !seen.add(key)) {
                continue;
            }
            boolean on = LandingConfig.bool(p.get("enabled"), false);
            if (on && !prov.integrated()) {
                errors.put("providers." + key, prov.label() + " is not connected yet, so it can't be switched on.");
                on = false;
            }
            int priority = Math.max(1, Math.min(9, num(p.get("priority"), 1)));
            providersOut.add(new ProviderSetting(key, on, priority));
        }
        String def = LandingConfig.str(in.get("defaultProvider"));
        if (providersOut.stream().noneMatch(ProviderSetting::enabled)) {
            errors.put("providers", "Switch on at least one payment provider.");
        } else if (providersOut.stream().noneMatch(p -> p.key().equals(def) && p.enabled())) {
            errors.put("defaultProvider", "The default provider must be one that is switched on.");
        }
        Set<String> mseen = new LinkedHashSet<>();
        int order = 0;
        Set<String> enabled = new LinkedHashSet<>();
        for (Object o : LandingConfig.list(in.get("methods"))) {
            Map<String, Object> m = LandingConfig.map(o);
            String key = LandingConfig.str(m.get("key"));
            if (!LandingCatalog.METHOD_KEYS.contains(key) || !mseen.add(key)) {
                continue;
            }
            boolean on = LandingConfig.bool(m.get("enabled"), false);
            if (on) {
                boolean supported = LandingCatalog.PROVIDERS.stream().anyMatch(p -> p.methods().contains(key)
                        && providersOut.stream().anyMatch(s -> s.key().equals(p.key()) && s.enabled()));
                if (!supported) {
                    errors.put("methods." + key, label(key) + " isn't supported by any provider switched on for this page.");
                    on = false;
                } else {
                    enabled.add(key);
                }
            }
            methodsOut.add(new String[]{key, on ? "1" : "0", String.valueOf(order++)});
        }
        if (enabled.stream().noneMatch(k -> !LandingCatalog.NETWORKS.contains(k))) {
            errors.put("methods", "Switch on at least one way to pay.");
        }
        if (enabled.stream().anyMatch(LandingCatalog.NETWORKS::contains) && !enabled.contains("credit_card") && !enabled.contains("debit_card")) {
            errors.put("methods.visa", "Card networks only apply when credit or debit cards are switched on.");
        }
        return errors;
    }

    private static String label(String key) {
        return LandingCatalog.METHODS.stream().filter(m -> m.key().equals(key)).map(LandingCatalog.Method::label).findFirst().orElse(key);
    }

    public void saveDefaults(String defaultProvider, List<ProviderSetting> providers, List<String[]> methods, long adminId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("defaultProvider", defaultProvider);
        m.put("providers", providers.stream().map(p -> Map.of("key", p.key(), "enabled", p.enabled(), "priority", p.priority())).toList());
        m.put("methods", methods.stream().map(x -> Map.of("key", x[0], "enabled", "1".equals(x[1]))).toList());
        LocalDateTime now = LocalDateTime.now();
        int n = db.update("update settings set meta_value = ?, updated_at = ? where meta_tag = 'landing_pages' and meta_key = 'payments'", json.encode(m), now);
        if (n == 0) {
            db.insert("settings", Row.of("meta_tag", "landing_pages", "meta_key", "payments", "meta_value", json.encode(m), "created_at", now, "updated_at", now));
        }
    }

    public void savePage(long pageId, String mode, String defaultProvider, List<ProviderSetting> providers, List<String[]> methods, long adminId) {
        LocalDateTime now = LocalDateTime.now();
        int n = db.update("update landing_payment_settings set mode = ?, default_provider = ?, updated_by = ?, updated_at = ? where landing_page_id = ?",
                mode, defaultProvider, adminId, now, pageId);
        if (n == 0) {
            db.insertPlain("landing_payment_settings", Row.of("landing_page_id", pageId, "mode", mode, "default_provider", defaultProvider,
                    "updated_by", adminId, "updated_at", now));
        }
        if ("CUSTOM".equals(mode)) {
            db.update("delete from landing_payment_providers where landing_page_id = ?", pageId);
            for (ProviderSetting p : providers) {
                db.insertReturningId("landing_payment_providers", Row.of("landing_page_id", pageId, "provider", p.key(), "enabled", p.enabled() ? 1 : 0, "priority", p.priority()));
            }
            db.update("delete from landing_payment_methods where landing_page_id = ?", pageId);
            for (String[] m : methods) {
                db.insertReturningId("landing_payment_methods", Row.of("landing_page_id", pageId, "method", m[0], "enabled", Integer.parseInt(m[1]), "sort_order", Integer.parseInt(m[2])));
            }
        }
    }

    // ------------------------------------------------------------------------------------------ public checkout

    /** What a buyer can choose (ordered as the admin set it): upi, card, netbanking, wallet, emi, paypal. */
    public record Choice(String key, String label, String hint, String provider, String currency, List<String> logos) {
    }

    public List<Choice> choices(Plan plan, boolean hasUsdPrice) {
        boolean rzp = plan.providerOn("razorpay") && connected("razorpay");
        boolean pp = plan.providerOn("paypal") && connected("paypal") && hasUsdPrice;
        Set<String> on = new LinkedHashSet<>(plan.enabledMethods());
        LinkedHashMap<String, Choice> out = new LinkedHashMap<>();
        for (String key : plan.order()) {
            if (!on.contains(key)) {
                continue;
            }
            String choice = switch (key) {
                case "upi", "upi_qr", "upi_intent", "gpay", "phonepe", "paytm" -> rzp ? "upi" : null;
                case "credit_card", "debit_card" -> rzp ? "card" : null;
                case "intl_cards" -> rzp ? "card" : pp ? "paypal" : null;
                case "netbanking" -> rzp ? "netbanking" : null;
                case "wallets" -> rzp ? "wallet" : null;
                case "emi", "nocost_emi" -> rzp ? "emi" : null;
                case "paypal" -> pp ? "paypal" : null;
                default -> null;
            };
            if (choice == null || out.containsKey(choice)) {
                continue;
            }
            out.put(choice, switch (choice) {
                case "upi" -> new Choice("upi", "UPI", upiHint(on), "razorpay", "INR", upiLogos(on));
                case "card" -> new Choice("card", cardLabel(on), cardHint(on), "razorpay", "INR", cardLogos(on));
                case "netbanking" -> new Choice("netbanking", "Net banking", "All major Indian banks", "razorpay", "INR", List.of());
                case "wallet" -> new Choice("wallet", "Wallets", "Paytm, PhonePe, Amazon Pay and more", "razorpay", "INR", List.of());
                case "emi" -> new Choice("emi", on.contains("nocost_emi") ? "EMI (no-cost where offered)" : "EMI", "Pay in monthly instalments on your card", "razorpay", "INR", List.of());
                default -> new Choice("paypal", "PayPal", "For buyers outside India · charged in USD", "paypal", "USD", List.of("paypal"));
            });
        }
        return new ArrayList<>(out.values());
    }

    private static String upiHint(Set<String> on) {
        List<String> apps = new ArrayList<>();
        if (on.contains("gpay")) apps.add("Google Pay");
        if (on.contains("phonepe")) apps.add("PhonePe");
        if (on.contains("paytm")) apps.add("Paytm");
        String base = on.contains("upi_qr") && (on.contains("upi_intent") || !apps.isEmpty()) ? "Scan a QR or pay in your UPI app" : on.contains("upi_qr") ? "Scan a QR code" : "Pay from any UPI app";
        return apps.isEmpty() ? base : base + " · " + String.join(", ", apps);
    }

    private static List<String> upiLogos(Set<String> on) {
        List<String> l = new ArrayList<>(List.of("upi"));
        if (on.contains("gpay")) l.add("gpay");
        if (on.contains("phonepe")) l.add("phonepe");
        if (on.contains("paytm")) l.add("paytm");
        return l;
    }

    private static String cardLabel(Set<String> on) {
        boolean c = on.contains("credit_card");
        boolean d = on.contains("debit_card");
        return c && d ? "Credit or debit card" : c ? "Credit card" : d ? "Debit card" : "Card";
    }

    private static String cardHint(Set<String> on) {
        List<String> nets = networks(on);
        String s = nets.isEmpty() ? "Visa, Mastercard, RuPay" : String.join(", ", nets);
        return on.contains("intl_cards") ? s + " · cards from outside India accepted" : s + " · cards issued in India";
    }

    private static List<String> cardLogos(Set<String> on) {
        List<String> nets = networks(on);
        return (nets.isEmpty() ? List.of("Visa", "Mastercard", "RuPay") : nets).stream().map(String::toLowerCase).toList();
    }

    private static List<String> networks(Set<String> on) {
        List<String> n = new ArrayList<>();
        if (on.contains("visa")) n.add("Visa");
        if (on.contains("mastercard")) n.add("Mastercard");
        if (on.contains("rupay")) n.add("RuPay");
        return n;
    }

    /**
     * Razorpay Checkout "config.display" limiting the payment sheet to this choice and the page's methods
     * (https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/configure-payment-methods/).
     */
    public Map<String, Object> razorpayDisplay(Plan plan, String choice) {
        Set<String> on = new LinkedHashSet<>(plan.enabledMethods());
        List<Map<String, Object>> instruments = new ArrayList<>();
        switch (choice) {
            case "upi" -> {
                List<String> flows = new ArrayList<>();
                if (on.contains("upi")) flows.add("collect");
                if (on.contains("upi_qr")) flows.add("qr");
                if (on.contains("upi_intent") || on.contains("gpay") || on.contains("phonepe") || on.contains("paytm")) flows.add("intent");
                Map<String, Object> i = new LinkedHashMap<>();
                i.put("method", "upi");
                i.put("flows", flows.isEmpty() ? List.of("collect", "qr", "intent") : flows);
                List<String> apps = new ArrayList<>();
                if (on.contains("gpay")) apps.add("google_pay");
                if (on.contains("phonepe")) apps.add("phonepe");
                if (on.contains("paytm")) apps.add("paytm");
                if (!apps.isEmpty() && !on.contains("upi_intent")) i.put("apps", apps);
                instruments.add(i);
            }
            case "card" -> {
                Map<String, Object> i = new LinkedHashMap<>();
                i.put("method", "card");
                List<String> types = new ArrayList<>();
                if (on.contains("credit_card") || on.contains("intl_cards")) types.add("credit");
                if (on.contains("debit_card") || on.contains("intl_cards")) types.add("debit");
                i.put("types", types);
                List<String> nets = networks(on).stream().map(n -> "Mastercard".equals(n) ? "MasterCard" : n).toList();
                if (!nets.isEmpty()) i.put("networks", nets);
                instruments.add(i);
            }
            case "netbanking" -> instruments.add(Map.of("method", "netbanking"));
            case "wallet" -> instruments.add(Map.of("method", "wallet"));
            case "emi" -> instruments.add(Map.of("method", "emi"));
            default -> { }
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("name", "Pay by " + choice);
        block.put("instruments", instruments);
        Map<String, Object> display = new LinkedHashMap<>();
        display.put("blocks", Map.of("chosen", block));
        display.put("sequence", List.of("block.chosen"));
        display.put("preferences", Map.of("show_default_blocks", false));
        return Map.of("display", display);
    }

    /** Razorpay's payment.method values that each choice allows (for the after-payment check). */
    public static Set<String> razorpayMethodsFor(String choice) {
        return switch (choice == null ? "" : choice) {
            case "upi" -> Set.of("upi");
            case "card" -> Set.of("card");
            case "netbanking" -> Set.of("netbanking");
            case "wallet" -> Set.of("wallet");
            case "emi" -> Set.of("emi", "cardless_emi", "card");
            default -> Set.of();
        };
    }
}
