# Product Requirements

## Goal

Move SL Tours from a static website plus WhatsApp/manual admin process toward a platform that supports discovery, planning, booking, confirmation, payment tracking, resource assignment, trip delivery, reviews, and returning customers.

> The authoritative brief for admin, driver, fleet, inspection, enquiry and private-trip features is
> [SL Tours Operations Requirements](SL_TOURS_OPERATIONS_REQUIREMENTS.md). The Explore catalogue and day planner are
> described in [Explore Cape Town Catalogue](EXPLORE_CATALOGUE.md); partnerships in [Partnerships](PARTNERSHIPS.md).

## Experiences

1. Public website: discover SL Tours and browse experiences.
2. Booking and trip planning: structured customer conversion.
3. My Trip portal: customer-facing journey details.
4. Operations: staff workflows for bookings, customers, guides, vehicles, quotes, and payments.

## Implemented Foundation

- New React public site with home, experiences, tour detail, private tours, transfers, about, contact, booking, trip planner, My Trip, and operations routes.
- Spring Boot API for tours, booking creation, trip planning requests, demo My Trip data, and operations dashboard/bookings.
- PostgreSQL/Flyway schema with seed data.
- Dockerized web/API/PostgreSQL stack.

## Not Yet Production Complete

- Real customer authentication.
- Payment gateway integration.
- Production email/WhatsApp providers.
- Staff CRUD for every resource.
- PDF quote generation.
- Full audit log and retention tooling.
- End-to-end tests for all journeys.
