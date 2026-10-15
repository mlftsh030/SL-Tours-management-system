# POPIA Notes

This is an engineering overview, not legal advice.

## Principles

- Purpose limitation: collect only booking, quotation, trip, and support information needed by SL Tours.
- Data minimisation: avoid collecting passport, ID, medical, or license data unless a specific operational requirement exists.
- Security safeguards: protect staff endpoints, database credentials, backups, logs, and exports.
- Openness: privacy notices should explain what data is collected and why.
- Retention: define how long enquiries, bookings, quotes, payment records, and support messages are kept.
- Data subject rights: plan processes for access, correction, and deletion where legally appropriate.

## Implementation Notes

- Current seed data uses demo identities only.
- Future audit logs should record staff access to customer records.
- Logs must not include passwords, tokens, raw card data, or unnecessary personal details.
- WhatsApp/email integrations should have provider agreements and retention review.

## Operations data (drivers and inspections)

- Driver licence, PrDP and identity documents are collected because South African passenger transport requires a valid licence and PrDP. They are visible to administrators only.
- Collection selfies are an accountability record for vehicle handovers. No biometric processing or facial recognition is performed. Drivers are told this in the portal.
- Inspection photos may show bystanders or number plates; drivers photograph the vehicle only.
- Drivers see a customer's name and phone number only for their own assignments, and no customer email or booking history.
- Retention periods for selfies, inspection photos and driver documents must be decided by SL Tours; see `docs/operations/MEDIA_STORAGE.md`.

## Planning assistant

- Chat messages are processed in memory only, bound to the visitor's session, and discarded when the panel is closed, after
  the idle timeout or on restart. They are not logged or stored in the database, and SL Tours does not use them for training.
- Messages are sent to the configured model provider to generate replies. Name the provider in the privacy notice and confirm
  its retention terms before enabling live chat.
- Contact details are collected only in the confirmation form when a visitor chooses to send a plan; that enquiry follows
  the normal enquiry retention policy.

## Partners

- Applicants consent to SL Tours using their business details to assess the application; the consent time is stored.
- Referral partners see only booking and enquiry references, product type, date and coarse status for their referrals:
  never guests' names, contact or travel details. Whether this may ever change is an open business decision.
- Guests are told when a partner referred them and may remove the referral before submitting.
