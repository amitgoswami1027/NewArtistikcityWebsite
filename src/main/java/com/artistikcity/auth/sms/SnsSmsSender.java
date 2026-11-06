package com.artistikcity.auth.sms;

import com.artistikcity.support.s3.AwsCredentials;
import com.artistikcity.support.s3.AwsCredentialsProvider;
import com.artistikcity.support.s3.SigV4;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Amazon SNS direct SMS (the {@code Publish} action with a phone number), signed with SigV4 (no SDK). Credentials: the
 * access key from the Configuration panel, else the server's (environment / EC2 instance role). India needs the DLT
 * entity and template ids as message attributes.
 */
public class SnsSmsSender implements SmsSender {

    private final String region;
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String senderId;
    private final String smsType;
    private final String entityId;
    private final String templateId;
    private final String endpoint;
    private final AwsCredentialsProvider roleCredentials = new AwsCredentialsProvider();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SnsSmsSender(String region, String accessKeyId, String secretAccessKey, String senderId, String smsType,
                        String entityId, String templateId, String endpoint) {
        if (isBlank(region)) throw new IllegalStateException("SMS provider sns needs a region (app.sms.sns.region)");
        this.region = region.trim();
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.senderId = senderId;
        this.smsType = isBlank(smsType) ? "Transactional" : smsType;
        this.entityId = entityId;
        this.templateId = templateId;
        this.endpoint = isBlank(endpoint) ? "https://sns." + this.region + ".amazonaws.com" : endpoint.replaceAll("/+$", "");
    }

    /** The form body of the Publish call (sorted, so it is stable for tests). */
    String body(String to, String message) {
        Map<String, String> p = new TreeMap<>();
        p.put("Action", "Publish");
        p.put("Version", "2010-03-31");
        p.put("PhoneNumber", to);
        p.put("Message", message);
        int n = 1;
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("AWS.SNS.SMS.SMSType", smsType);
        if (!isBlank(senderId)) attrs.put("AWS.SNS.SMS.SenderID", senderId);
        if (!isBlank(entityId)) attrs.put("AWS.MM.SMS.EntityId", entityId);
        if (!isBlank(templateId)) attrs.put("AWS.MM.SMS.TemplateId", templateId);
        for (Map.Entry<String, String> a : attrs.entrySet()) {
            p.put("MessageAttributes.entry." + n + ".Name", a.getKey());
            p.put("MessageAttributes.entry." + n + ".Value.DataType", "String");
            p.put("MessageAttributes.entry." + n + ".Value.StringValue", a.getValue());
            n++;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return sb.toString();
    }

    @Override
    public void send(String to, String message) throws SmsException {
        String form = body(to, message);
        try {
            AwsCredentials creds = !isBlank(accessKeyId) && !isBlank(secretAccessKey)
                    ? new AwsCredentials(accessKeyId, secretAccessKey, null, null) : roleCredentials.get();
            URI uri = URI.create(endpoint + "/");
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("host", uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
            headers.put("content-type", "application/x-www-form-urlencoded; charset=utf-8");
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(form.getBytes(StandardCharsets.UTF_8)));
            Map<String, String> signed = SigV4.sign("POST", "/", null, headers, hash, creds, region, "sns", Instant.now());
            HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).POST(HttpRequest.BodyPublishers.ofString(form));
            for (Map.Entry<String, String> h : signed.entrySet()) {
                if (!h.getKey().equals("host")) b.header(h.getKey(), h.getValue());
            }
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) throw new SmsException("AWS SNS returned " + res.statusCode() + ": " + res.body());
        } catch (SmsException e) {
            throw e;
        } catch (Exception e) {
            throw new SmsException("AWS SNS request failed: " + e.getMessage());
        }
    }

    @Override
    public String name() {
        return "sns";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
