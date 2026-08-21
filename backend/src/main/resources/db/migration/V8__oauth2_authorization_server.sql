-- Spring Authorization Server token store, backing JdbcOAuth2AuthorizationService.
--
-- This replaces the default in-memory OAuth2AuthorizationService, which loses every authorization
-- (authorization codes, access/refresh tokens, and the refresh-token rotation/reuse-detection state)
-- on restart and cannot be shared across instances. Persisting it here makes the auth server stateless
-- and horizontally scalable (NFR-4.1/4.2) and makes refresh-token family revocation on reuse durable
-- rather than best-effort (FR-1.10).
--
-- This is the canonical Spring Authorization Server schema with one Postgres adaptation the upstream
-- schema itself mandates: every column defined as 'blob' becomes 'text', because Postgres has no
-- 'blob' type. JdbcOAuth2AuthorizationService inspects the column metadata at startup and reads/writes
-- these as strings when they are not BLOB, so 'text' is fully supported. The device_code/user_code
-- columns are part of the standard schema (device-authorization grant) and remain unused here.
--
-- Only the authorization table is needed: the registered client is held in memory (configured under
-- spring.security.oauth2.authorizationserver.client in application.yaml) and consent is disabled, so
-- the registered-client and authorization-consent tables are intentionally omitted.

CREATE TABLE oauth2_authorization (
                                      id varchar(100) NOT NULL,
                                      registered_client_id varchar(100) NOT NULL,
                                      principal_name varchar(200) NOT NULL,
                                      authorization_grant_type varchar(100) NOT NULL,
                                      authorized_scopes varchar(1000) DEFAULT NULL,
                                      attributes text DEFAULT NULL,
                                      state varchar(500) DEFAULT NULL,
                                      authorization_code_value text DEFAULT NULL,
                                      authorization_code_issued_at timestamp DEFAULT NULL,
                                      authorization_code_expires_at timestamp DEFAULT NULL,
                                      authorization_code_metadata text DEFAULT NULL,
                                      access_token_value text DEFAULT NULL,
                                      access_token_issued_at timestamp DEFAULT NULL,
                                      access_token_expires_at timestamp DEFAULT NULL,
                                      access_token_metadata text DEFAULT NULL,
                                      access_token_type varchar(100) DEFAULT NULL,
                                      access_token_scopes varchar(1000) DEFAULT NULL,
                                      oidc_id_token_value text DEFAULT NULL,
                                      oidc_id_token_issued_at timestamp DEFAULT NULL,
                                      oidc_id_token_expires_at timestamp DEFAULT NULL,
                                      oidc_id_token_metadata text DEFAULT NULL,
                                      refresh_token_value text DEFAULT NULL,
                                      refresh_token_issued_at timestamp DEFAULT NULL,
                                      refresh_token_expires_at timestamp DEFAULT NULL,
                                      refresh_token_metadata text DEFAULT NULL,
                                      user_code_value text DEFAULT NULL,
                                      user_code_issued_at timestamp DEFAULT NULL,
                                      user_code_expires_at timestamp DEFAULT NULL,
                                      user_code_metadata text DEFAULT NULL,
                                      device_code_value text DEFAULT NULL,
                                      device_code_issued_at timestamp DEFAULT NULL,
                                      device_code_expires_at timestamp DEFAULT NULL,
                                      device_code_metadata text DEFAULT NULL,
                                      PRIMARY KEY (id)
);