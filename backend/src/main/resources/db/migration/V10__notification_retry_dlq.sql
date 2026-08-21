-- Reliable notification delivery: retry with backoff, then a dead-letter terminal state (FR-7.4).
--
-- Delivery was previously best-effort: a failed send was written as FAILED and dropped. This adds an
-- outbox-style retry — a failed non-secret notification is re-attempted up to 3 times with backoff,
-- then moved to DEAD_LETTER for an admin to inspect and requeue.
--
-- subject/body are stored only for retryable (non-secret) notifications so the worker can resend
-- without re-rendering. Secret-bearing types (OTP, verification/reset links) are never persisted or
-- retried (FR-5.3, FR-1.8b); if their immediate send fails they stay FAILED and the user re-requests.

ALTER TABLE notifications ADD COLUMN attempts        integer      NOT NULL DEFAULT 0;
ALTER TABLE notifications ADD COLUMN next_attempt_at timestamp    DEFAULT NULL;
ALTER TABLE notifications ADD COLUMN subject         varchar(200) DEFAULT NULL;
ALTER TABLE notifications ADD COLUMN body            text         DEFAULT NULL;
ALTER TABLE notifications ADD COLUMN last_error      varchar(500) DEFAULT NULL;

-- Pre-existing terminal failures become dead-letters so they surface in the admin view.
UPDATE notifications SET status = 'DEAD_LETTER' WHERE status = 'FAILED';

-- Index for the retry worker's due-row scan.
CREATE INDEX idx_notifications_due ON notifications (next_attempt_at) WHERE status = 'PENDING';
