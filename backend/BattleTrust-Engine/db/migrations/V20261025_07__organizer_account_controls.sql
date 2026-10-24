-- Apply once after _06 before production schema validation.
ALTER TABLE users ADD COLUMN blocked BIT NOT NULL DEFAULT b'0',
 ADD COLUMN block_reason VARCHAR(1000),ADD COLUMN blocked_at DATETIME(6),ADD COLUMN blocked_by VARCHAR(254);
-- Preserve historical wallets/ledger rows. Admin personal gaming APIs are disabled by role rules.
ALTER TABLE payout_approval ADD COLUMN review_note VARCHAR(1000);
