-- Optional Phase 3 load dataset: adds exactly 10,000 searchable public vendors.
-- Run against a disposable local database only:
--   psql "$DATABASE_URL" -f scripts/seed-search-load.sql
BEGIN;
CREATE TEMP TABLE load_vendors AS
SELECT gen_random_uuid() id, n,
       CASE WHEN n % 2 = 0 THEN 'Kathmandu' ELSE 'Melbourne' END city,
       CASE WHEN n % 2 = 0 THEN 'NP' ELSE 'AU' END country,
       CASE WHEN n % 2 = 0 THEN 'NPR' ELSE 'AUD' END currency
FROM generate_series(1,10000) n;
DELETE FROM load_vendors lv USING accounts a WHERE lower(a.email)=lower('search-load-'||lv.n||'@wedjan.invalid');
INSERT INTO accounts(id,email,password_hash,status,default_currency)
SELECT id,'search-load-'||n||'@wedjan.invalid','load-test-only','ACTIVE',currency FROM load_vendors
ON CONFLICT DO NOTHING;
INSERT INTO account_roles(id,account_id,role) SELECT gen_random_uuid(),id,'VENDOR' FROM load_vendors ON CONFLICT DO NOTHING;
INSERT INTO vendor_profiles(account_id,business_name,slug,tagline,about,base_city,base_country,lat,lng,status,is_public,onboarding_step,currency)
SELECT id,'Search Load Studio '||n,'search-load-studio-'||n,
       CASE WHEN n%3=0 THEN 'Candid wedding photography' WHEN n%3=1 THEN 'Modern event styling' ELSE 'Transparent celebration packages' END,
       'Load-test vendor with searchable package text and visible prices.',city,country,
       CASE WHEN country='NP' THEN 27.7172 ELSE -37.8136 END,
       CASE WHEN country='NP' THEN 85.3240 ELSE 144.9631 END,'VERIFIED',true,7,currency
FROM load_vendors ON CONFLICT DO NOTHING;
INSERT INTO vendor_categories(id,vendor_id,category_id,is_primary)
SELECT gen_random_uuid(),lv.id,c.id,true FROM load_vendors lv
JOIN LATERAL (SELECT id FROM categories WHERE parent_id IS NULL ORDER BY sort OFFSET (lv.n%20) LIMIT 1) c ON true
ON CONFLICT DO NOTHING;
INSERT INTO packages(id,vendor_id,category_id,title,slug,description_md,price_cents,currency,pricing_model,whats_included_md,booking_mode,deposit_pct,cancellation_policy,status)
SELECT gen_random_uuid(),lv.id,vc.category_id,'Signature package','signature-package','A complete event service with planning, delivery and support.',
       100000+(lv.n%500)*1000,lv.currency,'FLAT','Planning\nEvent-day service\nDelivery',CASE WHEN lv.n%4=0 THEN 'INSTANT' ELSE 'REQUEST' END,25,'MODERATE','PUBLISHED'
FROM load_vendors lv JOIN vendor_categories vc ON vc.vendor_id=lv.id ON CONFLICT DO NOTHING;
INSERT INTO service_areas(id,vendor_id,mode,city,country,lat,lng,radius_km)
SELECT gen_random_uuid(),id,'CITY_RADIUS',city,country,
       CASE WHEN country='NP' THEN 27.7172 ELSE -37.8136 END,
       CASE WHEN country='NP' THEN 85.3240 ELSE 144.9631 END,80 FROM load_vendors;
COMMIT;
ANALYZE vendor_profiles; ANALYZE packages; ANALYZE service_areas;
