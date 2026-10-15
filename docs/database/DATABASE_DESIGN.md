# Database Design

PostgreSQL 16, one database, schema managed by Flyway migrations in `apps/api/src/main/resources/db/migration`
(V1 to V25). Never change the schema outside a new migration.

The current model is documented in [Data model](DATA_MODEL.md):

- audit findings;
- relationship diagram;
- the enquiry → quotation → booking → invoice → payment chain;
- history and status rules;
- data dictionary;
- development-only data;
- open decisions.

Related:

- [Staff data guide](../operations/STAFF_DATA_GUIDE.md)
- [Backup and recovery](../operations/BACKUP_AND_RECOVERY.md)
- [Production database](../operations/PRODUCTION_DATABASE.md)

## Principles

- **Separate records.** A customer record (`customers`) and a login (`user_accounts`) are separate. A login is
  attached to a customer only after it verifies the email address.
- **Live vs issued data.** Live data (customers, catalogue, settings) can change. Issued documents, receipts and
  agreed booking prices are frozen copies.
- **Money.** All money is integer cents. Statuses for documents, payments and bookings are separate columns.
- **Corrections.** Nothing financial is deleted: corrections are revisions, voids, rejections and reversals, each
  with who, when and why.
- **Constraints.** Uniqueness, idempotency and concurrency rules live in the database (unique and partial indexes,
  exclusion constraints, row locks), not only in code.
- **Test data.** Development sample rows are flagged `demo_data`. Tests run on a disposable container database.
