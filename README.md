# Money Split

A backend API for personal money tracking and shared expense splitting, built with Java 25, Spring Boot 4.1, Spring Data JDBC, Liquibase, and MySQL 8.4.

[![CI](https://github.com/Bayvao/money-split/actions/workflows/ci.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/ci.yml)
[![CodeQL](https://github.com/Bayvao/money-split/actions/workflows/codeql.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/codeql.yml)
[![Security Scan](https://github.com/Bayvao/money-split/actions/workflows/security-scan.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/security-scan.yml)

## Implemented capability

The first backend feature establishes the versioned MySQL foundation:

- Liquibase creates application users, currencies, groups, memberships, and audit events.
- OIDC identities are unique by case-sensitive `issuer + subject`; email is not an identity key.
- Group membership contains lifecycle state only, with no owner, administrator, or role.
- Group ledger, synchronization, and optimistic-lock counters default to zero.
- Foreign keys restrict groups to the explicitly supported currency catalog.

No group or identity API is included in this schema-only feature.

## Currency catalog

Supported base currencies are `AED`, `AUD`, `BTN`, `CAD`, `EUR`, `GBP`, `IDR`, `INR`, `JPY`, `LKR`, `MYR`, `NPR`, `SGD`, `THB`, `USD`, and `VND`. JPY and VND use zero fraction digits; the remaining currencies use two.

## Configuration

Configuration is supplied through environment variables:

| Variable | Required | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | Yes | MySQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Yes | MySQL application user |
| `SPRING_DATASOURCE_PASSWORD` | Yes | MySQL application password |

Liquibase applies `src/main/resources/db/changelog/db.changelog-master.yaml` automatically at startup. The database identity should have schema-migration privileges during this foundation phase.

## Run locally

Prerequisites are JDK 25 and an accessible MySQL 8.4 database. In PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/money_split"
$env:SPRING_DATASOURCE_USERNAME = "money_split"
$env:SPRING_DATASOURCE_PASSWORD = "<password>"
.\gradlew.bat bootRun
```

## Test

Fast unit tests do not require external infrastructure:

```powershell
.\gradlew.bat test --no-daemon
```

MySQL integration tests start the pinned Testcontainers image automatically when `SPRING_DATASOURCE_URL` is absent. Alternatively, set the three datasource variables to use an isolated existing MySQL test database:

```powershell
.\gradlew.bat integrationTest --no-daemon
```

The complete merge gate runs unit tests and the real-MySQL integration suite:

```powershell
.\gradlew.bat clean check --no-daemon --stacktrace
```

## Technical design

See [the technical design document](docs/design.md) for the proposed MVP architecture, confirmed requirements, permission model, financial algorithms, multi-currency handling, database model, APIs, offline synchronization, and delivery roadmap.

See [the backend implementation plan](docs/backend-implementation-plan.md) for the backend-only sequence of PR-sized vertical slices, first implementation checklist, migrations, testing strategy, and delivery gates.

See [the pull request documentation standard](docs/prs/README.md) for the required per-PR change record and reusable template.

See [CI and security automation](docs/security-ci.md) for workflow coverage, enforcement policy, tool choices, and required repository settings.
