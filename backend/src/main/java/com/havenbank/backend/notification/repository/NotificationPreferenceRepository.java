package com.havenbank.backend.notification.repository;

import com.havenbank.backend.notification.domain.NotificationPreference;
import com.havenbank.backend.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    List<NotificationPreference> findByUserId(UUID userId);

    Optional<NotificationPreference> findByUserIdAndTypeAndChannel(UUID userId, NotificationType type, String channel);
}
