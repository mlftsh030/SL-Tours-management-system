-- Restricted database roles for SL Tours. Run once per database as a superuser or the database owner, with psql:
--
--   psql "$ADMIN_URL" -v db=sltours -v owner_password="'…'" -v app_password="'…'" -v readonly_password="'…'" -f tools/db/roles.sql
--
-- Roles:
--   sltours_owner     owns the schema; used ONLY by Flyway at deploy time (SPRING_FLYWAY_USER / SPRING_FLYWAY_PASSWORD).
--   sltours_app       the running API (SPRING_DATASOURCE_USERNAME / _PASSWORD): reads and writes rows, cannot change the schema,
--                     cannot delete customer, booking, document or payment records, and can only add to history tables.
--   sltours_readonly  backups (pg_dump) and reporting: read-only.
-- Re-running is safe: roles are created if missing and privileges are re-applied (run it again after migrations that add tables).
\set ON_ERROR_STOP on

select format('create role sltours_owner login password %s', :'owner_password') where not exists (select 1 from pg_roles where rolname = 'sltours_owner') \gexec
select format('create role sltours_app login password %s', :'app_password') where not exists (select 1 from pg_roles where rolname = 'sltours_app') \gexec
select format('create role sltours_readonly login password %s', :'readonly_password') where not exists (select 1 from pg_roles where rolname = 'sltours_readonly') \gexec

-- Ownership: the migration role owns the schema and everything in it.
select format('alter database %I owner to sltours_owner', :'db') \gexec
alter schema public owner to sltours_owner;
select format('alter table public.%I owner to sltours_owner', tablename) from pg_tables where schemaname = 'public' \gexec
select format('alter sequence public.%I owner to sltours_owner', sequencename) from pg_sequences where schemaname = 'public' \gexec
select format('alter function public.%I(%s) owner to sltours_owner', p.proname, pg_get_function_identity_arguments(p.oid))
  from pg_proc p join pg_namespace n on n.oid = p.pronamespace where n.nspname = 'public' and p.prokind = 'f'
  and not exists (select 1 from pg_depend d where d.objid = p.oid and d.deptype = 'e') \gexec

revoke all on schema public from public;
select format('revoke all on database %I from public', :'db') \gexec
select format('grant connect on database %I to sltours_app, sltours_readonly', :'db') \gexec
grant usage on schema public to sltours_app, sltours_readonly;

-- Application: full row access, then narrowed below. No CREATE on the schema, so no DDL.
grant select, insert, update, delete on all tables in schema public to sltours_app;
grant usage, select on all sequences in schema public to sltours_app;
grant execute on all functions in schema public to sltours_app;

-- Business records are corrected, voided, reversed or merged; never deleted by the application.
revoke delete on customers, user_accounts, enquiries, bookings, booking_services, booking_stops, assignments, vehicle_inspections, inspection_photos,
  billing_documents, billing_payments, billing_attachments, document_sequences, partner_commissions, partner_payouts, company_settings
  from sltours_app;
-- History is append-only.
revoke update, delete on admin_audit_events, billing_events, customer_merges, payments, referral_attribution_changes, flyway_schema_history from sltours_app;
revoke insert on flyway_schema_history from sltours_app;

-- Read-only role for backups and reporting.
grant select on all tables in schema public to sltours_readonly;
grant select on all sequences in schema public to sltours_readonly;

-- Tables created by later migrations get the same defaults (re-run this script to re-apply the narrowing above).
alter default privileges for role sltours_owner in schema public grant select, insert, update, delete on tables to sltours_app;
alter default privileges for role sltours_owner in schema public grant usage, select on sequences to sltours_app;
alter default privileges for role sltours_owner in schema public grant execute on functions to sltours_app;
alter default privileges for role sltours_owner in schema public grant select on tables to sltours_readonly;
