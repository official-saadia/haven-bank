package com.havenbank.backend.notification.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sender identity for outbound mail. Transport itself is configured under {@code spring.mail}.
 */
@ConfigurationProperties(prefix = "app.notification.email")
public class EmailProperties {

    /**
     * Envelope sender address, e.g. no-reply@havenbank.example. Required.
     */
    private String from;

    /**
     * Display name shown to the recipient.
     */
    private String fromName = "Haven Bank";

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }
}
