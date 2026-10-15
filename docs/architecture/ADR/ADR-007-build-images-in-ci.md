# ADR-007: Build and test in GitHub Actions; the server only pulls images

Status: Proposed. Date: 2026-10-07.

## Context

The test droplet (`ubuntu-s-1vcpu-1gb-fra1`: 1 vCPU, 1 GB RAM) builds the images itself with
`docker compose build`. Compiling the Java API with Maven and the frontend with Node at the same time exhausted
memory: `top` showed `kswapd0` (the kernel process that frees memory) using about 22% CPU, and the build stalled.
Building one service at a time works around this, but:

- builds on the server compete for memory with the running application;
- the images are built with tests skipped (`-DskipTests`), so nothing proves the deployed code passed its tests;
- each deployment depends on someone running commands by hand over SSH.

## Decision

GitHub Actions checks, tests and builds every change. Images are built once, tagged with the Git commit ID and stored
in GitHub Container Registry (GHCR). The server deploys by pulling those exact images and restarting
(`docker compose pull && docker compose up -d`). It never compiles code.

The stages are described in [CI/CD Pipeline](../../operations/CI_CD_PIPELINE.md).

## Alternatives considered

- **Keep building on the droplet, add swap.** Cheapest change and a useful safety net, but builds stay slow, still
  compete with the live application and still skip tests.
- **Resize the droplet.** Costs more every month to solve a problem that only exists during builds.
- **A hosted platform (e.g. App Platform, Render).** Less to operate, but more expensive for API plus database and
  less transparent while learning how deployment works.

## Consequences

- Deploys are fast and use little memory on the server.
- The image that passed the tests is the image that runs; a commit ID identifies exactly what is deployed.
- Rolling back the application means starting the previous image tag. Database migrations are not rolled back this
  way, so migrations must stay compatible with the previous release, and a backup runs before each deploy.
- Cost: free within GitHub's allowances (2,000 Actions minutes per month for private repositories on GitHub Free;
  GHCR storage). Set a spending limit of zero so overages are blocked rather than billed.
- The server needs a read-only GHCR token, and GitHub needs an SSH key for deploying. Both are secrets and are stored
  in GitHub/the server, never in Git.
