create table user_accounts (
 id uuid primary key,
 email varchar(180) not null unique,
 name varchar(160) not null,
 password_hash varchar(100) not null,
 role varchar(20) not null check (role in ('ADMIN', 'STAFF', 'CUSTOMER')),
 enabled boolean not null default true,
 created_at timestamptz not null,
 last_login_at timestamptz
);
alter table bookings add column account_id uuid references user_accounts(id);
create index idx_bookings_account on bookings(account_id);
create index idx_bookings_customer_email on bookings(lower(customer_email));
alter table tours add column version bigint not null default 0;
alter table bookings add column version bigint not null default 0;
create table admin_audit_events (
 id uuid primary key,
 actor varchar(180) not null,
 action varchar(80) not null,
 entity_id varchar(160) not null,
 detail varchar(500) not null,
 created_at timestamptz not null
);
create index idx_admin_audit_created on admin_audit_events(created_at desc);
