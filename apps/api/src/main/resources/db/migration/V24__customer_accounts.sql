-- Customer self-service accounts: contact number, verified email, single-use email tokens, and enquiries linked to accounts.

alter table user_accounts add column phone varchar(40);
alter table user_accounts add column email_verified_at timestamptz;
-- Staff, drivers and partners are created by administrators or approved applications, not by self-registration.
-- Existing customers verify through the emailed link like everyone else.
update user_accounts set email_verified_at = created_at where role <> 'CUSTOMER';

-- Email verification and password-reset links. Only a SHA-256 hash of the 256-bit token is stored.
create table account_tokens (
    id uuid primary key,
    account_id uuid not null references user_accounts(id),
    purpose varchar(20) not null check (purpose in ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash char(64) not null unique,
    -- The address the link was sent to; verification only counts for the account's current email.
    email varchar(180) not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    used_at timestamptz
);
create index idx_account_tokens_account on account_tokens(account_id, purpose, created_at desc);

-- Enquiries made while signed in belong to that customer. Guest enquiries stay unlinked until the customer
-- proves ownership of the email address they used.
alter table enquiries add column account_id uuid references user_accounts(id);
create index idx_enquiries_account on enquiries(account_id) where account_id is not null;
create index idx_enquiries_customer_email on enquiries(lower(customer_email));
create index idx_billing_documents_customer_email on billing_documents(lower(customer_email)) where customer_email is not null;
