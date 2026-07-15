-- wedjan V2 — vendor supply: taxonomy, vendor profiles, priced packages
-- (Product Law #1: price is NOT NULL and > 0 — "price on request" is
-- impossible at the schema level), media, verification, badges, FAQs.

-- ---------------------------------------------------------------------------
-- categories — 2-level taxonomy
-- ---------------------------------------------------------------------------
CREATE TABLE categories (
    id         uuid PRIMARY KEY,
    slug       varchar(80)  NOT NULL UNIQUE,
    name       varchar(120) NOT NULL,
    parent_id  uuid REFERENCES categories (id),
    icon       varchar(60),
    sort       integer      NOT NULL DEFAULT 0,
    is_active  boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    created_by uuid,
    deleted_at timestamptz
);

-- ---------------------------------------------------------------------------
-- vendor_profiles
-- ---------------------------------------------------------------------------
CREATE TABLE vendor_profiles (
    account_id                    uuid PRIMARY KEY REFERENCES accounts (id),
    business_name                 varchar(160),
    slug                          varchar(180) UNIQUE,
    tagline                       varchar(160),
    about                         text,
    founded_year                  smallint,
    team_size                     integer,
    languages                     text[],
    base_city                     varchar(120),
    base_country                  varchar(2),
    lat                           double precision,
    lng                           double precision,
    website                       varchar(300),
    instagram                     varchar(100),
    response_time_target_minutes  integer,
    status                        varchar(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'VERIFIED', 'REJECTED', 'SUSPENDED')),
    is_public                     boolean     NOT NULL DEFAULT false,
    rejection_reason              varchar(1000),
    onboarding_step               smallint    NOT NULL DEFAULT 1 CHECK (onboarding_step BETWEEN 1 AND 7),
    currency                      varchar(3)  NOT NULL DEFAULT 'AUD'
        CHECK (currency IN ('AUD', 'USD', 'GBP', 'NPR')),
    version                       bigint      NOT NULL DEFAULT 0,
    created_at                    timestamptz NOT NULL DEFAULT now(),
    updated_at                    timestamptz NOT NULL DEFAULT now(),
    created_by                    uuid,
    deleted_at                    timestamptz
);

CREATE INDEX ix_vendor_profiles_status ON vendor_profiles (status);
CREATE INDEX ix_vendor_profiles_city ON vendor_profiles (base_country, base_city);

-- ---------------------------------------------------------------------------
-- vendor_categories
-- ---------------------------------------------------------------------------
CREATE TABLE vendor_categories (
    id          uuid PRIMARY KEY,
    vendor_id   uuid    NOT NULL REFERENCES vendor_profiles (account_id),
    category_id uuid    NOT NULL REFERENCES categories (id),
    is_primary  boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    deleted_at  timestamptz,
    CONSTRAINT ux_vendor_categories UNIQUE (vendor_id, category_id)
);

CREATE UNIQUE INDEX ux_vendor_categories_primary
    ON vendor_categories (vendor_id) WHERE is_primary AND deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- service_areas
-- ---------------------------------------------------------------------------
CREATE TABLE service_areas (
    id               uuid PRIMARY KEY,
    vendor_id        uuid        NOT NULL REFERENCES vendor_profiles (account_id),
    mode             varchar(12) NOT NULL CHECK (mode IN ('CITY_RADIUS', 'REGION')),
    city             varchar(120) NOT NULL,
    country          varchar(2)  NOT NULL,
    lat              double precision,
    lng              double precision,
    radius_km        integer CHECK (radius_km BETWEEN 1 AND 1000),
    travel_fee_cents bigint CHECK (travel_fee_cents >= 0),
    travel_fee_note  varchar(300),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    deleted_at       timestamptz
);

CREATE INDEX ix_service_areas_vendor ON service_areas (vendor_id);

-- ---------------------------------------------------------------------------
-- packages — Law #1 enforced: price_cents NOT NULL and > 0
-- ---------------------------------------------------------------------------
CREATE TABLE packages (
    id                   uuid PRIMARY KEY,
    vendor_id            uuid         NOT NULL REFERENCES vendor_profiles (account_id),
    category_id          uuid         NOT NULL REFERENCES categories (id),
    title                varchar(160) NOT NULL,
    slug                 varchar(200) NOT NULL,
    description_md       text,
    price_cents          bigint       NOT NULL CHECK (price_cents > 0),
    currency             varchar(3)   NOT NULL CHECK (currency IN ('AUD', 'USD', 'GBP', 'NPR')),
    pricing_model        varchar(12)  NOT NULL DEFAULT 'FLAT'
        CHECK (pricing_model IN ('FLAT', 'PER_HOUR', 'PER_GUEST', 'STARTING_AT')),
    min_guests           integer CHECK (min_guests >= 1),
    max_guests           integer CHECK (max_guests >= 1),
    duration_minutes     integer CHECK (duration_minutes >= 15),
    whats_included_md    text,
    whats_excluded_md    text,
    booking_mode         varchar(8)   NOT NULL DEFAULT 'REQUEST'
        CHECK (booking_mode IN ('INSTANT', 'REQUEST')),
    deposit_pct          smallint     NOT NULL DEFAULT 25 CHECK (deposit_pct BETWEEN 10 AND 100),
    cancellation_policy  varchar(10)  NOT NULL DEFAULT 'MODERATE'
        CHECK (cancellation_policy IN ('FLEXIBLE', 'MODERATE', 'STRICT')),
    status               varchar(10)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    cover_media_id       uuid REFERENCES media_assets (id),
    sort                 integer      NOT NULL DEFAULT 0,
    version              bigint       NOT NULL DEFAULT 0,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    created_by           uuid,
    deleted_at           timestamptz,
    CONSTRAINT ux_packages_vendor_slug UNIQUE (vendor_id, slug)
);

CREATE INDEX ix_packages_vendor ON packages (vendor_id, status);
CREATE INDEX ix_packages_category ON packages (category_id) WHERE status = 'PUBLISHED';

-- Every edit to a published package snapshots its prior commercial terms.
-- Phase 4 bookings reference a concrete package version and copy these values.
CREATE TABLE package_price_versions (
    id                   uuid PRIMARY KEY,
    package_id           uuid         NOT NULL REFERENCES packages (id),
    version              bigint       NOT NULL,
    price_cents          bigint       NOT NULL CHECK (price_cents > 0),
    currency             varchar(3)   NOT NULL,
    pricing_model        varchar(12)  NOT NULL,
    deposit_pct          smallint     NOT NULL,
    cancellation_policy  varchar(10)  NOT NULL,
    booking_mode         varchar(8)   NOT NULL,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ux_package_price_version UNIQUE (package_id, version)
);

-- ---------------------------------------------------------------------------
-- add_ons
-- ---------------------------------------------------------------------------
CREATE TABLE add_ons (
    id            uuid PRIMARY KEY,
    vendor_id     uuid         NOT NULL REFERENCES vendor_profiles (account_id),
    package_id    uuid REFERENCES packages (id),
    title         varchar(160) NOT NULL,
    price_cents   bigint       NOT NULL CHECK (price_cents > 0),
    pricing_model varchar(12)  NOT NULL DEFAULT 'FLAT'
        CHECK (pricing_model IN ('FLAT', 'PER_HOUR', 'PER_GUEST', 'STARTING_AT')),
    max_qty       integer CHECK (max_qty BETWEEN 1 AND 999),
    description   varchar(1000),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    deleted_at    timestamptz
);

CREATE INDEX ix_add_ons_vendor ON add_ons (vendor_id);

-- ---------------------------------------------------------------------------
-- vendor_media
-- ---------------------------------------------------------------------------
CREATE TABLE vendor_media (
    id         uuid PRIMARY KEY,
    vendor_id  uuid        NOT NULL REFERENCES vendor_profiles (account_id),
    media_id   uuid        NOT NULL REFERENCES media_assets (id),
    kind       varchar(10) NOT NULL CHECK (kind IN ('GALLERY', 'COVER', 'LOGO', 'SHOWREEL')),
    caption    varchar(300),
    sort       integer     NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    deleted_at timestamptz
);

CREATE INDEX ix_vendor_media_vendor ON vendor_media (vendor_id, kind);

-- ---------------------------------------------------------------------------
-- verification_documents
-- ---------------------------------------------------------------------------
CREATE TABLE verification_documents (
    id            uuid PRIMARY KEY,
    vendor_id     uuid        NOT NULL REFERENCES vendor_profiles (account_id),
    type          varchar(24) NOT NULL
        CHECK (type IN ('BUSINESS_REGISTRATION', 'GOVERNMENT_ID', 'INSURANCE', 'PORTFOLIO_PROOF')),
    media_id      uuid        NOT NULL REFERENCES media_assets (id),
    status        varchar(10) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    expires_at    timestamptz,
    reviewer_note varchar(1000),
    reviewed_by   uuid REFERENCES accounts (id),
    reviewed_at   timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    deleted_at    timestamptz
);

CREATE INDEX ix_verification_documents_status ON verification_documents (status, created_at);
CREATE INDEX ix_verification_documents_vendor ON verification_documents (vendor_id, type);

-- ---------------------------------------------------------------------------
-- vendor_badges
-- ---------------------------------------------------------------------------
CREATE TABLE vendor_badges (
    id         uuid PRIMARY KEY,
    vendor_id  uuid        NOT NULL REFERENCES vendor_profiles (account_id),
    badge      varchar(20) NOT NULL
        CHECK (badge IN ('IDENTITY_VERIFIED', 'BUSINESS_VERIFIED', 'INSURED', 'TOP_RATED', 'RISING_STAR')),
    granted_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    deleted_at timestamptz,
    CONSTRAINT ux_vendor_badges UNIQUE (vendor_id, badge)
);

-- ---------------------------------------------------------------------------
-- vendor_faqs
-- ---------------------------------------------------------------------------
CREATE TABLE vendor_faqs (
    id         uuid PRIMARY KEY,
    vendor_id  uuid         NOT NULL REFERENCES vendor_profiles (account_id),
    question   varchar(300) NOT NULL,
    answer_md  text         NOT NULL,
    sort       integer      NOT NULL DEFAULT 0,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    created_by uuid,
    deleted_at timestamptz
);

CREATE INDEX ix_vendor_faqs_vendor ON vendor_faqs (vendor_id);

-- ---------------------------------------------------------------------------
-- taxonomy seed (top level + photography/videography children first-class
-- per the supply wedge)
-- ---------------------------------------------------------------------------
INSERT INTO categories (id, slug, name, parent_id, icon, sort) VALUES
    (gen_random_uuid(), 'venues',                  'Venues',                   NULL, 'villa',              1),
    (gen_random_uuid(), 'photography',             'Photography',              NULL, 'photo_camera',       2),
    (gen_random_uuid(), 'videography',             'Videography',              NULL, 'videocam',           3),
    (gen_random_uuid(), 'catering',                'Catering',                 NULL, 'restaurant',         4),
    (gen_random_uuid(), 'music-djs',               'Music & DJs',              NULL, 'music_note',         5),
    (gen_random_uuid(), 'live-entertainment',      'Live Entertainment',       NULL, 'theater_comedy',     6),
    (gen_random_uuid(), 'decor-florals',           'Decor & Florals',          NULL, 'local_florist',      7),
    (gen_random_uuid(), 'planning-coordination',   'Planning & Coordination',  NULL, 'event_note',         8),
    (gen_random_uuid(), 'beauty',                  'Beauty',                   NULL, 'face_retouching_natural', 9),
    (gen_random_uuid(), 'attire',                  'Attire',                   NULL, 'checkroom',          10),
    (gen_random_uuid(), 'invitations-stationery',  'Invitations & Stationery', NULL, 'mail',               11),
    (gen_random_uuid(), 'transport',               'Transport',                NULL, 'directions_car',     12),
    (gen_random_uuid(), 'av-lighting',             'AV & Lighting',            NULL, 'highlight',          13),
    (gen_random_uuid(), 'cakes-desserts',          'Cakes & Desserts',         NULL, 'cake',               14),
    (gen_random_uuid(), 'officiants',              'Officiants',               NULL, 'record_voice_over',  15),
    (gen_random_uuid(), 'rentals-equipment',       'Rentals & Equipment',      NULL, 'chair',              16),
    (gen_random_uuid(), 'photo-booth',             'Photo Booth',              NULL, 'photo_library',      17),
    (gen_random_uuid(), 'security-staffing',       'Security & Staffing',      NULL, 'security',           18),
    (gen_random_uuid(), 'kids-entertainment',      'Kids Entertainment',       NULL, 'child_care',         19),
    (gen_random_uuid(), 'fireworks-effects',       'Fireworks & Effects',      NULL, 'flare',              20);

INSERT INTO categories (id, slug, name, parent_id, icon, sort)
SELECT gen_random_uuid(), child.slug, child.name, parent.id, parent.icon, child.sort
FROM (VALUES
    ('wedding-photography', 'Wedding Photography', 'photography', 1),
    ('event-photography',   'Event Photography',   'photography', 2),
    ('studio-photography',  'Studio Photography',  'photography', 3),
    ('mua',                 'Makeup Artists',      'beauty',      1),
    ('hair',                'Hair Styling',        'beauty',      2)
) AS child(slug, name, parent_slug, sort)
JOIN categories parent ON parent.slug = child.parent_slug;

UPDATE app_config
SET value = 'true'::jsonb, updated_at = now()
WHERE key = 'feature.vendor_onboarding';
