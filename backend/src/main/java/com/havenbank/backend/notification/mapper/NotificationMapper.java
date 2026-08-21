package com.havenbank.backend.notification.mapper;

import com.havenbank.backend.notification.domain.Notification;
import com.havenbank.backend.notification.dto.NotificationView;
import org.springframework.stereotype.Component;

/** Maps {@link Notification} to its admin {@link NotificationView}. */
@Component
public class NotificationMapper {

    public NotificationView toView(Notification n) {
        return new NotificationView(n.getId(), n.getUserId(), n.getRecipientEmail(), n.getType(),
                n.getStatus(), n.getAttempts(), n.getLastError(), n.getCreatedAt());
    }
}
