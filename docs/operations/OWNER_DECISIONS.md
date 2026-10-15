# Owner Decisions

Facts and choices only SL Tours can make. Until each is answered, the site uses the safe interim behaviour shown:
nothing is invented, and unknowns are shown as "to be confirmed" or handled by quotation.

Mark an item answered by recording the answer, the date and who decided, then remove the interim behaviour.

**Launch blocker** means the public sl-tours.com should not move to this application until it is answered.

## Promotions and offers

| # | Question | Interim behaviour | Launch blocker |
|---|---|---|---|
| P1 | The old homepage advertised "free entrance fee and lunch" on city tours and "free wine tasting" on Winelands tours. The City & Heritage tour excludes meals and museum admission, and the Winelands tastings are part of the paid price. Do any such promotions exist? If so: which tour, what is included, eligibility, start and end dates. | Homepage slides describe the tours' actual inclusions, with no promotions ([HeroSlider.tsx](../../apps/web/src/features/home/HeroSlider.tsx)). | No |

## Private-trip pricing

| # | Question | Interim behaviour | Launch blocker |
|---|---|---|---|
| R1 | Hourly rate and minimum booking time for each vehicle class (sedan, V-Class, H1, Quantum, and larger vehicles). | Development sample rates are refused in production; vehicles without a live rate show "Priced on request" and lead to a quote request. | Yes, unless quote-only private trips are acceptable at launch |
| R2 | Distance: is distance included, and if not, how many km per hour and the charge per extra km? | "Distance limits and any charge for extra distance are not published yet; SL Tours confirms them with you." | Yes, if rates are published |
| R3 | Overtime: charge per extra hour on the day. | "Charges for extra time on the day are not published yet." | Yes, if rates are published |
| R4 | Other fees (fuel, tolls, parking, airport fees, night or public-holiday surcharges)? | None shown. Fees can be added under Operations → Private-trip rates. | Yes, if rates are published |

## Website addresses

| # | Question | Interim behaviour | Launch blocker |
|---|---|---|---|
| W1 | Where should the old `/faq` page lead? | Redirects to `/contact`. | No |
| W2 | Where should the old `/testimonials` page lead? | Redirects to the reviews section on the homepage. | No |
| W3 | Domain for staging (e.g. `staging.sl-tours.com`) and who manages DNS for sl-tours.com. | Staging runs on the droplet IP over HTTP. | Yes |
| W4 | Approval to move sl-tours.com from the current site to this application, and a date. | Not scheduled. | Yes |

## Email

| # | Question | Interim behaviour | Launch blocker |
|---|---|---|---|
| E1 | Email provider and sending address for account verification, password resets and notifications (e.g. a `no-reply@sl-tours.com` mailbox with SMTP access). | Local mail catcher only; the production profile refuses to start with it. | Yes |

## Customer communication

| # | Question | Interim behaviour | Launch blocker |
|---|---|---|---|
| C1 | How quickly can SL Tours reply to an enquiry or booking request (for example "within one working day"), and is it the same on weekends? | Confirmations show the reference number and say the team will contact the customer; no reply time is promised. | No |
| C2 | The public Tripadvisor listing address (the page where reviews are read, not the "write a review" form). Tripadvisor blocks automated checks, so the address could not be confirmed. | If the reviews widget cannot load, the homepage links to the Google listing only. | No |

## Tour terms

Once answered, these also become the tour pages' FAQs (Operations → Tours). Until then each tour page says what is not
published and asks visitors to confirm it when they enquire.

| # | Question | Interim behaviour | Launch blocker |
|---|---|---|---|
| T1 | Deposit, payment timing and cancellation/refund terms for tours and private trips. | Tour pages say these are confirmed with the quote. | Yes |
| T2 | Child prices and age limits. | Not published; confirmed with the quote. | No |
| T3 | City & Heritage tour: real duration and return time. It is advertised as about two hours (half day), but the sample itinerary runs 09:00 to after 11:15. | Practical note asks visitors to confirm times. | No |
| T4 | Airport transfer (R1,300 per trip): which areas are covered, maximum passengers and luggage, and the charge for waiting beyond the included time. | Price shown; conditions confirmed on enquiry. | No |
| T5 | Cape Peninsula: is the Cape Point funicular included? | Practical note says it is not stated. | No |
| T6 | Garden Route: accommodation standard, single-room option and supplement, whether the Botlierskop game drive and park entry are included. | Detail page lists the published inclusions only. | No |
| T7 | Winelands: which estates, and arrangements for children, guests who do not drink and dietary requirements. | Practical note asks visitors to mention these. | No |
| T8 | Smaller groups: for tours with a 5-guest minimum, can 1–4 guests book privately, and how is that priced? | Cards say "Fewer than 5? Ask for a private quote" and link to the custom-tour enquiry. | No |

## Privacy

The notice at `/privacy` ([PrivacyPage.tsx](../../apps/web/src/app/PrivacyPage.tsx)) states only what the website does
today. Update its "Last updated" date with any change.

| # | Question | Interim behaviour | Launch blocker |
|---|---|---|---|
| PR1 | Have the notice reviewed for POPIA by someone qualified. | Published as written; it makes no compliance claims. | Yes |
| PR2 | Retention periods for enquiries, bookings, invoices, accounts, server logs and backups. | Notice says information is kept only as long as needed, including records required by law. | Yes |
| PR3 | Name of SL Tours' Information Officer (registered with the Information Regulator), to be named in the notice. | Contact is the general email address. | Yes |
| PR4 | Email provider (see E1): its name and where it stores data, for the notice. | Notice says "our email provider". | With E1 |
