# <Feature Name>

| Field | Value |
|---|---|
| Status | Draft |
| Branch | `<change-branch>` |
| Pull request | Pending |
| Date | YYYY-MM-DD |
| Related plan item | `<plan milestone or N/A>` |

## Commit Message

Use the exact final or squash commit message. If multiple commits will be retained, list their exact messages in order.

```text
<type>(<scope>): <concise description>
```

## Feature

Describe the user-visible or system capability delivered by this pull request.

### Scope

- Included:
- Excluded:

### Acceptance criteria

- [ ] State observable behavior that must be true.
- [ ] Every new or changed Java method and constructor has JavaDoc, and important non-obvious logic or SQL has intent-focused comments.

## What Was Implemented

### Application changes

- Describe the application and domain behavior actually implemented.

### Code documentation

- Confirm that every new or changed Java method and constructor has JavaDoc describing its responsibility and contract.
- Identify the important business rules, algorithms, transaction/lock ordering, and SQL queries whose intent or mechanics are explained inline, or state `N/A`.

### API changes

| Method | Path | Change |
|---|---|---|
| N/A | N/A | No API change |

### Database changes

| Migration | Change | Compatibility notes |
|---|---|---|
| N/A | No database change | N/A |

### Configuration and dependencies

- Describe added or changed configuration and dependencies, or state `None`.

### Security and operational behavior

- Describe authorization, validation, logging, metrics, transaction, locking, and failure-handling changes, or state `None`.

## Technical Test Cases Implemented

List test cases, not only test files or commands.

### Unit tests

| Test case | Behavior verified |
|---|---|
| N/A | No unit test required |

### Integration tests

| Test case | Behavior verified |
|---|---|
| N/A | No integration test required |

### API, security, and concurrency tests

| Test case | Behavior verified |
|---|---|
| N/A | No API, security, or concurrency test required |

### Validation evidence

| Command or check | Result |
|---|---|
| `<command>` | `<result>` |

## Compatibility and Migration Notes

- State backward-compatibility, rollout, rollback, and data-migration considerations.

## Deferred Work

- List deliberately excluded follow-up work, or state `None`.
