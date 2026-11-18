package com.aeroflow.notification.service;

/** Outbound channel ports. Real adapters (SES, SendGrid, Twilio...) implement the same interfaces. */
public final class NotificationChannels {
    private NotificationChannels() {}

    public interface EmailSender { String send(String to, String subject, String body); }
    public interface SmsSender { String send(String to, String body); }
}
