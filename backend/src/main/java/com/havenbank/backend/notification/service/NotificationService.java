package com.havenbank.backend.notification.service;

import com.havenbank.backend.notification.dto.NotificationMessage;

/**
 * Published entry point for sending notifications.
 */
public interface NotificationService {

    /**
     * Dispatch a notification. Delivery is asynchronous and must never fail or roll back the
     * caller's originating operation (FR-7.4).
     */
    void send(NotificationMessage message);
}
