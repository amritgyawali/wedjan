# Phase 3 discovery operations

## Search model

`vendor_profiles.search_document` and `packages.search_document` are stored, weighted `tsvector`
columns with GIN indexes. Business names are weight A, package/category terms weight B, and long
descriptions/inclusions weight C. `pg_trgm` handles misspellings such as `photografer`.

Geo search requires both conditions: the supplier is inside the customer's requested radius and
the customer is inside the supplier's declared service radius. See ADR-013.

Price filters use package-native minor units. Category/city pages therefore describe one local
currency. Compare keeps every native amount authoritative and labels static FX conversions as
approximate (ADR-014).

## 10k performance gate

Use a disposable local database:

```powershell
psql $env:DATABASE_URL -f scripts/seed-search-load.sql
./scripts/benchmark-search.ps1 -Requests 100
```

The benchmark exits non-zero when p95 is 300 ms or higher. The data generator creates exactly
10,000 public vendors split across Kathmandu and Melbourne and then analyzes search tables.

Measured locally on 2026-07-15 with a 256 MB API heap and five warm-up requests: **243.4 ms p95
across 100 successful requests**.

## AEO surfaces

- `/{country}/{city}/{category}`: one-hour ISR, live counts/prices, six category FAQs.
- `/sitemap.xml`: sitemap index; page, vendor, and showcase maps refresh daily.
- `/robots.txt` and `/llms.txt`: crawler rules and canonical entity patterns.
- Vendor pages: `LocalBusiness`, `Offer`, and `FAQPage` JSON-LD.
- Landing pages: `ItemList`, `FAQPage`, and `BreadcrumbList` JSON-LD.

Public showcase credits expose only `ACCEPTED` tags. Declined/pending associations never enter
HTML, JSON-LD, or mobile responses.
