-- ============================================================================
-- V5  Saved beneficiaries (payees) — owned by a customer, used to prefill a
--     transfer. Deliberately NOT a foreign key to accounts: a saved payee is
--     the customer's own address book entry, not an assertion that the account
--     exists. Existence is proven only at transfer time (see BeneficiaryService).
-- ============================================================================

CREATE TABLE beneficiaries (
    id             UUID PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users(id),
    -- Third-party PII: encrypted at rest via CryptoConverter (NFR-1.5), hence the width.
    name           VARCHAR(512) NOT NULL,
    nickname       VARCHAR(512),
    -- Plaintext, matching accounts.account_number, so (user_id, account_number)
    -- can carry a real uniqueness constraint rather than a best-effort app check.
    account_number VARCHAR(34)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_beneficiary_per_user UNIQUE (user_id, account_number)
);

CREATE INDEX idx_beneficiaries_user ON beneficiaries (user_id, created_at DESC);
