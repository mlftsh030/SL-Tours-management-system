-- READ-ONLY report of records that look like development or test data. Changes nothing.
--   docker compose exec -T postgres psql -U sltours -d sltours -f - < tools/db/dev-records-review.sql
-- A record is listed when its email is on a reserved test domain (example.com, sltours.local), is a known test prefix
-- (e2e-, dbg-, smoke, walkthrough, reassign), or it is flagged demo_data by V12. Review the list with the business
-- before running dev-records-cleanup.sql: real customers can use any address.
\pset footer off
\echo '== Numbering (stored configuration; the next number is next_number, never inferred from one document)'
select kind, next_number, confirmed, updated_by, updated_at from document_sequences order by kind;
select kind, min(number) as lowest, max(number) as highest, count(*) filter (where number is not null) as numbered from billing_documents group by kind;
select min(receipt_number) as lowest_receipt, max(receipt_number) as highest_receipt from billing_payments;

create temporary view dev_email as
  select e from (values ('%@example.com'), ('%@sltours.local'), ('%@enail.com')) v(e);

\echo '== Issued or draft documents for test addresses or names'
select d.kind, d.number, d.status, d.payment_status, d.customer_name, d.customer_email,
  (select count(*) from billing_payments p where p.document_id = d.id and p.status = 'CONFIRMED') as confirmed_payments
from billing_documents d
where d.customer_email like any (select e from dev_email) or d.customer_name ~* '(local test|walkthrough|e2e|smoke)'
order by d.kind, d.number nulls last;

\echo '== Login accounts on test addresses'
select email, role, enabled, email_verified_at is not null as verified, created_at from user_accounts
where email like any (select e from dev_email) and role <> 'ADMIN' order by role, created_at;

\echo '== Customers, bookings and enquiries on test addresses or flagged demo_data'
select 'customer' as kind, coalesce(email, '(no email)') as key, name, demo_data from customers where email like any (select e from dev_email) or demo_data
union all select 'booking', reference, customer_email, demo_data from bookings where customer_email like any (select e from dev_email) or demo_data
union all select 'enquiry', reference, customer_email, demo_data from enquiries where customer_email like any (select e from dev_email) or demo_data
order by 1, 2;
