package com.havenbank.backend.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled workers (the notification retry worker). */
@Configuration
@EnableScheduling
class SchedulingConfig {
}
