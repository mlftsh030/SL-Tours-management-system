-- Which customer-facing vehicle option (model) a fleet vehicle is. Customers choose an option such as "Hyundai Staria";
-- only a fleet vehicle linked to that option is offered as that model. Other free vehicles of the same category are
-- shown as named alternatives the customer must choose explicitly, and anything else is quoted. Null means the vehicle
-- is not one of the published models: it can still be listed and booked under its own name.
alter table vehicles add column vehicle_option_slug varchar(80)
    references vehicle_options(slug) on update cascade on delete set null;
create index idx_vehicles_option on vehicles(vehicle_option_slug);

-- Development sample vehicles (V2, flagged demo_data by V12) whose model matches a published option. The generic
-- "Sedan" and the Hyundai H1 are not published options and stay unlinked.
update vehicles set vehicle_option_slug = 'mercedes-benz-v-class' where id = '70000000-0000-0000-0000-000000000001' and demo_data;
update vehicles set vehicle_option_slug = 'toyota-quantum' where id = '70000000-0000-0000-0000-000000000002' and demo_data;
