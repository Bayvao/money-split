# JWT Request-Security Boundary

| Field | Value |
|---|---|
| Status | Draft |
| Branch | `feat/jwt-request-security` |
| Pull request | Pending |
| Date | 2026-11-02 |
| Related plan item | F02 - JWT request-security boundary |

## Commit Message

The pull request initially retains this commit:

```text
feat(security): add JWT request security boundary

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

## Feature

Establish the backend request-security boundary so only explicitly public
operational and API-documentation routes are anonymous, while all other requests
require a validated OIDC bearer token.

### Scope

- Included: OIDC configuration validation, JWT signature/issuer/timestamp/audience/subject validation, stateless route policy, current-actor claim mapping, server-owned request IDs, safe `401` and `403` problem responses, test-only token decoding, and aggregate JaCoCo enforcement.
- Excluded: application-user persistence or provisioning, authorization based on group membership, group APIs, refresh tokens, identity-provider administration, and F03 or later behavior.

### Acceptance criteria

- [x] Production JWTs are verified using issuer discovery and signature validation.
- [x] Accepted tokens have the exact trusted issuer, valid timestamps, configured audience, and a non-blank subject.
- [x] The current actor uses `(issuer, subject)` as durable identity and treats profile claims as mutable data.
- [x] Health and API-documentation routes are public, while every other route requires authentication.
- [x] Missing and invalid bearer tokens return a stable `401` problem without decoder details.
- [x] Authorization failures return a stable `403` problem without internal policy details.
- [x] Every request receives a server-generated correlation ID that replaces any inbound value.
- [x] The API remains stateless and authenticated unsafe methods do not require a CSRF token.
- [x] Every new or changed Java method and constructor has JavaDoc, and important security decisions have intent-focused comments.
- [x] Aggregate changed-code line and branch coverage exceeds 80%.

## What Was Implemented

### Application changes

- Added validated OIDC issuer and audience configuration.
- Added a production `NimbusJwtDecoder` that composes Spring Security's issuer and timestamp validators with exact audience and required-subject validators.
- Added a framework-independent current-actor contract and a provider that maps authenticated JWT claims into it.
- Added normalization and length validation for issuer, subject, display name, and optional email claims.
- Added server-owned request-ID generation before the security filter chain.
- Added stable problem documents for authentication and authorization failures.

### Code documentation

- Every new production method, constructor, record constructor, test method, and test helper has JavaDoc describing its responsibility and contract.
- Intent comments explain the stateless CSRF decision, error-dispatch handling, validator composition, display-name fallback, query-string exclusion from problem instances, inbound request-ID replacement, and foreign-key-safe integration-test cleanup.
- No SQL was added or changed by F02.

### API changes

| Method | Path | Change |
|---|---|---|
| Any | `/actuator/health`, `/actuator/health/**` | Explicitly permits anonymous health access |
| Any | `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**` | Explicitly permits anonymous API-documentation access |
| Any | All other paths | Requires an authenticated bearer token |

Security failures use `application/problem+json` with `type`, `title`, `status`,
`code`, `detail`, `instance`, `requestId`, and `errors`. Every response receives
an `X-Request-ID` header.

### Database changes

| Migration | Change | Compatibility notes |
|---|---|---|
| N/A | No database change | F01 schema remains unchanged |

### Configuration and dependencies

- Added `OIDC_ISSUER_URI` and `OIDC_AUDIENCE` environment variables.
- Added Spring Boot OAuth2 resource-server and validation starters.
- Added Spring Security test support.
- Added JaCoCo 0.8.14 with aggregate unit/integration reporting and 80% line and branch gates.
- Added a `test`-profile decoder that rejects raw tokens without contacting an external identity provider.

### Security and operational behavior

- Uses stateless session management and bearer authentication.
- Disables CSRF because access tokens are explicit request credentials rather than ambient cookies.
- Validates signature, issuer, token time window, audience, and subject before accepting production tokens.
- Uses only the request path in problem `instance` values so query parameters are never reflected.
- Preserves RFC 6750 bearer challenge behavior while returning stable application problems.
- Replaces inbound correlation headers to prevent log-correlation spoofing.
- Protects non-health actuator endpoints under the same default-authenticated policy.

## Technical Test Cases Implemented

### Unit tests

| Test case | Behavior verified |
|---|---|
| Production validator composition | The decoder combines token type, timestamps, certificate thumbprint, issuer, audience, and subject validation |
| Valid issuer and operating window | A correctly scoped token inside its deterministic time window passes validation |
| Invalid issuer rejected | A token from an authority other than the configured issuer fails validation |
| Invalid operating window rejected | Expired and not-yet-valid tokens fail against a fixed test clock |
| Exact audience accepted | A token containing the configured audience passes validation |
| Invalid audiences rejected | Missing, empty, and different audiences return `invalid_token` |
| Blank configured audience rejected | Validator construction fails fast for invalid configuration |
| Non-blank subject accepted | A usable issuer-scoped subject passes validation |
| Missing or blank subject rejected | Identity-less tokens return `invalid_token` |
| Valid OIDC configuration accepted | Issuer and trimmed audience bind and validate |
| Invalid OIDC configuration rejected | Missing, malformed, non-HTTP(S), query-bearing, fragment-bearing, and oversized settings fail validation |
| Valid current actor normalized | Required and optional claims are trimmed and retained within bounds |
| Optional email accepted | Missing or blank email maps to `null` |
| Invalid actor claims rejected | Missing, malformed, or oversized issuer, subject, display-name, and email claims fail safely |
| JWT claims mapped to current actor | Authenticated issuer, subject, name, and email become the framework-independent actor |
| Display-name fallback applied | `name`, then `preferred_username`, then subject is selected |
| Invalid security context rejected | Missing, unauthenticated, or non-JWT authentication cannot produce an actor |
| Missing JWT issuer rejected | Actor mapping fails when the trusted identity tuple is incomplete |
| Server request ID propagated | The filter generates one UUID, stores it on the request, and returns it in the response header |
| Inbound request ID replaced | A client-controlled correlation value is not trusted |
| Uninitialized request ID rejected | Downstream resolution fails explicitly when the filter did not run |
| Authentication problem written | `401`, bearer challenge, stable fields, and decoder-detail redaction are enforced |
| Authorization problem written | `403`, stable fields, and internal-policy redaction are enforced |

### Integration tests

| Test case | Behavior verified |
|---|---|
| Public route policy | Health and OpenAPI routes return successfully without authentication |
| Missing bearer token | A protected route returns correlated `401 application/problem+json` |
| Invalid bearer token | Raw invalid credentials return `401` without decoder details |
| Authenticated request routing | Authenticated GET and POST requests reach MVC and return the expected unmapped-route `404` |
| CSRF-free stateless POST | An authenticated POST does not require a CSRF token |
| Operational endpoint protection | Non-health actuator endpoints require authentication |
| Application context and schema regression | The secured application starts against the Liquibase-managed MySQL 8.4 schema |

### API, security, and concurrency tests

| Test case | Behavior verified |
|---|---|
| RFC 6750 authentication response | Missing and invalid credentials retain the bearer challenge |
| Information-leak prevention | Decoder and access-policy exception details are absent from client responses |
| Correlation spoofing prevention | An inbound `X-Request-ID` is replaced by a valid server UUID |
| Public-route minimum | Only health and API-documentation routes bypass authentication |
| N/A | F02 introduces no concurrent state transition |

### Validation evidence

| Command or check | Result |
|---|---|
| `.\gradlew.bat test --no-daemon --stacktrace --warning-mode all` | Passed 25 unit tests |
| `.\gradlew.bat integrationTest --no-daemon --stacktrace --warning-mode all` | Passed 12 MySQL-backed integration tests |
| `.\gradlew.bat jacocoTestReport jacocoTestCoverageVerification --no-daemon --stacktrace --warning-mode all` | Passed with 95.29% line coverage and 93.94% branch coverage |
| `.\gradlew.bat clean check --no-daemon --stacktrace --warning-mode all` | Passed from a clean build |

## Compatibility and Migration Notes

- The database contract is unchanged.
- Deployments must provide both OIDC settings and allow the application to reach the issuer's discovery and key endpoints.
- Existing unauthenticated application routes become protected unless they are on the explicit public allowlist.
- Health probes remain anonymous; non-health actuator endpoints now require authentication.
- Rollback removes the request-security boundary and its two required environment variables; no data rollback is needed.

## Deferred Work

- F03 provisions a durable application user from the validated `(issuer, subject)` identity.
- Group membership authorization begins with the group behavior features.
- Provider-specific roles, refresh-token handling, and identity-provider administration remain outside the backend MVP request boundary.
