# Architecture Decision Records

An ADR records one significant decision: the problem, what we chose, what we rejected and what it costs us. Code shows
*what* the system does; ADRs explain *why* it is that way, so a later change can be made knowingly rather than by
accident.

## When to write one

Write an ADR when a choice is hard to reverse, affects several parts of the system, or is one you might be tempted to
undo later without remembering the reason. Examples: hosting, authentication, database, build and deployment approach.
Routine implementation choices do not need one.

## Index

| ADR | Decision | Status |
|---|---|---|
| [001](ADR-001-frontend-framework.md) | React, TypeScript and Vite for the frontend | Accepted |
| [002](ADR-002-backend-framework.md) | Java 21 and Spring Boot modular monolith | Accepted |
| [003](ADR-003-authentication.md) | HTTP Basic for the admin API during the foundation phase | Superseded by 004 (session) |
| [004](ADR-004-database.md) | PostgreSQL with Flyway migrations | Accepted |
| [004](ADR-004-session-authentication-and-admin-workflows.md) | Session authentication and administration workflows | Accepted |
| [005](ADR-005-file-storage.md) | Private media volume behind a storage abstraction | Accepted |
| [006](ADR-006-serve-frontend-from-nginx.md) | Serve the frontend from Nginx beside the API, not a static host | Accepted |
| [007](ADR-007-build-images-in-ci.md) | Build and test in GitHub Actions; the server only pulls images | Proposed |

Two records share the number 004. They are kept as they are so existing links keep working; new records continue from
the highest number.

## Statuses

- **Proposed**: agreed in principle, not yet implemented.
- **Accepted**: implemented, or the current rule.
- **Superseded by NNN**: replaced by a later decision. Do not edit or delete old records; add a new one and link both.

## Template

Copy this into `ADR-NNN-short-title.md`:

```markdown
# ADR-NNN: Title

Status: Proposed | Accepted | Superseded by ADR-NNN. Date: YYYY-MM-DD.

## Context

The problem and the facts that constrain the choice. What would happen if we did nothing?

## Decision

What we chose, stated plainly.

## Alternatives considered

Each serious option and why it was not chosen.

## Consequences

What becomes easier, what becomes harder, what we must now do, and what would make us revisit this.
```
