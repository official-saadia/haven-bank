-- Idempotency for account opening. Mirrors transactions.idempotency_key: a UNIQUE column is the
-- real guard against a double-submit creating two accounts, since the service-level "already seen?"
-- check is racy under concurrency and the constraint is what actually holds.
--
-- Nullable, unlike the transactions column: accounts predating this change (and any internal/seed
-- accounts opened without a client key) have none, and a partial unique index lets those NULLs
-- coexist while still forbidding two accounts from sharing a key.
ALTER TABLE accounts ADD COLUMN idempotency_key VARCHAR(80);

CREATE UNIQUE INDEX ux_accounts_idempotency_key
    ON accounts (idempotency_key)
    WHERE idempotency_key IS NOT NULL;