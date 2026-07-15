# 05 — Process & Roadmap

Phased build order, launch strategy, and the metrics that gate each phase.
Tiers come from [02-features.md](02-features.md); architecture from [03-implementation.md](03-implementation.md).

---

## Phase 0 — Foundations (before any feature)

**Goal:** the platform skeleton exists; the homepage remains byte-identical.

1. Supabase project: Postgres + Auth + Storage + `pgvector` enabled; local dev via Supabase CLI.
2. Stripe account + Connect (test mode); webhook route handler skeleton.
3. Route groups, layouts, and `src/lib/` scaffolding per 03 §2.
4. Auth flows: signup/login, role selection (customer / vendor / freelancer), onboarding shells.
5. CI: `npm run check` + `npm run build` + frozen-homepage diff guard on every PR.
6. `.env.example` with all required keys documented.

**Exit criteria:** a user can sign up, pick a role, and land on an empty dashboard; CI is green; `/` unchanged.

---

## Phase 1 — Tier 0: the booking loop

**Goal:** one real customer can pay a real (test-mode) deposit to one verified vendor for a priced package on an available date.

Build order (dependencies flow downward):

1. Vendor onboarding: profile, categories, service area → **verification doc upload** (T0.6)
2. Package builder with mandatory-pricing publish gate (T0.1)
3. Availability calendar + auto-block (T0.2)
4. Public vendor profile with JSON-LD (T0.1 + AEO)
5. Search with **date filter as the default entry point** (T0.2)
6. Inquiry (exclusive, structured) + request-to-book with 24h response clock (T0.3, T0.7)
7. Instant book + escrow deposit + milestone schedule (T0.3, T0.4)
8. Milestone release, refunds, disputes (T0.4)
9. Completion → verified-booking review (T0.5)
10. Vendor payout view with take-rate transparency (T0.8)

**Exit criteria:** full loop works end-to-end in test mode: signup → verify → publish priced package → found by date search → booked → deposit escrowed → milestone released → review posted. Homepage untouched.

---

## Phase 2 — Tier 1: the planning layer

**Goal:** couples arrive months before booking and plan everything here.

Build order:

1. Event workspace: project → ceremonies, multi-day templates (T1.1)
2. Family collaboration: invites, roles, shared checklist (T1.6)
3. Budget tracker with city + guest-count auto-estimates, wired to bookings (T1.3)
4. Guest list + RSVP (T1.4) → free event website + e-invites (T1.5)
5. Drag-and-drop table planner synced to RSVPs (T1.4)
6. AI copilot: budget, timeline, shortlists, drafting — with the human/creative boundary (T1.2)
7. Style quiz + pgvector matching (T1.7)
8. Inspiration feed with vendor-tagged real-event photos (T1.8)

**Exit criteria:** a project can be planned start-to-finish (guests, budget, website, seating) with ≥2 collaborators, and the budget reflects real escrow payments automatically.

---

## Phase 3 — Tier 2: the vendor OS

**Goal:** vendors run their whole business on wedjan.

1. Pipeline/CRM (Kanban) over inquiries and bookings (T2.1)
2. Proposal → contract (e-sign) → invoice chain (T2.1)
3. ROI/analytics dashboard: views → inquiries → bookings → revenue (T2.3)
4. AI vendor assistant: quote drafts, triage, follow-up nudges (T2.2)
5. Google/iCal calendar sync + team seats (T2.4)
6. Merit ranking live in search ordering, recomputed nightly (T2.5)

**Exit criteria:** a vendor can run a full client from inquiry to paid invoice without leaving the platform; ranking is demonstrably ad-spend-free.

---

## Phase 4 — Tier 3: the gig layer

**Goal:** the three-sided graph — vendors staff crews on-platform.

1. Freelancer profiles + skills + day rates (T3.1)
2. Shift posting with upfront pay → matching → acceptance (T3.2)
3. Escrowed gig pay with 48–72h payout + both-way ratings (T3.4)
4. Rosters/favorites (T3.5)
5. Direct freelancer booking for micro-events (T3.3)
6. Later: skills quizzes, show-rate scoring (T3.6)

**Exit criteria:** a photography vendor books a second shooter for a wedjan wedding entirely in-platform, and the freelancer is paid within 72h of the shift.

---

## Phase 5 — Tier 4: empire extensions

Priority order (revisit with real data):
1. Post-event: gallery delivery, guest photo sharing, review-at-peak, livestream (T4.2) — photography domain advantage, feeds the inspiration flywheel
2. Cross-border destination mode: multi-currency, Devanagari/Nepali (T4.6)
3. Booking-protection insurance attach (T4.3)
4. Sustainability/accessibility filters (T4.4), off-peak pricing tools (T4.5)
5. Registry/gifting commerce (T4.7)

---

## Launch wedge (go-to-market)

- **Market:** weddings + private events in one city corridor — **Kathmandu → Melbourne
  diaspora**, a genuinely unserved cross-border lane.
- **Supply seed:** photographers/videographers first — the vertical with 13 years of personal
  supply knowledge, quality signals, and pricing. Photography-first supply also generates the
  real-event photo inventory that powers the PartySlate-style discovery loop (T1.8).
- **Expansion rule:** add a new category **only after liquidity** in the current ones.

## Liquidity metrics (the numbers that gate expansion)

| Metric | Target |
|---|---|
| Inquiries answered < 24h | **≥ 70%** |
| Inquiry → booking conversion | **≥ 25%** |
| Vendors with ≥1 priced published package | 100% (enforced) |
| Verified vendors among published | 100% (enforced) |
| Dispute rate on escrowed bookings | < 2% |
| Review rate on completed bookings | ≥ 60% |

## Monetization rollout

| Phase | Revenue on |
|---|---|
| Phase 1 | GMV take rate 8–12% vendor-side, 0% customer-side |
| Phase 3 | + Vendor OS subscription ($25–35/mo) for CRM power features |
| Phase 4 | + Gig markup (< 20%) |
| Phase 5 | + Insurance attach, payment spread, labeled featured slots (small, capped) |

**Never:** shared-lead selling, 12-month lock-ins, guest-paid fees.

## Operating cadence

- Weekly: review liquidity metrics; the response-time SLA is watched from the first vendor.
- Per phase: exit criteria reviewed against this doc before the next phase starts; if reality
  diverges, update the doc first, then build.
- Every release: frozen-homepage guard, quality gates from [04-instructions.md](04-instructions.md) §6.
