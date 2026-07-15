# 06 — wedjan Master Build Playbook
## All prompts & instructions to build the full website + mobile app, structured and ready to run

This is the **operating manual** for executing the 15-phase build. The canonical full prompt
text lives in [15-phase-build-prompts.md](15-phase-build-prompts.md); this playbook structures
it: the wedjan-branded master context you paste every session, the per-phase runbook
(goal → dependencies → deliverables → acceptance gate → which prompt to paste), the wedjan
overrides that adapt the generic pack to this repo, and the tracking system.

**Brand name is final: every `[PLATFORM_NAME]` in the prompt pack = `wedjan`.**

---

## 1. Golden rules (read before every phase)

### The five Product Laws (never violate — they are the brand)
1. **No vendor listing without at least one published package with a real price.**
2. **Leads are EXCLUSIVE** — one inquiry goes to exactly one vendor. Never shared/resold.
3. **All payments escrowed** through the platform; release after completion + dispute window.
4. **Organic ranking = merit only** (response, completion, reviews). Paid placement, if ever,
   is separate, labeled, and capped. Customers pay 0% booking fees.
5. **Verified-booking-only reviews.** Both sides rate each other.

### The wedjan Homepage Rule (repo-specific, overrides the pack)
The existing wedjan homepage is the approved design and **must not change visually**:
- `src/app/page.tsx`, `src/components/home/**`, `src/data/home.ts`, `src/app/globals.css`,
  `public/images/wedjan/**`
- Phase 1's "marketing homepage placeholder" = **port the existing homepage verbatim** into
  `apps/web` when the monorepo is created (move files, keep markup/styles identical).
- Phase 3's "Homepage v1 (replace placeholder)" = **wire the existing homepage's search bar,
  category tiles, and CTAs to the real routes** — no visual redesign. New blocks the pack
  requires (trust strip, "How it works", city links footer) are added **below/around** the
  existing sections in the same visual language, never replacing them.

### Phase discipline
- **One phase = one focused build cycle.** Run phases in order; each prompt assumes all
  previous phases are complete.
- **Never start the next phase until every acceptance criterion of the current phase passes.**
- After each phase: commit, tag `phase-N-complete`, update
  [PROGRESS.md](PROGRESS.md) and [DECISIONS.md](DECISIONS.md).
- **Revenue milestone:** Phases 1–7 = a launchable, money-making marketplace. Launch after
  Phase 7. Phases 8–15 are built while live (recommended strategy — see §6).

---

## 2. How to run a phase (session workflow)

Every build session follows the same 7 steps:

1. **Branch:** `git checkout -b feat/phase-N-<short-name>` off `main`.
2. **Assemble the prompt** (in this order, as one message):
   1. §3 **Master Context Block** (below) — paste in full.
   2. §4 **wedjan Global Overrides** — paste in full.
   3. The phase's **PROMPT N** block from [15-phase-build-prompts.md](15-phase-build-prompts.md)
      (replace `[PLATFORM_NAME]` → `wedjan` if any remain).
   4. Any **phase-specific overrides** from §5 of this playbook.
3. **Build** until every item in the phase's ACCEPTANCE CRITERIA passes — run the checks,
   don't assume.
4. **Verify the Homepage Rule:** homepage renders identically (visual/snapshot check).
5. **Update living docs:** `docs/PROGRESS.md` (what exists, what's stubbed) and
   `docs/DECISIONS.md` (architecture decisions + reasons).
6. **Merge & tag:** PR → `main`, tag `phase-N-complete`.
7. **Log the gate:** tick the phase row in the tracker (§7).

---

## 3. MASTER CONTEXT BLOCK — wedjan edition (paste at the top of every phase prompt)

```
You are the lead engineer building wedjan, a three-sided event & wedding
marketplace: CUSTOMERS plan events and book vendors; VENDORS sell priced packages
and hire crew; FREELANCERS pick up gig shifts. Business-ready quality: production
code, not prototypes.

STACK (non-negotiable):
- Monorepo: pnpm + Turborepo. apps/web (Next.js App Router, TypeScript strict,
  Tailwind, shadcn/ui), apps/mobile (Expo SDK 52+, expo-router, TypeScript),
  apps/api (Spring Boot 3.4, Java 21, Gradle), packages/shared (API types
  generated from OpenAPI), packages/ui-tokens (design tokens shared web/mobile).
- Data: PostgreSQL 16 + pgvector, Flyway migrations, Redis (cache/queues/locks),
  S3-compatible object storage (Cloudflare R2) behind a MediaService.
- Payments: Stripe Connect (Express accounts), platform-held funds with delayed
  transfers for escrow-style protection. Stripe Billing for SaaS subscriptions.
- AI: provider-agnostic AiGateway (Claude primary via API), pgvector for matching.
- Realtime: Spring WebSocket (STOMP) + polling fallback. Email: Resend. Push: Expo.
- Deploy: web on Vercel, API+Postgres+Redis on Railway (Docker), mobile via EAS.

GLOBAL CONVENTIONS (enforce in every phase):
- API-first: write/extend openapi.yaml FIRST, generate TS client into packages/shared.
- REST /api/v1, cursor pagination (cursor, limit<=50), standard error envelope
  {error:{code,message,fieldErrors[],traceId}}, correlation IDs on all requests.
- Money: integer minor units + ISO currency code (amount_cents, currency). Supported:
  AUD, USD, GBP, NPR. Never floats. All timestamps UTC timestamptz; convert at edges.
- IDs: UUIDv7. Soft delete (deleted_at) + audit columns (created_at, updated_at,
  created_by) on every table. Optimistic locking (version) on mutable aggregates.
- AuthZ: single account, multiple roles (CUSTOMER, VENDOR, FREELANCER, ADMIN).
  Role checked per-endpoint via method security. Never trust client role claims.
- Validation server-side always; zod on clients for UX only.
- Idempotency-Key header required on all POSTs that move money or create bookings.
- Feature flags via a config table + typed accessor (no env-only flags).
- Testing bar per phase: unit tests on domain logic, integration tests with
  Testcontainers (Postgres+Redis), at least one happy-path E2E per new flow.
- Security baseline: Argon2id password hashing, JWT access (15m) + rotating refresh
  (30d) in httpOnly cookies (web) / SecureStore (mobile), rate limits on auth +
  search + messaging, input sanitization, uploaded-file type/size validation +
  image re-encode, no PII in logs.
- UX baseline: mobile-first responsive web, skeleton loaders, empty states, error
  states with retry, optimistic UI where safe, WCAG AA color contrast.
- Update docs/PROGRESS.md and docs/DECISIONS.md before finishing.

DOMAIN LANGUAGE (use these exact names in code):
Account, Role, VendorProfile, FreelancerProfile, Category, ServiceArea, Package,
AddOn, MediaAsset, VerificationDocument, Event (customer project), SubEvent,
Lead, Conversation, Quote, Booking, PaymentPlan, LedgerEntry, Review, MeritScore,
Shift, ShiftAssignment, Contract, Invoice.

PRODUCT LAWS (never violate, they are the brand):
1. No vendor listing without at least one published package with a real price.
2. Leads are EXCLUSIVE — one inquiry goes to exactly one vendor. Never shared/resold.
3. All payments escrowed through the platform; release after completion + dispute window.
4. Organic ranking = merit only (response, completion, reviews). Paid placement, if
   ever, is separate, labeled, and capped. Customers pay 0% booking fees.
5. Verified-booking-only reviews. Both sides rate each other.
```

---

## 4. wedjan GLOBAL OVERRIDES (paste right after the Master Context, every session)

```
WEDJAN REPO OVERRIDES (apply on top of everything above):

1. HOMEPAGE IS FROZEN. The repo already contains the approved wedjan homepage
   (src/app/page.tsx, src/components/home/**, src/data/home.ts, src/app/globals.css,
   public/images/wedjan/**). When creating apps/web, PORT these files verbatim as
   the marketing homepage. Never redesign, restyle, or rewrite them. New homepage
   functionality (working search, real links) wires into the existing markup.
   Every phase must end with the homepage visually identical.

2. NEXT.JS VERSION: the repo runs Next.js 16 (package.json). Use the repo's
   installed Next.js/React versions for apps/web, not the pack's "Next.js 15".

3. DESIGN TOKENS: derive packages/ui-tokens from the existing homepage design
   system in src/app/globals.css (colors, fonts, radii, spacing) so all new
   surfaces match the approved brand. Include a Devanagari-capable font stack.

4. BRAND: platform name is "wedjan" everywhere — code, UI copy, emails,
   store listings, JSON-LD, llms.txt.

5. LAUNCH CORRIDOR: Kathmandu (NPR, Asia/Kathmandu +05:45) and Melbourne
   (AUD, Australia/Melbourne). Seed data, city landing pages, currency and
   timezone tests must always cover both.

6. SUPPLY WEDGE: photography/videography categories are first-class — seed
   them first and use them in all demo/fixture data.

7. STRATEGY DOCS: docs/01-blueprint.md and docs/02-features.md define product
   intent; if a phase prompt is ambiguous, they win on WHAT. This prompt pack
   wins on HOW. Log any conflict in docs/DECISIONS.md.
```

> **Note on earlier docs:** [03-implementation.md](03-implementation.md) described a lighter
> single-app architecture (Next.js + Supabase). The 15-phase pack supersedes it for the full
> build (it adds the mobile app, Java API, admin app, and gig layer at production depth).
> 03 remains useful for its data-model reasoning and mechanics (escrow, exclusivity,
> merit, AEO) — the concepts carry over 1:1.

---

## 5. THE 15 PHASES — structured runbook

Phase index (what each phase builds and what it unlocks):

| # | Phase | Est. | Depends on | Unlocks | Prompt source |
|---|---|---|---|---|---|
| 1 | Foundation: monorepo, auth, schema, tokens, CI/CD | 5–7d | — | everything | PROMPT 1 |
| 2 | Vendor onboarding, verification, priced packages | 6–8d | P1 | P3, P4 | PROMPT 2 |
| 3 | Search, discovery, inspiration feed, AEO | 6–8d | P2 | P7, P10 | PROMPT 3 |
| 4 | Availability + booking engine | 7–9d | P2 | P5, P6, P12 | PROMPT 4 |
| 5 | Payments, escrow, payouts (Stripe Connect) | 8–10d | P4 | P7, P11, P12 | PROMPT 5 |
| 6 | Messaging, exclusive leads, quotes, anti-ghosting | 6–8d | P4 | P7, P10, P11 | PROMPT 6 |
| 7 | Reviews, trust, merit ranking — **LAUNCH GATE** | 5–7d | P3+P5+P6 | LAUNCH | PROMPT 7 |
| 8 | Event planning workspace | 7–9d | P1 (+P5 wiring) | P9, P10 | PROMPT 8 |
| 9 | Event websites, public RSVP, e-invite studio | 6–8d | P8 | — | PROMPT 9 |
| 10 | AI layer: copilot, vendor assistant, style match | 7–9d | P3, P6, P8 | — | PROMPT 10 |
| 11 | Vendor OS: CRM, contracts, e-sign, subscriptions | 7–9d | P5, P6, P7 | — | PROMPT 11 |
| 12 | Freelancer/gig layer: shifts, crew, gig pay | 8–10d | P4, P5 | — | PROMPT 12 |
| 13 | Mobile apps: native completion, store readiness | 7–9d | P1–P12 | stores | PROMPT 13 |
| 14 | Admin panel, ops, moderation, fraud | 7–9d | P13 | P15 | PROMPT 14 |
| 15 | Hardening, perf, i18n, SEO/AEO final, launch ops | 7–10d | P14 | GO LIVE | PROMPT 15 |

---

### PHASE 1 — Foundation: monorepo, auth, core schema, design system, CI/CD
- **Goal:** running skeleton of all three apps with real multi-role auth. **Est:** 5–7 days.
- **Build:**
  - *Monorepo:* pnpm + Turborepo; `apps/api` (Spring Boot + Docker Compose: Postgres 16 w/ pgvector, Redis, Mailpit), `apps/web` (Next.js, Tailwind, shadcn/ui, TanStack Query), `apps/mobile` (Expo, expo-router, NativeWind), `packages/shared` (openapi.yaml + codegen), `packages/ui-tokens`.
  - *Schema (Flyway V1):* accounts, account_roles, refresh_tokens, email_verifications, profiles, media_assets, app_config, audit_log.
  - *Auth API:* signup + OTP verify, login/refresh/logout(-all) with rotation + reuse detection, password reset, add-role, `/me`; media presign/complete pipeline (R2).
  - *Web shell:* **existing wedjan homepage ported verbatim**; signup (role picker), login, verify, forgot-password; role-aware authenticated layout; role switcher; settings + sessions.
  - *Mobile shell:* auth + app route groups, role-aware tabs, secure token storage, onboarding carousel.
  - *CI/CD:* GitHub Actions PR + deploy pipelines, lint/format hooks, seed script, one-command local setup.
- **wedjan overrides:** homepage port (Override 1); tokens from existing globals.css (Override 3); Next 16 (Override 2).
- **Gate (all must pass):** `pnpm dev` boots all apps against docker compose · full auth cycle works on web AND mobile · one account holds CUSTOMER+VENDOR and switches UI · OpenAPI covers every endpoint, generated client compiles, zero `any` · integration tests green in CI, deploys succeed · rate limiting proven by test.
- **Done:** tag `phase-1-complete`.

### PHASE 2 — Vendor onboarding, verification & mandatory priced packages
- **Goal:** vendors build a complete, verified, transparently-priced listing (Law #1). **Est:** 6–8 days.
- **Build:**
  - *Schema (V2):* categories (seed 20-category 2-level taxonomy), vendor_profiles (status machine DRAFT→VERIFIED), vendor_categories, service_areas, packages (price_cents required, pricing_model, deposit_pct, cancellation_policy, booking_mode), add_ons, vendor_media (≥5 gallery + cover gate), verification_documents, vendor_badges, vendor_faqs.
  - *API:* vendor self-service CRUD with step-based saves + submit validation gates; public profile endpoints (VERIFIED only); minimal admin approve/reject that auto-grants badges and flips to VERIFIED.
  - *Web:* resumable 7-step onboarding wizard with autosave + live package preview; vendor dashboard with listing-strength meter; public profile `/v/{slug}` with **prices always visible**, JSON-LD (LocalBusiness/Service/Offer), auto OG image.
  - *Mobile:* onboarding parity with native pickers/camera.
- **Key edge cases:** slug immutable after VERIFIED; package price edits version + snapshot; sensitive edits re-review without unlisting; currency locked once published.
- **Gate:** full onboard→approve→live on web and mobile · submit blocked with precise per-step errors · **"price on request" impossible anywhere** · strength meter live · JSON-LD passes Rich Results · 20 seeded vendors across ≥8 categories in Kathmandu + Melbourne.
- **Done:** tag `phase-2-complete`.

### PHASE 3 — Search, discovery, inspiration feed & AEO
- **Goal:** customers find the right vendor fast; platform is discoverable by Google and AI engines. **Est:** 6–8 days.
- **Build:**
  - *Search backend:* Postgres FTS + pg_trgm fuzzy + geo filtering; `/search/vendors` with full filter set, facet counts, cursor pagination; empty-result relaxation suggestions; search_events analytics.
  - *Inspiration feed:* showcases + showcase_media + vendor tags with accept/decline confirmation (PartySlate mechanic); `/inspiration` masonry feed; "Book this team" credits panel.
  - *Favorites/shortlists/compare:* up to 4 packages side-by-side with inclusion diffing.
  - *SEO/AEO programmatic pages:* `/[country]/[city]/[category]` ISR landing pages with live stats + FAQ from templates; ItemList/FAQPage/BreadcrumbList JSON-LD; sitemaps, robots.txt, **llms.txt**.
  - *Homepage wiring:* hero search + category tiles + CTAs go live **inside the existing frozen design** (Homepage Rule).
- **Gate:** search p95 < 300ms @ 10k seeded vendors · filters + facets + cursor correct · showcase tagging E2E with emails · 100% programmatic pages pass Rich Results, sitemap serves · Lighthouse SEO ≥ 95 · mobile parity.
- **Done:** tag `phase-3-complete`.

### PHASE 4 — Availability & the booking engine
- **Goal:** real-time availability + bulletproof booking state machine; date search goes live. **Est:** 7–9 days.
- **Build:**
  - *Availability:* DATE/SLOT modes, weekly rules + exceptions, hourly ICS pull + secret-token ICS export, derived `get_availability` function.
  - *Booking aggregate (V4):* human-readable codes, full price snapshots, 12-state machine (DRAFT→…→COMPLETED/DISPUTED), booking_events append-only history.
  - *Rules:* 15-min Redis checkout holds; 24h vendor SLA on requests + 24h customer payment window; partial-unique/exclusion constraints against double-booking; cancellation policy engine (FLEXIBLE/MODERATE/STRICT refund tables) producing RefundCalculation for Phase 5; auto IN_PROGRESS/COMPLETED transitions; 48h dispute window; single mutual reschedule ≥14d out.
  - *UI:* vendor calendar + booking inbox with SLA countdowns; customer booking widget with live availability coloring and refund preview before cancel; mobile parity.
- **Gate:** 20 parallel checkouts for one date → exactly 1 succeeds · every legal transition tested, illegal rejected · refund preview matches policy table (property tests) · date search returns only truly bookable vendors · ICS import/export works · full request→accept→confirm→complete cycle web + mobile.
- **Done:** tag `phase-4-complete`.

### PHASE 5 — Payments, escrow & payouts (Stripe Connect)
- **Goal:** money moves safely — deposits, milestones, escrow release, refunds, ledger (Laws #3, #4). **Est:** 8–10 days.
- **Build:**
  - *Connect:* Express onboarding, account status sync; instant-book gated on charges_enabled.
  - *Payment plans:* deposit at confirmation + balance at T-14d (single payment if closer); off-session auto-charge with D0/D2/D4 retries; separate charges & transfers — **funds sit on platform balance (escrow), transfer only at RELEASE**; take rate from config (default 12%) deducted vendor-side; customer pays 0%.
  - *Escrow lifecycle:* release at COMPLETED + 48h no-dispute; dispute freezes release; admin split-resolution API; refunds execute Phase 4 RefundCalculation; vendor-fault = 100% refund.
  - *Ledger:* double-entry, append-only, every txn balances to zero; nightly Stripe reconciliation with alerts.
  - *Reliability:* webhook signature verify + dedupe + DLQ + replay endpoint.
  - *Invoices/receipts:* branded PDFs, vendor-only fee breakdown, tax-field hooks.
  - *UI:* customer checkout (Elements/PaymentSheet), schedule timeline, receipts; vendor earnings dashboard with release ETAs.
- **Gate:** test-mode E2E deposit→balance→completion→48h→transfer with balanced ledger · every refund scenario matches preview to the cent · chaos test (kill API mid-payment) → clean replay · vendor sees exact fee math, customer never sees a fee line · 3DS off-session retries work · dispute freeze + split-resolution verified.
- **Done:** tag `phase-5-complete`.

### PHASE 6 — Messaging, exclusive leads, quotes & anti-ghosting
- **Goal:** every conversation converts — exclusive leads (Law #2), realtime chat, structured quotes, SLA machinery. **Est:** 6–8 days.
- **Build:**
  - *Schema:* leads (one lead → exactly ONE vendor, unique constraint; status + close reasons + sla_due_at), conversations, messages (TEXT/ATTACHMENT/QUOTE/SYSTEM/QUICK_ACTION), quotes (line items, validity, accept→booking draft), saved_replies.
  - *Realtime:* STOMP WebSocket + 15s polling fallback, read receipts, debounced email notifications, reply-by-email round-trip.
  - *Privacy & safety:* contact-detail shielding until CONFIRMED (blur + tooltip), disintermediation telemetry, block/report → moderation queue.
  - *SLA/anti-ghosting:* 24h vendor SLA with 4h/12h/20h reminders, public "Responds in ~X", SLA breach → EXPIRED + 3 alternative vendors; customer-side 72h quick-action nudges ("Still deciding" / "Over budget" / "Booked someone else"); weekly vendor digest.
  - *UI:* structured first-inquiry composer (quality-gated), customer inbox + quote compare, vendor unified inbox with SLA badges + quote builder drawer; mobile chat is the highest-polish surface.
- **Gate:** lead→chat→quote→accept→booking draft flawless web+mobile · **exclusivity provable: no API path shares a lead** · shielding unlocks exactly at CONFIRMED · SLA expiry fires alternatives; metrics compute correctly · WebSocket drop → seamless polling, zero message loss · reply-by-email lands in thread ≤60s.
- **Done:** tag `phase-6-complete`.

### PHASE 7 — Reviews, trust system & merit ranking → ★ LAUNCH GATE
- **Goal:** verified reviews, both-way ratings, badges, merit algorithm replaces all placeholders. Platform is launch-ready. **Est:** 5–7 days.
- **Build:**
  - *Reviews (Law #5):* booking-COMPLETED-only, double-blind (publish when both submit or 14d), criteria ratings both directions, vendor replies, V→C reviews private → customer reliability tiers (NEW/GOOD/EXCELLENT, never public shaming).
  - *Aggregates:* Bayesian vendor rating (m=8, global mean prior), histograms, criteria breakdowns.
  - *Badges:* TOP_RATED and RISING_STAR grant jobs with expiry; public `/trust` explainer page with the "we never sell ranking" pledge.
  - *Merit:* `0.30 bayesian + 0.20 response_rate + 0.15 conversion + 0.15 completion_reliability + 0.10 profile_quality + 0.10 recency` — cold-start fairness, nightly batch + event nudges, components stored for Phase 11's "why you rank here". Anti-gaming: velocity anomalies, self-booking detection, merit clamp while flagged.
  - *Launch sweep:* remove every placeholder from P2–P6; homepage "featured" = top merit per city; golden-path Playwright E2E becomes the permanent regression cornerstone.
- **Gate:** review without COMPLETED booking impossible (fuzz test) · double-blind exact · merit reacts to new 5★ within 60s · /trust live, components stored · golden-path E2E green · LAUNCH CHECKLIST doc generated.
- **Done:** tag `phase-7-complete`. **→ LAUNCH the Kathmandu–Melbourne corridor here (recommended), then build P8–P15 live.**

### PHASE 8 — Event planning workspace (multi-event, budget-wired, collaborative)
- **Goal:** the demand magnet — customers run their whole event on wedjan; every tool funnels to bookings. **Est:** 7–9 days.
- **Build:**
  - *Schema (V8):* events, sub_events (South Asian template: Mehndi+Sangeet+Baraat+Ceremony+Reception; Western: Rehearsal+Ceremony+Reception), event_members (6-collaborator roles OWNER/PARTNER/PLANNER/FAMILY/VIEWER with server-side permission checks), checklist templates with month-offset due dates, budget categories/lines, guests + groups + per-sub-event invites, rsvps, table_plans/tables/seat_assignments, activity_feed.
  - *Budget engine:* estimates from INTERNAL median package prices per city ("based on 42 real Melbourne packages"); **booking CONFIRMED → auto-fills budget actuals + completes matching checklist item**; overspend alerts with one-tap rebalance; bookings ask "Attach to which event?".
  - *UI:* events dashboard with countdown + progress ring; tabs Overview/Checklist/Budget/Guests/Seating/Vendors; CSV guest import with dedupe; RSVP matrix; drag-and-drop seating canvas synced to RSVPs with PDF export; invite-by-email collaboration.
  - *Mobile:* parity except seating = view-only v1 (log in DECISIONS).
- **Gate:** South Asian template creates 5 sub-events with correct checklist spans · confirmed booking auto-fills actuals + completes task · 500-guest import + grids <2s interactions · seating synced to RSVP flips, clean PDF · PLANNER permissions exact · activity feed captures every mutation.
- **Done:** tag `phase-8-complete`.

### PHASE 9 — Event websites, public RSVP & e-invite studio
- **Goal:** the viral guest-facing surface — beautiful event sites + bilingual e-invites, tasteful wedjan branding on every touchpoint. **Est:** 6–8 days.
- **Build:**
  - *Site builder:* 10 token-based templates (incl. Devanagari-first "Temple Dusk"), section system (HERO/STORY/SCHEDULE auto from sub_events/TRAVEL/GALLERY/REGISTRY/RSVP/FAQ), live preview, slug + password + QR publish, edge-cached public render with OG image.
  - *Public RSVP:* fuzzy guest lookup with household responses, per-invite sub-event responses, dietary + plus-ones, unknown-guest approval queue (anti-gatecrash), edit window until rsvp_lock, .ics confirmations, Turnstile + rate limits.
  - *E-invite studio:* 12 card templates with dual-script Devanagari+Latin text layers, server render → PNG (feed + story) + print PDF (A5 300dpi bleed); video invites (3 ffmpeg slideshow templates, ≤30s, 5 licensed tracks); WhatsApp/Instagram share (critical for the NP/AU-diaspora corridor).
  - *Guest messaging:* audience-filtered email blasts with merge fields, throttled queue, per-guest logs, bounce handling, RSVP-nudge presets.
- **Gate:** publish site <60s, Lighthouse ≥90 · RSVP round-trip updates Phase 8 grid + seating instantly · pixel-perfect Devanagari (visual snapshots) · video renders ≤90s p95, plays on iOS/Android/WhatsApp · unknown-guest + captcha flows work · 500-email blast, per-guest logs, zero dupes.
- **Done:** tag `phase-9-complete`.

### PHASE 10 — AI layer: customer copilot, vendor assistant & style matching
- **Goal:** the intelligence moat — grounded AI that plans, matches, drafts. **AI never auto-books, never auto-sends, never invents vendors.** **Est:** 7–9 days.
- **Build:**
  - *AiGateway:* provider-agnostic (Claude primary), versioned prompt_templates, per-account quotas, usage/cost logging, PII scrubbing, 90d retention, prompt-injection defenses (docs/ai-safety.md), CI evals harness.
  - *Customer copilot:* streaming chat in workspace + search; server-executed tools — generate_budget, generate_checklist, suggest_vendors (search+merit+style blend with reason strings), draft_message (insert-only), answer_planning_question (grounded in versioned knowledge pack). **Every mutation requires explicit APPLY confirmation.** Aesthetic questions → 2–3 option framings with tradeoffs, template-enforced.
  - *Style matching:* pgvector embeddings (vendor profiles, showcase media, customer quiz); 12-step image-pick quiz across style axes; blended match `0.5 style + 0.35 merit + 0.15 price_fit`; explainable "Matched to your style" badges with WHY popover.
  - *Vendor assistant:* summarize_lead, draft_quote (opens builder prefilled), draft_followup (tone options), draft_review_reply — all insert-only with visible "AI draft" chip; weekly AI digest.
  - *Degradation:* provider outage → graceful "AI is resting"; renderer refuses vendor names not present in tool JSON (hallucination guard).
- **Gate:** copilot builds budget+checklist+shortlist with confirmations, zero unconfirmed writes · suggest_vendors returns only DB-real vendors (fuzz eval) · quiz measurably reorders results · nothing auto-sends (audit test) · quotas + cost logging enforce · kill AI key in staging → platform fully usable.
- **Done:** tag `phase-10-complete`.

### PHASE 11 — Vendor OS: CRM, contracts, e-sign, analytics & subscriptions
- **Goal:** vendors run their whole business on wedjan — lock-in via love. **Est:** 7–9 days.
- **Build:**
  - *Pipeline CRM:* deals auto-created from leads, auto-advance on quote/booking/completion, Kanban + tasks + notes, guarded manual drag.
  - *Contracts & e-sign:* merge-field templates (3 plain-language platform defaults), immutable SENT contracts (VOID + reissue), dual signatures with hash-frozen PDF snapshots.
  - *Custom invoices:* off-platform clients allowed (adoption wedge) via Stripe Checkout — clearly labeled "Not covered by wedjan booking protection".
  - *Automations (PRO):* trigger→action rules with vendor-timezone quiet hours (21:00–08:00), loop protection, run logs.
  - *Analytics + transparency panel:* funnel, revenue split escrowed/released, response trend vs city median, package performance; **"Why you rank here"** renders Phase 7 merit components vs city p50/p90 with plain-language tips — design it as a screenshot-marketing asset.
  - *Team & calendar:* roles with permission matrix, Google Calendar 2-way OAuth sync.
  - *Subscriptions:* FREE vs PRO (USD 29-eq; AUD 45 / GBP 24 / NPR 3,900) on Stripe Billing, 14-day no-card trial, downgrade = read-only never delete.
- **Gate:** lead→deal→quote→contract signed→booking→completed reflects everywhere without manual sync · countersigned PDF hash-verifies, tamper fails loudly · automations respect quiet hours + loop caps (simulated clocks) · PRO gates exact both directions · transparency panel reconciles with merit rows · off-platform invoice pays with correct labeling.
- **Done:** tag `phase-11-complete`.

### PHASE 12 — Freelancer/gig layer: shifts, crew & gig pay
- **Goal:** the moat nobody has — vendors staff gigs and customers book micro-services in the same graph. Optimize for show rate above all. **Est:** 8–10 days.
- **Build:**
  - *Profiles:* freelancer_profiles with reliability/show-rate scores, 6-group skills taxonomy (photo/video, food & bev, event ops, tech, creative, logistics), license-gated skills require APPROVED certs, availability reuses Phase 4 engine.
  - *Shifts:* vendor or customer posters ("Staff this event" prefill from bookings), applications → offers → 4h accept holds, roster-only instant claim, **pay floors per country block underpayment**, overlap exclusion constraint, standby list with auto-broadcast on no-show.
  - *Gig money (extends P5 rails):* poster funded at accept (markup default 15% poster-side; **freelancer sees exact take-home before applying**), checkout math with 50% minimum billable, 12h poster adjustment window (deductions need freelancer approval), payout transfer at +48h, cancellation comp tiers, no-show = refund + reliability −25 + strike review.
  - *Day-of:* rotating TOTP QR check-in (60s window, replay-proof), 300m geo fallback, manual override, late alerts with standby button.
  - *Reliability:* `0.6 show_rate + 0.3 rating + 0.1 recency`, tiers NEW/RELIABLE/ELITE, elite unlocks auto-accept + feed priority; crew rosters with one-tap "Book my Saturday crew" rebooking.
  - *UI:* freelancer mobile-first (map/list discovery, take-home breakdown, check-in state machine, earnings); vendor Crew tab with day-of live board; customer micro-booking `/hire/{skill}` capped at 5 headcount; region-aware compliance surfaces (docs/gig-compliance.md — informational, counsel before scale).
- **Gate:** vendor staffs a booking with 3 servers E2E post→offers→fund→QR in/out→48h payouts, ledger balanced · no-show path verified · overlap constraint blocks double-booking (concurrency) · take-home shown = payout received to the cent · license-gated skills block uncertified · micro-booking capped and working.
- **Done:** tag `phase-12-complete`.

### PHASE 13 — Mobile apps: full native completion & store readiness
- **Goal:** one polished app, three roles, push + deep links + offline — App Store and Play ready. **Est:** 7–9 days.
- **Build:**
  - *Navigation:* persisted role switcher driving three tab sets (Customer/Vendor/Freelancer); audit every P1–P12 flow for parity or an intentional web-handoff logged in DECISIONS.
  - *Push:* token registry, per-category preferences (marketing default OFF), fanout from the P6 notification queue with email fallback, deep-link payloads, grouping, badge sync, in-app notification center.
  - *Links:* Universal/App Links for all entities, cold-start routing matrix, web fallbacks + smart banners.
  - *Offline:* TanStack Query persistence (MMKV), outbox pattern with idempotency keys for messages/checklist/RSVP recordings, background media upload manager with compression.
  - *Native polish checklist:* haptics, skeletons, empty/error states, FlashList, blurhash images, dark-mode audit, dynamic type + a11y, keyboard avoidance, opt-in biometric lock on money screens, in-app review prompts, cold start <2.5s mid-tier Android.
  - *Release engineering:* Sentry with source maps, analytics taxonomy (top 40 events), EAS profiles + staged OTA rollout + forced-update gate, full store asset set (screenshots via Maestro, descriptions, privacy manifests, data safety, account-deletion URL, reviewer demo account).
- **Gate:** golden paths pass on physical iOS + Android (Maestro) · every notification category delivers, deep-links, respects prefs · airplane-mode queue + clean sync · cold start <2.5s, no crash-free regressions · TestFlight + Play internal submitted with complete metadata.
- **Done:** tag `phase-13-complete`.

### PHASE 14 — Admin panel, operations, moderation & fraud
- **Goal:** a small ops team can run, moderate, and defend the platform. **Est:** 7–9 days.
- **Build:**
  - *apps/admin:* separate Next.js app; ADMIN role + mandatory TOTP + IP allowlist; every action → audit_log with before/after.
  - *KPI dashboard:* liquidity (first-reply <24h %, lead→booking, fill rate, no-show), money (GMV, take, escrow balance, refund rate, payout lag), growth (WAU by role, city×category supply heatmap — the expansion map), trust (dispute/review/fraud rates); every number drills down.
  - *Users & vendors:* account timelines, suspend with reason enums, **audited impersonation** (ghost-mode read-only default), merit clamp + badge revoke tools.
  - *Verification queues:* unified (vendor docs, freelancer IDs, certs) with SLA timers, claim assignment, side-by-side doc viewer, reviewer throughput stats.
  - *Bookings & disputes:* full state timeline + ledger view, guarded force-transitions, webhook replay; dispute workspace with auto-assembled evidence, split-resolution execution, outcome letters, 5-business-day SLA.
  - *Moderation & strikes:* queues for messages/reviews/showcases/sites; tombstoned removals; weighted strikes (≥3 → auto-suspend pending confirm); shadow-ban; image-safety hook interface.
  - *Fraud:* signup risk scoring (disposable domains, velocity, geo mismatch) gating activation; duplicate/linkage detection; payment anomaly alerts; review-fraud queue; collusion heuristics.
  - *Config & compliance:* taxonomy manager, typed flag editor with rollback, fee tables with effective dates, targeted announcements; GDPR/APP export + anonymizing deletion jobs (docs/data-retention.md), consent log, DSAR tracker.
- **Gate:** every destructive action requires reason + audit with before/after · dispute split-payout executes in test mode · strikes auto-suspend + notify · ghost mode cannot mutate (API test) · risk gate blocks fixture disposable-email vendor · export/deletion jobs verified.
- **Done:** tag `phase-14-complete`.

### PHASE 15 — Hardening, performance, i18n, SEO/AEO final & launch ops
- **Goal:** production-grade everything, then a controlled go-live in the corridor. **Est:** 7–10 days.
- **Build:**
  - *E2E matrix:* 12 golden flows (booking both modes, cancellations per policy, dispute resolution, onboarding+SLA, showcases, workspace wiring, sites+RSVP, copilot, vendor OS, gig incl. no-show, push) — all CI-green.
  - *Load & chaos (k6):* search 200 RPS p95<400ms, availability 300 RPS p95<250ms, checkout 50 RPS zero double-bookings, 500/min webhook bursts, 2k concurrent sockets; kill-pod/Redis-flush/webhook-storm chaos drills.
  - *Security:* OWASP ASVS L2 sweep, CVE gate in CI, authz fuzz auto-generated from OpenAPI (every endpoint × wrong role), rate-limit audit, secrets rotation, polyglot upload tests, security.txt, external pen-test scoped.
  - *Data safety:* PITR (RPO 15m), nightly cross-provider dumps, **executed restore drill with RTO ≤2h recorded**.
  - *Performance/cost:* Lighthouse CI budgets on 6 key pages (LCP<2.5s, INP<200ms, CLS<0.1), query EXPLAIN audit, Redis caching with invalidation, AI/storage/hosting budget alarms.
  - *i18n:* next-intl + i18n-js, all strings externalized, `ne` (Nepali) for top 200 customer strings, timezone fixtures for Kathmandu(+05:45)/Melbourne/London, pseudo-locale build.
  - *SEO/AEO final:* ≥98% sitemap coverage, JSON-LD validation in CI, thin-page noindex guard, llms.txt + /about/data entity page, ≥90% FAQ coverage, Search Console + IndexNow.
  - *Observability:* OpenTelemetry dashboards + Slack alerts (p95, 5xx, webhook lag, payment success, reconciliation, queue depth), runbook per alert, public status page.
  - *Launch ops:* env promotion checklist with live-mode $1 booking→escrow→release→refund; **supply seeding playbook** (50 founder-network photographers/videographers, white-glove onboarding, founding-vendor offer: 0% take 90 days + FOUNDING badge + PRO free 6mo, 20 pre-loaded showcases, 30 seeded freelancers); 2-week private beta; public launch gate (≥70% leads replied <24h in beta, zero P0s for 7 days); on-call rota, incident matrix, rollback plans, weekly KPI cadence.
- **Gate:** E2E matrix green 3 consecutive runs · load targets met · restore drill RTO ≤2h, alerts fire in game day · zero cross-role leaks, CVE clean · `ne` locale renders Devanagari across auth→booking→RSVP · live $1 cycle verified · docs/LAUNCH.md signed with owner + date. **GO LIVE.**
- **Done:** tag `phase-15-complete`.

---

## 6. Dependency map & launch strategy

```
P1 Foundation
 ├─ P2 Vendor supply ──┬─ P3 Search/Discovery ─┐
 │                     └─ P4 Booking engine ───┼─ P5 Payments/Escrow
 │                                             └─ P6 Leads/Messaging ─ P7 Trust/Merit ★LAUNCHABLE
 ├─ P8 Event workspace ─ P9 Sites/Invites
 ├─ P10 AI layer (needs P3/P6/P8)
 ├─ P11 Vendor OS (needs P5/P6/P7)
 ├─ P12 Gig layer (needs P4/P5)
 └─ P13 Mobile completion → P14 Admin/Ops → P15 Hardening & Launch
```

**Chosen strategy — Fast revenue (recommended):** ship P1–P7, launch the
Kathmandu–Melbourne corridor, then build P8–P15 while live. The trust + booking loop *is*
the business; everything else compounds it. (Alternative "full empire first" — all 15 before
launch — only makes sense with a long funding runway; it delays real-market learning by months.)

Total estimate: **P1–P7 ≈ 43–57 working days to launchable; P8–P15 ≈ 56–72 more.**

---

## 7. Tracking

- **Progress:** [PROGRESS.md](PROGRESS.md) — phase tracker + what exists vs stubbed. Update every phase.
- **Decisions:** [DECISIONS.md](DECISIONS.md) — architecture decisions + reasons. Update every phase.
- **Tags:** `phase-N-complete` on `main` after each gate passes.
- **Launch artifacts produced along the way:** docs/payments.md (P5), docs/ai-safety.md (P10),
  docs/gig-compliance.md (P12), docs/analytics-events.md (P13), docs/data-retention.md (P14),
  docs/dr-runbook.md + docs/runbooks/ + docs/LAUNCH.md (P15).
