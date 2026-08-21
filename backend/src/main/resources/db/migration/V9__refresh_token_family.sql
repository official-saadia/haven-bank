-- Refresh-token reuse detection with family revocation (RFC 9700 / OAuth 2.0 Security BCP).
--
-- Spring Authorization Server rotates refresh tokens but does not detect replay of a *consumed*
-- token: on rotation it overwrites the value, so a replayed old token is merely "not found". This
-- table retains the lineage (a family_id shared by every refresh token descended from one login)
-- and marks each token consumed on rotation. Replay of a consumed token is then detectable, and the
-- policy here is maximum safety: revoke ALL of that user's sessions.
--
-- Only a SHA-256 hash of each refresh token is stored, never the raw value. Durable in Postgres
-- (not Redis) because this is security-critical revocation state that must survive restarts.

CREATE TABLE oauth2_refresh_token_family (
                                             token_hash       varchar(64)  NOT NULL,          -- SHA-256 hex of the refresh token value
                                             family_id        uuid         NOT NULL,
                                             authorization_id varchar(100) NOT NULL,
                                             principal_name   varchar(200) NOT NULL,
                                             consumed         boolean      NOT NULL DEFAULT false,
                                             created_at       timestamp    NOT NULL DEFAULT now(),
                                             PRIMARY KEY (token_hash)
);

CREATE INDEX idx_rt_family_authorization ON oauth2_refresh_token_family (authorization_id);
CREATE INDEX idx_rt_family_principal     ON oauth2_refresh_token_family (principal_name);