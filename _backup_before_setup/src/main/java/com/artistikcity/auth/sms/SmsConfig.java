package com.artistikcity.auth.sms;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Chooses the SMS gateway from {@code app.sms.provider} (env SMS_PROVIDER). */
@Configuration
public class SmsConfig {

    @Bean
    public SmsSender smsSender(Environment env) {
        String provider = env.getProperty("app.sms.provider", "log").trim().toLowerCase();
        return switch (provider) {
            case "twilio" -> new TwilioSmsSender(env.getProperty("app.sms.twilio.account-sid"), env.getProperty("app.sms.twilio.auth-token"),
                    env.getProperty("app.sms.twilio.from"), env.getProperty("app.sms.twilio.messaging-service-sid"),
                    env.getProperty("app.sms.twilio.api-base"));
            case "log" -> new LogSmsSender();
            default -> throw new IllegalStateException("Unknown SMS_PROVIDER '" + provider + "' (use log or twilio)");
        };
    }
}
