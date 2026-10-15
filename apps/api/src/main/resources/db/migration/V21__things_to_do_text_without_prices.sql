-- Things to Do pages show no prices. Two listings carried prices in their text; the prices stay in the price rules used by the
-- day planner, and only the wording shown on Things to Do pages changes.
update activities set description = 'The Labia shows films on four screens, with concession tickets for students, seniors and children. Screening times change daily, so check the schedule before you go.'
where slug = 'the-labia-theatre' and description like 'All shows cost R90%';
update activity_faqs f set answer = 'A supervised mini rink for children under 1.3 m is open during public sessions.'
from activities a where a.id = f.activity_id and a.slug = 'grandwest-ice-station' and f.answer like '%skate hire there is R40%';
delete from activity_faqs f using activities a where a.id = f.activity_id and a.slug = 'century-karting' and f.question = 'How much does it cost?';
