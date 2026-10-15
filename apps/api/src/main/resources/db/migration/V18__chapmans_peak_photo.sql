-- SL Tours' own photo taken on Chapman's Peak Drive (gallery photo 16). Only set when no image has been chosen.
update activities set hero_image = '/assets/gallery/16.jpg', hero_image_alt = 'Guest overlooking Hout Bay from Chapman''s Peak Drive'
where slug = 'chapmans-peak-drive' and hero_image is null;
