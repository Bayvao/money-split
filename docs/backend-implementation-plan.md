# Money Split Backend Implementation Plan

**Status:** Approved incremental execution plan
**Scope:** Backend only  
**Source of truth:** [Technical design](design.md)  
**Current baseline:** Spring Boot application with CI/security automation and no merged domain implementation

## 1. Objective

Deliver the backend as a modular Spring Boot monolith backed by MySQL. Work in small, production-shaped vertical slices that expose usable API behavior, persist through Liquibase-managed schemas, enforce authorization, and include automated tests.

This plan does not include web or mobile implementation. It does include the REST and synchronization contracts required by those clients.

## 2. Delivery principles

1. Build one independently reviewable capability at a time. A behavior PR includes only the API, application, domain, persistence, security, tests, and documentation needed for that behavior.
2. Allow a schema-only prerequisite only when it has direct MySQL integration tests and the next behavior depends on the database contract.
3. Use TDD for domain and application behavior. Add the failing test before implementation; write migration tests before or with schema changes.
4. Add JavaDoc to every new or changed Java method and constructor, including test methods and helpers. State what it does and document parameters, return values, and declared exceptions where applicable. Use `{@inheritDoc}` only when the inherited contract is complete.
5. Inside methods, add concise intent comments before important or non-obvious business rules, algorithms, transaction or lock ordering, and SQL. Query comments explain purpose, authorization scope, filtering, joins, or locking behavior rather than restating syntax.
6. Keep financial calculations pure and independent of Spring, SQL, clocks, networks, and providers.
7. Use MySQL for persistence integration tests. Do not use H2 as a substitute for MySQL constraints or locking.
8. Apply Liquibase migrations before adding repository code that depends on them.
9. Keep external identity, FX, and notification vendors behind narrow ports. Select vendors at the latest responsible milestone.
10. Keep controllers thin. They validate transport input and call application services; they do not calculate balances or access JDBC directly.
11. Keep the immutable journal and revisions authoritative. Treat balances and reports as rebuildable projections.
12. Do not introduce message brokers, Redis, Kubernetes, JPA, event sourcing frameworks, or service decomposition before a measured need.

## 3. Current project assessment

The repository currently contains:

- Java 25 and Spring Boot 4.1.2.
- Gradle Kotlin DSL.
- Spring MVC, Spring Data JDBC, Liquibase, MySQL Connector/J, Actuator, and springdoc OpenAPI.
- One application class and one context-load test.
- No Liquibase master changelog, database configuration, security configuration, domain code, or API behavior.

The first implementation must therefore establish conventions that later financial code can safely reuse.

## 4. Target package structure

Create packages only when their first behavior is implemented.

```text
src\main\java\com\money\split\
|-- MoneySplitApplication.java
|-- platform\
|   |-- api\                 # Problem responses, cursor and HTTP conventions
|   |-- config\              # Typed application configuration
|   |-- persistence\         # JDBC mapping and transaction support shared by modules
|   `-- security\            # JWT validation and CurrentActor boundary
|-- identity\
|   |-- application\
|   |-- domain\
|   `-- persistence\
|-- groups\
|   |-- api\
|   |-- application\
|   |-- domain\
|   `-- persistence\
|-- money\
|   `-- domain\              # Money, currency metadata, allocation and rounding
|-- fx\
|   |-- api\
|   |-- application\
|   |-- domain\
|   `-- persistence\
|-- expenses\
|   |-- api\
|   |-- application\
|   |-- domain\
|   `-- persistence\
|-- ledger\
|   |-- application\
|   |-- domain\
|   `-- persistence\
|-- settlements\
|   |-- api\
|   |-- application\
|   |-- domain\
|   `-- persistence\
|-- personal\
|   |-- api\
|   |-- application\
|   |-- domain\
|   `-- persistence\
|-- budgets\
|   |-- api\
|   |-- application\
|   |-- domain\
|   `-- persistence\
|-- reports\
|   |-- api\
|   |-- application\
|   `-- persistence\
|-- sync\
|   |-- api\
|   |-- application\
|   `-- persistence\
`-- notifications\
    |-- application\
    |-- domain\
    `-- persistence\
```

Package rules:

- `domain` contains plain Java types and rules with no Spring, HTTP, or JDBC annotations.
- `application` owns use cases, transactions, authorization checks, and external ports.
- `api` owns controllers and request/response records.
- `persistence` owns SQL, row mapping, and implementations of application repository ports.
- A module may invoke another module only through its public application interface.
- Do not create an interface for every class. Use interfaces at database, identity, clock, FX, notification, and other replaceable boundaries.
- Prefer package-private implementation types unless another module has a reason to use them.

Spring Modulith and ArchUnit are deferred. Package discipline and focused integration tests are sufficient initially; add automated module-boundary checks when the module count makes violations likely.

## 5. Initial build and dependency changes

### 5.1 Required for F01

Add dependencies compatible with the Spring Boot dependency-management version:

| Change | Purpose |
|---|---|
| Testcontainers JUnit and MySQL modules | Run persistence, Liquibase, authorization, and locking tests against MySQL when a container runtime is available |

Use the Spring Boot BOM for managed versions where available. Do not hardcode library versions until Gradle dependency resolution confirms whether Boot manages them.

Add the OAuth2 resource-server starter, validation starter, Spring Security test support, and JaCoCo only with F02, when production Java security behavior first requires them.

The existing Spring Data JDBC dependency is sufficient for `JdbcClient`, `NamedParameterJdbcTemplate`, transactions, and explicit repositories. Do not add JPA.

### 5.2 Deferred dependencies

| Dependency or platform | Add only when |
|---|---|
| HTTP client/resilience library | An FX or notification provider is selected and the JDK/Spring client is insufficient |
| Redis | Measured multi-instance rate-limiting or cache requirements justify it |
| Queue/broker | Transactional-outbox workers cannot meet measured throughput or isolation needs |
| Object-storage SDK | Receipt attachments enter scope |
| Property-testing library | JUnit deterministic generators become difficult to maintain |
| Database migration rollback tooling | Operations choose a rollback policy requiring it |
| Spring Modulith/ArchUnit | Module boundaries begin to regress |

Do not add Lombok annotations automatically. Java records and explicit constructors are preferred for immutable data and transparent dependency injection.

## 6. Configuration and runtime profiles

Keep secrets and environment-specific values outside source control.

### 6.1 Profiles

| Profile | Use |
|---|---|
| Default | Production-shaped behavior; F01 requires datasource configuration and F02 adds OIDC configuration |
| `local` | Developer MySQL; from F02 onward use a real/local OIDC issuer and never a trusted-header bypass |
| `test` | Isolated MySQL datasource; from F02 onward add a test-only JWT decoder or Spring Security test principal support |

After F02, tests must not contact a real OIDC issuer. Test security configuration belongs under `src\test`, not production sources.

### 6.2 F01 environment variables

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

F01 requires only the three datasource variables. F02 introduces the OIDC variables, and F06 introduces `APP_GROUP_MAX_MEMBERS`. Later features add variables such as FX provider URL/key, rate-age limit, idempotency retention, sync retention, outbox lease duration, and notification provider credentials.

Validate required production configuration at startup. Do not provide default credentials or a production authentication bypass. Redact secret values from startup errors and logs.

### 6.3 Local and integration-test MySQL

Preferred integration-test path:

- Testcontainers starts a pinned MySQL image.
- Spring's dynamic properties or service-connection support supplies the datasource.
- Liquibase applies the same changelog used in production.

This path requires a Docker-compatible container runtime. F01 also supports a datasource supplied through environment variables; CI uses an isolated MySQL service. Both paths run MySQL and must never point tests at a shared development or production schema.

## 7. Cross-cutting rules introduced when relevant

### 7.1 API rules

- Base path: `/api/v1`.
- JSON field names use lower camel case.
- Resource IDs are UUID strings; internal MySQL storage uses `BINARY(16)`.
- Monetary values are decimal strings paired with ISO currency codes.
- Use `application/problem+json` with stable error codes and a request ID.
- Return 404 rather than exposing an inaccessible group through authorization differences.
- Use opaque cursor pagination; never expose offset pagination as a durable sync contract.
- Return `ETag` for mutable resources and require `If-Match` for updates.
- Generate OpenAPI from controllers and explicitly document non-2xx responses.
- Never accept the authenticated user ID from a request body or query parameter.

### 7.2 Idempotency rules

Group creation in F04 does not need the financial idempotency subsystem. Add it before the first financial write in F16.

For financial commands:

- Require `Idempotency-Key`.
- Scope the key to authenticated user, HTTP method, and canonical path.
- Bind it to a canonical request hash.
- Return the original outcome for the same key and payload.
- Return 409 for the same key with a different payload.
- Keep permanent uniqueness on resource IDs and journal source keys even after response records expire.
- Expose operation recovery before offline upload is released.

### 7.3 Transaction and lock rules

- Put `@Transactional` on application use cases, not controllers.
- Do external provider calls before opening a financial transaction.
- Lock the target group row before membership-sensitive or financial writes.
- Lock the personal-ledger row before personal financial writes.
- Acquire group and personal locks in separate transactions; use outbox events for cross-ledger projections.
- Increment ledger version and append sync/audit records in the same transaction as the business change.
- Use bounded retry only for identified transient deadlocks and only for idempotent commands.
- Never catch a database error and return a success-shaped response.

## 8. Implementation milestones

Sizing is relative complexity, not a calendar estimate:

- **S:** 1-3 points
- **M:** 3-5 points
- **L:** 5-8 points
- No implementation PR may exceed M. Split any feature estimated as L before coding begins.

### Small-feature pull request sequence

Each row is one branch, one pull request, one change record under `docs\prs`, and one exact commit message. A feature may start only after all dependencies are merged into `master` and the branch has been created from a freshly pulled `origin/master`.

| ID | Feature | Size | Depends on | Independently verifiable outcome |
|---|---|---:|---|---|
| F01 | Foundation schema and currency catalog | S | CI baseline | Liquibase creates the five foundation tables and exact supported currencies on MySQL |
| F02 | JWT request-security boundary | S | F01 | Issuer, audience, timestamps, subject mapping, `401`, and public-route behavior are tested |
| F03 | Concurrent application-user provisioning | S | F02 | Exact `(issuer, subject)` identities resolve to one durable user under concurrent first access |
| F04 | Authenticated group creation | M | F03 | `POST /groups` atomically creates the group, creator membership, and audit row |
| F05 | Membership-scoped group reads | S | F04 | List/get expose only active memberships and hide inaccessible IDs with the same `404` |
| F06 | Invitation issuance | S | F05 | An active member creates an expiring, hashed, recipient-bound invitation |
| F07 | Invitation acceptance | M | F06 | Single-use acceptance enforces recipient, expiry, replay, concurrency, and member limit |
| F08 | Group member listing | S | F07 | Active members can list the current membership without privilege fields |
| F09 | Optimistic group rename | S | F05 | Any active member can rename with `If-Match`; stale versions fail |
| F10 | Money and minor-unit primitives | S | F01 | Precision, overflow, currency scale, and exact arithmetic are pure and deterministic |
| F11 | Equal and exact allocation | S | F10 | Both modes conserve totals with stable remainder ordering |
| F12 | Percentage and share allocation | S | F11 | Both modes conserve totals and reject invalid weights or percentages |
| F13 | Same-currency and manual FX resolution | S | F10 | Rate provenance, precision, staleness rules, and conversion invariants are pure |
| F14 | Stored FX snapshots and lookup | M | F13 | Immutable rates persist and the authorized lookup API selects valid history |
| F15 | Expense, journal, balance, and idempotency schema | M | F05, F11 | MySQL constraints prove immutable posting and uniqueness contracts |
| F16 | Equal same-currency expense posting | M | F15 | One financial transaction writes expense, journal, balance, audit, and idempotency outcome |
| F17 | Expense reads and operation recovery | S | F16 | Authorized get/list and retry-outcome recovery return the stored posting |
| F18 | Exact, percentage, and share expense inputs | S | F12, F16 | API validation maps each split mode to stored authoritative allocations |
| F19 | Multi-currency expense posting | M | F14, F18 | Original/base amounts and immutable FX provenance conserve totals |
| F20 | Expense replacement revision | M | F19 | Creator-only `PUT` reverses then replaces once under optimistic locking |
| F21 | Expense logical deletion | S | F20 | Creator-only `DELETE` appends one exact reversal and preserves history |
| F22 | Balance reads and projection rebuild | M | F21 | Stored balances match a rebuild from the immutable journal |
| F23 | Debt simplification | S | F22 | Deterministic suggestions preserve every net position and use at most `n - 1` transfers |
| F24 | Group leave workflow | S | F22 | Only zero-balance members without blocking financial workflows can leave |
| F25 | Settlement proposal | S | F22 | Authorized partial/full proposals persist as pending without changing balances |
| F26 | Settlement decision workflow | M | F25 | Receiver confirm/reject and sender cancel transitions are race-safe and authorized |
| F27 | Consensual settlement reversal | S | F26 | Approved reversal creates one immutable compensating journal batch |
| F28 | Personal ledger and category foundation | M | F14 | Private reporting currency and category lifecycle remain user-isolated |
| F29 | Personal entry create and read | M | F28 | Manual entries use immutable revisions, idempotency, and stable FX |
| F30 | Personal entry correction | S | F29 | Update/delete append reversals and preserve history |
| F31 | Transactional outbox worker | M | F21, F30 | Lease, retry, replay, and dead-letter behavior never changes source transactions |
| F32 | Shared-expense personal attribution | M | F31 | Each participant receives only their allocation in source-version order |
| F33 | Monthly budgets | S | F32 | Overall/category limits evaluate by reporting month and user timezone |
| F34 | Monthly reports | M | F33 | Income, spending, cash flow, attribution completeness, and balances reconcile |
| F35 | Ordered incremental synchronization | M | F27, F34 | Authorized scope sequences, changes, tombstones, retention, and cursor expiry are gap-free |
| F36 | Materialized bootstrap snapshots | M | F35 | A bounded snapshot resumes from its captured high-water mark without gaps |
| F37 | Outbox-backed notifications | M | F31 | Deduplicated delivery retries cannot roll back financial transactions |
| F38 | Authorization and input hardening | M | All public features | Authorization matrix, request limits, privacy responses, and log redaction pass |
| F39 | Rate limiting and observability | M | F38 | Selected rate limits, health, logs, metrics, traces, and alerts are production-ready |
| F40 | Reconciliation and rebuild operations | M | F39 | Integrity checks block unsafe writes and projection rebuilds are exercised |
| F41 | Load, backup, restore, and release evidence | M | F40 | Capacity, recovery, OpenAPI compatibility, and smoke evidence meet release gates |

The capability milestones below group related outcomes for architectural context. They are not pull request boundaries.

### Capability milestone 1 - Identity and group foundation

**Delivery features:** F01-F05
**Prerequisites:** The CI/security baseline.

Deliver a working authenticated API slice:

- Resolve/provision an application user from OIDC `issuer + subject`.
- Create a group with name, base currency, and IANA timezone.
- Add the creator as an active member without assigning an administrator role.
- Get one authorized group and list the caller's active groups.
- Return standardized errors and resource ETags.
- Apply initial Liquibase migrations.

Representative endpoints:

```text
POST /api/v1/groups
GET  /api/v1/groups
GET  /api/v1/groups/{groupId}
```

Representative tables:

```text
app_user
currency
split_group
group_member
audit_event
```

Acceptance:

- An authenticated first-time JWT subject creates a group in one transaction and becomes an active member.
- A subsequent request reuses the same `app_user`; concurrent provisioning cannot create duplicates.
- The caller can list/get the group.
- A different authenticated user receives 404 and no group details.
- An unauthenticated request receives 401.
- Invalid name, currency, or timezone receives a field-level problem response.
- Group `ledger_version` and `sync_sequence` start at zero.
- There is no role/admin column or implicit creator administration.
- A fresh MySQL schema migrates and the integration suite passes.
- Changed-code coverage is at least 80%.

Deferred from this milestone: invitations, group updates, leave, expenses, balances, idempotency storage, sync feeds, and provider-specific OIDC claims beyond standard JWT validation.

### Capability milestone 2 - Invitations and membership visibility

**Delivery features:** F06-F09
**Depends on:** Capability milestone 1

Deliver:

- Create expiring invitations by intended recipient.
- Accept a single-use hashed invitation token.
- List active group members.
- Allow any active member to rename the group with `If-Match`.
- Enforce configured maximum membership.
- Emit audit records for invitations, joins, and renames.

Representative endpoints:

```text
POST  /api/v1/groups/{groupId}/invitations
POST  /api/v1/invitations/{token}/accept
GET   /api/v1/groups/{groupId}/members
PATCH /api/v1/groups/{groupId}
```

Representative table: `group_invitation`.

Tests cover token hashing, expiry, replay, recipient mismatch, concurrent acceptance, member limits, active-member checks, ETag conflict, and lack of admin privilege.

Deferred: member removal and leaving. Leaving depends on authoritative balances and pending settlements and is implemented after those exist.

### Capability milestone 3 - Money and split engine

**Delivery features:** F10-F12
**Depends on:** F01

Implement pure domain types:

```text
Money
CurrencyUnit
SplitMethod
SplitInput
SplitAllocation
SplitAllocator
MinorUnitConverter
```

Support equal, exact, percentage, and shares calculations in original currency. Use `BigDecimal` only at decimal boundaries and `long` minor units internally. Use largest-remainder allocation with stable user-ID tie breaking.

Exit criteria:

- No Spring or database dependencies in the money domain.
- All split methods satisfy exact-sum and deterministic-order invariants.
- Invalid precision, totals, percentages, shares, duplicates, zero/negative totals, and overflow are rejected with domain-specific errors.
- Deterministic property-style JUnit tests cover generated participant counts and amounts.

No public endpoint is required. This is a small prerequisite domain change, not broad scaffolding.

### Capability milestone 4 - FX snapshots and resolution

**Delivery features:** F13-F14
**Depends on:** Capability milestone 3
**Decision needed before completion:** Manual-rate product policy. External provider selection may remain deferred.

Deliver:

- `FxRateResolver` port and same-currency/manual-rate implementations.
- Immutable `fx_rate` persistence for provider snapshots.
- Historical rate selection rules, including seven-day maximum age.
- Original-to-base conversion with currency exponents and largest-remainder allocation.
- Rate-provenance response model.
- Rate lookup endpoint for available stored rates.

Representative endpoint:

```text
GET /api/v1/fx/rates?from=EUR&to=USD&date=2026-11-01
```

Tests cover same-currency rate 1, manual rates, unsupported pairs, stale/missing rates, weekend lookup, zero-base rounding, exponents, overflow, and allocation conservation.

Deferred: a live provider adapter and scheduled ingestion until provider licensing and coverage are approved.

### Capability milestone 5 - Equal same-currency expense and immutable ledger

**Delivery features:** F15-F17
**Depends on:** Capability milestones 2 and 3

Deliver the first financial write:

- Create/get/list an expense with one payer and equal splitting in group base currency.
- Persist immutable expense revision, payer allocation, participant allocations, balanced journal batch, entries, and balance projection.
- Add user-scoped idempotency records.
- Lock the group, validate active membership, append audit data, increment ledger version, and commit atomically.
- Return stored allocations and ledger version.

Representative endpoints:

```text
POST /api/v1/groups/{groupId}/expenses
GET  /api/v1/groups/{groupId}/expenses
GET  /api/v1/groups/{groupId}/expenses/{expenseId}
GET  /api/v1/operations/{operationId}
```

Representative tables:

```text
expense
expense_revision
expense_payer
expense_split
journal_batch
journal_entry
group_balance
idempotency_record
```

Acceptance:

- Payer and split allocations each equal the original/base total.
- Every journal batch sums to zero.
- Group balances sum to zero.
- Duplicate same-key requests create one financial posting and return the original result.
- Same key/different payload returns 409.
- A transaction failure leaves no partial expense, journal, balance, audit, or idempotency success record.
- Nonmembers and inactive members cannot submit or inspect group expenses.

Deferred: cross-currency, non-equal splits, edits, deletes, and sync changes.

### Capability milestone 6 - All split modes and multi-currency expenses

**Delivery features:** F18-F19
**Depends on:** Capability milestones 4 and 5
**Decision needed:** FX provider or an explicitly approved manual-only interim environment.

Deliver:

- Exact, percentage, and shares API inputs.
- Provider/manual FX snapshot selection.
- Original and base allocations with immutable provenance.
- Explicit `NEEDS_RATE` behavior where appropriate.
- Provider adapter and scheduled ingestion if a provider has been selected.

Acceptance:

- API rejects fields incompatible with the selected split method.
- Stored participant and payer allocations exactly conserve both original and base totals.
- Offline/provisional client calculations cannot override authoritative server allocations.
- New rates do not revalue existing revisions.

### Capability milestone 7 - Creator-only expense correction

**Delivery features:** F20-F21
**Depends on:** Capability milestone 6

Deliver creator-only replacement revisions and logical deletion:

```text
PUT    /api/v1/groups/{groupId}/expenses/{expenseId}
DELETE /api/v1/groups/{groupId}/expenses/{expenseId}
```

Use `If-Match`. Append an exact reversal batch before a replacement batch. Preserve original conversion for metadata-only edits and resolve a new rate for amount/currency/date changes.

Tests cover:

- Another member, payer, or group creator cannot edit an expense they did not create.
- Stale ETags fail without changes.
- Reversal exactly cancels the previous journal batch.
- Retry cannot reverse twice.
- Edits affecting a departed member fail with `FORMER_MEMBER_AFFECTED`.

### Capability milestone 8 - Balances, simplification, and leaving

**Delivery features:** F22-F24
**Depends on:** Capability milestone 7

Deliver:

```text
GET  /api/v1/groups/{groupId}/balances
GET  /api/v1/groups/{groupId}/simplified-payments
POST /api/v1/groups/{groupId}/leave
```

Implement deterministic greedy debtor/creditor matching with no claim of global optimality. Suggestions include ledger version and do not create postings.

Acceptance:

- The A-to-B/B-to-C example produces A-to-C.
- Suggestions conserve each member's net balance and use at most `n - 1` transfers.
- The documented nonoptimal counterexample proves product wording does not promise a minimum.
- Leaving is permitted only at zero balance and initially when there is no relevant pending financial workflow.
- Projection rebuild equals stored balances.

### Capability milestone 9 - External settlement workflow

**Delivery features:** F25-F27
**Depends on:** Capability milestone 8
**Decision needed:** Confirm the receiver-confirmation workflow before implementation.

Deliver pending creation, receiver confirmation/rejection, sender cancellation, and consensual reversal. Confirmation and approved reversal create immutable journal batches.

Representative endpoints follow the technical design's settlement routes.

Tests cover partial payments, stale ledger suggestions, concurrent confirmations, balance revalidation, double confirmation, state-transition authorization, pending settlements on leave, and reversal consent.

No bank or payment-provider integration is included.

### Capability milestone 10 - Personal categories and manual entries

**Delivery features:** F28-F30
**Depends on:** Capability milestone 4

Deliver:

- One personal ledger per user with immutable reporting currency after its first posting.
- Private income/expense categories.
- Manual entry create/get/list/update/delete using revisions and reversals.
- Reporting-currency FX snapshots.

Representative tables:

```text
personal_ledger
personal_category
personal_entry
personal_entry_revision
```

Tests prove user isolation, revision history, FX stability, category archival behavior, idempotent financial writes, and timezone/reporting-month assignment.

### Capability milestone 11 - Transactional outbox and shared-expense attribution

**Delivery features:** F31-F32
**Depends on:** Capability milestones 7 and 10

Deliver:

- Transactional `outbox_event`.
- Lease-based worker with retry and dead-letter status.
- One personal attribution per participant and immutable expense revision.
- Reversal/replacement attribution handling in source-version order.
- Completeness/version indicator on personal reads.

Representative table: `personal_attribution`.

Acceptance:

- A user's own shared allocation, not the full paid amount, affects personal spending.
- Group commits are independent of personal projection availability.
- Event replay does not duplicate attribution.
- Out-of-order revisions defer gaps rather than corrupting reports.
- Worker failures are observable and recoverable.

### Capability milestone 12 - Budgets and monthly reports

**Delivery features:** F33-F34
**Depends on:** Capability milestone 11

Deliver overall/category monthly budgets and reports for income, spending, cash flow, category totals, budget remaining, incomplete attribution status, and outstanding balances by group currency.

Tests cover month/timezone boundaries, historical corrections, missing reporting FX, independent overall/category limits, and exclusion of settlements from spending.

### Capability milestone 13A - Ordered incremental synchronization

**Delivery feature:** F35
**Depends on:** Stable group and personal mutations from capability milestone 12

Deliver:

- Per-group and per-personal-ledger sync sequences.
- Immutable `sync_change` entries and tombstones in the business transaction.
- Authorized cursor-based `/sync/changes`.
- Operation recovery and documented idempotency-retention behavior.
- Retention and expired-cursor response.

Tests include commit ordering, concurrent scope writes, membership revocation, cursor replay, partial local-apply simulation, and no skipped committed changes.

### Capability milestone 13B - Materialized bootstrap snapshots

**Delivery feature:** F36
**Depends on:** Capability milestone 13A

Deliver bounded-lifetime, authorized, materialized snapshots with a captured high-water sequence and cursor-paged data.

Acceptance:

- A client can install a consistent snapshot and continue from its high-water mark without gaps.
- Paging does not read a changing live entity set.
- Expired/revoked snapshot access fails.
- Unsent client drafts are unaffected by server resynchronization.

### Capability milestone 14 - Notifications

**Delivery feature:** F37
**Depends on:** Capability milestone 11
**Decision needed:** Provider and channels.

Use the outbox for invitations, settlement actions, and budget thresholds. Add delivery deduplication, bounded retries, safe templates, and provider references.

Provider failure must not roll back financial transactions. Do not claim exactly-once delivery when the provider does not support idempotency.

### Capability milestone 15A - Security and operational hardening

**Delivery features:** F38-F39
**Depends on:** All public MVP endpoints  
**Decisions needed:** Deployment platform, region, retention policy.

Deliver:

- Audience/issuer validation and claim-mapping review.
- Endpoint authorization matrix tests.
- Rate limits appropriate to the selected deployment.
- Strict request-size/list-size limits and log redaction tests.
- Protected Actuator exposure, readiness/liveness, structured logs, metrics, and traces.
- Production configuration validation and least-privilege database roles.
- Container image and cloud-neutral runtime documentation.
- Dependency, secret, and image scanning in CI.

### Capability milestone 15B - Reconciliation and release evidence

**Delivery features:** F40-F41
**Depends on:** Capability milestone 15A

Deliver:

- Scheduled journal/allocation/projection reconciliation.
- Ledger write blocking and incident signal on an integrity mismatch.
- Projection rebuild tooling.
- Load and concurrency evidence against the design targets.
- Backup/point-in-time recovery and restore-drill runbooks.
- OpenAPI compatibility check and production smoke tests.

## 9. F01 implementation in detail

F01 is deliberately schema-only. It establishes a testable database contract without mixing authentication, repositories, HTTP behavior, or transaction orchestration into the same review.

### 9.1 File boundary

```text
build.gradle.kts
.github\workflows\ci.yml
src\main\resources\
|-- application.properties
`-- db\changelog\
    |-- db.changelog-master.yaml
    `-- changes\001-foundation.sql
src\test\
|-- java\com\money\split\
|   |-- MoneySplitApplicationTests.java
|   |-- platform\LiquibaseMigrationIntegrationTest.java
|   `-- support\
|       |-- MySqlIntegrationTest.java
|       `-- MySqlTestDatabase.java
`-- resources\application-test.properties
docs\prs\foundation-schema-currency-catalog.md
```

### 9.2 F01 implementation checklist

- [x] Configure the Liquibase master changelog and environment-based datasource.
- [x] Add `app_user`, `currency`, `split_group`, `group_member`, and `audit_event`.
- [x] Add foreign keys, case-sensitive `(issuer, subject)` uniqueness, checks, and required indexes.
- [x] Seed exactly the approved 16 currencies with explicit fraction digits.
- [x] Keep group membership free of role, owner, and administrator fields.
- [x] Default group ledger, synchronization, and resource versions to zero.
- [x] Add pinned Testcontainers MySQL support and an isolated external-datasource fallback.
- [x] Execute the migration in CI instead of disabling Liquibase.
- [x] Add MySQL integration tests for tables, catalog metadata, identity uniqueness, privilege-column exclusion, version defaults, and currency referential integrity.
- [x] Add JavaDoc to changed Java methods and focused comments around non-obvious SQL and test infrastructure.
- [x] Update README and the dedicated PR change record.

### 9.3 F01 review boundaries

Reject F01 if it:

- Adds controllers, security, application services, domain services, or JDBC repositories.
- Adds an admin/owner permission or membership privilege column.
- Treats email as an identity key.
- Seeds currencies outside the approved product set.
- Uses H2 or another database to claim MySQL compatibility.
- Adds expense, settlement, personal-ledger, synchronization, or notification tables.
- Introduces Java production behavior merely to justify the schema.

## 10. Migration sequence and rules

Proposed changelog sequence:

| Migration | Introduced with |
|---|---|
| `001-foundation` | Users, currency, groups, membership, audit |
| `002-invitations` | Invitations and membership indexes |
| `003-fx` | Immutable FX snapshots |
| `004-expense-ledger` | Expenses, revisions, allocations, journal, balances, idempotency |
| `005-settlements` | Settlement state and events |
| `006-personal-ledger` | Personal ledger, categories, entries, revisions |
| `007-outbox-attribution` | Outbox and personal attribution |
| `008-budgets` | Budgets and reporting indexes |
| `009-sync` | Scope sequences, changes, snapshots/retention metadata |
| `010-notifications` | Delivery state and deduplication |

Rules:

1. Never edit an applied production changeset. Add a new forward migration.
2. Prefer expand/migrate/contract across separate releases for renames, type changes, and non-null additions.
3. Add nullable/new columns first, deploy dual-compatible code, backfill in bounded batches, then enforce constraints.
4. Create large indexes using the safest online capability supported by the deployed MySQL version.
5. Every migration must apply to an empty schema and to a snapshot of the previous schema.
6. Test referential integrity and important uniqueness/check constraints directly.
7. Do not perform unbounded data backfills in application startup.
8. Back up and rehearse recovery before a destructive contract migration.
9. Application deployment must remain compatible with the database during a rolling release.
10. Production Liquibase runs as a controlled release job, not independently in every application replica.

## 11. Test strategy

### 11.1 Unit tests

- Plain JUnit for domain and application services.
- Fakes for repositories, identity, clocks, FX, and notification ports.
- No network, filesystem, MySQL, Spring context, random clock, or live provider.
- Explicit happy path, boundary, validation, and error assertions.

High-value pure suites:

```text
SplitAllocatorTest
FxConverterTest
JournalBatchTest
DebtSimplifierTest
SettlementStateMachineTest
BudgetCalculatorTest
```

Use deterministic generated loops or seeded generators for monetary invariants:

- Allocation sum equals input total.
- Journal sum equals zero.
- Balance sum equals zero.
- Reversal plus source equals zero.
- FX allocation sum equals rounded base total.
- Simplification preserves each member's net position.

### 11.2 Application and HTTP tests

- Application-service tests use fakes to prove authorization and orchestration.
- MockMvc tests prove validation, status, headers, problem details, and JSON shape.
- Use Spring Security's JWT test support; do not call an identity provider.
- Keep OpenAPI examples synchronized with request/response tests.

### 11.3 MySQL integration tests

Use real MySQL for:

- Liquibase application.
- SQL mappings and constraints.
- Transaction rollback.
- `SELECT ... FOR UPDATE` behavior.
- Idempotency uniqueness.
- Deadlock/lock timeout handling.
- Concurrent settlement and user-provisioning cases.
- Scope sequence and snapshot consistency.

Concurrency tests must coordinate transactions with latches/barriers and assert the final financial state, not depend on arbitrary sleeps.

### 11.4 Coverage and gates

- At least 80% line and branch coverage on changed production code.
- Progress toward at least 80% repository-wide coverage.
- Coverage does not replace explicit invariant, authorization, or concurrency assertions.
- Unit tests run on every change.
- MySQL integration tests run before merge.
- Security/dependency scans become required before the first production deployment.

## 12. Definition of done

### 12.1 Every PR

- Behavior and acceptance criteria are explicit.
- A dedicated `docs\prs\<feature-slug>.md` change record follows `docs\prs\_template.md` and documents the exact commit message, feature, actual implementation, and technical test cases implemented.
- Failing tests were added before or with the implementation.
- Every new or changed Java method and constructor has useful JavaDoc describing its contract; test methods describe the behavior they verify.
- Important business logic, algorithms, transaction/lock ordering, and SQL have concise comments explaining intent or mechanics without narrating obvious syntax.
- Changed code has at least 80% line and branch coverage.
- Unit tests are hermetic and fast.
- MySQL-specific behavior has MySQL integration coverage.
- Authorization is enforced in the application layer and tested at HTTP boundaries.
- Input validation and safe error semantics are complete.
- Schema changes are Liquibase-managed and backward-compatible where required.
- Transactions, locks, idempotency, audit, and versions follow the design.
- OpenAPI and relevant README/design documentation are updated.
- Logs contain no tokens, notes, payment references, or other sensitive payloads.
- No unrelated refactoring, unused scaffolding, broad catch, silent fallback, or unowned TODO remains.

### 12.2 Backend MVP exit

- All confirmed group split methods and multi-currency rules work.
- Expense corrections preserve immutable history and creator-only authorization.
- Ledger, balance, simplification, and settlement invariants pass reconciliation.
- Personal entries, attributions, budgets, and monthly reports are complete and isolated.
- Offline financial creation can retry safely, recover outcomes, consume ordered changes, and bootstrap from snapshots.
- Security matrix, rate limits, privacy behavior, and auditability are reviewed.
- OpenAPI is stable for the client MVP.
- Load tests meet the agreed capacity targets or document approved limits.
- Monitoring, alerts, dead-letter replay, projection rebuild, backup, restore, and incident procedures are exercised.
- No unresolved critical/high dependency or application vulnerability is accepted without explicit risk approval.
- Required product/operations decisions in section 14 are closed.

## 13. Dependency and critical path

```text
F01 schema
 |-- F02 security -> F03 identity -> F04 group create -> F05 group reads
 |                                      |                 |-- F06-F08 invitations/members
 |                                      |                 `-- F09 group rename
 |                                      `--------------------> F15-F17 first expense
 |
 `-- F10 money -> F11-F12 allocation
        |-- F13-F14 FX ----------------------> F19 multi-currency
        `------------------------------------> F15-F18 expense foundation/splits

F19 -> F20-F21 corrections -> F22 balances
                                  |-- F23 simplification
                                  |-- F24 leave
                                  `-- F25-F27 settlements

F14 -> F28-F30 personal ledger
F21 + F30 -> F31 outbox -> F32 attribution -> F33-F34 budgets/reports
F27 + F34 -> F35 changes -> F36 snapshots
F31 -> F37 notifications
All public features -> F38-F39 hardening -> F40 reconciliation -> F41 release evidence
```

The financial critical path is F01-F05, F10-F12, and F15-F27. FX work can proceed after F10. Personal-ledger work can proceed after F14 while the group settlement path continues.

Only independent branches should be developed concurrently. Avoid parallel changes to the same migrations or shared money primitives without a single owner.

## 14. Open decisions and latest responsible point

| Decision | Latest responsible point | Interim approach |
|---|---|---|
| OIDC provider and exact claim mapping | Before a shared non-test environment after F02 | Standard issuer, audience, and subject boundary; test JWTs only in tests |
| Manual FX policy | Before F13 completes | Implement port and validation without enabling unsafe defaults |
| FX provider, licensing, and history | Before F19 provider mode is released | Same-currency/manual plus stored-rate boundary |
| Receiver confirmation for settlements | Before F25 starts | Keep state model from the technical design |
| Creator-unavailable expense disputes | Before pilot users can create lasting group history | Preserve creator-only rule; no hidden admin override |
| Notification channels/provider | Before F37 | Persist domain/outbox events without provider coupling |
| Deployment platform and region | Before F39 | Cloud-neutral container and managed MySQL assumptions |
| Legal retention/deletion requirements | Before production data lifecycle jobs | Preserve immutable financial integrity and configurable retention |
| Rate-limit implementation | Before multi-instance production | Define limits in configuration; select platform/Redis support later |
| Group member limit | Before F06 release | Configurable default of 50 from the technical design |

Escalate a decision only when its latest responsible point is approaching. Do not block independent domain work on vendor selection.

F01 resolved the supported currency set and the integration-test runtime: the catalog is versioned in Liquibase, local tests prefer the pinned Testcontainers image, and CI can supply an isolated MySQL datasource.

## 15. Development commands

Run commands from:

From:

```text
c:\Users\baverma\Downloads\Learning\Java Projects\Spring Boot\money-split
```

Run the complete current test suite:

```powershell
.\gradlew.bat clean check --no-daemon --stacktrace
```

Run coverage verification after F02 introduces changed production Java behavior and configures JaCoCo:

```powershell
.\gradlew.bat test jacocoTestReport jacocoTestCoverageVerification
```

Run locally with explicitly supplied configuration:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:3306/money_split"
$env:SPRING_DATASOURCE_USERNAME = "money_split_app"
$env:SPRING_DATASOURCE_PASSWORD = "<local-secret>"
.\gradlew.bat bootRun
```

Never commit secret values. F02 will document the additional OIDC variables when authentication exists.
