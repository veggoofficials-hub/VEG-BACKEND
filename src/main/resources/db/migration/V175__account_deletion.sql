-- ============================================================
-- V175 -- Self-service account deletion (Customer / Vendor / Delivery)
--
-- 1. users gets the reactivation-window fields.
-- 2. users.phone is widened: a purged account's phone is replaced by a
--    "deleted_<phone>_<millis>" placeholder, which is longer than 20 characters.
-- 3. deleted_account_log is a permanent, never-purged audit trail of every
--    self-deleted account (written BEFORE the users row is scrubbed, so admin can
--    still see who left, when and why). It also records what became of the account:
--    reactivated_at (the person came back inside the window) and purged_at.
-- ============================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS purge_at  TIMESTAMP(6) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_purged BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users ALTER COLUMN phone TYPE VARCHAR(64);

-- Feeds the 03:00 purge sweep (self-deleted, not yet purged).
CREATE INDEX IF NOT EXISTS idx_users_purge_due
    ON users (purge_at)
    WHERE deleted_at IS NOT NULL AND is_purged = FALSE;

-- Feeds the "logged-out-since" refresh: accounts deleted in the last few hours.
CREATE INDEX IF NOT EXISTS idx_users_deleted_at
    ON users (deleted_at)
    WHERE deleted_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS deleted_account_log (
    id          UUID NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at  TIMESTAMP(6) NULL,
    version     BIGINT NOT NULL DEFAULT 0,

    user_id     UUID NOT NULL,
    role        VARCHAR(50) NOT NULL,

    -- Captured BEFORE scrubbing -- unmasked, per product decision.
    phone       VARCHAR(20) NOT NULL,
    email       VARCHAR(255),
    full_name   VARCHAR(255),

    account_created_at       TIMESTAMP(6) NOT NULL,
    requested_retention_days INT NOT NULL,
    scheduled_purge_at       TIMESTAMP(6) NOT NULL,

    reason       VARCHAR(500),
    initiated_by VARCHAR(20) NOT NULL DEFAULT 'SELF',

    -- customer: orders placed / total spent.  vendor & delivery: orders fulfilled / total earned.
    total_orders INT,
    total_amount NUMERIC(12, 2),

    -- What became of the account.
    reactivated_at TIMESTAMP(6) NULL,
    purged_at      TIMESTAMP(6) NULL,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_deleted_account_log_user       ON deleted_account_log (user_id);
CREATE INDEX IF NOT EXISTS idx_deleted_account_log_phone_role ON deleted_account_log (phone, role);
CREATE INDEX IF NOT EXISTS idx_deleted_account_log_created    ON deleted_account_log (created_at DESC);
