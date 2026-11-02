# Foundation Schema and Currency Catalog

| Field | Value |
|---|---|
| Status | Pull request open |
| Branch | `feat/foundation-schema` |
| Pull request | [#3](https://github.com/Bayvao/money-split/pull/3) |
| Date | 2026-11-02 |
| Related plan item | F01 - Foundation schema and currency catalog |

## Commit Message

The pull request retains these commits in order:

```text
feat(database): add foundation schema and currency catalog

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

```text
docs(pr): link foundation schema pull request

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

## Feature

Establish the initial Liquibase-managed MySQL contract and the exact currency catalog required by later identity and group features.

### Scope

- Included: datasource and Liquibase configuration, user/currency/group/membership/audit tables, constraints and indexes, 16 approved currencies, a pinned MySQL integration-test runtime, CI migration execution, and schema documentation.
- Excluded: authentication, user provisioning services, repositories, group APIs, request/response contracts, authorization, transaction orchestration, and every financial workflow.

### Acceptance criteria

- [x] A fresh MySQL database creates all five foundation tables through Liquibase.
- [x] The catalog contains exactly the approved 16 active currencies and their fraction digits.
- [x] OIDC identity uniqueness is case-sensitive on `(issuer, subject)`.
- [x] Membership has no role, owner, or administrator field.
- [x] New group ledger, synchronization, and resource versions default to zero.
- [x] A group cannot reference a currency outside the catalog.
- [x] The complete Gradle check passes against MySQL 8.4.

## What Was Implemented

### Application changes

- Added only database bootstrap configuration; no domain or API behavior is introduced.
- Added JavaDoc to the changed Java test methods and helpers.
- Added focused comments explaining non-obvious schema, cleanup, and datasource decisions.

### API changes

| Method | Path | Change |
|---|---|---|
| N/A | N/A | No API change |

### Database changes

| Migration | Change | Compatibility notes |
|---|---|---|
| `001-foundation.sql` | Creates `app_user`, `currency`, `split_group`, `group_member`, and `audit_event`; seeds the approved catalog; adds keys, checks, and indexes | Additive initial schema; rollback drops tables in dependency order |

UUIDs use `BINARY(16)` for compact keys. OIDC issuer and subject use binary collation so exact identities remain case-sensitive. Membership intentionally contains no privilege field.

### Configuration and dependencies

- Configured the production datasource exclusively through environment variables.
- Configured the Liquibase master changelog.
- Added Testcontainers JUnit and MySQL modules with the MySQL image pinned by digest.
- Added a tagged `integrationTest` task and made it part of `check`.
- Enabled Liquibase in CI against the existing isolated MySQL service.

### Security and operational behavior

- No authentication or authorization behavior is included.
- Tests reuse a CI-provided isolated datasource when configured; otherwise they start the pinned Testcontainers image.
- Test cleanup deletes child rows before parent rows so foreign-key behavior remains enforced.
- Currency seed rows are retained between tests because they are versioned schema data.

## Technical Test Cases Implemented

### Unit tests

| Test case | Behavior verified |
|---|---|
| N/A | This schema-only feature has no production Java behavior requiring a unit test |

### Integration tests

| Test case | Behavior verified |
|---|---|
| Fresh schema migration | All five foundation tables exist after Liquibase runs |
| Approved currency catalog | Exact codes, names, active flags, and fraction digits are seeded |
| Case-sensitive OIDC identity | Case variants coexist while an exact `(issuer, subject)` duplicate fails |
| Membership privilege exclusion | No role, owner, or administrator column exists |
| Group version defaults | Ledger, sync, and optimistic-lock counters initialize to zero |
| Unsupported group currency | The currency foreign key rejects a code outside the catalog |
| Application context | Spring starts against the migrated MySQL schema |

### API, security, and concurrency tests

| Test case | Behavior verified |
|---|---|
| N/A | No API, security, or concurrent behavior is introduced |

### Validation evidence

| Command or check | Result |
|---|---|
| `.\gradlew.bat integrationTest --no-daemon --stacktrace --warning-mode all` | Passed against MySQL 8.4 |
| `.\gradlew.bat clean check --no-daemon --stacktrace --warning-mode all` | Passed |

## Compatibility and Migration Notes

- This is the initial application schema; no existing application data requires conversion.
- Production rollouts should execute Liquibase as a controlled release step before application replicas start using the schema.
- The formatted-SQL rollback removes all five tables and is appropriate only while no durable application data must be preserved.

## Deferred Work

- F02 adds JWT validation and the current-actor boundary.
- F03 adds concurrent-safe application-user provisioning.
- F04 adds authenticated group creation with creator membership and audit persistence.
- F05 adds membership-scoped group list and get APIs.
