package com.artistikcity.auth.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development "SMS gateway": logs the message instead of sending it. */
public class LogSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LogSmsSender.class);

    @Override
    public void send(String to, String message) {
        log.info("[SMS to {}] {}", mask(to), message);
    }

    @Override
    public String name() {
        return "log";
    }

    @Override
    public boolean isDevelopment() {
        return true;
    }

    static String mask(String phone) {
        return phone == null || phone.length() < 6 ? phone : phone.substring(0, 4) + "*****" + phone.substring(phone.length() - 3);
    }
}
