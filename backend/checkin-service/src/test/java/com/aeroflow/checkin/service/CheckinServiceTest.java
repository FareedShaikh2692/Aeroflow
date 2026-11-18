package com.aeroflow.checkin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aeroflow.checkin.api.CheckinDtos.CompleteRequest;
import com.aeroflow.checkin.domain.Checkin;
import com.aeroflow.checkin.repo.CheckinRepository;
import com.aeroflow.common.audit.AuditService;
import com.aeroflow.common.events.DomainEventPublisher;
import com.aeroflow.common.events.EventTypes;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckinServiceTest {
    static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    final BookingClient bookings = mock(BookingClient.class);
    final CheckinRepository repo = mock(CheckinRepository.class);
    final DomainEventPublisher events = mock(DomainEventPublisher.class);
    final CheckinService service = new CheckinService(bookings, repo, events, mock(AuditService.class),
            Clock.fixed(NOW, ZoneOffset.UTC), 48, 60);
    final UUID paxId = UUID.randomUUID();

    BookingClient.Booking booking(Instant departure, String status, boolean checkedIn) {
        return new BookingClient.Booking(UUID.randomUUID(), "K7Q2MX", UUID.randomUUID(), "AF100", "DXB", "LHR", departure,
                departure.plusSeconds(25200), status, BigDecimal.TEN, "AED", "a@b.co",
                List.of(new BookingClient.Passenger(paxId, "Sarah", "Ahmed", "BUSINESS", "1A", checkedIn, null)));
    }

    @Test
    void eligibilityWindowOpens48hAndCloses60mBeforeDeparture() {
        assertThat(service.eligibility(booking(NOW.plus(Duration.ofHours(72)), "CONFIRMED", false)).open()).isFalse();
        assertThat(service.eligibility(booking(NOW.plus(Duration.ofHours(24)), "CONFIRMED", false)).open()).isTrue();
        assertThat(service.eligibility(booking(NOW.plus(Duration.ofMinutes(30)), "CONFIRMED", false)).reason()).contains("closed");
        assertThat(service.eligibility(booking(NOW.plus(Duration.ofHours(24)), "CANCELLED", false)).reason()).contains("CANCELLED");
    }

    @Test
    void completeIssuesBoardingPassesOnceAndPublishesEvent() {
        BookingClient.Booking b = booking(NOW.plus(Duration.ofHours(24)), "CONFIRMED", false);
        when(bookings.retrieve("K7Q2MX", "Ahmed")).thenReturn(b);
        when(bookings.markCheckedIn(eq("K7Q2MX"), any())).thenReturn(booking(b.departureTime(), "CONFIRMED", true));
        when(repo.findByPassengerId(paxId)).thenReturn(Optional.empty());
        when(repo.nextSequence(any())).thenReturn(7);
        when(repo.save(any(Checkin.class))).thenAnswer(i -> i.getArgument(0));

        service.complete(new CompleteRequest("k7q2mx", "Ahmed", List.of(paxId), true));

        verify(repo).save(argThat(c -> c.getBoardingPassNumber().equals("AF100-K7Q2MX-007") && c.getBoardingGroup().equals("1")));
        verify(events).publish(any(), eq(EventTypes.CHECKIN_COMPLETED), any(), any());
    }

    @Test
    void completeOutsideWindowIsRejectedForPassengers() {
        when(bookings.retrieve(any(), any())).thenReturn(booking(NOW.plus(Duration.ofHours(72)), "CONFIRMED", false));
        assertThatThrownBy(() -> service.complete(new CompleteRequest("K7Q2MX", "Ahmed", List.of(paxId), true)))
                .extracting("code").isEqualTo("CHECKIN_NOT_OPEN");
        verify(bookings, never()).markCheckedIn(any(), any());
    }

    @Test
    void bcbpPayloadFollowsIataLayout() {
        Checkin c = new Checkin(UUID.randomUUID(), "K7Q2MX", paxId, "Sarah", "Ahmed", UUID.randomUUID(), "AF100", "DXB", "LHR",
                Instant.parse("2030-02-01T08:00:00Z"), "1A", "BUSINESS", "AF100-K7Q2MX-007", 7, "1", null, NOW, "ONLINE", "self");
        String bcbp = BoardingPassRenderer.bcbp(c);
        assertThat(bcbp).startsWith("M1AHMED/SARAH").contains("EK7Q2MX DXBLHRAF 100  032J001A00007");
        assertThat(new BoardingPassRenderer().render(c)).startsWith("%PDF".getBytes());
    }
}
