# Staff data guide

How SL Tours staff keep customer, booking and payment records correct in the administration area
(`/operations`). Background on how the records fit together is in [Data model](../database/DATA_MODEL.md).

**Who can do what**

| Action | Staff | Administrator |
| --- | --- | --- |
| Search customers, view history, correct name/phone/country/notes | ✓ | ✓ |
| See possible duplicates | ✓ | ✓ |
| Merge customer records | | ✓ |
| Prepare quotations and invoices, record payments, attach proof | ✓ | ✓ |
| Issue, void or revise documents; accept a quotation and create the booking | | ✓ |
| Confirm, reject or reverse payments | | ✓ |
| Export CSV files | | ✓ |

## Every day: Follow-up

**Sales & Customers → Follow-up** collects everything that needs action, oldest first. Sample data is left out.

- **Enquiries awaiting follow-up:** new for more than 4 hours, in progress for more than 2 days, or waiting for the
  customer for more than 5 days. Open the enquiry, reply, and update its status.
- **Upcoming trips without a vehicle or driver:** bookings in the next 21 days, including unconfirmed requests, with no
  active assignment. Assign them in **Operations → Trips & inspections**.
- **Invoices with a balance:** overdue first, showing any amount waiting for confirmation.
- **Payment evidence to check:** recorded payments waiting for an administrator, and proof the customer uploaded from
  their account that has not been matched to a payment yet.
- **Possible duplicate customers:** the number of groups to review.

## From enquiry to paid booking

1. **Enquiry.** Open it in **Enquiries**.
   - For a standard tour, you can still use *Convert to booking* as before.
   - For anything you need to price, click **Prepare quotation**. The quotation starts with the customer, date,
     passengers and route from the enquiry, and remembers which enquiry it answers.
2. **Quotation.** Check the services and prices and save the draft. An administrator **issues** it, which gives it
   the next number and freezes it. Share it with the customer.
3. **Accepted.**
   - An administrator clicks **Accept and create booking**. The booking takes the customer, services, stops and the
     quoted total exactly as issued, and the enquiry is marked booked.
   - If the quotation had no date or number of passengers, you are asked for them.
   - Clicking twice is safe: the same booking is returned.
4. **Invoice.**
   - On the quotation, click **Convert to invoice**. The invoice belongs to the booking.
   - For bookings made directly, start an invoice from the booking instead.
   - An invoice without a booking is fine for one-off services.
5. **Payment.**
   - Record the payment on the **invoice** (amount, date, method, bank reference) and attach the proof. It waits for
     an administrator.
   - The administrator checks the bank account and clicks **Funds received: confirm**. A receipt is created, and the
     invoice balance, the booking's amount paid and any partner commission update together.
   - Once a booking is invoiced, the booking's own payment form refuses new payments, so money is never counted
     twice.

## Corrections

| Problem | Do this |
| --- | --- |
| Customer's phone or spelling changed | Correct it on the customer record. Issued documents keep what they were issued with; new documents use the correction. |
| Wrong amount or line on an issued quotation/invoice | **Revise**. Issuing the revision gives a new number, voids the old one and moves its confirmed payments across. |
| Document not needed | **Void** with a reason. The number is never reused. |
| A recorded payment never arrived | **Reject** it (while it is still waiting) with a reason. |
| A confirmed payment bounced, was charged back or was recorded twice | **Reverse** it (administrators) with a reason. The balance and booking go back; the receipt stays on file marked reversed. |
| A customer cancels | Set the booking to cancelled (assignments are cancelled with it); then void or revise the invoice as needed. Partial refunds are an open decision; agree them with the owner and record the reversal or refund. |

## Customer records

**Sales & Customers → Customers.**

- **Search** by name, email, phone (any format), booking or enquiry reference, or invoice number.
- **A customer's record** shows every enquiry, booking, quotation, invoice and payment, whether they have a login,
  and the merge history.
- **Edit details** corrects the name, phone, country and staff notes. The email address identifies the customer and
  is not edited here.
- **One email, one record.** Records with the same email address join automatically, because the email is the only
  automatic match.
- **Possible duplicates** lists records with the same phone number, or the same name and different emails. These are
  only suggestions.

**Merging (administrators)**

1. Confirm with the customer that it is the same person, for example by phone or a reply from both addresses.
2. Open the record to keep, and under **Merge a duplicate into this record** find the other one.
3. Give the reason and confirm.

What a merge does and doesn't do:

- The other record's enquiries, bookings and documents move across.
- Both records and the list of moved items are kept, and the merge is in the audit log.
- What the customer can see in their own account does not change.
- Two records that each have their own login cannot be merged.
- There is no undo button: the merge history lists exactly what moved, so a developer can reverse it if needed.

## Exports

**Sales & Customers → Data exports** (administrators). Choose dates and download:

- customers, enquiries, bookings, quotations/invoices or invoice payments, as CSV;
- up to five years at a time.

Each export is recorded with your name, the dataset, the dates and the row count. Exports contain personal data: keep
them in SL Tours' own storage, don't email them around, and delete them when done. Passwords, sign-in tokens, staff
notes and uploaded files are never exported.

To answer a customer's request for their data, open their customer record (it lists everything held about them), or
filter an export by their dates.

## Test and sample data

- Records marked **Sample** come from the development seed and are left out of queues and reports.
- Never test on the live site with real-looking customer details. Use the development environment (see
  [Production database](PRODUCTION_DATABASE.md)).
