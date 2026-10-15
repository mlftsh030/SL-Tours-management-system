# ADR-002: Backend Framework

## Context

SL Tours needs bookings, quotes, operations, payments, customer trip data, and future integrations.

## Decision

Use Java 21 and Spring Boot 3 as a modular monolith.

## Consequences

- Strong fit for validation, security, JPA, OpenAPI, and long-lived business domains.
- Avoids premature microservices.
- Requires JVM deployment and disciplined package boundaries.
