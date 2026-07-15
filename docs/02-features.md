# 02 — Feature Specification (Customer → Vendor → Freelancer)

Features are organized in tiers. **Tiers are the build order.** A tier ships only when the
tier before it is live and stable. Each feature lists its scope so an implementer can start
without re-deriving the product intent.

---

## Tier 0 — The booking loop (MVP, ship this first)

The transaction engine. Nothing else matters until money can move safely from a customer to
a verified vendor for a real, priced, date-checked package.

### T0.1 Vendor profiles with mandatory package pricing
- **No listing goes live without at least one priced package.** Enforced at publish time, not by policy text.
- A package = name, description, what's included, base price, price unit (flat / per-guest / per-hour), duration, add-ons.
- Public profile shows: packages with real prices, portfolio, service area, categories, verification badges, response-rate stats.
- Machine-readable from day one (JSON-LD `LocalBusiness`/`Product`/`Offer`, FAQ schema) — see Rule 10.

### T0.2 Real-time availability calendar per vendor
- Vendor marks dates available / booked / blocked; bookings auto-block dates.
- Customers can **filter search results by their event date** — an unavailable vendor never appears.
- Calendar sync (Google/iCal) arrives in Tier 2; Tier 0 is manual + auto-block on booking.

### T0.3 Request-to-book + instant-book flows
- **Instant book:** date available + package chosen + deposit paid → booking confirmed.
- **Request-to-book:** vendor has 24h to accept/decline; response clock is visible to both sides and feeds ranking.
- Every inquiry carries structured context: event date, venue/city, guest count, budget band, package of interest. No blank "are you free?" messages.

### T0.4 Escrow payments
- Deposit held by the platform, milestone release schedule per booking (e.g. deposit / pre-event / post-event).
- Platform-mediated refunds and dispute flow.
- Payouts to vendors on milestone release.
- Implementation: Stripe Connect (destination charges + manual payouts) — see 03-implementation.

### T0.5 Verified-booking-only reviews
- A review can only be written from a completed booking. No booking, no review — in both directions (customer↔vendor).
- Review request fires at the emotional peak (right after event completion / gallery delivery).

### T0.6 Vendor verification
- Upload business registration document + insurance certificate → human/AI-assisted check → **Verified badge**.
- Badge states: Unverified (cannot publish), Documents pending, Verified, Verified + Insured.

### T0.7 Exclusive-lead guarantee
- An inquiry goes to **one vendor**. Product-enforced, publicly promised on the marketing site.
- If a customer wants three quotes, they send three separate inquiries by explicit choice.

### T0.8 Take rate
- 8–12% vendor-side commission on GMV. **0% customer-side fees** (Tagvenue demand playbook).
- No subscription required to list. ROI visible in the vendor dashboard from the first booking.

---

## Tier 1 — The planning layer (demand magnet)

Why couples come to wedjan *before* they're ready to book, and why they stay.

### T1.1 Event workspace (multi-event / multi-day)
- An **Event Project** contains multiple **Ceremonies/Sub-events** (mehndi, sangeet, ceremony, reception, day-after brunch), each with its own date, venue, guest list subset, and vendor bookings.
- Templates: South Asian multi-day sets, Western single-day + welcome party, custom.

### T1.2 AI planning copilot
- Budget generation from **city + guest count** (local cost model), timeline generation, vendor shortlists, etiquette/drafting help.
- Built on the Claude API. **Boundary: logistics automated, creative/emotional decisions stay human** — the copilot proposes, never decides, on aesthetics and people matters.

### T1.3 Budget tracker wired to real bookings
- Budget lines link to actual bookings and escrow payments — paid/due/remaining always true, never a separate spreadsheet.
- Auto-estimates per category from city + guest count until a real booking replaces the estimate.

### T1.4 Guest list, RSVP, and table planner
- Guest list with dietary needs, sides (bride/groom/family), per-ceremony invitations.
- RSVP collection via the free event website (T1.5).
- **Drag-and-drop room-visual table planner auto-synced to RSVPs** — a guest who declines disappears from the layout; no double data entry.

### T1.5 Free event websites + e-invites
- Per-project event website: schedule, venues/maps, RSVP, registry links, livestream embed slot.
- E-invite card/video maker (WedMeGood-style — huge in South Asia, cheap to build).

### T1.6 Family collaboration
- Multi-user workspace with roles (owner, partner, parent, planner, helper). Plan for **6 users, not 2**.
- Shared checklist, activity wall, per-role permissions (e.g. parents see budget or not — owner's choice).

### T1.7 Style-quiz onboarding → matched suppliers
- Visual quiz (pick aesthetics, palettes, moods) → embedding via pgvector → similarity match against vendor portfolio embeddings.
- Output: a personalized shortlist per category from minute one.

### T1.8 Inspiration feed of real events
- Every photo in the feed comes from a **real event** on the platform and **tags every vendor who made it** (PartySlate mechanic).
- This is the photographer-network cold-start advantage: photographer supply generates the photo inventory that powers discovery.
- Search photos by garment/decor detail (visual search — embeddings over image crops).

### Tier 1 privacy features (from day one of Tier 1)
- **Masked contact:** couples contact vendors without exposing their phone number; all comms in-platform until the couple chooses otherwise.

---

## Tier 2 — The vendor OS (lock-in)

When the vendor's whole business runs on wedjan, they never leave.

### T2.1 Inquiry pipeline / CRM
- Kanban pipeline: New → Replied → Quoted → Booked → Completed (+ Lost with reason).
- Proposal builder, contract builder with e-sign, invoice builder — all in-platform.

### T2.2 AI vendor assistant
- Quote drafts from package + inquiry context, auto-follow-ups on quiet threads, inquiry triage (fit score vs. vendor's typical bookings).
- Fights ghosting in both directions.

### T2.3 Analytics / ROI dashboard
- Views → inquiries → bookings → revenue. The dashboard The Knot refuses to give.
- Commission paid vs. revenue earned = ROI, always visible.

### T2.4 Calendar sync + team seats
- Google/iCal two-way sync; multiple team members with roles on one vendor account.

### T2.5 Merit-only ranking algorithm
- Rank = f(response time, completion rate, review quality, verification depth). **Never ad spend.**
- If featured slots are ever sold: clearly labeled, small, capped (see Never List).

---

## Tier 3 — The gig layer (the moat nobody has)

Every vendor is also a buyer. This is the third side no incumbent can copy.

### T3.1 Freelancer profiles
- Skills, portfolio, certifications, day rates, verified work history (work done on-platform is auto-verified).

### T3.2 Crew shifts
- Vendors post shifts (second shooter, servers, decorators' assistants, AV techs) with **pay shown upfront**.
- Matched freelancers accept; both sides rate after the shift.

### T3.3 Direct freelancer booking
- Customers can book freelancers directly for micro-events (a single photographer for a small ceremony).

### T3.4 Gig payments
- Escrowed gig pay, **48–72h payout** after shift completion, both-way ratings.

### T3.5 Rosters / favorites
- Vendors keep favorite crews; repeat booking in two taps. Schedule autonomy for freelancers.

### T3.6 Later depth (Instawork model)
- Skills quizzes, attire approval, show-rate scoring → reliability as the product.

---

## Tier 4 — Empire extensions

- **T4.1 AEO / AI-search-ready vendor pages:** schema, structured FAQs, entity pages — free distribution as ChatGPT/Perplexity discovery grows.
- **T4.2 Livestream + guest photo-sharing** per event; post-event gallery delivery (photography domain advantage). Fills the post-event void: review capture at emotional peak, anniversary re-engagement, vendor rebooking.
- **T4.3 Booking-protection guarantee** (insurance partner) as a paid trust product.
- **T4.4 Sustainability + accessibility filters** (eco-certified tags; 72% of 2026 couples weigh environmental impact).
- **T4.5 Dynamic off-peak pricing tools** for venues (surface 30–40% weekday discounts).
- **T4.6 Cross-border destination-event mode:** multi-currency, multi-language, Devanagari-ready (reuse the WedMomentsNepal bilingual system).
- **T4.7 Registry / gifting + bridal commerce** (Zola/WedMeGood revenue layers).

---

## Monetization stack (from proven models)

1. **GMV take rate on bookings** (core) — 8–12% vendor-side.
2. **Gig-layer markup on crew shifts** (Instawork model; keep it under 20%).
3. **Vendor OS subscription** for CRM power features ($25–35/mo — Wedy Pro pricing proof). Marketplace + basic tools stay free.
4. **Payment processing spread.**
5. **Booking-protection insurance attach.**
6. **Labeled featured slots** (small, honest, capped).
7. **Never:** shared-lead selling, 12-month lock-ins, guest-paid fees.
