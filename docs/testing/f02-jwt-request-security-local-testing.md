# F02 Firebase Request-Security Local Testing

This guide verifies the backend boundary for Firebase Authentication. Web,
iOS, and Android clients sign in with Google or Apple through a Firebase SDK,
then send the resulting **Firebase ID token** as:

```text
Authorization: Bearer <firebase-id-token>
```

The backend does not receive Google or Apple OAuth access tokens and does not
need provider client secrets. It verifies the Firebase ID token against Google's
public signing keys and the configured Firebase project and provider policy.

F02 does not expose a user or group API. A valid token sent to an unmapped
protected route returns `404 Not Found`; this proves authentication succeeded
and the request reached Spring MVC.

## Prerequisites

- JDK 25
- Docker Desktop or another Testcontainers-compatible runtime for integration
  tests
- PowerShell
- For manual bearer-token testing:
  - MySQL 8.4
  - A development Firebase project
  - Google and/or Apple enabled under Firebase Authentication sign-in methods
  - A configured web, iOS, or Android development client

Verify local tools:

```powershell
java -version
.\gradlew.bat --version
docker info
```

If `JAVA_HOME` does not select JDK 25, set it for the current terminal:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Adjust the path for the installed JDK.

## Recommended automated path

### 1. Run unit tests

Unit tests require neither Docker nor Firebase network access:

```powershell
.\gradlew.bat test --no-daemon --stacktrace --warning-mode all
```

They cover:

- Firebase project and provider-allowlist configuration
- Google Secure Token issuer and project audience validation
- RS256 decoder construction using Firebase's public JWK endpoint
- `exp`, `nbf`, `iat`, and `auth_time` validation against a fixed clock
- Google, Apple, disallowed, missing, and malformed sign-in-provider claims
- Firebase UID shape and 128-character limit
- current-actor profile mapping, including Apple private relay email
- request-ID spoofing prevention and safe `401`/`403` responses

Run only F02 unit tests:

```powershell
.\gradlew.bat test `
  --tests "com.money.split.platform.security.*" `
  --tests "com.money.split.platform.api.RequestIdFilterTest" `
  --tests "com.money.split.platform.config.FirebasePropertiesTest" `
  --no-daemon --stacktrace
```

### 2. Run the HTTP security integration test

Clear inherited datasource variables so Testcontainers starts the pinned MySQL
8.4 image:

```powershell
Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_USERNAME -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue

.\gradlew.bat integrationTest `
  --tests "com.money.split.platform.security.SecurityHttpIntegrationTest" `
  --no-daemon --stacktrace --warning-mode all
```

The integration test installs a test-only JWT decoder and Firebase-shaped Spring
Security principals. It never contacts Firebase or Google's key endpoint.

> **Important:** When `SPRING_DATASOURCE_URL` is present, integration tests use
> that database and delete mutable application rows during cleanup. Supply only
> an isolated disposable test schema.

To use an existing isolated test database:

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

This runs unit tests, MySQL-backed integration tests, and the aggregate JaCoCo
gate. Both line and branch coverage must remain at or above 80%.

At the completed F02 Firebase revision, the expected baseline is 35 passing unit
tests, 12 passing integration tests, 97.37% line coverage, and 94.57% branch
coverage.

Open the generated report:

```powershell
Start-Process "build\reports\jacoco\test\html\index.html"
```

## Manual Firebase bearer-token test

### 1. Start local MySQL

This example uses the same pinned image as the integration suite. These are
local-only example credentials:

```powershell
docker run --rm --name money-split-mysql `
  -p 3306:3306 `
  -e MYSQL_DATABASE=money_split `
  -e MYSQL_USER=money_split `
  -e MYSQL_PASSWORD=money_split_password `
  -e MYSQL_ROOT_PASSWORD=local_root_password `
  mysql:8.4.13@sha256:a0225d1ea6631c8bc2385dc4c2faba17bbf1834ecf369e1659a9a382bd8d5dd7
```

Wait for the ready message:

```powershell
docker logs money-split-mysql
```

### 2. Obtain a Firebase ID token

Use a development client already configured for the same Firebase project.
After Google sign-in, a Firebase Web SDK flow obtains the token as follows:

```javascript
const credential = await signInWithPopup(getAuth(), new GoogleAuthProvider());
const firebaseIdToken = await credential.user.getIdToken(true);
```

For Apple:

```javascript
const appleProvider = new OAuthProvider("apple.com");
const credential = await signInWithPopup(getAuth(), appleProvider);
const firebaseIdToken = await credential.user.getIdToken(true);
```

Mobile clients use their platform Firebase SDK's equivalent `getIdToken`
operation. Apple provider setup still requires the normal Apple Developer and
Firebase console configuration, but those secrets belong in the identity
provider/client setup, not in this backend.

Transfer the short-lived token only through a trusted local channel, then keep
it in the current PowerShell process:

```powershell
$env:FIREBASE_ID_TOKEN = "<temporary-development-firebase-id-token>"
```

Do not commit, log, paste into a public JWT website, or retain the token in shell
history. A Google or Apple OAuth access token is not interchangeable with a
Firebase ID token.

Optionally inspect the payload locally without sending it anywhere:

```powershell
$payload = $env:FIREBASE_ID_TOKEN.Split(".")[1].Replace("-", "+").Replace("_", "/")
switch ($payload.Length % 4) {
    2 { $payload += "==" }
    3 { $payload += "=" }
}
[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) |
    ConvertFrom-Json |
    Format-List
```

Confirm these claims:

| Claim | Expected value |
|---|---|
| `iss` | `https://securetoken.google.com/<firebase-project-id>` |
| `aud` | Exact Firebase project ID |
| `sub` | Non-blank Firebase UID, at most 128 characters |
| `exp` | Future expiration |
| `iat` | Present and not in the future beyond clock skew |
| `auth_time` | Present and not in the future beyond clock skew |
| `firebase.sign_in_provider` | `google.com` or `apple.com` by default |

### 3. Start the backend

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3306/money_split?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectionTimeZone=UTC"
$env:SPRING_DATASOURCE_USERNAME = "money_split"
$env:SPRING_DATASOURCE_PASSWORD = "money_split_password"
$env:FIREBASE_PROJECT_ID = "<firebase-project-id>"
$env:FIREBASE_ALLOWED_SIGN_IN_PROVIDERS = "google.com,apple.com"

.\gradlew.bat bootRun
```

`FIREBASE_ALLOWED_SIGN_IN_PROVIDERS` is optional and defaults to the two values
shown. Set it explicitly when verifying deployment configuration or enabling a
future Firebase provider. `FIREBASE_PROJECT_ID` must be the project ID, not the
display name, numeric project number, application ID, or web API key.

The backend must be able to reach:

```text
https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com
```

Nimbus caches and refreshes Google's rotating public keys. No Firebase Admin SDK
or service-account private key is required for this request boundary.

### 4. Verify anonymous routes

```powershell
curl.exe --silent --show-error --include `
  "http://localhost:8080/actuator/health"

curl.exe --silent --show-error --include `
  "http://localhost:8080/v3/api-docs"
```

Both requests should return `200`. Each response includes a server-generated
`X-Request-ID`.

### 5. Verify missing and invalid tokens

Missing token:

```powershell
curl.exe --silent --show-error --include `
  -H "X-Request-ID: client-controlled" `
  "http://localhost:8080/api/v1/not-implemented?sensitive=must-not-be-reflected"
```

Malformed token:

```powershell
curl.exe --silent --show-error --include `
  -H "Authorization: Bearer not-a-jwt" `
  "http://localhost:8080/api/v1/not-implemented"
```

Expected:

- HTTP `401`
- `Content-Type: application/problem+json`
- `WWW-Authenticate: Bearer...`
- a server-generated `X-Request-ID`
- problem code `UNAUTHORIZED`
- no decoder, signing-key, provider-policy, token, or query-string details

Representative response:

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

### 6. Verify a valid Firebase ID token

```powershell
curl.exe --silent --show-error --include `
  -H "Authorization: Bearer $env:FIREBASE_ID_TOKEN" `
  "http://localhost:8080/api/v1/not-implemented"
```

Expected: HTTP `404`, not `401`. F02 intentionally has no controller for this
path, so the result proves that the Firebase token passed authentication.

A token with the wrong Firebase project, invalid signature, expired time window,
future `iat`/`auth_time`, missing or oversized UID, or a provider outside the
allowlist must return `401`.

## Troubleshooting

| Symptom | Likely cause | Resolution |
|---|---|---|
| Gradle uses the wrong Java version | `JAVA_HOME` selects an older or missing JDK | Select JDK 25 for the current terminal |
| Testcontainers cannot find Docker | Docker is stopped or its engine is unavailable | Start Docker and verify `docker info` |
| Integration tests touch an unexpected database | Datasource variables were inherited | Clear them and use Testcontainers |
| Startup rejects `FIREBASE_PROJECT_ID` | A display name, number, app ID, or malformed ID was supplied | Copy the exact project ID from Firebase project settings |
| First authenticated request cannot load keys | The Google JWK endpoint is blocked | Allow outbound HTTPS to the documented endpoint |
| Google/Apple sign-in succeeds but API returns `401` | The client sent a provider OAuth token instead of a Firebase ID token | Call the signed-in Firebase user's `getIdToken` method |
| Token project is rejected | Client and backend use different Firebase projects | Match token `iss`/`aud` to `FIREBASE_PROJECT_ID` |
| Provider is rejected | `firebase.sign_in_provider` is not allowlisted | Use Google/Apple or deliberately extend the allowlist |
| Previously valid token expires | Firebase ID tokens are short-lived | Ask the Firebase SDK for a current token and retry |
| Valid token returns `404` | F02 has no protected business route | This is expected for `/api/v1/not-implemented` |
| `/actuator/info` returns `401` | Only health and API docs are public | Authenticate or use `/actuator/health` |

F02 validates tokens locally and does not perform a per-request Firebase Admin
lookup for revoked tokens or disabled users. Firebase ID tokens are short-lived;
immediate revocation enforcement is a separate policy requiring trusted
service-account-backed verification.

The current API has no business authorization rule that produces an end-to-end
`403`. The access-denied response is covered by its focused unit test.

## Cleanup

Stop the application with `Ctrl+C`, then stop local MySQL:

```powershell
docker stop money-split-mysql
```

Remove sensitive values from the shell:

```powershell
Remove-Item Env:FIREBASE_ID_TOKEN -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
Remove-Variable payload -ErrorAction SilentlyContinue
Remove-Variable firebaseIdToken -ErrorAction SilentlyContinue
```
