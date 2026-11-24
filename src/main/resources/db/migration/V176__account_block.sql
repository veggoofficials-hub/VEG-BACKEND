-- ============================================================
-- V176 -- Block / unblock with a reason (Customer / Vendor / Delivery)
--
-- BEFORE: users.is_blocked was the whole story: no reason, no time, no admin.
-- AFTER:  the CURRENT block (reason, time, admin, payout hold) lives on the user row,
--         and every block / unblock is kept forever in account_block_log.
--
-- A block is "restricted mode": the person can still log in and finish work already
-- in progress, but cannot start new work (see AccountAccessService).
-- ============================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS blocked_reason      VARCHAR(500) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS blocked_at          TIMESTAMP(6) NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS blocked_by_admin_id UUID         NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS payouts_on_hold     BOOLEAN      NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS account_block_log (
    id          UUID NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at  TIMESTAMP(6) NULL,
    version     BIGINT NOT NULL DEFAULT 0,

    user_id     UUID NOT NULL,
    role        VARCHAR(50) NOT NULL,
    action      VARCHAR(10) NOT NULL CHECK (action IN ('BLOCK', 'UNBLOCK')),

    -- BLOCK: the reason shown to the person.  UNBLOCK: the optional admin note.
    reason      VARCHAR(500),
    payouts_held BOOLEAN NOT NULL DEFAULT FALSE,

    admin_id    UUID NULL,
    admin_label VARCHAR(255),

    -- Snapshot so the row stays readable even if the account is deleted later.
    phone       VARCHAR(64),
    full_name   VARCHAR(255),

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_account_block_log_user         ON account_block_log (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_account_block_log_role_action  ON account_block_log (role, action, created_at DESC);
