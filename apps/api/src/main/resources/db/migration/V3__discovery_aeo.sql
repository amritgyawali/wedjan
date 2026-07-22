-- wedjan V3 — search/discovery, inspiration, favorites and AEO.

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;

ALTER TABLE vendor_profiles ADD COLUMN search_document tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(business_name, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(tagline, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(about, '')), 'C')
    ) STORED;
ALTER TABLE packages ADD COLUMN search_document tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(description_md, '') || ' ' ||
            coalesce(whats_included_md, '')), 'C')
    ) STORED;

CREATE INDEX ix_vendor_profiles_search ON vendor_profiles USING gin (search_document);
CREATE INDEX ix_vendor_profiles_name_trgm ON vendor_profiles USING gin (business_name gin_trgm_ops);
CREATE INDEX ix_packages_search ON packages USING gin (search_document);
CREATE INDEX ix_packages_title_trgm ON packages USING gin (title gin_trgm_ops);
CREATE INDEX ix_categories_name_trgm ON categories USING gin (name gin_trgm_ops);
CREATE INDEX ix_service_areas_earth ON service_areas USING gist
    (ll_to_earth(lat, lng)) WHERE lat IS NOT NULL AND lng IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE search_events (
    id            uuid PRIMARY KEY,
    query         varchar(300),
    filters       jsonb       NOT NULL DEFAULT '{}'::jsonb,
    results_count integer     NOT NULL CHECK (results_count >= 0),
    account_id    uuid REFERENCES accounts (id),
    session_id    varchar(120),
    duration_ms   integer CHECK (duration_ms >= 0),
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_search_events_created ON search_events (created_at DESC);
CREATE INDEX ix_search_events_low_result ON search_events (created_at DESC)
    WHERE results_count < 3;

CREATE TABLE showcases (
    id               uuid PRIMARY KEY,
    vendor_id        uuid         NOT NULL REFERENCES vendor_profiles (account_id),
    title            varchar(180) NOT NULL,
    slug             varchar(220) NOT NULL UNIQUE,
    event_type       varchar(12)  NOT NULL
        CHECK (event_type IN ('WEDDING','CORPORATE','BIRTHDAY','CULTURAL','OTHER')),
    event_date       date,
    city             varchar(120) NOT NULL,
    country          varchar(2)   NOT NULL,
    cover_media_id   uuid REFERENCES media_assets (id),
    description_md   text,
    status           varchar(10)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','PUBLISHED','FLAGGED')),
    style_tags       text[]       NOT NULL DEFAULT '{}',
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       uuid,
    deleted_at       timestamptz,
    CONSTRAINT ux_showcase_vendor_title UNIQUE (vendor_id, title)
);
CREATE INDEX ix_showcases_feed ON showcases (status, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_showcases_city_type ON showcases (lower(city), event_type)
    WHERE status='PUBLISHED' AND deleted_at IS NULL;
CREATE INDEX ix_showcases_style_tags ON showcases USING gin (style_tags);

CREATE TABLE showcase_media (
    id          uuid PRIMARY KEY,
    showcase_id uuid NOT NULL REFERENCES showcases (id) ON DELETE CASCADE,
    media_id    uuid NOT NULL REFERENCES media_assets (id),
    caption     varchar(300),
    sort        integer NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_showcase_media UNIQUE (showcase_id, media_id)
);

CREATE TABLE showcase_vendor_tags (
    id          uuid PRIMARY KEY,
    showcase_id uuid NOT NULL REFERENCES showcases (id) ON DELETE CASCADE,
    vendor_id   uuid NOT NULL REFERENCES vendor_profiles (account_id),
    role_label  varchar(120) NOT NULL,
    status      varchar(8) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','ACCEPTED','DECLINED')),
    responded_at timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_showcase_vendor_tag UNIQUE (showcase_id, vendor_id)
);
CREATE INDEX ix_showcase_tags_vendor ON showcase_vendor_tags (vendor_id, status, created_at DESC);

CREATE TABLE favorites (
    account_id  uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    entity_type varchar(8) NOT NULL CHECK (entity_type IN ('VENDOR','SHOWCASE','PACKAGE')),
    entity_id   uuid NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, entity_type, entity_id)
);

CREATE TABLE shortlists (
    id         uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    name       varchar(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_shortlists_account_name UNIQUE (account_id, name)
);

CREATE TABLE shortlist_items (
    id           uuid PRIMARY KEY,
    shortlist_id uuid NOT NULL REFERENCES shortlists (id) ON DELETE CASCADE,
    vendor_id    uuid NOT NULL REFERENCES vendor_profiles (account_id),
    package_id   uuid REFERENCES packages (id),
    note         varchar(500),
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_shortlist_vendor_package UNIQUE NULLS NOT DISTINCT
        (shortlist_id, vendor_id, package_id)
);

CREATE TABLE faq_templates (
    id              uuid PRIMARY KEY,
    category_id     uuid NOT NULL REFERENCES categories (id),
    question_tpl    varchar(300) NOT NULL,
    answer_tpl      varchar(1200) NOT NULL,
    sort            integer NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_faq_template_sort UNIQUE (category_id, sort)
);

INSERT INTO faq_templates (id, category_id, question_tpl, answer_tpl, sort)
SELECT gen_random_uuid(), c.id, seed.question, seed.answer, seed.sort
FROM categories c
CROSS JOIN (VALUES
  (1, 'How much does {category} cost in {city}?', '{category} in {city} currently starts from {min_price}; the live median is {median_price}.'),
  (2, 'How early should I book {category} in {city}?', 'Popular {category} teams in {city} can book months ahead. Shortlist early and confirm availability for your date.'),
  (3, 'What is included in a {category} package?', 'Every wedjan package lists inclusions, exclusions, deposit and cancellation terms beside its real price.'),
  (4, 'Are these {category} vendors verified?', 'Public wedjan listings show their earned verification badges so you can identify checked businesses and identities.'),
  (5, 'Can I compare {category} packages?', 'Yes. Add up to four packages to compare prices, inclusions, deposits, policies and badges side by side.'),
  (6, 'Do customers pay a booking fee?', 'Customers pay 0% wedjan booking fees. Any optional payment-processing fee is shown separately before payment.')
) AS seed(sort, question, answer)
WHERE c.is_active;

CREATE TABLE fx_rates (
    base_currency  varchar(3) NOT NULL,
    quote_currency varchar(3) NOT NULL,
    rate           numeric(18,8) NOT NULL CHECK (rate > 0),
    rate_date      date NOT NULL DEFAULT current_date,
    PRIMARY KEY (base_currency, quote_currency, rate_date)
);
INSERT INTO fx_rates(base_currency, quote_currency, rate) VALUES
    ('AUD','AUD',1), ('NPR','NPR',1), ('USD','USD',1), ('GBP','GBP',1),
    ('AUD','NPR',88.0), ('NPR','AUD',0.01136364),
    ('USD','AUD',1.52), ('AUD','USD',0.65789474),
    ('GBP','AUD',1.94), ('AUD','GBP',0.51546392);

INSERT INTO app_config(key, value, description)
VALUES ('feature.discovery', 'true'::jsonb, 'Search, showcases, favorites and AEO')
ON CONFLICT (key) DO UPDATE SET value=excluded.value, updated_at=now();
