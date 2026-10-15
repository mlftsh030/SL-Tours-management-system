# ADR-005: File Storage

## Context

Vehicle inspections need photos and a collection selfie; driver verification needs licence and PrDP documents;
administrators manage vehicle and website images. Inspection media and documents are personal data.

## Decision

Store files on a private persistent Docker volume behind a `MediaStorage` abstraction. Keep metadata and
relationships in PostgreSQL (`media_files` and owning tables). Serve private files only through authorised
API endpoints; public files only when explicitly marked PUBLIC. Detect file types from content, enforce size
limits, and generate storage keys on the server. Oracle Object Storage (or S3) is a later implementation of the
same interface, not a dependency now.

## Consequences

- Single-server deployments need no extra service; backups must include the media volume with the database.
- Horizontal scaling requires the object-storage implementation (shared storage).
- Retention periods remain a business decision; see `docs/operations/MEDIA_STORAGE.md`.
