-- ============================================================
-- V180 -- Remember WHICH bank a payout went to, without keeping the full account number
--
-- A purged user's bank account is moved to the archive, so an old payout row could no
-- longer show which account was paid. Each payout now carries the bank name and the
-- last 4 digits (masked -- safe to keep forever), set when the payout is requested.
-- Existing payouts are backfilled from the bank accounts that still exist.
-- ============================================================

ALTER TABLE payout_requests ADD COLUMN IF NOT EXISTS bank_name_snapshot  VARCHAR(100) NULL;
ALTER TABLE payout_requests ADD COLUMN IF NOT EXISTS bank_account_last4  VARCHAR(4)   NULL;

-- Payouts that point at a bank account by id.
UPDATE payout_requests p
SET    bank_name_snapshot = b.bank_name,
       bank_account_last4 = RIGHT(b.account_number, 4)
FROM   user_bank_accounts b
WHERE  p.bank_account_id = b.id
  AND  p.bank_account_last4 IS NULL;

-- Older payouts without a bank_account_id: fall back to the user's own account.
UPDATE payout_requests p
SET    bank_name_snapshot = b.bank_name,
       bank_account_last4 = RIGHT(b.account_number, 4)
FROM   user_bank_accounts b
WHERE  p.bank_account_id IS NULL
  AND  b.user_id = p.user_id
  AND  p.bank_account_last4 IS NULL;
