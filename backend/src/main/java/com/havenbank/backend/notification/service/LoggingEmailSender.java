package com.havenbank.backend.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Development email sender that logs instead of transmitting. Replaced by a real SMTP/API-backed
 * implementation in non-dev profiles. Deliberately never logs secret values (FR-7.5).
 */
@Slf4j
@Component
class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[email] to={} subject={}", to, subject);
        log.debug("[email] body={}", body);
    }
}
