# wedjan — Build Progress

Living document. Update at the end of **every** phase: flip the tracker row, then record
what exists vs. what is stubbed. Rules and workflow: [06-build-playbook.md](06-build-playbook.md).

## Phase tracker

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

## What exists

- **Homepage (`/`)** — finished, approved, and **frozen** (see the Homepage Rule in the
  playbook). Next.js App Router + Tailwind v4, typed content in `src/data/home.ts`,
  imagery in `public/images/wedjan/`.
- **Documentation system** — `docs/01`–`06`, the 15-phase prompt pack, this tracker.

## What is stubbed / not started

- Everything else: monorepo, API, auth, database, payments, mobile app, admin —
  begins at Phase 1.

## Known placeholders awaiting later phases

*(Record here anything a phase intentionally leaves stubbed — e.g. "P2 profile shows
'Responds in ~X min' placeholder until P6/P7 metrics exist." Clear each entry when the
owning phase removes it; Phase 7's launch sweep must empty the P2–P6 section.)*
