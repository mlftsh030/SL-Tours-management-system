# Media Storage

Uploaded files (inspection photos, collection selfies, driver verification documents, vehicle photos and
website images) are stored outside the database and served only through the API.

## Layout

| Part | Where | Notes |
|---|---|---|
| File bytes | `SLTOURS_MEDIA_ROOT` (Docker: `/var/lib/sltours/media`, volume `media-data`) | Keys are generated server-side: `<purpose>/<yyyy>/<mm>/<uuid>.<ext>`. Client filenames are never used as paths. |
| Metadata | PostgreSQL `media_files` | Content type, size, SHA-256, sanitised original name, purpose, visibility, uploader, deletion record. |
| Relationships | `driver_documents`, `vehicle_inspections.selfie_media_id`, `inspection_photos`, `vehicle_images`, `site_images` | Ownership checks follow these links. |

The API talks to storage only through `MediaStorage` (`LocalMediaStorage` today). An object-storage
implementation (for example Oracle Object Storage or S3) can replace it without schema changes: implement
`write`, `read` and `delete` for the same keys and register it instead of the local bean.

## Access rules

| File | Who can open it | Endpoint |
|---|---|---|
| Inspection photo or selfie | Administrators and staff; the driver who submitted it, while the assignment is still theirs | `/api/v1/admin/media/{id}`, `/api/v1/driver/media/{id}` |
| Driver verification document | Administrators only | `/api/v1/admin/media/{id}` |
| Vehicle listing photo, website image | Anyone | `/api/v1/public/media/{id}` (only files marked PUBLIC) |

A driver asking for someone else's file gets 404, so the file's existence is not revealed. Private
responses send `Cache-Control: private, no-store`, `X-Content-Type-Options: nosniff` and a sandboxing CSP;
PDFs download as attachments.

## Upload validation

- File type is detected from the file's content (magic bytes): JPEG, PNG, WebP for photos; PDF also for
  driver documents. Anything else is rejected whatever its name or declared type.
- Size limits: photos 10 MB (`SLTOURS_MAX_IMAGE_BYTES`), documents 15 MB (`SLTOURS_MAX_DOCUMENT_BYTES`),
  100 MB per request (`SLTOURS_MAX_UPLOAD_REQUEST`, matched by Nginx `client_max_body_size`).
- If the database transaction fails after a file is written, the file is deleted again.
- HEIC is not accepted. iPhone cameras deliver JPEG to the browser file input in the default "Most
  compatible" setting.

## Backup

Back up the database and the media volume together, at the same time, so metadata and files match:

```bash
# Database
docker compose exec -T postgres pg_dump -U sltours -Fc sltours > sltours-$(date +%F).dump
# Media volume
docker run --rm -v sl-tours-website_media-data:/media:ro -v "$PWD":/backup alpine \
  tar czf /backup/sltours-media-$(date +%F).tar.gz -C /media .
```

Restore with `pg_restore` and by extracting the archive into an empty `media-data` volume before starting
the API. Store backups encrypted and off the server (they contain personal data).

## Deletion and retention

- Removing a vehicle or website photo deletes the stored file after the transaction commits and keeps the
  `media_files` row as a deletion record (`deleted_at`, `deleted_by`) plus an audit event.
- Inspection photos, selfies and driver documents are kept: they are the accountability record for vehicle
  handovers. There is no automatic purge yet.
- **Open decision (POPIA):** SL Tours must set retention periods for selfies, inspection photos and driver
  documents (for example "inspection media 24 months after the trip; driver documents until 12 months after
  the driver leaves"). Once decided, implement a scheduled purge that calls `MediaService.delete` so each
  deletion is audited.

## Future: object storage

When moving to Oracle Object Storage: use a private bucket, server-side encryption, and either API-proxied
downloads (as today) or short-lived pre-authenticated requests generated only after the same ownership
checks. Copy existing files by key; `media_files.storage_key` stays valid.
