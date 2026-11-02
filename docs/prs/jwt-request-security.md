# Firebase ID-Token Request-Security Boundary

| Field | Value |
|---|---|
| Status | Pull request open |
| Branch | `feat/jwt-request-security` |
| Pull request | [#4](https://github.com/Bayvao/money-split/pull/4) |
| Date | 2026-11-02 |
| Related plan item | F02 - Firebase ID-token request-security boundary |

## Commit Messages

The pull request retains these commits in order:

```text
feat(security): add JWT request security boundary

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

```text
docs(pr): link JWT request security pull request

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

```text
docs(testing): add JWT local testing guide

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

```text
feat(security): validate Firebase identity tokens

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

## Feature

Establish a stateless backend security boundary for clients authenticated by
Firebase. Web, iOS, and Android clients sign in with Google or Apple through a
Firebase SDK and send the Firebase ID token as a bearer credential. The backend
validates that token locally before allowing access to protected routes.

### Scope

- Included: Firebase project/provider configuration, Google public-key signature
  validation, Firebase issuer/project-audience/time/UID/provider validation,
  stateless route policy, current-actor claim mapping, server-owned request IDs,
  safe `401` and `403` problems, test-only decoding, JaCoCo enforcement, and
  local testing documentation.
- Excluded: application-user provisioning, group authorization, refresh-token
  handling, Google/Apple OAuth callbacks, provider access-token processing,
  Firebase Admin SDK integration, per-request revocation checks, and F03 or
  later behavior.

### Acceptance Criteria

- [x] Production tokens are verified with Firebase's Google-hosted public keys
  and RS256 only.
- [x] Issuer and audience are derived from one validated Firebase project ID.
- [x] `exp`, optional `nbf`, required `iat`, and required `auth_time` are
  validated with a consistent 60-second clock skew.
- [x] Firebase UID is present, unpadded, and at most 128 characters.
- [x] `firebase.sign_in_provider` is restricted to a configurable allowlist
  whose deployment default is `google.com,apple.com`.
- [x] The current actor uses `(issuer, Firebase UID)` as durable identity and
  treats name/email as mutable profile data.
- [x] Health and API-documentation routes are public; every other route requires
  authentication.
- [x] Security failures return correlated safe problems without token, decoder,
  key, or policy details.
- [x] Incoming request IDs are replaced with server-generated UUIDs.
- [x] The API remains stateless and bearer-authenticated writes do not require a
  CSRF token.
- [x] Every changed Java method and constructor has JavaDoc; important security
  decisions have intent-focused comments.
- [x] Aggregate line and branch coverage remain above 80%.

## What Was Implemented

### Firebase trust configuration

- Replaced arbitrary issuer/audience configuration with
  `FIREBASE_PROJECT_ID`.
- Derived the only trusted issuer as
  `https://securetoken.google.com/<project-id>` and the exact audience as the
  project ID.
- Added `FIREBASE_ALLOWED_SIGN_IN_PROVIDERS`, defaulting to
  `google.com,apple.com` in application configuration.
- Normalized provider IDs to lowercase and validated project/provider values at
  startup.
- Kept future Firebase providers configurable without accepting all providers
  implicitly.

### Token verification

- Configured `NimbusJwtDecoder` with Firebase's fixed Google JWK endpoint and
  explicit RS256 restriction; no issuer discovery or Firebase Admin SDK is
  required.
- Retained Spring token-type, timestamp, certificate-thumbprint, and issuer
  checks.
- Added exact project-audience validation.
- Added Firebase UID validation with no whitespace normalization and a
  128-character maximum.
- Added required `iat` and `auth_time` checks against an injected UTC clock.
- Added nested `firebase.sign_in_provider` parsing and exact allowlist
  enforcement.
- Treats missing, malformed, future, or disallowed Firebase claims as
  `invalid_token`.

### Request and actor security

- Added a framework-independent current actor containing issuer, Firebase UID,
  display name, and optional email.
- Uses `name`, then `preferred_username`, then Firebase UID for display-name
  fallback.
- Accepts Apple private relay email as ordinary optional profile data; email is
  never an identity key.
- Configured stateless bearer authentication, an explicit anonymous route
  allowlist, server-owned request IDs, and safe problem responses.
- Preserved RFC 6750 bearer challenges while redacting internal validation
  details.
- Uses only the request path in problem `instance` so query values are not
  reflected.

### Route policy

| Method | Path | Policy |
|---|---|---|
| Any | `/actuator/health`, `/actuator/health/**` | Anonymous |
| Any | `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**` | Anonymous |
| Any | All other paths | Valid Firebase bearer token required |

### Database and dependency impact

| Area | Change |
|---|---|
| Database | No migration; the existing case-sensitive issuer/subject identity key remains compatible with Firebase issuer/UID |
| Runtime dependencies | Spring OAuth2 resource server and Bean Validation; no Firebase Admin SDK |
| Test dependencies | Spring Security test and existing MySQL Testcontainers support |
| Coverage | JaCoCo aggregate unit/integration report with 80% line and branch gates |

## Technical Test Cases Implemented

### Firebase configuration

| Test case | Behavior verified |
|---|---|
| Valid project/provider configuration | Trims project ID, lowercases providers, and derives issuer/audience |
| Immutable provider configuration | Caller mutations cannot change the bound allowlist |
| Invalid project IDs | Missing, blank, short, long, uppercase, and malformed IDs fail Bean Validation |
| Invalid provider allowlist | Null, empty, blank, null-element, and oversized values fail validation |

### Decoder and token claims

| Test case | Behavior verified |
|---|---|
| Decoder construction | Production decoder builds from the Google JWK URI without issuer discovery |
| Valid Google token | Correct project, timestamps, UID, and `google.com` provider pass |
| Valid Apple token | `apple.com` provider passes |
| Configured future provider | Explicitly allowlisted provider passes without code changes |
| Wrong issuer/audience | A token from another project is rejected |
| Expired/future operating window | `exp` and `nbf` violations are rejected against a fixed clock |
| Missing/future `iat` | Required issue time is enforced |
| Missing/malformed/future `auth_time` | Required Firebase authentication time is enforced safely |
| Missing/malformed provider envelope | Invalid nested Firebase claim shapes are rejected |
| Disallowed provider | Provider not in the configured allowlist is rejected |
| Missing/blank/padded/oversized UID | Invalid Firebase subjects are rejected |

### Current actor and HTTP boundary

| Test case | Behavior verified |
|---|---|
| Firebase actor mapping | Issuer, UID, name, and email map to the application actor |
| Display-name fallback | Name, preferred username, then UID are selected |
| Apple relay profile | `privaterelay.appleid.com` remains valid optional email data |
| Invalid security context | Missing, unauthenticated, and non-JWT contexts cannot produce an actor |
| Public routes | Health and OpenAPI remain anonymous |
| Missing/malformed token | Protected route returns safe correlated `401` problem |
| Authenticated GET/POST | Firebase-shaped principals reach MVC and do not require CSRF |
| Actuator protection | Non-health actuator endpoints require authentication |
| Request-ID spoofing | Inbound correlation IDs are replaced by server UUIDs |
| Security detail redaction | Decoder and authorization internals are absent from responses |

### Validation Evidence

| Command or check | Result |
|---|---|
| Focused Firebase tests before implementation | Failed to compile because `FirebaseProperties` and `FirebaseTokenValidator` did not exist (expected TDD red state) |
| Focused Firebase tests after implementation | Passed |
| `.\gradlew.bat test --no-daemon --stacktrace --warning-mode all` | Passed 35 unit tests |
| MySQL-backed `integrationTest` task | Passed 12 tests |
| `.\gradlew.bat clean check --no-daemon --stacktrace --warning-mode all` | Passed |
| Post-cleanup `.\gradlew.bat check --no-daemon --stacktrace --warning-mode all` | Passed |
| JaCoCo aggregate coverage | 97.37% line (222/228) and 94.57% branch (87/92) |
| IntelliJ inspections and project build | No warnings or build problems |

## Configuration and Migration Notes

- Replace `OIDC_ISSUER_URI` and `OIDC_AUDIENCE` with
  `FIREBASE_PROJECT_ID`.
- `FIREBASE_ALLOWED_SIGN_IN_PROVIDERS` is optional and defaults to
  `google.com,apple.com`.
- Clients must send a Firebase ID token from the Firebase SDK, not a Google or
  Apple OAuth access token.
- Runtime instances require outbound HTTPS to Firebase's Google public JWK
  endpoint. Nimbus caches and refreshes rotating keys.
- Google/Apple client secrets and Firebase service-account private keys are not
  backend configuration for this feature.
- Existing unauthenticated application routes become protected unless they are
  on the explicit public allowlist.
- No data migration or rollback is required.

## Deferred Work

- F03 provisions one durable application user from `(Firebase issuer, UID)`.
- Firebase manages account/provider linking; the backend does not merge users
  by email.
- Local verification does not immediately detect revoked tokens or disabled
  users. Service-account-backed revocation enforcement can be introduced if a
  later security policy requires it.
- Group membership authorization starts with group behavior features.
