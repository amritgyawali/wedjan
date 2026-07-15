# wedjan — Build Progress

Living document. Update at the end of **every** phase: flip the tracker row, then record
what exists vs. what is stubbed. Rules and workflow: [06-build-playbook.md](06-build-playbook.md).

## Phase tracker

| Phase | Status | Started | Done | Tag | Notes |
|---|---|---|---|---|---|
| 1 Foundation | 🔶 built | 2026-07-15 | | phase-1-complete | Core complete on `feat/phase-1-foundation`; gate items pending: deploys (no Railway/Vercel accounts yet), mobile device run |
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

## What exists

- **Homepage (`/`)** — finished, approved, and **frozen** (Homepage Rule / ADR-001), ported
  verbatim to `apps/web`; CI guard `scripts/check-homepage-frozen.mjs` verifies a 37-file
  SHA-256 manifest on every PR.
- **Monorepo** — pnpm workspaces + Turborepo (`apps/web`, `apps/api`, `apps/mobile`,
  `packages/shared`, `packages/ui-tokens`); hoisted node linker (ADR-004).
- **packages/shared** — `openapi.yaml` (API-first contract for all Phase 1 endpoints) +
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
    async audit writer, app_config typed accessor, dev seed (admin + 3 demo accounts),
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

## What is stubbed / not started

- Deploys: Railway/Vercel/EAS accounts + secrets not configured (deploy workflow inert).
- Mobile: not yet run on a physical device/simulator (shell typechecks; Phase 13 completes).
- shadcn/ui: not installed — platform shell uses handwritten classes in the homepage
  language; introduce shadcn/ui when Phase 2's form-heavy wizard needs it.
- Prettier + Husky pre-commit hooks: deferred (ESLint + CI gates cover the bar for now).
- Vendor/freelancer domain, search, bookings, payments, messaging, reviews: Phases 2–7.

## Known placeholders awaiting later phases

- P1 dashboard empty states reference upcoming phases by name ("Vendor onboarding opens in
  the next phase") — each owning phase replaces its card with the real surface.
- Settings "Change password" routes through the password-reset flow (no authenticated
  change-password endpoint yet; add when Phase 14 hardens account management).
- `feature.*` flags in app_config default to false for Phases 2–5 surfaces.
