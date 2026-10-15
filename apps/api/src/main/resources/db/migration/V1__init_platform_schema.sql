create table tours (
    id uuid primary key,
    slug varchar(160) not null unique,
    title varchar(180) not null,
    short_description varchar(500) not null,
    full_description varchar(2500) not null,
    duration varchar(80) not null,
    base_price_cents integer not null check (base_price_cents >= 0),
    pricing_type varchar(40) not null,
    minimum_guests integer not null check (minimum_guests >= 1),
    maximum_guests integer not null check (maximum_guests >= minimum_guests),
    pickup_available boolean not null default true,
    featured boolean not null default false,
    status varchar(40) not null,
    category varchar(40) not null,
    hero_image varchar(220) not null,
    seo_title varchar(220) not null,
    seo_description varchar(500) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table tour_locations (
    tour_id uuid not null references tours(id) on delete cascade,
    item_order integer not null,
    value varchar(255) not null,
    primary key (tour_id, item_order)
);

create table tour_tags (
    tour_id uuid not null references tours(id) on delete cascade,
    item_order integer not null,
    value varchar(255) not null,
    primary key (tour_id, item_order)
);

create table tour_highlights (
    tour_id uuid not null references tours(id) on delete cascade,
    item_order integer not null,
    value varchar(255) not null,
    primary key (tour_id, item_order)
);

create table tour_included (
    tour_id uuid not null references tours(id) on delete cascade,
    item_order integer not null,
    value varchar(255) not null,
    primary key (tour_id, item_order)
);

create table tour_excluded (
    tour_id uuid not null references tours(id) on delete cascade,
    item_order integer not null,
    value varchar(255) not null,
    primary key (tour_id, item_order)
);

create table tour_itinerary (
    tour_id uuid not null references tours(id) on delete cascade,
    item_order integer not null,
    value varchar(500) not null,
    primary key (tour_id, item_order)
);

create table tour_faqs (
    tour_id uuid not null references tours(id) on delete cascade,
    item_order integer not null,
    value varchar(800) not null,
    primary key (tour_id, item_order)
);

create table customers (
    id uuid primary key,
    name varchar(160) not null,
    email varchar(180) not null unique,
    phone varchar(80),
    country varchar(80),
    preferences text,
    notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table bookings (
    id uuid primary key,
    reference varchar(32) not null unique,
    tour_id uuid not null references tours(id),
    customer_id uuid references customers(id),
    travel_date date not null,
    guests integer not null check (guests > 0),
    customer_name varchar(160) not null,
    customer_email varchar(180) not null,
    customer_phone varchar(80),
    customer_country varchar(80),
    pickup_location varchar(240),
    notes varchar(1200),
    status varchar(40) not null,
    total_amount_cents integer not null check (total_amount_cents >= 0),
    amount_paid_cents integer not null check (amount_paid_cents >= 0),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_bookings_travel_date on bookings(travel_date);
create index idx_bookings_status on bookings(status);

create table trip_planning_requests (
    id uuid primary key,
    reference varchar(32) not null unique,
    customer_name varchar(160) not null,
    customer_email varchar(180) not null,
    customer_phone varchar(80),
    arrival_date date not null,
    departure_date date not null,
    adults integer not null check (adults >= 1),
    children integer not null check (children >= 0),
    group_type varchar(80) not null,
    notes varchar(1600),
    status varchar(40) not null,
    created_at timestamptz not null,
    constraint chk_trip_planning_dates check (departure_date >= arrival_date)
);

create table trip_planning_interests (
    request_id uuid not null references trip_planning_requests(id) on delete cascade,
    item_order integer not null,
    value varchar(255) not null,
    primary key (request_id, item_order)
);

create table trip_planning_services (
    request_id uuid not null references trip_planning_requests(id) on delete cascade,
    item_order integer not null,
    value varchar(255) not null,
    primary key (request_id, item_order)
);

create table guides (
    id uuid primary key,
    name varchar(160) not null,
    contact varchar(120),
    status varchar(40) not null,
    languages varchar(240),
    specialties varchar(240),
    notes text,
    created_at timestamptz not null
);

create table drivers (
    id uuid primary key,
    name varchar(160) not null,
    contact varchar(120),
    status varchar(40) not null,
    notes text,
    created_at timestamptz not null
);

create table vehicles (
    id uuid primary key,
    registration varchar(40) not null unique,
    type varchar(80) not null,
    capacity integer not null check (capacity > 0),
    status varchar(40) not null,
    notes text,
    created_at timestamptz not null
);

create table assignments (
    id uuid primary key,
    booking_id uuid not null references bookings(id) on delete cascade,
    guide_id uuid references guides(id),
    driver_id uuid references drivers(id),
    vehicle_id uuid references vehicles(id),
    status varchar(40) not null,
    created_at timestamptz not null
);

create table quotes (
    id uuid primary key,
    reference varchar(32) not null unique,
    customer_id uuid references customers(id),
    trip_planning_request_id uuid references trip_planning_requests(id),
    status varchar(40) not null,
    subtotal_cents integer not null default 0,
    discount_cents integer not null default 0,
    tax_cents integer not null default 0,
    total_cents integer not null default 0,
    valid_until date,
    notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table quote_items (
    id uuid primary key,
    quote_id uuid not null references quotes(id) on delete cascade,
    description varchar(500) not null,
    quantity integer not null check (quantity > 0),
    unit_price_cents integer not null check (unit_price_cents >= 0),
    line_total_cents integer not null check (line_total_cents >= 0)
);

create table payments (
    id uuid primary key,
    booking_id uuid references bookings(id),
    quote_id uuid references quotes(id),
    method varchar(60) not null,
    status varchar(40) not null,
    amount_cents integer not null check (amount_cents >= 0),
    external_reference varchar(120),
    recorded_by varchar(120),
    recorded_at timestamptz not null,
    notes text
);

create table trips (
    id uuid primary key,
    booking_id uuid references bookings(id),
    customer_id uuid references customers(id),
    title varchar(180) not null,
    starts_on date not null,
    ends_on date not null,
    status varchar(40) not null,
    secure_portal_token_hash varchar(255),
    created_at timestamptz not null,
    constraint chk_trip_dates check (ends_on >= starts_on)
);

create table trip_days (
    id uuid primary key,
    trip_id uuid not null references trips(id) on delete cascade,
    trip_date date not null,
    title varchar(180) not null
);

create table itinerary_items (
    id uuid primary key,
    trip_day_id uuid not null references trip_days(id) on delete cascade,
    item_time time,
    title varchar(180) not null,
    detail varchar(800),
    sort_order integer not null
);

create table notifications (
    id uuid primary key,
    booking_id uuid references bookings(id),
    channel varchar(40) not null,
    type varchar(80) not null,
    status varchar(40) not null,
    recipient varchar(180) not null,
    created_at timestamptz not null,
    sent_at timestamptz
);

create table review_requests (
    id uuid primary key,
    booking_id uuid not null references bookings(id),
    status varchar(40) not null,
    review_url varchar(500),
    created_at timestamptz not null,
    sent_at timestamptz
);
