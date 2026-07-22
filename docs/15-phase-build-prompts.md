# EVENT MARKETPLACE EMPIRE — 15-PHASE BUILD SYSTEM
## Complete Prompt Pack for Website + Mobile App (Business-Ready)
*Customer → Vendor → Freelancer/Gig · Based on the Competitive Blueprint (July 2026)*

---

# HOW TO USE THIS PACK

1. **Replace `wedjan`** everywhere with your final brand name before starting.
2. **One phase = one focused build cycle.** Run phases in order. Each prompt assumes all previous phases are complete.
3. **Every session:** paste the **MASTER CONTEXT** block first, then the phase prompt.
4. **Never start the next phase until the current phase's ACCEPTANCE CRITERIA all pass.** Commit and tag `phase-N-complete` after each.
5. Keep two living docs the agent must update every phase: `docs/PROGRESS.md` (what exists, what's stubbed) and `docs/DECISIONS.md` (architecture decisions + reasons).
6. **Revenue milestone:** Phases 1–7 = a launchable, money-making marketplace (Tier 0 + trust). Launch after Phase 7. Phases 8–15 are built while live.

---

# MASTER CONTEXT BLOCK (PASTE AT THE TOP OF EVERY PHASE PROMPT)

```
You are the lead engineer building [wedjan], a three-sided event & wedding
marketplace: CUSTOMERS plan events and book vendors; VENDORS sell priced packages
and hire crew; FREELANCERS pick up gig shifts. Business-ready quality: production
code, not prototypes.

STACK (non-negotiable):
- Monorepo: pnpm + Turborepo. apps/web (Next.js 15 App Router, TypeScript strict,
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

# PHASE 1 — FOUNDATION: MONOREPO, AUTH, CORE SCHEMA, DESIGN SYSTEM, CI/CD
**Goal:** running skeleton of all three apps with real multi-role auth. **Est: 5–7 days.**

### PROMPT 1
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Bootstrap the entire platform foundation: monorepo, Spring Boot API with auth,
Next.js web shell, Expo mobile shell, shared design tokens, CI/CD, and the core
identity schema. Everything later builds on this — prioritize correctness and DX.

1) MONOREPO & TOOLING
- pnpm workspaces + Turborepo pipelines (build, dev, lint, test, typecheck).
- apps/api: Spring Boot 3.4 (Gradle Kotlin DSL), packages: auth, account, common
  (errors, pagination, idempotency, clock), config. Dockerfile + docker-compose
  for local Postgres 16 (with pgvector extension), Redis, Mailpit.
- apps/web: Next.js 15 App Router, Tailwind, shadcn/ui init, next-themes,
  TanStack Query, generated API client from packages/shared.
- apps/mobile: Expo SDK 52+, expo-router, NativeWind (consume ui-tokens),
  TanStack Query, expo-secure-store.
- packages/shared: openapi.yaml + codegen script (openapi-typescript) producing
  typed fetch client used by web AND mobile.
- packages/ui-tokens: colors, spacing, radii, typography scale exported as
  Tailwind preset + TS object for NativeWind. Brand direction: premium, editorial,
  celebration-grade; light + dark; one accent; large display serif for headlines,
  clean grotesk for UI. Include Devanagari-capable font fallback stack.

2) DATABASE SCHEMA (Flyway V1__core.sql)
- accounts(id, email UNIQUE, phone NULLABLE, password_hash, status
  [ACTIVE|SUSPENDED|DELETED], locale, default_currency, marketing_opt_in, ...audit)
- account_roles(account_id, role [CUSTOMER|VENDOR|FREELANCER|ADMIN], granted_at)
  UNIQUE(account_id, role)
- refresh_tokens(id, account_id, token_hash, device_name, ip, expires_at,
  revoked_at, rotated_from)
- email_verifications(id, account_id, code_hash, purpose
  [SIGNUP|PASSWORD_RESET|EMAIL_CHANGE], expires_at, consumed_at, attempts)
- profiles(account_id PK, display_name, avatar_media_id, city, country, timezone)
- media_assets(id, owner_account_id, kind [IMAGE|VIDEO|DOC], storage_key,
  mime, bytes, width, height, blurhash, status [UPLOADING|READY|BLOCKED], ...audit)
- app_config(key PK, value jsonb, description) — feature flags & tunables.
- audit_log(id, actor_account_id, action, entity_type, entity_id, metadata jsonb,
  created_at) + async writer.

3) AUTH (API)
- POST /auth/signup {email, password, role[CUSTOMER|VENDOR|FREELANCER]} → creates
  account + role, sends 6-digit OTP (Mailpit locally). Rate limit 5/min/IP.
- POST /auth/verify-email {email, code} — 5 attempts max then invalidate.
- POST /auth/login → access JWT (15m, includes roles[]) + refresh (httpOnly
  cookie on web; returned once for mobile SecureStore). Refresh rotation with
  reuse detection (revoke family on reuse). POST /auth/refresh, /auth/logout,
  /auth/logout-all. Password reset flow (request + confirm).
- POST /auth/roles/add {role} — an account can add CUSTOMER/VENDOR/FREELANCER
  roles later (multi-role is core to the gig layer).
- GET /me → account + roles + profile. PATCH /me for profile basics.
- Media: POST /media/presign {kind,mime,bytes} → presigned PUT to R2 +
  media_asset UPLOADING; POST /media/{id}/complete → verify, probe dimensions,
  generate blurhash, mark READY. Enforce max sizes (img 15MB, video 200MB, doc 10MB).

4) WEB SHELL
- Marketing homepage placeholder (hero, three-sided value props, CTA).
- /signup (role picker: "Plan an event" / "I'm a vendor" / "I work events"),
  /login, /verify, /forgot-password. Authenticated layout with role-aware
  sidebar: customer → "My Events", vendor → "Dashboard", freelancer → "Shifts".
- Role switcher in account menu when multiple roles exist.
- Settings page: profile, email, password, sessions list with revoke.

5) MOBILE SHELL
- expo-router stacks: (auth) group + (app) group with role-aware tabs mirroring
  web nav. Secure token storage, auto-refresh interceptor, logout. Onboarding
  carousel (3 slides, skippable).

6) CI/CD & QUALITY
- GitHub Actions: PR pipeline (lint, typecheck, unit + Testcontainers integration
  tests, web build, api build); main pipeline deploys API→Railway, web→Vercel.
- ESLint+Prettier (web/mobile), Spotless+Checkstyle (api). Husky pre-commit.
- Seed script: admin account, 3 demo accounts (one per role), config defaults.
- README with one-command local setup (docker compose up + pnpm dev).

EDGE CASES TO HANDLE
- Signup with existing email → generic "check your email" (no enumeration).
- Refresh token reuse → revoke family, force re-login, audit_log entry.
- Media complete called twice / for foreign asset → idempotent / 403.
- Role add when already granted → 200 no-op.

ACCEPTANCE CRITERIA
[ ] pnpm dev boots web+mobile+api against docker compose in one command.
[ ] Full signup→verify→login→refresh→logout works on web AND mobile.
[ ] An account can hold CUSTOMER+VENDOR simultaneously and switch UI context.
[ ] OpenAPI spec covers every endpoint; generated client compiles; zero `any`.
[ ] Integration tests green in CI; deploys succeed to Railway+Vercel.
[ ] Rate limiting proven by test (6th login attempt in a minute → 429).
```

---

# PHASE 2 — VENDOR ONBOARDING, VERIFICATION & MANDATORY PRICED PACKAGES
**Goal:** vendors can build a complete, verified, transparently-priced listing. **Est: 6–8 days.**

### PROMPT 2
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Build the full vendor supply pipeline: taxonomy, multi-step onboarding wizard,
mandatory priced packages (Product Law #1), media galleries, document
verification with badges, and the public vendor profile page. This phase defines
supply quality for the whole platform.

1) SCHEMA (Flyway V2)
- categories(id, slug, name, parent_id NULLABLE, icon, sort, is_active). Seed a
  2-level taxonomy: Venues; Photography (Wedding, Event, Studio); Videography;
  Catering; Music & DJs; Live Entertainment; Decor & Florals; Planning &
  Coordination; Beauty (MUA, Hair); Attire; Invitations & Stationery; Transport;
  AV & Lighting; Cakes & Desserts; Officiants; Rentals & Equipment; Photo Booth;
  Security & Staffing; Kids Entertainment; Fireworks & Effects.
- vendor_profiles(account_id PK, business_name, slug UNIQUE, tagline, about,
  founded_year, team_size, languages text[], base_city, base_country, lat, lng,
  website, instagram, response_time_target_minutes, status
  [DRAFT|SUBMITTED|UNDER_REVIEW|VERIFIED|REJECTED|SUSPENDED], rejection_reason,
  onboarding_step, currency, ...audit)
- vendor_categories(vendor_id, category_id, is_primary) UNIQUE pair.
- service_areas(id, vendor_id, mode [CITY_RADIUS|REGION], city, country, lat,
  lng, radius_km, travel_fee_cents NULLABLE, travel_fee_note)
- packages(id, vendor_id, category_id, title, slug, description_md,
  price_cents, currency, pricing_model [FLAT|PER_HOUR|PER_GUEST|STARTING_AT],
  min_guests, max_guests, duration_minutes, whats_included_md,
  whats_excluded_md, booking_mode [INSTANT|REQUEST], deposit_pct SMALLINT
  DEFAULT 25 CHECK 10..100, cancellation_policy [FLEXIBLE|MODERATE|STRICT],
  status [DRAFT|PUBLISHED|ARCHIVED], sort, version, ...audit)
- add_ons(id, package_id NULLABLE (null=vendor-wide), title, price_cents,
  pricing_model, max_qty, description)
- vendor_media(id, vendor_id, media_id, kind [GALLERY|COVER|LOGO|SHOWREEL],
  caption, sort) — enforce ≥5 GALLERY + 1 COVER before submit.
- verification_documents(id, vendor_id, type [BUSINESS_REGISTRATION|
  GOVERNMENT_ID|INSURANCE|PORTFOLIO_PROOF], media_id, status
  [PENDING|APPROVED|REJECTED], reviewer_note, reviewed_by, reviewed_at)
- vendor_badges(vendor_id, badge [IDENTITY_VERIFIED|BUSINESS_VERIFIED|
  INSURED|TOP_RATED], granted_at, expires_at NULLABLE)
- vendor_faqs(id, vendor_id, question, answer_md, sort) — max 12.

2) API
- Vendor self-service: GET/PATCH /vendors/me (step-based partial saves),
  POST /vendors/me/submit (validates completeness → SUBMITTED),
  CRUD /vendors/me/packages (+ publish/unpublish; publishing requires price>0,
  included list ≥3 items, ≥1 package photo), CRUD add-ons, service-areas,
  media (with drag-sort), faqs, documents upload.
- Validation gates on submit: ≥1 category, ≥1 service area, ≥1 PUBLISHED-ready
  package, ≥5 gallery photos + cover, GOVERNMENT_ID + BUSINESS_REGISTRATION docs
  uploaded, about ≥200 chars. Return structured fieldErrors per step.
- Public: GET /vendors/{slug} (only VERIFIED), GET /vendors/{slug}/packages.
- Admin (minimal now, full console Phase 14): GET /admin/verifications?status=,
  POST /admin/verifications/{docId}/approve|reject {note}. Approving both
  GOVERNMENT_ID and BUSINESS_REGISTRATION auto-grants badges and, if profile
  SUBMITTED, flips vendor to VERIFIED + sends congratulation email with public URL.
  INSURANCE approval grants INSURED badge with expiry from doc metadata.

3) WEB — VENDOR ONBOARDING WIZARD (/vendor/onboarding)
Resumable 7-step wizard with progress bar and per-step autosave:
  1 Business basics  2 Categories (primary + up to 3)  3 Service areas (map
  picker with radius slider, travel fee)  4 Packages (builder with live preview
  card; enforce transparent pricing; add-ons)  5 Media (drag-drop multi-upload,
  reorder, cover select, captions; client compression)  6 Verification docs
  (camera/file upload, status chips)  7 Review & submit (checklist of gates,
  submit CTA disabled until all pass, then celebratory success screen).
- Vendor dashboard home: profile status card, verification checklist,
  "listing strength" meter (0–100 scoring completeness), preview-as-customer.

4) WEB — PUBLIC VENDOR PROFILE (/v/{slug})
- Hero: cover, logo, name, badges row, primary category, base city, "Responds in
  ~X min" placeholder, favorite button (stub storage).
- Gallery with lightbox; About; Packages grid (price ALWAYS visible, pricing_model
  suffix e.g. "/guest", deposit + cancellation chips, Instant Book bolt icon);
  Add-ons; Service area map; FAQs; Reviews placeholder section ("New on
  [wedjan]").
- JSON-LD: LocalBusiness + Service + Offer per package. OG image auto-generated
  (vercel/og) with cover + name + starting price.
- CTA buttons "Check availability" / "Message vendor" → route to waitlist modal
  (booking Phase 4, messaging Phase 6) — capture intent events.

5) MOBILE
- Vendor onboarding parity (native pickers, camera uploads), dashboard home,
  public vendor profile screen (shared components philosophy, native gallery).

EDGE CASES
- Slug collisions → auto-suffix; slug immutable after VERIFIED (SEO).
- Package price edit after publish → new version row; existing bookings keep old
  price snapshot (snapshot fields on Booking in Phase 4 — note in DECISIONS).
- Doc image unreadable → reviewer rejects with reason; vendor re-uploads same slot.
- Vendor edits profile after VERIFIED → sensitive fields (business_name, docs)
  flip status to UNDER_REVIEW for those items only, listing stays live.
- Currency: packages inherit vendor currency; changing vendor currency blocked
  once any package is published.

ACCEPTANCE CRITERIA
[ ] A new vendor completes all 7 steps on web and on mobile, submits, admin
    approves docs, profile goes live at /v/{slug} with visible prices.
[ ] Submit blocked with precise per-step errors when any gate fails.
[ ] No "price on request" is possible anywhere in the system.
[ ] Listing strength meter reacts live to edits.
[ ] JSON-LD validates in Google Rich Results test.
[ ] 20 seeded demo vendors across ≥8 categories, 2 cities (Kathmandu, Melbourne).
```

---

# PHASE 3 — SEARCH, DISCOVERY, INSPIRATION FEED & AEO
**Goal:** customers find the right vendor fast; the platform is discoverable by Google and AI engines. **Est: 6–8 days.**

### PROMPT 3
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Build the discovery layer: filtered search, category×city SEO landing pages,
the vendor-tagged inspiration feed (PartySlate mechanic), favorites/compare,
and AI-engine-ready structured content (AEO).

1) SEARCH BACKEND
- Add search infrastructure: Postgres FTS (tsvector column on vendor_profiles +
  packages, weighted: business_name A, categories B, about/package text C) +
  pg_trgm for fuzzy. GIN indexes. Geo filter via earthdistance or PostGIS
  (choose, justify in DECISIONS) matching customer point against service_areas.
- GET /search/vendors params: q, category, city|lat/lng+radius, price_min/max
  (normalized to package price_cents in profile currency; document FX display
  approach), guests, badges[], booking_mode, sort [RELEVANCE|PRICE_ASC|
  PRICE_DESC|RATING(placeholder)|NEWEST], cursor. Response: vendor cards with
  cheapest matching package summary, badge list, distance_km, next 3 available
  dates placeholder (Phase 4 fills), facet counts for filters.
- Empty-result intelligence: return relaxation suggestions (nearby cities,
  adjacent categories, price band widening) computed server-side.
- search_events analytics table (query, filters jsonb, results_count,
  account_id NULLABLE, session_id) — async insert.

2) INSPIRATION FEED (real events, every photo tags its vendors)
- Schema: showcases(id, vendor_id owner, title, event_type [WEDDING|CORPORATE|
  BIRTHDAY|CULTURAL|OTHER], event_date, city, country, cover_media_id,
  description_md, status [DRAFT|PUBLISHED|FLAGGED], style_tags text[]),
  showcase_media(id, showcase_id, media_id, caption, sort),
  showcase_vendor_tags(showcase_id, vendor_id, role_label e.g. "Photographer",
  status [PENDING|ACCEPTED|DECLINED]) — tagged vendors confirm before their
  name shows (prevents fake association).
- API: vendor CRUD for showcases; tag other vendors by search; tagged vendor
  gets notification (email now) to accept; public GET /showcases feed
  (cursor, filters: event_type, city, style_tags) + GET /showcases/{id}.
- Web: /inspiration masonry feed; showcase detail = full-bleed gallery, credits
  panel listing every tagged vendor with mini-card + link; "Book this team"
  CTA linking each vendor. Vendor dashboard: "Add a real event" flow.
- Mobile: inspiration tab with infinite scroll, double-tap favorite.

3) FAVORITES, SHORTLISTS & COMPARE
- favorites(account_id, entity_type [VENDOR|SHOWCASE|PACKAGE], entity_id).
- shortlists(id, account_id, name) + shortlist_items — customers group vendors
  per event idea. Compare view (web): up to 4 packages side-by-side (price,
  inclusions diff highlighting, deposit, policy, badges).

4) SEO/AEO PROGRAMMATIC PAGES
- Route: /[country]/[city]/[category] (e.g. /au/melbourne/wedding-photography)
  statically generated for every active city×category with ≥1 vendor; ISR 1h.
  Content blocks: H1 "{Category} in {City}", intro paragraph (template with
  live stats: vendor count, price range from real packages), top vendors grid
  (merit order placeholder), FAQ section (6 questions per category from a seeded
  faq_templates table, answers include live median prices), internal links to
  sibling cities/categories.
- JSON-LD: ItemList of vendors, FAQPage, BreadcrumbList. XML sitemaps
  (index + per-type, auto-regenerated daily). robots.txt. llms.txt describing
  the platform + key entity URL patterns for AI crawlers.
- Vendor profile pages get FAQPage schema from vendor_faqs. Clean semantic
  HTML: every fact (price, city, category) in machine-readable microcopy.

5) HOMEPAGE v1 (replace placeholder)
- Hero search (category + city + date pickers), trust strip (escrow, verified,
  0% customer fees), category tiles, featured showcases carousel, "How it works"
  for all three sides, city links footer.

EDGE CASES
- Search with typo ("photografer") → trigram still matches; log low-result queries.
- Vendor tagged in showcase declines → credit hidden, media stays.
- City with 1 vendor → landing page still renders, FAQ prices say "from {X}".
- Compare packages in different currencies → show native + approx converted
  (static daily FX table, clearly marked "approx").

ACCEPTANCE CRITERIA
[ ] Search p95 < 300ms with 10k seeded vendors (write a seed generator).
[ ] All filters combine correctly; facet counts accurate; cursor stable.
[ ] Showcase tagging flow works end-to-end incl. accept/decline emails.
[ ] 100% of programmatic pages pass Rich Results test; sitemap serves.
[ ] Lighthouse SEO ≥ 95 on landing + vendor pages; llms.txt live.
[ ] Mobile search + inspiration feed at parity.
```

---

# PHASE 4 — AVAILABILITY & THE BOOKING ENGINE
**Goal:** real-time availability and a bulletproof booking state machine. Date-availability search goes live. **Est: 7–9 days.**

### PROMPT 4
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Build vendor availability management and the core Booking aggregate with a
strict state machine, holds, double-booking prevention, cancellation policies,
and iCal sync. Wire date filters into search. (Payments attach in Phase 5 —
design PENDING_PAYMENT to hand off cleanly.)

1) AVAILABILITY MODEL
- Vendors operate in DATE mode (photographers, caterers: whole-day slots with
  configurable jobs_per_day, default 1) or SLOT mode (venues, studios: defined
  time blocks per day). Field on vendor_profiles: availability_mode.
- availability_rules(id, vendor_id, weekday 0-6, is_available, slots jsonb for
  SLOT mode [{start,end,label}], jobs_per_day) — the weekly default.
- availability_exceptions(id, vendor_id, date, type [BLACKOUT|EXTRA_OPEN|
  CUSTOM_SLOTS], slots jsonb, note) — overrides.
- external_calendars(id, vendor_id, ics_url, last_synced_at, sync_status) —
  pull busy dates hourly (job) and write BLACKOUT exceptions tagged source=ICS.
- Derived function: get_availability(vendor_id, from, to) → per-date status
  [AVAILABLE|LIMITED|BOOKED|BLACKED_OUT] considering confirmed bookings + holds.
- Expose ICS export feed per vendor (secret token URL) of confirmed bookings.

2) BOOKING AGGREGATE (Flyway V4)
- bookings(id, code human-readable e.g. BK-2026-000123, customer_id, vendor_id,
  package_id, event_id NULLABLE (Phase 8 links), event_date, start_time,
  end_time, guests, venue_address, venue_lat/lng, notes,
  price snapshot fields: package_title_snap, package_terms_snap jsonb,
  currency, subtotal_cents, addons_cents, travel_fee_cents, discount_cents,
  total_cents, deposit_pct, cancellation_policy_snap,
  status [DRAFT|REQUESTED|VENDOR_ACCEPTED|PENDING_PAYMENT|CONFIRMED|
  IN_PROGRESS|COMPLETED|CANCELLED_BY_CUSTOMER|CANCELLED_BY_VENDOR|DECLINED|
  EXPIRED|DISPUTED], hold_expires_at, requested_at, confirmed_at, completed_at,
  cancelled_at, cancel_reason, version, ...audit)
- booking_add_ons(booking_id, add_on_id, title_snap, qty, price_cents_snap)
- booking_events(id, booking_id, from_status, to_status, actor, metadata,
  created_at) — full history, append-only.

3) STATE MACHINE & RULES (implement as explicit transition service; reject
   illegal transitions with BOOKING_INVALID_TRANSITION)
- INSTANT packages: customer configures → DRAFT → (Phase 5 payment) →
  CONFIRMED. Availability HOLD placed at checkout start for 15 min (Redis lock
  + hold row) so two customers can't pay for the same date.
- REQUEST packages: customer submits → REQUESTED (24h vendor SLA timer).
  Vendor ACCEPTS (optionally adjusting add-ons/travel fee, never base price
  above published) → VENDOR_ACCEPTED + 24h customer payment window
  (PENDING_PAYMENT) with date soft-held; vendor DECLINES {reason enum +
  message} → DECLINED; timer lapse → EXPIRED (notify both, suggest
  alternatives).
- Double-booking guard: partial unique index on (vendor_id, event_date) for
  DATE-mode active statuses; SLOT mode: exclusion constraint on tstzrange.
  All transitions in serializable transaction; concurrency test required.
- Cancellation policy engine (evaluates days-to-event → refund %):
  FLEXIBLE full<30d,50%<14d,0%<7d · MODERATE full<60d,50%<30d,0%<14d ·
  STRICT full<90d,25%<45d,0%<30d. Deposit refundability follows same table.
  Output a RefundCalculation object Phase 5 will execute. Vendor-initiated
  cancellation → always 100% refund + vendor penalty flag (merit impact).
- Completion: auto-transition CONFIRMED→IN_PROGRESS at event start,
  IN_PROGRESS→COMPLETED at end + 24h (scheduler), or vendor marks complete;
  customer has 48h dispute window (flag only; resolution console Phase 14).
- Reschedule: single mutual-consent reschedule allowed ≥14d before event; both
  parties confirm new date (availability re-checked); history logged.

4) API
- Vendor: CRUD availability rules/exceptions, connect ICS, GET calendar month
  view (statuses per date), booking inbox (filter by status), accept/decline/
  complete endpoints.
- Customer: GET /vendors/{slug}/availability?from&to (public, cached 60s),
  POST /bookings (from package + config; validates availability atomically),
  GET /bookings/{id}, cancel, reschedule-propose/confirm.
- Search integration: date param now filters truly-available vendors and vendor
  cards show real "next available dates".

5) WEB
- Vendor: calendar screen (month grid, tap date → edit exception, bulk blackout
  drag), booking inbox with urgent-SLA countdown chips, booking detail with
  timeline of booking_events, accept/decline modal (decline requires reason).
- Customer: booking widget on vendor profile (date picker with live
  availability coloring, guest count, add-on selector, price breakdown updating
  live incl. travel fee by distance), request/instant flows, my-bookings list +
  detail with status stepper, cancel flow showing exact refund preview from
  policy engine BEFORE confirm.
- Mobile: full parity both roles; calendar optimized for touch.

EDGE CASES
- Hold expiry while customer on payment page → clear message + re-check.
- Vendor blacks out a date with active REQUESTED booking → blocked with prompt
  to decline the request first.
- ICS feed flaky → mark sync_status DEGRADED, keep last data, alert vendor.
- Timezones: event_date is venue-local; store tz on booking; all SLA timers UTC.
- Same-day booking attempts → allowed only if package.allow_same_day flag.

ACCEPTANCE CRITERIA
[ ] Concurrency test: 20 parallel checkouts for one date → exactly 1 succeeds.
[ ] Every legal transition covered by tests; illegal transitions rejected.
[ ] Refund preview matches policy table in all boundary cases (property tests).
[ ] Search by date returns only truly bookable vendors; profile widget matches.
[ ] ICS import blocks dates within 1 hour; export feed loads in Google Calendar.
[ ] Full request→accept→(payment stub)→confirm→complete cycle on web + mobile.
```

---

# PHASE 5 — PAYMENTS, ESCROW & PAYOUTS (STRIPE CONNECT)
**Goal:** money moves safely: deposits, milestones, escrow release, refunds, ledger. **Est: 8–10 days.**

### PROMPT 5
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Implement the full money system on Stripe Connect: vendor onboarding, deposit +
milestone payment plans, platform-held escrow with release after completion,
refunds driven by the Phase 4 policy engine, a double-entry ledger as source of
truth, invoices, and payout dashboards. Product Law #3 and #4 apply.

1) CONNECT ONBOARDING
- vendor_payment_accounts(vendor_id PK, stripe_account_id, country,
  charges_enabled, payouts_enabled, requirements jsonb, status) — Express
  accounts; hosted onboarding link flow; webhook account.updated syncs status.
- Gate: packages cannot be INSTANT-bookable and vendor cannot accept REQUEST
  bookings into PENDING_PAYMENT until charges_enabled (UI shows blocking task).

2) PAYMENT PLAN MODEL
- payment_plans(id, booking_id UNIQUE, currency, total_cents, schedule jsonb:
  [{milestone: DEPOSIT|BALANCE|CUSTOM, due_at, amount_cents, status}],
  platform_fee_pct snapshot, status)
- Default schedule: DEPOSIT (deposit_pct) due at confirmation; BALANCE due at
  min(event_date - 14d, now+…) — if event <14d away, single full payment.
- payments(id, plan_id, milestone_idx, stripe_payment_intent_id, amount_cents,
  application_fee_cents, status [REQUIRES_ACTION|PROCESSING|SUCCEEDED|FAILED|
  REFUNDED|PARTIALLY_REFUNDED], failure_code, receipt_url, idempotency_key)
- Charge pattern: destination charges to platform account with
  on_behalf_of/transfer_data.destination deferred — use SEPARATE CHARGES &
  TRANSFERS: customer pays platform; funds sit on platform balance (escrow);
  transfers to vendor created only at RELEASE. Document flow-of-funds diagram
  in docs/payments.md.
- Platform fee: app_config key take_rate_default (12%) + per-category override
  table. Customer pays 0% fees (Law #4) — fee deducted from vendor side at
  transfer time.

3) ESCROW LIFECYCLE
- On final payment success → booking CONFIRMED (or stays CONFIRMED if deposit
  model — deposit success alone confirms; balance auto-charged at due date via
  off-session PaymentIntent with saved card; 3 retry schedule D0/D2/D4 then
  booking flag BALANCE_OVERDUE + notifications).
- RELEASE: at COMPLETED + 48h dispute window with no dispute → create transfer
  (total - platform fee - any refunds) to vendor; ledger entries; email both.
- DISPUTE raised in window → freeze release; Phase 14 console resolves with
  split-release tooling (build the API now: POST /admin/bookings/{id}/resolve
  {vendor_cents, customer_refund_cents, note}).
- Refunds: execute RefundCalculation from Phase 4 (Stripe refund on the charge,
  ledger entries, plan/booking status updates). Vendor-fault cancellations
  refund 100% incl. any processing cost absorbed by platform (config flag).

4) LEDGER (source of truth, append-only)
- ledger_accounts(id, type [PLATFORM_CASH|PLATFORM_FEES|VENDOR_PAYABLE|
  CUSTOMER_ESCROW|STRIPE_FEES|REFUNDS], owner_ref NULLABLE)
- ledger_entries(id, txn_id groups double entries, account_id, booking_id,
  direction [DEBIT|CREDIT], amount_cents, currency, description, created_at)
- Invariant test: every txn balances to zero. Nightly reconciliation job:
  Stripe balance vs ledger; discrepancies → alert + report row.

5) WEBHOOKS & RELIABILITY
- /webhooks/stripe: signature verify, event dedupe table (event_id PK),
  handle payment_intent.succeeded/failed, charge.refunded, account.updated,
  payout.paid/failed, charge.dispute.created (card dispute ≠ platform dispute:
  freeze + flag). All handlers idempotent; DLQ via Redis for failures; replay
  admin endpoint.

6) INVOICES, RECEIPTS, TAX FIELDS
- PDF receipt per payment + booking invoice (platform-branded, vendor details,
  ABN/tax-id field on vendor_profiles, line items, fee breakdown visible to
  vendor only). Store as media, email links. GST/VAT display hooks per country
  (labels only; tax engine deferred, note in DECISIONS).

7) UI
- Customer web/mobile: checkout (Stripe Elements/PaymentSheet), saved cards
  (SetupIntent), payment schedule timeline on booking detail, receipts list,
  refund status states.
- Vendor web/mobile: earnings dashboard (upcoming payouts = escrow balance per
  booking with release ETA, transferred history, fee breakdowns, monthly chart),
  onboarding status card, payout account management link.

EDGE CASES
- Balance auto-charge fails 3x → clear customer flows to update card; vendor
  informed; event-week rules (T-7d unpaid → vendor may cancel penalty-free).
- Customer cancels between deposit and balance → refund math on paid amounts
  only; test all policy × timing combos.
- Currency mismatch guard: charge currency = vendor currency; UI shows customer
  approx conversion pre-pay.
- Duplicate webhook / out-of-order events → state machine + dedupe safe.
- Stripe account disabled after bookings exist → block new, allow existing to
  complete, warn admin.

ACCEPTANCE CRITERIA
[ ] Test-mode E2E: deposit → auto balance → completion → 48h → transfer, with
    ledger perfectly balanced and reconciliation clean.
[ ] Every refund scenario matches the Phase 4 preview to the cent.
[ ] Kill the API mid-payment (chaos test) → no money state corruption on replay.
[ ] Vendor sees exact fee math; customer never sees a platform fee line.
[ ] Off-session balance charge works with 3DS test cards incl. failure retries.
[ ] Dispute freeze prevents release; admin split-resolution API works.
```

---

# PHASE 6 — MESSAGING, EXCLUSIVE LEADS, QUOTES & ANTI-GHOSTING
**Goal:** every conversation converts: exclusive leads, real-time chat, structured quotes, SLA machinery. **Est: 6–8 days.**

### PROMPT 6
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Build the communication core enforcing Product Law #2 (exclusive leads):
real-time messaging, structured quotes inside chat, response-SLA tracking that
feeds merit ranking, privacy shielding, and anti-ghosting mechanics for BOTH
sides.

1) SCHEMA
- leads(id, customer_id, vendor_id, source [PROFILE|SEARCH|SHOWCASE|
  AI_SHORTLIST], event_type, event_date NULLABLE, city, guests, budget_cents
  NULLABLE, message_first, status [OPEN|QUOTED|BOOKED|CLOSED_BY_CUSTOMER|
  CLOSED_BY_VENDOR|EXPIRED], close_reason [BOOKED_ELSEWHERE|OVER_BUDGET|
  DATE_UNAVAILABLE|NO_RESPONSE|OTHER], sla_due_at, first_vendor_reply_at)
  — one lead targets exactly ONE vendor. UNIQUE(customer_id, vendor_id,
  event_date) to prevent accidental dupes; creating a new lead to a different
  vendor is a NEW lead (never fan-out).
- conversations(id, lead_id NULLABLE, booking_id NULLABLE, customer_id,
  vendor_id, last_message_at, customer_unread, vendor_unread) — a booking
  continues its lead conversation.
- messages(id, conversation_id, sender_account_id NULLABLE (null=system), kind
  [TEXT|ATTACHMENT|QUOTE|SYSTEM|QUICK_ACTION], body, media_ids uuid[],
  quote_id NULLABLE, created_at, read_at)
- quotes(id, conversation_id, vendor_id, package_id NULLABLE, line_items jsonb
  [{title, qty, unit_cents}], subtotal_cents, travel_fee_cents, discount_cents,
  total_cents, currency, valid_until, note_md, status [SENT|VIEWED|ACCEPTED|
  DECLINED|EXPIRED|SUPERSEDED]) — accepting a quote spawns a booking draft
  prefilled (hands to Phase 4/5 flow).
- saved_replies(id, vendor_id, title, body_md, sort) — max 20.

2) REALTIME & NOTIFICATIONS
- STOMP over WebSocket: /topic/conversations/{id}; presence + typing events;
  15s polling fallback endpoint. Read receipts (per-message read_at, batched).
- Email notifications with 5-min debounce per conversation, reply-by-email via
  inbound Resend webhook appending as message (strip signatures). Push wiring
  lands Phase 13 — emit NotificationEvents to a queue now.

3) EXCLUSIVITY, PRIVACY & SAFETY
- Contact shielding pre-booking: regex+heuristics detect phone/email/instagram
  handles in messages → allow send but blur to counterparty with tooltip
  "Contact details unlock after booking — this protects your payment." Vendor
  and customer full contact auto-shared on CONFIRMED (system message card).
- Disintermediation telemetry: count shield triggers per vendor (report only).
- Abuse: block user, report message → moderation queue table (console Phase 14).
  Basic profanity/link filters on first-contact messages from new accounts.

4) SLA & ANTI-GHOSTING ENGINE (feeds Phase 7 merit)
- Vendor side: sla_due_at = lead created +24h. Reminders at 4h/12h/20h (email +
  in-app). Metrics per vendor: response_rate (30d, replied within SLA / leads),
  median_first_response_minutes → shown publicly as "Responds in ~X" once ≥5
  leads. On SLA breach: lead EXPIRED, customer notified with 3 alternative
  vendor suggestions (same category/city/price band) + one-tap re-inquiry.
- Customer side: if vendor quoted and customer silent 72h → gentle nudge with
  one-tap quick actions rendered as buttons in chat AND email: "Still deciding",
  "Over budget", "Booked someone else", "Not a fit" → posts QUICK_ACTION message
  + closes/annotates lead. Vendors see honest closure instead of silence.
- Weekly vendor digest: open leads, at-risk SLAs, response stats vs city median.

5) UI
- Customer: "Message vendor" opens structured first-inquiry composer (event
  type, date, city, guests, budget slider optional, message ≥30 chars) —
  quality-gated leads. Inbox with lead status chips; quote cards in-thread with
  Accept → booking flow, Decline-with-reason; compare quotes across
  conversations screen (side-by-side line items).
- Vendor: unified inbox (filters: needs-reply, quoted, booked; SLA countdown
  badges, urgent sort), thread view with lead context panel (event details,
  customer history: past bookings count, response behavior), quote builder
  drawer (start from package → edit line items; enforce total ≥ deposit
  viability), saved replies picker, close-lead flow with reason.
- Mobile: full parity; chat is the highest-polish surface (optimistic send,
  retry queue, image share from camera roll).

EDGE CASES
- Quote accepted after valid_until → auto-EXPIRED, prompt vendor to refresh.
- Two quotes active in one thread → newest supersedes (status SUPERSEDED).
- Vendor replies at 23h59m → SLA met; clock precision tests.
- Lead for date vendor later blacks out → system message + suggest close.
- Attachment virus/type check via MediaService rules; images only from new accounts.

ACCEPTANCE CRITERIA
[ ] Lead → chat → quote → accept → booking draft flows perfectly on web+mobile.
[ ] Exclusivity provable: no API path shares a lead with a second vendor.
[ ] Contact shielding blurs and then unlocks exactly at CONFIRMED.
[ ] SLA expiry fires alternatives email; response metrics compute correctly
    against fixture timelines.
[ ] WebSocket drop → polling fallback seamless; messages never lost (queue test).
[ ] Reply-by-email round-trip lands in thread within 60s.
```

---

# PHASE 7 — REVIEWS, TRUST SYSTEM & MERIT RANKING  → **LAUNCH GATE**
**Goal:** verified reviews, both-way ratings, badges, and the merit algorithm replace all ranking placeholders. Platform is launch-ready. **Est: 5–7 days.**

### PROMPT 7
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Complete the trust layer: verified-booking-only reviews (Product Law #5),
double-blind submission, vendor replies, customer reliability scores, badge
finalization, and the transparent merit ranking that powers all organic sort.
After this phase the platform launches.

1) REVIEWS
- reviews(id, booking_id UNIQUE per direction, direction [CUSTOMER_TO_VENDOR|
  VENDOR_TO_CUSTOMER], overall SMALLINT 1-5, criteria jsonb — customer→vendor:
  {quality, communication, value, punctuality}; vendor→customer:
  {communication, payment_reliability, respect}, title, body (min 50 chars for
  public C→V), media_ids uuid[] max 8, status [PENDING_COUNTERPART|PUBLISHED|
  FLAGGED|REMOVED], published_at)
- Eligibility: booking COMPLETED, window opens immediately, closes +30d.
  Double-blind: neither published until both submit OR 14d elapses (then
  publish whichever exists). Reminder emails day 2 and day 10 with deep links.
- vendor replies: one per review, ≤1000 chars, editable 7d.
- Reporting → moderation queue; REMOVED keeps tombstone "removed for guideline
  violation" (transparency).
- V→C reviews are PRIVATE: aggregate into customer_reliability(account_id,
  score 0-100, bookings_count, avg jsonb) — vendors with INSTANT packages can
  require min reliability (default off); shown to vendors on lead context panel
  as simple tier [NEW|GOOD|EXCELLENT] only (no raw scores, no public shaming).

2) RATING AGGREGATES
- vendor_rating_stats(vendor_id, count, avg_overall, avg_criteria jsonb,
  histogram int[5], bayesian_score) — Bayesian: (v*R + m*C)/(v+m) with m=8,
  C=global mean; recompute on publish (async).
- Profile UI: histogram bars, criteria breakdown, sort/filter reviews
  (recent, highest, lowest, with photos), verified-booking badge on each.

3) BADGES FINAL
- TOP_RATED: rolling grant job — ≥10 reviews, bayesian ≥4.8, response_rate
  ≥90%, cancellation_rate ≤2% in 12mo; re-evaluated monthly, expiry set.
- RISING_STAR: <12mo on platform, ≥3 reviews avg ≥4.8, response ≥95%.
- Badge explainer page /trust: how every badge is earned, how ranking works,
  "we never sell ranking" pledge — this page is marketing, write it well.

4) MERIT RANKING (replaces all placeholders)
- merit_scores(vendor_id, score numeric, components jsonb, computed_at).
  score = 0.30*bayesian_norm + 0.20*response_rate + 0.15*conversion
  (leads→booked, smoothed) + 0.15*completion_reliability (1 - vendor_cancel
  rate, weighted recent) + 0.10*profile_quality (listing strength) +
  0.10*recency_activity (logins, calendar freshness, new showcases).
  New-vendor cold start: score = category median * 0.9 for first 5 leads
  (fair exposure). Nightly batch + on-event nudges.
- Search sort RELEVANCE = merit * text-relevance blend; RATING sort = bayesian.
  Components stored so Phase 11 can show vendors "why you rank here".
- Anti-gaming: review-velocity anomaly detection (>=3 reviews same IP/24h →
  flag), self-booking detection (customer/vendor account linkage heuristics),
  merit clamp while flagged.

5) LAUNCH-GATE SWEEP (part of this phase)
- Remove every placeholder from Phases 2–6 (response time, next-available,
  rating sorts). Wire homepage "featured" to top merit per city.
- Golden-path E2E (Playwright): signup → search Melbourne wedding photography →
  vendor profile → request booking → vendor accepts → pay deposit (test mode) →
  auto balance → complete → both reviews → merit updates → ranking changes.
  This test becomes the permanent regression suite cornerstone.

EDGE CASES
- Booking cancelled after review window opened → reviews voided.
- Review with contact info/URLs → auto-hold for moderation.
- Tie merit scores → newer vendor first (support the underdog, log decision).
- Customer deletes account → reviews persist anonymized ("Verified customer").

ACCEPTANCE CRITERIA
[ ] Impossible to create a review without a COMPLETED booking (API fuzz test).
[ ] Double-blind logic exact: early single-sided publish only after 14d.
[ ] Merit recompute reacts to a new 5★ review within 60s (event nudge path).
[ ] /trust page live; ranking components stored per vendor.
[ ] Golden-path E2E green on CI, web + mobile smoke.
[ ] LAUNCH CHECKLIST doc generated: envs, keys, seed supply plan, support inbox.
```

---

# PHASE 8 — EVENT PLANNING WORKSPACE (MULTI-EVENT, BUDGET-WIRED, COLLABORATIVE)
**Goal:** the demand magnet — customers run their whole event inside the platform, and every tool funnels toward bookings. **Est: 7–9 days.**

### PROMPT 8
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Build the customer Event Workspace: multi-day/multi-ceremony event projects,
auto-generated checklists, a budget engine wired to REAL bookings and payments,
guest list + RSVP tracking, a drag-and-drop seating planner synced to RSVPs,
and family collaboration. This is where South Asian multi-event reality meets
Western multi-event trends — build for 6 collaborators, not 2.

1) SCHEMA (Flyway V8)
- events(id, owner_account_id, title, type [WEDDING|CORPORATE|BIRTHDAY|
  ANNIVERSARY|CULTURAL|CONFERENCE|OTHER], primary_date, city, country,
  guest_estimate, total_budget_cents NULLABLE, currency, cover_media_id,
  status [PLANNING|UPCOMING|COMPLETED|ARCHIVED], ...audit)
- sub_events(id, event_id, title e.g. "Mehndi"/"Sangeet"/"Ceremony"/
  "Reception"/"Welcome Dinner", date, start_time, end_time, venue_name,
  venue_address, lat, lng, guest_estimate, dress_code, sort) — templates per
  event type seed: WEDDING(Western)=Rehearsal+Ceremony+Reception;
  WEDDING(South Asian)=Mehndi+Sangeet+Baraat+Ceremony+Reception; editable.
- event_members(event_id, account_id, role [OWNER|PARTNER|PLANNER|FAMILY|
  VIEWER], invited_email, status [INVITED|ACTIVE|REMOVED], permissions
  derived: OWNER/PARTNER full; PLANNER all except delete event + money;
  FAMILY comment+view+guest-edit own group; VIEWER read-only)
- checklist_items(id, event_id, sub_event_id NULLABLE, title, description_md,
  due_at (derived from event date offset), category_id NULLABLE (links to
  vendor category → "Book photographer" deep-links to search prefiltered),
  status [TODO|IN_PROGRESS|DONE|SKIPPED], assignee_member_id, source
  [TEMPLATE|AI|MANUAL], sort) — checklist_templates seed per event type with
  month-offset rules (e.g. -10mo book venue, -8mo photographer, -2w final
  headcount).
- budget_categories(id, event_id, name, icon, estimate_cents, sort)
- budget_lines(id, budget_category_id, title, estimate_cents, actual_cents,
  booking_id NULLABLE, payment_plan_visible bool, vendor_name_snap, status
  [PLANNED|BOOKED|PAID|OVERPAID_FLAG])
- guests(id, event_id, group_id NULLABLE, first_name, last_name, email,
  phone, side [A|B|SHARED] labels configurable, is_plus_one_of NULLABLE,
  age_group [ADULT|CHILD|INFANT], notes)
- guest_groups(id, event_id, name e.g. "Sharma family", invite_household bool)
- guest_invites(id, guest_id, sub_event_id) — who is invited to what.
- rsvps(id, guest_id, sub_event_id, response [YES|NO|MAYBE|PENDING],
  dietary text[], accessibility_note, song_request, responded_at, source
  [SITE|MANUAL|IMPORT]) UNIQUE(guest_id, sub_event_id)
- table_plans(id, sub_event_id UNIQUE), tables(id, plan_id, name, shape
  [ROUND|RECT|SQUARE], capacity, x, y, rotation), seat_assignments(table_id,
  guest_id UNIQUE per plan)
- activity_feed(id, event_id, actor_member_id, verb, entity_type, entity_id,
  metadata jsonb, created_at)

2) BUDGET ENGINE (the killer wiring)
- Estimate generator: POST /events/{id}/budget/generate {guests, city,
  categories[]} → line-item estimates from INTERNAL data (median published
  package price per category per city, weighted by guest bands); fall back to
  seeded regional heuristics table when <5 vendors. Show data source label
  ("based on 42 real Melbourne packages").
- Live wiring: booking CONFIRMED for a linked event → auto-create/attach
  budget_line with actual_cents from booking total, vendor name, deep link;
  payment milestones surface as due-date chips; payment success updates paid
  progress. Checklist item of matching category auto-completes with a
  celebratory toast + activity entry.
- Overspend alerts: category actuals > estimate → banner + suggestion to
  rebalance (move slack from underspent categories, one-tap).
- Bookings created from vendor profiles ask "Attach to which event?" when
  the customer has ≥1 active event (and pass event_id into Phase 4 flow).

3) API + UI (WEB)
- /events dashboard: event cards with countdown, progress ring (checklist %),
  budget health bar, next 3 tasks.
- Event home: tabs Overview | Checklist | Budget | Guests | Seating |
  Vendors | Website(P9) | Copilot(P10). Overview = countdown hero, sub-event
  timeline strip, member avatars + invite button, activity feed.
- Checklist: grouped by month ("10 months before"), drag reorder, assign
  members, quick-add, category items deep-link to prefiltered search,
  overdue styling.
- Budget: category accordions, estimated vs actual dual bars, unbooked
  categories show "Find vendors from {median}" CTA, export CSV.
- Guests: table with inline edit, CSV import (mapping UI, dedupe on
  email/name+phone, household grouping suggestion), bulk invite-to-sub-event,
  RSVP status matrix (guests × sub-events grid), dietary rollup report,
  manual RSVP recording for phone replies.
- Seating: canvas (dnd-kit or konva) — palette of table shapes, drop guests
  from RSVP-YES sidebar (search + filters by group/side/dietary), capacity
  warnings, unseated counter, auto-suggest fill (greedy by group), print/PDF
  export with table cards. RSVP flips to NO → seat freed + alert chip.
- Collaboration: invite by email with role; pending invite accept flow joins
  account; permission checks on EVERY mutation server-side.

4) MOBILE
- Parity for dashboard, checklist (swipe complete), guests (quick RSVP
  recording at family gatherings — big tap targets), budget view. Seating is
  view-only on mobile v1 (note in DECISIONS).

EDGE CASES
- Sub-event date change → recompute checklist due dates (offset preserved),
  notify assignees of shifted overdue items.
- Guest deleted with seat + RSVPs → cascade cleanly, feed entry.
- Two members editing seating → last-write-wins per seat with realtime
  presence warning (WebSocket room per plan).
- Import 900-row CSV with dupes/bad emails → row-level error report,
  partial import allowed.
- Member removed mid-plan → their assignments unassigned, invites revoked.

ACCEPTANCE CRITERIA
[ ] Create South Asian wedding template → 5 sub-events, checklist spans
    correctly from the primary date.
[ ] Confirmed booking auto-fills budget actuals + completes matching task.
[ ] 500-guest import, invite matrix, RSVP grid all under 2s interactions.
[ ] Seating drag-drop synced to RSVP changes; PDF export prints clean.
[ ] PLANNER role can do everything except delete event / see payment cards.
[ ] Activity feed captures every meaningful mutation with actor attribution.
```

---

# PHASE 9 — EVENT WEBSITES, PUBLIC RSVP & E-INVITE STUDIO
**Goal:** every event gets a beautiful public site + bilingual e-invites — the viral guest-facing surface. **Est: 6–8 days.**

### PROMPT 9
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Build the guest-facing layer: a template-based event website builder with
public RSVP tied to the Phase 8 guest list, a card e-invite studio with
Devanagari + Latin typography, a template-based video invite renderer, and
guest messaging. Every guest touchpoint carries tasteful [wedjan]
branding → organic growth loop.

1) EVENT SITE BUILDER
- Schema: event_sites(id, event_id UNIQUE, slug UNIQUE, template_key, theme
  jsonb {palette, fontPair, motif}, password_hash NULLABLE, is_published,
  noindex bool DEFAULT true, og_image_media_id, custom_domain NULLABLE
  (schema only, wiring later), sections jsonb ordered array), site_visits
  (site_id, day, count) aggregated.
- 10 launch templates on ui-tokens (names/dirs): Temple Dusk (Devanagari-first
  bilingual), Modern Editorial, Botanical, Royal Heritage, Minimal Ink,
  Coastal, Midnight Lux, Festival Pop, Corporate Clean, Garden Party. Each =
  config of tokens + section variants, NOT separate codebases.
- Section types: HERO (names, date, countdown), STORY, SCHEDULE (auto from
  sub_events with per-guest visibility respecting guest_invites), TRAVEL &
  STAY, GALLERY (from event media), REGISTRY_LINKS (external URLs only v1),
  RSVP, FAQ, FOOTER. Drag-reorder, per-section show/hide, inline text editing
  with live preview (desktop/mobile toggle).
- Publish flow: slug picker with availability check, optional password,
  QR code generator (download PNG/SVG for printed cards).
- Public render: /{slug} on an edge-cached route, ISR on content change,
  password gate (signed cookie 30d), OG image auto-render (names+date+theme).

2) PUBLIC RSVP FLOW (ties to real guest list)
- Guest lookup: name search with trigram fuzzy + email fallback; household
  match shows all group members ("Responding for the Sharma family").
- Per-sub-event responses ONLY for sub-events the guest is invited to;
  dietary multi-select + free note; plus-one enablement per guest flag; song
  request if section enabled.
- Unknown guest submits → pending_rsvp_requests queue; owner approves →
  creates guest + RSVPs (anti-gatecrash control, notify owner).
- Edit window: guests can update until rsvp_lock_at (default T-14d, owner
  configurable). Confirmation email to guest with .ics calendar files per
  sub-event.
- Rate limiting + captcha (Turnstile) on public endpoints.

3) E-INVITE CARD STUDIO
- Schema: invite_designs(id, event_id, kind [CARD|VIDEO], template_key,
  payload jsonb (text layers, colors, media refs), render_media_id, status
  [DRAFT|RENDERING|READY|FAILED])
- 12 card templates (share DNA with site templates); editor: canvas preview,
  editable text layers (names, date, venue, Nepali/Hindi + English dual-line
  support), palette swap, motif toggle, photo slot. Fonts: include 2 quality
  Devanagari faces + Latin pairs; test conjunct rendering.
- Server render: satori/resvg (or Playwright screenshot fallback) → PNG (1080
  ×1350 + 1080×1920 story) + print PDF (A5 300dpi, 3mm bleed). Async job
  queue (Redis) with progress polling.
- VIDEO invite v1: template-based slideshow (3 templates: Ken Burns photos +
  text cards + music from 5 licensed tracks) rendered via ffmpeg job → MP4
  1080×1920 ≤30s. Progress UI, retry on fail.
- Share: download, copy link (public invite page with RSVP CTA), WhatsApp
  share intent (critical for NP/AU-diaspora), Instagram story dimensions.

4) GUEST MESSAGING
- guest_messages(id, event_id, audience jsonb filters {sub_event, rsvp_status,
  group, side}, subject, body_md with merge fields {{first_name}}
  {{rsvp_link}}, channel [EMAIL] (SMS behind flag, schema ready),
  scheduled_at, sent_count, status)
- Composer with audience preview count, test-send to self, throttled sending
  (batch 50/min via queue), per-guest send log, bounce handling marks guest
  email invalid. Reminder presets: "RSVP nudge to PENDING", "Week-of details
  to YES". Compliance: transactional framing, event-owner as reply-to.

5) MOBILE
- Site editor = simplified (theme, text, publish); full section editing web-
  only v1. E-invite studio: full parity (this is a mobile-first behavior),
  native share sheet. RSVP public page is responsive web (no app needed).

EDGE CASES
- Two guests same name in lookup → disambiguate by group/partial email hint
  (mask: s•••@gmail.com).
- Site unpublished while guests hold links → friendly "paused" page.
- Devanagari text overflowing layers → auto text-fit with min size + warning.
- Video render >5 min → timeout, requeue once, then FAILED with support CTA.
- RSVP after lock → read-only view + "contact the hosts" mailto.

ACCEPTANCE CRITERIA
[ ] Publish site in <60s from template; Lighthouse perf ≥90 on public page.
[ ] RSVP round-trip updates Phase 8 grid + seating availability instantly.
[ ] Bilingual card renders pixel-perfect Devanagari (visual snapshot tests).
[ ] Video invite renders ≤90s p95 and plays on iOS/Android/WhatsApp.
[ ] Unknown-guest approval flow works; captcha blocks scripted RSVP spam.
[ ] Guest blast to 500 emails completes with per-guest logs, zero dupes.
```

---

# PHASE 10 — AI LAYER: CUSTOMER COPILOT, VENDOR ASSISTANT & STYLE MATCHING
**Goal:** the intelligence moat — grounded AI that plans, matches, and drafts, with hard boundaries. **Est: 7–9 days.**

### PROMPT 10
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Build a provider-agnostic AI layer with three surfaces: (1) customer planning
copilot inside the event workspace, (2) vendor assistant inside the inbox/CRM,
(3) pgvector style matching powering quiz-based and visual discovery.
Non-negotiable boundaries: AI never auto-books, never auto-sends, and only
ever names vendors returned by real DB queries (tool-grounded). Logistics
automated; taste and emotion stay human — present options, not verdicts.

1) AI GATEWAY (apps/api module `ai`)
- Interface AiGateway {complete(promptKey, vars, tools), embedText(text),
  embedImageCaption(mediaId)} — Claude via API primary; model + params per
  prompt template row.
- prompt_templates(key, version, model, system_md, temperature, max_tokens,
  is_active) — versioned, editable via admin later; NEVER accept system
  prompts from clients; user content always injected as data with delimiter
  hygiene (prompt-injection defenses documented in docs/ai-safety.md).
- ai_usage(id, account_id, surface, prompt_key, input_tokens, output_tokens,
  cost_micros, latency_ms, created_at) + daily per-account quotas from
  app_config (customer 40 msgs/day, vendor 60; friendly limit UX).
- PII hygiene: scrub emails/phones from logs; store conversation transcripts
  ai_threads/ai_messages scoped per account with 90d retention job.
- Evals harness: fixtures/ai-evals/*.yaml (input → expected tool calls /
  rubric), `pnpm ai:eval` runs against gateway in CI (recorded/replayed).

2) CUSTOMER COPILOT (tab in Event Workspace + floating entry on search)
- Chat UI (web + mobile) with streaming, suggested starter chips ("Build my
  budget", "What should I do this month?", "Find photographers that match my
  vibe", "Draft a message to this vendor").
- Tool set (server-executed, results rendered as rich cards):
  · generate_budget(event_id) → calls Phase 8 estimate engine, returns
    editable draft; APPLY button writes categories (confirm dialog).
  · generate_checklist(event_id) → gap-fills against existing items, never
    duplicates; APPLY adds selected.
  · suggest_vendors(criteria) → Phase 3 search + merit + style vector blend;
    card list with reason strings ("matches your candid/documentary quiz,
    4.9★, ~AUD 2,800, free on 14 Nov"); actions: save to shortlist, start
    inquiry (prefilled composer — user sends).
  · draft_message(vendor_id, intent) → inserts into composer, never sends.
  · answer_planning_question(q) → etiquette/logistics answers grounded in a
    curated knowledge markdown pack (versioned in repo), cites section.
- Boundary enforcement in code: tool allowlist per surface; any tool that
  mutates requires explicit user confirmation UI event; aesthetic questions
  answered as 2–3 option framings with tradeoffs, template-enforced.

3) STYLE MATCHING (pgvector)
- Migrations: vendor_profiles.style_embedding vector(1024),
  showcase_media.style_embedding vector(1024), accounts_style(account_id,
  embedding vector(1024), quiz_answers jsonb, updated_at). IVFFlat indexes.
- Ingestion jobs: caption gallery/showcase images via vision model →
  style_tags + caption text; embed caption+tags+about into vendor embedding
  (mean of top media + profile text, weighted). Backfill command + on-upload
  hook (queue, batched).
- Style quiz (customer, 12 steps): pick 1 of 3 images per step (curated seed
  set tagged by style axes: candid↔posed, moody↔bright, traditional↔modern,
  minimal↔opulent…) → synthesize preference text → embed → store.
- Endpoints: GET /match/vendors?event_id&category → cosine top-N blended
  score = 0.5*style_sim + 0.35*merit + 0.15*price_fit(budget band);
  GET /showcase-media/{id}/similar → visual "more like this".
- UI: quiz entry after event creation + from copilot; "Matched to your style"
  badges with a WHY popover (top shared tags) — explainability required.

4) VENDOR ASSISTANT (inbox drawer + review replies)
- Tools: summarize_lead(thread) → bullet brief + priority score + suggested
  next step; draft_quote(lead_id) → line items from best-fit package +
  lead specifics, opens quote builder prefilled; draft_followup(thread,
  tone[WARM|PROFESSIONAL|BRIEF]); draft_review_reply(review_id) respecting
  guidelines (thank, address, invite offline for negatives).
- Insert-only pattern everywhere: assistant output lands in an editable
  composer with visible "AI draft" chip; send is always human.
- Weekly AI digest email per vendor: leads at risk, suggested price insight
  (your median vs city median), one profile improvement tip.

5) DEGRADATION & SAFETY
- Provider outage → surfaces show cached suggestions + "AI is resting" state;
  core flows unaffected (AI is additive, never load-bearing).
- Jailbreak/off-domain asks → polite scope redirect template.
- Log-and-alert on tool errors; hallucination guard: any vendor/package
  mentioned must originate from tool JSON — renderer refuses free-text names.

ACCEPTANCE CRITERIA
[ ] Copilot builds budget + checklist + shortlist for a fixture event with
    all APPLY confirmations, zero direct writes without confirm.
[ ] suggest_vendors returns only DB-real vendors (fuzz eval proves it).
[ ] Style quiz measurably reorders results (fixture: candid vs traditional
    profiles swap top slot per persona).
[ ] Vendor drafts insert into composers; nothing auto-sends (audit test).
[ ] Quotas enforce; usage + cost logged per call; evals pass in CI.
[ ] Kill AI provider key in staging → platform fully usable, graceful states.
```

---

# PHASE 11 — VENDOR OS: CRM, CONTRACTS, E-SIGN, ANALYTICS & SUBSCRIPTIONS
**Goal:** vendors run their whole business on you — pipeline, paper, and proof of ROI. Lock-in via love. **Est: 7–9 days.**

### PROMPT 11
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Build the Vendor OS on top of leads/bookings: Kanban pipeline, tasks, notes,
contract templates with e-signature, custom invoices, automations with quiet
hours, 2-way Google Calendar sync, team seats, the transparency analytics
panel ("why you rank here" — the anti-Knot feature), and a FREE vs PRO
subscription on Stripe Billing.

1) PIPELINE CRM
- Schema: deals(id, vendor_id, lead_id NULLABLE, booking_id NULLABLE, title,
  value_cents estimate, stage [NEW|QUOTED|NEGOTIATING|BOOKED|COMPLETED|LOST],
  lost_reason [PRICE|DATE|GHOSTED|CHOSE_OTHER|OTHER], stage_changed_at,
  position), deal_tasks(id, deal_id, title, due_at, done_at, assignee),
  deal_notes(id, deal_id, author, body_md).
- Auto-create deal on new lead; auto-advance on quote sent / booking
  confirmed / completed; manual drag allowed with rules (can't drag to BOOKED
  without booking). Kanban board (dnd), list view, filters, quick actions.

2) CONTRACTS & E-SIGN
- contract_templates(id, vendor_id NULLABLE null=platform defaults, title,
  body_md with merge fields {{customer_name}} {{event_date}} {{package_title}}
  {{total}} {{deposit}} {{cancellation_policy}} …, version) — ship 3 platform
  defaults (photography, catering, venue) reviewed for plain-language.
- contracts(id, booking_id, template_version_snap, rendered_html_snap,
  status [DRAFT|SENT|VIEWED|SIGNED|COUNTERSIGNED|VOID], sent_at, viewed_at)
- signatures(contract_id, party [CUSTOMER|VENDOR], name_typed, signature_svg,
  ip, user_agent, signed_at, doc_sha256) — on countersign: freeze PDF
  snapshot (media), email both, attach to booking detail. Legal note page:
  e-sign act references (ESIGN/UETA, AU ETA) — informational, not legal advice.
- Flow: vendor generates from booking → edits merge output → SEND → customer
  signs via booking page (view tracking) → vendor countersigns. VOID +
  re-issue creates v2 linked.

3) CUSTOM INVOICES (off-platform clients allowed — adoption wedge)
- custom_invoices(id, vendor_id, client_name, client_email, line_items jsonb,
  currency, total_cents, due_at, status [DRAFT|SENT|PAID|OVERDUE|VOID],
  stripe_checkout_id NULLABLE, is_off_platform bool) — payment via Stripe
  Checkout to vendor's connected account (standard rails, NO escrow), pages
  clearly labeled "Not covered by [wedjan] booking protection".
  Purpose: pulls the vendor's whole book of business into the OS.

4) AUTOMATIONS (PRO)
- automations(id, vendor_id, trigger [LEAD_CREATED|QUOTE_SENT_NO_REPLY_48H|
  BOOKING_CONFIRMED|EVENT_T_MINUS_7D|COMPLETED], action [SEND_SAVED_REPLY|
  CREATE_TASK|SEND_EMAIL_TEMPLATE], config jsonb, is_active, quiet_hours
  {start,end,tz} default 21:00–08:00 vendor tz, last_run_at) — engine on the
  queue with per-automation run log; loop protection (max 1 auto-message per
  conversation per 24h; never auto-message after customer said BOOKED_ELSEWHERE).

5) ANALYTICS & THE TRANSPARENCY PANEL
- Dashboard: funnel (profile views → leads → quotes → bookings) with period
  compare; revenue chart (escrowed/released split); response-time trend vs
  city median; lead source breakdown; package performance table (views,
  conversion, suggest re-price hint if 2σ off city median).
- "Why you rank here": render Phase 7 merit components as progress bars vs
  city p50/p90 with plain-language tips ("Reply 3h faster to enter the top
  10"). Public pledge link. This screen is a screenshot-marketing asset —
  design it beautifully.

6) TEAM & CALENDAR
- vendor_team_members(vendor_id, account_id, role [OWNER|MANAGER|STAFF],
  permissions matrix: STAFF no payouts/pricing) — invites by email; audit.
- Google Calendar 2-way (OAuth): confirmed bookings pushed as events; busy
  blocks pulled hourly into availability exceptions (source=GOOGLE); channel
  watch renewal job; disconnect cleanly.

7) SUBSCRIPTIONS (Stripe Billing)
- Plans: FREE (marketplace, inbox, bookings, basic CRM) / PRO 29 USD-eq per
  month regional pricing table (AUD 45, GBP 24, NPR 3,900): automations,
  contracts+e-sign, custom invoices, advanced analytics, team seats >1,
  calendar sync. 14-day trial, no card for trial. subscription state synced
  by webhooks; downgrade → PRO features read-only (never delete data),
  automations paused with banner.
- Upgrade touchpoints: feature-gate modals with concrete value copy; usage
  nudges ("You replied to 12 leads manually this week — automate it").

EDGE CASES
- Contract edited after SENT → must VOID + re-issue (immutability).
- Team member removed → their tasks reassigned to OWNER, sessions revoked.
- Google token expiry → degrade with reconnect banner, never silent.
- Trial end with automations on → pause, don't fire, notify.
- Invoice to email that bounces → status flag + resend flow.

ACCEPTANCE CRITERIA
[ ] Lead → deal → quote → contract signed both parties → booking confirmed →
    completed reflects across pipeline, analytics, calendar without manual sync.
[ ] Countersigned PDF hash-verifies; tamper test fails loudly.
[ ] Automations respect quiet hours + loop caps (simulated clock tests).
[ ] Free vendor hits every PRO gate correctly; upgrade → instant unlock;
    downgrade keeps data read-only.
[ ] Transparency panel numbers reconcile exactly with Phase 7 merit rows.
[ ] Off-platform invoice pays out via Connect with correct "no protection" labeling.
```

---

# PHASE 12 — FREELANCER/GIG LAYER: SHIFTS, CREW & GIG PAY
**Goal:** the moat nobody has — vendors staff their gigs and customers book micro-services inside the same graph. **Est: 8–10 days.**

### PROMPT 12
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Build the third side: freelancer profiles with verification and reliability
scores, shift posting by vendors (and micro-booking by customers), matching
feed, QR/geo check-in, escrowed gig pay with fast payouts, both-way ratings,
and crew rosters. Reliability is the entire product here — optimize for show
rate above all.

1) FREELANCER PROFILES
- freelancer_profiles(account_id PK, headline, bio, base_city, country, lat,
  lng, radius_km, hourly_rate_cents NULLABLE, day_rate_cents NULLABLE,
  currency, years_experience, languages text[], transport [NONE|OWN_VEHICLE|
  PUBLIC], id_verification_status [UNVERIFIED|PENDING|VERIFIED],
  reliability_score numeric, show_rate numeric, shifts_completed int,
  status [DRAFT|ACTIVE|PAUSED|SUSPENDED])
- skills taxonomy seed (skill groups → skills): PHOTO/VIDEO(second shooter,
  photo editor, videographer assistant, drone operator, photobooth attendant);
  FOOD&BEV(server, bartender, barista, cook, kitchen hand, catering
  assistant); EVENT OPS(setup/teardown crew, coordinator assistant, usher,
  registration desk, runner, cleaner, security*license-flag); TECH(AV tech,
  lighting tech, sound engineer, DJ assistant, LED wall op); CREATIVE(MC/host,
  mehndi artist, makeup assistant, hair assistant, decorator assistant,
  florist assistant); LOGISTICS(driver*license-flag, equipment handler).
- freelancer_skills(freelancer_id, skill_id, years, is_primary),
  freelancer_certs(id, type [RSA|FOOD_SAFETY|SECURITY_LICENSE|DRIVER_LICENSE|
  FIRST_AID|OTHER], media_id, expires_at, status reviewed like Phase 2 docs),
  portfolio via media galleries. License-flagged skills require matching
  APPROVED cert before applying to those shifts.
- Availability: reuse Phase 4 rules/exceptions engine (mode DATE) per
  freelancer.

2) SHIFTS
- shifts(id, poster_type [VENDOR|CUSTOMER], poster_id, booking_id NULLABLE
  ("Staff this event" prefill), skill_id, title, date, start_time, end_time,
  break_minutes unpaid, location fields + lat/lng, pay_type [HOURLY|FLAT],
  pay_offer_cents, currency, headcount, filled_count, requirements_md,
  dress_code, parking_note, status [DRAFT|POSTED|PARTIALLY_FILLED|FILLED|
  IN_PROGRESS|COMPLETED|CANCELLED|EXPIRED], visibility [PUBLIC|ROSTER_ONLY],
  auto_accept_roster bool)
- shift_applications(id, shift_id, freelancer_id, note, status [APPLIED|
  OFFERED|ACCEPTED|DECLINED|WITHDRAWN|EXPIRED]) — poster reviews cards
  (rate, distance, show_rate, badges) and OFFERs; freelancer accepts within
  4h hold. ROSTER_ONLY + auto_accept → instant claim by rostered members.
- shift_assignments(id, shift_id, freelancer_id UNIQUE per shift, status
  [ASSIGNED|CHECKED_IN|COMPLETED|NO_SHOW|CANCELLED_BY_FREELANCER|
  CANCELLED_BY_POSTER], checkin_method [QR|GEO|MANUAL], checkin_at,
  checkout_at, actual_minutes, adjustment_cents signed, adjustment_note)
- Pay floor: min_pay_floor per country in app_config (e.g. AU respects award
  guidance value; NP set sensible floor) — posting below floor blocked with
  explanation. Overlap guard: freelancer can't hold two assignments with
  overlapping tstzrange (exclusion constraint).
- Standby list: applicants beyond headcount opt-in as standby; no-show →
  auto-broadcast to standby (push+SMS-flag) in rank order, 20-min claim
  windows.

3) GIG MONEY (extends Phase 5 rails)
- Funding: on each assignment ACCEPTED → PaymentIntent charges poster
  estimated total (pay + platform markup, config gig_markup_pct default 15%,
  poster-side; freelancer sees exact take-home BEFORE applying — Law-level
  transparency). Vendor posters may use saved card; customers pay in flow.
- Completion math: checkout time → actual_minutes (min billable = 50% of
  scheduled or actual, whichever higher); poster has 12h to dispute/adjust
  (±, with note, freelancer must approve increases in hours? no — approve
  DEDUCTIONS; increases auto-ok) → capture/adjust and schedule payout
  transfer at +48h. Ledger entries for every leg.
- Cancellation comp: poster cancels <24h → freelancer gets 50% (config);
  <4h → 100%. Freelancer cancels <24h → reliability hit + strike.
- No-show: freelancer NO_SHOW → full refund of that headcount to poster,
  reliability −25pts, 2nd no-show in 90d → suspension review.
- Payouts: freelancers onboard Stripe Express (reuse Phase 5 module),
  earnings screen with weekly summary; payout p95 ≤72h from checkout.

4) CHECK-IN & DAY-OF
- Poster shift screen generates rotating QR (TOTP-based, 60s window);
  freelancer scans in-app → CHECKED_IN; geo fallback: allow self check-in
  within 300m of venue ±15min of start; MANUAL by poster with reason.
  Checkout mirrors. Late alerts: T+10min not checked in → nudge freelancer +
  warn poster + surface standby button.

5) RELIABILITY & RATINGS
- Both-way post-shift ratings (1–5 + tags: punctual, skilled, great attitude /
  clear brief, safe environment, paid fairly). reliability_score =
  0.6*show_rate + 0.3*avg_rating_norm + 0.1*recency; tier badges
  [NEW|RELIABLE|ELITE(≥98% show, ≥4.9, ≥20 shifts)]. Elite unlocks
  auto_accept eligibility and feed priority.
- crew_rosters(id, owner poster, name) + members — one-tap rebook ("Book my
  Saturday crew" duplicates last crew across a new date, individual accepts).

6) UI
- Freelancer (MOBILE-FIRST): discover tab (map + list, filters skill/date/
  distance/pay), shift detail with take-home breakdown, apply/claim, My
  Shifts (upcoming with check-in button state machine, history), earnings,
  profile editor, availability calendar.
- Vendor web+mobile: "Crew" tab — post wizard (from booking or blank),
  applicant review, day-of live board (who's in, late, standby), rosters.
- Customer micro-booking: /hire/{skill-slug} simple flow (bartender for a
  house party) using same shift rails, capped headcount 5.
- Compliance surfaces: region-aware contractor status explainer, tax detail
  collection (ABN field AU, W-9 placeholder US, PAN NP), insurance
  disclosure checkbox at posting, terms per region in docs/gig-compliance.md
  (mark clearly: informational, obtain local counsel before scale).

EDGE CASES
- Shift spans midnight → tstzrange handles; pay math on minutes not dates.
- Poster edits time after fills → all assignees must re-confirm; declines
  release spots to standby.
- QR screenshot replay → TOTP window + one-time nonce per scan.
- Freelancer device offline at venue → geo/manual paths; sync on reconnect.
- Currency: shift currency = poster currency; freelancer feed shows native +
  approx home-currency.

ACCEPTANCE CRITERIA
[ ] Vendor staffs a real booking with 3 servers end-to-end: post → offers →
    accepts → funding → QR check-in/out → 48h payouts, ledger balanced.
[ ] No-show path: refund + standby broadcast + reliability drop verified.
[ ] Overlap constraint blocks double-booking a freelancer (concurrency test).
[ ] Take-home shown pre-apply equals payout received to the cent.
[ ] License-gated skill (security) blocks uncertified applications.
[ ] Micro-booking by a customer works with capped scope.
```

---

# PHASE 13 — MOBILE APPS: FULL NATIVE COMPLETION & STORE READINESS
**Goal:** one polished app, three roles, push + deep links + offline — ready for App Store and Play. **Est: 7–9 days.**

### PROMPT 13
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Consolidate everything built in Phases 1–12 into a store-ready single mobile
app with role-aware navigation (Customer / Vendor / Freelancer switcher —
justify single-app in DECISIONS: shared identity, cross-role users, one
listing, shared code), full push notification system, universal/deep links,
offline resilience, native polish, and complete store submission assets.

1) NAVIGATION CONSOLIDATION
- Role switcher (persisted) drives tab sets:
  CUSTOMER: Home(events) · Explore(search+inspiration) · Inbox · Bookings ·
  Profile.  VENDOR: Dashboard · Inbox(SLA badges) · Calendar · Crew · More
  (packages, showcases, earnings, OS).  FREELANCER: Shifts · My Shifts ·
  Earnings · Inbox · Profile.
- Audit EVERY flow from P1–P12 for mobile parity or an intentional
  view-only/web-handoff decision logged in DECISIONS (e.g. seating editor,
  full site builder → web handoff cards with QR/link).

2) PUSH NOTIFICATIONS (end-to-end)
- push_tokens(account_id, expo_token, device_id, platform, last_seen_at);
  register on login, prune invalid on receipt errors.
- notification_preferences(account_id, category [LEADS|MESSAGES|BOOKINGS|
  PAYMENTS|RSVPS|SHIFTS|REVIEWS|MARKETING], push bool, email bool) — settings
  screen; MARKETING default OFF.
- Consume the Phase 6 NotificationEvents queue → fanout service (push via
  Expo, email fallback if no active token + high importance). Deep-link
  payloads for every category. Group/collapse (thread messages), badge count
  sync, quiet delivery for vendor quiet hours except SLA-critical.
- In-app notification center (bell) with read states, mirrors push history.

3) LINKS
- Universal Links/App Links + expo-router mapping: /v/{slug}, /bookings/{id},
  /inbox/{conversation}, /shifts/{id}, /e/{event}, invite accept, review
  deep links. Cold-start routing test matrix. Web fallback pages when app
  absent; smart app banners on web.

4) OFFLINE & RESILIENCE
- TanStack Query persistence (MMKV); read caches: my bookings, my shifts,
  event checklist, inbox last 50 threads.
- Outbox pattern: messages + checklist toggles + RSVP recordings queue
  offline, replay with idempotency keys, conflict toasts on version clash.
- Media upload manager: background-capable queue, compression (images ≤2MB
  target), retry with backoff, gallery multi-select.

5) NATIVE POLISH PASS (checklist to execute, not skip)
- Haptics on primary confirms; skeletons on every list; empty states with
  action CTAs; error states with retry; pull-to-refresh standardized;
  FlashList for all feeds; expo-image with blurhash placeholders from
  MediaAsset; dark mode audit; dynamic type + a11y labels on interactive
  elements; keyboard avoidance on all forms; biometric app lock (opt-in)
  gating payment + earnings screens; in-app review prompt trigger: customer
  submits a 5★ review OR freelancer 10th completed shift.
- Performance budget: cold start <2.5s mid-tier Android, JS bundle audit,
  Hermes, screen TTI traces via Sentry.

6) RELEASE ENGINEERING
- Sentry (errors + performance) with source maps; analytics events parity
  with web (single taxonomy doc docs/analytics-events.md — create it,
  instrument top 40 events).
- EAS: dev/preview/production profiles; env injection; OTA updates policy:
  runtimeVersion per native change, staged rollout 10→50→100%, forced-update
  gate via min_supported_version config + blocking screen.
- Store assets: app icon adaptive set, splash, 6.7"/6.1"/iPad + Android
  phone/tablet screenshot set (generate via Maestro flows on themed demo
  data), preview video script, store descriptions (write them: keyword-aware,
  three-sided value), iOS privacy manifest + nutrition labels, Play Data
  Safety form answers, content ratings, account-deletion URL (required),
  demo reviewer account seed + review notes doc.

EDGE CASES
- Role added after install → switcher appears with coach mark.
- Push token rotates → dedupe by device_id.
- Deep link to entity user lacks permission for → graceful gate screen.
- OTA update mid-session → apply on next foreground, never mid-flow.
- Notification tapped for deleted entity → friendly not-found + home.

ACCEPTANCE CRITERIA
[ ] Golden paths (booking, gig, RSVP record, quote accept) pass on physical
    iOS + Android via Maestro suite.
[ ] Every notification category delivers, deep-links, and respects prefs.
[ ] Airplane-mode test: message + checklist edits queue and sync cleanly.
[ ] Cold start <2.5s on mid-tier Android; zero crash-free-session regressions.
[ ] TestFlight + Play internal track builds submitted with complete metadata;
    reviewer account works end-to-end.
```

---

# PHASE 14 — ADMIN PANEL, OPERATIONS, MODERATION & FRAUD
**Goal:** the platform can be run, moderated, and defended by a small ops team. **Est: 7–9 days.**

### PROMPT 14
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Build apps/admin (separate Next.js app, ADMIN role + TOTP 2FA + IP allowlist
config) covering: KPI dashboard, user/vendor management with audited
impersonation, verification queues, booking/payment console with ledger,
dispute resolution workspace, moderation queues with a strike system,
taxonomy/config/flag editors, announcements, fraud tooling, and GDPR/APP
compliance jobs. Every admin action writes audit_log with before/after.

1) ACCESS & SHELL
- Admin login = platform account with ADMIN role + mandatory TOTP (enroll
  flow, backup codes); session 8h; IP allowlist from app_config (bypass flag
  for break-glass with alert). Sidebar modules below; global search
  (accounts, vendors, bookings by code, emails).

2) KPI DASHBOARD (the numbers that matter)
- Liquidity: % leads first-reply <24h, lead→booking conversion, median
  time-to-book, shift fill rate, no-show rate. Money: GMV, take revenue,
  escrow balance, refund rate, payout lag p95. Growth: WAU by role, new
  verified vendors/week, city×category coverage heatmap (supply gaps
  highlighted red — your expansion map). Trust: dispute rate, review rate,
  fraud flags open. Date ranges + city filter; every number links to a
  drill-down list.

3) USERS & VENDORS
- Account detail: roles, sessions (revoke), risk score, timeline of key
  events; actions suspend/unsuspend with reason enums + templated email;
  impersonation: consent-based (support ticket ref required) OR read-only
  ghost mode default — banner shown, every request tagged impersonator_id,
  full audit.
- Vendor console: profile + merit components, listing strength history,
  penalty tools (merit clamp with expiry, badge revoke), verification
  re-request.

4) VERIFICATION QUEUES (upgrade Phase 2 stub)
- Unified queue (vendor docs, freelancer IDs, certs) with SLA timers,
  claim-assignment (avoid double review), doc viewer with zoom + previous
  submissions side-by-side, approve/reject with reason templates, daily
  throughput stats per reviewer.

5) BOOKINGS, PAYMENTS & DISPUTES
- Booking console: full state timeline, ledger view (Phase 5), linked
  conversation read-only, force-transition tools (guarded, reason required),
  manual refund with policy-override justification, webhook replay per booking.
- Dispute workspace: dispute cases table (booking or gig), evidence timeline
  auto-assembled (messages, media, contract PDF, policy calculation, check-in
  records for gigs), internal notes, resolution templates (full refund /
  split / release with goodwill credit), execute via Phase 5/12 split APIs,
  outcome letters emailed both parties, SLA 5 business days with aging alerts.

6) MODERATION & STRIKES
- Queues: reported messages, reviews pending (auto-held), showcase media
  flags, public-site content flags. Actions: approve, remove (tombstone),
  warn (templated), strike. strikes(account_id, reason, weight, expires_at
  365d): weight sum ≥3 → auto-suspend pending human confirm. Repeat-offender
  view. Shadow-ban tool (content visible to author only) for spam patterns.
- Image safety hook: interface for a moderation model on upload (stub
  provider now, wire config later); nudity/violence auto-hold.

7) FRAUD & RISK
- Signup risk scoring job: disposable email domains list, velocity per
  IP/device hash, geo mismatch → risk_score on account; high risk → manual
  review gate before vendor/freelancer activation.
- Duplicate detection: shared device fingerprint hash, payment fingerprint,
  bank account collisions → linkage graph view (simple table + connections).
- Payment anomalies: refund-rate spikes, card-testing patterns (many small
  failures), payout right after first booking → alerts queue.
- Review-fraud queue from Phase 7 flags with one-click void + merit recompute.
- Collusion heuristic: same-IP customer/vendor booking + review → flag.

8) PLATFORM CONFIG & COMMS
- Taxonomy manager (categories, skills, cert types) with soft-archive rules
  (block archive when active listings exist). Config/flag editor with typed
  schemas, change audit + rollback. Fee tables editor (take rate, gig
  markup, pay floors) with effective-date scheduling.
- Announcements: targeted banners/in-app messages (audience: role, city,
  plan) with schedule + dismissal tracking.

9) COMPLIANCE JOBS
- Data export: user requests → async job zips their data (account, bookings,
  messages, media links) → secure download email (expires 7d).
- Deletion: soft-delete + anonymization strategy (bookings/ledger retained
  with anonymized party per finance retention; reviews → "Verified customer";
  media purged) documented in docs/data-retention.md; admin-triggered and
  self-serve (settings) both route through same job.
- Consent log for ToS/privacy versions; DSAR request tracker.

ACCEPTANCE CRITERIA
[ ] Every destructive admin action requires reason + lands in audit_log with
    before/after snapshots (spot-check tests).
[ ] Dispute case resolves with split payout executing correctly in test mode.
[ ] Strike accumulation auto-suspends and notifies; appeal path documented.
[ ] Impersonation is fully audited; ghost mode cannot mutate (API-level test).
[ ] Risk gate blocks a fixture disposable-email vendor from activating.
[ ] Export + deletion jobs run against a seeded account and pass a manual
    verification checklist.
```

---

# PHASE 15 — HARDENING, PERFORMANCE, i18n, SEO/AEO FINAL & LAUNCH OPS
**Goal:** production-grade everything, then a controlled go-live in the first city corridor. **Est: 7–10 days.**

### PROMPT 15
```
[PASTE MASTER CONTEXT]

OBJECTIVE
Final hardening across testing, security, performance, internationalization,
search/AI discoverability, observability, and launch operations. Output is a
signed go-live: green E2E matrix, load-tested hot paths, restore-drilled
backups, monitored SLOs, submitted store builds, and a seeded supply plan.

1) E2E MATRIX (Playwright web + Maestro mobile) — all must be CI-green:
  1 Customer signup→search→instant book→pay→complete→review
  2 Request booking→quote in chat→accept→deposit→auto-balance→complete
  3 Cancellation each policy tier → refund math verified
  4 Dispute raise→admin split resolution→ledger balanced
  5 Vendor onboarding→verification→first lead SLA→reply
  6 Showcase publish→vendor tag accept→appears in inspiration + profile
  7 Event workspace: template→budget generate→booking wires actuals→RSVP→seating
  8 Event site publish→public RSVP (known + unknown guest)→e-invite render
  9 Copilot: budget+shortlist+draft with confirmations (recorded AI)
  10 Vendor OS: deal→contract e-sign both→automation fires in window
  11 Gig: post→fill→QR check-in/out→payout; no-show→standby path
  12 Push: each category delivers + deep-links (device farm or local matrix)

2) LOAD & CHAOS (k6 + scripts, run against staging)
- search 200 RPS p95<400ms · availability lookups 300 RPS p95<250ms ·
  checkout 50 RPS zero double-bookings · webhook burst 500/min no loss ·
  chat fanout 2k concurrent sockets. Chaos: kill API pod mid-payment (replay
  clean), Redis flush (graceful degrade), Stripe webhook replay storm
  (dedupe holds), DB failover drill notes.

3) SECURITY PASS
- OWASP ASVS L2 checklist sweep with fixes; dependency audit gate in CI
  (fail on high CVEs); authz fuzz suite (every endpoint × wrong-role matrix
  auto-generated from OpenAPI); rate-limit audit incl. public RSVP + search;
  secrets rotation runbook + rotate now; upload pipeline re-encode verified
  (polyglot file tests); security.txt; pen-test scope doc prepared for an
  external tester (book one before paid ads).

4) DATA SAFETY
- Postgres PITR configured (RPO 15m), nightly logical dumps to second
  provider, media bucket versioning; EXECUTE a restore drill into a fresh
  environment and record RTO (target ≤2h) in docs/dr-runbook.md.

5) PERFORMANCE & COST
- Web: Lighthouse CI budgets on 6 key pages (home, search, vendor, booking,
  event site, workspace): LCP<2.5s, INP<200ms, CLS<0.1 on throttled mobile;
  image CDN transforms; route-level bundle analysis, kill >150KB offenders.
- API: top-20 query EXPLAIN audit + missing index fixes; N+1 sweep; Redis
  cache layer for availability months, merit scores, search facets, landing
  stats (with invalidation events); connection pool tuning documented.
- Cost guardrails: AI spend alert thresholds, media storage lifecycle rules,
  Railway/Vercel budget alarms.

6) i18n & LOCALIZATION SCAFFOLD
- next-intl (web) + i18n-js (mobile): externalize ALL strings; ship en
  complete + ne (Nepali) for the top 200 customer-facing strings (auth,
  search, booking, RSVP, invites); locale switcher; currency + date
  formatting via Intl everywhere; timezone correctness audit (event-local vs
  UTC) with fixture tests across Kathmandu(+05:45!)/Melbourne/London;
  pseudo-locale build to catch hardcoded strings.

7) SEO/AEO FINAL
- Sitemap coverage report ≥98% of public entities; canonical + robots audit;
  JSON-LD validation as CI step; thin-page guard (landing pages under
  content threshold get noindex until ≥3 vendors); llms.txt finalized +
  /about/data page describing entities for AI engines; structured FAQ
  coverage ≥90% of category pages; OG image render checks; Search Console +
  Bing/IndexNow wired.

8) OBSERVABILITY & SLOs
- OpenTelemetry traces (api+web) → dashboard set: API p95 by route, error
  rate, DB pool, queue depth/lag, webhook lag, payment success %, socket
  count, AI cost/day, crash-free sessions. Alerts→Slack: p95>500ms 10m,
  5xx>0.5%, webhook lag>60s, payment success<97%, escrow reconciliation
  mismatch, queue depth>1k, cert/domain expiry. Runbook per alert in
  docs/runbooks/. Public status page (simple) wired to healthchecks.

9) LAUNCH OPERATIONS
- Environment promotion checklist (envs, keys, Stripe live-mode verification
  with a real $1 booking + refund, webhook endpoints, DNS, email domain
  warm-up/DMARC).
- SUPPLY SEEDING PLAYBOOK (write it): corridor = Kathmandu + Melbourne;
  first 50 vendors photographer/videographer-led via founder network;
  white-glove onboarding script (we build your profile in a 30-min call),
  founding-vendor offer (0% take rate 90 days + FOUNDING badge, PRO free 6
  months), 20 showcases pre-loaded before public launch so inspiration feed
  is alive on day one; freelancer seeding: 30 second-shooters/assistants
  recruited from vendor referrals.
- Soft-launch plan: 2-week private beta (invite links, feedback widget,
  weekly triage), public launch gate checklist (liquidity: ≥70% leads
  replied<24h in beta, zero P0s for 7 days), store release coordination,
  day-1/-7 on-call rota, incident severity matrix + comms templates,
  rollback plan (web/app/api independently), post-launch KPI review cadence
  (weekly: liquidity, GMV, NPS-lite).

ACCEPTANCE CRITERIA
[ ] Full E2E matrix green 3 consecutive CI runs; load targets met with report.
[ ] Restore drill executed with recorded RTO ≤2h; alerts fire in game-day test.
[ ] Authz fuzz: zero cross-role leaks; CVE gate clean.
[ ] ne locale renders correctly incl. Devanagari across auth→booking→RSVP.
[ ] Live-mode $1 booking → escrow → release → payout verified then refunded.
[ ] Launch checklist signed in docs/LAUNCH.md with owner + date. GO LIVE.
```

---

# DEPENDENCY MAP & BUILD ORDER

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

**Two valid strategies:**
- **Fast revenue:** ship P1–P7, launch the corridor, then build P8–P15 live (recommended — trust + booking loop is the business; everything else compounds it).
- **Full empire first:** run all 15, launch once. Only choose this if funding runway is long — it delays real-market learning by months.

# PROGRESS TRACKER (copy into docs/PROGRESS.md)

| Phase | Status | Started | Done | Tag | Notes |
|---|---|---|---|---|---|
| 1 Foundation | ☐ | | | phase-1-complete | |
| 2 Vendor supply | ☐ | | | phase-2-complete | |
| 3 Discovery/AEO | ☐ | | | phase-3-complete | |
| 4 Booking engine | ☐ | | | phase-4-complete | |
| 5 Payments/Escrow | ☐ | | | phase-5-complete | |
| 6 Leads/Messaging | ☐ | | | phase-6-complete | |
| 7 Trust/Merit ★launch | ☐ | | | phase-7-complete | |
| 8 Event workspace | ☐ | | | phase-8-complete | |
| 9 Sites & E-invites | ☐ | | | phase-9-complete | |
| 10 AI layer | ☐ | | | phase-10-complete | |
| 11 Vendor OS | ☐ | | | phase-11-complete | |
| 12 Gig layer | ☐ | | | phase-12-complete | |
| 13 Mobile completion | ☐ | | | phase-13-complete | |
| 14 Admin & Ops | ☐ | | | phase-14-complete | |
| 15 Hardening & Launch | ☐ | | | phase-15-complete | |
