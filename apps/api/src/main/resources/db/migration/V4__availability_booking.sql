-- wedjan V4 — availability, calendar sync and the booking aggregate.

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE vendor_profiles
    ADD COLUMN availability_mode varchar(4) NOT NULL DEFAULT 'DATE'
        CHECK (availability_mode IN ('DATE', 'SLOT')),
    ADD COLUMN timezone varchar(64) NOT NULL DEFAULT 'UTC',
    ADD COLUMN calendar_export_token uuid NOT NULL DEFAULT gen_random_uuid();
-- Preserve venue-local dates for the two launch corridors when V4 is applied
-- to vendor listings that already existed before timezone was vendor-owned.
UPDATE vendor_profiles
SET timezone = CASE
    WHEN base_country = 'NP' THEN 'Asia/Kathmandu'
    WHEN base_country = 'AU' AND lower(base_city) = 'melbourne' THEN 'Australia/Melbourne'
    ELSE timezone
END;
CREATE UNIQUE INDEX ux_vendor_calendar_export_token
    ON vendor_profiles (calendar_export_token);

ALTER TABLE packages
    ADD COLUMN allow_same_day boolean NOT NULL DEFAULT false;
ALTER TABLE package_price_versions
    ADD COLUMN allow_same_day boolean NOT NULL DEFAULT false;

CREATE TABLE availability_rules (
    id           uuid PRIMARY KEY,
    vendor_id    uuid        NOT NULL REFERENCES vendor_profiles (account_id) ON DELETE CASCADE,
    weekday      smallint    NOT NULL CHECK (weekday BETWEEN 0 AND 6),
    is_available boolean     NOT NULL DEFAULT true,
    slots        jsonb       NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(slots) = 'array'),
    jobs_per_day smallint    NOT NULL DEFAULT 1 CHECK (jobs_per_day BETWEEN 1 AND 100),
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    created_by   uuid,
    deleted_at   timestamptz
);
CREATE UNIQUE INDEX ux_availability_rules_vendor_weekday
    ON availability_rules (vendor_id, weekday) WHERE deleted_at IS NULL;

CREATE TABLE external_calendars (
    id              uuid PRIMARY KEY,
    vendor_id       uuid         NOT NULL REFERENCES vendor_profiles (account_id) ON DELETE CASCADE,
    ics_url         varchar(2048) NOT NULL,
    last_synced_at  timestamptz,
    sync_started_at timestamptz,
    sync_status     varchar(10)   NOT NULL DEFAULT 'PENDING'
        CHECK (sync_status IN ('PENDING', 'HEALTHY', 'DEGRADED')),
    etag            varchar(500),
    last_modified   varchar(200),
    last_error      varchar(1000),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    deleted_at      timestamptz
);
CREATE UNIQUE INDEX ux_external_calendars_vendor_url
    ON external_calendars (vendor_id, ics_url) WHERE deleted_at IS NULL;
CREATE INDEX ix_external_calendars_sync
    ON external_calendars (last_synced_at, sync_started_at) WHERE deleted_at IS NULL;

CREATE TABLE availability_exceptions (
    id                   uuid PRIMARY KEY,
    vendor_id            uuid        NOT NULL REFERENCES vendor_profiles (account_id) ON DELETE CASCADE,
    date                 date        NOT NULL,
    type                 varchar(12) NOT NULL
        CHECK (type IN ('BLACKOUT', 'EXTRA_OPEN', 'CUSTOM_SLOTS')),
    slots                jsonb       NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(slots) = 'array'),
    note                 varchar(500),
    source               varchar(8)  NOT NULL DEFAULT 'MANUAL'
        CHECK (source IN ('MANUAL', 'ICS')),
    source_ref           varchar(500),
    external_calendar_id uuid REFERENCES external_calendars (id) ON DELETE CASCADE,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    created_by           uuid,
    deleted_at           timestamptz
);
CREATE UNIQUE INDEX ux_availability_exceptions_manual
    ON availability_exceptions (vendor_id, date)
    WHERE source = 'MANUAL' AND deleted_at IS NULL;
CREATE UNIQUE INDEX ux_availability_exceptions_ics
    ON availability_exceptions (external_calendar_id, date, source_ref)
    WHERE source = 'ICS' AND deleted_at IS NULL;
CREATE INDEX ix_availability_exceptions_vendor_date
    ON availability_exceptions (vendor_id, date) WHERE deleted_at IS NULL;

CREATE SEQUENCE booking_code_seq START WITH 1;

CREATE TABLE bookings (
    id                       uuid PRIMARY KEY,
    code                     varchar(20)  NOT NULL UNIQUE,
    idempotency_key          varchar(100) NOT NULL,
    idempotency_request_hash varchar(64)  NOT NULL,
    customer_id              uuid         NOT NULL REFERENCES accounts (id),
    vendor_id                uuid         NOT NULL REFERENCES vendor_profiles (account_id),
    package_id               uuid         NOT NULL REFERENCES packages (id),
    package_version          bigint       NOT NULL,
    event_id                 uuid,
    event_date               date         NOT NULL,
    start_time               time,
    end_time                 time,
    event_timezone           varchar(64)  NOT NULL,
    event_start_at           timestamptz  NOT NULL,
    event_end_at             timestamptz  NOT NULL,
    event_window             tstzrange GENERATED ALWAYS AS
                                 (tstzrange(event_start_at, event_end_at, '[)')) STORED,
    availability_mode_snap   varchar(4)   NOT NULL CHECK (availability_mode_snap IN ('DATE', 'SLOT')),
    capacity_slot            smallint     NOT NULL DEFAULT 1 CHECK (capacity_slot BETWEEN 1 AND 100),
    guests                   integer      CHECK (guests >= 1),
    venue_location_mode      varchar(6)   NOT NULL CHECK (venue_location_mode IN ('VENDOR', 'TRAVEL')),
    venue_address            varchar(500),
    venue_city               varchar(120),
    venue_country            varchar(2),
    venue_lat                double precision CHECK (venue_lat BETWEEN -90 AND 90),
    venue_lng                double precision CHECK (venue_lng BETWEEN -180 AND 180),
    notes                    varchar(3000),
    package_title_snap       varchar(160) NOT NULL,
    package_terms_snap       jsonb        NOT NULL DEFAULT '{}'::jsonb,
    currency                 varchar(3)   NOT NULL,
    subtotal_cents           bigint       NOT NULL CHECK (subtotal_cents > 0),
    addons_cents             bigint       NOT NULL DEFAULT 0 CHECK (addons_cents >= 0),
    travel_fee_cents         bigint       NOT NULL DEFAULT 0 CHECK (travel_fee_cents >= 0),
    discount_cents           bigint       NOT NULL DEFAULT 0 CHECK (discount_cents >= 0),
    total_cents              bigint       NOT NULL CHECK (total_cents > 0),
    paid_cents               bigint       NOT NULL DEFAULT 0 CHECK (paid_cents >= 0),
    deposit_pct              smallint     NOT NULL CHECK (deposit_pct BETWEEN 10 AND 100),
    cancellation_policy_snap varchar(10)  NOT NULL
        CHECK (cancellation_policy_snap IN ('FLEXIBLE', 'MODERATE', 'STRICT')),
    status                   varchar(24)  NOT NULL
        CHECK (status IN ('DRAFT', 'REQUESTED', 'VENDOR_ACCEPTED', 'PENDING_PAYMENT',
            'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED_BY_CUSTOMER',
            'CANCELLED_BY_VENDOR', 'DECLINED', 'EXPIRED', 'DISPUTED')),
    hold_expires_at          timestamptz,
    sla_due_at               timestamptz,
    payment_due_at           timestamptz,
    requested_at             timestamptz,
    accepted_at              timestamptz,
    confirmed_at             timestamptz,
    completed_at             timestamptz,
    dispute_window_ends_at   timestamptz,
    cancelled_at             timestamptz,
    cancel_reason            varchar(1000),
    vendor_penalty_flag      boolean      NOT NULL DEFAULT false,
    reschedule_count         smallint     NOT NULL DEFAULT 0 CHECK (reschedule_count BETWEEN 0 AND 1),
    version                  bigint       NOT NULL DEFAULT 0,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    created_by               uuid,
    deleted_at               timestamptz,
    CONSTRAINT ck_booking_times CHECK (event_end_at > event_start_at),
    CONSTRAINT ck_booking_venue_region CHECK (
        (venue_city IS NULL AND venue_country IS NULL)
          OR (venue_city IS NOT NULL AND venue_city <> '' AND venue_country ~ '^[A-Z]{2}$')
    ),
    CONSTRAINT ck_booking_venue_coordinates CHECK (
        (venue_lat IS NULL AND venue_lng IS NULL)
          OR (venue_lat IS NOT NULL AND venue_lng IS NOT NULL)
    ),
    CONSTRAINT ck_booking_venue_location CHECK (
        (venue_location_mode = 'VENDOR' AND venue_city IS NOT NULL AND venue_country IS NOT NULL)
          OR (venue_location_mode = 'TRAVEL' AND
              ((venue_city IS NOT NULL AND venue_country IS NOT NULL)
                OR (venue_lat IS NOT NULL AND venue_lng IS NOT NULL)))
    ),
    CONSTRAINT ck_booking_paid CHECK (paid_cents <= total_cents),
    CONSTRAINT ck_booking_total CHECK
        (total_cents = subtotal_cents + addons_cents + travel_fee_cents - discount_cents)
);
CREATE INDEX ix_bookings_customer ON bookings (customer_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_bookings_customer_idempotency
    ON bookings (customer_id, idempotency_key) WHERE deleted_at IS NULL;
CREATE INDEX ix_bookings_vendor_inbox ON bookings (vendor_id, status, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_bookings_vendor_date_active ON bookings (vendor_id, event_date, status)
    WHERE status IN ('PENDING_PAYMENT', 'CONFIRMED', 'IN_PROGRESS') AND deleted_at IS NULL;
CREATE INDEX ix_bookings_automation ON bookings (status, sla_due_at, payment_due_at, event_start_at, event_end_at);
-- Capacity-slot allocation lets DATE vendors safely opt into jobs_per_day > 1.
CREATE UNIQUE INDEX ux_bookings_date_capacity
    ON bookings (vendor_id, event_date, capacity_slot)
    WHERE availability_mode_snap = 'DATE'
      AND status IN ('PENDING_PAYMENT', 'CONFIRMED', 'IN_PROGRESS')
      AND deleted_at IS NULL;
ALTER TABLE bookings ADD CONSTRAINT ex_bookings_slot_overlap
    EXCLUDE USING gist (vendor_id WITH =, event_window WITH &&)
    WHERE (availability_mode_snap = 'SLOT'
      AND status IN ('PENDING_PAYMENT', 'CONFIRMED', 'IN_PROGRESS')
      AND deleted_at IS NULL);

CREATE TABLE booking_holds (
    id                     uuid PRIMARY KEY,
    booking_id             uuid         NOT NULL UNIQUE REFERENCES bookings (id) ON DELETE CASCADE,
    vendor_id              uuid         NOT NULL REFERENCES vendor_profiles (account_id),
    customer_id            uuid         NOT NULL REFERENCES accounts (id),
    event_date             date         NOT NULL,
    event_start_at         timestamptz  NOT NULL,
    event_end_at           timestamptz  NOT NULL,
    event_window           tstzrange GENERATED ALWAYS AS
                               (tstzrange(event_start_at, event_end_at, '[)')) STORED,
    availability_mode_snap varchar(4)   NOT NULL CHECK (availability_mode_snap IN ('DATE', 'SLOT')),
    capacity_slot          smallint     NOT NULL CHECK (capacity_slot BETWEEN 1 AND 100),
    token_hash             varchar(64)  NOT NULL,
    expires_at             timestamptz  NOT NULL,
    released_at            timestamptz,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_booking_hold_times CHECK (event_end_at > event_start_at)
);
CREATE INDEX ix_booking_holds_expiry ON booking_holds (expires_at) WHERE released_at IS NULL;
CREATE INDEX ix_booking_holds_vendor_date_live ON booking_holds (vendor_id, event_date, expires_at)
    WHERE released_at IS NULL;
CREATE UNIQUE INDEX ux_booking_holds_date_capacity
    ON booking_holds (vendor_id, event_date, capacity_slot)
    WHERE availability_mode_snap = 'DATE' AND released_at IS NULL;
ALTER TABLE booking_holds ADD CONSTRAINT ex_booking_holds_slot_overlap
    EXCLUDE USING gist (vendor_id WITH =, event_window WITH &&)
    WHERE (availability_mode_snap = 'SLOT' AND released_at IS NULL);

CREATE TABLE booking_add_ons (
    booking_id       uuid         NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    add_on_id        uuid         NOT NULL REFERENCES add_ons (id),
    title_snap       varchar(160) NOT NULL,
    qty              integer      NOT NULL CHECK (qty BETWEEN 1 AND 999),
    price_cents_snap bigint       NOT NULL CHECK (price_cents_snap > 0),
    total_cents_snap bigint       NOT NULL CHECK (total_cents_snap > 0),
    PRIMARY KEY (booking_id, add_on_id)
);

CREATE TABLE booking_events (
    id          uuid PRIMARY KEY,
    booking_id  uuid         NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    from_status varchar(24),
    to_status   varchar(24)   NOT NULL,
    actor       varchar(12)   NOT NULL CHECK (actor IN ('CUSTOMER', 'VENDOR', 'SYSTEM', 'ADMIN')),
    actor_id    uuid REFERENCES accounts (id),
    event_type  varchar(40)   NOT NULL DEFAULT 'STATUS_TRANSITION',
    metadata    jsonb         NOT NULL DEFAULT '{}'::jsonb,
    created_at  timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX ix_booking_events_timeline ON booking_events (booking_id, created_at, id);

CREATE FUNCTION prevent_booking_event_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'booking_events is append-only';
END $$;
CREATE TRIGGER booking_events_append_only
    BEFORE UPDATE OR DELETE ON booking_events
    FOR EACH ROW EXECUTE FUNCTION prevent_booking_event_mutation();

CREATE TABLE booking_reschedules (
    id                  uuid PRIMARY KEY,
    booking_id          uuid        NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    proposed_by         uuid        NOT NULL REFERENCES accounts (id),
    event_date          date        NOT NULL,
    start_time          time,
    end_time            time,
    event_timezone      varchar(64) NOT NULL,
    customer_approved   boolean     NOT NULL DEFAULT false,
    vendor_approved     boolean     NOT NULL DEFAULT false,
    status              varchar(10) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED')),
    created_at          timestamptz NOT NULL DEFAULT now(),
    resolved_at         timestamptz
);
CREATE UNIQUE INDEX ux_booking_reschedule_pending
    ON booking_reschedules (booking_id) WHERE status = 'PENDING';

CREATE TABLE refund_calculations (
    id                   uuid PRIMARY KEY,
    booking_id           uuid        NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    policy_snap          varchar(10) NOT NULL,
    days_to_event        integer     NOT NULL,
    basis_cents          bigint      NOT NULL CHECK (basis_cents >= 0),
    refund_percent       smallint    NOT NULL CHECK (refund_percent BETWEEN 0 AND 100),
    refundable_cents     bigint      NOT NULL CHECK (refundable_cents >= 0),
    non_refundable_cents bigint      NOT NULL CHECK (non_refundable_cents >= 0),
    vendor_initiated     boolean     NOT NULL,
    status               varchar(10) NOT NULL DEFAULT 'PREVIEWED'
        CHECK (status IN ('PREVIEWED', 'FINAL', 'EXECUTED', 'VOID')),
    created_at           timestamptz NOT NULL DEFAULT now(),
    finalized_at         timestamptz,
    CONSTRAINT ck_refund_calculation_total
        CHECK (refundable_cents + non_refundable_cents = basis_cents)
);
CREATE UNIQUE INDEX ux_refund_calculation_final
    ON refund_calculations (booking_id) WHERE status = 'FINAL';

-- Weekly defaults are deliberately present for existing verified listings so
-- enabling Phase 4 does not silently remove the Phase 3 catalogue from search.
INSERT INTO availability_rules (id, vendor_id, weekday, is_available, slots, jobs_per_day)
SELECT gen_random_uuid(), vp.account_id, d.weekday, true, '[]'::jsonb, 1
FROM vendor_profiles vp CROSS JOIN generate_series(0, 6) AS d(weekday);

CREATE FUNCTION seed_vendor_availability_defaults() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO availability_rules (id, vendor_id, weekday, is_available, slots, jobs_per_day)
    SELECT gen_random_uuid(), NEW.account_id, d.weekday, true, '[]'::jsonb, 1
    FROM generate_series(0, 6) AS d(weekday);
    RETURN NEW;
END $$;
CREATE TRIGGER vendor_availability_defaults
    AFTER INSERT ON vendor_profiles
    FOR EACH ROW EXECUTE FUNCTION seed_vendor_availability_defaults();

-- Public/search-compatible derived day status. PostgreSQL DOW is Sunday=0,
-- matching the API contract.
CREATE OR REPLACE FUNCTION get_availability(p_vendor_id uuid, p_from date, p_to date)
RETURNS TABLE(date date, status varchar, capacity integer, occupied integer)
LANGUAGE sql STABLE AS $$
WITH days AS (
    SELECT d::date AS day FROM generate_series(p_from, p_to, interval '1 day') d
), facts AS (
    SELECT days.day,
        vp.availability_mode,
        COALESCE(ar.is_available, true) weekly_open,
        COALESCE(ar.jobs_per_day, 1)::integer jobs_per_day,
        COALESCE(ar.slots, '[]'::jsonb) weekly_slots,
        bool_or(ae.type = 'BLACKOUT') FILTER (WHERE ae.id IS NOT NULL) has_blackout,
        bool_or(ae.type = 'EXTRA_OPEN') FILTER (WHERE ae.id IS NOT NULL) has_extra_open,
        COALESCE((array_agg(ae.slots) FILTER (WHERE ae.type = 'CUSTOM_SLOTS'))[1], '[]'::jsonb) custom_slots
    FROM days
    JOIN vendor_profiles vp ON vp.account_id = p_vendor_id AND vp.deleted_at IS NULL
    LEFT JOIN availability_rules ar ON ar.vendor_id = vp.account_id
        AND ar.weekday = extract(dow FROM days.day)::smallint AND ar.deleted_at IS NULL
    LEFT JOIN availability_exceptions ae ON ae.vendor_id = vp.account_id
        AND ae.date = days.day AND ae.deleted_at IS NULL
    GROUP BY days.day, vp.availability_mode, ar.is_available, ar.jobs_per_day, ar.slots
), counts AS (
    SELECT facts.*,
        CASE WHEN availability_mode = 'SLOT' THEN
            jsonb_array_length(CASE WHEN jsonb_array_length(custom_slots) > 0
                THEN custom_slots ELSE weekly_slots END)
            ELSE jobs_per_day END AS day_capacity,
        (SELECT count(*)::integer FROM (
            SELECT b.id AS booking_id FROM bookings b
             WHERE b.vendor_id = p_vendor_id AND b.event_date = facts.day
               AND b.status IN ('PENDING_PAYMENT', 'CONFIRMED', 'IN_PROGRESS')
               AND b.deleted_at IS NULL
            UNION
            SELECT h.booking_id FROM booking_holds h
             WHERE h.vendor_id = p_vendor_id AND h.event_date = facts.day
               AND h.released_at IS NULL AND h.expires_at > now()
        ) occupied_bookings) AS day_occupied
    FROM facts
)
SELECT day,
    CASE
      WHEN COALESCE(has_blackout, false) THEN 'BLACKED_OUT'
      WHEN NOT weekly_open AND NOT COALESCE(has_extra_open, false)
           AND jsonb_array_length(custom_slots) = 0 THEN 'BLACKED_OUT'
      WHEN availability_mode = 'SLOT' AND day_capacity = 0 THEN 'BLACKED_OUT'
      WHEN day_occupied >= day_capacity THEN 'BOOKED'
      WHEN day_occupied > 0 THEN 'LIMITED'
      ELSE 'AVAILABLE'
    END::varchar,
    day_capacity,
    day_occupied
FROM counts ORDER BY day
$$;

UPDATE app_config
SET value = 'true'::jsonb, updated_at = now(), description = 'Phase 4 availability and booking engine'
WHERE key = 'feature.bookings';
