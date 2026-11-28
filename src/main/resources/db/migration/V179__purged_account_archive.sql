-- ============================================================
-- V179 -- Archive for data removed when an account is purged
--
-- When a self-deleted account is purged, its bank accounts, KYC documents and
-- change-request documents are no longer left in the live tables. They are MOVED
-- here instead of being destroyed, so an admin can later decide whether to keep
-- them or delete them permanently.
--
--   purged_account_archive       one header row per purged user
--   purged_account_archive_item  one row per archived thing (bank account, document, ...)
--
-- Nothing in the live application reads these tables. They are linked to the rest of
-- the system by plain ids only (user_id, deleted_account_log_id).
-- ============================================================

CREATE TABLE IF NOT EXISTS purged_account_archive (
    id                      UUID NOT NULL,
    created_at              TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at              TIMESTAMP(6) NULL,
    version                 BIGINT NOT NULL DEFAULT 0,

    user_id                 UUID NOT NULL,
    role                    VARCHAR(20) NOT NULL,
    deleted_account_log_id  UUID NULL,
    status                  VARCHAR(30) NOT NULL DEFAULT 'ARCHIVED',   -- ARCHIVED | PERMANENTLY_DELETED
    archived_at             TIMESTAMP(6) NOT NULL,
    permanently_deleted_at  TIMESTAMP(6) NULL,
    permanently_deleted_by  UUID NULL,
    deletion_note           VARCHAR(500) NULL,

    CONSTRAINT pk_purged_account_archive PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_purged_account_archive_user
    ON purged_account_archive (user_id);
CREATE INDEX IF NOT EXISTS idx_purged_account_archive_log
    ON purged_account_archive (deleted_account_log_id);

CREATE TABLE IF NOT EXISTS purged_account_archive_item (
    id              UUID NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at      TIMESTAMP(6) NULL,
    version         BIGINT NOT NULL DEFAULT 0,

    archive_id      UUID NOT NULL,
    item_type       VARCHAR(40) NOT NULL,   -- BANK_ACCOUNT | PROFILE_BANK_DETAILS | EMERGENCY_CONTACT | VENDOR_DOCUMENT | DELIVERY_DOCUMENT | CHANGE_REQUEST
    source_id       UUID NULL,              -- id of the original row, when it had one
    payload         TEXT NOT NULL,          -- JSON of the archived values
    file_url        VARCHAR(500) NULL,      -- Cloudinary file kept for this item (not deleted at purge)
    file_public_id  VARCHAR(500) NULL,

    CONSTRAINT pk_purged_account_archive_item PRIMARY KEY (id),
    CONSTRAINT fk_purged_archive_item_archive FOREIGN KEY (archive_id) REFERENCES purged_account_archive (id)
);

CREATE INDEX IF NOT EXISTS idx_purged_archive_item_archive
    ON purged_account_archive_item (archive_id);
