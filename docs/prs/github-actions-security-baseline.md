# GitHub Actions Security Baseline

| Field | Value |
|---|---|
| Status | Validated in pull request |
| Branch | `ci/github-actions-security` |
| Pull request | [#2](https://github.com/Bayvao/money-split/pull/2) |
| Date | 2026-11-02 |
| Related plan item | Pre-PR 1 delivery and security foundation |

## Commit Message

Planned squash commit message:

```text
ci(security): add GitHub Actions security gates

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

Branch commit messages:

- `ci(security): add GitHub Actions security gates`
- `fix(ci): mark Gradle wrapper executable`
- `fix(ci): make workflow security a stable gate`

## Feature

Add a no-external-login CI and security baseline before backend PR 1 so every backend change is built, tested, analyzed, and dependency-scanned consistently.

### Scope

- Included: Gradle CI, MySQL test service, CodeQL, dependency review/submission, Trivy, Zizmor, Dependabot, OWASP ZAP workflow, PR checklist, security policy, and operating documentation.
- Excluded: paid/account-based Black Duck, Checkmarx/CxSAST, Snyk, and SonarCloud integrations; deployment; application features; automatic authenticated DAST.

### Acceptance criteria

- [x] Pull requests run the Gradle build/tests on Java 25 against isolated MySQL.
- [x] Static source analysis runs with CodeQL security-and-quality queries.
- [x] New moderate-or-higher dependency vulnerabilities fail dependency review.
- [x] Trivy reports vulnerabilities, secrets, and misconfiguration and gates fixable high/critical findings.
- [x] Zizmor rejects material GitHub Actions security weaknesses.
- [x] The dependency graph is submitted from `master`, and Dependabot monitors Gradle and Actions.
- [x] Gradle dependency locking gives builds and vulnerability scanners a deterministic dependency set.
- [x] An explicitly authorized manual OWASP ZAP API scan is available for a future non-production API.
- [x] Actions use commit-SHA pins and minimal job permissions.
- [x] Required repository settings and current bootstrap limitations are documented.

## What Was Implemented

### Application changes

- No production application behavior changed.
- CI provides an ephemeral pinned MySQL 8.4 service for Spring context and future persistence tests.
- Liquibase is temporarily disabled only in CI until PR 1 supplies the first changelog.

### GitHub automation

| File | Behavior |
|---|---|
| `.github\workflows\ci.yml` | Java 25 Gradle build/test, wrapper validation, MySQL, failed-test artifacts |
| `.github\workflows\codeql.yml` | Manual-build CodeQL SAST and quality analysis |
| `.github\workflows\dependency-review.yml` | Pull-request dependency vulnerability policy |
| `.github\workflows\dependency-submission.yml` | Gradle dependency graph submission |
| `.github\workflows\security-scan.yml` | Trivy SARIF reporting and high/critical gate |
| `.github\workflows\actions-security.yml` | Zizmor workflow and Dependabot security audit |
| `.github\workflows\api-security.yml` | Authorized manual OWASP ZAP active API scan |
| `.github\dependabot.yml` | Weekly Gradle and Actions update groups |
| `.github\pull_request_template.md` | PR documentation and security review checklist |
| `.github\SECURITY.md` | Private vulnerability-reporting guidance |

### API changes

| Method | Path | Change |
|---|---|---|
| N/A | N/A | No application API change |

The ZAP workflow accepts a future HTTPS OpenAPI-definition URL; it does not add or modify an application endpoint.

### Database changes

| Migration | Change | Compatibility notes |
|---|---|---|
| N/A | No schema change | CI uses an ephemeral empty MySQL database |

### Configuration and dependencies

- Added GitHub workflow and Dependabot configuration only.
- Did not modify Gradle application dependencies; enabled dependency locking.
- Added the generated `gradle.lockfile` as the reviewed dependency baseline.
- Preserved the Unix executable bit on `gradlew` for GitHub-hosted Linux runners.
- Pinned action releases to commit SHAs, pinned service/scanner container images by digest, and selected Gradle's open-source basic cache provider.
- Added `docs\security-ci.md` with gates, limitations, tool rationale, and repository settings.

### Security and operational behavior

- Workflows follow least-privilege token permissions.
- Checkout credentials are not persisted in any workflow.
- No external scanner credentials or accounts are required.
- DAST requires an explicit authorization acknowledgement and HTTPS target.
- Trivy SARIF is retained as an artifact and uploaded to code scanning when permissions allow.
- Enabled the dependency graph, Dependabot alerts and security updates, secret scanning, push protection, private vulnerability reporting, and read-only default workflow permissions.
- Branch protection remains a documented post-merge action because the new checks must first exist on `master`.

## Technical Test Cases Implemented

No application unit tests were required because production code did not change.

### CI and integration test cases

| Test case | Behavior verified |
|---|---|
| Java 25 Gradle `clean check` | Project compiles and automated tests execute with the supported toolchain |
| Gradle wrapper validation | Tampered or unknown wrapper JARs fail setup |
| Linux Gradle wrapper execution | The committed executable bit allows `./gradlew` on GitHub-hosted runners |
| MySQL service health gate | Tests start only after isolated MySQL accepts connections |
| Failed-test artifact upload | Test reports remain available when the Gradle check fails |
| Dependency graph generation | Resolved Gradle dependencies are submitted for alerts and review |

### Security test cases

| Test case | Behavior verified |
|---|---|
| CodeQL manual Java build | Compiled Java is traced and analyzed with security-and-quality queries |
| Moderate vulnerable dependency introduction | Dependency Review rejects the pull request |
| Trivy complete SARIF scan | Vulnerability, secret, and misconfiguration findings are recorded |
| Fixable high/critical Trivy finding | Security workflow exits unsuccessfully |
| Zizmor medium-confidence security finding | Workflow-security job exits unsuccessfully |
| Fork pull request | Scan still runs while unsafe SARIF upload is skipped |
| ZAP authorization acknowledgement missing | Active API scan fails before attacking a target |
| Non-HTTPS OpenAPI target | Active API scan is rejected |
| Authorized HTTPS OpenAPI target | ZAP performs an active scan and fails on findings |

### Validation evidence

| Command or check | Result |
|---|---|
| `git diff --check` | Passed |
| `actionlint` 1.7.12 for `.github\workflows\*.yml` | Passed |
| Zizmor 1.31.1, pedantic persona, medium severity/confidence | Passed with no reportable findings |
| `.\gradlew.bat clean classes bootJar --no-daemon --stacktrace` with Java 25 | Passed |
| `.\gradlew.bat dependencies --write-locks` with Java 25 | Passed; deterministic lock state generated |
| Trivy 0.76.1 filesystem gate with development dependencies | Passed; no fixable high/critical vulnerabilities, secrets, or misconfigurations |
| MySQL-backed `clean check` | Passed in `CI / Build and test` on pull request #2 |
| GitHub-hosted security suite | Passed on corrected baseline commit `3889265`: CodeQL, Dependency Review, Trivy, and Zizmor |
| Repository-native security settings | Enabled and confirmed through the GitHub API |

## Compatibility and Migration Notes

- No application API or database migration is introduced.
- Workflow action pins are maintained by Dependabot.
- PR 1 must remove the CI Liquibase-disable override after adding the master changelog.
- Repository administrators must enable the documented GitHub security settings and required checks after merge.

## Deferred Work

- Authenticated ZAP scanning after OIDC and a protected test environment exist.
- Container-image scanning after the backend Dockerfile is introduced.
- Coverage enforcement after JaCoCo is configured in PR 1.
- CI enforcement that validates the structure of each `docs\prs` record.
