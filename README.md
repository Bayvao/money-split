# Money Split

A backend API for personal money tracking and shared expense splitting, built with Java 25, Spring Boot 4.1, Spring Data JDBC, Liquibase, and MySQL 8.4.

[![CI](https://github.com/Bayvao/money-split/actions/workflows/ci.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/ci.yml)
[![CodeQL](https://github.com/Bayvao/money-split/actions/workflows/codeql.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/codeql.yml)
[![Security Scan](https://github.com/Bayvao/money-split/actions/workflows/security-scan.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/security-scan.yml)

## Implemented capabilities

The backend currently provides a versioned MySQL foundation and a stateless JWT
request-security boundary:

- Liquibase creates application users, currencies, groups, memberships, and audit events.
- OIDC identities are unique by case-sensitive `issuer + subject`; email is not an identity key.
- Group membership contains lifecycle state only, with no owner, administrator, or role.
- Group ledger, synchronization, and optimistic-lock counters default to zero.
- Foreign keys restrict groups to the explicitly supported currency catalog.
- JWT signature, issuer, timestamp, audience, and subject validation protects application routes.
- The current actor is mapped from the issuer-scoped subject and optional profile claims.
- Health and API documentation routes remain public; all other routes require authentication.
- Authentication and authorization failures use correlated `application/problem+json` responses.
- Server-generated request IDs prevent clients from spoofing request correlation.

Application-user provisioning, group APIs, and financial workflows are not included yet.

## Currency catalog

Supported base currencies are `AED`, `AUD`, `BTN`, `CAD`, `EUR`, `GBP`, `IDR`, `INR`, `JPY`, `LKR`, `MYR`, `NPR`, `SGD`, `THB`, `USD`, and `VND`. JPY and VND use zero fraction digits; the remaining currencies use two.

## Configuration

Configuration is supplied through environment variables:

| Variable | Required | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | Yes | MySQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | Yes | MySQL application user |
| `SPRING_DATASOURCE_PASSWORD` | Yes | MySQL application password |
| `OIDC_ISSUER_URI` | Yes | Exact trusted OIDC issuer URL used for discovery, signature verification, and issuer validation |
| `OIDC_AUDIENCE` | Yes | API audience that every accepted access token must contain |

Liquibase applies `src/main/resources/db/changelog/db.changelog-master.yaml` automatically at startup. The database identity should have schema-migration privileges during this foundation phase.

The issuer must be an absolute HTTP or HTTPS URL with a host and no query or
fragment. HTTP is supported for local identity providers; production deployments
should use HTTPS. The audience is matched exactly after surrounding configuration
whitespace is removed.

## Request security

Anonymous access is limited to:

- `/actuator/health` and `/actuator/health/**`
- `/v3/api-docs/**`
- `/swagger-ui.html` and `/swagger-ui/**`

Every other route, including non-health actuator endpoints, requires a bearer
access token. The API is stateless and does not create HTTP sessions. CSRF is
disabled because bearer tokens are explicit request credentials rather than
ambient cookie credentials.

Accepted JWTs must have a valid signature, trusted issuer, valid timestamps, the
configured audience, and a non-blank subject. The current actor uses
`(issuer, subject)` as the durable external identity. Email is optional mutable
profile data, and display name falls back from `name` to `preferred_username` to
the subject.

Security failures return a stable problem document and an `X-Request-ID` header.
Incoming request IDs are ignored and replaced with a server-generated UUID.

## Run locally

Prerequisites are JDK 25 and an accessible MySQL 8.4 database. In PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/money_split"
$env:SPRING_DATASOURCE_USERNAME = "money_split"
$env:SPRING_DATASOURCE_PASSWORD = "<password>"
$env:OIDC_ISSUER_URI = "https://id.example.com/"
$env:OIDC_AUDIENCE = "money-split-api"
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

The complete merge gate runs unit tests, the real-MySQL integration suite, and
enforces at least 80% line and branch coverage:

```powershell
.\gradlew.bat clean check --no-daemon --stacktrace
```

The HTML coverage report is generated at
`build/reports/jacoco/test/html/index.html`.

## Technical design

See [the technical design document](docs/design.md) for the proposed MVP architecture, confirmed requirements, permission model, financial algorithms, multi-currency handling, database model, APIs, offline synchronization, and delivery roadmap.

See [the backend implementation plan](docs/backend-implementation-plan.md) for the backend-only sequence of PR-sized vertical slices, first implementation checklist, migrations, testing strategy, and delivery gates.

See [the pull request documentation standard](docs/prs/README.md) for the required per-PR change record and reusable template.

See [CI and security automation](docs/security-ci.md) for workflow coverage, enforcement policy, tool choices, and required repository settings.
