# CI and Security Automation

## Purpose

The repository uses GitHub Actions and free, no-external-login security tools before backend feature development begins. All third-party actions are pinned to commit SHAs, and Dependabot monitors their versions.

The repository is public, so GitHub CodeQL and Dependency Review are available without a GitHub Advanced Security subscription. Workflows use the automatically provided `GITHUB_TOKEN`; no external scanner account or API token is required.

## Automated controls

| Workflow or service | Coverage | Trigger | Merge gate |
|---|---|---|---|
| CI | Gradle wrapper validation, Java 25 compilation, tests, ephemeral MySQL | Pull request, `master`, manual | Yes |
| CodeQL | Java/Kotlin semantic SAST and quality queries | Pull request, `master`, weekly, manual | Yes |
| Dependency Review | New vulnerable Gradle or Actions dependencies at moderate severity or above | Pull request | Yes |
| Dependency Submission | Complete Gradle dependency graph for alerts and Dependabot | `master`, manual | Operational |
| Trivy | Dependency CVEs, committed secrets, and repository misconfiguration | Pull request, `master`, weekly, manual | Fixable high/critical findings |
| Zizmor | GitHub Actions and Dependabot security analysis | Pull request, workflow changes on `master`, weekly, manual | Medium-or-higher, medium-confidence findings |
| Dependabot | Gradle and GitHub Actions update pull requests | Weekly | Review required |
| OWASP ZAP API Scan | Active DAST from an OpenAPI definition | Authorized manual run | Required before release, not before PR 1 |

The CI MySQL credentials are static only inside an isolated GitHub-hosted runner and are not production secrets. The MySQL and ZAP containers are pinned by version and multi-architecture image digest.

Gradle dependency locking produces a committed `gradle.lockfile`. Builds therefore resolve the reviewed versions until an intentional dependency update refreshes the lock state, and Trivy has a deterministic Java dependency manifest to scan.

## Tool selection

Selected tools:

- **CodeQL:** semantic SAST and code-quality analysis for this public GitHub repository.
- **GitHub Dependency Review and Dependabot:** pull-request dependency policy, vulnerability alerts, and updates.
- **Trivy:** unauthenticated filesystem scan for vulnerable packages, secrets, and misconfiguration; the Gradle lockfile and built Spring Boot JAR are included in the scan.
- **Zizmor:** static analysis of workflow permissions, credential persistence, expression injection, and action references.
- **OWASP ZAP:** active API scanning against an explicitly authorized non-production target.

Not selected:

- **Black Duck, Checkmarx/CxSAST, and Snyk:** require an external account, token, paid entitlement, or hosted-service onboarding for the expected workflow.
- **OWASP Dependency-Check:** open source, but reliable NVD-backed CI commonly requires an API key and database caching to avoid anonymous rate-limit failures. Dependency Review and Trivy provide the initial no-login SCA controls.
- **SonarCloud:** requires external service onboarding and authentication. CodeQL's `security-and-quality` query suite is used initially.

Revisit the selection only when a requirement is not covered or organizational tooling becomes available. Do not add overlapping scanners solely to increase tool count.

## Enforcement policy

- Dependency Review fails when a pull request introduces a moderate, high, or critical vulnerability in runtime or development scope.
- Trivy publishes all severities to SARIF and blocks fixable high/critical vulnerability, secret, or misconfiguration findings.
- There is no ignore file initially. Any future suppression must identify the advisory/rule, rationale, owner, compensating control, and expiration date in its PR record.
- CodeQL uses the `security-and-quality` suite and uploads findings to GitHub code scanning.
- ZAP is an active attack tool. It must run only against an authorized, non-production target. The manual workflow requires explicit acknowledgement and an HTTPS OpenAPI URL.
- Pull requests from forks still run the scanners, but SARIF upload may be skipped when the token cannot safely receive `security-events: write`.
- Workflow checkouts disable credential persistence because no job needs authenticated Git commands.
- Container image digests must be reviewed and refreshed deliberately; mutable tags are not executed directly.

## Repository security settings

The following settings were enabled while establishing this baseline:

- Dependency graph and Dependabot vulnerability alerts.
- Dependabot security updates.
- Secret scanning and push protection.
- Private vulnerability reporting.
- Read-only default workflow permissions, without permission for Actions to approve pull requests.

After these workflows reach `master`, configure branch protection:

1. Protect `master` and require pull requests.
2. Require these status checks after each has completed at least once:
   - `CI / Build and test`
   - `CodeQL / Analyze Java`
   - `Dependency Review / Review dependency changes`
   - `Security Scan / Trivy repository scan`
   - `GitHub Actions Security / Audit workflow security`
3. Require the branch to be current before merge and dismiss approvals when security-relevant code changes.
4. Limit direct pushes and force pushes to `master`.

The dependency-submission workflow alone requests `contents: write`. Branch protection is deferred until these new checks exist on `master`; a repository administrator must then select the completed checks by name.

## Current bootstrap limitation

The generated project includes Liquibase but does not yet have its first changelog. CI temporarily sets `SPRING_LIQUIBASE_ENABLED=false` while still connecting the Spring context to an isolated MySQL service. PR 1 must add the real master changelog and remove this override so CI validates migrations.

## API scan usage

The ZAP workflow is intentionally manual until an API and non-production deployment exist:

1. Deploy an authorized test environment with no production data.
2. Expose its OpenAPI definition over HTTPS.
3. Run `API Security Scan` from GitHub Actions.
4. Supply the OpenAPI URL and confirm authorization.
5. Review the attached ZAP report and fix or explicitly govern findings.

Authentication support can be added later through protected GitHub secrets. Never pass bearer tokens through workflow-dispatch text inputs.
