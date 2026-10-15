-- V2 inserted fixed-ID sample customers, bookings, people, vehicles and finance rows in every environment.
-- Flag exactly those rows (by ID) so production can exclude them from inventory and reporting. Nothing is
-- deleted here; docs/operations/DEVELOPMENT_SEED_DATA.md explains how to review and remove them safely.
-- The seeded tours are the real catalogue (aligned with the live site in V3) and are not flagged.
alter table customers add column demo_data boolean not null default false;
alter table bookings add column demo_data boolean not null default false;
alter table trip_planning_requests add column demo_data boolean not null default false;
alter table enquiries add column demo_data boolean not null default false;
alter table guides add column demo_data boolean not null default false;
alter table drivers add column demo_data boolean not null default false;
alter table vehicles add column demo_data boolean not null default false;
alter table assignments add column demo_data boolean not null default false;
alter table quotes add column demo_data boolean not null default false;
alter table payments add column demo_data boolean not null default false;

update customers set demo_data = true where id = '20000000-0000-0000-0000-000000000001';
update bookings set demo_data = true where id in ('30000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002');
update trip_planning_requests set demo_data = true where id = '40000000-0000-0000-0000-000000000001';
update enquiries set demo_data = true where trip_planning_request_id = '40000000-0000-0000-0000-000000000001';
update guides set demo_data = true where id in ('50000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000002',
    '50000000-0000-0000-0000-000000000003');
update drivers set demo_data = true where id in ('60000000-0000-0000-0000-000000000001', '60000000-0000-0000-0000-000000000002');
update vehicles set demo_data = true where id in ('70000000-0000-0000-0000-000000000001', '70000000-0000-0000-0000-000000000002',
    '70000000-0000-0000-0000-000000000003', '70000000-0000-0000-0000-000000000004');
update assignments set demo_data = true where id = '80000000-0000-0000-0000-000000000001';
update quotes set demo_data = true where id = '90000000-0000-0000-0000-000000000001';
update payments set demo_data = true where id = '92000000-0000-0000-0000-000000000001';
