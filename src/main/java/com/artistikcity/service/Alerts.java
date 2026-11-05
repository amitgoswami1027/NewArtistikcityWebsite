package com.artistikcity.service;

import com.artistikcity.config.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * E-mails an alert to the staff (app.alerts.email, else the admin e-mail): security revocations, payments matched late,
 * refunds waiting for a decision, payout disputes. The same alert key is sent at most once every 10 minutes, so a burst
 * of events does not flood the inbox. Always logged as a warning as well.
 */
@Component
public class Alerts {

    private static final Logger log = LoggerFactory.getLogger(Alerts.class);
    private static final long QUIET_MS = 10 * 60 * 1000L;

    private final Mailer mailer;
    private final Settings settings;
    private final Environment env;
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();

    public Alerts(Mailer mailer, Settings settings, Environment env) {
        this.mailer = mailer;
        this.settings = settings;
        this.env = env;
    }

    /**
     * @param key   what kind of alert this is, for the quiet period (e.g. "security:revoke:admin:3")
     * @param path  admin console path to open, e.g. "/admin/security/audit" (may be null)
     * @return true when it was sent (false: inside the quiet period, or no recipient)
     */
    public boolean send(String key, String title, List<String> lines, String path) {
        log.warn("ALERT {}: {} {}", key, title, lines);
        long now = System.currentTimeMillis();
        Long last = lastSent.get(key);
        if (last != null && now - last < QUIET_MS) return false;
        lastSent.put(key, now);
        String to = settings.alertEmail();
        if (to == null || to.isBlank()) return false;
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("title", title);
        model.put("lines", lines);
        String base = env.getProperty("app.url", "");
        if (path != null) model.put("url", (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path);
        model.put("at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")));
        try {
            return mailer.send(to, "[ArtistikCity] " + title, "emails/alert", model);
        } catch (RuntimeException e) {
            log.error("Could not send alert {}", key, e);
            return false;
        }
    }
}
