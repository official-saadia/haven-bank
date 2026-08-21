-- ============================================================================
-- V3  Notification records + per-user convenience preferences.
-- ============================================================================

CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    user_id         UUID,
    recipient_email VARCHAR(320),
    type            VARCHAR(32)  NOT NULL,
    category        VARCHAR(24)  NOT NULL,
    channel         VARCHAR(16)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_notifications_user ON notifications (user_id);

CREATE TABLE notification_preferences (
    id       UUID PRIMARY KEY,
    user_id  UUID        NOT NULL,
    type     VARCHAR(32) NOT NULL,
    channel  VARCHAR(16) NOT NULL,
    enabled  BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_pref UNIQUE (user_id, type, channel)
);
