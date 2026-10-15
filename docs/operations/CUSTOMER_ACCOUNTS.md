# Customer Accounts

Customers can create an account, confirm their email address, sign in, reset a forgotten password, and see their own
enquiries, bookings, quotations and invoices. Visitors can still browse tours and send enquiries without an account.

This uses the existing Spring Security session login (HttpOnly cookie, CSRF, 30-minute idle timeout). There is no
separate identity provider. The only external dependency is an SMTP server for the verification and password-reset
emails.

## Setup

| Setting | Local Docker default | Deployment |
| --- | --- | --- |
| `SLTOURS_MAIL_PROVIDER` | `smtp` | `smtp`. `none` turns email off; the site then says that confirmation and reset by email are unavailable. |
| `SLTOURS_MAIL_FROM` | `SL Tours <no-reply@sltours.local>` | An address on a domain the provider is allowed to send for (SPF/DKIM set up). |
| `SLTOURS_SMTP_HOST` / `_PORT` | `mailpit` / `1025` | The provider's SMTP host, usually port 587. |
| `SLTOURS_SMTP_USERNAME` / `_PASSWORD` | empty | Provider credentials. Keep them in the deployment's secret store, not in git. |
| `SLTOURS_SMTP_STARTTLS` | `false` (Mailpit has no TLS) | `true` |
| `SLTOURS_ACCOUNT_LINK_BASE_URL` | `http://localhost:8080` | `https://sl-tours.com` (defaults to `SLTOURS_PUBLIC_BASE_URL`) |

Local Docker (`docker compose up --build`) starts **Mailpit**. Mailpit accepts every message and delivers none, so local
testing never emails a real person. Read the messages at http://localhost:8025.

**Not configured yet:** SL Tours has no production SMTP provider. Until one is set, a deployment cannot send verification
or reset emails. Customers can still register and sign in, but guest bookings cannot be linked to their accounts, and
forgotten passwords need staff help. The `production` profile refuses to start if `SLTOURS_SMTP_HOST` is `mailpit`,
`localhost` or `127.0.0.1`.

## What customers see

- **Public header and mobile menu:** *Sign in* and *Create account* for visitors; *My account* when signed in (staff,
  drivers and partners get a link to their own area instead).
- **Create account** (`/register`): full name, email, optional WhatsApp number with country code, password and
  confirmation. Each problem is shown next to its field and announced to screen readers, and focus moves to the first
  one. Both password fields have show/hide buttons. Buttons show a loading state while the form is submitting.
- **Sign in** (`/login`): links to *Forgot your password?* and to browsing without an account.
- **Recovery pages:** `/forgot-password`, `/reset-password?token=…` and `/verify-email?token=…`. Each page removes the
  token from the address bar as soon as it has read it.
- **My account** (`/account`), with its own navigation, separate from the staff sidebar:
  - Overview: counts, plus *Explore tours* and *Request a quote* when there is nothing yet.
  - Enquiries, Bookings, and Invoices & quotes (PDF download and payment status).
  - Proof-of-payment upload on issued invoices with a balance.
  - Profile: name and WhatsApp number. Email changes go through SL Tours.
  - A *Confirm your email address* banner with *Send a new link* until the address is confirmed.

## Ownership rules

- Public registration always creates a `CUSTOMER`. The request has no role field, and extra fields such as `role` or
  `emailVerified` are ignored.
- Enquiries and bookings made while signed in as a customer are linked to that account. When staff convert a customer's
  enquiry, the booking is linked too.
- **Guest records** are the bookings, enquiries and issued documents without a booking that were made without signing
  in. They are linked only after the customer opens the emailed verification link, which proves they control the
  address. Registering with a matching address that has not been confirmed shows nothing. After confirming, *Find
  earlier bookings* links any newer guest records made with that address.
- Every `/api/v1/account/**` call is resolved from the signed-in session. Another customer's document returns `404`,
  exactly as if it did not exist, for both the PDF download and proof upload.
- Proof of payment is evidence for staff. It appears under attachments on the invoice in administration, with a
  `CUSTOMER_PROOF_UPLOADED` event. The balance changes only when staff record and confirm the payment. Files can be
  PDF, JPEG, PNG or WebP, up to 15 MB, with at most 10 per invoice.
- Tokens: 256 random bits, stored as SHA-256 hashes, single use, and tied to the address they were sent to.
  Verification links last 48 hours and reset links 1 hour. A new link cancels earlier unused ones.
- Throttling: at most 1 verification email a minute and 5 a day, and 3 reset emails an hour, per account. On top of
  that, nginx allows about 10 auth requests a minute per address and the API limits each address per endpoint.
- A password reset signs out every existing session. Reset requests give the same answer whether or not the address has
  an account.
- Duplicate registration returns *An account with this email already exists*, with links to sign in or reset the
  password. This tells someone whether an address is registered, which was chosen in exchange for a clear message.

## API

| Method | Path | Access |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | public; returns `{email, verificationEmail: SENT\|NOT_CONFIGURED\|FAILED}` |
| POST | `/api/v1/auth/verify-email` | public; `{token}`, returns the counts of linked guest records |
| POST | `/api/v1/auth/password-reset/request` | public; `{email}` |
| POST | `/api/v1/auth/password-reset/confirm` | public; `{token, password, passwordConfirmation}` |
| GET / PUT | `/api/v1/account/profile` | customer |
| GET | `/api/v1/account/enquiries` | customer |
| POST | `/api/v1/account/verification-email` | customer |
| POST | `/api/v1/account/guest-records` | customer with a confirmed address |
| POST | `/api/v1/account/documents/{id}/proof` | customer (owner only) |

Schema: `V24__customer_accounts.sql` adds `user_accounts.phone`, `user_accounts.email_verified_at`, the `account_tokens`
table and `enquiries.account_id`. Existing staff, driver and partner accounts are marked as confirmed. Existing customers
confirm through the link like new ones.

## Verification (5 October 2026)

- **API integration tests** (`CustomerAccountFlowIntegrationTest`, 8 tests). They run on PostgreSQL 16 through the real
  security chain. Only the outgoing email is replaced, so the tests can read the links. Covered:
  - registration that tries to set a role;
  - duplicate and invalid registration;
  - guest records hidden until the address is verified, then linked;
  - single-use and expired links;
  - cross-customer access to lists, PDFs and proof upload;
  - staff access kept;
  - password reset signing out old sessions;
  - sign-out;
  - the not-configured email path.
- **End-to-end on the local Docker stack** (46 checks). A browser drives `http://localhost:8080` with nothing mocked.
  Emails go over SMTP to Mailpit, and the script follows the real links. Covered:
  - registration, verification, guest booking and invoice linking, PDF download and proof upload (seen by staff);
  - signed-in enquiry linking, sign-out, duplicate registration, wrong password;
  - another customer getting `404`, a `role` field being ignored;
  - password reset expiring the old session and links working once;
  - mobile menu and account area;
  - admin sidebar at 280px and as a drawer at 900px.
- **Not verified:** delivery through a real email provider, because none is configured (see Setup).
