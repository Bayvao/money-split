# Money Split

A backend API for personal money tracking and shared expense splitting, built with Java 25, Spring Boot 4.1, Spring Data JDBC, Liquibase, and MySQL 8.4.

[![CI](https://github.com/Bayvao/money-split/actions/workflows/ci.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/ci.yml)
[![CodeQL](https://github.com/Bayvao/money-split/actions/workflows/codeql.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/codeql.yml)
[![Security Scan](https://github.com/Bayvao/money-split/actions/workflows/security-scan.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/security-scan.yml)

## Implemented capabilities

The backend currently provides a versioned MySQL foundation and a stateless
Firebase ID-token request-security boundary:

- Liquibase creates application users, currencies, groups, memberships, and audit events.
- Firebase identities are unique by case-sensitive `issuer + UID`; email is not an identity key.
- Group membership contains lifecycle state only, with no owner, administrator, or role.
- Group ledger, synchronization, and optimistic-lock counters default to zero.
- Foreign keys restrict groups to the explicitly supported currency catalog.
- Firebase ID-token signature, project issuer/audience, time, UID, and provider validation protects application routes.
- The current actor is mapped from the issuer-scoped Firebase UID and optional profile claims.
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
| `FIREBASE_PROJECT_ID` | Yes | Exact Firebase project ID used to derive the trusted issuer and audience |
| `FIREBASE_ALLOWED_SIGN_IN_PROVIDERS` | No | Comma-separated provider IDs; defaults to `google.com,apple.com` |

Liquibase applies `src/main/resources/db/changelog/db.changelog-master.yaml` automatically at startup. The database identity should have schema-migration privileges during this foundation phase.

The project ID must use Firebase's lowercase project-ID format. The provider
allowlist is normalized to lowercase and matched exactly against
`firebase.sign_in_provider`.

## Request security

Anonymous access is limited to:

- `/actuator/health` and `/actuator/health/**`
- `/v3/api-docs/**`
- `/swagger-ui.html` and `/swagger-ui/**`

Every other route, including non-health actuator endpoints, requires a Firebase
ID token as a bearer credential. Clients sign in with Google or Apple through a
Firebase SDK and send the ID token returned by `getIdToken`; provider OAuth
access tokens are not accepted. The API is stateless, creates no HTTP sessions,
and disables CSRF because bearer tokens are explicit credentials.

Accepted tokens must use Google's Firebase RS256 signing keys; contain the
derived issuer and exact project audience; satisfy `exp`, optional `nbf`, `iat`,
and `auth_time`; identify an allowlisted sign-in provider; and contain an
unpadded Firebase UID of at most 128 characters. The durable external identity
is `(issuer, Firebase UID)`. Firebase owns account/provider linking. Email,
including an Apple private relay address, remains optional mutable profile data.
Display name falls back from `name` to `preferred_username` to the UID.

The backend does not need Google/Apple client secrets, provider access tokens, a
Firebase Admin SDK, or a service-account private key for local JWT validation.
Nimbus obtains and caches Google's rotating public keys. This boundary does not
perform a per-request revocation/disabled-user lookup; immediate revocation
enforcement would require a separate service-account-backed policy.

Security failures return a stable problem document and an `X-Request-ID` header.
Incoming request IDs are ignored and replaced with a server-generated UUID.

## Run locally

Prerequisites are JDK 25 and an accessible MySQL 8.4 database. In PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/money_split"
$env:SPRING_DATASOURCE_USERNAME = "money_split"
$env:SPRING_DATASOURCE_PASSWORD = "<password>"
$env:FIREBASE_PROJECT_ID = "<firebase-project-id>"
$env:FIREBASE_ALLOWED_SIGN_IN_PROVIDERS = "google.com,apple.com"
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

See the
[F02 Firebase request-security local testing guide](docs/testing/f02-jwt-request-security-local-testing.md)
for targeted test commands, Firebase client token acquisition, manual bearer
smoke tests, expected responses, and troubleshooting.

## Technical design

See [the technical design document](docs/design.md) for the proposed MVP architecture, confirmed requirements, permission model, financial algorithms, multi-currency handling, database model, APIs, offline synchronization, and delivery roadmap.

See [the backend implementation plan](docs/backend-implementation-plan.md) for the backend-only sequence of PR-sized vertical slices, first implementation checklist, migrations, testing strategy, and delivery gates.

See [the pull request documentation standard](docs/prs/README.md) for the required per-PR change record and reusable template.

See [CI and security automation](docs/security-ci.md) for workflow coverage, enforcement policy, tool choices, and required repository settings.
