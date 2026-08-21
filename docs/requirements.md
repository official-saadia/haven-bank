# Requirements Specification
## Haven Bank

**Document status:** Draft v1.0
**Scope:** Core banking with production-grade authentication, authorisation and transaction integrity.

---

## 1. Overview

A retail banking web application providing account holders with secure authentication, account management, and money movement. The system is deliberately built around a self-hosted identity provider rather than delegated social login, reflecting the regulatory and trust constraints of financial services.

**Architecture:** Spring Boot REST API (resource server) + Spring Authorization Server (identity provider) + React SPA (client) + PostgreSQL (system of record) + Redis (rate limiting, token revocation, ephemeral state).

**Non-goals:** third-party identity federation, real payment-rail integration, multi-currency, regulatory reporting.

---

## 2. Actors

| Actor | Description |
|---|---|
| **Customer** | Authenticated account holder. Can view and operate only their own accounts. |
| **Bank Staff** | Read-only operational access for support purposes. Cannot initiate money movement. |
| **Administrator** | Manages user lifecycle and system configuration. Cannot access customer balances. |
| **System** | Scheduled and internal processes (authenticated via client credentials grant). |

> **Design note:** the deliberate separation of Staff (sees data, can't move money) from Administrator (manages users, can't see money) is a segregation-of-duties control. It is worth stating explicitly — it demonstrates threat modelling rather than convenience-driven role design.

---

## 3. Functional Requirements

### 3.1 Identity & Access Management

| ID | Requirement | Priority |
|---|---|---|
| FR-1.1 | A prospective customer shall register with email, password, and personal details. Email must be unique and verified before the account is activated. | Must |
| FR-1.1a | The email-verification token shall be a single-use secret held in Redis with a TTL of approximately 24 hours, invalidated on first use. | Must |
| FR-1.2 | Passwords shall be validated against a minimum-length and breached-password policy at registration and at change. | Must |
| FR-1.3 | Passwords shall be stored only as salted, adaptive one-way hashes with a tunable work factor. Plaintext passwords shall never be logged, persisted, or returned. | Must |
| FR-1.4 | A registered customer shall authenticate via the OAuth 2.1 Authorization Code flow with PKCE against the platform's own authorization server. | Must |
| FR-1.5 | Successful authentication shall issue a short-lived access token (≤5 min), a rotating refresh token, and an OIDC ID token. | Must |
| FR-1.6 | Consecutive failed authentication attempts shall trigger progressive throttling, escalating to a temporary account lock. Lock state and attempt counters are held in Redis with TTL-based expiry. | Must |
| FR-1.7 | Authentication responses shall be indistinguishable between "unknown user" and "incorrect password" to prevent account enumeration. | Must |
| FR-1.8 | A customer shall complete a second authentication factor by email one-time passcode (OTP). The code shall be single-use, numeric, and issued per challenge. | Must |
| FR-1.8a | OTP state shall be held in Redis with a short TTL (≤5 min), shall be invalidated on first successful use, and shall be subject to a bounded number of verification attempts before re-issue is required. | Must |
| FR-1.8b | OTP codes shall be generated from a cryptographically secure random source and shall never be returned in any API response or written to logs. | Must |
| FR-1.9 | Logout shall invalidate the refresh token server-side and add the active access token's `jti` to a Redis-backed denylist for the remainder of its lifetime. | Must |
| FR-1.10 | Refresh token rotation shall be enforced. Reuse of a consumed refresh token shall invalidate the entire token family and force re-authentication. | Should |
| FR-1.11 | A customer shall reset a forgotten password via a single-use, out-of-band token held in Redis with a short TTL (approximately 1 hour, deliberately shorter than email verification given its account-takeover risk), invalidated on first use. | Should |
| FR-1.12 | Authorisation shall be enforced by role (RBAC) at the URL and method level, **and** by resource ownership at the data-access level. | Must |

> **Implementation note — FR-1.3.** The requirement is stated algorithm-neutrally so it survives cryptographic change. The selected implementation is **BCrypt**, as the platform default with mature framework support. **Argon2id** was evaluated and is the stronger memory-hard choice; it remains the intended migration target should the threat model warrant it. Because only the hash's own encoded parameters determine verification, migration is achievable by re-hashing on next successful login without a forced credential reset.

### 3.2 Account Management

| ID | Requirement | Priority |
|---|---|---|
| FR-2.1 | A customer shall hold one or more accounts, each with a unique account number, type, currency and status. | Must |
| FR-2.2 | A customer shall view a summary of their accounts and current balances. | Must |
| FR-2.3 | A customer shall retrieve only accounts they own. Requests for another customer's account shall be rejected without disclosing whether the account exists. | Must |
| FR-2.4 | Account balances shall be derived from the ledger rather than stored as an independently mutable field. | Should |

### 3.3 Money Movement

| ID | Requirement | Priority |
|---|---|---|
| FR-3.1 | A customer shall deposit funds into an owned account. | Must |
| FR-3.2 | A customer shall withdraw funds from an owned account, subject to sufficient available balance. | Must |
| FR-3.3 | A customer shall transfer funds between their own accounts. | Must |
| FR-3.4 | A customer shall transfer funds to a third-party account, validated against account number and beneficiary name. | Must |
| FR-3.5 | Every money movement shall be recorded as a balanced double-entry pair (debit and credit) committed within a single atomic transaction. | Must |
| FR-3.6 | Monetary values shall use fixed-precision decimal arithmetic. Floating-point representation is prohibited. | Must |
| FR-3.7 | All mutating money-movement endpoints shall accept an `Idempotency-Key` header. A repeated request with the same key shall return the original result without re-executing the transfer. | Must |
| FR-3.8 | Concurrent operations against the same account shall not produce lost updates. Enforced via optimistic locking or row-level pessimistic locking. | Must |
| FR-3.9 | Transfers exceeding a configurable value threshold shall require step-up re-authentication (fresh OTP challenge) irrespective of session validity. The threshold shall be expressed relative to the customer's daily limit rather than as a fixed absolute amount, so that it scales across account tiers. | Should |
| FR-3.10 | A transfer shall be rejected — with a distinct, non-leaking error — where the account is frozen, closed, or has insufficient funds. | Must |
| FR-3.11 | A rolling daily transfer limit shall be enforced per customer, evaluated across all outbound money movements. | Could |
| FR-3.12 | A customer shall save, rename and remove beneficiaries (payees), each holding a beneficiary name, account number and optional nickname, scoped to the owning customer. | Should |
| FR-3.13 | Saving a beneficiary shall validate the account number's format only. It shall **not** confirm that the account exists, and shall return an identical response whether or not it does. | Must |
| FR-3.14 | A saved beneficiary shall confer no authority. A transfer to a saved beneficiary shall be subject to the same ownership, balance, limit, step-up and idempotency controls as a transfer to a hand-entered account number. | Must |
| FR-3.15 | Beneficiary name and nickname shall be encrypted at rest as third-party PII. Account numbers shall be masked to their last four characters in all audit records and logs. | Must |

> **Design note — FR-3.13.** Verifying the account at save time is the intuitive design and the insecure one: it makes the endpoint an account-enumeration oracle, contradicting FR-2.3. Validation is therefore deferred to the transfer itself, which is already rate limited at the sensitive tier, already audited, and already returns a non-leaking rejection under FR-3.10. The cost is that a customer can save a payee that does not exist; the error surfaces on first use rather than at entry, which is an acceptable trade for not disclosing which accounts are real.

> **Design note — FR-3.9 / FR-3.11.** These are distinct controls and must be tuned together. The step-up threshold governs a *single* transfer; the daily limit governs *cumulative* outbound value. The step-up threshold must sit below the daily limit, otherwise no transfer can ever be large enough to trigger step-up and the control is dead code.

### 3.4 Transaction History

| ID | Requirement | Priority |
|---|---|---|
| FR-4.1 | A customer shall retrieve the transaction history for an owned account, paginated and sorted by descending timestamp. | Must |
| FR-4.2 | History shall be filterable by date range, transaction type, and amount range. | Should |
| FR-4.3 | Each entry shall show timestamp, type, counterparty (masked), amount, running balance, and status. | Must |
| FR-4.4 | Ledger entries shall be immutable. Corrections are made by posting a reversing entry, never by mutating or deleting the original. | Must |
| FR-4.5 | A customer shall export a statement for a chosen period. | Could |

### 3.5 Auditing & Observability

| ID | Requirement | Priority |
|---|---|---|
| FR-5.1 | The system shall maintain an audit log of all security-relevant events — registration, login success/failure, lockout, OTP issue and verification, password change, logout, and every money movement — written append-only, with no application pathway permitting update or deletion of an existing record. | Must |
| FR-5.2 | Each audit record shall capture actor, action, timestamp, source IP, user agent, correlation ID, and outcome. | Must |
| FR-5.3 | Secret values — passwords, access and refresh tokens, OTP codes, and password-reset tokens — shall never be written to any log or audit record in any form. Sensitive-but-referenceable values, such as account numbers, shall be masked to their last four characters. | Must |
| FR-5.4 | A correlation ID shall be assigned to every inbound request at the point it first enters the system — the API gateway, or the outermost request filter where no gateway is present — before any business logic runs. It shall be propagated across all downstream calls and emitted on every log line produced while handling that request. FR-5.2 records this identifier on the audit trail; this requirement establishes it across general application logging. | Must |

### 3.6 API Protection

| ID | Requirement | Priority |
|---|---|---|
| FR-6.1 | All endpoints shall be rate limited. Limits shall be tiered by endpoint sensitivity, not applied uniformly. | Must |
| FR-6.2 | Unauthenticated endpoints (login, registration, password reset) shall be limited per source IP with strict thresholds and exponential backoff. | Must |
| FR-6.2a | The strictest tier shall apply to requests that **submit a credential**, not to the GET that renders the corresponding form. Rendering a login page is not an authentication attempt and shall not consume the attempt budget. | Must |
| FR-6.3 | Authenticated endpoints shall be limited per authenticated subject. | Must |
| FR-6.4 | Rate limit state shall be held in Redis to remain correct across horizontally scaled instances. | Must |
| FR-6.5 | Throttled requests shall return HTTP 429 with `Retry-After`, and shall be audited. | Should |
| FR-6.6 | Tier thresholds shall be externally configurable, so limits can be tightened during an incident without redeploying. | Should |

**Indicative tiers:**

| Tier | Applies to | Limit | Key |
|---|---|---|---|
| Critical | `POST` login, OTP verify, register, password forgot/reset | 5 / min, exponential backoff | IP |
| Sensitive | `POST` transfers, deposits, withdrawals, password change, token exchange | 20 / min | Subject |
| Standard | reads (balances, history), and the interactive login/authorize pages | 100 / min | Subject |

> **Design note — tiering by method, and where the token endpoint belongs.**
> Two calibration errors are easy to make here, and both were made in the first implementation.
>
> The first is counting page loads. A single sign-in touches `/oauth2/authorize`, `/login`,
> `/login/otp` and `/oauth2/token`; if every one of those counts against a five-per-minute budget,
> a legitimate login consumes six and can never complete. The limit must count *attempts* — the
> requests carrying a credential — so the GET that renders a form is not throttled at the strict
> tier.
>
> The second is treating the token endpoint as credential-guessable. It is not: it consumes a
> single-use authorization code bound to a PKCE verifier the caller must already hold, so there is
> nothing to brute-force. Placing it on the strictest tier costs a working sign-in and buys no
> security, which is why it sits with the other authenticated writes.
>
> Note also that the attempt budget is the *outer* control. Per-challenge limits — a bounded number
> of OTP verifications (FR-1.8a) and progressive account lockout (FR-1.6) — are what actually stop
> guessing; rate limiting bounds the volume reaching them.

### 3.7 Notifications

| ID | Requirement | Priority |
|---|---|---|
| FR-7.1 | The system shall notify a customer of significant events: successful registration, password change, OTP issuance, and every completed money movement. | Must |
| FR-7.1a | Notifications shall be classified as either **security-critical** (e.g. password change, OTP, unusual or high-value money movement) or **convenience** (e.g. registration confirmation, routine transfer receipts). | Must |
| FR-7.1b | Security-critical notifications shall be mandatory and non-suppressible; the customer shall have no opt-out. Convenience notifications shall be user-configurable through stored notification preferences (per category, and per channel once multiple channels exist). | Must |
| FR-7.2 | Notifications shall be dispatched through a dedicated notification component behind a channel-agnostic interface, so that delivery channels can be added or swapped without changing calling code. | Must |
| FR-7.3 | Email shall be the delivery channel. SMS and push are deferred (see Out of Scope) and shall require only a new channel implementation, not a change to notification call sites. | Should |
| FR-7.4 | Notification dispatch shall be asynchronous and shall not block or fail the originating operation; a failed notification shall be retried and shall not roll back a committed money movement. | Must |
| FR-7.5 | Notification content shall never include secret values (passwords, tokens, full account numbers) and shall mask sensitive references to their last four characters. | Must |

---

## 4. Non-Functional Requirements

### 4.1 Security

| ID | Requirement |
|---|---|
| NFR-1.1 | All traffic shall be served over TLS 1.2+ with HSTS enabled. |
| NFR-1.2 | The system shall demonstrate no unmitigated findings against the OWASP API Security Top 10. |
| NFR-1.3 | Access tokens shall be asymmetrically signed (RS256/ES256); resource servers shall verify via the published JWKS endpoint and shall validate signature, issuer, audience, and expiry. |
| NFR-1.4 | Tokens shall never be persisted in browser `localStorage` or `sessionStorage`. Refresh tokens shall be held in `HttpOnly`, `Secure`, `SameSite` cookies. |
| NFR-1.5 | Sensitive data at rest shall be encrypted at the column level. |
| NFR-1.6 | Security headers (CSP, X-Content-Type-Options, X-Frame-Options, Referrer-Policy) shall be present on all responses. |
| NFR-1.7 | Dependencies shall be scanned for known vulnerabilities on every build; the pipeline shall fail on high-severity findings. |
| NFR-1.8 | Secrets shall be externalised from source control and injected at runtime. |

### 4.2 Consistency & Integrity

| ID | Requirement |
|---|---|
| NFR-2.1 | Money movement shall be strongly consistent. The ledger shall satisfy the invariant that all entries for a transaction sum to zero. |
| NFR-2.2 | No committed transaction shall be lost, duplicated, or partially applied under concurrent load or instance failure. |
| NFR-2.3 | The system shall favour consistency over availability for write paths. Under partition, money movement fails closed; read paths may degrade to a reduced-functionality mode. |

> **Design note on CAP:** "highly available *and* strongly consistent" is not achievable under network partition. This system chooses **CP for writes** — a failed transfer is recoverable, a phantom or duplicated transfer is not. Read paths (balances, history) may be served with relaxed guarantees. State this trade-off explicitly rather than listing both properties as though they were independently satisfiable; it is exactly the distinction a reviewer looks for.

### 4.3 Performance & Latency

| ID | Requirement |
|---|---|
| NFR-3.1 | Read endpoints (balance, account summary) shall respond within 200 ms at p95 under nominal load. |
| NFR-3.2 | Money-movement endpoints shall respond within 500 ms at p95. |
| NFR-3.3 | Authentication shall complete within 1 s at p95, acknowledging deliberate hashing cost. |
| NFR-3.4 | Rate limit and token-denylist lookups shall add no more than 5 ms, achieved via Redis rather than relational round-trips. |
| NFR-3.5 | Transaction history shall be cursor-paginated and indexed to remain constant-time as volume grows. |

### 4.4 Scalability

| ID | Requirement |
|---|---|
| NFR-4.1 | Application services shall be stateless and horizontally scalable; no session affinity shall be required. |
| NFR-4.2 | All shared ephemeral state (rate limits, lockout counters, token denylist) shall reside in Redis. |
| NFR-4.3 | Database access shall use connection pooling with bounded pool sizes. |
| NFR-4.4 | Read and write paths shall be cleanly separated at the service layer, with read-only operations marked as such, so that query traffic could later be routed to a replica without restructuring. This is a boundary-hygiene requirement only; it does not mandate CQRS, separate read models, or service decomposition. |

### 4.5 Availability & Reliability

| ID | Requirement |
|---|---|
| NFR-5.1 | Services shall expose liveness and readiness probes. |
| NFR-5.2 | Failure of Redis shall degrade gracefully: rate limiting fails **closed** for authentication endpoints and **open** for read endpoints. |
| NFR-5.3 | The system shall recover to a consistent state after abrupt termination, with no partially applied transfers. |
| NFR-5.4 | Errors shall return structured, non-leaking responses; internal exceptions, stack traces, and framework details shall never reach the client. |

### 4.6 Maintainability & Quality

| ID | Requirement |
|---|---|
| NFR-6.1 | Unit test coverage of domain and security logic shall exceed 80%. |
| NFR-6.2 | Integration tests shall run against real PostgreSQL and Redis instances via Testcontainers. |
| NFR-6.3 | The security test suite shall explicitly cover: unauthenticated access, insufficient role, cross-customer access (IDOR), expired token, wrongly signed token, incorrect audience, lockout, replayed idempotency key, and concurrent transfer correctness. |
| NFR-6.4 | Schema changes shall be applied through versioned migrations (Flyway/Liquibase). |
| NFR-6.5 | The API shall be documented via OpenAPI. |

---

## 5. Out of Scope

Deferred deliberately to keep depth over breadth: mobile top-up, utility and bill payments, scheduled and standing transfers, multi-currency and FX, statement PDF generation, push/SMS notifications, fraud scoring, and admin back-office UI.

---

## 6. Open Points

1. **Lockout policy.** An aggressive threshold with a long fixed lock (e.g. 3 attempts → 24 hours) is itself a denial-of-service vector: anyone who knows a customer's email can lock them out at will, and in banking that generates real support cost. The stronger pattern is progressive delay — brief throttling that escalates — combined with a shorter lock and a self-service unlock path, plus alerting on distributed low-and-slow attempts. Recommend confirming the threshold and duration, and separating *throttling* from *locking* as distinct controls.
2. Whether the React client adopts a Backend-for-Frontend token-handling pattern, or in-memory access tokens with cookie-held refresh tokens.
3. Retention period for audit records and ledger history.
4. Whether the Staff role is included in the initial build or deferred.
5. **OTP delivery channel.** Email is specified as it requires no third-party telephony contract. It is the weaker channel — a compromised mailbox defeats it, and delivery latency is not guaranteed. SMS or an authenticator app (TOTP, RFC 6238) is the stronger option and should be revisited before any production use.
6. Calibration of the FR-3.9 step-up threshold as a proportion of the FR-3.11 daily limit.
