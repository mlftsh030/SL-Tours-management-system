# Migration Plan

## Preserve

- SL Tours brand name and logo.
- Phone: `+27 67 012 7776`.
- Email: `tours.st@outlook.com`.
- Existing social links.
- High-quality images from `upload/` and `images/`.
- Existing SEO page intent and tour names/prices.

## Replace

- Root static HTML runtime.
- Inline EmailJS/WhatsApp scripts.
- External booking links as the primary conversion path.
- Hardcoded tour/pricing duplication.

## Redirects

Recommended redirects:

- `/index.html` -> `/`
- `/our_tours.html` -> `/experiences`
- `/about-us.html` -> `/about`
- `/contact.html` -> `/contact`
- `/gallery.html` -> future gallery or `/`
- `/private.html` -> `/private-tours`

## Risks

- ActivityBridge may still hold live operational bookings; confirm before removing links publicly.
- Confirm image rights and compress/source optimize final assets.
- Validate SEO impact before replacing a live GitHub Pages deployment.
