# 04 — Working Instructions (humans and AI agents)

Rules for anyone writing code in this repository. These are binding.

---

## 1. The frozen homepage

The current homepage ships as-is. **Do not modify:**

- `src/app/page.tsx`
- `src/app/layout.tsx` (root layout) — extend via nested route-group layouts instead
- `src/app/globals.css` — additive changes only if a platform-wide token is truly needed, and never altering existing rules
- `src/components/home/**` — all homepage components
- `src/data/home.ts` — homepage content
- `public/images/wedjan/**` — existing imagery (adding new files is fine)

If a new feature needs something the homepage owns (e.g. header/footer), **copy it into the
new surface's components** (`src/components/ui/` or the feature folder) and evolve the copy.
Do not refactor homepage files "to share code."

## 2. Where new code goes

- New routes → route groups under `src/app/` as laid out in [03-implementation.md](03-implementation.md) §2.
- New components → `src/components/<feature>/` (`marketplace`, `planning`, `vendor-os`, `gigs`, `ui`).
- Shared logic → `src/lib/<domain>/`.
- Database changes → one SQL migration per change in `supabase/migrations/`; never edit a merged migration.

## 3. Coding conventions

- TypeScript strict; no `any` unless annotated with a reason.
- Match the existing code style: typed data modules (like `src/data/home.ts`), small
  presentational components, Tailwind utility classes, `next/image` for all imagery.
- Server Components by default; `"use client"` only where interaction requires it.
- Money: integer minor units + currency code, always. Dates: UTC in storage.
- All user-facing strings in new surfaces go through the message catalog (see 03 §5).
- Every public marketplace page must ship with its JSON-LD schema (see 03 §4.5) — AEO is
  not a follow-up task.

## 4. Product rules that constrain code

These come from the Ten Rules ([01-blueprint.md](01-blueprint.md) §5) and are enforced in code review:

1. **No unpriced listing can be published.** The publish path must validate ≥1 priced package.
2. **No schema or API may represent a shared lead.** `inquiries.vendor_id` stays singular.
3. **No ad-spend variable may enter the ranking function.** Featured placement, if ever built, is a separate, labeled UI slot — never a ranking input.
4. **No payment flow bypasses escrow.** Direct vendor charging is not built, ever.
5. **Reviews require a completed `booking_id`.** No standalone review creation path.
6. **No customer-side booking fees.** Price shown = price paid.
7. **Copilot never auto-writes project data.** AI output → user confirms → write.
8. **Phone numbers are masked by default.** Revealing is the customer's explicit action.

## 5. Workflow

1. Work happens on feature branches off `main`; PR per feature; no direct pushes to `main`.
2. Branch names: `feat/t0-escrow`, `feat/t1-table-planner`, `fix/...` — tier prefix keeps the
   build order visible.
3. Before every commit: `npm run check`. Before every merge: `npm run build`.
4. Verify the homepage is untouched: `git diff main --stat -- src/components/home src/data/home.ts src/app/page.tsx src/app/globals.css src/app/layout.tsx` must be empty on every PR.
5. Secrets live in `.env.local` (gitignored). Required keys are documented in `.env.example`
   (create it when the first secret is introduced; never commit real values).
6. Follow the tier order in [05-process.md](05-process.md). Do not start a Tier N feature
   while a Tier N-1 exit criterion is unmet, unless the roadmap doc is updated first.

## 6. Definition of Done (every feature)

- [ ] Meets its spec in [02-features.md](02-features.md)
- [ ] Passes the product rules in §4 above
- [ ] `npm run check` and `npm run build` green
- [ ] RLS policies written and tested for any new table
- [ ] Mobile-responsive (the homepage sets the bar)
- [ ] Public pages have JSON-LD + metadata
- [ ] Homepage diff is empty
- [ ] Docs updated if behavior differs from spec (update `docs/`, don't let it drift)

## 7. For AI agents specifically

- Read this file and [03-implementation.md](03-implementation.md) before writing code.
- Never "improve" frozen files, even trivially (imports, formatting, comments).
- When a spec conflict is found between docs, `02-features.md` wins on *what*,
  `03-implementation.md` wins on *how*; flag the conflict in your summary.
- Prefer extending existing patterns in the repo over introducing new libraries; any new
  dependency must be justified in the PR description.
