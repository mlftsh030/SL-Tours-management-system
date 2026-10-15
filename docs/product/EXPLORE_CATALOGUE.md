# Explore Cape Town Catalogue and Day Planner

Phase 2 of the product brief. Sources and verification gaps: [Activity Research, October 2026](ACTIVITY_RESEARCH_2026-10.md).

## What is stored

| Data | Detail |
|---|---|
| Activity | Category, area, address, official site, typical duration, schedule type, weather dependency, indoor, minimum age, accompaniment rule, booking notes, price status, internal verification notes, last verified date and verifier |
| Sources | URL, publisher, type (official operator, public authority, tourism authority, other), what it confirms, date checked |
| Price rules | Label, audience, age range, residency, unit (person, vehicle, group, item), amount in cents, days, session times, conditions, effective from/to, estimate default, source, date checked |
| Extras | Mandatory or optional additional costs with unit, conditions, effective dates and source |
| Hours | Seasonal (MM-DD ranges, may wrap the new year) and day-specific opening hours with last entry, or bookable sessions |
| FAQs | Visitor questions answered from the sources |

## Publication rules

An activity can be published only when it has an official operator or public-authority source, was verified within the
last 180 days, has an address, has hours or sessions when it uses them, and every price rule cites a source. A published
activity cannot be saved into a state that breaks these rules; unpublish it to keep editing. Candidates that cannot be
verified from an accessible official page stay drafts.

`price_status` distinguishes **verified** prices, prices **set at booking**, **not published** and **free**. Only verified
prices are used as costs; the others appear as "not verified" in itineraries.

## Uncertainty and freshness

Official information is not always complete or consistent. The catalogue records that explicitly, and plans never present
it as confirmed:

| Situation | Stored as | Effect in plans and pages |
|---|---|---|
| Official sources conflict, or eligibility is undefined | `uncertainty_note` on a price, extra or hours row | Price shown as an **estimate**; hours flagged "not confirmed"; note shown on the page |
| Sessions sampled for particular days | Rows limited to the weekdays seen, optional `valid_from`/`valid_to` | Not used for other days; planner says "no verified sessions for Wednesdays" |
| Prices past their end date | Effective dates | **Not verified**; the last published amount stays visible, labelled as ended |
| Participation limits not published | `participation_rules` UNKNOWN / PARTLY_KNOWN with a note | Stop caution; plan says suitability is not confirmed |
| Information past its re-check period | Per-row check dates | Prices become estimates; hours and sessions carry cautions |

Re-check periods are configurable (`sltours.catalogue.freshness`). Defaults: standard prices 180 days, promotional prices 14
days (promotions need an end date), additional costs 180 days, opening hours 120 days, sessions 30 days, participation rules
365 days. The 180-day overall verification still gates publishing. Every plan carries a `confidence` summary (prices,
times, suitability) used by the planner page and the assistant.

## Day planner

Inputs: date, start and finish times, adults, each child's age, residency (SA, SADC, other African, international),
interests, starting area, transport (own car or SL Tours vehicle with driver), optional food allowance per person,
optional budget, number of stops, and whether to use only activities with published prices.

The planner:

1. keeps published activities that match the interests, are open on the date, and allow the youngest child's age;
2. builds the route greedily from the starting area, preferring short travel and little waiting, and inserts lunch;
3. honours seasonal hours, days, last entry and session start times;
4. prices every traveller with the rule for their age, residency, day, session and date (international rates are used
   for SADC and other African visitors only when no specific rate is published), plus mandatory extras;
5. labels every cost **published price**, **assumption** (fixed price with an assumed quantity, or the visitor's own food
   amount), **estimate** (e.g. a development sample vehicle rate), **not verified** or **not included**;
6. says a plan **fits the budget only when no cost is unverified**, lists exclusions (fuel, parking, food when not given),
   and never treats a price range as a guarantee.

Travel times are planning estimates (straight-line distance between area centres × road factor at an average speed plus
a buffer), editable under **Explore activities → Day-planner assumptions**, and shown with every plan.

The optional planning assistant ([Planning Assistant](../operations/PLANNING_ASSISTANT.md)) explains planner results in a chat
and can be switched off without affecting the planner. The partnership system is a later phase.
