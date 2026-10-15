# Performance and Caching

How the web container delivers files, and how page speed was measured. Configuration lives in
[nginx.conf](../../apps/web/nginx.conf).

## Cache and compression policy

| What | Example | `Cache-Control` | Why |
|---|---|---|---|
| Built scripts and styles | `/assets/index-D9YZY2KH.js` | `public, max-age=31536000, immutable` | The name contains a content hash, so a new release gets a new name. Browsers never need to ask again. |
| Photos, logos and other files in `public/` | `/assets/home/slider-1.webp` | `public, max-age=86400` | They keep their names when replaced, so browsers check again (ETag) after a day. |
| Pages (HTML) | `/experiences` | `no-cache` | Revalidated on every visit, so a release is picked up immediately. |
| Private pages | `/account`, `/operations`, `/login` | `no-store` | Personal or staff data must not stay in a browser or proxy cache. |

Scripts, styles, JSON, SVG, XML and HTML are sent gzip-compressed when the browser asks (`gzip_vary` tells caches
that the response depends on `Accept-Encoding`). Photos are already compressed and are sent as they are.

Every location that sets its own `add_header` also includes
[security-headers.conf](../../apps/web/security-headers.conf): in Nginx, one `add_header` in a location discards all
headers inherited from the server block.

Only the homepage preloads its hero photo; other pages preload nothing they do not use (`SeoService`, tested in
`SeoIndexingTest.onlyTheHomepagePreloadsTheHeroImage`).

## Measuring

Run against the local Docker stack, built from the branch being measured (`docker compose up -d --build`):

```sh
# Lighthouse 12, mobile form factor, default simulated throttling (Moto G Power, slow 4G), performance only.
CHROME_PATH=/usr/bin/google-chrome npx -y lighthouse@12 http://localhost:8080/experiences \
  --chrome-flags="--headless=new --no-sandbox" --only-categories=performance --form-factor=mobile \
  --output=json --output-path=experiences.json

# Bytes on the wire with compression.
curl -s -o /dev/null -H 'Accept-Encoding: gzip' -w '%{size_download}\n' http://localhost:8080/assets/index-<hash>.js
```

Run each route at least twice; scores move by a few points between runs.

## Results, 8–9 October 2026

Same machine, same Docker stack and Lighthouse settings before and after. "After" is two runs; ranges are shown.

| Route | Measure | Before | After |
|---|---|---|---|
| `/experiences` | Lighthouse performance score | 52 | 61–62 |
| | Largest Contentful Paint (simulated) | 10.8 s | 6.8–7.0 s |
| | First Contentful Paint (simulated) | 6.4 s | 3.6 s |
| | Total Blocking Time | 330 ms | 220–250 ms |
| | Bytes transferred | 1611 KiB | 836 KiB |
| `/` (homepage) | Lighthouse performance score | 30 | 60–61 |
| | Largest Contentful Paint (simulated) | 16.6 s | 8.8–8.9 s |
| | First Contentful Paint (simulated) | 6.4 s | 3.6–3.7 s |
| | Total Blocking Time | 1,460 ms | 190–220 ms |
| | Bytes transferred | 4048 KiB | 2165 KiB |
| Main script | Bytes on the wire | 516,060 (not compressed) | 103,455 (gzip) |
| `/experiences` HTML | Bytes on the wire | 4,175 | 1,645 (gzip) |

What changed: gzip, cache headers, the homepage-only hero preload, public pages other than home and tours (Explore,
gallery, private trips, planners, sign-in and others) loaded as separate scripts when first visited, hero and banner photos converted to WebP, six oversized tour photos re-encoded (same size and name, quality
82), and later slider photos loaded only when their slide is next.

**What these numbers do not show:** these are lab measurements on one machine with simulated throttling, not Core Web
Vitals from real visitors. The homepage's LCP (about 9 s on simulated slow 4G) is the hero photo itself; it remains
the main cost. Field data needs the site on its real domain (for example the Chrome UX Report or Search Console).
