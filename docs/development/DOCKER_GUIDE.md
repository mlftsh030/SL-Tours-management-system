# Docker Guide

## Start

```bash
cp .env.example .env
docker compose up --build
```

## Services

| Service | Port | Purpose |
| --- | --- | --- |
| `web` | `8080` | Nginx-served React app and `/api` proxy |
| `api` | `8081` -> container `8080` | Spring Boot API |
| `postgres` | `5432` | PostgreSQL development database |

## Common Commands

```bash
docker compose ps
docker compose logs -f api
docker compose down
docker compose down -v
docker compose up --build -d
```

## Volumes

| Volume | Contents |
| --- | --- |
| `postgres-data` | PostgreSQL data |
| `media-data` | Private uploads mounted at `/var/lib/sltours/media` in the API container (inspection photos, selfies, driver documents, vehicle and website images) |

The API container runs as an unprivileged user and owns the media directory. Back up both volumes together; see [Media Storage](../operations/MEDIA_STORAGE.md).

## Database

Flyway runs automatically on API startup (migrations V1–V11). Use `docker compose down -v` to reset the database **and media** volumes and reseed.

## Development demo data (dev profile only)

On startup with `SPRING_PROFILES_ACTIVE=dev` the API:

- creates driver sign-ins for the seeded drivers: `sipho.driver@sltours.local` (verified, with a demo assignment) and `daniel.driver@sltours.local` (pending verification), password `SLTOURS_DEV_DRIVER_PASSWORD` (default `driver-demo-pass`);
- lists the seeded vehicles on the website with amenities;
- shows the seeded **sample** private-trip rates, labelled “Sample rate · development only”.

None of this runs outside the dev profile.

## Operations settings

| Variable | Default | Purpose |
| --- | --- | --- |
| `SLTOURS_COLLECTION_APPROVAL_REQUIRED` | `true` | Collections wait for admin release |
| `SLTOURS_INSPECTION_MIN_PHOTOS` | `4` | Vehicle photos per inspection |
| `SLTOURS_COLLECTION_OPENS_BEFORE` | `12h` | How long before the trip a driver may collect the vehicle |
| `SLTOURS_IDLE_TIMEOUT` | `30m` | Sign-out after inactivity |
| `SLTOURS_MEDIA_ROOT` | `/var/lib/sltours/media` | Upload storage path in the container |
| `SLTOURS_DOCUMENT_LINK_BASE_URL` | `http://localhost:8080` in Compose; `SLTOURS_PUBLIC_BASE_URL` otherwise | Address used in customer invoice/quotation links |
| `SLTOURS_DOCUMENT_LINK_DAYS` | `14` | How long a customer document link works (`SLTOURS_DOCUMENT_LINK_MAX_DAYS`, default 90, caps it) |

Invoices and quotations: see [Invoicing](../billing/INVOICING.md).

## Smoke Checks

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8081/api/v1/tours
curl http://localhost:8081/api/v1/private-trips/vehicles
# Open http://localhost:8080/operations and use the staff sign-in form
# Open http://localhost:8080/driver and sign in as a demo driver
# Open http://localhost:8080/book-private-trip for the public private-trip flow
```

The API debug port binds to loopback. Compose defaults to the dev profile and HTTP cookies; use strong secrets, a production profile and secure cookies behind HTTPS in shared deployments. See [Admin Guide](ADMIN_GUIDE.md).
