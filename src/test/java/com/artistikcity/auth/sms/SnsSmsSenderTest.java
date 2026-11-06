package com.artistikcity.auth.sms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The AWS SNS Publish request: phone, message and the SMS attributes (type, sender ID, India DLT ids). */
class SnsSmsSenderTest {

    @Test
    void publishBodyCarriesTheAttributes() {
        SnsSmsSender s = new SnsSmsSender("ap-south-1", "AKIAEXAMPLE", "secret", "ARTSTK", null, "1101234567890", "1107654321098", null);
        String body = s.body("+919876543210", "Your code is 123456");
        assertTrue(body.contains("Action=Publish"));
        assertTrue(body.contains("PhoneNumber=%2B919876543210"));
        assertTrue(body.contains("Message=Your%20code%20is%20123456") || body.contains("Message=Your+code+is+123456"));
        assertTrue(body.contains("AWS.SNS.SMS.SMSType") && body.contains("Transactional"));
        assertTrue(body.contains("AWS.SNS.SMS.SenderID") && body.contains("ARTSTK"));
        assertTrue(body.contains("AWS.MM.SMS.EntityId") && body.contains("1101234567890"));
        assertTrue(body.contains("AWS.MM.SMS.TemplateId"));
    }

    @Test
    void optionalAttributesAreLeftOut() {
        String body = new SnsSmsSender("ap-south-1", null, null, null, "Promotional", null, null, null).body("+15550001111", "hi");
        assertTrue(body.contains("Promotional"));
        assertFalse(body.contains("SenderID"));
        assertFalse(body.contains("EntityId"));
    }

    @Test
    void regionIsRequired() {
        assertThrows(IllegalStateException.class, () -> new SnsSmsSender(" ", null, null, null, null, null, null, null));
    }
}
