# 01 — Blueprint: Strategy & Market Analysis

wedjan is a **booking platform, never a directory**. Every decision in this document flows
from documented failures of the incumbents and validated 2026 user demand.

---

## 1. Unique features worth copying (by platform)

Each of these is the *one thing* a competitor does well. wedjan combines all of them.

| Feature | Who does it | Why it wins |
|---|---|---|
| Filter vendors by real date availability | Zola | Kills the #1 wasted inquiry ("are you free on my date?") |
| Actual prices shown, not $ symbols | WeddingWire, Tagvenue | 78% of couples say price is the top contact factor |
| Budget tool auto-estimating local costs by city + guest count | The Knot | Instant realism; reduces budget-mismatch ghosting |
| Seating chart auto-synced to RSVPs | The Knot | Removes double data entry |
| Drag-and-drop room-visual table planner | Hitched | Emotional "aha" feature couples love |
| Style-quiz onboarding → matched suppliers | Bridebook | Personalization from minute one |
| Search inspiration photos by garment/decor detail | WedMeGood | Visual search = discovery moat in South Asia |
| Contact vendors without exposing your phone number | WedMeGood | Privacy = trust, especially for brides |
| Family collaboration wall + shared checklist | WedMeGood | Weddings are family projects in Asia — plan for 6 users, not 2 |
| Multi-day / multi-ceremony event templates | WedMeGood, ShaadiSaga | Sangeet + mehndi + baraat ≠ one event; 37% of US couples now host extra events too |
| $1M liability coverage on every booking | Peerspace | Converts risk-averse corporate bookers |
| Vetted-only listings + platform helps build the listing | Peerspace | Supply quality as brand promise |
| Concierge that books extras (catering, AV) onto a venue booking | Peerspace | Raises AOV per transaction |
| 0% customer-side fees | Tagvenue | Demand-side growth lever |
| Marketplace + AI CRM for the vendor in one product | Tagvenue Pro, Wedy Pro | Lock-in: vendors run their whole business on you |
| Exclusive (never shared) leads | Wedy, Zola-ish | Directly answers the industry's loudest vendor complaint |
| Real bookable packages instead of inquiry forms | Wedy, Peerspace | Directory → transaction platform = take-rate on GMV |
| AI matching on billions of signals → 98% show rate | Instawork | Reliability is the entire product in staffing |
| Skills quizzes + attire approval + phone screens | Instawork | Vetting depth = premium pricing power |
| Machine-readable vendor profiles for AI-search discovery | Events in Minutes | 72% of planners now discover via marketplaces or AI search — AEO is the new SEO |
| Portfolio pages built around real events, tagging every vendor | PartySlate | Every completed event becomes marketing for all its vendors |
| Lowest-commission positioning (5%) | GigSalad | Supply acquisition weapon |

---

## 2. What incumbents are all lacking (documented gaps & complaints)

These are not guesses — each maps to complaint records, surveys, or press coverage.

### 2.1 Trust & ranking failures

- **Pay-to-play rankings.** Page 1 = who paid most, not who's best (The Knot/WeddingWire).
  Couples are told by their own community to bypass platform messaging entirely.
- **Fake/low-quality leads.** ~200 vendor complaints reached a US Senator; FTC investigation
  requested; allegations include fake leads, unhonored ad contracts, false discounts,
  NDA-silenced whistleblowers.
- **Shared leads.** One inquiry blasted to 5+ vendors → fastest discounter wins, conversion
  collapses, vendors burn out.
- **Zero vendor verification depth.** Scam vendors with stolen photos and fake reviews still
  get listed; a whole new marketplace (DayOfWeddings) launched in 2026 purely on
  "we verify business registration + insurance."

### 2.2 Pricing opacity (the #1 consumer complaint)

- 78% of couples say pricing decides who they contact — yet most vendors/venues hide it.
- Analysis of 847 venue sites: ~88–89% hide real pricing; "starting at" quotes jumped ~112%
  in final quotes; transparent venues got **43% more qualified inquiries** and booked
  **~60% faster**.
- Directories tolerate this because vendors are the paying customer.
  **A booking platform can mandate transparency.** wedjan does.

### 2.3 Broken transaction model

- **The directory loop:** inquiry form → wait → discovery call → maybe a price.
  71% of couples feel unprepared for the decision volume. Nobody has fixed the loop at scale.
- **No payment protection.** Deposit scams (vendor no-shows, venue shutdowns, photo ransom)
  are common enough that consumer agencies publish warning guides. Directories hold no money,
  so they can't protect anyone. Escrow/milestone payments are absent from every major
  wedding directory.
- **Ghosting both directions.** Vendors' top-2 pain: budget mismatch + couples vanishing.
  Couples' pain: vendors slow or never reply. No platform enforces response-time SLAs or
  shows response-rate badges prominently enough to change behavior.

### 2.4 Structural gaps

- **Nobody runs all three sides.** Venue marketplaces (Peerspace/Tagvenue) don't do vendors.
  Vendor directories (Knot/GigSalad) don't do staffing. Staffing apps (Instawork/Qwick)
  don't touch customers planning events. No platform cleanly connects
  **customer → vendor → freelancer/gig crew in one transaction graph. This is the empire thesis.**
- **Vendor tool fragmentation.** Marketplace here, CRM there (HoneyBook/Dubsado), payments
  elsewhere, contracts elsewhere. Only Wedy and Tagvenue have started bundling — both tiny
  and single-region.
- **12-month lock-in contracts** with no ROI tracking and retention-specialist cancellation
  walls — the most hated vendor experience in the industry.
- **Sub-vendor/crew invisibility.** A photography studio needing a second shooter, or a
  caterer needing 6 servers for Saturday, has to leave every marketplace and go to
  Instawork/WhatsApp. The marketplace loses that transaction entirely.
- **Regional blind spots.** TKWW covers US/EU/LatAm; WedMeGood covers India. Nepal,
  Bangladesh, Sri Lanka, SE Asia, Middle East diaspora weddings, and AU multicultural
  weddings are underserved. Destination weddings across borders are unsupported by any
  single platform.
- **Weak AI-search readiness.** Almost no marketplace structures vendor data for LLM-era
  discovery (schema, entity pages, structured FAQs) — only Events in Minutes markets this.
  Meanwhile 72% of planners already discover via marketplaces or AI search.
- **Post-event void.** No major marketplace handles the after: gallery delivery, review
  capture at emotional peak, guest photo sharing, anniversary re-engagement, vendor
  rebooking for the next event.

---

## 3. Validated demand — what users are asking for (2026)

### From couples / customers

1. **Upfront, all-in package pricing with instant "book this date"** — the single loudest demand.
2. **AI planning copilot.** 54% of couples now use AI to plan (150% growth in one year).
   They want it for logistics, budgets, timelines, etiquette, drafting — while keeping
   creative/emotional decisions human. Build the copilot with that boundary respected.
3. **Real-time availability calendars** — never inquire with an unavailable vendor again.
4. **Verification badges backed by documents** (business registration, insurance) — the
   anti-scam feature the news is now covering.
5. **Payment protection / escrow / milestone release** — deposit safety.
6. **Budget tracker wired to actual bookings and payments**, not a separate spreadsheet.
7. **Multi-event architecture** — welcome party, mehndi, ceremony, day-after brunch as one
   project (37% of US couples host extra events; 100% of South Asian weddings are multi-day).
8. **Hybrid/virtual guest experience** — livestream integration, remote guest participation.
9. **Sustainability filters** — 72% of 2026 couples weigh environmental impact;
   eco-certified venue/vendor tags.
10. **Off-peak dynamic pricing** — venues offer 30–40% weekday discounts; surface them.
11. **Personalization engine** — 90% want a highly personalized event; style-quiz →
    curated matches (Bridebook model, but deeper with pgvector).
12. **Candid/documentary style discovery** — search vendors by aesthetic, not just category.

### From vendors

1. **Exclusive leads only** — never resold, never shared.
2. **No lock-in contracts; pay on success** (commission or pay-per-booking), with visible
   ROI dashboards.
3. **Built-in CRM:** pipeline, proposals, contracts, invoices, automations in the
   marketplace itself.
4. **Response-rate and quality-based ranking** instead of ad-spend ranking.
5. **Fair review handling** with verified-booking-only reviews (kills fake reviews both ways).
6. **AI assistant for vendors:** quote drafting, inquiry triage, follow-up nudges to fight ghosting.
7. **A way to staff their own gigs** — second shooters, assistants, servers — without
   leaving the platform.

### From freelancers / gig workers

1. **Pay shown upfront + fast payouts** (24–72h standard now).
2. **Skill-verified profiles that travel with them;** ratings that unlock better gigs.
3. **Schedule autonomy;** favorites relationships with repeat vendors.
4. **Insurance/protection bundled into the booking.**

---

## 4. Why this weld wins

- **Wedy** proved transparency + booking wins vendors.
- **Tagvenue** proved marketplace + CRM wins venues.
- **Instawork** proved AI-vetted gig supply wins reliability.
- **Nobody has welded all three. That weld is the empire.**

---

## 5. The Ten Rules (compressed strategy)

Every feature, screen, and line of code must pass these rules.

1. **Be a booking platform, never a directory.** Transactions, not leads.
2. **Mandatory price transparency.** It's the market's #1 complaint and our cheapest differentiator.
3. **Exclusive leads, forever.** Make it a public promise.
4. **Escrow everything.** Trust is the product; payments are the trust.
5. **Rank by merit** (response, completion, reviews) — never by ad spend.
   Their scandal is our marketing.
6. **Three sides, one graph:** every vendor is also a buyer (of freelancers). That
   double-sided demand is the moat no incumbent can copy without breaking their
   ad-revenue model.
7. **AI copilot with boundaries:** logistics automated, emotions human — exactly how
   2026 couples say they want it.
8. **Multi-day, multi-event, multi-family by default** — built for South Asian reality,
   which also now fits Western trends.
9. **Vendor OS included** — when their CRM lives on us, they never leave.
10. **AEO from day one** — structure every profile for AI-engine discovery while
    incumbents still optimize for 2015 Google.

## 6. The Never List (monetization guardrails)

- **Never** shared-lead selling.
- **Never** 12-month lock-in contracts.
- **Never** guest-paid fees.
- **Never** rank by ad spend (labeled featured slots only, small, honest, capped).
