# Money Split

A Java and Spring Boot backend project for personal money tracking and shared expense splitting.

[![CI](https://github.com/Bayvao/money-split/actions/workflows/ci.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/ci.yml)
[![CodeQL](https://github.com/Bayvao/money-split/actions/workflows/codeql.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/codeql.yml)
[![Security Scan](https://github.com/Bayvao/money-split/actions/workflows/security-scan.yml/badge.svg)](https://github.com/Bayvao/money-split/actions/workflows/security-scan.yml)

## Current state

The backend has been returned to a pre-feature baseline so its architecture can be redesigned. It currently provides:

- a Java 25 and Spring Boot 4.1 project skeleton;
- declared Spring Data JDBC, Liquibase, and MySQL dependencies for the confirmed technology stack;
- CI, code analysis, dependency review, and vulnerability scanning workflows; and
- the pull request documentation standard.

No product feature, authentication boundary, database migration, or application-owned table is implemented. A replacement architecture and implementation plan must be approved before feature development resumes.

## Project governance

- [Pull request documentation standard](docs/prs/README.md)
- [CI and security automation](docs/security-ci.md)
