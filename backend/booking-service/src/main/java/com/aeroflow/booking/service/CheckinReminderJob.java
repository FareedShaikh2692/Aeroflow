package com.aeroflow.booking.service;

import com.aeroflow.booking.domain.Booking;
import com.aeroflow.booking.repo.BookingRepository;
import com.aeroflow.booking.repo.IdempotencyKeyRepository;
import com.aeroflow.common.events.DomainEventPublisher;
import com.aeroflow.common.events.EventTypes;
import com.aeroflow.common.events.Topics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Emits check-in reminders for bookings departing within the reminder window. */
@Component
public class CheckinReminderJob {
    private final BookingRepository bookings;
    private final IdempotencyKeyRepository idempotency;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final Duration window;

    public CheckinReminderJob(BookingRepository bookings, IdempotencyKeyRepository idempotency, DomainEventPublisher events,
                              Clock clock, @Value("${aeroflow.booking.reminder-window-hours:24}") int hours) {
        this.bookings = bookings;
        this.idempotency = idempotency;
        this.events = events;
        this.clock = clock;
        this.window = Duration.ofHours(hours);
    }

    @Scheduled(fixedDelayString = "${aeroflow.booking.reminder-interval-ms:300000}", initialDelay = 30000)
    @Transactional
    public int sendReminders() {
        Instant now = Instant.now(clock);
        List<Booking> due = bookings.findDueForReminder(now, now.plus(window), PageRequest.of(0, 200));
        for (Booking b : due) {
            events.publish(Topics.BOOKING, EventTypes.BOOKING_CHECKIN_REMINDER, b.getId().toString(), BookingEvents.payload(b));
            b.markReminderSent();
        }
        return due.size();
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeIdempotencyKeys() {
        idempotency.deleteOlderThan(Instant.now(clock).minus(Duration.ofDays(2)));
    }
}
