-- ============================================================================
-- V2  Money module: accounts, double-entry ledger, transactions, fees, policy.
-- ============================================================================

CREATE TABLE accounts (
    id               UUID PRIMARY KEY,
    user_id          UUID,                       -- null for INTERNAL accounts
    account_category VARCHAR(16)  NOT NULL,
    account_number   VARCHAR(34)  NOT NULL UNIQUE,
    type             VARCHAR(16)  NOT NULL,
    currency         VARCHAR(3)      NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    opened_at        TIMESTAMPTZ  NOT NULL,
    closed_at        TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT chk_account_owner CHECK (
        (account_category = 'CUSTOMER' AND user_id IS NOT NULL) OR
        (account_category = 'INTERNAL' AND user_id IS NULL)
    )
);
CREATE INDEX idx_accounts_user ON accounts (user_id);

CREATE TABLE transactions (
    id                     UUID PRIMARY KEY,
    reference_number       VARCHAR(32)  NOT NULL UNIQUE,
    type                   VARCHAR(16)  NOT NULL,
    status                 VARCHAR(16)  NOT NULL,
    initiating_user_id     UUID         NOT NULL,
    source_account_id      UUID REFERENCES accounts(id),
    destination_account_id UUID REFERENCES accounts(id),
    amount                 NUMERIC(19,4) NOT NULL,
    fee_amount             NUMERIC(19,4) NOT NULL DEFAULT 0,
    fee_schedule_id        UUID,
    currency               VARCHAR(3)      NOT NULL,
    idempotency_key        VARCHAR(80)  NOT NULL UNIQUE,
    correlation_id         VARCHAR(64),
    created_at             TIMESTAMPTZ  NOT NULL,
    completed_at           TIMESTAMPTZ
);
CREATE INDEX idx_txn_user    ON transactions (initiating_user_id);
CREATE INDEX idx_txn_source  ON transactions (source_account_id, created_at DESC);
CREATE INDEX idx_txn_dest    ON transactions (destination_account_id, created_at DESC);

-- Append-only. No UPDATE/DELETE pathway (FR-4.4).
CREATE TABLE ledger_entries (
    id             UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    account_id     UUID NOT NULL REFERENCES accounts(id),
    direction      VARCHAR(8)   NOT NULL,
    amount         NUMERIC(19,4) NOT NULL,
    balance_after  NUMERIC(19,4),
    created_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_ledger_account ON ledger_entries (account_id);
CREATE INDEX idx_ledger_txn     ON ledger_entries (transaction_id);

CREATE TABLE fee_schedules (
    id             UUID PRIMARY KEY,
    applies_to     VARCHAR(16)  NOT NULL,
    tier_min       NUMERIC(19,4),
    tier_max       NUMERIC(19,4),
    fee_flat       NUMERIC(19,4) NOT NULL,
    fee_percent    NUMERIC(6,4)  NOT NULL,
    effective_from TIMESTAMPTZ  NOT NULL,
    effective_to   TIMESTAMPTZ
);

CREATE TABLE policies (
    id             UUID PRIMARY KEY,
    policy_key     VARCHAR(32)  NOT NULL,
    scope          VARCHAR(32)  NOT NULL,
    value          NUMERIC(19,4) NOT NULL,
    effective_from TIMESTAMPTZ  NOT NULL,
    effective_to   TIMESTAMPTZ
);

-- --- Seed bank-internal accounts, an initial fee schedule, and policy thresholds ---
INSERT INTO accounts (id, user_id, account_category, account_number, type, currency, status, opened_at)
VALUES
    (gen_random_uuid(), NULL, 'INTERNAL', 'BANK-CASH-0001', 'CASH',       'GBP', 'ACTIVE', now()),
    (gen_random_uuid(), NULL, 'INTERNAL', 'BANK-FEE-0001',  'FEE_INCOME', 'GBP', 'ACTIVE', now());

-- Flat GBP 0.00 base fee + 0% for transfers (a real bank would tier this); versioned by effective_from.
INSERT INTO fee_schedules (id, applies_to, tier_min, tier_max, fee_flat, fee_percent, effective_from)
VALUES (gen_random_uuid(), 'TRANSFER', NULL, NULL, 0.00, 0.0000, now());

-- Step-up threshold must sit below the daily limit (FR-3.9 design note).
INSERT INTO policies (id, policy_key, scope, value, effective_from) VALUES
    (gen_random_uuid(), 'STEP_UP_THRESHOLD', 'GLOBAL', 1000.00, now()),
    (gen_random_uuid(), 'DAILY_LIMIT',       'GLOBAL', 10000.00, now());
