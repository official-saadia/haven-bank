package com.havenbank.backend.notification.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.notification.domain.Notification;
import com.havenbank.backend.notification.domain.NotificationType;
import com.havenbank.backend.notification.domain.Status;
import com.havenbank.backend.notification.dto.NotificationMessage;
import com.havenbank.backend.notification.dto.NotificationView;
import com.havenbank.backend.notification.mapper.NotificationMapper;
import com.havenbank.backend.notification.repository.NotificationRepository;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationAdminServiceTest {

    @Mock
    private NotificationRepository notifications;
    @Mock
    private NotificationMapper mapper;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private NotificationAdminService service;

    @Test
    void requeuingADeadLetteredNotificationResetsItAndAudits() {
        UUID actor = UUID.randomUUID();
        Notification deadLettered = Notification.of(
                new NotificationMessage("customer@example.com", "Customer", UUID.randomUUID(),
                        NotificationType.ACCOUNT_CREATED, Map.of()),
                "EMAIL", Status.DEAD_LETTER);
        deadLettered.recordFailure("smtp timeout", 1, java.time.Instant.now()); // attempts >= 1 -> DEAD_LETTER already
        when(notifications.findById(deadLettered.getId())).thenReturn(Optional.of(deadLettered));
        when(mapper.toView(deadLettered)).thenReturn(
                new NotificationView(deadLettered.getId(), null, "customer@example.com",
                        NotificationType.ACCOUNT_CREATED, Status.PENDING, 0, null, null));

        NotificationView result = service.requeue(deadLettered.getId(), actor);

        assertThat(deadLettered.getStatus()).isEqualTo(Status.PENDING);
        assertThat(result.status()).isEqualTo(Status.PENDING);
        org.mockito.Mockito.verify(auditService).record(argThatMatchesRequeue(deadLettered.getId()));
    }

    @Test
    void requeuingANonexistentNotificationIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(notifications.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requeue(missing, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listingDelegatesTheStatusFilterStraightToTheRepository() {
        when(notifications.findByStatus(Status.DEAD_LETTER, org.springframework.data.domain.Pageable.unpaged()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.list(Status.DEAD_LETTER, org.springframework.data.domain.Pageable.unpaged());

        org.mockito.Mockito.verify(notifications)
                .findByStatus(Status.DEAD_LETTER, org.springframework.data.domain.Pageable.unpaged());
    }

    private com.havenbank.backend.audit.domain.AuditEvent argThatMatchesRequeue(UUID notificationId) {
        return org.mockito.ArgumentMatchers.argThat(event ->
                event.action() == AuditAction.NOTIFICATION_REQUEUED && event.detail().contains(notificationId.toString()));
    }
}
