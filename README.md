# Money Split

A Java and Spring Boot project for personal money tracking and shared expense splitting, using MySQL.

[![CI](https://github.com/Bayvao/money-split/actions/workflows/ci.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/ci.yml)
[![CodeQL](https://github.com/Bayvao/money-split/actions/workflows/codeql.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/codeql.yml)
[![Security Scan](https://github.com/Bayvao/money-split/actions/workflows/security-scan.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/security-scan.yml)

## Technical design

See [the technical design document](docs/design.md) for the proposed MVP architecture, confirmed requirements, permission model, financial algorithms, multi-currency handling, database model, APIs, offline synchronization, and delivery roadmap.

See [the backend implementation plan](docs/backend-implementation-plan.md) for the backend-only sequence of PR-sized vertical slices, first implementation checklist, migrations, testing strategy, and delivery gates.

See [the pull request documentation standard](docs/prs/README.md) for the required per-PR change record and reusable template.

See [CI and security automation](docs/security-ci.md) for workflow coverage, enforcement policy, tool choices, and required repository settings.

The design is a proposal; the described features are not yet implemented. The current Gradle build declares Java 25, Spring Boot 4.1.2, Spring Data JDBC, and Liquibase.
