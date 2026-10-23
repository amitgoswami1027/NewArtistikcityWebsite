package com.artistikcity.auth.sms;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/** Twilio Programmable Messaging over its REST API (no SDK needed). */
public class TwilioSmsSender implements SmsSender {

    private final String accountSid;
    private final String authToken;
    private final String from;
    private final String messagingServiceSid;
    private final String apiBase;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public TwilioSmsSender(String accountSid, String authToken, String from, String messagingServiceSid, String apiBase) {
        if (isBlank(accountSid) || isBlank(authToken) || (isBlank(from) && isBlank(messagingServiceSid))) {
            throw new IllegalStateException("SMS_PROVIDER=twilio needs TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_FROM (or TWILIO_MESSAGING_SERVICE_SID)");
        }
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.from = from;
        this.messagingServiceSid = messagingServiceSid;
        this.apiBase = isBlank(apiBase) ? "https://api.twilio.com" : apiBase;
    }

    @Override
    public void send(String to, String message) throws SmsException {
        String form = "To=" + enc(to) + "&Body=" + enc(message)
                + (isBlank(messagingServiceSid) ? "&From=" + enc(from) : "&MessagingServiceSid=" + enc(messagingServiceSid));
        String basic = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        try {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(apiBase + "/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new SmsException("Twilio returned " + res.statusCode() + ": " + res.body());
            }
        } catch (SmsException e) {
            throw e;
        } catch (Exception e) {
            throw new SmsException("Twilio request failed: " + e.getMessage());
        }
    }

    @Override
    public String name() {
        return "twilio";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
