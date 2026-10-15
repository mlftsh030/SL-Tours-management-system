# Tour catalogue: implementation notes and open questions

Source: `SL_Tours_Online_Content.md` (sl-tours.com/tours, checked 4 October 2026). Applied by Flyway migration
`V22__tour_catalogue_from_published_site.sql`, which updates the nine existing tour records in place (by slug), so links,
bookings and enquiries that refer to them keep working and no duplicates are created.

## The nine offerings

| Offering | Page | Price | Duration | Group | Enquiry form |
|---|---|---|---|---|---|
| Cape Peninsula & Cape Point | /experiences/cape-peninsula-journey | R2,100 per person | Full day | Minimum 5 | Standard |
| Cape Town City & Heritage Tour | /experiences/cape-town-essentials | R900 per person | Half day | Minimum 5 | Standard |
| Township Cultural Experience | /experiences/cape-culture-experience | R950 per person | Half day | Minimum 5 | Standard |
| Cape Winelands Tour | /experiences/cape-winelands-escape | R1,800 per person | Full day | Minimum 5 | Standard |
| Garden Route Tour | /experiences/garden-route-journey | R6,500 per person | 3 days, 2 nights | Minimum 5 | Standard |
| Aquila Safari Day Tour | /experiences/aquila-safari-day-trip | R3,500 per person | Full day | Any size | Standard |
| West Coast Tour | /experiences/west-coast-tour | R2,900 per person | Full day | Minimum 5 | Standard |
| Airport Transfers | /experiences/airport-transfer | R1,300 per trip | Point to point | Private | Airport (flight, direction, destination, luggage) |
| Private & Custom Tours | /experiences/private-custom-tour | Quote on request | Flexible | Solo to large groups | Custom (dates, interests, group size) |

Images are the nine published files from `https://sl-tours.com/assets/images/tours/`, all retrieved successfully, stored in
`apps/web/public/assets/tours/` as optimised JPEGs (eight were identical to images already in the app; the township image was
new; the custom-tours PNG was converted to JPEG). No videos were available, so none were added.

## Decisions taken

- **City tour name:** published as "Table Mountain & Cape Town City Tour", but no mountain ascent or cableway ticket is listed.
  Following the brief, it is shown as "Cape Town City & Heritage Tour", mentions views of Table Mountain only, and says that
  going up the mountain and cableway tickets are not included. Rename it back once a mountain visit is confirmed.
- **Itineraries** are labelled as samples. The city and township itineraries say they are "for illustration only" and ask the
  customer to confirm timings; the published times are shown unchanged.
- **Booking wording:** tour cards and pages use "View Details", "Enquire" and "Send Enquiry". The "Book Online" links were
  removed from tour cards and pages because that flow records a request, not a confirmed booking. The `/book/:slug` page still
  exists but is no longer linked from the tours.
- **No invented terms:** each detail page says cancellation terms, deposits and child prices are not published and are confirmed
  with the quote. Missing details that affect a decision appear under "Practical information" with a request to confirm them.
- **Design:** the site keeps its established SL Tours design (colours, fonts, header, footer, package cards and the detail-page layout). Only the information from the published site was added: summary facts, labelled itineraries, practical information, the tour-specific enquiry forms, related tours and the business address.
- **Structured data:** `TouristTrip` with name, description, URL, image and provider only. No offers, ratings or availability,
  because prices are published website information rather than confirmed booking terms.

## Open questions for SL Tours

These are also stored as admin-only review notes on each tour (shown in Operations > Tours > Edit, never public).

1. **City tour:** confirm whether a Table Mountain visit is included. Confirm the real duration: it is advertised as two hours or a
   half day, the sample itinerary runs from 09:00 to after 11:15, and the page also says return by 11:00-11:30.
2. **Township tour:** confirm the total duration (drop-off around 11:00 conflicts with the 11:00 return drive) and which community
   (Langa or Khayelitsha) each booking visits.
3. **Cape Peninsula:** confirm whether the Cape Point funicular is included. Lunch is own account.
4. **Winelands:** estate names, lunch menu, tasting quantities, dietary arrangements, and provisions for children and guests who
   do not drink alcohol.
5. **Garden Route:** accommodation standard, single supplements, park admission, and whether a Botlierskop game drive is included
   (only time at the reserve is listed).
6. **Aquila:** whether the optional afternoon drive costs extra; the booked reserve package; age restrictions. Big Five sightings
   are described as possible, never guaranteed.
7. **West Coast:** return time, which "additional entrance fees" are covered, and seasonal access. Wildflowers are described as
   seasonal.
8. **Airport transfers:** passengers and luggage per vehicle, the area covered by R1,300, waiting charges beyond 60 minutes, child
   seats and accessibility. The price is per trip.
9. **All tours:** price validity, VAT, deposit and payment terms, cancellation and refund rules, weather cancellations, child
   prices and ages, accessibility and pickup boundaries.
