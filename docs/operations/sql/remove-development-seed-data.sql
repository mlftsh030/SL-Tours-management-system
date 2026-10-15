-- Removes the development sample rows inserted by migration V2 and flagged by V12 (demo_data = true).
-- Safe by design: it aborts without changing anything if any real (non-sample) row still refers to a sample row.
-- Run as the application database owner, after a backup:  psql "$DATABASE_URL" -f remove-development-seed-data.sql
-- The seeded tours are the real catalogue and are never touched.
begin;

create temporary table blocking_references on commit drop as
select 'assignment on sample booking' as reason, a.id::text as row_id from assignments a join bookings b on b.id = a.booking_id where b.demo_data and not a.demo_data
union all select 'assignment using sample vehicle', a.id::text from assignments a join vehicles v on v.id = a.vehicle_id where v.demo_data and not a.demo_data
union all select 'assignment using sample driver', a.id::text from assignments a join drivers d on d.id = a.driver_id where d.demo_data and not a.demo_data
union all select 'assignment using sample guide', a.id::text from assignments a join guides g on g.id = a.guide_id where g.demo_data and not a.demo_data
union all select 'inspection on sample vehicle, driver or assignment', i.id::text from vehicle_inspections i
    left join vehicles v on v.id = i.vehicle_id left join drivers d on d.id = i.driver_id left join assignments a on a.id = i.assignment_id
    where v.demo_data or d.demo_data or a.demo_data
union all select 'restriction on sample vehicle (not from migration)', r.id::text from vehicle_restrictions r join vehicles v on v.id = r.vehicle_id
    where v.demo_data and r.opened_by <> 'migration'
union all select 'photo on sample vehicle', i.id::text from vehicle_images i join vehicles v on v.id = i.vehicle_id where v.demo_data
union all select 'document for sample driver', dd.id::text from driver_documents dd join drivers d on d.id = dd.driver_id where d.demo_data
union all select 'sign-in linked to sample driver', d.id::text from drivers d where d.demo_data and d.account_id is not null
union all select 'private trip requesting sample vehicle', p.booking_id::text from private_trip_details p join vehicles v on v.id = p.requested_vehicle_id where v.demo_data
union all select 'payment on sample booking', p.id::text from payments p join bookings b on b.id = p.booking_id where b.demo_data and not p.demo_data
union all select 'real booking for sample customer', b.id::text from bookings b join customers c on c.id = b.customer_id where c.demo_data and not b.demo_data
union all select 'real quote for sample customer or planner request', q.id::text from quotes q left join customers c on c.id = q.customer_id
    left join trip_planning_requests t on t.id = q.trip_planning_request_id where (c.demo_data or t.demo_data) and not q.demo_data
union all select 'enquiry converted to sample booking', e.id::text from enquiries e join bookings b on b.id = e.converted_booking_id where b.demo_data and not e.demo_data
union all select 'other record on sample booking', x.id::text from (
    select id, booking_id from notifications union all select id, booking_id from review_requests union all select id, booking_id from trips) x
    join bookings b on b.id = x.booking_id where b.demo_data;

do $$
declare blocking integer;
begin
    select count(*) into blocking from blocking_references;
    if blocking > 0 then
        raise exception 'Not removing sample data: % real record(s) refer to it. Review with: select * from blocking_references (run the first part only), then mark rows as genuine in the admin screens or resolve them.', blocking;
    end if;
end $$;

delete from vehicle_restrictions r using vehicles v where v.id = r.vehicle_id and v.demo_data and r.opened_by = 'migration';
delete from payments where demo_data;
delete from quotes where demo_data;           -- quote_items cascade
delete from assignments where demo_data;
delete from enquiries where demo_data;
delete from bookings where demo_data;
delete from trip_planning_requests where demo_data;  -- interests and services cascade
delete from customers where demo_data;
delete from guides where demo_data;
delete from drivers where demo_data;
delete from vehicles where demo_data;          -- amenities cascade
insert into admin_audit_events (id, actor, action, entity_id, detail, created_at)
values (gen_random_uuid(), 'database-script', 'SAMPLE_DATA_REMOVED', 'migration-v2', 'Removed development sample rows flagged by V12', now());
commit;
