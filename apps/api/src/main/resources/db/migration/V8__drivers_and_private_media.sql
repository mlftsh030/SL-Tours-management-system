-- Driver role, driver verification and private media metadata.
alter table user_accounts drop constraint user_accounts_role_check;
alter table user_accounts add constraint user_accounts_role_check check (role in ('ADMIN', 'STAFF', 'CUSTOMER', 'DRIVER'));

-- Files live in the configured storage backend; PostgreSQL holds metadata and relationships only.
create table media_files (
    id uuid primary key,
    storage_key varchar(200) not null unique,
    content_type varchar(80) not null,
    size_bytes bigint not null check (size_bytes > 0),
    sha256 varchar(64) not null,
    original_filename varchar(200),
    purpose varchar(30) not null check (purpose in ('INSPECTION_PHOTO', 'INSPECTION_SELFIE', 'DRIVER_DOCUMENT', 'VEHICLE_IMAGE', 'SITE_IMAGE')),
    visibility varchar(10) not null check (visibility in ('PRIVATE', 'PUBLIC')),
    uploaded_by varchar(180) not null,
    created_at timestamptz not null,
    deleted_at timestamptz,
    deleted_by varchar(180)
);
create index idx_media_purpose on media_files(purpose, created_at desc);

alter table drivers add constraint drivers_status_check check (status in ('ACTIVE', 'INACTIVE'));
alter table drivers add column account_id uuid unique references user_accounts(id);
alter table drivers add column verification_status varchar(20) not null default 'PENDING'
    check (verification_status in ('PENDING', 'VERIFIED', 'REJECTED', 'SUSPENDED'));
alter table drivers add column licence_number varchar(40);
alter table drivers add column licence_expires_on date;
alter table drivers add column prdp_expires_on date;
alter table drivers add column verification_notes varchar(1000);
alter table drivers add column verified_by varchar(180);
alter table drivers add column verified_at timestamptz;
alter table drivers add column version bigint not null default 0;
alter table drivers add column updated_at timestamptz;
update drivers set updated_at = created_at;
alter table drivers alter column updated_at set not null;

create table driver_documents (
    id uuid primary key,
    driver_id uuid not null references drivers(id),
    document_type varchar(30) not null check (document_type in ('DRIVING_LICENCE', 'PRDP', 'IDENTITY_DOCUMENT', 'OTHER')),
    media_id uuid not null unique references media_files(id),
    expires_on date,
    status varchar(20) not null check (status in ('PENDING_REVIEW', 'ACCEPTED', 'REJECTED')),
    review_notes varchar(1000),
    reviewed_by varchar(180),
    reviewed_at timestamptz,
    uploaded_by varchar(180) not null,
    created_at timestamptz not null,
    version bigint not null default 0
);
create index idx_driver_documents_driver on driver_documents(driver_id, created_at desc);
