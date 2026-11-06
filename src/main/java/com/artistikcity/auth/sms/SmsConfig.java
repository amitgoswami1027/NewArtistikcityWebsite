package com.artistikcity.auth.sms;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * The SMS gateway, chosen per message from {@code app.sms.provider} (log, twilio, sns, infobip), so a change in the
 * Configuration panel takes effect without a restart. A misconfigured provider fails the send (the user sees "could not
 * send the code") instead of stopping the application at start-up.
 */
@Configuration
public class SmsConfig {

    @Bean
    public SmsSender smsSender(Environment env) {
        return new DynamicSmsSender(env);
    }

    static final class DynamicSmsSender implements SmsSender {
        private final Environment env;
        private volatile List<String> signature;
        private volatile SmsSender current;

        DynamicSmsSender(Environment env) {
            this.env = env;
        }

        private String p(String key) {
            return env.getProperty(key, "").trim();
        }

        private synchronized SmsSender delegate() {
            String provider = p("app.sms.provider").toLowerCase();
            if (provider.isEmpty()) provider = "log";
            List<String> sig = switch (provider) {
                case "twilio" -> List.of(provider, p("app.sms.twilio.account-sid"), p("app.sms.twilio.auth-token"), p("app.sms.twilio.from"),
                        p("app.sms.twilio.messaging-service-sid"), p("app.sms.twilio.api-base"));
                case "sns" -> List.of(provider, p("app.sms.sns.region"), p("app.sms.sns.access-key-id"), p("app.sms.sns.secret-access-key"),
                        p("app.sms.sns.sender-id"), p("app.sms.sns.sms-type"), p("app.sms.sns.entity-id"), p("app.sms.sns.template-id"), p("app.sms.sns.endpoint"));
                case "infobip" -> List.of(provider, p("app.sms.infobip.base-url"), p("app.sms.infobip.api-key"), p("app.sms.infobip.sender"));
                default -> List.of(provider);
            };
            if (sig.equals(signature) && current != null) return current;
            SmsSender s = switch (provider) {
                case "twilio" -> new TwilioSmsSender(sig.get(1), sig.get(2), sig.get(3), sig.get(4), sig.get(5));
                case "sns" -> new SnsSmsSender(sig.get(1), sig.get(2), sig.get(3), sig.get(4), sig.get(5), sig.get(6), sig.get(7), sig.get(8));
                case "infobip" -> new InfobipSmsSender(sig.get(1), sig.get(2), sig.get(3));
                case "log" -> new LogSmsSender();
                default -> throw new IllegalStateException("Unknown SMS provider '" + provider + "' (use log, twilio, sns or infobip)");
            };
            signature = sig;
            current = s;
            return s;
        }

        @Override
        public void send(String to, String message) throws SmsException {
            SmsSender s;
            try {
                s = delegate();
            } catch (IllegalStateException e) {
                throw new SmsException(e.getMessage());
            }
            s.send(to, message);
        }

        @Override
        public String name() {
            try {
                return delegate().name();
            } catch (IllegalStateException e) {
                return "misconfigured";
            }
        }

        @Override
        public boolean isDevelopment() {
            try {
                return delegate().isDevelopment();
            } catch (IllegalStateException e) {
                return false;
            }
        }
    }
}
