-- Invoices, quotations and payment receipts.
--
-- One table holds quotations and invoices. Drafts keep editable content; issuing allocates the official number and
-- freezes a snapshot of everything printed (customer, services, prices, company, banking, terms) plus the generated
-- PDF, so later changes to tours or business settings never rewrite an issued document.
-- The V1 tables quotes/quote_items were never used by the application and are left untouched.

-- Generated documents, proof of payment and the company logo are private uploads.
alter table media_files drop constraint media_files_purpose_check;
alter table media_files add constraint media_files_purpose_check check (purpose in
    ('INSPECTION_PHOTO', 'INSPECTION_SELFIE', 'DRIVER_DOCUMENT', 'VEHICLE_IMAGE', 'SITE_IMAGE', 'BILLING_DOCUMENT', 'PAYMENT_PROOF', 'COMPANY_LOGO'));

create table company_settings (
    id smallint primary key check (id = 1),
    version bigint not null default 0,
    company_name varchar(160) not null,
    tagline varchar(240),
    phone varchar(60),
    whatsapp varchar(60),
    email varchar(180),
    website varchar(180),
    address varchar(400) not null,
    -- The printed address reads "Example Street"; kept as printed until the operator confirms the spelling.
    address_needs_review boolean not null default true,
    logo_media_id uuid references media_files(id),
    bank_account_name varchar(160),
    bank_name varchar(120),
    bank_account_number varchar(40),
    bank_account_type varchar(60),
    bank_branch_code varchar(20),
    bank_swift_code varchar(20),
    payment_instructions varchar(500),
    invoice_terms text not null,
    quotation_terms text not null,
    currency varchar(3) not null default 'ZAR',
    -- No VAT unless an administrator configures it. A rate without registration details is never printed as "Tax Invoice".
    tax_enabled boolean not null default false,
    tax_label varchar(20),
    tax_rate_basis_points integer check (tax_rate_basis_points between 0 and 10000),
    tax_number varchar(40),
    default_due_days integer check (default_due_days between 0 and 365),
    quotation_valid_days integer check (quotation_valid_days between 1 and 365),
    updated_by varchar(180) not null,
    updated_at timestamptz not null
);

-- Values printed on invoice 100099 (14-07-2026). Branch code, SWIFT, registration number and payment policy are not
-- known and are left empty on purpose.
insert into company_settings (id, company_name, tagline, phone, whatsapp, email, address, address_needs_review,
    bank_account_name, bank_name, bank_account_number, payment_instructions, invoice_terms, quotation_terms, currency,
    quotation_valid_days, updated_by, updated_at)
values (1, 'SL Tours (Pty) Ltd', 'SL Tours offers excellent opportunities to explore South Africa.', '+27 67 012 7776', '+27 67 012 7776',
    'tours.st@outlook.com', '100 Example Street Cape Town 8001', true,
    'SL TOURS (PTY) LTD', 'FNB SA', '00000000000', 'Submit proof of payment via WhatsApp or email.',
    'Submit proof of payment via WhatsApp or email.', 'Submit proof of payment via WhatsApp or email.', 'ZAR',
    null, 'migration V23', now());

-- Official numbers. Invoices continue SL Tours' existing paper series (the reference invoice is 100099), so an
-- administrator must confirm the next number after checking the invoices already sent; issuing is refused until then.
-- Quotations and receipts start new series in this system. The allocator never goes below the highest number in use.
create table document_sequences (
    kind varchar(20) primary key check (kind in ('INVOICE', 'QUOTATION', 'RECEIPT')),
    next_number bigint not null check (next_number between 1 and 999999999),
    confirmed boolean not null,
    updated_by varchar(180) not null,
    updated_at timestamptz not null
);
insert into document_sequences (kind, next_number, confirmed, updated_by, updated_at) values
    ('INVOICE', 100100, false, 'migration V23', now()),
    ('QUOTATION', 1001, true, 'migration V23', now()),
    ('RECEIPT', 1001, true, 'migration V23', now());

create table billing_documents (
    id uuid primary key,
    version bigint not null default 0,
    kind varchar(20) not null check (kind in ('QUOTATION', 'INVOICE')),
    status varchar(20) not null check (status in ('DRAFT', 'ISSUED', 'ACCEPTED', 'DECLINED', 'VOID')),
    number bigint,
    booking_id uuid references bookings(id),
    account_id uuid references user_accounts(id),
    source_quotation_id uuid references billing_documents(id),
    revision_of_id uuid references billing_documents(id),
    superseded_by_id uuid references billing_documents(id),
    customer_name varchar(200) not null,
    customer_email varchar(180),
    customer_whatsapp varchar(40),
    booking_reference varchar(40),
    issue_date date,
    due_date date,
    service_start date,
    currency varchar(3) not null,
    subtotal_cents bigint not null default 0,
    discount_cents bigint not null default 0,
    tax_cents bigint not null default 0,
    total_cents bigint not null default 0 check (total_cents >= 0),
    paid_cents bigint not null default 0 check (paid_cents >= 0),
    payment_status varchar(20) not null check (payment_status in ('NOT_APPLICABLE', 'UNPAID', 'PARTIALLY_PAID', 'PAID')),
    content jsonb not null,
    internal_notes text,
    snapshot jsonb,
    issued_pdf_media_id uuid references media_files(id),
    idempotency_key uuid unique,
    created_by varchar(180) not null,
    created_at timestamptz not null,
    updated_by varchar(180) not null,
    updated_at timestamptz not null,
    issued_by varchar(180),
    issued_at timestamptz,
    decided_by varchar(180),
    decided_at timestamptz,
    voided_by varchar(180),
    voided_at timestamptz,
    void_reason varchar(500),
    -- Issued, accepted and declined documents always carry their official number and frozen snapshot. Discarded
    -- drafts are VOID without a number.
    check (status in ('DRAFT', 'VOID') or (number is not null and snapshot is not null)),
    check (kind = 'QUOTATION' or status not in ('ACCEPTED', 'DECLINED')),
    check (paid_cents <= total_cents)
);
-- A number is never reused, including by voided documents.
create unique index ux_billing_documents_number on billing_documents(kind, number) where number is not null;
-- One live invoice per quotation: converting twice returns the existing invoice.
create unique index ux_billing_documents_conversion on billing_documents(source_quotation_id)
    where source_quotation_id is not null and status <> 'VOID';
-- A document can be revised once at a time.
create unique index ux_billing_documents_revision on billing_documents(revision_of_id)
    where revision_of_id is not null and status <> 'VOID';
create index idx_billing_documents_list on billing_documents(kind, status, updated_at desc);
create index idx_billing_documents_issue_date on billing_documents(issue_date);
create index idx_billing_documents_booking on billing_documents(booking_id);
create index idx_billing_documents_account on billing_documents(account_id) where account_id is not null;
create index idx_billing_documents_customer on billing_documents(lower(customer_name));

create table billing_payments (
    id uuid primary key,
    version bigint not null default 0,
    document_id uuid not null references billing_documents(id),
    amount_cents bigint not null check (amount_cents > 0),
    paid_on date not null,
    method varchar(20) not null check (method in ('EFT', 'CASH', 'CARD', 'OTHER')),
    reference varchar(120),
    notes varchar(500),
    -- Proof of payment is evidence only. A payment counts towards the balance once an administrator confirms the funds.
    status varchar(20) not null check (status in ('PENDING', 'CONFIRMED', 'REJECTED')),
    receipt_number bigint unique,
    receipt_snapshot jsonb,
    receipt_pdf_media_id uuid references media_files(id),
    idempotency_key uuid not null unique,
    recorded_by varchar(180) not null,
    recorded_at timestamptz not null,
    decided_by varchar(180),
    decided_at timestamptz,
    reject_reason varchar(500),
    check (status <> 'CONFIRMED' or (receipt_number is not null and receipt_snapshot is not null)),
    check (status <> 'REJECTED' or reject_reason is not null)
);
create index idx_billing_payments_document on billing_payments(document_id, recorded_at);

create table billing_attachments (
    id uuid primary key,
    document_id uuid not null references billing_documents(id),
    payment_id uuid references billing_payments(id),
    media_id uuid not null references media_files(id),
    label varchar(160),
    uploaded_by varchar(180) not null,
    uploaded_at timestamptz not null
);
create index idx_billing_attachments_document on billing_attachments(document_id);

-- Customer document links. Only a SHA-256 hash of the 256-bit token is stored; the token is shown to staff once.
create table billing_share_links (
    id uuid primary key,
    document_id uuid not null references billing_documents(id),
    payment_id uuid references billing_payments(id),
    token_hash char(64) not null unique,
    channel varchar(20) not null check (channel in ('WHATSAPP', 'EMAIL', 'NATIVE', 'LINK')),
    created_by varchar(180) not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    revoked_by varchar(180),
    revoked_at timestamptz,
    last_opened_at timestamptz,
    open_count integer not null default 0
);
create index idx_billing_share_links_document on billing_share_links(document_id);

-- Per-document history: who created, changed, issued, shared, voided or recorded payments, and when.
create table billing_events (
    id uuid primary key,
    document_id uuid not null references billing_documents(id),
    payment_id uuid references billing_payments(id),
    event varchar(40) not null,
    channel varchar(20),
    actor varchar(180) not null,
    detail varchar(500),
    created_at timestamptz not null
);
create index idx_billing_events_document on billing_events(document_id, created_at);
