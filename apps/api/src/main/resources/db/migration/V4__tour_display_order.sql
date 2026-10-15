-- Homepage package order from the live sl-tours.com index.html.
alter table tours add column display_order integer not null default 100;

update tours set display_order = 1 where slug = 'cape-peninsula-journey';
update tours set display_order = 2 where slug = 'cape-town-essentials';
update tours set display_order = 3 where slug = 'cape-winelands-escape';
update tours set display_order = 4 where slug = 'cape-culture-experience';
update tours set display_order = 5 where slug = 'garden-route-journey';
update tours set display_order = 6 where slug = 'airport-transfer';
update tours set display_order = 7 where slug = 'aquila-safari-day-trip';
update tours set display_order = 8 where slug = 'west-coast-tour';
update tours set display_order = 9 where slug = 'private-custom-tour';
