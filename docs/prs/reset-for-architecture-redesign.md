# Reset for Architecture Redesign

| Field | Value |
|---|---|
| Status | Pull request open |
| Branch | `chore/reset-for-redesign` |
| Pull request | [#5](https://github.com/Bayvao/money-split/pull/5) |
| Date | 2026-11-02 |
| Related plan item | N/A - retires the previous implementation plan |

## Commit Message

The pull request retains these commits in order:

```text
chore(reset): return backend to redesign baseline

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

```text
docs(pr): link architecture reset pull request

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

## Feature

Return the backend to a pre-feature baseline so the product, domain, security, persistence, and API architecture can be redesigned without depending on the retired F01 or F02 decisions.

### Scope

- Included: removal of the F01 Liquibase schema and schema-specific integration harness, retirement of the previous design and implementation plan, restoration of the generic Spring Boot smoke test and pre-F01 build configuration, closure of the unmerged F02 Firebase security pull request, deletion of its feature branch, and recreation of the local application schema as an empty database.
- Excluded: replacement architecture, product requirements, database model, authentication design, APIs, and feature implementation.
- Preserved: Java/Spring Boot/MySQL technology skeleton, CI and security workflows, PR documentation and JavaDoc standards, user-owned `mysql-compose.yaml` and static resources, and the external Firebase credential file.

### Acceptance criteria

- [x] No F01 Liquibase changelog or application-owned database table definition remains in the active branch.
- [x] No F02 authentication implementation is merged into the reset branch.
- [x] The previous design and feature roadmap are no longer presented as active decisions.
- [x] CI/security automation and PR documentation standards remain intact.
- [x] The local `money_split` schema is recreated without application tables.
- [x] User-owned untracked files and the external Firebase credential remain untouched.
- [x] Changed Java methods retain JavaDoc where applicable, and no important non-obvious logic or SQL is introduced without intent-focused documentation.

## What Was Implemented

### Application changes

- Restored the application to the generic Spring Boot project skeleton that existed before F01.
- Removed schema-specific Testcontainers support, tagged integration-test task wiring, and Liquibase migration tests.
- Removed the obsolete architecture document and backend implementation plan.
- Reworded the README to identify the repository as intentionally unimplemented pending redesign.

### Code documentation

- No production Java method or constructor was added or changed.
- The restored Spring context smoke test matches the documented pre-feature baseline.
- No business rule, algorithm, transaction, lock, or SQL query was introduced.

### API changes

| Method | Path | Change |
|---|---|---|
| N/A | N/A | No API existed on `master`; the unmerged F02 security boundary was retired |

### Database changes

| Migration | Change | Compatibility notes |
|---|---|---|
| `001-foundation.sql` | Removed the F01 application schema and seed catalog from the active codebase | This source rollback does not automatically drop an already-migrated shared database |

The local Docker-hosted `money_split` schema was separately dropped and recreated as an empty schema. No automatic destructive rollback is included in the application.

### Configuration and dependencies

- Restored the pre-F01 datasource-neutral `application.properties`.
- Restored the pre-F01 Gradle dependency lock and removed F01-only Testcontainers dependencies and task wiring.
- Restored the temporary CI Liquibase disable flag because no active changelog exists.
- Retained the previously approved CI and security workflows.

### Security and operational behavior

- Closed unmerged PR #4 and removed its local and remote feature branches so Firebase authentication is not part of the redesign baseline.
- Preserved the external Firebase service-account file outside the repository without reading, moving, or deleting it.
- Preserved unrelated user-owned untracked files.

## Technical Test Cases Implemented

### Unit tests

| Test case | Behavior verified |
|---|---|
| Spring context smoke test | The generic application context starts with the pre-feature configuration |

### Integration tests

| Test case | Behavior verified |
|---|---|
| Local schema inspection | The recreated `money_split` schema contains no application tables |

### API, security, and concurrency tests

| Test case | Behavior verified |
|---|---|
| N/A | No API, security boundary, or concurrent behavior remains to test |

### Validation evidence

| Command or check | Result |
|---|---|
| `.\gradlew.bat clean check --no-daemon --console=plain --warning-mode all` | Passed |
| Local MySQL `information_schema` table count | Passed; the recreated schema contains zero tables |
| Repository search for retired changelogs and F02 source | Passed; only historical PR documentation references remain |

## Compatibility and Migration Notes

- This is an intentional breaking reset of unshipped feature work.
- Shared or deployed databases are not modified automatically; operators must explicitly retire any F01-only schema after confirming that it contains no durable data.
- Git history and the historical PR records remain available for auditability.

## Deferred Work

- Elicit fresh product and non-functional requirements.
- Produce and approve a replacement architecture document.
- Produce a new sequence of small, independently testable backend features.
