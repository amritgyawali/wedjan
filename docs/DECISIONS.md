# wedjan — Architecture Decision Log

Living document. Append a decision entry whenever a meaningful architectural or product-shape
choice is made (the 15-phase prompts call several out explicitly — e.g. PostGIS vs
earthdistance in P3, price-snapshot strategy in P2/P4, seating view-only mobile in P8,
single-app vs multi-app mobile in P13). **Never delete entries; supersede them.**

Format per entry:

```
## ADR-NNN — <title>
- Date: YYYY-MM-DD
- Phase: N
- Status: Accepted | Superseded by ADR-MMM
- Decision: <what was chosen>
- Context: <the problem / options considered>
- Consequences: <what this commits us to>
```

---

## ADR-001 — The existing homepage is frozen and ported verbatim
- Date: 2026-07-15
- Phase: pre-1
- Status: Accepted
- Decision: The approved wedjan homepage (current repo `/`) is the permanent marketing
  homepage. It is ported into `apps/web` unchanged during Phase 1; Phase 3 wires its
  search/CTAs to real routes without visual changes. Every phase gate includes a
  homepage-unchanged check.
- Context: Owner directive — the homepage design is final. The prompt pack's "homepage
  placeholder → Homepage v1" steps are reinterpreted as port + wire-up, never redesign.
- Consequences: New homepage-adjacent blocks (trust strip, how-it-works, city links) are
  added around existing sections in the same visual language; design tokens for all new
  surfaces derive from the homepage's `globals.css`.

## ADR-002 — The 15-phase pack supersedes the earlier single-app plan
- Date: 2026-07-15
- Phase: pre-1
- Status: Accepted
- Decision: Build on the prompt pack's architecture (pnpm/Turborepo monorepo; Next.js web;
  Expo mobile; Spring Boot 3.4 + Java 21 API; Postgres 16 + pgvector + Flyway; Redis; R2;
  Stripe Connect; Railway + Vercel + EAS), not the earlier Next.js + Supabase design in
  `docs/03-implementation.md`.
- Context: 03-implementation.md predates the prompt pack and covered web-only scope. The
  pack adds mobile apps, an admin app, the gig layer, and production-ops depth that the
  lighter stack didn't address. Its concepts (escrow, exclusivity, merit, AEO) carry over 1:1.
- Consequences: Backend work happens in `apps/api` (Java), not Supabase; 03 is kept for
  domain reasoning only. One deviation from the pack: use the repo's Next.js 16 rather
  than "Next.js 15".

## ADR-003 — Fast-revenue launch strategy
- Date: 2026-07-15
- Phase: pre-1
- Status: Accepted
- Decision: Ship Phases 1–7, launch the Kathmandu–Melbourne corridor, build Phases 8–15
  while live.
- Context: The pack offers "fast revenue" vs "full empire first". The booking + trust loop
  is the business; live-market learning compounds everything after it.
- Consequences: Phase 7's launch gate (golden-path E2E, placeholder sweep, launch checklist)
  is a hard stop; P8–P15 development must never break the live loop (regression suite from
  P7 runs on every merge).

## ADR-004 — pnpm hoisted node linker for the whole workspace
- Date: 2026-07-15
- Phase: 1
- Status: Accepted
- Decision: `.npmrc` sets `node-linker=hoisted` workspace-wide.
- Context: React Native/Expo (apps/mobile) does not resolve modules reliably from pnpm's
  default isolated (symlinked) layout; Metro needs a flat node_modules.
- Consequences: Slightly weaker dependency isolation for web/api tooling; Metro config
  adds the workspace root to watchFolders/nodeModulesPaths.

## ADR-005 — Refresh-token families carry an explicit family_id
- Date: 2026-07-15
- Phase: 1
- Status: Accepted
- Decision: `refresh_tokens` has both `rotated_from` (audit chain, per the pack schema) and
  a `family_id` UUID shared by every rotation of one login.
- Context: Reuse detection must revoke the whole family; walking the rotated_from chain is
  O(n) queries, `family_id` makes it one UPDATE.
- Consequences: Reuse of a rotated token revokes the entire family in a single statement;
  audit log records the family id.

## ADR-006 — Account status includes PENDING_VERIFICATION
- Date: 2026-07-15
- Phase: 1
- Status: Accepted
- Decision: `accounts.status ∈ {PENDING_VERIFICATION, ACTIVE, SUSPENDED, DELETED}` (the pack
  listed only the last three).
- Context: Signup→OTP verification needs a first-class pre-active state; login is blocked
  with AUTH_EMAIL_NOT_VERIFIED until the OTP is consumed.
- Consequences: Email verification is enforceable at the state-machine level, not by flag.

## ADR-007 — New web surfaces use --wj-* CSS custom properties, not a second Tailwind root
- Date: 2026-07-15
- Phase: 1
- Status: Accepted
- Decision: `packages/ui-tokens/tokens.css` exports plain `:root { --wj-* }` custom
  properties; platform routes style with handwritten classes (platform.css) in the
  homepage's visual language. The frozen `globals.css` remains the single Tailwind v4 root.
- Context: A second `@import "tailwindcss"` in a route-group stylesheet would duplicate
  preflight/utilities and risk specificity drift against the frozen homepage.
- Consequences: One Tailwind pipeline; tokens usable from any CSS; the TS token object
  (`@wedjan/ui-tokens`) serves mobile StyleSheets.

## ADR-008 — Mobile styling via StyleSheet + token object; NativeWind deferred
- Date: 2026-07-15
- Phase: 1
- Status: Accepted
- Decision: apps/mobile consumes `@wedjan/ui-tokens` as a typed TS object with React Native
  StyleSheet; NativeWind is deferred to Phase 13 (native polish).
- Context: NativeWind adds babel/tailwind config surface with little payoff for the Phase 1
  shell; tokens keep the visual language identical either way.
- Consequences: Revisit at Phase 13; no tailwind classes in mobile code until then.

## ADR-009 — BlurHash encoder implemented in-repo
- Date: 2026-07-15
- Phase: 1
- Status: Accepted
- Decision: The media pipeline's blurhash generation is a self-contained ~100-line port of
  the public-domain BlurHash algorithm (`media/BlurHash.java`) instead of a third-party jar.
- Context: No well-maintained Maven artifact; the algorithm is tiny and stable.
- Consequences: We own the code (unit-tested); encode only ≤64px thumbnails for cost.

## ADR-010 — Public visibility is independent from verification workflow status
- Date: 2026-07-15
- Phase: 2
- Status: Accepted
- Decision: `vendor_profiles.is_public` records whether a listing may be served publicly;
  review workflow remains in `status`. First verification sets both `VERIFIED` and
  `is_public=true`; later sensitive edits set `UNDER_REVIEW` without clearing `is_public`.
- Context: A single status enum cannot simultaneously say "this edit needs review" and "the
  previously approved listing remains live." Treating `UNDER_REVIEW` as private would violate
  the Phase 2 no-unlisting edge case.
- Consequences: Public reads require `is_public=true` and reject suspended vendors. Admin
  suspension remains able to remove a listing immediately; later moderation can version the
  exact sensitive fields under review.

## ADR-011 — Published package edits snapshot commercial terms by version
- Date: 2026-07-15
- Phase: 2
- Status: Accepted
- Decision: Before changing any published package, copy its price, currency, pricing model,
  deposit, cancellation policy, and booking mode into `package_price_versions`, keyed by
  `(package_id, version)`, then increment the live row version.
- Context: Editing a listing must not rewrite the commercial terms attached to an existing
  booking. Phase 4 will create booking snapshots, but Phase 2 already needs a durable source
  version to snapshot from.
- Consequences: Phase 4 bookings reference a package version and copy the values they display;
  the current package row remains efficient for discovery while prior terms are immutable.

## ADR-012 — Phase 2 forms extend the existing token-based CSS system
- Date: 2026-07-15
- Phase: 2
- Status: Accepted
- Decision: The onboarding wizard uses accessible native controls and focused platform
  components styled with the existing `--wj-*` tokens; shadcn/ui remains uninstalled.
- Context: ADR-007 established a single Tailwind root to protect the frozen homepage. The Phase
  2 controls required no behavior that justified adding a parallel component/theme layer.
- Consequences: Web and native continue to share the token source rather than component code;
  shadcn can still be introduced for a future complex primitive if it preserves ADR-001/007.
