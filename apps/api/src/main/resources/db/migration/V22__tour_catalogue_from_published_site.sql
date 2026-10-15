-- The nine offerings published on sl-tours.com/tours (checked 4 October 2026), applied to the existing tour records by slug so
-- no duplicates are created and existing bookings and links keep working. Descriptions are rewritten summaries; prices,
-- durations, group sizes, pickups, itineraries and inclusions follow the published pages. Conflicts and gaps in the published
-- information are kept in review_notes (admin only) and, where they affect a customer's decision, in practical information
-- that asks the customer to confirm the detail when enquiring. No cancellation, deposit, child-price or accessibility terms
-- are added because none are published.

alter table tours add column pickup_summary varchar(160);
alter table tours add column group_size_label varchar(80);
alter table tours add column availability_label varchar(160);
alter table tours add column tour_type_label varchar(80);
alter table tours add column hero_image_alt varchar(200);
alter table tours add column itinerary_note varchar(600);
-- Admin-only: open questions for the operator. Never shown publicly.
alter table tours add column review_notes varchar(4000);
alter table tours add column source_url varchar(300);
-- Which enquiry form the detail page shows: STANDARD (tours), AIRPORT (flight and luggage details) or CUSTOM (dates and interests).
alter table tours add column enquiry_form varchar(20) not null default 'STANDARD' check (enquiry_form in ('STANDARD', 'AIRPORT', 'CUSTOM'));

create table tour_practical_info (
    tour_id uuid not null references tours(id) on delete cascade,
    item_order integer not null,
    value varchar(600) not null,
    primary key (tour_id, item_order)
);

-- Replaces a tour's list content. Lists are stored in order; item_order starts at 0.
create temporary table tour_content (slug varchar(160), kind varchar(20), item_order int, value varchar(800)) on commit drop;

-- 1. Cape Peninsula & Cape Point
update tours set title = 'Cape Peninsula & Cape Point', duration = 'Full day', base_price_cents = 210000, pricing_type = 'PER_PERSON',
    minimum_guests = 5, pickup_available = true, category = 'PENINSULA', display_order = 1, status = 'ACTIVE',
    short_description = 'Coastal drives, Cape Point and the African penguins at Boulders Beach.',
    full_description = 'Discover the Cape Peninsula through coastal drives, scenic viewpoints and an encounter with the African penguins at Boulders Beach. The day follows the Atlantic coast through Bo-Kaap, Camps Bay, Hout Bay and Chapman''s Peak to Cape Point and the Cape of Good Hope, then returns via Boulders Beach, Simon''s Town and Silvermine.',
    hero_image = '/assets/tours/cape-point-tour.jpg', banner_image = null, hero_image_alt = 'Cape of Good Hope sign at the south-western tip of the Cape Peninsula',
    card_note = 'Included: Cape Point and Boulders entry, Chapman''s Peak toll',
    pickup_summary = 'Your Cape Town hotel', group_size_label = 'Minimum 5 guests', availability_label = 'Daily, weather permitting',
    tour_type_label = 'Full-day guided tour', enquiry_form = 'STANDARD', source_url = 'https://sl-tours.com/tours/cape-point',
    itinerary_note = 'Sample itinerary. Times are a guide and are confirmed with your booking.',
    seo_title = 'Cape Peninsula & Cape Point Tour | SL Tours Cape Town',
    seo_description = 'Full-day Cape Peninsula tour from Cape Town: Chapman''s Peak, Cape Point, the Cape of Good Hope and the penguins at Boulders Beach. R2,100 per person, minimum 5 guests.',
    review_notes = 'Funicular payment at Cape Point is not specified on the published page. Lunch is own account. Confirm price validity, VAT, deposit and cancellation terms, child prices and accessibility before publishing them.'
where slug = 'cape-peninsula-journey';
insert into tour_content values
 ('cape-peninsula-journey','highlight',0,'Bo-Kaap''s colourful streets'),('cape-peninsula-journey','highlight',1,'Camps Bay'),
 ('cape-peninsula-journey','highlight',2,'Hout Bay harbour'),('cape-peninsula-journey','highlight',3,'Chapman''s Peak Drive'),
 ('cape-peninsula-journey','highlight',4,'Ostrich farm'),('cape-peninsula-journey','highlight',5,'Cape Point lighthouse'),
 ('cape-peninsula-journey','highlight',6,'Cape of Good Hope'),('cape-peninsula-journey','highlight',7,'Boulders Beach penguins'),
 ('cape-peninsula-journey','highlight',8,'Simon''s Town and Silvermine'),
 ('cape-peninsula-journey','itinerary',0,'07:30 Hotel pickup'),('cape-peninsula-journey','itinerary',1,'08:00 Bo-Kaap and Camps Bay'),
 ('cape-peninsula-journey','itinerary',2,'09:00 Hout Bay and Chapman''s Peak Drive'),('cape-peninsula-journey','itinerary',3,'10:30 Ostrich farm and nature reserve'),
 ('cape-peninsula-journey','itinerary',4,'11:30 Cape Point and the Cape of Good Hope'),('cape-peninsula-journey','itinerary',5,'13:30 Lunch (own account) and the Boulders Beach penguins'),
 ('cape-peninsula-journey','itinerary',6,'15:30 Simon''s Town and Silvermine, then return to your hotel'),
 ('cape-peninsula-journey','included',0,'Hotel pickup and drop-off'),('cape-peninsula-journey','included',1,'Cape Point entrance fee'),
 ('cape-peninsula-journey','included',2,'Boulders Beach entrance fee'),('cape-peninsula-journey','included',3,'Chapman''s Peak toll'),
 ('cape-peninsula-journey','included',4,'Coffee'),('cape-peninsula-journey','included',5,'Bottled water'),
 ('cape-peninsula-journey','included',6,'Qualified guide'),('cape-peninsula-journey','included',7,'Air-conditioned transport'),
 ('cape-peninsula-journey','excluded',0,'Optional Seal Island boat trip'),('cape-peninsula-journey','excluded',1,'Lunch (own account)'),
 ('cape-peninsula-journey','practical',0,'Lunch is at your own cost.'),
 ('cape-peninsula-journey','practical',1,'Whether the Cape Point funicular is included is not stated. Please ask when you enquire.'),
 ('cape-peninsula-journey','practical',2,'The tour runs daily, weather permitting, for groups of at least five guests.');

-- 2. Cape Town City & Heritage Tour (published as "Table Mountain & Cape Town City Tour"; no mountain ascent or cableway is listed)
update tours set title = 'Cape Town City & Heritage Tour', duration = 'Half day', base_price_cents = 90000, pricing_type = 'PER_PERSON',
    minimum_guests = 5, pickup_available = true, category = 'CITY', display_order = 2, status = 'ACTIVE',
    short_description = 'Bo-Kaap, the District Six Museum, Greenmarket Square and the Company''s Garden, with views of Table Mountain.',
    full_description = 'Explore Cape Town''s history and everyday life through Bo-Kaap, the District Six Museum, Greenmarket Square and the Company''s Garden, with views of Table Mountain along the way. This is a city and heritage tour: going up Table Mountain and cableway tickets are not part of the published tour.',
    hero_image = '/assets/tours/city-tour.jpg', banner_image = null, hero_image_alt = 'Aerial view of Cape Town with the stadium and the V&A Waterfront',
    card_note = 'Included: Pickup, drop-off and water',
    pickup_summary = 'Your Cape Town hotel', group_size_label = 'Minimum 5 guests', availability_label = 'Daily, weather permitting',
    tour_type_label = 'Guided city tour', enquiry_form = 'STANDARD', source_url = 'https://sl-tours.com/tours/city-tour',
    itinerary_note = 'Sample itinerary for illustration only. The published times are being confirmed with SL Tours, so please confirm the start and finish times when you enquire.',
    seo_title = 'Cape Town City & Heritage Tour | SL Tours',
    seo_description = 'Guided Cape Town city and heritage tour: Bo-Kaap, the District Six Museum, Greenmarket Square and the Company''s Garden. R900 per person, minimum 5 guests.',
    review_notes = 'Published as "Table Mountain & Cape Town City Tour" but no mountain ascent or cableway ticket is listed; shown as City & Heritage until a mountain visit is confirmed. Duration is advertised as two hours / half day, but the sample itinerary runs from 09:00 to after 11:15 and the page also says return by 11:00-11:30, which conflicts with the final stop. Confirm the real duration and return time.'
where slug = 'cape-town-essentials';
insert into tour_content values
 ('cape-town-essentials','highlight',0,'Bo-Kaap'),('cape-town-essentials','highlight',1,'District Six Museum'),
 ('cape-town-essentials','highlight',2,'Greenmarket Square'),('cape-town-essentials','highlight',3,'Company''s Garden'),
 ('cape-town-essentials','highlight',4,'Views of Table Mountain'),
 ('cape-town-essentials','itinerary',0,'09:00 Hotel pickup'),('cape-town-essentials','itinerary',1,'09:20 Bo-Kaap'),
 ('cape-town-essentials','itinerary',2,'10:00 District Six Museum'),('cape-town-essentials','itinerary',3,'10:45 Greenmarket Square'),
 ('cape-town-essentials','itinerary',4,'11:15 Company''s Garden and Table Mountain views'),
 ('cape-town-essentials','included',0,'Hotel pickup and drop-off'),('cape-town-essentials','included',1,'Qualified guide'),
 ('cape-town-essentials','included',2,'Comfortable vehicle'),('cape-town-essentials','included',3,'Bottled water'),
 ('cape-town-essentials','excluded',0,'Meals'),('cape-town-essentials','excluded',1,'Museum admission'),('cape-town-essentials','excluded',2,'Personal purchases'),
 ('cape-town-essentials','practical',0,'Going up Table Mountain and cableway tickets are not included; the tour offers views of the mountain.'),
 ('cape-town-essentials','practical',1,'The tour is advertised as about two hours (half day), but the sample itinerary runs longer. Please confirm the start and finish times when you enquire.'),
 ('cape-town-essentials','practical',2,'District Six Museum admission is not included.');

-- 3. Township Cultural Experience
update tours set title = 'Township Cultural Experience', duration = 'Half day', base_price_cents = 95000, pricing_type = 'PER_PERSON',
    minimum_guests = 5, pickup_available = true, category = 'CULTURE', display_order = 3, status = 'ACTIVE',
    short_description = 'Langa or Khayelitsha with a local guide: community history, local entrepreneurs and neighbourhood life.',
    full_description = 'Visit Langa or Khayelitsha with a local guide to learn about community history, meet local entrepreneurs and explore neighbourhood life through conversation and guided visits. The visit can include small businesses, eateries, community initiatives and craft workshops.',
    hero_image = '/assets/tours/township-tour.jpg', banner_image = null, hero_image_alt = 'Street in a Cape Town township with Table Mountain in the distance',
    card_note = 'Included: Pickup, drop-off and a local guide',
    pickup_summary = 'Your Cape Town hotel', group_size_label = 'Minimum 5 guests', availability_label = 'Daily, weather permitting',
    tour_type_label = 'Guided cultural tour', enquiry_form = 'STANDARD', source_url = 'https://sl-tours.com/tours/township-tour',
    itinerary_note = 'Sample itinerary for illustration only. Please confirm the timing and which community you will visit when you enquire.',
    seo_title = 'Township Cultural Experience | Langa or Khayelitsha | SL Tours',
    seo_description = 'Guided township visit in Langa or Khayelitsha with a local guide: community history, small businesses and neighbourhood life. R950 per person, minimum 5 guests.',
    review_notes = 'Duration advertised as two hours / half day; the page promises hotel drop-off around 11:00, yet the return drive starts at 11:00. Confirm the total duration and return time. Confirm which community (Langa or Khayelitsha) is visited for each booking.'
where slug = 'cape-culture-experience';
insert into tour_content values
 ('cape-culture-experience','highlight',0,'Local community guide'),('cape-culture-experience','highlight',1,'Small businesses'),
 ('cape-culture-experience','highlight',2,'Cultural and historical stories'),('cape-culture-experience','highlight',3,'Local eateries'),
 ('cape-culture-experience','highlight',4,'Community initiatives'),('cape-culture-experience','highlight',5,'Craft workshops'),
 ('cape-culture-experience','itinerary',0,'09:00 Hotel pickup'),('cape-culture-experience','itinerary',1,'09:40 Orientation'),
 ('cape-culture-experience','itinerary',2,'10:00 Walking tour and visits to local businesses'),('cape-culture-experience','itinerary',3,'10:45 Conversations with the community'),
 ('cape-culture-experience','itinerary',4,'11:00 Return drive'),
 ('cape-culture-experience','included',0,'Hotel pickup and drop-off'),('cape-culture-experience','included',1,'Qualified local guide'),
 ('cape-culture-experience','included',2,'Air-conditioned vehicle'),('cape-culture-experience','included',3,'Bottled water'),
 ('cape-culture-experience','excluded',0,'Meals'),('cape-culture-experience','excluded',1,'Personal purchases'),
 ('cape-culture-experience','practical',0,'The tour visits Langa or Khayelitsha. Please ask which community your tour will visit when you enquire.'),
 ('cape-culture-experience','practical',1,'Advertised as about two hours (half day). Please confirm the return time when you enquire.');

-- 4. Cape Winelands Tour
update tours set title = 'Cape Winelands Tour', duration = 'Full day', base_price_cents = 180000, pricing_type = 'PER_PERSON',
    minimum_guests = 5, pickup_available = true, category = 'WINELANDS', display_order = 4, status = 'ACTIVE',
    short_description = 'Stellenbosch and Franschhoek: two wine estates, two tastings, cheese pairing and lunch.',
    full_description = 'Enjoy Stellenbosch and Franschhoek with visits to two wine estates, guided tastings, cheese pairing and a vineyard lunch, with transport and a guide throughout.',
    hero_image = '/assets/tours/winelands-tour.jpg', banner_image = null, hero_image_alt = 'Glass of red wine in front of vineyards in the Cape Winelands',
    card_note = 'Included: Two tastings, cheese and lunch',
    pickup_summary = 'Your Cape Town hotel', group_size_label = 'Minimum 5 guests', availability_label = 'Daily, weather permitting',
    tour_type_label = 'Full-day wine tour', enquiry_form = 'STANDARD', source_url = 'https://sl-tours.com/tours/winelands-tour',
    itinerary_note = 'Sample itinerary. Times are a guide and are confirmed with your booking.',
    seo_title = 'Cape Winelands Tour | Stellenbosch & Franschhoek | SL Tours',
    seo_description = 'Full-day Cape Winelands tour to Stellenbosch and Franschhoek with two wine estates, tastings, cheese pairing and lunch. R1,800 per person, minimum 5 guests.',
    review_notes = 'Not specified on the published page: estate names, lunch menu, tasting quantities, dietary arrangements and provisions for children or guests who do not drink alcohol.'
where slug = 'cape-winelands-escape';
insert into tour_content values
 ('cape-winelands-escape','highlight',0,'Stellenbosch'),('cape-winelands-escape','highlight',1,'Franschhoek'),
 ('cape-winelands-escape','highlight',2,'Two wine estates'),('cape-winelands-escape','highlight',3,'Guided wine tastings'),
 ('cape-winelands-escape','highlight',4,'Cheese pairing'),('cape-winelands-escape','highlight',5,'Vineyard lunch'),
 ('cape-winelands-escape','itinerary',0,'08:30 Hotel pickup'),('cape-winelands-escape','itinerary',1,'09:30 Stellenbosch'),
 ('cape-winelands-escape','itinerary',2,'10:00 First wine tasting and cheese pairing'),('cape-winelands-escape','itinerary',3,'12:00 Lunch'),
 ('cape-winelands-escape','itinerary',4,'14:00 Franschhoek'),('cape-winelands-escape','itinerary',5,'14:30 Second wine tasting'),
 ('cape-winelands-escape','itinerary',6,'16:30 Return to Cape Town'),
 ('cape-winelands-escape','included',0,'Hotel pickup and drop-off'),('cape-winelands-escape','included',1,'Visits to two wine estates'),
 ('cape-winelands-escape','included',2,'Two wine tastings'),('cape-winelands-escape','included',3,'Cheese tasting'),
 ('cape-winelands-escape','included',4,'Lunch'),('cape-winelands-escape','included',5,'Bottled water'),
 ('cape-winelands-escape','included',6,'Qualified guide'),('cape-winelands-escape','included',7,'Air-conditioned vehicle'),
 ('cape-winelands-escape','excluded',0,'Wine purchases'),('cape-winelands-escape','excluded',1,'Gifts'),('cape-winelands-escape','excluded',2,'Extras'),
 ('cape-winelands-escape','practical',0,'The estates visited are not named in advance. Please ask when you enquire.'),
 ('cape-winelands-escape','practical',1,'If anyone in your group has dietary needs, does not drink alcohol or is under 18, please mention it when you enquire.');

-- 5. Garden Route Tour
update tours set title = 'Garden Route Tour', duration = '3 days, 2 nights', base_price_cents = 650000, pricing_type = 'PER_PERSON',
    minimum_guests = 5, pickup_available = true, category = 'GARDEN_ROUTE', display_order = 5, status = 'ACTIVE',
    short_description = 'Wilderness, Knysna and Tsitsikamma, with kayaking and two nights of shared accommodation.',
    full_description = 'Explore Wilderness, Knysna and Tsitsikamma on a guided three-day journey with coastal scenery, guided kayaking and two nights of shared accommodation. The route includes Mossel Bay on the way out and time at the Botlierskop reserve on the way back.',
    hero_image = '/assets/tours/garden-route-tour.jpg', banner_image = null, hero_image_alt = 'Suspension bridge over a river mouth in the Tsitsikamma forest',
    card_note = 'Included: Two nights, breakfasts and dinners',
    pickup_summary = 'Your Cape Town accommodation', group_size_label = 'Minimum 5 guests', availability_label = 'Daily, weather permitting',
    tour_type_label = 'Three-day guided tour', enquiry_form = 'STANDARD', source_url = 'https://sl-tours.com/tours/garden-route',
    itinerary_note = 'Sample itinerary. Times are a guide and are confirmed with your booking.',
    seo_title = 'Garden Route Tour | 3 Days from Cape Town | SL Tours',
    seo_description = 'Three-day Garden Route tour from Cape Town: Wilderness, Knysna, Tsitsikamma and guided kayaking, with two nights of shared accommodation. R6,500 per person, minimum 5 guests.',
    review_notes = 'Clarify accommodation standard, single supplements, park admission fees, and whether a game drive at Botlierskop is included (the page lists only time at the reserve). Lunches are own account in the itinerary.'
where slug = 'garden-route-journey';
insert into tour_content values
 ('garden-route-journey','highlight',0,'Knysna Lagoon and the Heads'),('garden-route-journey','highlight',1,'Tsitsikamma forest and coast'),
 ('garden-route-journey','highlight',2,'Storms River suspension bridge'),('garden-route-journey','highlight',3,'Wilderness lakes and beaches'),
 ('garden-route-journey','highlight',4,'Mossel Bay'),('garden-route-journey','highlight',5,'Botlierskop'),
 ('garden-route-journey','itinerary',0,'Day 1, 07:00 Departure from Cape Town'),('garden-route-journey','itinerary',1,'Day 1, 12:30 Lunch in Mossel Bay (own account)'),
 ('garden-route-journey','itinerary',2,'Day 1, 16:00 Check-in at Wilderness'),('garden-route-journey','itinerary',3,'Day 2, 08:30 Knysna'),
 ('garden-route-journey','itinerary',4,'Day 2, 11:30 Guided kayaking'),('garden-route-journey','itinerary',5,'Day 2, 14:30 Tsitsikamma'),
 ('garden-route-journey','itinerary',6,'Day 2, 18:30 Dinner'),('garden-route-journey','itinerary',7,'Day 3, 08:00 Breakfast and check-out'),
 ('garden-route-journey','itinerary',8,'Day 3, 10:30 Botlierskop'),('garden-route-journey','itinerary',9,'Day 3, 17:30 Arrival in Cape Town'),
 ('garden-route-journey','included',0,'Transfers from and to Cape Town'),('garden-route-journey','included',1,'Two nights of shared accommodation'),
 ('garden-route-journey','included',2,'Two dinners'),('garden-route-journey','included',3,'Two breakfasts'),
 ('garden-route-journey','included',4,'Guided kayaking'),('garden-route-journey','included',5,'Bottled water'),('garden-route-journey','included',6,'Professional guide'),
 ('garden-route-journey','excluded',0,'Travel insurance'),('garden-route-journey','excluded',1,'Flights'),
 ('garden-route-journey','excluded',2,'Optional bungee jumping'),('garden-route-journey','excluded',3,'Tsitsikamma extras'),('garden-route-journey','excluded',4,'Lunches (own account)'),
 ('garden-route-journey','practical',0,'Accommodation is shared. Please ask about the accommodation standard and single rooms when you enquire.'),
 ('garden-route-journey','practical',1,'Whether a game drive at Botlierskop is included is not stated; the itinerary lists time at the reserve. Please ask when you enquire.'),
 ('garden-route-journey','practical',2,'Please confirm whether national park admission fees are included when you enquire.');

-- 6. Aquila Safari Day Tour
update tours set title = 'Aquila Safari Day Tour', duration = 'Full day', base_price_cents = 350000, pricing_type = 'PER_PERSON',
    minimum_guests = 1, pickup_available = true, category = 'SAFARI', display_order = 6, status = 'ACTIVE',
    short_description = 'Ranger-led game drive at Aquila Private Game Reserve, with a welcome drink and buffet lunch.',
    full_description = 'Travel to Aquila Private Game Reserve for a ranger-led safari in an open vehicle, a welcome drink, buffet lunch and time to relax at the lodge before returning to Cape Town. Aquila is a malaria-free reserve with the chance to see the Big Five; sightings depend on the day and are never guaranteed.',
    hero_image = '/assets/tours/aquila-safari.jpg', banner_image = null, hero_image_alt = 'Lion resting in long grass at a game reserve',
    card_note = 'Included: Game drive, reserve entry and lunch',
    pickup_summary = 'Your Cape Town accommodation', group_size_label = 'Any group size', availability_label = 'Daily, weather permitting',
    tour_type_label = 'Full-day safari', enquiry_form = 'STANDARD', source_url = 'https://sl-tours.com/tours/aquila-safari',
    itinerary_note = 'Sample itinerary. Times are a guide and depend on the reserve''s programme on the day.',
    seo_title = 'Aquila Safari Day Tour from Cape Town | SL Tours',
    seo_description = 'Full-day trip from Cape Town to Aquila Private Game Reserve with a ranger-led game drive, welcome drink and buffet lunch. R3,500 per person, any group size.',
    review_notes = 'Clarify whether the optional afternoon drive costs extra and confirm the booked reserve package and age restrictions.'
where slug = 'aquila-safari-day-trip';
insert into tour_content values
 ('aquila-safari-day-trip','highlight',0,'Ranger-led game drive in an open vehicle'),('aquila-safari-day-trip','highlight',1,'Malaria-free reserve'),
 ('aquila-safari-day-trip','highlight',2,'Chance to see the Big Five'),('aquila-safari-day-trip','highlight',3,'Welcome drink and buffet lunch'),
 ('aquila-safari-day-trip','highlight',4,'Time to relax at the lodge'),
 ('aquila-safari-day-trip','itinerary',0,'06:30 Pickup in Cape Town'),('aquila-safari-day-trip','itinerary',1,'08:30 Arrival at the reserve'),
 ('aquila-safari-day-trip','itinerary',2,'09:00 Game drive'),('aquila-safari-day-trip','itinerary',3,'12:00 Lunch'),
 ('aquila-safari-day-trip','itinerary',4,'13:30 Time at the lodge'),('aquila-safari-day-trip','itinerary',5,'14:30 Optional afternoon drive, depending on the reserve'),
 ('aquila-safari-day-trip','itinerary',6,'16:00 Return to Cape Town'),
 ('aquila-safari-day-trip','included',0,'Return air-conditioned transport'),('aquila-safari-day-trip','included',1,'Pickup and drop-off at your accommodation'),
 ('aquila-safari-day-trip','included',2,'Reserve admission'),('aquila-safari-day-trip','included',3,'Guided game drive in an open vehicle'),
 ('aquila-safari-day-trip','included',4,'Ranger'),('aquila-safari-day-trip','included',5,'Welcome drink'),('aquila-safari-day-trip','included',6,'Buffet lunch'),
 ('aquila-safari-day-trip','included',7,'Time at the lodge'),('aquila-safari-day-trip','included',8,'Bottled water'),
 ('aquila-safari-day-trip','excluded',0,'Spa treatments'),('aquila-safari-day-trip','excluded',1,'Gratuities'),('aquila-safari-day-trip','excluded',2,'Personal purchases'),
 ('aquila-safari-day-trip','practical',0,'Wildlife sightings depend on the day; seeing every Big Five species is not guaranteed.'),
 ('aquila-safari-day-trip','practical',1,'The afternoon drive is optional and depends on the reserve. Please ask whether it costs extra when you enquire.'),
 ('aquila-safari-day-trip','practical',2,'If you are travelling with children, please confirm the reserve''s age rules when you enquire.');

-- 7. West Coast Tour
update tours set title = 'West Coast Tour', duration = 'Full day', base_price_cents = 290000, pricing_type = 'PER_PERSON',
    minimum_guests = 5, pickup_available = true, category = 'NATURE', display_order = 7, status = 'ACTIVE',
    short_description = '!Khwa ttu San heritage centre and West Coast National Park.',
    full_description = 'Discover San heritage at !Khwa ttu and explore West Coast National Park, with lagoon views, birdlife, fynbos and, in season, wildflowers.',
    hero_image = '/assets/tours/west-coast-tour.jpg', banner_image = null, hero_image_alt = 'Traditional thatched huts among trees on the West Coast',
    card_note = 'Included: Lunch and park admission',
    pickup_summary = 'Your Cape Town accommodation', group_size_label = 'Minimum 5 guests', availability_label = 'Daily, weather permitting',
    tour_type_label = 'Full-day guided tour', enquiry_form = 'STANDARD', source_url = 'https://sl-tours.com/tours/west-coast',
    itinerary_note = 'Sample itinerary. Times are a guide; please confirm the return time when you enquire.',
    seo_title = 'West Coast Tour | !Khwa ttu & West Coast National Park | SL Tours',
    seo_description = 'Full-day West Coast tour from Cape Town: San heritage at !Khwa ttu and West Coast National Park, with lunch and park admission. R2,900 per person, minimum 5 guests.',
    review_notes = 'Clarify the return timing (the page describes a 17:00 return drive/drop-off), the scope of "additional entrance fees", and seasonal access. Wildflowers are seasonal (West Coast National Park lists August to September).'
where slug = 'west-coast-tour';
insert into tour_content values
 ('west-coast-tour','highlight',0,'Guided visit to !Khwa ttu San heritage centre'),('west-coast-tour','highlight',1,'West Coast National Park'),
 ('west-coast-tour','highlight',2,'Langebaan lagoon viewpoints'),('west-coast-tour','highlight',3,'Birdlife and fynbos'),
 ('west-coast-tour','highlight',4,'Wildflowers in season'),
 ('west-coast-tour','itinerary',0,'07:30 Pickup'),('west-coast-tour','itinerary',1,'09:00 Guided visit to !Khwa ttu'),
 ('west-coast-tour','itinerary',2,'11:00 Drive to West Coast National Park'),('west-coast-tour','itinerary',3,'12:00 Trails and lagoon viewpoints'),
 ('west-coast-tour','itinerary',4,'13:00 Lunch'),('west-coast-tour','itinerary',5,'14:30 More time in the park'),
 ('west-coast-tour','itinerary',6,'17:00 Return drive and drop-off'),
 ('west-coast-tour','included',0,'Transfers'),('west-coast-tour','included',1,'Lunch'),('west-coast-tour','included',2,'Qualified guide'),
 ('west-coast-tour','included',3,'Guided !Khwa ttu visit'),('west-coast-tour','included',4,'National park admission'),
 ('west-coast-tour','included',5,'Additional entrance fees'),('west-coast-tour','included',6,'Air-conditioned transport'),('west-coast-tour','included',7,'Bottled water'),
 ('west-coast-tour','excluded',0,'Personal purchases'),
 ('west-coast-tour','practical',0,'Wildflowers are seasonal and not guaranteed.'),
 ('west-coast-tour','practical',1,'Please confirm the return time and which entrance fees are covered when you enquire.');

-- 8. Airport Transfers
update tours set title = 'Airport Transfers', duration = 'Point to point', base_price_cents = 130000, pricing_type = 'PER_TRIP',
    minimum_guests = 1, pickup_available = true, category = 'TRANSFER', display_order = 8, status = 'ACTIVE',
    short_description = 'Private transfers between Cape Town International Airport and your accommodation.',
    full_description = 'Book a private airport transfer with flight monitoring, a meet-and-greet in the arrivals hall and direct air-conditioned transport to your accommodation. Departures to the airport are also offered.',
    hero_image = '/assets/tours/airport-transfer.jpg', banner_image = null, hero_image_alt = 'Cape Town International Airport sign above directions to the airline lounges',
    card_note = 'Included: Flight monitoring and meet-and-greet',
    pickup_summary = 'Cape Town International Airport or your accommodation', group_size_label = 'Private', availability_label = '24/7 on request',
    tour_type_label = 'Private transfer', enquiry_form = 'AIRPORT', source_url = 'https://sl-tours.com/tours/airport-transfers',
    itinerary_note = 'How it works',
    seo_title = 'Airport Transfers | Cape Town International | SL Tours',
    seo_description = 'Private transfers between Cape Town International Airport and your accommodation, with flight monitoring and meet-and-greet. R1,300 per trip.',
    review_notes = 'Clarify passenger and luggage capacity, the area covered by R1,300, waiting charges, child seats and accessibility. The price is per trip, not per person.'
where slug = 'airport-transfer';
insert into tour_content values
 ('airport-transfer','highlight',0,'Private transfer'),('airport-transfer','highlight',1,'Flight monitoring'),
 ('airport-transfer','highlight',2,'Meet-and-greet inside the terminal'),('airport-transfer','highlight',3,'Direct to your accommodation'),
 ('airport-transfer','highlight',4,'Airport departures also offered'),
 ('airport-transfer','itinerary',0,'Step 1 Send your flight details'),('airport-transfer','itinerary',1,'Step 2 Your driver monitors your arrival'),
 ('airport-transfer','itinerary',2,'Step 3 Meet your driver inside the terminal'),('airport-transfer','itinerary',3,'Step 4 Travel directly to your accommodation'),
 ('airport-transfer','included',0,'Pickup and drop-off between the airport and your accommodation'),('airport-transfer','included',1,'Professional driver'),
 ('airport-transfer','included',2,'Air-conditioned vehicle'),('airport-transfer','included',3,'Flight monitoring'),('airport-transfer','included',4,'Meet-and-greet'),
 ('airport-transfer','excluded',0,'Waiting beyond 60 minutes after landing'),('airport-transfer','excluded',1,'Extra stops not agreed beforehand'),
 ('airport-transfer','practical',0,'The price is R1,300 per trip, not per person.'),
 ('airport-transfer','practical',1,'The number of passengers and amount of luggage per vehicle, and the areas covered by this price, are not published. Please give your group size, luggage and destination when you enquire.'),
 ('airport-transfer','practical',2,'If you need a child seat or accessible transport, please ask when you enquire.');

-- 9. Private & Custom Tours
update tours set title = 'Private & Custom Tours', duration = 'Flexible, including multi-day', base_price_cents = 0, pricing_type = 'CUSTOM',
    minimum_guests = 1, pickup_available = true, category = 'PRIVATE', display_order = 9, status = 'ACTIVE',
    short_description = 'A private itinerary around your interests, dates and pace, with a dedicated guide.',
    full_description = 'Create a private itinerary around your interests, dates and preferred pace, with dedicated guide support and a private air-conditioned vehicle. Combine Cape Point, the Winelands, Table Mountain, the Garden Route or other stops you request, from solo travellers to large groups.',
    hero_image = '/assets/tours/private-tours.jpg', banner_image = null, hero_image_alt = 'Guests on a private tour at a coastal viewpoint',
    card_note = 'Quote usually within a day',
    pickup_summary = 'Anywhere in Cape Town', group_size_label = 'Solo travellers to large groups', availability_label = 'On request',
    tour_type_label = 'Private tour', enquiry_form = 'CUSTOM', source_url = 'https://sl-tours.com/private-tours',
    itinerary_note = 'Your itinerary is planned with you.',
    seo_title = 'Private Custom Tours Cape Town | SL Tours',
    seo_description = 'Private tours in Cape Town and beyond planned around your dates, interests and pace, with a dedicated guide and private transport. Quote on request.',
    review_notes = 'Entrance fees, meals and optional activities are excluded unless confirmed in the quote. The published page says a quote is usually prepared within a day.'
where slug = 'private-custom-tour';
insert into tour_content values
 ('private-custom-tour','highlight',0,'Cape Point'),('private-custom-tour','highlight',1,'The Winelands'),('private-custom-tour','highlight',2,'Table Mountain'),
 ('private-custom-tour','highlight',3,'The Garden Route'),('private-custom-tour','highlight',4,'Any other stops you request'),
 ('private-custom-tour','included',0,'Flexible transfers'),('private-custom-tour','included',1,'Itinerary planning'),
 ('private-custom-tour','included',2,'Private air-conditioned transport'),('private-custom-tour','included',3,'Dedicated qualified guide'),
 ('private-custom-tour','excluded',0,'Entrance fees, unless confirmed in your quote'),('private-custom-tour','excluded',1,'Meals, unless confirmed in your quote'),
 ('private-custom-tour','excluded',2,'Optional activities, unless confirmed in your quote'),
 ('private-custom-tour','practical',0,'Tell us your dates, group size and the places you would like to see. A quote is usually prepared within a day.'),
 ('private-custom-tour','practical',1,'Entrance fees, meals and optional activities are only included if your quote says so.');

-- Replace the lists of the nine tours with the published content.
delete from tour_highlights where tour_id in (select t.id from tours t where t.slug in (select distinct slug from tour_content));
delete from tour_itinerary where tour_id in (select t.id from tours t where t.slug in (select distinct slug from tour_content));
delete from tour_included where tour_id in (select t.id from tours t where t.slug in (select distinct slug from tour_content));
delete from tour_excluded where tour_id in (select t.id from tours t where t.slug in (select distinct slug from tour_content));
delete from tour_faqs where tour_id in (select t.id from tours t where t.slug in (select distinct slug from tour_content));
insert into tour_highlights (tour_id, item_order, value) select t.id, c.item_order, c.value from tour_content c join tours t on t.slug = c.slug where c.kind = 'highlight';
insert into tour_itinerary (tour_id, item_order, value) select t.id, c.item_order, c.value from tour_content c join tours t on t.slug = c.slug where c.kind = 'itinerary';
insert into tour_included (tour_id, item_order, value) select t.id, c.item_order, c.value from tour_content c join tours t on t.slug = c.slug where c.kind = 'included';
insert into tour_excluded (tour_id, item_order, value) select t.id, c.item_order, c.value from tour_content c join tours t on t.slug = c.slug where c.kind = 'excluded';
insert into tour_practical_info (tour_id, item_order, value) select t.id, c.item_order, c.value from tour_content c join tours t on t.slug = c.slug where c.kind = 'practical';
