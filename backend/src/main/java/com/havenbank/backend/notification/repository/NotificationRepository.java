package com.havenbank.backend.notification.repository;

import com.havenbank.backend.notification.domain.Notification;
import com.havenbank.backend.notification.domain.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Claim a batch of due retry rows. {@code FOR UPDATE SKIP LOCKED} lets multiple instances run the
     * worker without handing the same row to two of them, and without blocking each other.
     */
    @Query(value = "SELECT * FROM notifications WHERE status = 'PENDING' AND next_attempt_at <= :now "
            + "ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT :limit", nativeQuery = true)
    List<Notification> claimDuePending(@Param("now") Instant now, @Param("limit") int limit);

    Page<Notification> findByStatus(Status status, Pageable pageable);
}
