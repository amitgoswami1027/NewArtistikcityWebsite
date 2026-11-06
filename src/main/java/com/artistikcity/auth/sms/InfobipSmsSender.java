package com.artistikcity.auth.sms;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Infobip SMS API ({@code POST /sms/2/text/advanced}, header {@code Authorization: App <api key>}). */
public class InfobipSmsSender implements SmsSender {

    private final String baseUrl;
    private final String apiKey;
    private final String sender;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    public InfobipSmsSender(String baseUrl, String apiKey, String sender) {
        if (isBlank(baseUrl) || isBlank(apiKey)) throw new IllegalStateException("SMS provider infobip needs a base URL and an API key");
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.sender = isBlank(sender) ? "ArtistikCity" : sender;
    }

    String body(String to, String message) throws Exception {
        return json.writeValueAsString(Map.of("messages", List.of(Map.of(
                "from", sender, "destinations", List.of(Map.of("to", to.replaceFirst("^\\+", ""))), "text", message))));
    }

    @Override
    public void send(String to, String message) throws SmsException {
        try {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/sms/2/text/advanced"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "App " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body(to, message))).build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) throw new SmsException("Infobip returned " + res.statusCode() + ": " + res.body());
        } catch (SmsException e) {
            throw e;
        } catch (Exception e) {
            throw new SmsException("Infobip request failed: " + e.getMessage());
        }
    }

    @Override
    public String name() {
        return "infobip";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
