# 03 — Technical Implementation Plan

How the platform gets built **on top of** the existing codebase without touching the homepage.

---

## 1. Stack decisions

| Layer | Choice | Rationale |
|---|---|---|
| Framework | **Next.js (App Router) + React + TypeScript** | Already in place; SSR/ISR gives the SEO/AEO surface Rule 10 demands |
| Styling | **Tailwind CSS v4** | Already in place; reuse the existing design tokens in `globals.css` (read, don't edit) |
| Database | **Supabase Postgres** | Relational graph (bookings ↔ payments ↔ reviews), RLS for multi-tenant safety, `pgvector` for style/visual matching |
| Auth | **Supabase Auth** | Email/OTP + Google; one `users` table with role claims (customer / vendor / freelancer / admin) |
| Storage | **Supabase Storage** | Portfolios, verification documents, event galleries; signed URLs for private docs |
| Payments/Escrow | **Stripe Connect** (Express accounts, destination charges, manual payouts) | Deposit held on platform, milestone-released transfers = the escrow model in T0.4 |
| AI copilot & vendor assistant | **Claude API** (`claude-sonnet-5` default; `claude-haiku-4-5` for triage/classification) | Budget/timeline generation, quote drafting, inquiry triage |
| Embeddings / visual search | `pgvector` + an embedding model over portfolio images & style-quiz answers | T1.7 style matching, T1.8 visual search |
| Email/notifications | Resend (email) + web push later | Booking confirmations, response-clock nudges |
| Hosting | Vercel | Fits Next.js; preview deployments per PR |

**Principle:** boring, few, managed services. Every service must serve at least two tiers.

## 2. Repository layout (additive only)

```text
src/
  app/
    page.tsx                 # FROZEN homepage
    globals.css              # FROZEN (extend via new css only if unavoidable — prefer component-level)
    (marketplace)/
      search/                # /search — date-filtered vendor search
      vendors/[slug]/        # public vendor profile (SSR + JSON-LD)
      venues/[slug]/
      inspiration/           # real-event photo feed
    (planning)/
      plan/                  # event workspace (project → ceremonies)
      plan/[projectId]/...   # budget, guests, tables, checklist, website builder
    (vendor-os)/
      dashboard/             # vendor: pipeline, calendar, packages, analytics, payouts
    (gigs)/
      gigs/                  # freelancer: shift board, profile, earnings
    (auth)/
      login/ signup/ onboarding/
    api/                     # route handlers: webhooks (Stripe), AI endpoints, RSVP submit
  components/
    home/                    # FROZEN
    marketplace/ planning/ vendor-os/ gigs/ ui/   # new feature components
  data/
    home.ts                  # FROZEN
  lib/
    supabase/                # client + server helpers, generated types
    stripe/                  # connect, escrow milestones, webhook handlers
    ai/                      # Claude client, prompts, copilot boundary rules
    ranking/                 # merit score (response, completion, reviews)
    schema-org/              # JSON-LD builders for AEO
supabase/
  migrations/                # SQL migrations, one per change, never edited after merge
docs/                        # this documentation
```

Route groups `(marketplace)`, `(planning)`, `(vendor-os)`, `(gigs)` get their own layouts;
the root layout and `/` stay untouched.

## 3. Data model (core entities)

```text
users            id, role[], name, email, phone(masked-by-default), locale, created_at
vendors          id, owner_user_id, slug, name, categories[], city, service_area,
                 verification_status, insurance_verified, response_rate, avg_response_mins,
                 completion_rate, merit_score, style_embedding vector
vendor_packages  id, vendor_id, name, description, includes[], base_price, price_unit,
                 duration, addons jsonb, published
availability     vendor_id, date, status(available|booked|blocked), source(manual|booking|sync)
event_projects   id, owner_user_id, name, type, city, guest_estimate, budget_total, currency
ceremonies       id, project_id, name, date, venue_text|venue_vendor_id, guest_subset
collaborators    project_id, user_id, role(owner|partner|parent|planner|helper), permissions
inquiries        id, project_id, vendor_id (EXCLUSIVE — one vendor), package_id?, event_date,
                 guest_count, budget_band, message, status, responded_at   -- response clock
bookings         id, inquiry_id?, vendor_id, project_id, package_id, event_date, price_total,
                 status(pending|confirmed|in_progress|completed|cancelled|disputed)
payment_milestones  booking_id, label, amount, due_at, status(held|released|refunded),
                    stripe_payment_intent, released_at
reviews          id, booking_id (REQUIRED — verified-booking-only), direction(c2v|v2c),
                 rating, text, created_at
guests           project_id, name, side, dietary, contact
guest_invites    guest_id, ceremony_id, rsvp(pending|yes|no), party_size
tables           ceremony_id, name, shape, capacity, x, y      -- room-visual planner
seat_assignments table_id, guest_id                            -- auto-pruned on RSVP=no
budget_lines     project_id, category, estimate, booking_id?   -- estimate replaced by booking
event_photos     id, project_id, uploader_vendor_id, url, embedding vector, published
photo_vendor_tags photo_id, vendor_id, role                    -- PartySlate mechanic
freelancers      id, user_id, skills[], day_rate, certifications, show_rate, rating
shifts           id, vendor_id, ceremony_id?, role, date, hours, pay_amount (upfront),
                 status(open|filled|completed|paid)
shift_applications / shift_ratings, rosters (vendor_id, freelancer_id, favorite)
verification_docs vendor_id, type(registration|insurance), storage_path, status, reviewed_by
```

**RLS everywhere.** Customers see their projects; vendors see their pipeline; freelancers see
their shifts; public sees only published profiles/photos. Verification docs: admin-only.

## 4. Key mechanics

### 4.1 Escrow (T0.4) on Stripe Connect
1. Customer pays deposit → PaymentIntent captured to the **platform** account (funds held).
2. Milestones stored in `payment_milestones`; release = `transfer` to the vendor's Express
   account minus take rate.
3. Refund/dispute → platform mediates before any transfer; webhooks keep state in sync.
4. Gig pay (Tier 3) reuses the same rails with a 48–72h auto-release after shift completion.

### 4.2 Exclusive leads (T0.7)
`inquiries.vendor_id` is a single FK — the schema itself cannot express a shared lead.
Sending to three vendors = three separate inquiry rows created by explicit user action.

### 4.3 Merit ranking (T2.5)
`merit_score = w1·response_speed + w2·response_rate + w3·completion_rate + w4·review_quality + w5·verification_depth`
Recomputed nightly (cron/edge function). Search orders by availability-match first, then
merit. **No ad-spend input exists in the function — keep it that way.**

### 4.4 AI copilot boundary (T1.2)
The system prompt encodes the rule: budgets, timelines, checklists, drafts = automate;
aesthetic choices, people decisions = present options, never pick. All copilot outputs are
suggestions requiring user confirmation before touching project data (tool-use with
confirm-before-write).

### 4.5 AEO (Rule 10, T0.1/T4.1)
Every public vendor page is SSR/ISR with: JSON-LD (`LocalBusiness` + `Offer` per package +
`AggregateRating` from verified reviews + `FAQPage`), semantic HTML, an entity-style URL
(`/vendors/[slug]`), and structured FAQs. Sitemaps regenerate on publish. This ships with
the **first** vendor page, not as a later retrofit.

### 4.6 Style matching (T1.7)
Quiz answers → text/image embedding → stored per project. Vendor portfolio images →
embeddings averaged into `vendors.style_embedding`. Match = cosine similarity via pgvector,
filtered by category, city, date availability, and budget band.

### 4.7 Availability filter (T0.2)
Search query joins `availability` on the customer's event date. Default search **requires** a
date; "browsing without a date" is the explicit fallback, not the default.

## 5. Internationalization & currency (T4.6, planned from day one)

- All money as integer minor units + currency code. No floats.
- All user-facing strings in the new surfaces go through a message catalog (even while
  English-only) so Devanagari/Nepali arrives as translation, not refactor.
- Dates stored UTC; ceremonies carry venue timezone.

## 6. Quality gates

- `npm run check` (ESLint + tsc) green on every commit; `npm run build` green before merge.
- Every migration reviewed; RLS policies tested with per-role integration tests.
- Payment flows covered by Stripe test-mode E2E before any tier ships.
- Homepage snapshot: `/` renders byte-identical markup before and after every PR
  (guard for the frozen-homepage rule).
