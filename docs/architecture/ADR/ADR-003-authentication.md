# ADR-003: Authentication

## Context

Operations must be protected immediately, but full auth is larger than the foundation slice.

## Decision

Use Spring Security with environment-backed HTTP Basic for `/api/v1/admin/**` in local/foundation phase.

## Consequences

- Simple protected admin API now.
- Must be replaced with production-grade customer/staff authentication before launch.
