# CI/CD Pipeline

How a change gets from a developer's machine to the server, and what is checked on the way. The reasons for this
design are in [ADR-007](../architecture/ADR/ADR-007-build-images-in-ci.md).

- **CI (continuous integration):** every change is automatically checked and tested before it can be merged.
- **CD (continuous delivery):** a change that passed CI is packaged once and deployed the same way every time.

A required check that fails blocks the change. Tests use synthetic data and test credentials only; CI never connects
to the server or its database.

## Stages

| # | Stage | What it checks | Runs on | Status |
|---|---|---|---|---|
| 1 | Code checks | Frontend types; Java compiles; frontend production build | Every pull request and push to `main` | **Done** ([ci.yml](../../.github/workflows/ci.yml)) |
| 2 | Automated tests | 91 frontend tests; 167 API tests against a throwaway PostgreSQL (Testcontainers) | Every pull request and push to `main` | **Done** |
| 3 | Security checks | Dependencies with known HIGH/CRITICAL vulnerabilities; secrets committed in files | Every pull request and push to `main` | **Done**; scheduled scan to add |
| 4 | Build release | Build API and web images once, tag with the commit ID, push to GitHub Container Registry | After stages 1-3 pass on `main` | To do. CI currently only proves the images build |
| 5 | Test deployment | Server pulls those exact images; smoke tests check API health and key pages | Each release | To do |
| 6 | Release gate | Successful checks plus a deliberate approval | Production releases | To do |
| 7 | Production deployment | Back up database and media, check migrations, deploy, verify health | Approved releases | To do. Needs HTTPS and production settings first |
| 8 | Monitor and recover | Uptime alerts, error monitoring, known-good previous release, tested restore | Continuously | To do |

## What runs today

The [CI workflow](../../.github/workflows/ci.yml) has four jobs. The first three run in parallel; the last waits for
the frontend and API jobs.

| Job | Commands | Equivalent on your machine |
|---|---|---|
| Frontend | `npm ci`, then typecheck, test and build the `@sltours/web` workspace | `npm ci && npm --workspace @sltours/web run typecheck && npm --workspace @sltours/web run test` |
| API | `mvn -B verify` in `apps/api` (needs Docker for Testcontainers) | `cd apps/api && mvn verify` |
| Security | Trivy scans dependencies and files | See [Running the security scan locally](#running-the-security-scan-locally) |
| Docker images | `docker compose build api web` | Same |

If a job fails, open the run in the repository's **Actions** tab, select the failed job and read the first red step.
When API tests fail, the job keeps their reports for 7 days as the `api-test-reports` artifact.

## Security findings

The security job fails on a HIGH or CRITICAL vulnerability that has a fixed version available. To resolve one:

1. **Upgrade.** Prefer upgrading the direct dependency (for example Spring Boot). If that version still contains the
   flaw, pin the fixed version of the indirect library in `apps/api/pom.xml` `<properties>` with a comment naming the
   CVEs, as done for Tomcat, Jackson and the PostgreSQL driver. Remove the pin once Spring Boot manages that version.
2. **Accept, only when the flaw cannot affect this system.** Add the ID to [.trivyignore](../../.trivyignore) with
   the reason and an `exp:` date. After that date the check fails again so the decision is reviewed.

Run the full tests after any dependency change.

### Running the security scan locally

```bash
mvn -B -q -f apps/api/pom.xml dependency:resolve
docker run --rm -v "$PWD":/src:ro -v "$HOME/.m2":/root/.m2:ro aquasec/trivy:0.75.0 fs --offline-scan \
  --scanners vuln,secret --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 \
  --ignorefile /src/.trivyignore --skip-dirs node_modules --skip-dirs apps/api/target /src
```

## Cost

GitHub Actions on standard Linux runners is free for public repositories and includes 2,000 minutes per month for
private repositories on GitHub Free. Keep the Actions spending limit at zero so overages are blocked rather than
billed. Stored artifacts are kept small and expire after 7 days.

## Known gaps

- The web Dockerfile runs `npm install` without a lock file (the lock file is at the repository root), so the image
  can contain different dependency versions from the ones CI tested.
- There is no linter beyond the TypeScript compiler (`lint` runs `typecheck`).
- `main` is not yet protected: GitHub branch protection should require these checks and a pull request.
- The secret scan checks current files, not Git history.
