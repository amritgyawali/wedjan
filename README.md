# wedjan

Three-sided event & wedding marketplace: **customers** plan events and book vendors,
**vendors** sell priced packages and hire crew, **freelancers** pick up gig shifts.

> "The first event marketplace where you can plan the event, book every verified vendor at a
> real price with protected payments, and where vendors can staff their crews — in one platform."

Full documentation lives in [docs/](docs/README.md) — start with the
[build playbook](docs/06-build-playbook.md) and the [progress tracker](docs/PROGRESS.md).

## Monorepo layout

```text
apps/
  web/       Next.js 16 App Router (frozen approved homepage + platform shell)
  api/       Spring Boot 3.4 / Java 21 — auth, accounts, media (Flyway + Postgres)
  mobile/    Expo (expo-router) — auth + role-aware tabs
packages/
  shared/    openapi.yaml (API-first source of truth) + generated TS client
  ui-tokens/ Design tokens derived from the approved homepage (web + mobile)
scripts/     Homepage freeze guard + tooling
```

## Prerequisites

- Node.js ≥ 22, pnpm ≥ 11 (`corepack enable` or `npm i -g pnpm`)
- Java 21 (Temurin/Oracle)
- Docker Desktop (Postgres 16 + pgvector, Redis, Mailpit, MinIO)

## One-command local setup

```bash
docker compose up -d      # Postgres, Redis, Mailpit (:8025), MinIO (:9001)
pnpm install
pnpm dev                  # turbo: web (:3000) + api (:8080) + mobile (expo)
```

Copy `.env.example` to `.env` only if you need to override defaults — local dev
works with zero configuration. Enable demo accounts with `SEED_ENABLED=true`
(admin/customer/vendor/freelancer @wedjan.local, password `Demo@wedjan1`).

Emails (signup OTPs) land in Mailpit: <http://localhost:8025>.

## Everyday commands

```bash
pnpm check                 # lint + typecheck everywhere
pnpm build                 # build all apps
pnpm test                  # API unit + integration tests (Testcontainers)
pnpm codegen               # regenerate TS client from packages/shared/openapi.yaml
pnpm guard:homepage        # verify the frozen homepage is untouched
```

**Working inside OneDrive/Dropbox?** The sync client intermittently locks Gradle's
build outputs and fails the API build with `Unable to delete directory`. Point the
build directory at local disk:

```bash
WEDJAN_BUILD_DIR=/c/temp/wedjan-api-build   # or -PwedjanBuildDir=... per invocation
```

## The Homepage Rule (ADR-001)

The homepage (`apps/web/src/app/page.tsx`, `src/components/home/**`, `src/data/home.ts`,
`src/app/globals.css`, `public/images/wedjan/**`) is **approved and frozen**. CI enforces a
hash manifest (`scripts/check-homepage-frozen.mjs`). New features are new routes/components —
never edits to the frozen tree.

## API-first convention

Every endpoint is specified in [packages/shared/openapi.yaml](packages/shared/openapi.yaml)
**before** implementation; `pnpm codegen` produces the typed client used by web and mobile.
CI fails if the generated client is stale.
