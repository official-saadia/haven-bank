-- STAFF was seeded with no permissions, so its "read-only operational support" authority (see the
-- actors table in the requirements) was expressed only through role checks, and its permission set
-- rendered blank. Model that access explicitly instead: an AUDIT_READ permission, granted to STAFF
-- and ADMIN (both read the security audit log). The audit endpoint is then gated on this permission
-- rather than the raw role, making the permission catalogue the single source of truth.

INSERT INTO permissions (id, name, description, created_at, updated_at) VALUES
    (gen_random_uuid(), 'AUDIT_READ', 'Read the security audit log', now(), now());

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('STAFF', 'ADMIN') AND p.name = 'AUDIT_READ';