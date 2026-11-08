package com.artistikcity.landing;

import com.artistikcity.support.Db;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Is the database ready for Landing Page Management? The module needs the tables and columns of
 * database/migrations/2026-11-08_landing_pages.sql and 2026-11-09_landing_site_pages.sql. When a server's database hasn't
 * had them applied (e.g. a deploy whose migration step was skipped), the admin pages say exactly which file is missing
 * instead of failing with an error, and the publishing job stays quiet. Checked once and remembered when complete;
 * while incomplete it is checked again at most once a minute, so applying the migration needs no restart.
 */
@Component
public class LandingSchema {

    private static final Logger log = LoggerFactory.getLogger(LandingSchema.class);

    /** migration file -> the "table" or "table.column" names it creates. */
    private static final Map<String, List<String>> NEEDS = new LinkedHashMap<>();

    static {
        NEEDS.put("2026-11-08_landing_pages.sql", List.of("landing_pages", "landing_page_versions", "landing_pricing", "landing_payment_settings",
                "landing_payment_providers", "landing_payment_methods", "landing_checkout", "landing_coupons", "landing_coupon_targets",
                "landing_coupon_redemptions", "landing_events", "landing_media", "orders.landing_page_id", "orders.utm_campaign", "admins.extra_capabilities"));
        NEEDS.put("2026-11-09_landing_site_pages.sql", List.of("landing_pages.kind", "landing_pages.site_route"));
    }

    private final Db db;
    private volatile boolean ok;
    private volatile long checkedAt;
    private volatile List<String> missing = List.of();

    public LandingSchema(Db db) {
        this.db = db;
    }

    public boolean ok() {
        if (ok) return true;
        if (System.currentTimeMillis() - checkedAt < 60_000 && checkedAt > 0) return false;
        check();
        return ok;
    }

    /** The migration files that still have to be applied (empty when ready). */
    public List<String> missingMigrations() {
        ok();
        return missing;
    }

    private synchronized void check() {
        List<String> out = new ArrayList<>();
        try {
            for (Map.Entry<String, List<String>> e : NEEDS.entrySet()) {
                for (String what : e.getValue()) {
                    String[] p = what.split("\\.");
                    long n = p.length == 1
                            ? db.count("select count(*) from information_schema.tables where table_schema = database() and table_name = ?", p[0])
                            : db.count("select count(*) from information_schema.columns where table_schema = database() and table_name = ? and column_name = ?", p[0], p[1]);
                    if (n == 0) {
                        out.add(e.getKey());
                        break;
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Could not check the landing page tables: {}", ex.toString());
            checkedAt = System.currentTimeMillis();
            return;
        }
        checkedAt = System.currentTimeMillis();
        missing = List.copyOf(out);
        ok = out.isEmpty();
        if (!ok) {
            log.warn("Landing Page Management is waiting for database migrations that are not applied on this database: {}"
                    + " (database/migrations). Apply them: the deploy / launcher runs pending ones; if schema_migrations already lists them,"
                    + " run the files by hand (docs/AWS-DEPLOY.md, Troubleshooting). Course checkout needs them too.", out);
        }
    }

    /** The explanation shown to staff instead of the admin page. */
    public String message() {
        return "Landing Page Management needs a database update that hasn't been applied on this server yet: "
                + String.join(", ", missingMigrations()) + " (in database/migrations). Run the deploy again, or apply "
                + (missing.size() == 1 ? "the file" : "the files")
                + " to this database by hand (docs/AWS-DEPLOY.md, Troubleshooting: \"Landing Page Management says Database update needed\").";
    }
}
