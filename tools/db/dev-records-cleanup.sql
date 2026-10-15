-- PREPARED cleanup of CONFIRMED development/test records. Not run automatically. Deletes nothing and renumbers nothing.
--
-- 1. Take and verify a backup:      tools/db/backup.sh && tools/db/verify-restore.sh backups/<latest>
-- 2. Review:                         tools/db/dev-records-review.sql, and edit the lists below to exactly the confirmed records.
-- 3. Dry run (rolls back):           psql … -v backup=backups/<latest> -f tools/db/dev-records-cleanup.sql
-- 4. Apply:                          psql … -v backup=backups/<latest> -v apply=1 -f tools/db/dev-records-cleanup.sql
--
-- What it does, inside one transaction:
--   * issued test invoices WITHOUT confirmed payments are voided with a reason; their numbers stay used (numbers are never reused);
--   * test customer logins are disabled (they can no longer sign in), not deleted;
--   * test customers, bookings and enquiries are flagged demo_data, so work queues and reports leave them out;
--   * each step is written to admin_audit_events (actor 'dev-records-cleanup').
-- Documents with confirmed payments (e.g. a walkthrough invoice) are reported, not changed: correct them in the app.
\set ON_ERROR_STOP on
\if :{?backup}
\else
  \echo 'Refusing to run: pass -v backup=<path of the verified backup taken just before>'
  \quit
\endif
begin;
create temporary table cleanup_invoices (number bigint) on commit drop;
create temporary table cleanup_emails (email text) on commit drop;
-- EDIT: confirmed test invoices and addresses (as found on 5 October 2026 in the local development database).
insert into cleanup_invoices values (100101), (100102), (100103), (100104), (100105);
insert into cleanup_emails select email from user_accounts where email ~ '^(e2e-(alice|bob)-[a-z0-9]+|dbg-[0-9]+)@example\.com$';
insert into cleanup_emails values ('smoke@example.com'), ('walkthrough@example.com'), ('reassign@example.com');

\echo '== Not changed (confirmed payments): correct these in the application'
select d.number, d.customer_name from billing_documents d join cleanup_invoices c on c.number = d.number and d.kind = 'INVOICE'
where exists (select 1 from billing_payments p where p.document_id = d.id and p.status = 'CONFIRMED');

with v as (
  update billing_documents d set status = 'VOID', voided_at = now(), voided_by = 'dev-records-cleanup',
    void_reason = 'Development test record (local test run); number kept, not reused', updated_at = now(), updated_by = 'dev-records-cleanup'
  from cleanup_invoices c where d.kind = 'INVOICE' and d.number = c.number and d.status = 'ISSUED'
    and not exists (select 1 from billing_payments p where p.document_id = d.id and p.status in ('CONFIRMED', 'PENDING'))
  returning d.id, d.number)
insert into billing_events (id, document_id, event, actor, detail, created_at)
select gen_random_uuid(), id, 'VOIDED', 'dev-records-cleanup', 'Development test record voided by ' || :'backup' || ' cleanup', now() from v;
update billing_share_links l set revoked_at = now(), revoked_by = 'dev-records-cleanup'
from billing_documents d where d.id = l.document_id and d.voided_by = 'dev-records-cleanup' and l.revoked_at is null;

update user_accounts set enabled = false where email in (select email from cleanup_emails) and role = 'CUSTOMER';
update customers set demo_data = true where email in (select email from cleanup_emails);
update bookings set demo_data = true where lower(customer_email) in (select email from cleanup_emails);
update enquiries set demo_data = true where lower(customer_email) in (select email from cleanup_emails);
insert into admin_audit_events (id, actor, action, entity_id, detail, created_at)
values (gen_random_uuid(), 'dev-records-cleanup', 'DEV_RECORDS_CLEANED', 'local-database', 'Backup before cleanup: ' || :'backup', now());

\echo '== Result'
select kind, number, status, void_reason from billing_documents where voided_by = 'dev-records-cleanup' order by number;
select count(*) as disabled_logins from user_accounts where email in (select email from cleanup_emails) and not enabled;
\if :{?apply}
  commit;
  \echo 'Applied.'
\else
  rollback;
  \echo 'Dry run only: rolled back. Add -v apply=1 to apply.'
\endif
