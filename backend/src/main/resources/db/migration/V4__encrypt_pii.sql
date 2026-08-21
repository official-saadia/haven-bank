-- ============================================================================
-- V4  Widen PII columns that now store ciphertext (AES-GCM, Base64) at rest.
--     full_name is encrypted via the application-layer CryptoConverter (NFR-1.5).
-- ============================================================================
ALTER TABLE users ALTER COLUMN full_name TYPE VARCHAR(512);
