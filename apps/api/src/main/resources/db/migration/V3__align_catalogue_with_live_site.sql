-- Align tour catalogue with the live sl-tours.com content (our_tours.html / index.html, 2026-09-30):
-- inclusions/exclusions, 5-guest minimums, durations, homepage card notes, and the West Coast tour.

alter table tours add column card_note varchar(160);

insert into tours (
    id, slug, title, short_description, full_description, duration, base_price_cents, pricing_type,
    minimum_guests, maximum_guests, pickup_available, featured, status, category, hero_image,
    seo_title, seo_description, created_at, updated_at
) values
('10000000-0000-0000-0000-000000000009', 'west-coast-tour', 'West Coast Tour',
 'A full day up the West Coast with lunch, West Coast National Park, and a guided visit inside !Khwa ttu.',
 'A relaxed full-day journey north of Cape Town through the West Coast National Park, with a guided visit inside the !Khwa ttu San heritage centre, lunch, and entrance fees included.',
 'Full day', 290000, 'PER_PERSON', 5, 12, true, false, 'ACTIVE', 'NATURE', '/assets/west-coast-tour.jpg',
 'West Coast Tour | SL Tours Cape Town', 'Explore the West Coast National Park and !Khwa ttu with SL Tours local guides.', now(), now());

insert into tour_locations (tour_id, item_order, value) values
('10000000-0000-0000-0000-000000000009', 0, 'West Coast National Park'), ('10000000-0000-0000-0000-000000000009', 1, '!Khwa ttu Nature Reserve');

insert into tour_tags (tour_id, item_order, value) values
('10000000-0000-0000-0000-000000000009', 0, 'nature'), ('10000000-0000-0000-0000-000000000009', 1, 'culture'), ('10000000-0000-0000-0000-000000000009', 2, 'full day');

update tours set minimum_guests = 5 where id in ('10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000005');
update tours set duration = '2 hours' where id in ('10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004');
update tours set duration = '3 days' where id = '10000000-0000-0000-0000-000000000005';

delete from tour_included where tour_id in ('10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000009');
delete from tour_excluded where tour_id in ('10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000009');

insert into tour_included (tour_id, item_order, value) values
('10000000-0000-0000-0000-000000000001', 0, 'Pick-up and drop-off'),
('10000000-0000-0000-0000-000000000001', 1, 'Cape Point entrance fee'),
('10000000-0000-0000-0000-000000000001', 2, 'Boulders Beach entrance fee'),
('10000000-0000-0000-0000-000000000001', 3, 'Chapman''s Peak toll fee'),
('10000000-0000-0000-0000-000000000001', 4, 'Coffee'),
('10000000-0000-0000-0000-000000000001', 5, 'Bottled water'),
('10000000-0000-0000-0000-000000000001', 6, 'Qualified tour guide'),
('10000000-0000-0000-0000-000000000001', 7, 'Comfortable air-conditioned vehicle'),
('10000000-0000-0000-0000-000000000002', 0, 'Pick-up and drop-off'),
('10000000-0000-0000-0000-000000000002', 1, 'Visits to two premium wine farms'),
('10000000-0000-0000-0000-000000000002', 2, 'Wine tastings at two farms'),
('10000000-0000-0000-0000-000000000002', 3, 'Cheese tasting'),
('10000000-0000-0000-0000-000000000002', 4, 'Lunch'),
('10000000-0000-0000-0000-000000000002', 5, 'Bottled water'),
('10000000-0000-0000-0000-000000000002', 6, 'Qualified tour guide'),
('10000000-0000-0000-0000-000000000002', 7, 'Comfortable air-conditioned vehicle'),
('10000000-0000-0000-0000-000000000003', 0, 'Pick-up and drop-off'),
('10000000-0000-0000-0000-000000000003', 1, 'Qualified tour guide'),
('10000000-0000-0000-0000-000000000003', 2, 'Comfortable vehicle'),
('10000000-0000-0000-0000-000000000003', 3, 'Bottled water'),
('10000000-0000-0000-0000-000000000004', 0, 'Pick-up and drop-off'),
('10000000-0000-0000-0000-000000000004', 1, 'Qualified local tour guide'),
('10000000-0000-0000-0000-000000000004', 2, 'Comfortable air-conditioned vehicle'),
('10000000-0000-0000-0000-000000000004', 3, 'Bottled water'),
('10000000-0000-0000-0000-000000000005', 0, 'Pick-up and drop-off'),
('10000000-0000-0000-0000-000000000005', 1, 'Two nights shared accommodation'),
('10000000-0000-0000-0000-000000000005', 2, 'Two dinners and two breakfasts'),
('10000000-0000-0000-0000-000000000005', 3, 'Kayaking'),
('10000000-0000-0000-0000-000000000005', 4, 'Bottled water'),
('10000000-0000-0000-0000-000000000005', 5, 'Qualified tour guide'),
('10000000-0000-0000-0000-000000000007', 0, 'Return transport from Cape Town in an air-conditioned vehicle'),
('10000000-0000-0000-0000-000000000007', 1, 'Accommodation pick-up and drop-off'),
('10000000-0000-0000-0000-000000000007', 2, 'Aquila Private Game Reserve entry fees'),
('10000000-0000-0000-0000-000000000007', 3, 'Guided Big Five game drive in an open safari vehicle'),
('10000000-0000-0000-0000-000000000007', 4, 'Professional safari ranger'),
('10000000-0000-0000-0000-000000000007', 5, 'Welcome drink on arrival'),
('10000000-0000-0000-0000-000000000007', 6, 'Buffet lunch at the lodge'),
('10000000-0000-0000-0000-000000000007', 7, 'Free time to enjoy lodge facilities'),
('10000000-0000-0000-0000-000000000007', 8, 'Bottled water'),
('10000000-0000-0000-0000-000000000008', 0, 'Flexible pick-up and drop-off'),
('10000000-0000-0000-0000-000000000008', 1, 'Custom itinerary planning'),
('10000000-0000-0000-0000-000000000008', 2, 'Private transport'),
('10000000-0000-0000-0000-000000000008', 3, 'Dedicated guide support'),
('10000000-0000-0000-0000-000000000009', 0, 'Pick-up and drop-off'),
('10000000-0000-0000-0000-000000000009', 1, 'Delicious lunch'),
('10000000-0000-0000-0000-000000000009', 2, 'Qualified tour guide'),
('10000000-0000-0000-0000-000000000009', 3, 'Guided visit inside !Khwa ttu'),
('10000000-0000-0000-0000-000000000009', 4, 'West Coast National Park entrance fee'),
('10000000-0000-0000-0000-000000000009', 5, 'Additional entrance fees'),
('10000000-0000-0000-0000-000000000009', 6, 'Comfortable air-conditioned vehicle'),
('10000000-0000-0000-0000-000000000009', 7, 'Bottled water');

insert into tour_excluded (tour_id, item_order, value) values
('10000000-0000-0000-0000-000000000001', 0, 'Seal Island boat fee (optional, at own cost)'),
('10000000-0000-0000-0000-000000000002', 0, 'Personal purchases'),
('10000000-0000-0000-0000-000000000003', 0, 'Meals'),
('10000000-0000-0000-0000-000000000004', 0, 'Meals'),
('10000000-0000-0000-0000-000000000005', 0, 'Travel insurance'),
('10000000-0000-0000-0000-000000000005', 1, 'Flights'),
('10000000-0000-0000-0000-000000000005', 2, 'Optional extras (Bungee Jumping and Tsitsikamma activities)'),
('10000000-0000-0000-0000-000000000007', 0, 'Spa treatments'),
('10000000-0000-0000-0000-000000000007', 1, 'Gratuities (optional)'),
('10000000-0000-0000-0000-000000000007', 2, 'Personal purchases'),
('10000000-0000-0000-0000-000000000008', 0, 'Entrance fees'),
('10000000-0000-0000-0000-000000000008', 1, 'Meals'),
('10000000-0000-0000-0000-000000000008', 2, 'Optional activities unless confirmed in your quote'),
('10000000-0000-0000-0000-000000000009', 0, 'Personal purchases');

update tours set card_note = 'Included: Entrance fees, toll fee and coffee.' where id = '10000000-0000-0000-0000-000000000001';
update tours set card_note = 'Included: Wine tastings at two farms.' where id = '10000000-0000-0000-0000-000000000002';
update tours set card_note = 'Included: Pick-up, drop-off, and bottled water.' where id = '10000000-0000-0000-0000-000000000003';
update tours set card_note = 'Included: Pick-up and drop-off.' where id = '10000000-0000-0000-0000-000000000004';
update tours set card_note = 'Included: Accommodation and meals.' where id = '10000000-0000-0000-0000-000000000005';
update tours set card_note = 'Included: Pick-up and drop-off.' where id = '10000000-0000-0000-0000-000000000006';
update tours set card_note = 'Included: Game drive, reserve entry and lunch.' where id = '10000000-0000-0000-0000-000000000007';
update tours set card_note = 'Included: Lunch and entrance fees.' where id = '10000000-0000-0000-0000-000000000009';

-- Seed quote line priced Winelands at the Peninsula rate.
update quote_items set description = 'Cape Winelands Escape', unit_price_cents = 180000, line_total_cents = 360000
where id = '91000000-0000-0000-0000-000000000002';
update quotes set subtotal_cents = 1200000, total_cents = 1200000 where id = '90000000-0000-0000-0000-000000000001';
