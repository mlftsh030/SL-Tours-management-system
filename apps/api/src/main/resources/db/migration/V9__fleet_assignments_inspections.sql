-- Fleet state, time-windowed assignments with overlap protection, and collection/return inspections.
create extension if not exists btree_gist;

-- Operational settings shared by availability and pricing. One row only.
create table operations_settings (
    id smallint primary key check (id = 1),
    turnaround_minutes integer not null check (turnaround_minutes between 0 and 1440),
    billing_increment_minutes integer not null check (billing_increment_minutes in (15, 30, 60)),
    included_km_per_hour integer check (included_km_per_hour > 0),
    excess_km_rate_cents integer check (excess_km_rate_cents > 0),
    overtime_hourly_rate_cents integer check (overtime_hourly_rate_cents > 0),
    version bigint not null default 0,
    updated_by varchar(180) not null,
    updated_at timestamptz not null
);
insert into operations_settings (id, turnaround_minutes, billing_increment_minutes, updated_by, updated_at)
values (1, 60, 60, 'migration', now());

-- Vehicles: operational condition, public listing and assignment state are independent.
alter table vehicles add column display_name varchar(120);
alter table vehicles add column category varchar(30);
alter table vehicles add column operational_status varchar(20);
alter table vehicles add column listing_status varchar(20);
alter table vehicles add column luggage_capacity integer check (luggage_capacity >= 0);
alter table vehicles add column public_description varchar(500);
alter table vehicles add column operating_notes varchar(1000);
alter table vehicles add column version bigint not null default 0;
alter table vehicles add column updated_at timestamptz;

update vehicles set
    display_name = type,
    category = case id
        when '70000000-0000-0000-0000-000000000001' then 'LUXURY_VAN'
        when '70000000-0000-0000-0000-000000000002' then 'MINIBUS'
        when '70000000-0000-0000-0000-000000000003' then 'VAN'
        when '70000000-0000-0000-0000-000000000004' then 'SEDAN'
        else case when capacity <= 4 then 'SEDAN' when capacity <= 8 then 'VAN' else 'MINIBUS' end end,
    operational_status = case when status = 'RETIRED' then 'RETIRED' else 'ACTIVE' end,
    -- Listing on the public website is an explicit admin decision.
    listing_status = 'HIDDEN',
    updated_at = created_at;

alter table vehicles alter column display_name set not null;
alter table vehicles alter column category set not null;
alter table vehicles alter column operational_status set not null;
alter table vehicles alter column listing_status set not null;
alter table vehicles alter column updated_at set not null;
alter table vehicles add constraint vehicles_category_check check (category in ('SEDAN', 'SUV', 'VAN', 'LUXURY_VAN', 'MINIBUS', 'COACH'));
alter table vehicles add constraint vehicles_operational_check check (operational_status in ('ACTIVE', 'OUT_OF_SERVICE', 'RETIRED'));
alter table vehicles add constraint vehicles_listing_check check (listing_status in ('LISTED', 'HIDDEN'));
-- The legacy status column mixed several concepts; it is kept for history but no longer written.
alter table vehicles alter column status drop not null;
comment on column vehicles.status is 'Legacy. Superseded by operational_status, listing_status and vehicle_restrictions.';

create table vehicle_amenities (
    vehicle_id uuid not null references vehicles(id) on delete cascade,
    item_order integer not null,
    value varchar(80) not null,
    primary key (vehicle_id, item_order)
);

create table vehicle_images (
    id uuid primary key,
    vehicle_id uuid not null references vehicles(id),
    media_id uuid not null unique references media_files(id),
    alt_text varchar(200) not null,
    sort_order integer not null default 0,
    created_at timestamptz not null
);
create index idx_vehicle_images_vehicle on vehicle_images(vehicle_id, sort_order);

-- An open restriction (cleared_at is null) blocks collection, availability and booking.
create table vehicle_restrictions (
    id uuid primary key,
    vehicle_id uuid not null references vehicles(id),
    restriction_type varchar(20) not null check (restriction_type in ('SAFETY', 'MAINTENANCE')),
    reason varchar(1000) not null,
    source_inspection_id uuid,
    opened_by varchar(180) not null,
    opened_at timestamptz not null,
    cleared_by varchar(180),
    cleared_at timestamptz,
    clearance_note varchar(1000),
    version bigint not null default 0,
    constraint chk_restriction_clearance check ((cleared_at is null) = (cleared_by is null) and (cleared_at is null) = (clearance_note is null))
);
create index idx_vehicle_restrictions_open on vehicle_restrictions(vehicle_id) where cleared_at is null;

insert into vehicle_restrictions (id, vehicle_id, restriction_type, reason, opened_by, opened_at)
select gen_random_uuid(), id, 'MAINTENANCE', coalesce(nullif(notes, ''), 'Marked for maintenance before restrictions were introduced'),
       'migration', now()
from vehicles where status = 'MAINTENANCE';

-- Assignments get explicit time windows. blocked_until includes turnaround and drives overlap protection.
alter table assignments add column starts_at timestamptz;
alter table assignments add column ends_at timestamptz;
alter table assignments add column blocked_until timestamptz;
alter table assignments add column released_at timestamptz;
alter table assignments add column released_by varchar(180);
alter table assignments add column cancellation_reason varchar(500);
alter table assignments add column version bigint not null default 0;
alter table assignments add column updated_at timestamptz;

-- Existing tour assignments cover the travel day (08:00-18:00 Cape Town time) plus the default turnaround.
update assignments a set
    starts_at = (b.travel_date + time '08:00') at time zone 'Africa/Johannesburg',
    ends_at = (b.travel_date + time '18:00') at time zone 'Africa/Johannesburg',
    blocked_until = ((b.travel_date + time '18:00') at time zone 'Africa/Johannesburg') + interval '60 minutes',
    status = case a.status when 'CANCELLED' then 'CANCELLED' when 'COMPLETED' then 'CLOSED' else 'SCHEDULED' end,
    updated_at = a.created_at
from bookings b where b.id = a.booking_id;

alter table assignments alter column starts_at set not null;
alter table assignments alter column ends_at set not null;
alter table assignments alter column blocked_until set not null;
alter table assignments alter column updated_at set not null;
alter table assignments add constraint assignments_window_check check (ends_at > starts_at and blocked_until >= ends_at);
alter table assignments add constraint assignments_status_check
    check (status in ('SCHEDULED', 'AWAITING_RELEASE', 'RELEASED', 'RETURNED', 'CLOSED', 'CANCELLED'));
-- Final guard against double-booking, including concurrent confirmations.
alter table assignments add constraint assignments_vehicle_no_overlap
    exclude using gist (vehicle_id with =, tstzrange(starts_at, blocked_until) with &&)
    where (vehicle_id is not null and status <> 'CANCELLED');
alter table assignments add constraint assignments_driver_no_overlap
    exclude using gist (driver_id with =, tstzrange(starts_at, blocked_until) with &&)
    where (driver_id is not null and status <> 'CANCELLED');
create unique index ux_assignments_active_booking on assignments(booking_id) where status <> 'CANCELLED';
create index idx_assignments_driver on assignments(driver_id, starts_at);

create table vehicle_inspections (
    id uuid primary key,
    assignment_id uuid not null references assignments(id),
    inspection_type varchar(20) not null check (inspection_type in ('COLLECTION', 'RETURN')),
    driver_id uuid not null references drivers(id),
    vehicle_id uuid not null references vehicles(id),
    submitted_by varchar(180) not null,
    submitted_at timestamptz not null,
    odometer_km integer not null check (odometer_km >= 0),
    fuel_level varchar(20) not null check (fuel_level in ('EMPTY', 'QUARTER', 'HALF', 'THREE_QUARTERS', 'FULL')),
    notes varchar(2000),
    checklist jsonb not null,
    has_defect boolean not null,
    has_safety_defect boolean not null,
    selfie_media_id uuid references media_files(id),
    review_status varchar(20) not null check (review_status in ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    reviewed_by varchar(180),
    reviewed_at timestamptz,
    review_notes varchar(1000),
    version bigint not null default 0,
    constraint chk_collection_selfie check (inspection_type = 'RETURN' or selfie_media_id is not null)
);
-- One live collection and one live return per assignment; a rejected inspection may be resubmitted.
create unique index ux_inspections_live on vehicle_inspections(assignment_id, inspection_type) where review_status <> 'REJECTED';
create index idx_inspections_review on vehicle_inspections(review_status, submitted_at);

alter table vehicle_restrictions add constraint fk_restriction_inspection
    foreign key (source_inspection_id) references vehicle_inspections(id);

create table inspection_photos (
    id uuid primary key,
    inspection_id uuid not null references vehicle_inspections(id),
    media_id uuid not null unique references media_files(id),
    photo_kind varchar(20) not null check (photo_kind in ('VEHICLE', 'DEFECT')),
    checklist_item varchar(40),
    sort_order integer not null
);
create index idx_inspection_photos on inspection_photos(inspection_id, sort_order);
