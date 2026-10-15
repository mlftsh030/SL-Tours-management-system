# Partnerships

Two separate routes. A business can apply for either or both; each route is reviewed separately, and referral commission
and supplier payments are kept in separate ledgers.

| Route | Who | What they get |
|---|---|---|
| Referral partner | Hotels, guesthouses, accommodation hosts, concierges, travel agents | A referral link and code; a partner area with their referrals' references and status; commission under a recorded agreement |
| Experience supplier | Safari operators, activity providers, local guides | A partner area to propose experiences; supplier payments recorded by SL Tours |

No rates, payout schedules, joining fees or contractual terms are built in. Applications are accepted before any terms exist.

## Application and review

1. The public page `/partner-with-us` (linked discreetly in the footer) collects business details, routes, a password and
   privacy consent. The login is created **disabled**.
2. Administrators review each route: **Approve**, **Decline** (reason required), **Suspend** (reason required) or
   **Reinstate**. A declined route can be approved on reconsideration.
3. The login is enabled while at least one route is approved. When none is, it is disabled and active sessions end.
4. Approving the referral route creates the partner's permanent referral code (8 characters, no ambiguous letters).

## Referral attribution rules

1. A booking or enquiry is credited to a partner only when it is submitted with the partner's code **and** the partner's
   referral route is approved at that moment. Unknown, declined or suspended codes are ignored, and the booking still succeeds.
2. Codes arrive in two ways:
   - **Referral link** (`/?ref=CODE`): the code is kept in the visitor's browser for up to a year, and a newer link replaces an
     older one (last referral wins). The capture time is reported by the browser and cannot be later than the submission.
   - **Typed code** on a booking or enquiry form: the capture time is the submission time.
3. Attribution is fixed when the booking or enquiry is created. A converted enquiry passes its attribution, and its
   original capture time, to the booking.
4. Only an administrator can change a booking's attribution. A reason is required, and the change is only possible while
   the booking has no commission lines. Every change is kept, with who and when, and written to the audit log.
5. Visitors see the disclosure "Referred by X. X may receive a commission from SL Tours if you book; your price is the
   same." They can remove the referral before submitting.
6. Attribution alone earns nothing (see below).

Covered submissions: tour bookings, private-trip bookings and quote requests, contact enquiries, Plan My Trip requests and
plans sent from the planning assistant.

## Commission

**Agreements.** An administrator records each agreement exactly as signed. Referral terms are:

- commission type: a percentage (stored in basis points) or a fixed amount per booking;
- eligible amount: net amount paid (payments less refunds, capped at the booking total) or the booking total;
- eligible booking types (tours, private trips);
- attribution window in days;
- refund treatment: proportional, or full reversal;
- optional minimum eligible amount and maximum commission per booking.

Supplier agreements record the supplier terms as text. Agreements cannot overlap for the same route (enforced by the
database) and cannot be edited, only ended. An end date cannot cut off bookings that already earned under the agreement.

**Earning.** One commission line is written when an attributed booking is **completed** and **fully paid** (net paid at least
the total), and every check passes:

- the referral route is approved at that time;
- a referral agreement was in force on the date the booking was made (Africa/Johannesburg);
- the booking type is covered by the agreement;
- the referral was captured less than the window's number of full days before the booking;
- it is not a self-referral (customer email equals the partner's email);
- it is not a development sample booking;
- the eligible amount meets any minimum.

The line stores the agreement, the basis amount and the full calculation, for example "10.00% of R4 200.00 (net amount
paid), rounded half-up under agreement AGR-1 (from 2026-10-01); booking made 2026-10-03, referral captured 2026-10-02".
Commission is evaluated whenever a payment, refund or status change is recorded. An administrator can also recalculate
it, for example after reinstating a partner.

**Money.** Integer cents throughout. Percentages use exact decimal arithmetic, rounded half-up to the cent.

**Refunds and cancellations.**

| Event after commission is earned | Result |
|---|---|
| Refund recorded, agreement says PROPORTIONAL | One adjustment of −(earned × refund ÷ basis), rounded half-up |
| Refund recorded, agreement says FULL_REVERSAL | The remaining balance is reversed |
| Booking set to REFUNDED or CANCELLED | Whatever balance remains is reversed |

Adjustments never take a booking's commission below zero. An adjustment after a payout appears as a negative unpaid line,
shown to the partner as owed back and netted against the next payout.

**Duplicates and concurrency.**

- The booking row is locked while commission is evaluated.
- Unique indexes allow one EARNED line and one REVERSAL per booking, and one adjustment per refund payment.
- Payments, payouts and supplier payments take idempotency keys, so a retry returns the original record.

## Payouts and supplier payments

Administrators record payouts after paying outside the system: date, method and payment reference, settling chosen
unpaid lines. The amount must equal those lines, and a line can only be paid once (the partner row is locked and lines are
claimed only while unpaid). Supplier payments are a separate ledger with their own references, optionally linked to a
booking.

Customer payments and refunds are recorded per booking in the administration's booking view. They set the booking's
amount paid, which commission depends on.

## Supplier proposals and the catalogue

Approved suppliers propose experiences: description, the prices, schedule and participation limits they state, and an
optional official page. Administrators can approve, request changes (with a reason) or decline (with a reason).

**Approval is commercial only.** It creates a **draft** Explore activity linked to the supplier, with:

- no price rules and the price status "not published";
- the schedule set to "varies";
- participation limits marked unknown;
- the supplier's figures kept only in the internal verification notes, marked NOT VERIFIED.

Publishing then needs the normal workflow: an official source, verification, and the publication checks. Published
activities from a supplier show "This experience is provided by X, an SL Tours supplier partner. SL Tours has a commercial
relationship with this supplier." This appears on the page and in the server-rendered HTML.

## What partners can see

Their own profile, route status, agreement terms, referral link and code, and their referrals as:

- booking or enquiry reference;
- product type;
- date received;
- coarse status (received, awaiting payment, confirmed, completed, cancelled, refunded);
- commission status.

They also see their commission statement, payouts and supplier payments. They never see customer names, contact
details, travel details, chats, driver documents, inspection records, or other partners' data. Every partner endpoint
resolves the partner from the login. Another partner's proposal returns "not found".

## Audit events

- PARTNER_APPLIED
- PARTNER_{ROUTE}_{STATUS}
- PARTNER_AGREEMENT_RECORDED and PARTNER_AGREEMENT_ENDED
- REFERRAL_ATTRIBUTION_CHANGED
- PAYMENT_RECORDED and REFUND_RECORDED
- COMMISSION_EARNED, COMMISSION_ADJUSTED and COMMISSION_REVERSED
- PARTNER_PAYOUT_RECORDED and SUPPLIER_PAYMENT_RECORDED
- SUPPLIER_PROPOSAL_*

## Unresolved business decisions

The behaviour each implemented default produces is noted where relevant.

1. **Commission rates, fixed amounts, eligible basis, booking types, attribution windows, minimums and caps** per partner.
   Nothing is earned until recorded.
2. **Payout schedule and method.** When commission becomes payable (for example monthly, or after a holding period
   following travel), and any minimum payout. Currently an administrator pays whenever they choose.
3. **Refund treatment.** Proportional or full reversal per agreement, and whether partners repay commission already paid
   (currently carried as a negative balance).
4. **Suspended partners.** Whether completed bookings referred before a suspension still earn. Currently they do not earn
   while suspended; an administrator can recalculate after reinstatement.
5. **Rounding.** Currently half-up to the cent. Confirm, or specify another rule in agreements.
6. **VAT and tax treatment** of commission and supplier payments, and whether partners invoice SL Tours. Not modelled.
7. **Joining fees, exclusivity, marketing rules and termination terms.** None implemented.
8. **Supplier commercial terms** (net rates, commission to SL Tours, payment timing) and how supplier experiences are
   priced and sold. Approved experiences are catalogue drafts only; selling them is not yet implemented.
9. **Attribution conflicts.** Last referral wins in the browser, and a typed code replaces a link. Confirm, or choose first referral.
10. **Partner agreement documents.** Where signed agreements are stored. The system keeps a reference only.
11. **Contact-detail sharing.** Whether referral partners may ever see guest names (for example for concierge follow-up).
    Currently never.
