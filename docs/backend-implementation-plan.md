# Money Split Backend Implementation Plan

**Status:** Proposed execution plan  
**Scope:** Backend only  
**Source of truth:** [Technical design](design.md)  
**Current baseline:** Generated Spring Boot application with no domain implementation

## 1. Objective

Deliver the backend as a modular Spring Boot monolith backed by MySQL. Work in small, production-shaped vertical slices that expose usable API behavior, persist through Liquibase-managed schemas, enforce authorization, and include automated tests.

This plan does not include web or mobile implementation. It does include the REST and synchronization contracts required by those clients.

## 2. Delivery principles

1. Build one complete behavior at a time: API, application service, domain rules, persistence, migration, security, tests, and documentation.
2. Start with a group slice that proves the architecture. Do not create empty packages for every future module.
3. Use TDD for domain and application behavior. Add the failing test before implementation.
4. Keep financial calculations pure and independent of Spring, SQL, clocks, networks, and providers.
5. Use MySQL for persistence integration tests. Do not use H2 as a substitute for MySQL constraints or locking.
6. Apply Liquibase migrations before adding repository code that depends on them.
7. Keep external identity, FX, and notification vendors behind narrow ports. Select vendors at the latest responsible milestone.
8. Keep controllers thin. They validate transport input and call application services; they do not calculate balances or access JDBC directly.
9. Keep the immutable journal and revisions authoritative. Treat balances and reports as rebuildable projections.
10. Do not introduce message brokers, Redis, Kubernetes, JPA, event sourcing frameworks, or service decomposition before a measured need.

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

### 5.1 Required for the first implementation PR

Add dependencies compatible with the Spring Boot dependency-management version:

| Change | Purpose |
|---|---|
| OAuth2 resource-server starter | Validate production OIDC JWT access tokens |
| Validation starter | Jakarta Bean Validation for API inputs and typed configuration |
| Spring Security test support | Supply authenticated JWT principals in MockMvc tests without a live identity provider |
| Testcontainers JUnit and MySQL modules | Run persistence, Liquibase, authorization, and locking tests against MySQL when a container runtime is available |
| Gradle `jacoco` plugin | Enforce and report coverage without adding an external coverage product |

Use the Spring Boot BOM for managed versions where available. Do not hardcode library versions until Gradle dependency resolution confirms whether Boot manages them.

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
| Default | Production-shaped behavior; requires datasource and OIDC configuration |
| `local` | Developer MySQL and a real/local OIDC issuer; never bypass authorization with a trusted header |
| `test` | Test datasource plus a test-only JWT decoder or Spring Security test principal support |

Tests must not contact a real OIDC issuer. Test security configuration belongs under `src\test`, not production sources.

### 6.2 First-iteration environment variables

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
OIDC_ISSUER_URI
OIDC_AUDIENCE
APP_GROUP_MAX_MEMBERS
```

Later milestones add variables such as FX provider URL/key, rate-age limit, idempotency retention, sync retention, outbox lease duration, and notification provider credentials.

Validate required production configuration at startup. Do not provide default credentials or a production authentication bypass. Redact secret values from startup errors and logs.

### 6.3 Local and integration-test MySQL

Preferred integration-test path:

- Testcontainers starts a pinned MySQL image.
- Spring's dynamic properties or service-connection support supplies the datasource.
- Liquibase applies the same changelog used in production.

This path requires a Docker-compatible container runtime; do not assume every developer machine has one. Before merging PR 1, the team must choose one supported fallback:

1. A dedicated local MySQL database configured through environment variables.
2. A CI-provided MySQL service with an isolated database per test run.

The fallback must still run MySQL and must never point tests at a shared development or production schema.

## 7. Cross-cutting rules from the first PR

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

Group creation does not need the financial idempotency subsystem in PR 1. Add it before the first financial write.

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
- Any item larger than L must be split before implementation.

### PR 1 - Authenticated group creation and retrieval

**Size:** L  
**Prerequisites:** Choose the supported MySQL integration-test path and provide CI access to it.

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

Deferred from PR 1: invitations, group updates, leave, expenses, balances, idempotency storage, sync feeds, and external OIDC-provider-specific claims beyond standard JWT validation.

### PR 2 - Invitations and membership visibility

**Size:** M  
**Depends on:** PR 1

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

### PR 3 - Money and split engine

**Size:** M  
**Depends on:** PR 1

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

### PR 4 - FX snapshots and resolution

**Size:** M  
**Depends on:** PR 3  
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

### PR 5 - Equal same-currency expense and immutable ledger

**Size:** L  
**Depends on:** PR 2 and PR 3

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

### PR 6 - All split modes and multi-currency expenses

**Size:** M  
**Depends on:** PR 4 and PR 5  
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

### PR 7 - Creator-only expense correction

**Size:** M  
**Depends on:** PR 6

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

### PR 8 - Balances, simplification, and leaving

**Size:** M  
**Depends on:** PR 7

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

### PR 9 - External settlement workflow

**Size:** L  
**Depends on:** PR 8  
**Decision needed:** Confirm the receiver-confirmation workflow before implementation.

Deliver pending creation, receiver confirmation/rejection, sender cancellation, and consensual reversal. Confirmation and approved reversal create immutable journal batches.

Representative endpoints follow the technical design's settlement routes.

Tests cover partial payments, stale ledger suggestions, concurrent confirmations, balance revalidation, double confirmation, state-transition authorization, pending settlements on leave, and reversal consent.

No bank or payment-provider integration is included.

### PR 10 - Personal categories and manual entries

**Size:** M  
**Depends on:** PR 4

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

### PR 11 - Transactional outbox and shared-expense attribution

**Size:** L  
**Depends on:** PR 7 and PR 10

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

### PR 12 - Budgets and monthly reports

**Size:** M  
**Depends on:** PR 11

Deliver overall/category monthly budgets and reports for income, spending, cash flow, category totals, budget remaining, incomplete attribution status, and outstanding balances by group currency.

Tests cover month/timezone boundaries, historical corrections, missing reporting FX, independent overall/category limits, and exclusion of settlements from spending.

### PR 13A - Ordered incremental synchronization

**Size:** L  
**Depends on:** Stable group and personal mutations from PR 12

Deliver:

- Per-group and per-personal-ledger sync sequences.
- Immutable `sync_change` entries and tombstones in the business transaction.
- Authorized cursor-based `/sync/changes`.
- Operation recovery and documented idempotency-retention behavior.
- Retention and expired-cursor response.

Tests include commit ordering, concurrent scope writes, membership revocation, cursor replay, partial local-apply simulation, and no skipped committed changes.

### PR 13B - Materialized bootstrap snapshots

**Size:** M  
**Depends on:** PR 13A

Deliver bounded-lifetime, authorized, materialized snapshots with a captured high-water sequence and cursor-paged data.

Acceptance:

- A client can install a consistent snapshot and continue from its high-water mark without gaps.
- Paging does not read a changing live entity set.
- Expired/revoked snapshot access fails.
- Unsent client drafts are unaffected by server resynchronization.

### PR 14 - Notifications

**Size:** M  
**Depends on:** PR 11  
**Decision needed:** Provider and channels.

Use the outbox for invitations, settlement actions, and budget thresholds. Add delivery deduplication, bounded retries, safe templates, and provider references.

Provider failure must not roll back financial transactions. Do not claim exactly-once delivery when the provider does not support idempotency.

### PR 15A - Security and operational hardening

**Size:** L  
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

### PR 15B - Reconciliation and release evidence

**Size:** L  
**Depends on:** PR 15A

Deliver:

- Scheduled journal/allocation/projection reconciliation.
- Ledger write blocking and incident signal on an integrity mismatch.
- Projection rebuild tooling.
- Load and concurrency evidence against the design targets.
- Backup/point-in-time recovery and restore-drill runbooks.
- OpenAPI compatibility check and production smoke tests.

## 9. First implementation PR in detail

PR 1 is the next coding step. It must prove a real behavior, not only configure frameworks.

### 9.1 Suggested file tree

Names may be adjusted during TDD, but responsibilities should remain narrow.

```text
build.gradle.kts
src\main\java\com\money\split\
|-- MoneySplitApplication.java
|-- platform\
|   |-- api\
|   |   |-- ApiProblem.java
|   |   |-- ApiExceptionHandler.java
|   |   `-- ErrorCode.java
|   |-- config\
|   |   `-- GroupProperties.java
|   `-- security\
|       |-- CurrentActor.java
|       |-- CurrentActorProvider.java
|       |-- JwtCurrentActorProvider.java
|       `-- SecurityConfiguration.java
|-- identity\
|   |-- application\
|   |   `-- CurrentUserService.java
|   |-- domain\
|   |   `-- AppUser.java
|   `-- persistence\
|       |-- UserRepository.java
|       `-- JdbcUserRepository.java
`-- groups\
    |-- api\
    |   |-- GroupController.java
    |   |-- CreateGroupRequest.java
    |   |-- GroupResponse.java
    |   `-- GroupListResponse.java
    |-- application\
    |   |-- GroupService.java
    |   `-- GroupRepository.java
    |-- domain\
    |   |-- Group.java
    |   |-- GroupId.java
    |   `-- GroupStatus.java
    `-- persistence\
        `-- JdbcGroupRepository.java

src\main\resources\
|-- application.properties
|-- application-local.properties
`-- db\changelog\
    |-- db.changelog-master.yaml
    `-- changes\001-foundation.yaml

src\test\java\com\money\split\
|-- support\
|   |-- MySqlIntegrationTest.java
|   `-- TestSecurityConfiguration.java
|-- groups\
|   |-- GroupServiceTest.java
|   |-- GroupApiIntegrationTest.java
|   `-- GroupAuthorizationIntegrationTest.java
`-- platform\
    |-- LiquibaseMigrationIntegrationTest.java
    `-- ApiProblemIntegrationTest.java
```

`TestSecurityConfiguration` must exist only in test sources. Prefer Spring Security MockMvc JWT support over a test HTTP header.

### 9.2 PR 1 implementation checklist

- [ ] Add required security, validation, MySQL-test, and coverage dependencies.
- [ ] Configure the Liquibase master changelog.
- [ ] Add `app_user`, `currency`, `split_group`, `group_member`, and `audit_event`.
- [ ] Add foreign keys, unique `(issuer, subject)`, membership uniqueness, checks, and required indexes.
- [ ] Seed only the supported currency metadata needed by the application; make the seed explicit and versioned.
- [ ] Validate issuer, audience, subject, display-name fallback, UUIDs, group name, currency, and IANA timezone.
- [ ] Implement concurrent-safe user provisioning without merging identities by email.
- [ ] Implement group creation and creator membership in one transaction.
- [ ] Implement membership-scoped get/list queries.
- [ ] Add request IDs, problem responses, ETags, and OpenAPI descriptions.
- [ ] Add unit tests before application code.
- [ ] Add MySQL integration tests for migrations, API behavior, constraints, transactions, and authorization.
- [ ] Configure JaCoCo with at least 80% coverage for changed production code.
- [ ] Update README configuration and test commands as part of the implementation PR.

### 9.3 PR 1 review boundaries

Reject the PR if it:

- Adds an admin/owner permission.
- Trusts email as the immutable OIDC identity.
- Allows an authentication-bypass header in production code.
- Uses H2 to claim MySQL integration coverage.
- Persists JWT access tokens.
- Exposes groups before checking active membership.
- Creates every future module as empty scaffolding.
- Adds financial tables or generic event frameworks before their first behavior.

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
- Failing tests were added before or with the implementation.
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
PR 1 groups/auth
 |-- PR 2 invitations/membership
 |    `-- PR 5 expense ledger
 |
 `-- PR 3 money engine
      |-- PR 4 FX
      |    |-- PR 6 all splits/multi-currency
      |    `-- PR 10 personal manual ledger
      |
      `-- PR 5 expense ledger

PR 5 -> PR 6 -> PR 7 corrections -> PR 8 balances/simplification -> PR 9 settlements
                         |
                         `-> PR 11 outbox/attribution <- PR 10
                                  `-> PR 12 budgets/reports

PR 12 -> PR 13A changes -> PR 13B snapshots
PR 11 -> PR 14 notifications
All public slices -> PR 15A hardening -> PR 15B release evidence
```

The financial critical path is PR 1, PR 3, PR 5, PR 6, PR 7, PR 8, and PR 9. FX work can proceed after the money engine. Personal manual-ledger work can proceed after FX while the group settlement path continues.

Only independent branches should be developed concurrently. Avoid parallel changes to the same migrations or shared money primitives without a single owner.

## 14. Open decisions and latest responsible point

| Decision | Latest responsible point | Interim approach |
|---|---|---|
| OIDC provider and exact claim mapping | Before a shared non-test environment after PR 1 | Standard issuer, audience, and subject boundary; test JWTs only in tests |
| Integration-test MySQL runtime | Before PR 1 implementation merges | Prefer Testcontainers; approve isolated external MySQL fallback |
| Supported currency set | During PR 1 | Versioned currency metadata; avoid claiming all ISO currencies are supported |
| Manual FX policy | Before PR 4 completes | Implement port and validation without enabling unsafe defaults |
| FX provider, licensing, and history | Before PR 6 provider mode is released | Same-currency/manual plus stored-rate boundary |
| Receiver confirmation for settlements | Before PR 9 starts | Keep state model from the technical design |
| Creator-unavailable expense disputes | Before pilot users can create lasting group history | Preserve creator-only rule; no hidden admin override |
| Notification channels/provider | Before PR 14 | Persist domain/outbox events without provider coupling |
| Deployment platform and region | Before PR 15A | Cloud-neutral container and managed MySQL assumptions |
| Legal retention/deletion requirements | Before production data lifecycle jobs | Preserve immutable financial integrity and configurable retention |
| Rate-limit implementation | Before multi-instance production | Define limits in configuration; select platform/Redis support later |
| Group member limit | Before PR 2 release | Configurable default of 50 from the technical design |

Escalate a decision only when its latest responsible point is approaching. Do not block independent domain work on vendor selection.

## 15. Future implementation commands

These are intended commands for future code PRs. They are not a claim that the currently empty application is fully configured or that the commands pass today.

From:

```text
c:\Users\baverma\Downloads\Learning\Java Projects\Spring Boot\money-split
```

Run the future test suite:

```powershell
.\gradlew.bat clean test
```

Run a future coverage verification task after JaCoCo is configured:

```powershell
.\gradlew.bat test jacocoTestReport jacocoTestCoverageVerification
```

Run locally with explicitly supplied configuration:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:3306/money_split"
$env:SPRING_DATASOURCE_USERNAME = "money_split_app"
$env:SPRING_DATASOURCE_PASSWORD = "<local-secret>"
$env:OIDC_ISSUER_URI = "https://<local-or-development-issuer>"
$env:OIDC_AUDIENCE = "money-split-api"
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Never commit the values used for secrets. A future local setup guide may provide a dedicated database creation script or container definition after the team chooses its supported local MySQL path.
