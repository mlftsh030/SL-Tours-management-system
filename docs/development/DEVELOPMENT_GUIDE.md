# Development Guide

## Prerequisites

- Node 20+
- npm 10+
- Java 21
- Maven 3.8+
- Docker and Docker Compose

## Install

```bash
npm install
```

## Frontend

```bash
npm run web:dev
npm --workspace @sltours/web run typecheck
npm --workspace @sltours/web run test
```

## Backend

```bash
mvn -f apps/api/pom.xml test
mvn -f apps/api/pom.xml spring-boot:run
```

## Environment

Copy `.env.example` to `.env` for Docker usage. Local Maven defaults point to `localhost:5432`.

## Dependency Security

Run:

```bash
npm audit --omit=dev
```

Dev build tooling may report advisories separately; production dependency audit should stay clean.
