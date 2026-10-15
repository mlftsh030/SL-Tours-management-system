-- Website enquiries inbox. Every website enquiry is persisted before the customer sees a confirmation.
create table enquiries (
    id uuid primary key,
    reference varchar(32) not null unique,
    source varchar(30) not null check (source in ('CONTACT_FORM', 'TRIP_PLANNER', 'PRIVATE_TRIP_QUOTE')),
    enquiry_type varchar(20) not null check (enquiry_type in ('GENERAL', 'TOUR', 'PRIVATE_TRIP')),
    customer_name varchar(160) not null,
    customer_email varchar(180) not null,
    customer_phone varchar(80),
    message varchar(4000) not null,
    tour_id uuid references tours(id),
    requested_date date,
    requested_time time,
    duration_minutes integer check (duration_minutes > 0),
    pickup_location varchar(240),
    destination varchar(1200),
    passengers integer check (passengers between 1 and 60),
    status varchar(30) not null check (status in ('NEW', 'IN_PROGRESS', 'AWAITING_CUSTOMER', 'CONVERTED', 'CLOSED')),
    trip_planning_request_id uuid unique references trip_planning_requests(id),
    converted_booking_id uuid references bookings(id),
    idempotency_key uuid unique,
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create index idx_enquiries_status_created on enquiries(status, created_at desc);
create index idx_enquiries_created on enquiries(created_at desc);
create index idx_enquiries_email on enquiries(lower(customer_email));

create table enquiry_notes (
    id uuid primary key,
    enquiry_id uuid not null references enquiries(id) on delete cascade,
    author varchar(180) not null,
    body varchar(2000) not null,
    created_at timestamptz not null
);
create index idx_enquiry_notes_enquiry on enquiry_notes(enquiry_id, created_at);

-- Existing trip planner submissions become inbox enquiries; the original records are kept unchanged.
insert into enquiries (id, reference, source, enquiry_type, customer_name, customer_email, customer_phone, message,
    requested_date, passengers, status, trip_planning_request_id, version, created_at, updated_at)
select gen_random_uuid(), r.reference, 'TRIP_PLANNER', 'GENERAL', r.customer_name, r.customer_email, r.customer_phone,
    left(concat_ws(E'\n',
        'Trip planner request: ' || r.arrival_date || ' to ' || r.departure_date,
        'Travellers: ' || r.adults || ' adult(s), ' || r.children || ' child(ren); group: ' || r.group_type,
        'Interests: ' || coalesce((select string_agg(i.value, ', ' order by i.item_order) from trip_planning_interests i where i.request_id = r.id), '—'),
        'Services: ' || coalesce((select string_agg(s.value, ', ' order by s.item_order) from trip_planning_services s where s.request_id = r.id), '—'),
        nullif(r.notes, '')), 4000),
    r.arrival_date, least(r.adults + r.children, 60),
    case r.status when 'NEW' then 'NEW' when 'QUOTE_SENT' then 'AWAITING_CUSTOMER' when 'CONVERTED' then 'CONVERTED'
        when 'CLOSED' then 'CLOSED' else 'IN_PROGRESS' end,
    r.id, 0, r.created_at, r.created_at
from trip_planning_requests r;
