# wedjan — Platform Documentation

This folder is the single source of truth for turning the existing **wedjan** homepage into a
full three-sided event marketplace (customer → vendor → freelancer).

> **Hard rule: the current homepage is frozen.** Nothing in `src/components/home/`,
> `src/data/home.ts`, `src/app/page.tsx`, or `src/app/globals.css` may be modified while
> building the platform. All new features are added as new routes, new components, and new
> data modules. See [04-instructions.md](04-instructions.md).

## The positioning sentence

> "The first event marketplace where you can plan the event, book every verified vendor at a
> real price with protected payments, and where vendors can staff their crews — in one platform."

## Documents

| File | What it covers |
|---|---|
| [01-blueprint.md](01-blueprint.md) | Strategy: market gaps, competitor feature analysis, validated user demand, the Ten Rules |
| [02-features.md](02-features.md) | The full feature specification, Tier 0 → Tier 4, with scope per feature |
| [03-implementation.md](03-implementation.md) | Technical architecture: stack, data model, routes, services, integrations |
| [04-instructions.md](04-instructions.md) | Working rules for anyone (human or AI) writing code in this repo |
| [05-process.md](05-process.md) | Phased build order, milestones, launch wedge, liquidity metrics, monetization rollout |
| [06-build-playbook.md](06-build-playbook.md) | **Master build playbook** — structured runbook for all 15 phases: master context, wedjan overrides, per-phase gates |
| [15-phase-build-prompts.md](15-phase-build-prompts.md) | Canonical full prompt text for each of the 15 phases (paste per the playbook) |
| [PROGRESS.md](PROGRESS.md) | Living tracker: phase status, what exists, what's stubbed |
| [DECISIONS.md](DECISIONS.md) | Living architecture decision log (ADRs) |

## Reading order

1. **Product / strategy people:** 01 → 02 → 05
2. **Engineers starting work:** 04 → 03 → 02 → 05
3. **Everyone before shipping anything:** the Ten Rules at the end of 01-blueprint.md

## Current state of the codebase

- Next.js App Router, React, TypeScript, Tailwind CSS v4
- One finished route: `/` (the homepage) — frozen
- Typed content in `src/data/home.ts`; imagery in `public/images/wedjan/`
- No backend, no auth, no database yet — that build begins at Tier 0 (see 03 and 05)
