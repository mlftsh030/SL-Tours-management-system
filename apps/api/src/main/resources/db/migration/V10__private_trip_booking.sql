-- Hourly private trips: bookings become typed, prices are configured per category (or vehicle) and
-- every request keeps the price breakdown it was quoted.
alter table bookings add column booking_type varchar(20) not null default 'TOUR' check (booking_type in ('TOUR', 'PRIVATE_TRIP'));
alter table bookings alter column tour_id drop not null;
alter table bookings add constraint bookings_tour_required check (booking_type <> 'TOUR' or tour_id is not null);
alter table bookings add column idempotency_key uuid unique;

-- Category rates. SAMPLE rates exist only so development can exercise pricing; they are never shown
-- or charged as live prices unless an administrator confirms a real rate (rate_status = 'LIVE').
create table private_trip_rates (
    category varchar(30) primary key check (category in ('SEDAN', 'SUV', 'VAN', 'LUXURY_VAN', 'MINIBUS', 'COACH')),
    hourly_rate_cents integer check (hourly_rate_cents > 0),
    minimum_minutes integer not null check (minimum_minutes between 60 and 1440),
    rate_status varchar(10) not null check (rate_status in ('SAMPLE', 'LIVE')),
    version bigint not null default 0,
    updated_by varchar(180) not null,
    updated_at timestamptz not null
);
insert into private_trip_rates (category, hourly_rate_cents, minimum_minutes, rate_status, updated_by, updated_at) values
    ('SEDAN', 45000, 120, 'SAMPLE', 'migration (sample rate)', now()),
    ('SUV', 55000, 120, 'SAMPLE', 'migration (sample rate)', now()),
    ('VAN', 65000, 180, 'SAMPLE', 'migration (sample rate)', now()),
    ('LUXURY_VAN', 85000, 180, 'SAMPLE', 'migration (sample rate)', now()),
    ('MINIBUS', 95000, 180, 'SAMPLE', 'migration (sample rate)', now()),
    ('COACH', null, 240, 'SAMPLE', 'migration (sample rate)', now());

-- Optional per-vehicle override, always entered by an administrator.
alter table vehicles add column hourly_rate_cents integer check (hourly_rate_cents > 0);
alter table vehicles add column minimum_minutes integer check (minimum_minutes between 60 and 1440);

create table private_trip_fees (
    id uuid primary key,
    name varchar(120) not null,
    fee_type varchar(20) not null check (fee_type in ('FIXED', 'PER_BILLABLE_HOUR')),
    amount_cents integer not null check (amount_cents > 0),
    active boolean not null default true,
    sort_order integer not null default 0,
    version bigint not null default 0,
    updated_by varchar(180) not null,
    updated_at timestamptz not null
);

create table private_trip_details (
    booking_id uuid primary key references bookings(id) on delete cascade,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    duration_minutes integer not null check (duration_minutes between 60 and 1440),
    destination varchar(1200) not null,
    special_requests varchar(1200),
    requested_vehicle_id uuid references vehicles(id),
    requested_category varchar(30) not null,
    -- Null for manually priced trips (rate_source = 'MANUAL'), e.g. converted quote requests.
    hourly_rate_cents integer check (hourly_rate_cents > 0),
    rate_source varchar(20) not null check (rate_source in ('VEHICLE', 'CATEGORY', 'MANUAL')),
    rate_is_sample boolean not null,
    minimum_minutes integer not null,
    billable_minutes integer not null,
    billing_increment_minutes integer not null,
    base_amount_cents integer not null check (base_amount_cents >= 0),
    fees_cents integer not null check (fees_cents >= 0),
    total_cents integer not null check (total_cents >= 0),
    price_breakdown jsonb not null,
    constraint chk_private_trip_window check (ends_at > starts_at),
    constraint chk_private_trip_rate check ((rate_source = 'MANUAL') = (hourly_rate_cents is null))
);
create index idx_private_trip_starts on private_trip_details(starts_at);
