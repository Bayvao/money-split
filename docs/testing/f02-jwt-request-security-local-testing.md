# F02 JWT Request-Security Local Testing

This guide covers local verification of the F02 JWT request-security boundary.
It provides two complementary paths:

1. Automated unit and integration tests, which do not contact a real identity
   provider.
2. Manual HTTP smoke tests against the running application and a local or
   development OIDC provider.

F02 does not yet expose a user or group API. A valid token reaching an unmapped
protected route therefore returns `404 Not Found`; that result proves the request
passed authentication and reached Spring MVC.

## Prerequisites

- JDK 25
- Docker Desktop or another Testcontainers-compatible Docker runtime for
  MySQL-backed integration tests
- PowerShell
- For manual HTTP testing only:
  - MySQL 8.4
  - An OIDC provider that publishes discovery metadata and signing keys

Verify the local tools:

```powershell
java -version
.\gradlew.bat --version
docker info
```

If `JAVA_HOME` does not point to JDK 25, set it for the current PowerShell
session and prepend its `bin` directory:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Adjust the path to match the installed JDK.

## Recommended automated test path

### 1. Run fast unit tests

These tests require neither Docker nor an OIDC provider:

```powershell
.\gradlew.bat test --no-daemon --stacktrace --warning-mode all
```

The tests cover:

- OIDC configuration validation
- issuer and timestamp validator composition using a fixed clock
- exact audience and required-subject validation
- current-actor claim normalization and fallback behavior
- request-ID creation and spoofing prevention
- safe `401` and `403` problem responses

To run only the F02 unit tests:

```powershell
.\gradlew.bat test `
  --tests "com.money.split.platform.security.*" `
  --tests "com.money.split.platform.api.RequestIdFilterTest" `
  --tests "com.money.split.platform.config.OidcPropertiesTest" `
  --no-daemon --stacktrace
```

### 2. Run the HTTP security integration test

First remove datasource variables left over from a manually running application.
When `SPRING_DATASOURCE_URL` is absent, the test starts the pinned MySQL 8.4
Testcontainers image automatically:

```powershell
Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_USERNAME -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue

.\gradlew.bat integrationTest `
  --tests "com.money.split.platform.security.SecurityHttpIntegrationTest" `
  --no-daemon --stacktrace --warning-mode all
```

The integration test uses a test-only JWT decoder and Spring Security test
principals. It never contacts an external issuer.

> **Important:** If `SPRING_DATASOURCE_URL` is set, integration tests use that
> database instead of Testcontainers and delete mutable application rows during
> cleanup. Only configure an isolated disposable test database.

To deliberately use an existing isolated test database:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/money_split_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectionTimeZone=UTC"
$env:SPRING_DATASOURCE_USERNAME = "money_split"
$env:SPRING_DATASOURCE_PASSWORD = "<test-password>"

.\gradlew.bat integrationTest --no-daemon --stacktrace --warning-mode all
```

### 3. Run the complete merge gate

```powershell
.\gradlew.bat clean check --no-daemon --stacktrace --warning-mode all
```

This runs unit tests, MySQL-backed integration tests, and JaCoCo verification.
The build fails if aggregate line or branch coverage drops below 80%.

At the F02 baseline, the expected result is:

- 25 unit tests passed
- 12 integration tests passed
- 95.29% line coverage
- 93.94% branch coverage

Open the HTML coverage report:

```powershell
Start-Process "build\reports\jacoco\test\html\index.html"
```

## Manual HTTP smoke testing

### 1. Start an isolated MySQL database

The following example starts the same pinned MySQL release used by integration
tests. The credentials are local examples only and must not be reused in a
shared environment:

```powershell
docker run --rm --name money-split-mysql `
  -p 3306:3306 `
  -e MYSQL_DATABASE=money_split `
  -e MYSQL_USER=money_split `
  -e MYSQL_PASSWORD=money_split_password `
  -e MYSQL_ROOT_PASSWORD=local_root_password `
  mysql:8.4.13@sha256:a0225d1ea6631c8bc2385dc4c2faba17bbf1834ecf369e1659a9a382bd8d5dd7
```

Wait until the container reports that it is ready for connections:

```powershell
docker logs money-split-mysql
```

### 2. Prepare an OIDC client

The application intentionally has no local authentication bypass. Configure a
client in a local or development OIDC provider with:

- An issuer that exactly matches `OIDC_ISSUER_URI`
- A reachable OpenID Connect discovery document and `jwks_uri`
- Signed JWT access tokens
- A non-blank `sub` claim
- An `aud` array containing the exact `OIDC_AUDIENCE` value
- Valid `exp` and, when supplied, `nbf` timestamps

Use an access token, not an ID token.

For example, when using Keycloak:

1. Create a realm named `money-split`.
2. Create a confidential OpenID Connect client used for local testing.
3. Enable service accounts if using the client-credentials flow.
4. Add an audience mapper that includes `money-split-api` in the access-token
   `aud` claim.
5. Copy the client secret without committing it to the repository.

A Keycloak client-credentials request typically looks like this:

```powershell
$tokenResponse = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8180/realms/money-split/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body @{
    grant_type    = "client_credentials"
    client_id     = "<local-client-id>"
    client_secret = "<local-client-secret>"
  }

$accessToken = $tokenResponse.access_token
```

Token acquisition is provider-specific. Never paste a real token into a public
JWT inspection website, terminal transcript, issue, or committed file.

### 3. Start the application

Set the application configuration in a new PowerShell terminal:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/money_split?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectionTimeZone=UTC"
$env:SPRING_DATASOURCE_USERNAME = "money_split"
$env:SPRING_DATASOURCE_PASSWORD = "money_split_password"
$env:OIDC_ISSUER_URI = "http://localhost:8180/realms/money-split"
$env:OIDC_AUDIENCE = "money-split-api"

.\gradlew.bat bootRun
```

Startup should fail rather than silently weakening security when the issuer is
invalid, discovery is unavailable, or required configuration is missing.

### 4. Verify public routes

Health must be available without a token:

```powershell
curl.exe --silent --show-error --include `
  "http://localhost:8080/actuator/health"
```

Expected:

- HTTP `200`
- An `X-Request-ID` response header containing a UUID

OpenAPI discovery must also be public:

```powershell
curl.exe --silent --show-error --include `
  "http://localhost:8080/v3/api-docs"
```

Expected: HTTP `200`.

### 5. Verify a missing-token failure

```powershell
curl.exe --silent --show-error --include `
  -H "X-Request-ID: client-controlled" `
  "http://localhost:8080/api/v1/not-implemented?sensitive=must-not-be-reflected"
```

Expected:

- HTTP `401`
- `Content-Type: application/problem+json`
- A `WWW-Authenticate: Bearer...` header
- A server-generated `X-Request-ID` that is not `client-controlled`
- Problem code `UNAUTHORIZED`
- Problem `instance` equal to `/api/v1/not-implemented`, without the query string

Representative response body:

```json
{
  "type": "urn:money-split:problem:unauthorized",
  "title": "Authentication required",
  "status": 401,
  "code": "UNAUTHORIZED",
  "detail": "A valid bearer token is required",
  "instance": "/api/v1/not-implemented",
  "requestId": "<server-generated-uuid>",
  "errors": {}
}
```

### 6. Verify an invalid-token failure

```powershell
curl.exe --silent --show-error --include `
  -H "Authorization: Bearer definitely-invalid" `
  "http://localhost:8080/api/v1/not-implemented"
```

Expected: the same safe `401` problem shape. Decoder, signature, and provider
details must not appear in the response.

### 7. Verify a valid token reaches MVC

After obtaining a valid access token:

```powershell
curl.exe --silent --show-error --include `
  -H "Authorization: Bearer $accessToken" `
  "http://localhost:8080/api/v1/not-implemented"
```

Expected: HTTP `404`, not `401`. The route intentionally does not exist in F02,
so `404` demonstrates that authentication succeeded and MVC handled the request.

The same token with the wrong issuer, missing audience, blank subject, expired
timestamp, future `nbf`, or invalid signature must receive `401`.

## Troubleshooting

| Symptom | Likely cause | Resolution |
|---|---|---|
| Gradle reports the wrong Java version | `JAVA_HOME` points to an older or missing JDK | Set `JAVA_HOME` to JDK 25 and update `Path` for the current terminal |
| Testcontainers cannot find Docker | Docker Desktop is stopped or its engine is unavailable | Start Docker and confirm `docker info` succeeds |
| Integration tests modify an unexpected database | `SPRING_DATASOURCE_URL` was inherited from another terminal setup | Clear the datasource variables and rerun with Testcontainers |
| Application startup cannot resolve the issuer | OIDC discovery is unreachable or the issuer URL is incorrect | Open the provider discovery document locally and verify the exact issuer |
| A seemingly valid token returns `401` | Wrong token type, issuer, audience, time window, or signing key | Use an access token and compare `iss` and `aud` exactly with application configuration |
| A valid token returns `404` | No protected business endpoint exists in F02 | This is the expected authenticated result for `/api/v1/not-implemented` |
| `/actuator/info` returns `401` without a token | Only health is public | Authenticate or use `/actuator/health` for anonymous probes |

F02 cannot currently produce an HTTP `403` through a business endpoint because
role- or membership-based authorization is introduced by later features. The
`ApiAccessDeniedHandler` behavior is covered by its focused unit test.

## Cleanup

Stop the application with `Ctrl+C`. Stop the named local MySQL container:

```powershell
docker stop money-split-mysql
```

Remove sensitive values from the current shell when testing is complete:

```powershell
Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
Remove-Variable accessToken -ErrorAction SilentlyContinue
Remove-Variable tokenResponse -ErrorAction SilentlyContinue
```
