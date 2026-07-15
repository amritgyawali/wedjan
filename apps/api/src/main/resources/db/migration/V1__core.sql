-- wedjan V1 — core identity, media, config, audit.
-- Conventions: UUIDv7 ids (generated in the app), UTC timestamptz, soft delete
-- (deleted_at) + audit columns on every table, optimistic locking (version)
-- on mutable aggregates.

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------------
-- accounts
-- ---------------------------------------------------------------------------
CREATE TABLE accounts (
    id                 uuid PRIMARY KEY,
    email              varchar(320) NOT NULL,
    phone              varchar(32),
    password_hash      varchar(255) NOT NULL,
    status             varchar(24)  NOT NULL DEFAULT 'PENDING_VERIFICATION'
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED')),
    locale             varchar(10)  NOT NULL DEFAULT 'en',
    default_currency   varchar(3)   NOT NULL DEFAULT 'AUD'
        CHECK (default_currency IN ('AUD', 'USD', 'GBP', 'NPR')),
    marketing_opt_in   boolean      NOT NULL DEFAULT false,
    version            bigint       NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    created_by         uuid,
    deleted_at         timestamptz
);

CREATE UNIQUE INDEX ux_accounts_email ON accounts (lower(email)) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- account_roles — single account, multiple roles
-- ---------------------------------------------------------------------------
CREATE TABLE account_roles (
    id          uuid PRIMARY KEY,
    account_id  uuid        NOT NULL REFERENCES accounts (id),
    role        varchar(16) NOT NULL
        CHECK (role IN ('CUSTOMER', 'VENDOR', 'FREELANCER', 'ADMIN')),
    granted_at  timestamptz NOT NULL DEFAULT now(),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    deleted_at  timestamptz,
    CONSTRAINT ux_account_roles UNIQUE (account_id, role)
);

CREATE INDEX ix_account_roles_account ON account_roles (account_id);

-- ---------------------------------------------------------------------------
-- refresh_tokens — rotating refresh tokens with family reuse detection
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id            uuid PRIMARY KEY,
    account_id    uuid         NOT NULL REFERENCES accounts (id),
    family_id     uuid         NOT NULL,
    token_hash    varchar(64)  NOT NULL,
    device_name   varchar(120),
    ip            varchar(45),
    expires_at    timestamptz  NOT NULL,
    revoked_at    timestamptz,
    rotated_from  uuid,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    deleted_at    timestamptz
);

CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_account ON refresh_tokens (account_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

-- ---------------------------------------------------------------------------
-- email_verifications — OTP codes for signup / password reset / email change
-- ---------------------------------------------------------------------------
CREATE TABLE email_verifications (
    id           uuid PRIMARY KEY,
    account_id   uuid        NOT NULL REFERENCES accounts (id),
    code_hash    varchar(64) NOT NULL,
    purpose      varchar(20) NOT NULL
        CHECK (purpose IN ('SIGNUP', 'PASSWORD_RESET', 'EMAIL_CHANGE')),
    expires_at   timestamptz NOT NULL,
    consumed_at  timestamptz,
    attempts     smallint    NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    created_by   uuid,
    deleted_at   timestamptz
);

CREATE INDEX ix_email_verifications_account
    ON email_verifications (account_id, purpose);

-- ---------------------------------------------------------------------------
-- profiles — display basics shared by all roles
-- ---------------------------------------------------------------------------
CREATE TABLE profiles (
    account_id       uuid PRIMARY KEY REFERENCES accounts (id),
    display_name     varchar(120),
    avatar_media_id  uuid,
    city             varchar(120),
    country          varchar(2),
    timezone         varchar(64),
    version          bigint      NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    deleted_at       timestamptz
);

-- ---------------------------------------------------------------------------
-- media_assets — object storage references (R2/MinIO behind MediaService)
-- ---------------------------------------------------------------------------
CREATE TABLE media_assets (
    id                uuid PRIMARY KEY,
    owner_account_id  uuid         NOT NULL REFERENCES accounts (id),
    kind              varchar(8)   NOT NULL CHECK (kind IN ('IMAGE', 'VIDEO', 'DOC')),
    storage_key       varchar(512) NOT NULL,
    mime              varchar(255) NOT NULL,
    bytes             bigint       NOT NULL,
    width             integer,
    height            integer,
    blurhash          varchar(120),
    status            varchar(12)  NOT NULL DEFAULT 'UPLOADING'
        CHECK (status IN ('UPLOADING', 'READY', 'BLOCKED')),
    version           bigint       NOT NULL DEFAULT 0,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        uuid,
    deleted_at        timestamptz
);

CREATE UNIQUE INDEX ux_media_assets_storage_key ON media_assets (storage_key);
CREATE INDEX ix_media_assets_owner ON media_assets (owner_account_id);

ALTER TABLE profiles
    ADD CONSTRAINT fk_profiles_avatar_media
    FOREIGN KEY (avatar_media_id) REFERENCES media_assets (id);

-- ---------------------------------------------------------------------------
-- app_config — feature flags & tunables (typed accessor in the app)
-- ---------------------------------------------------------------------------
CREATE TABLE app_config (
    key          varchar(120) PRIMARY KEY,
    value        jsonb        NOT NULL,
    description  varchar(500),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   uuid,
    deleted_at   timestamptz
);

-- ---------------------------------------------------------------------------
-- audit_log — append-only, written asynchronously
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id                uuid PRIMARY KEY,
    actor_account_id  uuid,
    action            varchar(80)  NOT NULL,
    entity_type       varchar(80)  NOT NULL,
    entity_id         varchar(80),
    metadata          jsonb,
    created_at        timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_log_actor ON audit_log (actor_account_id, created_at);
CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id);

-- ---------------------------------------------------------------------------
-- config defaults
-- ---------------------------------------------------------------------------
INSERT INTO app_config (key, value, description) VALUES
    ('take_rate_default', '12', 'Platform take rate (%) deducted vendor-side at escrow release'),
    ('signup_rate_limit_per_minute', '5', 'Max signup attempts per IP per minute'),
    ('login_rate_limit_per_minute', '5', 'Max login attempts per IP per minute'),
    ('feature.mobile_app', 'true', 'Mobile app surfaces enabled'),
    ('feature.vendor_onboarding', 'false', 'Phase 2 — vendor onboarding wizard'),
    ('feature.search', 'false', 'Phase 3 — public vendor search'),
    ('feature.bookings', 'false', 'Phase 4 — booking engine'),
    ('feature.payments', 'false', 'Phase 5 — Stripe Connect payments');
