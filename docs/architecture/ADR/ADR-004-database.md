# ADR-004: Database

## Context

Tour pricing and booking data must not live only in frontend pages.

## Decision

Use PostgreSQL with Flyway migrations.

## Consequences

- Database is source of truth.
- Schema changes are reviewable.
- Local Docker requires Postgres availability.
