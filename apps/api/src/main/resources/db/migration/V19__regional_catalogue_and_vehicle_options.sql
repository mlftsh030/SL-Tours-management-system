-- Things to Do across the Western Cape, and the vehicle options customers can request for private trips.
--
-- Facts are tracked separately so an unknown in one does not hide what is known in another:
--   existence_verified_on   the listing was confirmed to exist on an official page;
--   operating_status/_note  whether it is currently open, partly closed, seasonal or closed (with the date checked);
--   activity_hours          opening hours, each with its own verified_on (V15);
--   participation_rules     published age, height, weight or group limits (V15);
--   accessibility_info      accessibility as published by the operator (null = not published);
--   activity_media          each picture or video with its credit, licence and whether it shows the exact place;
--   activity_sources        every source with the date it was accessed (V13).
-- Things to Do pages show no prices. Price rules stay in the database for the day planner and are not changed here.

alter table activities alter column area drop not null;
alter table activities add column region varchar(30) not null default 'CAPE_TOWN'
    check (region in ('CAPE_TOWN', 'CAPE_WINELANDS', 'OVERBERG', 'WEST_COAST', 'GARDEN_ROUTE_KLEIN_KAROO', 'CAPE_KAROO'));
-- Planning areas are Cape Town districts; listings elsewhere have none and are never placed in a day plan.
alter table activities add constraint chk_activity_area_region check (area is null or region = 'CAPE_TOWN');
alter table activities add column town varchar(120);
alter table activities add column latitude numeric(8,5);
alter table activities add column longitude numeric(8,5);
-- A rough Western Cape bounding box guards against typing errors; each location was also checked against the province boundary.
alter table activities add constraint chk_activity_coordinates check ((latitude is null) = (longitude is null)
    and (latitude is null or (latitude between -35.0 and -30.3 and longitude between 17.5 and 24.4)));
alter table activities add column kind varchar(20) not null default 'ATTRACTION' check (kind in ('ATTRACTION', 'EVENT'));
alter table activities add column event_schedule varchar(400);
alter table activities add column what_to_expect varchar(2000);
alter table activities add column practical_info varchar(2000);
alter table activities add column seasonal_info varchar(1000);
alter table activities add column accessibility_info varchar(1000);
alter table activities add column operating_status varchar(20) not null default 'OPEN'
    check (operating_status in ('OPEN', 'PARTLY_CLOSED', 'SEASONAL', 'CLOSED', 'UNKNOWN'));
alter table activities add column operating_note varchar(400);
alter table activities add column operating_checked_on date;
alter table activities add column existence_verified_on date;

-- NOT_RECORDED: SL Tours has not recorded prices for this listing (not a statement that it is free).
alter table activities drop constraint activities_price_status_check;
alter table activities add constraint activities_price_status_check
    check (price_status in ('VERIFIED', 'VARIES_AT_BOOKING', 'NOT_PUBLISHED', 'FREE', 'NOT_RECORDED'));
-- CHECK_OPERATOR: hours were not published on the pages checked; visitors are told to confirm with the operator.
alter table activities drop constraint activities_schedule_type_check;
alter table activities add constraint activities_schedule_type_check
    check (schedule_type in ('OPENING_HOURS', 'SESSIONS', 'SCHEDULE_VARIES', 'ALWAYS_OPEN', 'CHECK_OPERATOR'));

update activities set existence_verified_on = last_verified_on, operating_checked_on = last_verified_on;

-- Activity categories, separate from regions. A listing can have several.
create table activity_themes (
    activity_id uuid not null references activities(id) on delete cascade,
    theme varchar(30) not null check (theme in ('ART', 'HERITAGE', 'HIKING', 'ADVENTURE', 'OCEAN', 'WILDLIFE', 'FOOD', 'WINE', 'WORKSHOPS',
        'FAMILY', 'SCENIC', 'CYCLING_RIDING', 'WELLNESS', 'EVENTS', 'ESCAPES')),
    primary key (activity_id, theme)
);

-- Pictures and videos. Images are served from /assets; videos are privacy-enhanced YouTube embeds from official channels.
-- permission: SL_TOURS_OWN (SL Tours' own photo), OPEN_LICENCE (e.g. Creative Commons, credit shown), PERMISSION_GRANTED
-- (written permission on file) or PENDING (never shown publicly). depiction: EXACT shows this place; ILLUSTRATIVE shows the
-- kind of activity and is labelled as such.
create table activity_media (
    id uuid primary key,
    activity_id uuid not null references activities(id) on delete cascade,
    kind varchar(10) not null check (kind in ('IMAGE', 'VIDEO')),
    url varchar(500) not null,
    thumbnail_url varchar(500),
    width integer,
    height integer,
    alt_text varchar(300) not null,
    caption varchar(300),
    credit varchar(300) not null,
    source_page varchar(500) not null,
    licence varchar(120) not null,
    licence_url varchar(500),
    permission varchar(20) not null check (permission in ('SL_TOURS_OWN', 'OPEN_LICENCE', 'PERMISSION_GRANTED', 'PENDING')),
    depiction varchar(20) not null check (depiction in ('EXACT', 'ILLUSTRATIVE')),
    checked_on date not null,
    sort_order integer not null default 0,
    constraint chk_media_url check ((kind = 'IMAGE' and url ~ '^/assets/[A-Za-z0-9_./-]+\.(jpg|jpeg|png|webp|avif)$' and url !~ '\.\.')
        or (kind = 'VIDEO' and url ~ '^https://www\.youtube-nocookie\.com/embed/[A-Za-z0-9_-]{6,20}$'))
);
create index idx_activity_media on activity_media(activity_id, sort_order);

-- SL Tours' own photos already chosen for activities become media rows.
insert into activity_media (id, activity_id, kind, url, alt_text, credit, source_page, licence, permission, depiction, checked_on, sort_order)
select gen_random_uuid(), id, 'IMAGE', hero_image, coalesce(hero_image_alt, title), 'SL Tours', 'https://sl-tours.com/gallery',
    'SL Tours own photograph', 'SL_TOURS_OWN', 'EXACT', coalesce(last_verified_on, current_date), 0
from activities where hero_image is not null;

-- The vehicles customers can ask for. These are request options, not fleet records: fleet vehicles (with registrations)
-- are assigned when a trip is confirmed, and availability is always subject to confirmation.
-- passenger_capacity is the customer capacity requested by SL Tours, excluding the driver. capacity_status says whether
-- it has been checked against the manufacturer's seating (CONFIRMED) or still needs confirming (TO_BE_CONFIRMED).
create table vehicle_options (
    id uuid primary key,
    slug varchar(80) not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name varchar(120) not null,
    type_label varchar(60) not null,
    fleet_category varchar(20) not null check (fleet_category in ('SEDAN', 'SUV', 'VAN', 'LUXURY_VAN', 'MINIBUS', 'COACH')),
    passenger_capacity integer not null check (passenger_capacity between 1 and 80),
    capacity_status varchar(20) not null check (capacity_status in ('CONFIRMED', 'TO_BE_CONFIRMED')),
    capacity_note varchar(600) not null,
    description varchar(600) not null,
    image_url varchar(300) not null check (image_url ~ '^/assets/[A-Za-z0-9_./-]+\.(jpg|jpeg|png|webp)$'),
    image_thumbnail_url varchar(300),
    image_alt varchar(300) not null,
    image_kind varchar(20) not null check (image_kind in ('SL_TOURS_PHOTO', 'ILLUSTRATIVE')),
    image_credit varchar(300) not null,
    image_source_page varchar(500) not null,
    image_licence varchar(120) not null,
    image_licence_url varchar(500),
    spec_source_url varchar(500),
    spec_source_publisher varchar(160),
    checked_on date not null,
    sort_order integer not null,
    published boolean not null default true
);
create index idx_vehicle_options on vehicle_options(published, sort_order);
