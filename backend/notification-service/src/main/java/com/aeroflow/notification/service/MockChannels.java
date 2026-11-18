package com.aeroflow.notification.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Demo adapters: log the message and return a fake provider reference. Nothing leaves the system. */
public final class MockChannels {
    private MockChannels() {}

    @Component
    public static class MockEmailSender implements NotificationChannels.EmailSender {
        private static final Logger log = LoggerFactory.getLogger(MockEmailSender.class);
        private final String from;
        public MockEmailSender(@Value("${aeroflow.notifications.email-from}") String from) { this.from = from; }
        @Override public String send(String to, String subject, String body) {
            String ref = "MOCK-EMAIL-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("[mock-email] from={} to={} subject=\"{}\" ref={}", from, mask(to), subject, ref);
            return ref;
        }
    }

    @Component
    public static class MockSmsSender implements NotificationChannels.SmsSender {
        private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);
        @Override public String send(String to, String body) {
            String ref = "MOCK-SMS-" + UUID.randomUUID().toString().substring(0, 8);
            log.info("[mock-sms] to={} chars={} ref={}", mask(to), body.length(), ref);
            return ref;
        }
    }

    static String mask(String v) {
        if (v == null || v.length() < 4) return "***";
        int at = v.indexOf('@');
        return at > 1 ? v.charAt(0) + "***" + v.substring(at) : "***" + v.substring(v.length() - 3);
    }
}
