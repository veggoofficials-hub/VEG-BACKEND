-- ═══════════════════════════════════════════════════════════════════════
-- Vendor account deletion used to leave the shop's listings published while
-- the shop itself was soft-deleted. Product pages then crashed with
-- ENTITY_NOT_FOUND ("Unable to find ...Shop with id ...").
--
-- 1) unlisted_by_account_deletion: marks listings switched off BY the account
--    deletion, so reactivation can relist exactly those (and not ones the
--    vendor had unlisted on purpose).
-- 2) Heal existing data: unlist (and flag) every listed row of an already
--    soft-deleted shop. For shops still inside their reactivation window the
--    flag lets them come back on reactivation; for purged shops it is inert.
-- ═══════════════════════════════════════════════════════════════════════

ALTER TABLE vendor_listings
    ADD COLUMN IF NOT EXISTS unlisted_by_account_deletion BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE vendor_listings
SET    is_listed = FALSE,
       unlisted_by_account_deletion = TRUE,
       updated_at = NOW(),
       version = version + 1
WHERE  is_listed = TRUE
  AND  deleted_at IS NULL
  AND  shop_id IN (SELECT id FROM vendor_shops WHERE deleted_at IS NOT NULL);
