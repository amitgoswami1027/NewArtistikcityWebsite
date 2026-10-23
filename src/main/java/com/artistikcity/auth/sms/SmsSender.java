package com.artistikcity.auth.sms;

/**
 * Sends a text message. Pick the implementation with {@code SMS_PROVIDER}:
 * <ul>
 *   <li>{@code log} (default) - {@link LogSmsSender}: writes the message to the application log (development).</li>
 *   <li>{@code twilio} - {@link TwilioSmsSender}: Twilio Programmable Messaging (TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN,
 *       and TWILIO_FROM or TWILIO_MESSAGING_SERVICE_SID).</li>
 * </ul>
 * To add another gateway (Firebase, MSG91, AWS SNS...), implement this interface as a Spring bean and
 * register it in {@link SmsConfig}.
 */
public interface SmsSender {

    /** @param to E.164 phone number, e.g. +919876543210 */
    void send(String to, String message) throws SmsException;

    /** Short provider name for logs and diagnostics. */
    String name();

    /** True when messages are not really delivered (the OTP may then be shown on screen in development). */
    default boolean isDevelopment() {
        return false;
    }

    class SmsException extends Exception {
        public SmsException(String message) {
            super(message);
        }
    }
}
