-- Card photos are 700px wide and blur when stretched across the tour detail hero.
-- Where a full-size SL Tours gallery photo clearly matches the tour, use it for the detail banner.
alter table tours add column banner_image varchar(220);

update tours set banner_image = '/assets/gallery/30.jpg' where slug = 'cape-peninsula-journey';
update tours set banner_image = '/assets/gallery/44.jpg' where slug = 'cape-town-essentials';
update tours set banner_image = '/assets/gallery/1.jpg' where slug = 'airport-transfer';
update tours set banner_image = '/assets/gallery/27.jpg' where slug = 'private-custom-tour';
