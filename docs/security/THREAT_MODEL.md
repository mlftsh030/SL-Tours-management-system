# Threat Model

## Assets

- Customer contact details and travel dates.
- Booking, payment, and pickup information.
- Staff operational data.
- Tour pricing and quote information.
- Admin credentials.

## Threats And Mitigations

| Threat | Risk | Current/Future Mitigation |
| --- | --- | --- |
| IDOR | Customer accesses another trip | Use opaque references/tokens now; add authenticated ownership checks before production |
| SQL injection | Data compromise | Spring Data/JPA parameterization |
| XSS | Session/data compromise | React escaping, avoid raw HTML, future CSP |
| Brute force | Admin account compromise | Strong env password now; add rate limiting/MFA later |
| Mass assignment | Unauthorized field changes | DTOs instead of exposed entities |
| Secret leakage | Credential compromise | `.env.example` only, no real secrets |
| Payment data exposure | Legal/compliance risk | Never store raw card details |
| Insecure uploads | Malware or data exposure | Uploads not enabled; require MIME/extension/size scanning before launch |
| Sensitive URLs | Trip privacy leak | Avoid predictable trip IDs; use auth/token strategy |
| Assistant prompt injection | Visitor or catalogue text steers the assistant or seeks private data | Six public catalogue/planner tools only, server-validated inputs; replies checked so amounts, times, discounts and booking claims come from structured tool results; plans rendered from planner data; enquiries need explicit visitor confirmation (`docs/operations/PLANNING_ASSISTANT.md`) |
| Commission fraud | Self-referral, duplicate or inflated commission | Commission only on completed, fully paid bookings under a recorded agreement; self-referral check; unique ledger indexes and row locks; idempotent payments and payouts; admin-only money actions; audit log |
| Cross-partner data access | A partner sees another partner's referrals or guests' details | Partner resolved from the login on every request; queries scoped by partner id; portal views omit customer data; tests for isolation |
| Assistant cost abuse | Excessive model spend | Per-session and per-address hourly limits, conversation length and count caps, daily request cap, daily and monthly spending caps checked before each model request |

## Highest Priority Gaps

1. Production authentication and authorization.
2. Customer trip ownership.
3. Audit logging for staff workflows.
4. Notification provider security.
5. File upload/storage controls.
