package com.havenbank.backend.notification.service;

/**
 * Channel abstraction. Swapping SMTP for SendGrid, SES, etc. is a single new implementation.
 */
interface EmailSender {
    void send(String to, String subject, String body);
}
