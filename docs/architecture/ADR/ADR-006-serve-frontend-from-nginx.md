# ADR-006: Serve the frontend from Nginx beside the API, not a static host

Status: Accepted. Date: 2026-10-07.

## Context

We considered moving the React frontend to Netlify, keeping the API and database on the DigitalOcean droplet.
The frontend is not purely static. The bundled [nginx.conf](../../../apps/web/nginx.conf) does three jobs a static host
cannot:

- **Server-side includes (SSI).** For each page, Nginx asks the API for the title, description, canonical link and
  structured data and inserts them into the HTML before sending it. Search engines and link previews depend on this
  ([Search Metadata](../../operations/SEO.md)).
- **Same-origin API proxy.** The browser talks to one origin; Nginx forwards `/api/` to the API container. Session
  cookies (`SameSite=Lax`) and CSRF protection ([ADR-004](ADR-004-session-authentication-and-admin-workflows.md)) rely
  on this.
- **Edge protection.** Rate limiting of sign-in, registration and password-reset requests, security headers and the
  private-route content security policy.

The API and database need the droplet regardless of where the frontend lives.

## Decision

Keep serving the built frontend from the Nginx container on the same server as the API, behind one domain with HTTPS.

## Alternatives considered

- **Netlify (or similar) for the frontend, API on the droplet, separate domains.** Loses SSI, so every page shares the
  fallback title and description. Session cookies and CSRF become cross-site, requiring CORS and cookie changes in
  security-sensitive code. Sign-in rate limiting must be rebuilt. Adds a second platform to deploy and monitor.
- **Netlify with a proxy rule forwarding `/api/*` to the droplet.** Keeps one origin for cookies but still loses SSI
  and adds a hop and a second platform.

## Consequences

- One deployment target and one place to look when something fails.
- We provide what Netlify would have provided: HTTPS certificates, automated deploys and caching. HTTPS comes from a
  certificate on the droplet; automated deploys come from the pipeline in [ADR-007](ADR-007-build-images-in-ci.md).
- Static assets are served from a single region (Frankfurt). If load or distance becomes a problem, put a CDN such as
  Cloudflare in front of the same server rather than moving the frontend.
- Revisit if page metadata stops depending on the API (e.g. pre-rendered at build time) and sign-in moves to a
  separate identity provider.
