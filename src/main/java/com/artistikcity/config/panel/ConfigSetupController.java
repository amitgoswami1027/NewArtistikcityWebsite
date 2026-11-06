package com.artistikcity.config.panel;

import com.artistikcity.admin.AdminKit;
import com.artistikcity.admin.Audit;
import com.artistikcity.auth.sms.SmsSender;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Admin > System parameters > Configuration panel: payment, SMS and Google sign-in settings without editing files.
 * <ul>
 *   <li>{@code GET /admin/api/setup/config}: every field with its value and where it comes from; secrets come back as
 *       {@value ConfigCatalog#MASK} when set and empty when not, never in clear.</li>
 *   <li>{@code POST /admin/api/setup/config} {@code {values: {key: value}, reset: [key]}}: checked field by field (422 with
 *       an error per field, nothing saved, if any is wrong); a secret sent back as the mask is left as it is.</li>
 *   <li>{@code POST /admin/api/setup/sms-test} {@code {to}}: sends a test text through the current provider.</li>
 * </ul>
 * Capability {@code setup} (super admins only). Every save is in the security log, secrets only as "changed".
 */
@Controller
public class ConfigSetupController {

    static final String CAP = "setup";

    private final AdminKit kit;
    private final Json json;
    private final Audit audit;
    private final PanelConfig panel;
    private final Environment env;
    private final SmsSender sms;

    public ConfigSetupController(AdminKit kit, Json json, Audit audit, PanelConfig panel, Environment env, SmsSender sms) {
        this.kit = kit;
        this.json = json;
        this.audit = audit;
        this.panel = panel;
        this.env = env;
        this.sms = sms;
    }

    @GetMapping({"/admin/setup", "/admin/setup/", "/admin/setup/config"})
    public ResponseEntity<String> page(HttpServletRequest request) {
        return kit.page(request, CAP, "Admin/Setup/Config", () -> {
            Map<String, Object> p = new LinkedHashMap<>(kit.base(kit.admin(request)));
            p.putAll(payload());
            return p;
        });
    }

    @GetMapping("/admin/api/setup/config")
    public ResponseEntity<String> get(HttpServletRequest request) {
        Row admin = kit.admin(request);
        if (!kit.can(admin, CAP)) return kit.forbidden();
        return kit.ok(payload());
    }

    @PostMapping("/admin/api/setup/config")
    public ResponseEntity<String> save(HttpServletRequest request) throws IOException {
        Row admin = kit.admin(request);
        if (!kit.can(admin, CAP)) return kit.forbidden();
        Map<String, Object> body = json.decodeMap(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        if (body == null) return kit.fail(HttpStatus.BAD_REQUEST, "Send JSON: {\"values\": {\"key\": \"value\"}, \"reset\": [\"key\"]}.");
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, String> set = new LinkedHashMap<>();
        List<String> reset = new ArrayList<>();

        Object rawValues = body.getOrDefault("values", Map.of());
        if (!(rawValues instanceof Map<?, ?> values)) return kit.invalid(Map.of("values", "values must be an object of setting: value."));
        for (Map.Entry<?, ?> e : values.entrySet()) {
            String key = String.valueOf(e.getKey());
            ConfigCatalog.Field f = ConfigCatalog.field(key);
            if (f == null) { errors.put(key, "This setting can't be changed here."); continue; }
            Object raw = e.getValue();
            if (raw != null && !(raw instanceof String) && !(raw instanceof Number) && !(raw instanceof Boolean)) {
                errors.put(key, "Send this value as text."); continue;
            }
            String v = raw == null ? "" : String.valueOf(raw).trim();
            if (f.secret() && ConfigCatalog.MASK.equals(v)) continue;          // unchanged secret
            if (f.secret() && v.isEmpty()) continue;                           // empty secret box = keep; use reset to remove
            if (!f.secret() && v.equals(effective(f))) continue;               // nothing changed
            String problem = ConfigCatalog.check(f, v);
            if (problem != null) { errors.put(key, problem); continue; }
            if (f.secret() && !panel.canStoreSecrets()) {
                errors.put(key, "The server has no encryption key, so secrets can't be saved here. Set CONFIG_ENCRYPTION_KEY (or OTP_SECRET) on the server.");
                continue;
            }
            set.put(key, v);
        }
        Object rawReset = body.getOrDefault("reset", List.of());
        if (rawReset instanceof List<?> list) {
            for (Object o : list) {
                String key = String.valueOf(o);
                if (ConfigCatalog.field(key) == null) errors.put(key, "This setting can't be changed here.");
                else reset.add(key);
            }
        } else {
            errors.put("reset", "reset must be a list of setting names.");
        }
        crossCheck(set, reset, errors);
        if (!errors.isEmpty()) return kit.invalid(errors);
        if (set.isEmpty() && reset.isEmpty()) return kit.ok(Map.of("message", "Nothing changed.", "changed", List.of(), "config", payload()));

        Map<String, Object> before = snapshot();
        List<String> changed = panel.save(set, reset, admin.lng("id"));
        audit.change(admin, "config", 0, "configuration updated", String.join(", ", changed), before, snapshot());
        return kit.ok(Map.of("message", changed.size() == 1 ? "1 setting saved. It applies straight away." : changed.size() + " settings saved. They apply straight away.",
                "changed", changed, "config", payload()));
    }

    /** {to}: one test message through the provider currently in effect. */
    @PostMapping("/admin/api/setup/sms-test")
    public ResponseEntity<String> smsTest(HttpServletRequest request) throws IOException {
        Row admin = kit.admin(request);
        if (!kit.can(admin, CAP)) return kit.forbidden();
        Map<String, Object> body = json.decodeMap(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        String to = body == null ? "" : String.valueOf(body.getOrDefault("to", "")).replaceAll("[\\s()-]", "");
        if (!to.matches("^\\+[1-9]\\d{6,14}$")) return kit.invalid(Map.of("to", "Enter the number with its country code, e.g. +919876543210."));
        try {
            sms.send(to, "ArtistikCity test message: your SMS settings work.");
        } catch (SmsSender.SmsException e) {
            audit.log(admin, "config", 0, "sms test failed", sms.name() + " to " + mask(to));
            return kit.fail(HttpStatus.BAD_GATEWAY, "The provider refused the message: " + e.getMessage());
        }
        audit.log(admin, "config", 0, "sms test sent", sms.name() + " to " + mask(to));
        return kit.ok(Map.of("message", sms.isDevelopment() ? "Provider is \"log\": the message was written to the server log, not sent."
                : "Test message sent through " + sms.name() + "."));
    }

    // ------------------------------------------------------------------ helpers

    /** What the field is set to right now (panel, server or default). */
    private String effective(ConfigCatalog.Field f) {
        return env.getProperty(f.key(), "").trim();
    }

    /** The page / API shape: tabs > groups > fields with value, source and last change. */
    Map<String, Object> payload() {
        Map<String, Map<String, Object>> changes = panel.changes();
        List<String> unreadable = panel.unreadable();
        List<Map<String, Object>> tabs = new ArrayList<>();
        for (ConfigCatalog.Tab t : ConfigCatalog.TABS) {
            List<Map<String, Object>> groups = new ArrayList<>();
            for (ConfigCatalog.Group g : t.groups()) {
                List<Map<String, Object>> fields = new ArrayList<>();
                for (ConfigCatalog.Field f : g.fields()) {
                    Map<String, Object> m = f.describe();
                    String v = effective(f);
                    m.put("value", f.secret() ? (v.isEmpty() ? "" : ConfigCatalog.MASK) : v);
                    m.put("isSet", !v.isEmpty());
                    m.put("source", panel.source(f));
                    m.put("serverSet", !panel.serverValue(f.key()).isBlank());
                    m.put("changed", changes.get(f.key()));
                    m.put("unreadable", unreadable.contains(f.key()));
                    fields.add(m);
                }
                Map<String, Object> gm = new LinkedHashMap<>();
                gm.put("id", g.id());
                gm.put("title", g.title());
                gm.put("intro", g.intro());
                gm.put("status", g.status() != null ? g.status() : status(t.id(), g.id()));
                gm.put("fields", fields);
                groups.add(gm);
            }
            tabs.add(Map.of("id", t.id(), "title", t.title(), "icon", t.icon(), "groups", groups));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tabs", tabs);
        out.put("mask", ConfigCatalog.MASK);
        out.put("canStoreSecrets", panel.canStoreSecrets());
        out.put("googleCallback", googleCallback());
        out.put("smsProvider", sms.name());
        return out;
    }

    /** connected / incomplete / off for a group, from the effective values. */
    private String status(String tab, String group) {
        String key = switch (tab + "." + group) {
            case "payments.razorpay" -> live("app.payments.razorpay.enabled") ? both("razorpay.key", "razorpay.secret") : "off";
            case "payments.paypal" -> live("app.payments.paypal.enabled") ? both("paypal.client-id", "paypal.secret") : "off";
            case "sms.provider" -> "log".equalsIgnoreCase(env.getProperty("app.sms.provider", "log")) ? "development" : "connected";
            case "sms.twilio" -> "twilio".equalsIgnoreCase(env.getProperty("app.sms.provider", "")) ? both("app.sms.twilio.account-sid", "app.sms.twilio.auth-token") : "unused";
            case "sms.sns" -> "sns".equalsIgnoreCase(env.getProperty("app.sms.provider", "")) ? (env.getProperty("app.sms.sns.region", "").isBlank() ? "incomplete" : "connected") : "unused";
            case "sms.infobip" -> "infobip".equalsIgnoreCase(env.getProperty("app.sms.provider", "")) ? both("app.sms.infobip.base-url", "app.sms.infobip.api-key") : "unused";
            case "google.oauth" -> both("social.google.client-id", "social.google.client-secret");
            default -> null;
        };
        return key;
    }

    private boolean live(String key) {
        return !"false".equalsIgnoreCase(env.getProperty(key, "true"));
    }

    private String both(String a, String b) {
        boolean x = !env.getProperty(a, "").isBlank();
        boolean y = !env.getProperty(b, "").isBlank();
        return x && y ? "connected" : x || y ? "incomplete" : "not_set";
    }

    private String googleCallback() {
        String fixed = env.getProperty("social.google.redirect-uri", "").trim();
        if (!fixed.isEmpty()) return fixed;
        String base = env.getProperty("app.url", "").replaceAll("/+$", "");
        return (base.isEmpty() ? "{site address}" : base) + "/social-login/google/callback";
    }

    /** Rules across fields, on the values as they will be after this save. */
    private void crossCheck(Map<String, String> set, List<String> reset, Map<String, String> errors) {
        java.util.function.Function<String, String> after = k -> reset.contains(k) ? panel.serverValue(k).trim()
                : set.containsKey(k) ? set.get(k) : env.getProperty(k, "").trim();
        String provider = after.apply("app.sms.provider").toLowerCase();
        if (set.containsKey("app.sms.provider") || set.keySet().stream().anyMatch(k -> k.startsWith("app.sms."))) {
            switch (provider) {
                case "twilio" -> {
                    if (after.apply("app.sms.twilio.account-sid").isEmpty()) errors.putIfAbsent("app.sms.twilio.account-sid", "Twilio needs the Account SID.");
                    if (after.apply("app.sms.twilio.auth-token").isEmpty()) errors.putIfAbsent("app.sms.twilio.auth-token", "Twilio needs the auth token.");
                    if (after.apply("app.sms.twilio.from").isEmpty() && after.apply("app.sms.twilio.messaging-service-sid").isEmpty()) {
                        errors.putIfAbsent("app.sms.twilio.from", "Twilio needs a sender or a messaging service SID.");
                    }
                }
                case "sns" -> {
                    if (after.apply("app.sms.sns.region").isEmpty()) errors.putIfAbsent("app.sms.sns.region", "AWS SNS needs a region.");
                    boolean id = !after.apply("app.sms.sns.access-key-id").isEmpty();
                    boolean secret = !after.apply("app.sms.sns.secret-access-key").isEmpty();
                    if (id != secret) errors.putIfAbsent(id ? "app.sms.sns.secret-access-key" : "app.sms.sns.access-key-id",
                            "Enter both the access key ID and the secret, or neither (then the server's IAM role is used).");
                }
                case "infobip" -> {
                    if (after.apply("app.sms.infobip.base-url").isEmpty()) errors.putIfAbsent("app.sms.infobip.base-url", "Infobip needs your base URL.");
                    if (after.apply("app.sms.infobip.api-key").isEmpty()) errors.putIfAbsent("app.sms.infobip.api-key", "Infobip needs an API key.");
                }
                default -> { }
            }
        }
        if ("live".equals(after.apply("paypal.mode")) && set.containsKey("paypal.mode")
                && (after.apply("paypal.client-id").isEmpty() || after.apply("paypal.secret").isEmpty())) {
            errors.putIfAbsent("paypal.mode", "Add the live client ID and secret before switching PayPal to live.");
        }
        String rk = after.apply("razorpay.key");
        if (set.containsKey("razorpay.key") && !rk.isEmpty() && after.apply("razorpay.secret").isEmpty()) {
            errors.putIfAbsent("razorpay.secret", "Add the key secret that belongs to this key ID.");
        }
    }

    /** Values for the security log: plain settings as they are, secrets only as set / not set. */
    private Map<String, Object> snapshot() {
        Map<String, Object> m = new TreeMap<>();
        Map<String, Map<String, Object>> changes = panel.changes();
        for (ConfigCatalog.Field f : ConfigCatalog.fields()) {
            if (!panel.overridden(f.key())) continue;
            // a secret is never written to the log: only that it is set and when it was last saved
            m.put(f.key(), f.secret() ? "(secret, saved " + (changes.get(f.key()) == null ? "?" : changes.get(f.key()).get("at")) + ")" : env.getProperty(f.key(), ""));
        }
        return m;
    }

    private static String mask(String phone) {
        return phone.length() <= 5 ? phone : phone.substring(0, phone.length() - 4).replaceAll("\\d", "•") + phone.substring(phone.length() - 4);
    }
}
