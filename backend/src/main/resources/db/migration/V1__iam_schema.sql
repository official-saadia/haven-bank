-- ============================================================================
-- V1  IAM (user) module schema: users, RBAC, and the audit trail.
-- Owned exclusively by the IAM/audit modules. Money-module tables arrive in
-- later, module-scoped migrations (V2+), not in one monolithic baseline.
-- ============================================================================

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    name        VARCHAR(32)  NOT NULL UNIQUE,
    description VARCHAR(256),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE permissions (
    id          UUID PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL UNIQUE,
    description VARCHAR(256),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id)       ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    id             UUID PRIMARY KEY,
    email          VARCHAR(320) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(200) NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE TABLE user_roles (
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id    UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

-- Append-only. No UPDATE/DELETE pathway exists in application code (FR-5.1).
CREATE TABLE audit_trail (
    id             UUID PRIMARY KEY,
    actor_user_id  UUID,
    action         VARCHAR(64)  NOT NULL,
    target_type    VARCHAR(64),
    target_id      VARCHAR(128),
    outcome        VARCHAR(16)  NOT NULL,
    detail         VARCHAR(512),
    source_ip      VARCHAR(64),
    user_agent     VARCHAR(256),
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_audit_actor   ON audit_trail (actor_user_id);
CREATE INDEX idx_audit_action  ON audit_trail (action);
CREATE INDEX idx_audit_created ON audit_trail (created_at);

-- --- Seed baseline roles & permissions -------------------------------------
INSERT INTO permissions (id, name, description, created_at, updated_at) VALUES
    (gen_random_uuid(), 'ACCOUNT_READ',     'View own accounts and balances',      now(), now()),
    (gen_random_uuid(), 'TRANSFER_EXECUTE', 'Initiate money movement',             now(), now()),
    (gen_random_uuid(), 'USER_MANAGE',      'Manage users, roles and permissions', now(), now());

INSERT INTO roles (id, name, description, created_at, updated_at) VALUES
    (gen_random_uuid(), 'CUSTOMER', 'Account holder',                now(), now()),
    (gen_random_uuid(), 'STAFF',    'Read-only operational support',  now(), now()),
    (gen_random_uuid(), 'ADMIN',    'User and system administration', now(), now());

-- CUSTOMER: read accounts + execute transfers
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'CUSTOMER' AND p.name IN ('ACCOUNT_READ', 'TRANSFER_EXECUTE');

-- ADMIN: manage users
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN' AND p.name = 'USER_MANAGE';
