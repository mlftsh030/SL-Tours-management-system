-- Admin-managed website images. Built-in assets stay in use until an administrator activates replacements.
create table site_images (
    id uuid primary key,
    slot varchar(30) not null check (slot in ('HOME_HERO', 'GALLERY')),
    media_id uuid not null unique references media_files(id),
    alt_text varchar(300) not null,
    title varchar(160),
    subtitle varchar(160),
    body varchar(300),
    sort_order integer not null default 0,
    active boolean not null default false,
    version bigint not null default 0,
    created_by varchar(180) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);
create index idx_site_images_slot on site_images(slot, active, sort_order);
