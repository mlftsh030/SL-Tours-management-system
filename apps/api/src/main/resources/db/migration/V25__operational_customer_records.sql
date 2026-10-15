-- Operational customer records and the enquiry -> quotation -> booking -> invoice -> payment chain.
-- See docs/database/DATA_MODEL.md for the reasoning behind each change.

-- ---------------------------------------------------------------------------------------------------------------
-- 1. Customers: one operational record per customer, separate from login accounts (user_accounts).
--    The V1 table is reused. Email becomes optional (some invoices have none) and unique case-insensitively among
--    live records. A customer is linked to a login account only once that account has verified the address.
-- ---------------------------------------------------------------------------------------------------------------
alter table customers drop constraint customers_email_key;
alter table customers alter column email drop not null;
alter table customers add column account_id uuid unique references user_accounts(id);
alter table customers add column merged_into_id uuid references customers(id);
alter table customers add column merged_at timestamptz;
alter table customers add column merged_by varchar(180);
alter table customers add column phone_digits varchar(20);
alter table customers add column version bigint not null default 0;
alter table customers add column updated_by varchar(180);
alter table customers add constraint customers_email_lower check (email is null or email = lower(btrim(email)));
alter table customers add constraint customers_not_merged_into_self check (merged_into_id is null or merged_into_id <> id);
create unique index ux_customers_email on customers(email) where email is not null and merged_into_id is null;
create index idx_customers_name on customers(lower(name));
create index idx_customers_phone on customers(phone_digits) where phone_digits is not null;

-- Every merge is recorded; nothing is deleted. Moved record ids are kept so a merge can be reviewed later.
create table customer_merges (
    id uuid primary key,
    survivor_id uuid not null references customers(id),
    merged_id uuid not null references customers(id),
    reason varchar(500) not null,
    moved jsonb not null,
    merged_by varchar(180) not null,
    merged_at timestamptz not null,
    check (survivor_id <> merged_id)
);

alter table enquiries add column customer_id uuid references customers(id);
alter table billing_documents add column customer_id uuid references customers(id);
alter table trip_planning_requests add column customer_id uuid references customers(id);
create index idx_bookings_customer on bookings(customer_id);
create index idx_enquiries_customer on enquiries(customer_id);
create index idx_billing_documents_customer_id on billing_documents(customer_id);
create index idx_trip_planning_customer on trip_planning_requests(customer_id);

-- Digits only, for duplicate detection. Leading 0 is read as South African (+27).
create function sl_phone_digits(phone text) returns varchar language sql immutable as $$
    select case
        when phone is null then null
        when length(regexp_replace(phone, '\D', '', 'g')) < 7 then null
        when regexp_replace(phone, '\D', '', 'g') ~ '^0' then '27' || substr(regexp_replace(phone, '\D', '', 'g'), 2)
        else left(regexp_replace(phone, '\D', '', 'g'), 20)
    end
$$;

-- The live customer for an email address, created when it does not exist yet. Merged records resolve to the survivor.
-- Matching is by email only: names and phone numbers never join two people automatically.
create function sl_customer_for(p_email text, p_name text, p_phone text) returns uuid language plpgsql as $$
declare
    v_email text := lower(btrim(p_email));
    v_id uuid;
begin
    if v_email is null or v_email = '' then return null; end if;
    select coalesce(merged_into_id, id) into v_id from customers where email = v_email order by merged_into_id nulls first limit 1;
    if v_id is null then
        insert into customers (id, name, email, phone, phone_digits, created_at, updated_at)
        values (gen_random_uuid(), coalesce(nullif(btrim(p_name), ''), v_email), v_email, nullif(btrim(p_phone), ''), sl_phone_digits(p_phone), now(), now())
        on conflict (email) where email is not null and merged_into_id is null do nothing
        returning id into v_id;
        if v_id is null then select id into v_id from customers where email = v_email and merged_into_id is null; end if;
    else
        -- Fill a missing phone; never overwrite details staff may have corrected.
        update customers set phone = nullif(btrim(p_phone), ''), phone_digits = sl_phone_digits(p_phone), updated_at = now()
        where id = v_id and phone is null and nullif(btrim(p_phone), '') is not null;
    end if;
    return v_id;
end $$;

-- Every record with a customer email gets its customer, whichever code path wrote it. Changing the email on a record
-- moves it to that address's customer unless staff linked it to a customer by hand.
create function sl_link_customer() returns trigger language plpgsql as $$
declare
    v_phone text;
begin
    -- Separate statements: each table has only one of these columns.
    if TG_TABLE_NAME = 'billing_documents' then v_phone := NEW.customer_whatsapp; else v_phone := NEW.customer_phone; end if;
    if TG_OP = 'INSERT' then
        if NEW.customer_id is null then NEW.customer_id := sl_customer_for(NEW.customer_email, NEW.customer_name, v_phone); end if;
    elsif lower(coalesce(NEW.customer_email, '')) <> lower(coalesce(OLD.customer_email, '')) and NEW.customer_id is not distinct from OLD.customer_id then
        NEW.customer_id := sl_customer_for(NEW.customer_email, NEW.customer_name, v_phone);
    end if;
    return NEW;
end $$;

create trigger trg_bookings_customer before insert or update of customer_email on bookings
    for each row execute function sl_link_customer();
create trigger trg_enquiries_customer before insert or update of customer_email on enquiries
    for each row execute function sl_link_customer();
create trigger trg_billing_documents_customer before insert or update of customer_email on billing_documents
    for each row execute function sl_link_customer();
create trigger trg_trip_planning_customer before insert or update of customer_email on trip_planning_requests
    for each row execute function sl_link_customer();

-- Backfill: one customer per distinct email across existing records, named from the most recent record.
insert into customers (id, name, email, phone, phone_digits, demo_data, created_at, updated_at)
select gen_random_uuid(), x.name, x.email, x.phone, sl_phone_digits(x.phone), x.demo, x.first_seen, now()
from (
    select distinct on (email) email, name, phone, demo,
           min(created_at) over (partition by email) as first_seen
    from (
        select lower(customer_email) email, customer_name name, customer_phone phone, demo_data demo, created_at from bookings
        union all select lower(customer_email), customer_name, customer_phone, demo_data, created_at from enquiries
        union all select lower(customer_email), customer_name, customer_whatsapp, false, created_at from billing_documents where customer_email is not null
        union all select lower(customer_email), customer_name, customer_phone, demo_data, created_at from trip_planning_requests
        union all select email, name, phone, false, created_at from user_accounts where role = 'CUSTOMER'
    ) r
    where email is not null and email <> ''
    order by email, created_at desc
) x
where not exists (select 1 from customers c where c.email = x.email);

update bookings b set customer_id = c.id from customers c where c.email = lower(b.customer_email) and b.customer_id is null;
update enquiries e set customer_id = c.id from customers c where c.email = lower(e.customer_email);
update billing_documents d set customer_id = c.id from customers c where c.email = lower(d.customer_email);
update trip_planning_requests t set customer_id = c.id from customers c where c.email = lower(t.customer_email);
update customers set phone_digits = sl_phone_digits(phone) where phone_digits is null;
-- Login accounts are attached only where the account holder proved the address.
update customers c set account_id = a.id from user_accounts a
where a.email = c.email and a.role = 'CUSTOMER' and a.email_verified_at is not null and c.account_id is null;

-- ---------------------------------------------------------------------------------------------------------------
-- 2. The customer journey: enquiry -> quotation -> booking -> invoice -> payment.
-- ---------------------------------------------------------------------------------------------------------------
-- A quotation (or invoice) can name the enquiry it answers.
alter table billing_documents add column enquiry_id uuid references enquiries(id);
create index idx_billing_documents_enquiry on billing_documents(enquiry_id) where enquiry_id is not null;

-- A booking created from an accepted quotation keeps that link; converting again returns the same booking.
alter table bookings add column source_quotation_id uuid references billing_documents(id);
create unique index ux_bookings_source_quotation on bookings(source_quotation_id) where source_quotation_id is not null;
-- Quoted bookings can combine services that are not one catalogue tour; they carry their own title.
alter table bookings add column title varchar(180);
alter table bookings drop constraint bookings_booking_type_check;
alter table bookings add constraint bookings_booking_type_check check (booking_type in ('TOUR', 'PRIVATE_TRIP', 'QUOTED'));
alter table bookings add constraint bookings_quoted_has_source check (booking_type <> 'QUOTED' or (source_quotation_id is not null and title is not null));
alter table bookings add constraint bookings_paid_within_total check (amount_paid_cents <= total_amount_cents);

-- The services and prices agreed on a quoted booking, copied from the accepted quotation's frozen snapshot.
create table booking_services (
    id uuid primary key,
    booking_id uuid not null references bookings(id),
    line_order integer not null,
    title varchar(160) not null,
    details varchar(3000),
    quantity numeric(9, 2) not null check (quantity > 0),
    unit varchar(30),
    unit_price_cents bigint not null,
    line_total_cents bigint not null,
    tour_id uuid references tours(id),
    unique (booking_id, line_order)
);

-- Pickup and drop-off stops in visiting order, per leg. Filled from quotations and private trips.
create table booking_stops (
    id uuid primary key,
    booking_id uuid not null references bookings(id),
    leg_order integer not null,
    stop_order integer not null,
    kind varchar(10) not null check (kind in ('PICKUP', 'DROP_OFF')),
    location varchar(240) not null,
    unique (booking_id, leg_order, stop_order)
);

-- Booking history must survive: an assignment (and its inspections) is never removed with a booking.
alter table assignments drop constraint assignments_booking_id_fkey;
alter table assignments add constraint assignments_booking_id_fkey foreign key (booking_id) references bookings(id);

-- ---------------------------------------------------------------------------------------------------------------
-- 3. Payments. billing_payments (invoice payments, confirmed with a receipt) are the customer-facing record. When one
--    is confirmed on an invoice for a booking, an allocation row in the booking ledger (payments) follows it, so the
--    booking's amount paid and partner commission use the same money. Reversals are recorded, never deleted.
-- ---------------------------------------------------------------------------------------------------------------
alter table payments add column billing_payment_id uuid references billing_payments(id);
alter table payments add column allocation_of varchar(10) check (allocation_of in ('PAYMENT', 'REVERSAL'));
alter table payments add constraint payments_allocation_complete check ((billing_payment_id is null) = (allocation_of is null));
create unique index ux_payments_billing_allocation on payments(billing_payment_id, allocation_of) where billing_payment_id is not null;

alter table billing_payments drop constraint billing_payments_status_check;
alter table billing_payments add constraint billing_payments_status_check check (status in ('PENDING', 'CONFIRMED', 'REJECTED', 'REVERSED'));
alter table billing_payments add column reversed_by varchar(180);
alter table billing_payments add column reversed_at timestamptz;
alter table billing_payments add column reverse_reason varchar(500);
alter table billing_payments add constraint billing_payments_reversal_complete
    check ((status = 'REVERSED') = (reversed_at is not null and reverse_reason is not null));

-- ---------------------------------------------------------------------------------------------------------------
-- 4. Integrity and housekeeping.
-- ---------------------------------------------------------------------------------------------------------------
alter table user_accounts add constraint user_accounts_email_lower check (email = lower(btrim(email)));
create unique index ux_enquiries_converted_booking on enquiries(converted_booking_id) where converted_booking_id is not null;
create index idx_enquiries_follow_up on enquiries(status, updated_at) where status in ('NEW', 'IN_PROGRESS', 'AWAITING_CUSTOMER');
create index idx_billing_documents_open on billing_documents(due_date) where kind = 'INVOICE' and status = 'ISSUED' and payment_status <> 'PAID';
create index idx_billing_payments_pending on billing_payments(recorded_at) where status = 'PENDING';
drop index if exists idx_enquiries_email;  -- duplicate of idx_enquiries_customer_email

-- V1 placeholders that no code has ever read or written. Stop rather than drop anything that holds data.
do $$
begin
    if exists (select 1 from itinerary_items) or exists (select 1 from trip_days) or exists (select 1 from trips)
       or exists (select 1 from notifications) or exists (select 1 from review_requests) then
        raise exception 'V25: an unused V1 table (trips, trip_days, itinerary_items, notifications, review_requests) holds rows; review them before migrating';
    end if;
end $$;
drop table itinerary_items;
drop table trip_days;
drop table trips;
drop table notifications;
drop table review_requests;
