# Invoices, quotations and receipts

SL Tours staff create quotations and invoices in the operations workspace. The documents use the company's Word template
layout (reference: invoice 100099). The system stores each issued document permanently, produces PDF and Word copies,
shares them by link, and records payments with receipts.

> **Showcase copy:** the generated example documents and screenshots are not included, because they reproduce a real
> invoice. In this copy the customer name, street address and bank account number are placeholders ("Sample Client",
> "100 Example Street", all zeros).

The reference invoice is a fixture in the tests (`BillingFixtures`). It is rendered directly with its printed number and
is never saved to the database, so it never takes up a number in the live sequence.

## Setup

Run Flyway migration `V23__invoices_quotations_receipts.sql` (Flyway applies it automatically at start-up). It creates the tables and the
settings row with the details printed on invoice 100099. No other setup is required, but before the first invoice an
administrator must:

1. Open **Billing settings** and confirm the next invoice number. Check the last invoice SL Tours sent outside this system
   first. Issuing an invoice is refused until this is confirmed. Quotations and receipts start new series at 1001.
2. Check the address copied from the template. The settings page flags it until someone ticks "I have checked the
   address spelling" (the original template contained a spelling mistake).
3. Add any banking details that are known (branch code, account type, SWIFT). None were invented; empty fields are not printed.

| Variable | Default | Purpose |
| --- | --- | --- |
| `SLTOURS_DOCUMENT_LINK_BASE_URL` | `SLTOURS_PUBLIC_BASE_URL` (`https://sl-tours.com`); Compose sets `http://localhost:8080` | Start of customer document links. Must reach the API's `/api/v1/public/documents/…` through the web server |
| `SLTOURS_DOCUMENT_LINK_DAYS` | `14` | Default lifetime of a customer link |
| `SLTOURS_DOCUMENT_LINK_MAX_DAYS` | `90` | Longest lifetime staff can choose |
| `SLTOURS_MEDIA_ROOT` | `/var/lib/sltours/media` | Where generated PDFs, proof of payment and logos are stored (existing private upload storage) |

Fonts are bundled in the API (`apps/api/src/main/resources/billing/fonts`): Liberation Sans, which has the same character widths as
Arial, and Barlow Bold for the heading. Both are under the SIL Open Font License, and the server needs no installed fonts.
The original logo (681 × 666, from `sltours_invoicetemplate.docx`) is `billing/sl-tours-logo.jpg`; an administrator can replace it.

## Staff workflow

**Invoices & quotes** in the operations menu shows the steps on each screen:

1. **Customer and booking.** Type the customer or organisation (previous documents and bookings are suggested), contact
   person, email and WhatsApp number. Optionally enter a booking reference and press **Fill from booking**, or start from
   **Bookings → Create invoice / Create quotation**. Booking documents start at the booking's agreed total.
2. **Services and pricing.** Each service has a bold title, any number of transfer legs (a pickup and one or more drop-offs, typed one per line;
   several become a bulleted list), free text details, quantity, unit and unit price. **Add deduction** adds a negative
   line such as "Less: 50% deposit". A service can come from the tour catalogue. If its price is changed, the editor
   asks for a reason, and the catalogue price is looked up on the server, not taken from the browser. Discount, dates,
   passengers, vehicle, itinerary notes, a note to the customer, custom terms and the layout (standard or detailed) follow.
   Internal notes are for staff only.
3. **Preview.** The preview beside the form (below it on a phone) is the server's layout of the document, so its totals
   and wording are the ones that will print. Unsaved work is also kept in the browser, so a refresh or a lost connection
   does not lose it.
4. **Issue** (administrator). After confirming, the document gets its number and is frozen.
5. **Download or share.** Download the PDF or the Word file, or use **Share** (below).
6. **Record payment** for invoices; for quotations, **Mark accepted / declined** or **Convert to invoice**.

Other actions: **Duplicate** (a new draft with the same details), **Revise** (corrects an issued document, see below),
**Void** (with a reason) and **Discard draft**. The list can be searched by customer, email, number or booking and
filtered by type, status, payment status, date range and "overdue only".

The Word file is for staff to edit outside the system. The page says that edits made in Word do not change the stored document.

## Data model

| Table | Holds |
| --- | --- |
| `company_settings` | One row: company name, tagline, contacts, address (and review flag), logo, banking, payment instruction, invoice and quotation terms, currency, tax setup, default due and validity days |
| `document_sequences` | Next number and "confirmed" flag for `INVOICE`, `QUOTATION`, `RECEIPT` |
| `billing_documents` | Quotations and invoices. `content` (JSON) is the editable draft; `snapshot` (JSON) is everything printed at issue (customer, services, prices, totals, company, banking, terms); `issued_pdf_media_id` is the stored PDF. Searchable columns (customer, dates, totals, paid, payment status) are copied from the content. Links to `bookings`, `user_accounts`, the source quotation and revisions |
| `billing_payments` | Payments with amount, date, method, reference and status `PENDING`/`CONFIRMED`/`REJECTED`; confirmed ones carry the receipt number, receipt snapshot and stored receipt PDF |
| `billing_attachments` | Proof of payment files (private media) |
| `billing_share_links` | Customer links: SHA-256 hash of the token, expiry, withdrawal and open count |
| `billing_events` | Per-document history; every entry is also written to `admin_audit_events` |

Constraints enforce: one number per kind, never reused; issued documents always have a number and snapshot; one live
invoice per quotation; one live revision per document; paid ≤ total; confirmed payments have a receipt. Customers are
not copied into a new table. Documents reference the booking or account when there is one, and keep the printed customer
details in the snapshot. The unused V1 tables `quotes` and `quote_items` are left as they were.

Document status and payment status are separate: `DRAFT → ISSUED (→ ACCEPTED / DECLINED for quotations) → VOID`, and for
invoices `UNPAID → PARTIALLY_PAID → PAID`.

## Numbering

- Drafts have no official number; they are shown as "Draft 1a2b3c4d" and downloaded as `INV-DRAFT-1a2b3c4d.pdf`.
- Issuing allocates the number in the same database transaction. The sequence row is locked, so simultaneous requests
  queue and each gets a different number. The allocator never goes below the highest number already stored, and a unique
  index is the final guard. A number is used only if the transaction commits. A retried issue request returns the same number.
- Voided documents keep their number; it is never reused. An administrator can move the next number forward but never to
  or below a number already issued.
- Invoices print the plain number (`100100`) as on the template; files are named `INV-100100.pdf/.docx`,
  `QUO-1001.pdf`, `REC-1001.pdf`.
- Tested: 16 concurrent issue requests for 8 drafts produce 8 consecutive, distinct numbers.

## Calculations

All money is whole cents (`long`); totals sent by the browser are ignored.

1. Line total = quantity × unit price, rounded half-up to the cent. Quantities allow two decimals.
2. Subtotal = sum of line totals. Deduction lines may be negative; the subtotal may not.
3. Discount = a fixed amount (not above the subtotal) or a percentage of the subtotal, rounded half-up.
4. Tax = (subtotal − discount) × rate, rounded half-up. Prices are treated as tax-exclusive. Tax is **off** unless an
   administrator enables it with a name and rate; documents are always titled INVOICE or QUOTATION, never "Tax Invoice",
   and no registration number is printed unless one is entered.
5. Total = subtotal − discount + tax. Paid = confirmed payments only. Balance = total − paid.

Amounts print like the template: `R2 850`, `2 850,50` and `(22 900)` for deductions.

The standard layout follows the sample's two-column DESCRIPTION / COST - R table. With a single service and no adjustments,
the cost column stays empty and only the grand total shows, as on invoice 100099. Otherwise each service shows its amount,
and subtotal, discount and tax rows appear above the grand total. Quantities other than 1 show as "2 × R1 950 per vehicle".
The detailed layout shows that line on every service.

## Issued documents, revisions and conversion

Issuing freezes the customer details, services, prices, totals, company details, banking details and terms as they are
that day. Changing tours, prices or settings later does not affect issued documents. This is tested by changing a tour
price and the bank name after issuing; the stored PDF stays byte-for-byte the same.

Corrections use **Revise**. It creates a draft linked to the original. When the revision is issued it gets a new number,
the original becomes VOID ("Replaced by invoice N"), confirmed payments move to the revision, and the original's links stop
working. The revision cannot total less than the payments already confirmed. A quotation converts to an invoice draft with
the same customer and services; converting again opens the same invoice.

## Payments and receipts

Staff record a payment (amount, date, method, reference, notes) and can attach proof of payment (PDF or photo, checked to
be a real, complete file, up to 15 MB). The payment stays **waiting for confirmation**: proof shows money was sent, not
that it has arrived. An administrator confirms when the funds are in the account, or rejects with a reason. Confirming
allocates a receipt number and stores the receipt (PDF, with a Word copy on request) showing the payment, the total paid
so far and the balance. Payments above the open balance (including payments still waiting) are refused. Retries with
the same request key do not record a payment twice.

These payments belong to the invoice. Payments recorded under **Bookings → Review** (which drive partner commission)
are a separate record; record a payment in one place only.

## Document generation and storage

- `DocumentViews` turns the saved data into one printed view. `PdfRenderer` (openhtmltopdf + PDFBox) lays it out on A4
  with embedded fonts, so text is real, selectable text. `DocxRenderer` writes the same view as Office Open XML with real
  tables, a Word bullet list and the logo, strip and contact details in the footer. The live preview shows the same view,
  so the three cannot disagree. A test compares the text of the PDF and the Word file.
- Long documents continue on further pages: the table header repeats and the footer is on every page. The last service
  stays on the same page as the totals, so a total is never printed alone. Tests check that no text overlaps on any page.
- Issued PDFs and receipts are stored once in the existing private media storage (`BILLING_DOCUMENT`) and served
  unchanged. Drafts and Word files are generated when requested. Voided documents are regenerated from the snapshot with "VOID".

## Sharing: WhatsApp, email and links

**Share** offers:

1. **Prepare WhatsApp message.** Creates a customer link and a message with the customer's name, the number, total,
   outstanding balance, due date if set, the link, its expiry, and a request for proof of payment. Staff can edit it,
   then **Open WhatsApp** opens `wa.me` for the customer's number (South African numbers starting with 0 are converted
   to 27…). Staff choose the chat if needed and press send themselves.
2. **Share the PDF file…** hands the actual PDF to the device's share sheet where the browser supports file sharing
   (most phones). Elsewhere it explains how to download the PDF and attach it in WhatsApp (paperclip, then Document).
3. **Download PDF**, plus **Copy message** and **Copy link**.
4. **Prepare email** opens the email app with the subject and message filled in.

What the history records: link created, WhatsApp opened, email app opened, PDF handed to the share sheet, downloads,
link opened by the customer, link withdrawn. It never records "sent" or "delivered", because opening WhatsApp or an email
app does not show the customer received anything. There is no WhatsApp Business API and no email service in this
application, so nothing is sent by the server. A provider can be added behind `BillingShareService` once credentials and
approval exist; until then the capabilities endpoint reports both as not configured. A real API integration should record
"sent" and "delivered" only from the provider's confirmations.

Customer links (`/api/v1/public/documents/{token}`): 256-bit random token, only its hash stored, default 14 days (up
to 90), can be withdrawn on the document page, and open exactly one issued document or receipt. The number is not used as a
key. Expired, withdrawn, unknown and voided links show a short page with contact details and reveal nothing else.
Responses are `no-store`, `noindex` and `no-referrer`.

Signed-in customers also see their issued invoices and quotations on **My Trip** (`/account`). These are the documents
linked to their account or to one of their bookings, with balances based on confirmed payments.

## Permissions and audit

| Action | Staff | Administrator |
| --- | --- | --- |
| View, search, preview, download PDF/Word | ✓ | ✓ |
| Create and edit drafts, duplicate | ✓ | ✓ |
| Share, create and withdraw customer links | ✓ | ✓ |
| Record payments and attach proof | ✓ | ✓ |
| Issue, void, discard, revise, accept/decline, convert | | ✓ |
| Confirm or reject payments | | ✓ |
| Change billing settings, banking, logo, numbering | | ✓ |

The server enforces these rules (`AdminBillingController` with method security, all under `/api/v1/admin/**`); the
interface only hides buttons. Drivers, partners and customers get 403 from the staff endpoints. Customers can reach
only their own issued documents, and another customer's document behaves as missing. Internal notes, drafts and proof
of payment are never included in customer documents, links or the account page. Proof and generated files are private
media, readable only by staff.

Every create, save, issue, duplicate, revision, conversion, decision, void, payment, proof, confirmation, rejection,
link and share action is recorded in `billing_events` (shown as **History** on the document) and in `admin_audit_events`
with the user and time. Banking changes are logged as `BILLING_BANKING_CHANGED` with the account number masked
(`****0000`); numbering, logo and tax changes are logged too.

## Backup and recovery

Invoices are financial records. South African tax rules generally expect business records to be kept for five years; confirm the period with SL Tours' accountant.

- **Database:** back up PostgreSQL (including `billing_*`, `company_settings`, `document_sequences`,
  `admin_audit_events`) on a schedule, for example nightly `pg_dump` kept off the server, and test a restore.
- **Files:** back up the media volume (`media-data` in Compose, `SLTOURS_MEDIA_ROOT`). It holds issued PDFs, receipts,
  proof of payment and the logo. Back it up together with the database so that file references match.
- **After a restore:** check `document_sequences` against the highest numbers in `billing_documents` and
  `billing_payments`. The allocator never goes below the highest stored number, but invoices issued after the backup
  point and missing from the restore must be re-entered, or the next number moved past them in Billing settings.
- If an issued PDF file is lost, the document can still be regenerated from its snapshot (the snapshot is the record).
  The stored file is for exact reproduction.

## Checks performed

- Backend (`mvn verify`): 152 tests, including 31 new billing tests. They cover calculations and rounding, concurrent
  numbering, retries, voided numbers, booking prefill and price snapshots, catalogue override reasons, tax switched off,
  quotation conversion, partial and full payments, proof as evidence only, rejections, revisions, internal notes, stale
  edits, filters, share links (expiry, withdrawal, void, invalid tokens, receipts), customer ownership and role
  boundaries. Rendering checks cover A4 size, embedded fonts, text, multiple pages, overlaps, the totals page, repeated
  headers, PDF/Word consistency and Word package validity.
- Frontend (`vitest`): 57 tests, including money parsing, form conversion, the preview, role-based actions,
  issue confirmation, the WhatsApp message, native file sharing and the download fallback.
- Browser run on Docker as administrator, desktop and phone: settings → new invoice with two transfer legs → live
  preview → save → issue (100100) → WhatsApp message prepared → part payment with proof → confirmed (receipt 1001,
  balance R1 850) → quotation from a booking on a phone → issued → converted to an invoice. There were no page errors and
  no sideways scrolling. The customer link was checked through nginx (PDF, headers, unknown token page).

## Open configuration items

- Confirm the next invoice number in Billing settings (issuing is blocked until then).
- Confirm or correct the address spelling.
- Add the branch code, account type or SWIFT code if they should be printed; set a default due date and quotation validity if wanted.
- Set `SLTOURS_DOCUMENT_LINK_BASE_URL` (or `SLTOURS_PUBLIC_BASE_URL`) to the live site address before sharing real links.
- Enable tax only if SL Tours is registered, with the real rate and number.
- WhatsApp Business API or email sending: not implemented; it needs a provider, credentials and a separate decision.
