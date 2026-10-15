# HTTPS, Staging and Moving sl-tours.com

How to put the application behind HTTPS, keep a staging copy out of search results, and later move the public
sl-tours.com domain from the previous site to this application. Nothing here has been run against a real domain yet:
it was verified locally (see [Verification](#verification)).

## How it fits together

```
visitor ──HTTPS──> TLS proxy ──HTTP──> web: Nginx (port 8080) ──> api: Spring Boot ──> postgres
                   certificates          pages, redirects, 404s, rate limits
```

- HTTPS is provided by a proxy in front of the `web` container, for example Caddy with Let's Encrypt or a Cloudflare
  Tunnel. This snapshot does not include proxy configuration; the original development repository has a tested Caddy
  setup.
- Expose only the proxy to the internet. The database, mail catcher, API and plain-HTTP web port should not be
  reachable from outside the server.
- Nginx trusts `X-Forwarded-For` and `X-Forwarded-Proto` only from addresses listed in its edge files
  ([apps/web/edge](../../apps/web/edge)). The image trusts no proxy by default. Behind a proxy, mount a folder over
  `/etc/nginx/edge` that trusts only the proxy's address. This keeps the sign-in rate limits per visitor rather than
  shared, and a visitor cannot fake their address.
- Set `SESSION_COOKIE_SECURE=true` and use `https://` for `CORS_ALLOWED_ORIGINS`, `SLTOURS_ACCOUNT_LINK_BASE_URL` and
  `SLTOURS_DOCUMENT_LINK_BASE_URL`.

## Settings per environment

| Setting | Local | Staging (e.g. `staging.sl-tours.com`) | Production (`sl-tours.com`) |
|---|---|---|---|
| TLS proxy | none | in front of `web` | in front of `web` |
| `SESSION_COOKIE_SECURE` | `false` | `true` | `true` |
| `SPRING_PROFILES_ACTIVE` | `dev` | `production` | `production` |
| `SLTOURS_SEO_INDEXING` | blank | `false` | `true` |
| `SLTOURS_PUBLIC_BASE_URL` | default | `https://sl-tours.com` (canonicals name production) | `https://sl-tours.com` |
| Email | Mailpit | real SMTP, or accounts cannot verify | real SMTP |
| Sample private-trip rates | shown, labelled | refused at startup | refused at startup |

The `production` profile refuses to start with sample rates, the mock assistant, the local mail catcher, or no explicit
`SLTOURS_SEO_INDEXING`. Staging should run it too, so that staging behaves like production.

With search indexing off, every page carries `noindex`, `/sitemap.xml` answers 404 and `/robots.txt` does not mention
it. Canonical links still name `https://sl-tours.com`, so nothing staging-specific can leak into search results.

## 1. Staging on HTTPS

Prerequisites:

1. A staging hostname, e.g. `staging.sl-tours.com`, routed to the server (a DNS record, or a tunnel route).
2. A TLS proxy forwarding that hostname to the `web` container on port 8080.
3. SMTP credentials if staging accounts must receive emails.

Deploy, on the server in the project folder:

```bash
# Back up first (database and uploads): see BACKUP_AND_RECOVERY.md
tools/db/backup.sh

# In .env: SPRING_PROFILES_ACTIVE=production, SESSION_COOKIE_SECURE=true, SLTOURS_SEO_INDEXING=false,
# CORS_ALLOWED_ORIGINS=https://staging.sl-tours.com, real passwords, SMTP settings
docker compose up -d --build
tools/smoke/routes.sh https://staging.sl-tours.com false
```

`docker compose down` without `-v` keeps the database and upload volumes. Run the commands from the same folder name
as before: Compose names volumes after the folder.

## 2. Moving sl-tours.com to this application

Do this only after staging has been checked and the owner decisions in [Owner decisions](OWNER_DECISIONS.md) marked as
launch blockers are resolved.

1. **Lower the DNS TTL** of `sl-tours.com` to 300 seconds a day in advance, so the switch and any rollback spread fast.
2. **Back up** (`tools/db/backup.sh`) and note the current image tags.
3. **Configure production**: point the TLS proxy at `sl-tours.com`, set `SLTOURS_SEO_INDEXING=true` and the
   `https://sl-tours.com` origin in `.env`, then `docker compose up -d`. A proxy that obtains certificates itself can
   only do so once DNS points here, so expect certificate errors for the few minutes after step 4.
4. **Switch DNS**: point `sl-tours.com` (and `www`, if used) to the server or proxy.
5. **Verify**: `tools/smoke/routes.sh https://sl-tours.com true`. Then open the old addresses below in a browser and
   submit a test enquiry using an address you control.
6. **Search Console**: submit `https://sl-tours.com/sitemap.xml` and watch the coverage report for errors over the
   next weeks.

Old addresses are redirected permanently (HTTP 301) by [nginx.conf](../../apps/web/nginx.conf):

| Old address | New page |
|---|---|
| `/tours` | `/experiences` |
| `/tours/cape-point` | `/experiences/cape-peninsula-journey` |
| `/tours/city-tour` | `/experiences/cape-town-essentials` |
| `/tours/winelands-tour` | `/experiences/cape-winelands-escape` |
| `/tours/township-tour` | `/experiences/cape-culture-experience` |
| `/tours/garden-route` | `/experiences/garden-route-journey` |
| `/tours/airport-transfers` | `/experiences/airport-transfer` |
| `/faq` | `/contact` (no FAQ page yet; see owner decisions) |
| `/testimonials` | `/#reviews` (reviews section on the homepage) |
| `/our_tours.html`, `/about-us.html`, `/contact.html`, `/gallery.html` | matching new pages |

Any other unknown address returns HTTP 404 with a "Page not found" page that is marked `noindex`.

## Rollback

| Problem | Action |
|---|---|
| Certificate not issued | Check that DNS points here, the proxy is reachable and its logs. The old site keeps serving until DNS changes. |
| New site broken after the DNS switch | Point DNS back to the previous host. With TTL 300 this takes effect within minutes. |
| Application release broken | Start the previous image tags (stage 4 of the [pipeline](CI_CD_PIPELINE.md) records them). |
| Data damaged by a migration | Stop the API and restore the pre-deploy backup ([Backup and recovery](BACKUP_AND_RECOVERY.md)). Older images cannot run against a newer schema. |

This release adds one migration, `V26__fleet_vehicle_model_option.sql`: a nullable `vehicles.vehicle_option_slug` column
and an index. It only adds, so the previous application can run on the migrated database. Tested on 9 October 2026:
the API from commit `ad000b8` (before this release) started against a database at version 26, Flyway reported
"Schema is up to date", and the tours API answered normally. Rolling back the application therefore needs no database
restore; staff links between fleet vehicles and models stay in the column and are used again after a redeploy.

## Verification

Done locally on 2026-10-08 in the original development repository, with Caddy as the TLS proxy and
`SITE_ADDRESS=localhost`, for which Caddy issues certificates from its own local authority instead of Let's Encrypt.
Checks used that authority's root certificate; certificate verification was not switched off.

- HTTP → HTTPS redirect (308), certificate verified, `Strict-Transport-Security` present, session cookie `Secure`.
- `tools/smoke/routes.sh https://localhost false`: all page, 404, redirect, robots and sitemap checks passed.
- A forged `X-Forwarded-For` header was ignored: Nginx logged the real peer address.
- With the API stopped, pages still load (built-in titles); unknown pages then return 200, as nothing can check them.

Not yet verified: a real public certificate, the server firewall, and DNS. These need the domain.
