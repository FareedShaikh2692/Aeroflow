package com.aeroflow.notification.repo;

import com.aeroflow.notification.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {
    List<Notification> findTop50ByRecipientIgnoreCaseOrderByCreatedAtDesc(String recipient);
    boolean existsByEventIdAndChannelAndRecipient(UUID eventId, String channel, String recipient);
}
