insert into tours (
    id, slug, title, short_description, full_description, duration, base_price_cents, pricing_type,
    minimum_guests, maximum_guests, pickup_available, featured, status, category, hero_image,
    seo_title, seo_description, created_at, updated_at
) values
('10000000-0000-0000-0000-000000000001', 'cape-peninsula-journey', 'Cape Peninsula Journey',
 'Cape Point, Chapman''s Peak, Boulders penguins, and coastal villages with a local guide.',
 'A full-day private-friendly journey around the Cape Peninsula, shaped around scenic viewpoints, wildlife stops, local stories, and enough time to enjoy the route without being rushed.',
 'Full day', 210000, 'PER_PERSON', 1, 18, true, true, 'ACTIVE', 'PENINSULA', '/assets/cape-point-tour.jpg',
 'Cape Peninsula Tour | SL Tours Cape Town', 'Explore Cape Point, Boulders Beach, and Chapman''s Peak with SL Tours local guides.', now(), now()),
('10000000-0000-0000-0000-000000000002', 'cape-winelands-escape', 'Cape Winelands Escape',
 'Stellenbosch, Franschhoek, cellar-door tastings, mountain views, and relaxed local hosting.',
 'A refined Winelands day out with carefully paced tastings, village time, scenic drives, and flexibility for guests who prefer food, history, photography, or wine-focused stops.',
 'Full day', 180000, 'PER_PERSON', 1, 16, true, true, 'ACTIVE', 'WINELANDS', '/assets/winelands-tour.jpg',
 'Cape Winelands Tour | SL Tours Cape Town', 'Book a Cape Winelands tour through Stellenbosch and Franschhoek with local guidance.', now(), now()),
('10000000-0000-0000-0000-000000000003', 'cape-town-essentials', 'Cape Town Essentials',
 'A compact city introduction covering Table Mountain views, Bo-Kaap, history, and local neighbourhoods.',
 'A half-day city tour for travellers who want orientation, history, colour, and useful local insight early in their Cape Town stay.',
 'Half day', 90000, 'PER_PERSON', 1, 20, true, true, 'ACTIVE', 'CITY', '/assets/city-tour.jpg',
 'Cape Town City Tour | SL Tours Cape Town', 'See Cape Town essentials including Bo-Kaap, city history, and scenic viewpoints.', now(), now()),
('10000000-0000-0000-0000-000000000004', 'cape-culture-experience', 'Cape Culture Experience',
 'A respectful cultural route through Langa, Khayelitsha, community stories, and local entrepreneurship.',
 'A guided cultural experience focused on listening, context, and authentic connection, designed with care around community respect and traveller understanding.',
 'Half day', 95000, 'PER_PERSON', 1, 14, true, true, 'ACTIVE', 'CULTURE', '/assets/township-tour.png',
 'Cape Town Township and Culture Tour | SL Tours', 'Experience Cape Town culture through a respectful guided township and community visit.', now(), now()),
('10000000-0000-0000-0000-000000000005', 'garden-route-journey', 'Garden Route Journey',
 'A multi-day South African road journey through Knysna, Tsitsikamma, Wilderness, and coastal landscapes.',
 'A customisable Garden Route experience for travellers who want a longer South African journey with transport, itinerary support, and local recommendations handled by SL Tours.',
 '3 days', 650000, 'PER_PERSON', 2, 12, true, true, 'ACTIVE', 'GARDEN_ROUTE', '/assets/garden-route-tour.jpg',
 'Garden Route Tour | SL Tours South Africa', 'Plan a Garden Route trip with SL Tours from Cape Town through South Africa''s coastal highlights.', now(), now()),
('10000000-0000-0000-0000-000000000006', 'airport-transfer', 'Airport Transfer',
 'Reliable Cape Town International Airport transfers with meet-and-greet and clear pickup communication.',
 'A comfortable private airport transfer designed for simple arrivals and departures, with flight-aware coordination and direct communication.',
 'Transfer', 130000, 'PER_TRIP', 1, 7, true, true, 'ACTIVE', 'TRANSFER', '/assets/airport-transfer.jpg',
 'Cape Town Airport Transfer | SL Tours', 'Book a private Cape Town airport transfer with SL Tours.', now(), now()),
('10000000-0000-0000-0000-000000000007', 'aquila-safari-day-trip', 'Aquila Safari Day Trip',
 'A Big Five safari day trip from Cape Town including lodge entry, game drive, lunch, and transport.',
 'A full-day safari option for visitors who want a wildlife experience from Cape Town without planning the logistics themselves.',
 'Full day', 350000, 'PER_PERSON', 1, 12, true, false, 'ACTIVE', 'SAFARI', '/assets/aquila-safari.jpeg',
 'Aquila Safari Day Trip | SL Tours Cape Town', 'Take a Cape Town safari day trip to Aquila Private Game Reserve with SL Tours.', now(), now()),
('10000000-0000-0000-0000-000000000008', 'private-custom-tour', 'Private Custom Tour',
 'A flexible private tour or chauffeur-led day shaped around your timing, interests, and pace.',
 'A tailor-made SL Tours experience for guests who want a private vehicle, dedicated guide support, and an itinerary built around their interests.',
 'Custom', 0, 'CUSTOM', 1, 40, true, false, 'ACTIVE', 'PRIVATE', '/assets/private-tour.png',
 'Private Custom Tour | SL Tours Cape Town', 'Plan a private custom Cape Town tour with SL Tours.', now(), now());

insert into tour_locations (tour_id, item_order, value) values
('10000000-0000-0000-0000-000000000001', 0, 'Hout Bay'), ('10000000-0000-0000-0000-000000000001', 1, 'Chapman''s Peak'), ('10000000-0000-0000-0000-000000000001', 2, 'Cape Point'), ('10000000-0000-0000-0000-000000000001', 3, 'Boulders Beach'),
('10000000-0000-0000-0000-000000000002', 0, 'Stellenbosch'), ('10000000-0000-0000-0000-000000000002', 1, 'Franschhoek'), ('10000000-0000-0000-0000-000000000002', 2, 'Paarl'),
('10000000-0000-0000-0000-000000000003', 0, 'Cape Town CBD'), ('10000000-0000-0000-0000-000000000003', 1, 'Bo-Kaap'), ('10000000-0000-0000-0000-000000000003', 2, 'Company''s Garden'),
('10000000-0000-0000-0000-000000000004', 0, 'Langa'), ('10000000-0000-0000-0000-000000000004', 1, 'Khayelitsha'),
('10000000-0000-0000-0000-000000000005', 0, 'Knysna'), ('10000000-0000-0000-0000-000000000005', 1, 'Tsitsikamma'), ('10000000-0000-0000-0000-000000000005', 2, 'Wilderness'),
('10000000-0000-0000-0000-000000000006', 0, 'Cape Town International Airport'), ('10000000-0000-0000-0000-000000000006', 1, 'Cape Town accommodation');

insert into tour_tags (tour_id, item_order, value) values
('10000000-0000-0000-0000-000000000001', 0, 'nature'), ('10000000-0000-0000-0000-000000000001', 1, 'full day'), ('10000000-0000-0000-0000-000000000001', 2, 'private friendly'),
('10000000-0000-0000-0000-000000000002', 0, 'wine'), ('10000000-0000-0000-0000-000000000002', 1, 'food'), ('10000000-0000-0000-0000-000000000002', 2, 'full day'),
('10000000-0000-0000-0000-000000000003', 0, 'city'), ('10000000-0000-0000-0000-000000000003', 1, 'history'), ('10000000-0000-0000-0000-000000000003', 2, 'half day'),
('10000000-0000-0000-0000-000000000004', 0, 'culture'), ('10000000-0000-0000-0000-000000000004', 1, 'community'), ('10000000-0000-0000-0000-000000000004', 2, 'half day'),
('10000000-0000-0000-0000-000000000005', 0, 'multi-day'), ('10000000-0000-0000-0000-000000000005', 1, 'nature'), ('10000000-0000-0000-0000-000000000005', 2, 'custom'),
('10000000-0000-0000-0000-000000000006', 0, 'transfer'), ('10000000-0000-0000-0000-000000000006', 1, 'private'), ('10000000-0000-0000-0000-000000000006', 2, 'airport'),
('10000000-0000-0000-0000-000000000007', 0, 'wildlife'), ('10000000-0000-0000-0000-000000000007', 1, 'safari'), ('10000000-0000-0000-0000-000000000007', 2, 'full day'),
('10000000-0000-0000-0000-000000000008', 0, 'private'), ('10000000-0000-0000-0000-000000000008', 1, 'custom itinerary'), ('10000000-0000-0000-0000-000000000008', 2, 'chauffeur');

insert into tour_highlights (tour_id, item_order, value) values
('10000000-0000-0000-0000-000000000001', 0, 'Cape Point and Cape of Good Hope viewpoints'), ('10000000-0000-0000-0000-000000000001', 1, 'Boulders Beach penguin colony'), ('10000000-0000-0000-0000-000000000001', 2, 'Flexible coastal photo stops'),
('10000000-0000-0000-0000-000000000002', 0, 'Curated wine estate visits'), ('10000000-0000-0000-0000-000000000002', 1, 'Franschhoek village time'), ('10000000-0000-0000-0000-000000000002', 2, 'Mountain and vineyard scenery'),
('10000000-0000-0000-0000-000000000003', 0, 'Bo-Kaap colour and history'), ('10000000-0000-0000-0000-000000000003', 1, 'Cape Town city orientation'), ('10000000-0000-0000-0000-000000000003', 2, 'Local recommendations for the rest of your stay'),
('10000000-0000-0000-0000-000000000004', 0, 'Community-led context'), ('10000000-0000-0000-0000-000000000004', 1, 'Local entrepreneurship and culture'), ('10000000-0000-0000-0000-000000000004', 2, 'Respectful guided conversation'),
('10000000-0000-0000-0000-000000000005', 0, 'Knysna lagoon'), ('10000000-0000-0000-0000-000000000005', 1, 'Tsitsikamma forest and coast'), ('10000000-0000-0000-0000-000000000005', 2, 'Multi-day itinerary support'),
('10000000-0000-0000-0000-000000000006', 0, 'Meet-and-greet pickup'), ('10000000-0000-0000-0000-000000000006', 1, 'Private comfortable vehicle'), ('10000000-0000-0000-0000-000000000006', 2, 'Clear WhatsApp coordination'),
('10000000-0000-0000-0000-000000000007', 0, 'Big Five game drive'), ('10000000-0000-0000-0000-000000000007', 1, 'Lunch at the lodge'), ('10000000-0000-0000-0000-000000000007', 2, 'Return transport from Cape Town'),
('10000000-0000-0000-0000-000000000008', 0, 'Custom itinerary design'), ('10000000-0000-0000-0000-000000000008', 1, 'Private transport'), ('10000000-0000-0000-0000-000000000008', 2, 'Dedicated guide support');

insert into tour_included (tour_id, item_order, value) values
('10000000-0000-0000-0000-000000000001', 0, 'Pickup and drop-off'), ('10000000-0000-0000-0000-000000000001', 1, 'Qualified local guide'), ('10000000-0000-0000-0000-000000000001', 2, 'Bottled water'),
('10000000-0000-0000-0000-000000000002', 0, 'Pickup and drop-off'), ('10000000-0000-0000-0000-000000000002', 1, 'Route planning'), ('10000000-0000-0000-0000-000000000002', 2, 'Bottled water'),
('10000000-0000-0000-0000-000000000003', 0, 'Pickup and drop-off'), ('10000000-0000-0000-0000-000000000003', 1, 'City guide'), ('10000000-0000-0000-0000-000000000003', 2, 'Flexible stops'),
('10000000-0000-0000-0000-000000000004', 0, 'Pickup and drop-off'), ('10000000-0000-0000-0000-000000000004', 1, 'Guided cultural experience'), ('10000000-0000-0000-0000-000000000004', 2, 'Community context'),
('10000000-0000-0000-0000-000000000005', 0, 'Private transport'), ('10000000-0000-0000-0000-000000000005', 1, 'Itinerary planning'), ('10000000-0000-0000-0000-000000000005', 2, 'Guide support'),
('10000000-0000-0000-0000-000000000006', 0, 'Airport meet-and-greet'), ('10000000-0000-0000-0000-000000000006', 1, 'Private transfer'), ('10000000-0000-0000-0000-000000000006', 2, 'Flight-aware coordination');

insert into tour_excluded (tour_id, item_order, value) values
('10000000-0000-0000-0000-000000000001', 0, 'Lunch unless arranged'), ('10000000-0000-0000-0000-000000000001', 1, 'Optional entrance fees not listed in quote'),
('10000000-0000-0000-0000-000000000002', 0, 'Wine tasting fees unless quoted'), ('10000000-0000-0000-0000-000000000002', 1, 'Lunch'),
('10000000-0000-0000-0000-000000000003', 0, 'Table Mountain cableway tickets'), ('10000000-0000-0000-0000-000000000003', 1, 'Meals'),
('10000000-0000-0000-0000-000000000004', 0, 'Meals unless arranged'), ('10000000-0000-0000-0000-000000000004', 1, 'Personal purchases');

insert into tour_itinerary (tour_id, item_order, value) values
('10000000-0000-0000-0000-000000000001', 0, '07:45 Hotel pickup and coastal departure'), ('10000000-0000-0000-0000-000000000001', 1, '10:00 Cape Point and Cape of Good Hope'), ('10000000-0000-0000-0000-000000000001', 2, '14:00 Boulders Beach penguins'), ('10000000-0000-0000-0000-000000000001', 3, '17:30 Return to hotel'),
('10000000-0000-0000-0000-000000000002', 0, '09:00 Stellenbosch cellar visit'), ('10000000-0000-0000-0000-000000000002', 1, '12:30 Lunch and village time'), ('10000000-0000-0000-0000-000000000002', 2, '15:00 Franschhoek scenic tasting'),
('10000000-0000-0000-0000-000000000006', 0, 'Driver confirms pickup details'), ('10000000-0000-0000-0000-000000000006', 1, 'Meet at arrivals or accommodation'), ('10000000-0000-0000-0000-000000000006', 2, 'Direct private transfer');

insert into tour_faqs (tour_id, item_order, value) values
('10000000-0000-0000-0000-000000000001', 0, 'Is this private? Private departures can be arranged on request.'),
('10000000-0000-0000-0000-000000000001', 1, 'Can children join? Yes, the route works well for families.'),
('10000000-0000-0000-0000-000000000002', 0, 'Can non-drinkers join? Yes, scenery, food, and village time can be emphasised.'),
('10000000-0000-0000-0000-000000000006', 0, 'Do you track flights? Flight-aware pickup can be coordinated from the booking details.');

insert into customers (id, name, email, phone, country, preferences, notes, created_at, updated_at) values
('20000000-0000-0000-0000-000000000001', 'Sarah Demo', 'sarah.demo@example.com', '+44 20 0000 0000', 'United Kingdom', 'Nature, wine, relaxed pacing', 'Demo customer only', now(), now());

insert into bookings (
    id, reference, tour_id, customer_id, travel_date, guests, customer_name, customer_email, customer_phone,
    customer_country, pickup_location, notes, status, total_amount_cents, amount_paid_cents, created_at, updated_at
) values
('30000000-0000-0000-0000-000000000001', 'SLT-2026-A1B2C3', '10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000001', current_date + interval '10 days', 4, 'Sarah Demo',
 'sarah.demo@example.com', '+44 20 0000 0000', 'United Kingdom', 'V&A Waterfront hotel lobby',
 'Family prefers scenic photo stops.', 'CONFIRMED', 840000, 420000, now(), now()),
('30000000-0000-0000-0000-000000000002', 'SLT-2026-D4E5F6', '10000000-0000-0000-0000-000000000002',
 null, current_date + interval '15 days', 2, 'David Demo', 'david.demo@example.com', '+1 555 0100',
 'United States', 'Cape Town CBD hotel', 'Interested in food pairings.', 'AWAITING_PAYMENT', 360000, 0, now(), now());

insert into trip_planning_requests (
    id, reference, customer_name, customer_email, customer_phone, arrival_date, departure_date,
    adults, children, group_type, notes, status, created_at
) values
('40000000-0000-0000-0000-000000000001', 'SLP-2026-K9M8N', 'Priya Demo', 'priya.demo@example.com',
 '+91 90000 00000', current_date + interval '30 days', current_date + interval '36 days',
 2, 1, 'family', 'Would like nature, penguins, and one relaxed wine farm day.', 'NEW', now());

insert into trip_planning_interests (request_id, item_order, value) values
('40000000-0000-0000-0000-000000000001', 0, 'nature'), ('40000000-0000-0000-0000-000000000001', 1, 'wine'), ('40000000-0000-0000-0000-000000000001', 2, 'food');

insert into trip_planning_services (request_id, item_order, value) values
('40000000-0000-0000-0000-000000000001', 0, 'tours'), ('40000000-0000-0000-0000-000000000001', 1, 'airport transfer'), ('40000000-0000-0000-0000-000000000001', 2, 'custom itinerary');

insert into guides (id, name, contact, status, languages, specialties, notes, created_at) values
('50000000-0000-0000-0000-000000000001', 'Amina Daniels', '+27 67 100 0001', 'ACTIVE', 'English, Afrikaans', 'Cape Peninsula, culture', 'Demo guide', now()),
('50000000-0000-0000-0000-000000000002', 'Sipho Mokoena', '+27 67 100 0002', 'ACTIVE', 'English, isiXhosa', 'City, history, transfers', 'Demo guide', now()),
('50000000-0000-0000-0000-000000000003', 'Marlene Jacobs', '+27 67 100 0003', 'ACTIVE', 'English', 'Winelands, food', 'Demo guide', now());

insert into drivers (id, name, contact, status, notes, created_at) values
('60000000-0000-0000-0000-000000000001', 'Sipho Mokoena', '+27 67 100 0002', 'ACTIVE', 'Guide-driver', now()),
('60000000-0000-0000-0000-000000000002', 'Daniel Brown', '+27 67 100 0004', 'ACTIVE', 'Transfers', now());

insert into vehicles (id, registration, type, capacity, status, notes, created_at) values
('70000000-0000-0000-0000-000000000001', 'CA 123 456', 'Mercedes V-Class', 7, 'AVAILABLE', 'Premium people carrier', now()),
('70000000-0000-0000-0000-000000000002', 'CA 789 101', 'Toyota Quantum', 13, 'AVAILABLE', 'Group tours', now()),
('70000000-0000-0000-0000-000000000003', 'CA 222 333', 'Hyundai H1', 8, 'MAINTENANCE', 'Service scheduled', now()),
('70000000-0000-0000-0000-000000000004', 'CA 444 555', 'Sedan', 3, 'AVAILABLE', 'Airport transfers', now());

insert into assignments (id, booking_id, guide_id, driver_id, vehicle_id, status, created_at) values
('80000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001',
 '50000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000001',
 '70000000-0000-0000-0000-000000000001', 'ASSIGNED', now());

insert into quotes (id, reference, customer_id, trip_planning_request_id, status, subtotal_cents, discount_cents, tax_cents, total_cents, valid_until, notes, created_at, updated_at) values
('90000000-0000-0000-0000-000000000001', 'SLQ-2026-0041', '20000000-0000-0000-0000-000000000001', null, 'SENT', 1260000, 0, 0, 1260000, current_date + interval '14 days', 'Demo quote', now(), now());

insert into quote_items (id, quote_id, description, quantity, unit_price_cents, line_total_cents) values
('91000000-0000-0000-0000-000000000001', '90000000-0000-0000-0000-000000000001', 'Cape Peninsula Journey', 4, 210000, 840000),
('91000000-0000-0000-0000-000000000002', '90000000-0000-0000-0000-000000000001', 'Winelands Escape', 2, 210000, 420000);

insert into payments (id, booking_id, quote_id, method, status, amount_cents, external_reference, recorded_by, recorded_at, notes) values
('92000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', null, 'MANUAL_EFT', 'PAID', 420000, 'DEMO-EFT-001', 'system-seed', now(), 'Demo deposit');
