package com.havenbank.backend.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async}. Without this, the annotation on notification dispatch is inert and
 * delivery runs on the request thread — which would let a mail failure surface inside a committed
 * money movement (FR-7.4).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
