package com.aeroflow.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aeroflow.common.events.EventEnvelope;
import com.aeroflow.common.events.EventInbox;
import com.aeroflow.common.events.EventTypes;
import com.aeroflow.notification.repo.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {
    final ObjectMapper mapper = new ObjectMapper();
    final EventInbox inbox = mock(EventInbox.class);
    final NotificationRepository repo = mock(NotificationRepository.class);
    final NotificationChannels.EmailSender email = mock(NotificationChannels.EmailSender.class);
    final NotificationChannels.SmsSender sms = mock(NotificationChannels.SmsSender.class);
    final NotificationService service = new NotificationService(inbox, repo, email, sms);

    EventEnvelope env(String type, ObjectNode p) {
        return new EventEnvelope(UUID.randomUUID(), type, "booking-service", "b", Instant.now(), p);
    }

    ObjectNode booking() {
        return mapper.createObjectNode().put("pnr", "K7Q2MX").put("flightNumber", "AF100").put("origin", "DXB")
                .put("destination", "LHR").put("departureTime", "2030-01-01T08:00:00Z").put("contactEmail", "sarah@example.com")
                .put("contactPhone", "+971500000000").put("passengerCount", 2).put("totalAmount", "5700.00").put("currency", "AED");
    }

    @Test
    void bookingConfirmationGoesOutByEmailAndSms() {
        when(inbox.firstDelivery(any())).thenReturn(true);
        when(email.send(any(), any(), any())).thenReturn("MOCK-EMAIL-1");
        when(sms.send(any(), any())).thenReturn("MOCK-SMS-1");
        assertThat(service.handle(env(EventTypes.BOOKING_CONFIRMED, booking()))).isEqualTo(2);
        verify(email).send(eq("sarah@example.com"), eq("Booking confirmed - K7Q2MX"), contains("5700.00 AED"));
        verify(sms).send(eq("+971500000000"), contains("K7Q2MX"));
        verify(repo, times(2)).save(argThat(n -> "SENT".equals(n.getStatus())));
    }

    @Test
    void channelFailureIsRecordedNotThrown() {
        when(inbox.firstDelivery(any())).thenReturn(true);
        when(email.send(any(), any(), any())).thenThrow(new RuntimeException("SMTP down"));
        ObjectNode p = booking();
        p.remove("contactPhone");
        service.handle(env(EventTypes.BOOKING_CANCELLED, p.put("reason", "Customer").put("refundAmount", "5700.00")));
        verify(repo).save(argThat(n -> "FAILED".equals(n.getStatus())));
    }

    @Test
    void duplicatesAndUnknownEventsSendNothing() {
        when(inbox.firstDelivery(any())).thenReturn(false);
        assertThat(service.handle(env(EventTypes.BOOKING_CONFIRMED, booking()))).isZero();
        when(inbox.firstDelivery(any())).thenReturn(true);
        assertThat(service.handle(env("flight.created", booking()))).isZero();
        verifyNoInteractions(email, sms);
    }

    @Test
    void templatesCoverDelayBoardingReminderAndCheckin() {
        ObjectNode delay = booking().put("flightStatus", "DELAYED").put("delayMinutes", 40).put("estimatedDepartureTime", "2030-01-01T08:40:00Z")
                .put("reason", "ATC");
        assertThat(NotificationTemplates.render(EventTypes.BOOKING_FLIGHT_NOTICE, delay).orElseThrow().email())
                .contains("delayed by 40 minutes").contains("Reason: ATC");
        ObjectNode boarding = booking().put("flightStatus", "BOARDING").put("gate", "B12");
        assertThat(NotificationTemplates.render(EventTypes.BOOKING_FLIGHT_NOTICE, boarding).orElseThrow().sms()).contains("gate B12");
        assertThat(NotificationTemplates.render(EventTypes.BOOKING_CHECKIN_REMINDER, booking()).orElseThrow().subject()).startsWith("Check-in is open");
        ObjectNode checkin = booking();
        checkin.putArray("passengers").addObject().put("name", "Sarah");
        assertThat(NotificationTemplates.render(EventTypes.CHECKIN_COMPLETED, checkin).orElseThrow().email()).contains("1 boarding pass");
        assertThat(MockChannels.mask("sarah@example.com")).isEqualTo("s***@example.com");
        assertThat(MockChannels.mask("+971500000000")).isEqualTo("***000");
    }
}
