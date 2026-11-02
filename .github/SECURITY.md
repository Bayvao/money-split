# Security Policy

## Supported version

Security fixes are applied to the latest code on `master`. No released version is currently supported because the application is still under initial development.

## Reporting a vulnerability

Use [GitHub private vulnerability reporting](https://github.com/Bayvao/money-split/security/advisories/new) and include:

- A clear description of the issue and affected behavior.
- Reproduction steps or a minimal proof of concept.
- Expected impact and any known preconditions.
- Suggested remediation, when available.

Do not include real credentials, personal information, or financial records. Do not disclose exploit details in a public issue. If private reporting is temporarily unavailable, open a public issue requesting a private maintainer contact without including vulnerability details.

## Automated controls

The repository runs build/test, CodeQL, dependency review, Trivy, dependency-graph submission, Dependabot, and an authorized manual OWASP ZAP API scan. Automated results support review but do not replace threat modeling or manual security review of authentication, authorization, financial invariants, and data exposure.
