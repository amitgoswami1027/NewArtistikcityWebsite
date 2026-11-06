package com.artistikcity.config.panel;

import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Values saved in Admin > System parameters > Configuration panel ({@code config_overrides}). They are added to Spring's
 * Environment as its first property source, so they win over AWS Parameter Store / environment variables and over the
 * defaults in config/artistikcity.yml; features keep reading {@code env.getProperty(key)} and see the change at once
 * (no restart). Secrets are AES-GCM encrypted at rest ({@link SecretBox}) and never leave the server in clear.
 * Every save is re-read by all app instances within a minute ({@link #reload()}).
 */
@Service
public class PanelConfig {

    public static final String SOURCE_NAME = "adminConfigPanel";
    private static final Logger log = LoggerFactory.getLogger(PanelConfig.class);

    private final Db db;
    private final ConfigurableEnvironment env;
    private final Map<String, Object> values = new ConcurrentHashMap<>();
    /** keys whose stored secret can't be decrypted with the current key */
    private final List<String> unreadable = new ArrayList<>();
    private final SecretBox box;

    public PanelConfig(Db db, ConfigurableEnvironment env) {
        this.db = db;
        this.env = env;
        // the key comes from the server only (never from this panel): CONFIG_ENCRYPTION_KEY, else OTP_SECRET
        String material = System.getenv("CONFIG_ENCRYPTION_KEY");
        if (material == null || material.isBlank()) material = env.getProperty("app.config.encryption-key", "");
        if (material.isBlank()) material = env.getProperty("app.auth.otp-secret", "");
        this.box = SecretBox.of(material);
    }

    @PostConstruct
    void install() {
        MutablePropertySources sources = env.getPropertySources();
        if (!sources.contains(SOURCE_NAME)) sources.addFirst(new MapPropertySource(SOURCE_NAME, values));
        reload();
    }

    /** Re-read the saved values (after a save, and every minute for other app instances). */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public synchronized void reload() {
        Map<String, Object> fresh = new LinkedHashMap<>();
        List<String> bad = new ArrayList<>();
        try {
            for (Row r : db.select("select config_key, value_text, value_enc, is_secret from config_overrides")) {
                String key = r.str("config_key");
                if (ConfigCatalog.field(key) == null) continue;               // only what the panel is allowed to set
                if (r.lng("is_secret", 0) == 1) {
                    String plain = box == null ? null : box.open(r.str("value_enc"));
                    if (plain == null) { bad.add(key); continue; }            // falls back to the server value
                    fresh.put(key, plain);
                } else {
                    fresh.put(key, r.str("value_text", ""));
                }
            }
        } catch (RuntimeException e) {
            // the table may not exist yet while migrations run: keep the last good values
            log.warn("Configuration panel values could not be loaded: {}", e.getMessage());
            return;
        }
        values.keySet().retainAll(fresh.keySet());
        values.putAll(fresh);
        synchronized (unreadable) {
            unreadable.clear();
            unreadable.addAll(bad);
        }
        if (!bad.isEmpty()) log.warn("Configuration panel: {} secret(s) can't be decrypted with the current key: {}", bad.size(), bad);
    }

    public boolean canStoreSecrets() {
        return box != null;
    }

    /** The server's encryption box (null without a key), also used for students' payout details. */
    public SecretBox secretBox() {
        return box;
    }

    public boolean overridden(String key) {
        return values.containsKey(key);
    }

    public List<String> unreadable() {
        synchronized (unreadable) {
            return List.copyOf(unreadable);
        }
    }

    /** The value without the panel's own override: what the server (env / Parameter Store) or the defaults give. */
    public String serverValue(String key) {
        for (var ps : env.getPropertySources()) {
            if (SOURCE_NAME.equals(ps.getName())) continue;
            Object v = ps.getProperty(key);
            if (v != null) return env.resolvePlaceholders(String.valueOf(v));
        }
        return "";
    }

    /** Where the effective value comes from: panel, server (an environment variable is set), default, or none. */
    public String source(ConfigCatalog.Field f) {
        if (overridden(f.key())) return "panel";
        String e = System.getenv(f.env());
        if (e != null && !e.isBlank()) return "server";
        if (serverValue(f.key()).isBlank()) return "none";
        // the first source that has the key: the config files (artistikcity.yml, application*.properties) are the
        // defaults; anything else (command line, system properties, Parameter Store) was set on the server
        for (var ps : env.getPropertySources()) {
            if (SOURCE_NAME.equals(ps.getName()) || ps.getProperty(f.key()) == null) continue;
            String n = ps.getName();
            return n.startsWith("Config resource") || n.startsWith("applicationConfig") ? "default" : "server";
        }
        return "default";
    }

    /**
     * Saves the changes (only allow-listed keys): {@code set} = key -> new value (secrets are encrypted),
     * {@code reset} = keys to give back to the server value. Returns the keys changed.
     */
    public synchronized List<String> save(Map<String, String> set, List<String> reset, Long adminId) {
        LocalDateTime now = LocalDateTime.now();
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> e : set.entrySet()) {
            ConfigCatalog.Field f = ConfigCatalog.field(e.getKey());
            if (f == null) throw new IllegalArgumentException("Not a panel setting: " + e.getKey());
            String v = e.getValue() == null ? "" : e.getValue();
            String text = f.secret() ? null : v;
            String enc = f.secret() ? seal(v) : null;
            int n = db.update("update config_overrides set value_text = ?, value_enc = ?, is_secret = ?, updated_by = ?, updated_at = ? where config_key = ?",
                    text, enc, f.secret() ? 1 : 0, adminId, now, f.key());
            if (n == 0) {
                db.update("insert into config_overrides (config_key, value_text, value_enc, is_secret, updated_by, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
                        f.key(), text, enc, f.secret() ? 1 : 0, adminId, now, now);
            }
            changed.add(f.key());
        }
        for (String k : reset) {
            if (ConfigCatalog.field(k) != null && db.update("delete from config_overrides where config_key = ?", k) > 0) changed.add(k);
        }
        reload();
        return changed;
    }

    private String seal(String plain) {
        if (box == null) throw new IllegalStateException("No encryption key: set CONFIG_ENCRYPTION_KEY (or OTP_SECRET) on the server.");
        return box.seal(plain);
    }

    /** Last change per key: {key: {at, by}} for the page. */
    public Map<String, Map<String, Object>> changes() {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        for (Row r : db.select("select o.config_key, o.updated_at, a.name from config_overrides o left join admins a on a.id = o.updated_by")) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("at", r.get("updated_at"));
            x.put("by", r.get("name"));
            m.put(r.str("config_key"), x);
        }
        return m;
    }
}
