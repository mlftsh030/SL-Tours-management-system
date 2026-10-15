-- Plans that a visitor explicitly sends from the planning assistant arrive in the enquiries inbox.
alter table enquiries drop constraint enquiries_source_check;
alter table enquiries add constraint enquiries_source_check
    check (source in ('CONTACT_FORM', 'TRIP_PLANNER', 'PRIVATE_TRIP_QUOTE', 'ASSISTANT'));

-- Daily usage counters for the spending controls. Aggregates only: no messages, visitors or sessions are stored.
create table assistant_usage_days (
    usage_date date primary key,
    model_requests integer not null default 0,
    input_tokens bigint not null default 0,
    output_tokens bigint not null default 0,
    cost_micro_usd bigint not null default 0,
    replies_replaced integer not null default 0,
    provider_failures integer not null default 0
);
