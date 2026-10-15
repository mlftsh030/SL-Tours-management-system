-- Verified activity catalogue for the Explore pages and day itineraries. Every price, hours row and extra can
-- cite the source it was verified against, with the date it was checked and the dates it is valid for.
create table activities (
    id uuid primary key,
    slug varchar(160) not null unique,
    title varchar(180) not null,
    category varchar(30) not null check (category in ('NATURE', 'WILDLIFE', 'MUSEUM_HISTORY', 'ART_CULTURE', 'WINE', 'FOOD_MARKET',
        'BEACH', 'HIKING', 'SCENIC_DRIVE', 'FAMILY_LEISURE', 'GO_KARTING', 'ICE_SKATING', 'CINEMA', 'ADVENTURE')),
    status varchar(20) not null check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    summary varchar(400) not null,
    description varchar(4000) not null,
    area varchar(30) not null check (area in ('CITY_CENTRE', 'WATERFRONT', 'TABLE_MOUNTAIN', 'ATLANTIC_SEABOARD', 'SOUTHERN_SUBURBS',
        'CONSTANTIA', 'HOUT_BAY_CHAPMANS', 'FALSE_BAY', 'CAPE_POINT', 'NORTHERN_SUBURBS')),
    address varchar(300),
    official_url varchar(500),
    typical_duration_minutes integer not null check (typical_duration_minutes between 15 and 720),
    schedule_type varchar(20) not null check (schedule_type in ('OPENING_HOURS', 'SESSIONS', 'SCHEDULE_VARIES', 'ALWAYS_OPEN')),
    weather_dependent boolean not null default false,
    indoor boolean not null default false,
    min_age integer check (min_age between 0 and 99),
    accompaniment_rule varchar(300),
    booking_notes varchar(600),
    -- VERIFIED: price rules are taken from an official source. VARIES_AT_BOOKING: the operator says prices are set per
    -- booking. NOT_PUBLISHED: the operator publishes no prices. FREE: the official source says there is no charge.
    price_status varchar(30) not null check (price_status in ('VERIFIED', 'VARIES_AT_BOOKING', 'NOT_PUBLISHED', 'FREE')),
    verification_notes varchar(2000),
    last_verified_on date,
    verified_by varchar(180),
    hero_image varchar(220),
    hero_image_alt varchar(200),
    seo_title varchar(180),
    seo_description varchar(300),
    published_at timestamptz,
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create index idx_activities_status on activities(status, category);

create table activity_sources (
    id uuid primary key,
    activity_id uuid not null references activities(id) on delete cascade,
    url varchar(500) not null,
    publisher varchar(160) not null,
    source_type varchar(30) not null check (source_type in ('OFFICIAL_OPERATOR', 'TOURISM_AUTHORITY', 'PUBLIC_AUTHORITY', 'OTHER')),
    covers varchar(300) not null,
    accessed_on date not null,
    sort_order integer not null default 0
);
create index idx_activity_sources on activity_sources(activity_id, sort_order);

create table activity_price_rules (
    id uuid primary key,
    activity_id uuid not null references activities(id) on delete cascade,
    label varchar(160) not null,
    audience varchar(20) not null check (audience in ('EVERYONE', 'ADULT', 'CHILD', 'INFANT', 'SENIOR', 'STUDENT', 'FAMILY', 'GROUP')),
    min_age integer check (min_age between 0 and 120),
    max_age integer check (max_age between 0 and 120),
    residency varchar(20) not null default 'ANY' check (residency in ('ANY', 'SA_RESIDENT', 'SADC', 'AFRICAN', 'INTERNATIONAL')),
    unit varchar(20) not null check (unit in ('PER_PERSON', 'PER_VEHICLE', 'PER_GROUP', 'PER_ITEM')),
    amount_cents integer not null check (amount_cents >= 0),
    days_of_week varchar(40),
    time_from time,
    time_to time,
    conditions varchar(400),
    -- The rule used for estimates when several rules fit the same traveller (e.g. online versus ticket office).
    estimate_default boolean not null default false,
    effective_from date,
    effective_to date,
    source_id uuid references activity_sources(id) on delete set null,
    verified_on date not null,
    sort_order integer not null default 0,
    constraint chk_price_ages check (min_age is null or max_age is null or min_age <= max_age),
    constraint chk_price_dates check (effective_from is null or effective_to is null or effective_from <= effective_to)
);
create index idx_activity_prices on activity_price_rules(activity_id, sort_order);

create table activity_extras (
    id uuid primary key,
    activity_id uuid not null references activities(id) on delete cascade,
    label varchar(160) not null,
    amount_cents integer not null check (amount_cents >= 0),
    unit varchar(20) not null check (unit in ('PER_PERSON', 'PER_VEHICLE', 'PER_GROUP', 'PER_ITEM')),
    mandatory boolean not null,
    conditions varchar(400),
    effective_from date,
    effective_to date,
    source_id uuid references activity_sources(id) on delete set null,
    verified_on date not null,
    sort_order integer not null default 0
);

-- Seasonal opening hours (season_start/end as MM-DD, may wrap over the new year) or bookable sessions.
create table activity_hours (
    id uuid primary key,
    activity_id uuid not null references activities(id) on delete cascade,
    season_label varchar(80),
    season_start varchar(5) check (season_start ~ '^[0-1][0-9]-[0-3][0-9]$'),
    season_end varchar(5) check (season_end ~ '^[0-1][0-9]-[0-3][0-9]$'),
    days_of_week varchar(40),
    opens time not null,
    closes time not null,
    last_entry time,
    notes varchar(300),
    source_id uuid references activity_sources(id) on delete set null,
    sort_order integer not null default 0,
    constraint chk_hours_season check ((season_start is null) = (season_end is null))
);

create table activity_faqs (
    id uuid primary key,
    activity_id uuid not null references activities(id) on delete cascade,
    question varchar(300) not null,
    answer varchar(2000) not null,
    sort_order integer not null default 0
);

-- Planning assumptions used by day itineraries. These are SL Tours planning estimates, not measured routes.
create table planning_areas (
    area varchar(30) primary key,
    label varchar(80) not null,
    latitude numeric(8,5) not null,
    longitude numeric(8,5) not null
);
insert into planning_areas (area, label, latitude, longitude) values
    ('CITY_CENTRE', 'City centre, Gardens and Woodstock', -33.92500, 18.42300),
    ('WATERFRONT', 'V&A Waterfront', -33.90500, 18.42000),
    ('TABLE_MOUNTAIN', 'Table Mountain and Kloof Nek', -33.94800, 18.40300),
    ('ATLANTIC_SEABOARD', 'Atlantic Seaboard (Sea Point to Camps Bay)', -33.95000, 18.38000),
    ('SOUTHERN_SUBURBS', 'Southern Suburbs (Observatory to Newlands)', -33.95500, 18.47000),
    ('CONSTANTIA', 'Constantia', -34.02700, 18.42500),
    ('HOUT_BAY_CHAPMANS', 'Hout Bay and Chapman''s Peak', -34.06000, 18.36000),
    ('FALSE_BAY', 'False Bay (Muizenberg to Simon''s Town)', -34.15000, 18.45000),
    ('CAPE_POINT', 'Cape Point', -34.35700, 18.49700),
    ('NORTHERN_SUBURBS', 'Northern Suburbs (Century City and Goodwood)', -33.90000, 18.53000);

create table planning_settings (
    id smallint primary key check (id = 1),
    road_factor numeric(4,2) not null check (road_factor between 1 and 3),
    average_speed_kmh integer not null check (average_speed_kmh between 10 and 120),
    buffer_minutes integer not null check (buffer_minutes between 0 and 60),
    lunch_minutes integer not null check (lunch_minutes between 0 and 180),
    version bigint not null default 0,
    updated_by varchar(180) not null,
    updated_at timestamptz not null
);
-- Travel time = straight-line distance between area centres x road_factor at average_speed_kmh, plus a buffer for parking
-- and walking. Default values are SL Tours planning assumptions and are shown to visitors with every itinerary.
insert into planning_settings (id, road_factor, average_speed_kmh, buffer_minutes, lunch_minutes, updated_by, updated_at)
values (1, 1.40, 40, 10, 60, 'migration (planning assumption)', now());
