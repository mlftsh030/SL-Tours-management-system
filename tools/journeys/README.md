# Journey checks

Browser checks that use the site the way visitors do: by keyboard, on a phone-sized screen, with forms, emails and
failures. They complement the unit tests (`npm test`) and the route smoke test (`tools/smoke/routes.sh`).

**Run them only against a local or disposable stack.** They create enquiries, a private-trip request and a customer
account, all with `@example.test` addresses, and read emails from Mailpit. Never point them at production.

## Running

```sh
docker compose up -d --build          # local stack with Mailpit
cd tools/journeys && npm ci           # once
npm run all                           # every check; or e.g. npm run accounts
```

| Setting | Default | Meaning |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | Site to test |
| `MAILPIT_URL` | `http://localhost:8025` | Local mail catcher |
| `CHROME_PATH` | `/usr/bin/google-chrome` | Chrome or Chromium to drive |
| `RUN_ID` | current time | Added to test email addresses so runs do not collide |
| `SHOTS_DIR` | not set | Folder for layout screenshots |

Each check prints `ok` or `FAIL` per step and exits non-zero if anything failed.

## What each check covers

| Check | Covers |
|---|---|
| `layout` | 14 public pages at 320–1440 px: no sideways scrolling, nothing past the screen edge, tap targets of at least 24 × 24 px, one `h1` |
| `keyboard` | Mobile menu (focus trap, Escape), hero carousel (pause, slide buttons, hidden slides skipped), gallery lightbox, tour filters, Explore "Show all" |
| `enquiries` | Standard, airport and custom-tour enquiries: validation, network failure, retry with the same key, edited retry with a new key, pending state, receipt |
| `booking` | `/book/:slug`: starts at the tour's minimum group, refuses fewer guests, confirms a request with a reference |
| `privatetrip` | Search in Cape Town time, stale results, a price change (409) and retry, quote fallback. Needs priced vehicles (dev sample rates) |
| `accounts` | Guest enquiry, registration, verification email and linking, duplicate address, password reset signing out other sessions, single-use links |
| `translation` | A chosen language across route changes and the sign-in page; the selector after returning. Needs internet access |
| `reviews` | Review widget blocked (fallback) and loaded. The loaded case needs internet access |

## Cleaning up

The records stay in the local database and are recognisable by their `@example.test` addresses. To start clean, reset
the local database with `docker compose down -v`, which deletes **all** local data.
