package com.artistikcity.admin;

import com.artistikcity.service.Mailer;
import com.artistikcity.support.Db;
import com.artistikcity.support.Row;
import com.artistikcity.view.Views;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Newsletter e-mails: renders a campaign, signs unsubscribe links and sends a campaign to every active
 * subscriber on a background thread (progress is written to newsletter_campaigns as it goes).
 */
@Service
public class NewsletterService {

    private static final Logger log = LoggerFactory.getLogger(NewsletterService.class);

    private final Db db;
    private final Mailer mailer;
    private final Views views;
    private final Environment env;
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "newsletter-sender");
        t.setDaemon(true);
        return t;
    });
    private volatile byte[] secret;

    public NewsletterService(Db db, Mailer mailer, Views views, Environment env) {
        this.db = db;
        this.mailer = mailer;
        this.views = views;
        this.env = env;
    }

    @PreDestroy
    void stop() {
        sender.shutdown();
    }

    public String siteUrl() {
        String url = env.getProperty("app.url", "http://localhost:8080");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    // ------------------------------------------------------------ unsubscribe

    /** Signing key: NEWSLETTER_SECRET / OTP_SECRET, otherwise a random key kept in settings. */
    private byte[] key() {
        if (secret != null) {
            return secret;
        }
        String configured = env.getProperty("app.newsletter.secret", env.getProperty("app.auth.otp-secret", ""));
        if (configured == null || configured.isBlank()) {
            Row row = db.first("select meta_value from settings where meta_tag = 'system' and meta_key = 'newsletter_secret'");
            if (row == null || row.str("meta_value", "").isBlank()) {
                byte[] b = new byte[32];
                new SecureRandom().nextBytes(b);
                configured = HexFormat.of().formatHex(b);
                db.insert("settings", Row.of("meta_tag", "system", "meta_key", "newsletter_secret", "meta_value", configured));
            } else {
                configured = row.str("meta_value");
            }
        }
        secret = configured.getBytes(StandardCharsets.UTF_8);
        return secret;
    }

    public String token(String email) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key(), "HmacSHA256"));
            byte[] h = mac.doFinal(("newsletter:" + email.trim().toLowerCase()).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean validToken(String email, String token) {
        return email != null && token != null && MessageDigest.isEqual(token(email).getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    public String unsubscribeUrl(String email) {
        return siteUrl() + "/newsletter/unsubscribe?e=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&t=" + token(email);
    }

    // ---------------------------------------------------------------- render

    public String render(Row campaign, String email) {
        return views.render("emails/newsletter", model(campaign, email));
    }

    private Map<String, Object> model(Row campaign, String email) {
        return Map.of("subject", campaign.str("subject", ""), "preheader", campaign.str("preheader", ""),
                "body", absolute(campaign.str("body", "")), "siteUrl", siteUrl(), "unsubscribeUrl", unsubscribeUrl(email));
    }

    /** Images and links stored as "/storage/…" need the full site address inside an e-mail. */
    private String absolute(String html) {
        return html.replace("src=\"/", "src=\"" + siteUrl() + "/").replace("href=\"/", "href=\"" + siteUrl() + "/");
    }

    public boolean sendOne(Row campaign, String email) {
        return mailer.send(email, campaign.str("subject"), "emails/newsletter", model(campaign, email));
    }

    // ------------------------------------------------------------------ blast

    /** Marks the campaign as sending and mails every active subscriber in the background. */
    public int start(long campaignId) {
        List<Row> to = db.select("select email from newsletters where unsubscribed_at is null order by id");
        int changed = db.update("update newsletter_campaigns set status = 'sending', recipients = ?, sent_count = 0, failed_count = 0, updated_at = ?"
                + " where id = ? and status in ('draft', 'failed')", to.size(), LocalDateTime.now(), campaignId);
        if (changed == 0) {
            return -1;
        }
        sender.submit(() -> run(campaignId, to));
        return to.size();
    }

    private void run(long campaignId, List<Row> to) {
        Row campaign = db.find("newsletter_campaigns", campaignId);
        int sent = 0;
        int failed = 0;
        for (Row r : to) {
            boolean ok;
            try {
                ok = sendOne(campaign, r.str("email"));
            } catch (RuntimeException e) {
                log.warn("Newsletter {} to {} failed", campaignId, r.str("email"), e);
                ok = false;
            }
            if (ok) {
                sent++;
            } else {
                failed++;
            }
            if ((sent + failed) % 20 == 0) {
                db.update("update newsletter_campaigns set sent_count = ?, failed_count = ?, updated_at = ? where id = ?", sent, failed, LocalDateTime.now(), campaignId);
            }
        }
        String status = sent == 0 && failed > 0 ? "failed" : "sent";
        db.update("update newsletter_campaigns set status = ?, sent_count = ?, failed_count = ?, sent_at = ?, updated_at = ? where id = ?",
                status, sent, failed, LocalDateTime.now(), LocalDateTime.now(), campaignId);
        log.info("Newsletter {} finished: {} sent, {} failed", campaignId, sent, failed);
    }
}
