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
