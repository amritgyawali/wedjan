# wedjan — Build Progress

Living document. Update at the end of **every** phase: flip the tracker row, then record
what exists vs. what is stubbed. Rules and workflow: [06-build-playbook.md](06-build-playbook.md).

## Phase tracker

| Phase | Status | Started | Done | Tag | Notes |
|---|---|---|---|---|---|
| 1 Foundation | ✅ | 2026-07-15 | 2026-07-15 | phase-1-complete | Merged + tagged (owner accepted). Deferred: Railway/Vercel deploys (accounts pending), mobile device run (P13) |
| 2 Vendor supply | ✅ | 2026-07-15 | 2026-07-15 | phase-2-complete | Built + locally verified on `feat/phase-2-vendor-supply`; tag after merge/owner acceptance |
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

## What exists

- **Homepage (`/`)** — finished, approved, and **frozen** (Homepage Rule / ADR-001), ported
  verbatim to `apps/web`; CI guard `scripts/check-homepage-frozen.mjs` verifies a 37-file
  SHA-256 manifest on every PR.
- **Monorepo** — pnpm workspaces + Turborepo (`apps/web`, `apps/api`, `apps/mobile`,
  `packages/shared`, `packages/ui-tokens`); hoisted node linker (ADR-004).
- **packages/shared** — `openapi.yaml` (API-first contract for all Phase 1–2 endpoints) +
  generated `openapi-fetch` typed client used by web and mobile; CI fails on stale codegen.
- **packages/ui-tokens** — brand tokens derived from the frozen homepage: TS object
  (mobile) + `--wj-*` CSS custom properties (web, ADR-007).
- **apps/api** (Spring Boot 3.4, Java 21, Gradle 8.14 wrapper):
  - Flyway `V1__core.sql`: accounts, account_roles, refresh_tokens, email_verifications,
    profiles, media_assets, app_config (+ seeded defaults incl. `take_rate_default`),
    audit_log; pgvector extension enabled.
  - Auth: signup + 6-digit OTP (Mailpit), verify-email (5-attempt lockout), login (Argon2id,
    15-min JWT with roles, timing-safe unknown-email path), rotating refresh tokens
    (httpOnly cookie web / body mobile) with family reuse detection (ADR-005), logout /
    logout-all, password reset (request + confirm, revokes all sessions), add-role
    (idempotent), GET/PATCH `/me`, sessions list + revoke.
  - Rate limiting: Redis fixed-window (5/min/IP login+signup, fail-open), proven by test.
  - Media: presign (MIME/size gates per kind) → PUT to MinIO/R2 → complete (HEAD verify,
    image dimension probe, in-repo BlurHash, idempotent; 403 on foreign asset).
  - Cross-cutting: error envelope {code,message,fieldErrors,traceId}, correlation-id filter,
    async audit writer, app_config typed accessor, dev seed (admin + 3 role accounts + 20 vendors),
    Dockerfile for Railway.
  - Tests: JwtService + BlurHash unit; Testcontainers (Postgres+Redis) integration covering
    signup→verify→login→refresh→reuse-detection→multi-role→sessions→429 rate limit.
- **apps/web** — platform shell in the homepage's visual language: `/signup` (role picker),
  `/login`, `/verify`, `/forgot-password`, `/reset-password`; authenticated `(app)` group
  with role-aware sidebar (My Events / Dashboard / Shifts), role switcher, `/dashboard`
  role-aware empty states, `/settings` (profile, currency, roles add, sessions revoke,
  logout-all). Access token in memory + auto-refresh; httpOnly refresh cookie.
- **apps/mobile** — Expo SDK 54 + expo-router: onboarding carousel (3 slides, skippable),
  (auth) login/signup/verify, (app) role-aware tabs (Home + Settings with role switcher),
  SecureStore refresh tokens, shared API client, tokens via StyleSheet (ADR-008).
- **CI/CD** — `.github/workflows/ci.yml`: homepage guard, codegen freshness, lint,
  typecheck, web build, API build + Testcontainers tests. `deploy.yml` scaffolded, gated
  behind `DEPLOY_ENABLED` repo variable + Railway/Vercel secrets (accounts not yet created).
- **Local dev** — `docker compose up -d` (Postgres16+pgvector, Redis, Mailpit :8025,
  MinIO :9001 with auto bucket) + `pnpm dev`; `.env.example` documents every key with
  working local defaults.
- **Phase 2 vendor supply** — Flyway V2 taxonomy + vendor/profile/service-area/package/add-on/
  media/document/badge/FAQ model; price required at API and database layers; published commercial
  terms versioned; submit returns precise step errors; admin document approval grants badges and
  publishes the listing while later sensitive re-review leaves it live.
- **Vendor web** — resumable seven-step `/vendor/onboarding` flow with strength meter, map/radius
  service areas, priced package live preview + add-ons, client-compressed/reorderable media,
  document status, FAQ editor, review checklist, dashboard status, and admin verification queue.
- **Public vendor profile** — `/v/{slug}` renders verified/public vendors with gallery, badges,
  visible package/add-on prices, service areas, FAQs, LocalBusiness/Service/Offer JSON-LD, dynamic
  OG image, local favorites, and booking/messaging intent capture.
- **Vendor mobile** — native seven-step onboarding with library/camera uploads and a public
  vendor profile route; Expo ImagePicker added at the SDK-compatible version.
- **Phase 2 seed/test** — optional dev seed creates 20 verified vendors across 10 categories in
  Kathmandu + Melbourne; the Testcontainers flow proves incomplete gates, onboard→approve→live,
  badges, visible prices, price versioning, and live sensitive-field re-review.

## What is stubbed / not started

- Deploys: Railway/Vercel/EAS accounts + secrets not configured (deploy workflow inert).
- Mobile: not yet run on a physical device/simulator (shell typechecks; Phase 13 completes).
- shadcn/ui: not installed — ADR-012 keeps the Phase 2 wizard on accessible native controls and
  the existing token system; reconsider only for a future complex primitive.
- Prettier + Husky pre-commit hooks: deferred (ESLint + CI gates cover the bar for now).
- Search, bookings, payments, messaging, reviews, and freelancer domain: Phases 3–7/12.

## Known placeholders awaiting later phases

- Customer/freelancer dashboard empty states reference their upcoming owning phases; the vendor
  and admin placeholders were replaced by real Phase 2 surfaces.
- Public profile availability/message CTAs deliberately capture intent in a local waitlist modal;
  Phase 4 and Phase 6 replace them with the booking and messaging flows.
- Settings "Change password" routes through the password-reset flow (no authenticated
  change-password endpoint yet; add when Phase 14 hardens account management).
- `feature.vendor_onboarding` is enabled by V2; future Phase 3–5 feature flags remain false.
