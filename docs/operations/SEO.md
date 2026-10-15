# Search Metadata and Crawlable Content

## How public pages are rendered

`apps/web/index.html` contains two Nginx server-side includes:

- `<!--# include virtual="/__seo/head?path=$request_uri" ... -->` in `<head>`: title, meta description, canonical link,
  Open Graph tags and structured data for the requested page.
- `<!--# include virtual="/__seo/body?path=$request_uri" ... -->` inside `#root`: the page's core content (heading,
  description, key facts and links) as plain HTML.

`/__seo/` is an internal Nginx location proxied to `GET /api/v1/seo/head|body`. React replaces the `#root` content when
it starts, so visitors see the normal site; crawlers and link previews get meaningful HTML without running JavaScript.

If the API is unavailable, Nginx uses the fallback block in `index.html` (generic title and description, no canonical
link) and the page still loads.

## Refresh behaviour

Head, body and sitemap are generated from the database on every request, with `Cache-Control: no-cache`. Publishing,
unpublishing or editing a tour or activity changes the HTML and sitemap immediately; there is no build step or cache to
clear. If a CDN is added in front of the site, configure it to revalidate HTML and `/sitemap.xml` (or purge on publish).

## Sitemap

`/sitemap.xml` (proxied to `GET /api/v1/seo/sitemap.xml`) lists the public static pages, every **ACTIVE** tour
(`/experiences/{slug}`) and every **PUBLISHED** activity (`/explore/{slug}`), with `lastmod` from the record's last update.
Draft and archived records are never listed. `robots.txt` points to it.

## Indexing rules

- Private routes (`/operations`, `/driver`, `/login`, `/register`, `/account`, `/book/...`) and unknown paths get
  `<meta name="robots" content="noindex">` and no canonical link.
- Canonical URLs use `SLTOURS_PUBLIC_BASE_URL` (default `https://sl-tours.com`) and drop query strings.

## Structured data

Only where it matches what the page visibly shows:

| Page | Types |
|---|---|
| Home | `TravelAgency` (organisation details shown in the footer and contact pages) |
| Tour | `TouristTrip` (name, description, provider) and `BreadcrumbList` |
| Activity | `TouristAttraction` (name, description, address, image when shown; `isAccessibleForFree` only when the source says it is free) and `BreadcrumbList` |

Prices are **not** marked up: they depend on visitor residency, age, day and session, and change at the source. The old
hard-coded `makesOffer` prices in `index.html` were removed because they duplicated catalogue pricing outside the API.

FAQs are shown on activity pages to help visitors. They are deliberately **not** marked up as `FAQPage`: Google limits FAQ
rich results to a small set of authoritative sites, and no ranking or rich-result outcome is promised.
