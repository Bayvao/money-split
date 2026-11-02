# Pull Request Documentation

Every pull request must include one dedicated change record in this directory. The record documents the delivered feature, the implementation, the technical test cases added for that change, and the commit message.

## Workflow

Before starting a change:

```powershell
git fetch origin
git switch master
git pull --ff-only origin master
git switch -c <change-branch>
```

Then:

1. Copy `_template.md` to `docs\prs\<feature-slug>.md`.
2. Use a short, unique, lower-kebab-case feature slug, such as `authenticated-group-api.md`.
3. Complete the document as the implementation evolves; do not reconstruct it only at the end.
4. Add the GitHub PR number and URL when they become available.
5. Keep the record in the same PR as the code or documentation it describes.
6. Update the record whenever review changes the feature, implementation, or tests.
7. Before merge, update `Commit Message` with the exact final or squash commit message. If the PR will retain multiple commits, list their exact messages in order.

Do not combine unrelated pull requests into one change record. A follow-up PR receives a new file even when it modifies the same feature.

## Mandatory content

Every change record must clearly contain these sections:

1. **Feature** - user or system behavior delivered, its scope, and acceptance criteria.
2. **What Was Implemented** - code, API, data model, configuration, security, and operational changes actually made.
3. **Technical Test Cases Implemented** - automated test cases, their level, and the behavior or risk each case verifies.
4. **Commit Message** - exact final/squash commit message, or the ordered commit messages when multiple commits will be retained.

The supplied template also captures schema/API compatibility, validation evidence, and deferred work. Remove optional rows that do not apply, but do not remove the four mandatory sections.

For a documentation-only PR, the technical-test section must explicitly state that no runtime tests were added and list any documentation checks performed. It must not claim application test coverage.

## Review rule

A pull request is not complete when its change record is missing, describes intended work instead of actual work, lists test commands without identifying the technical cases they cover, or omits the exact commit message.
