-- Records what the official information leaves uncertain, so plans cannot present it as confirmed.
-- uncertainty_note: official sources conflict or leave eligibility undefined; such prices are treated as estimates
--   and such hours as unconfirmed until an administrator resolves the note.
-- promotional: a temporary offer, re-checked on a much shorter cycle than standard prices.
-- activity_hours.verified_on: when the hours or sessions were last checked (schedules go stale faster than prices).
-- activity_hours.valid_from/valid_to: limits observed sessions to the dates they were seen for, when known.
-- participation_rules: whether age, height, health or similar limits are published (KNOWN), partly published
--   (PARTLY_KNOWN) or not published (UNKNOWN). Unknown limits mean suitability cannot be confirmed.
alter table activity_price_rules add column uncertainty_note varchar(400);
alter table activity_price_rules add column promotional boolean not null default false;
alter table activity_extras add column uncertainty_note varchar(400);
alter table activity_hours add column uncertainty_note varchar(400);
alter table activity_hours add column verified_on date;
alter table activity_hours add column valid_from date;
alter table activity_hours add column valid_to date;
alter table activity_hours add constraint chk_hours_valid check (valid_from is null or valid_to is null or valid_from <= valid_to);
alter table activities add column participation_rules varchar(20) not null default 'KNOWN'
    check (participation_rules in ('KNOWN', 'PARTLY_KNOWN', 'UNKNOWN'));
alter table activities add column participation_note varchar(400);

update activity_hours h set verified_on = coalesce(a.last_verified_on, current_date)
from activities a where a.id = h.activity_id;
alter table activity_hours alter column verified_on set not null;

-- Castle of Good Hope: the operator's own pages disagree on the last ticket sale.
update activity_hours h set uncertainty_note = 'Official pages give the last ticket sale as 15:15 (visitor information) and 15:45 (home page); 15:15 is used until resolved.'
from activities a where a.id = h.activity_id and a.slug = 'castle-of-good-hope';

-- Iziko: weekend hours change in an undefined "winter" period.
update activity_hours h set uncertainty_note = 'Iziko lists winter weekend hours of 08:30-16:00 without defining the winter period; confirm weekend times.'
from activities a where a.id = h.activity_id and a.slug = 'iziko-south-african-museum';

-- Old Biscuit Mill: two official pages state different parking fees.
update activity_extras x set uncertainty_note = 'The official site states R10 on the home page and R20 on the visitor information page; R20 is shown until resolved.'
from activities a where a.id = x.activity_id and a.slug = 'neighbourgoods-market';

-- The Labia does not define who qualifies for the concession; applying it to under-18s is an SL Tours assumption.
update activity_price_rules p set uncertainty_note = 'The cinema does not define the concession ages; applying it to children under 18 is an assumption.'
from activities a where a.id = p.activity_id and a.slug = 'the-labia-theatre' and p.audience = 'CHILD';

-- The Ice Station: weekday sessions and prices were observed on Tuesday 13 October 2026 only, weekend sessions on
-- Saturday 17 and Sunday 18 October. Limit weekday rows to Tuesdays until other weekdays are checked.
update activity_hours h set days_of_week = 'TUE',
    notes = coalesce(h.notes || ' ', '') || 'Observed for Tuesday 13 Oct 2026 only; other weekdays not checked.'
from activities a where a.id = h.activity_id and a.slug = 'grandwest-ice-station' and h.days_of_week = 'MON,TUE,WED,THU,FRI';
update activity_hours h set notes = coalesce(h.notes || ' ', '') || 'Observed for 17-18 Oct 2026.'
from activities a where a.id = h.activity_id and a.slug = 'grandwest-ice-station' and h.days_of_week in ('SAT,SUN', 'SAT');
update activity_price_rules p set days_of_week = 'TUE',
    conditions = coalesce(p.conditions || '. ', '') || 'Observed for Tuesday 13 Oct 2026 only'
from activities a where a.id = p.activity_id and a.slug = 'grandwest-ice-station' and p.days_of_week = 'MON,TUE,WED,THU,FRI';

-- Participation limits.
update activities set participation_rules = 'UNKNOWN',
    participation_note = 'Century Karting does not publish minimum age or height limits online. Contact the venue before planning for children.'
where slug = 'century-karting';
update activities set participation_rules = 'PARTLY_KNOWN',
    participation_note = 'No minimum age is published for public sessions. The mini rink is for children under 1.3 m; gloves are recommended.'
where slug = 'grandwest-ice-station';
update activities set participation_rules = 'PARTLY_KNOWN',
    participation_note = 'Age restrictions depend on each film''s classification.'
where slug = 'the-labia-theatre';
update activities set participation_rules = 'UNKNOWN', participation_note = 'Not verified.'
where status = 'DRAFT' and slug in ('kenilworth-karting', 'lions-head-hike', 'muizenberg-beach');
