package com.havenbank.backend.notification.service;

import com.havenbank.backend.audit.domain.AuditAction;
import com.havenbank.backend.audit.domain.AuditEvent;
import com.havenbank.backend.audit.service.AuditService;
import com.havenbank.backend.notification.domain.Notification;
import com.havenbank.backend.notification.domain.Status;
import com.havenbank.backend.notification.dto.NotificationView;
import com.havenbank.backend.notification.mapper.NotificationMapper;
import com.havenbank.backend.notification.repository.NotificationRepository;
import com.havenbank.backend.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Admin operations over the notification log: inspect dead-letters and requeue them. */
@Service
@RequiredArgsConstructor
public class NotificationAdminService {

    private final NotificationRepository notifications;
    private final NotificationMapper mapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<NotificationView> list(Status status, Pageable pageable) {
        return notifications.findByStatus(status, pageable).map(mapper::toView);
    }

    /** Return a dead-lettered (or any) notification to the retry queue for a fresh set of attempts. */
    @Transactional
    public NotificationView requeue(UUID id, UUID actor) {
        Notification n = notifications.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        n.requeue();
        auditService.record(AuditEvent.success(actor, AuditAction.NOTIFICATION_REQUEUED,
                "notification requeued " + id));
        return mapper.toView(n);
    }
}
