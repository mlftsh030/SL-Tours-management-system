-- Partnerships: referral partners (hotels, hosts, concierges, agents) and experience suppliers (operators, guides).
-- No commercial terms are seeded. Commission is earned only under an agreement an administrator records.

alter table user_accounts drop constraint user_accounts_role_check;
alter table user_accounts add constraint user_accounts_role_check check (role in ('ADMIN', 'STAFF', 'CUSTOMER', 'DRIVER', 'PARTNER'));

create table partners (
    id uuid primary key,
    reference varchar(20) not null unique,
    business_name varchar(160) not null,
    business_type varchar(30) not null check (business_type in ('HOTEL', 'GUESTHOUSE', 'ACCOMMODATION_HOST', 'CONCIERGE', 'TRAVEL_AGENT',
        'SAFARI_OPERATOR', 'ACTIVITY_PROVIDER', 'LOCAL_GUIDE', 'OTHER')),
    contact_name varchar(160) not null,
    email varchar(180) not null unique,
    phone varchar(80),
    website varchar(300),
    area varchar(160),
    registration_number varchar(80),
    licences varchar(600),
    description varchar(2000) not null,
    services_offered varchar(2000),
    account_id uuid not null unique references user_accounts(id),
    -- One code per partner, created when the referral route is first approved. Never reused.
    referral_code varchar(20) unique,
    privacy_consent_at timestamptz not null,
    idempotency_key uuid not null unique,
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

-- Each route is reviewed separately; a business may hold both.
create table partner_routes (
    partner_id uuid not null references partners(id),
    route varchar(20) not null check (route in ('REFERRAL', 'SUPPLIER')),
    status varchar(20) not null check (status in ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    decided_by varchar(180),
    decided_at timestamptz,
    decision_reason varchar(600),
    primary key (partner_id, route)
);

-- Agreements cannot be edited once recorded (only ended), so every commission keeps the terms it was calculated under.
create table partner_agreements (
    id uuid primary key,
    partner_id uuid not null references partners(id),
    route varchar(20) not null check (route in ('REFERRAL', 'SUPPLIER')),
    agreement_reference varchar(80) not null,
    effective_from date not null,
    effective_to date,
    commission_type varchar(30) check (commission_type in ('PERCENTAGE', 'FIXED_PER_BOOKING')),
    rate_basis_points integer check (rate_basis_points between 1 and 10000),
    fixed_amount_cents integer check (fixed_amount_cents > 0),
    eligible_basis varchar(30) check (eligible_basis in ('NET_AMOUNT_PAID', 'BOOKING_TOTAL')),
    eligible_booking_types varchar(40),
    attribution_window_days integer check (attribution_window_days between 1 and 3650),
    refund_treatment varchar(30) check (refund_treatment in ('PROPORTIONAL', 'FULL_REVERSAL')),
    minimum_eligible_cents integer check (minimum_eligible_cents >= 0),
    maximum_commission_cents integer check (maximum_commission_cents > 0),
    supplier_terms varchar(2000),
    notes varchar(2000),
    recorded_by varchar(180) not null,
    recorded_at timestamptz not null,
    ended_by varchar(180),
    ended_at timestamptz,
    constraint chk_agreement_dates check (effective_to is null or effective_to >= effective_from),
    constraint chk_referral_terms check (route <> 'REFERRAL' or (commission_type is not null and eligible_basis is not null
        and eligible_booking_types is not null and attribution_window_days is not null and refund_treatment is not null
        and ((commission_type = 'PERCENTAGE' and rate_basis_points is not null and fixed_amount_cents is null)
          or (commission_type = 'FIXED_PER_BOOKING' and fixed_amount_cents is not null and rate_basis_points is null)))),
    constraint chk_supplier_terms check (route <> 'SUPPLIER' or (supplier_terms is not null and commission_type is null)),
    -- One agreement per partner and route at any time.
    constraint no_overlapping_agreements exclude using gist (partner_id with =, route with =,
        daterange(effective_from, coalesce(effective_to + 1, 'infinity'::date)) with &&)
);

-- Referral attribution. Kept outside the booking and enquiry entities; changed only through the referral service.
alter table bookings add column referral_partner_id uuid references partners(id);
alter table bookings add column referral_code varchar(20);
alter table bookings add column referral_source varchar(20) check (referral_source in ('LINK', 'CODE', 'ENQUIRY', 'ADMIN'));
alter table bookings add column referral_captured_at timestamptz;
create index idx_bookings_referral on bookings(referral_partner_id) where referral_partner_id is not null;
alter table enquiries add column referral_partner_id uuid references partners(id);
alter table enquiries add column referral_code varchar(20);
alter table enquiries add column referral_source varchar(20) check (referral_source in ('LINK', 'CODE', 'ADMIN'));
alter table enquiries add column referral_captured_at timestamptz;
create index idx_enquiries_referral on enquiries(referral_partner_id) where referral_partner_id is not null;

create table referral_attribution_changes (
    id uuid primary key,
    booking_id uuid references bookings(id),
    enquiry_id uuid references enquiries(id),
    from_partner_id uuid references partners(id),
    to_partner_id uuid references partners(id),
    reason varchar(600) not null,
    changed_by varchar(180) not null,
    changed_at timestamptz not null,
    constraint chk_attribution_target check ((booking_id is null) <> (enquiry_id is null))
);

-- Customer payments and refunds (the V1 table, now used). Booking amount_paid_cents is the net of these rows.
alter table payments add column kind varchar(10) not null default 'PAYMENT' check (kind in ('PAYMENT', 'REFUND'));
alter table payments add column received_on date;
alter table payments add column idempotency_key uuid unique;
create index idx_payments_booking on payments(booking_id);

create table partner_payouts (
    id uuid primary key,
    partner_id uuid not null references partners(id),
    amount_cents integer not null check (amount_cents > 0),
    paid_on date not null,
    method varchar(60) not null,
    payment_reference varchar(120) not null,
    notes varchar(1000),
    idempotency_key uuid not null unique,
    recorded_by varchar(180) not null,
    recorded_at timestamptz not null
);

-- Commission ledger. Positive EARNED lines and negative adjustments; a payout settles a set of lines.
create table partner_commissions (
    id uuid primary key,
    partner_id uuid not null references partners(id),
    booking_id uuid not null references bookings(id),
    agreement_id uuid not null references partner_agreements(id),
    kind varchar(30) not null check (kind in ('EARNED', 'REFUND_ADJUSTMENT', 'REVERSAL')),
    amount_cents integer not null,
    basis_amount_cents integer not null,
    calculation varchar(800) not null,
    source_payment_id uuid unique references payments(id),
    payout_id uuid references partner_payouts(id),
    created_by varchar(180) not null,
    created_at timestamptz not null,
    constraint chk_commission_sign check ((kind = 'EARNED' and amount_cents > 0) or (kind <> 'EARNED' and amount_cents < 0))
);
-- At most one earned line and one final reversal per booking, whatever retries or concurrent updates occur.
create unique index uq_commission_earned on partner_commissions(booking_id) where kind = 'EARNED';
create unique index uq_commission_reversal on partner_commissions(booking_id) where kind = 'REVERSAL';
create index idx_commissions_partner on partner_commissions(partner_id, created_at);

-- Payments to experience suppliers, separate from referral commissions.
create table supplier_payments (
    id uuid primary key,
    partner_id uuid not null references partners(id),
    amount_cents integer not null check (amount_cents > 0),
    paid_on date not null,
    method varchar(60) not null,
    payment_reference varchar(120) not null,
    booking_id uuid references bookings(id),
    description varchar(600) not null,
    idempotency_key uuid not null unique,
    recorded_by varchar(180) not null,
    recorded_at timestamptz not null
);

create table supplier_proposals (
    id uuid primary key,
    reference varchar(20) not null unique,
    partner_id uuid not null references partners(id),
    status varchar(20) not null check (status in ('SUBMITTED', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    title varchar(180) not null,
    category varchar(30) not null,
    area varchar(30) not null,
    summary varchar(400) not null,
    description varchar(4000) not null,
    duration_minutes integer not null check (duration_minutes between 15 and 720),
    address varchar(300),
    website varchar(500),
    proposed_prices varchar(2000) not null,
    proposed_schedule varchar(2000) not null,
    participation_info varchar(1000) not null,
    supplier_notes varchar(2000),
    decision_reason varchar(600),
    decided_by varchar(180),
    decided_at timestamptz,
    activity_id uuid references activities(id),
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create index idx_proposals_partner on supplier_proposals(partner_id, created_at);

-- Catalogue entries that came from a supplier proposal, so the commercial relationship can be disclosed.
alter table activities add column supplier_partner_id uuid references partners(id);
