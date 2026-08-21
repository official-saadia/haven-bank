package com.havenbank.backend.notification.domain;

/**
 * SENT: delivered. SUPPRESSED: blocked by user preference. FAILED: a secret-bearing notification
 * whose immediate send failed (not retried). PENDING: awaiting a (re)delivery attempt by the worker.
 * DEAD_LETTER: retries exhausted; awaiting admin inspection/requeue.
 */
public enum Status {SENT, FAILED, SUPPRESSED, PENDING, DEAD_LETTER}
