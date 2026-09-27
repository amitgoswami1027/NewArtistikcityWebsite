package com.artistikcity.service;

import com.artistikcity.view.Views;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Sends HTML e-mails rendered from Mustache templates (Laravel Mailables: ContactMail,
 * FreeCoursesNotifyMail, OrderConfirmationMail, password reset).
 * When no SMTP host is configured the message is only logged, so the site works without mail.
 */
@Service
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final Views views;
    private final String from;
    private final boolean enabled;

    public Mailer(ObjectProvider<JavaMailSender> sender, Views views, Environment env) {
        this.sender = sender;
        this.views = views;
        this.from = env.getProperty("app.mail.from", "no-reply@artistikcity.com");
        String host = env.getProperty("spring.mail.host", "");
        this.enabled = host != null && !host.isBlank();
    }

    /** @return true when the mail was sent (or logged because mail is disabled), false on failure */
    public boolean send(String to, String subject, String template, Map<String, ?> model) {
        String html = views.render(template, model);
        JavaMailSender mailSender = enabled ? sender.getIfAvailable() : null;
        if (mailSender == null) {
            log.info("Mail disabled (MAIL_HOST not set) - would send '{}' to {}", subject, to);
            return true;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("Could not send mail '{}' to {}", subject, to, e);
            return false;
        }
    }
}
