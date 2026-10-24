-- One-time MySQL 8 migration from the preceding staking/voucher schema.
-- Apply under your migration runner before starting the prod profile (ddl-auto=validate).
-- Development creates new fields through Hibernate and repairs the old NOT NULL constraint at startup.
CREATE TABLE financial_policy (
 id BIGINT NOT NULL PRIMARY KEY,
 platform_fee_percent DECIMAL(5,2) NOT NULL,
 automatic_payouts_enabled BIT NOT NULL,
 version BIGINT NOT NULL,
 updated_by VARCHAR(255)
) ENGINE=InnoDB;
INSERT INTO financial_policy(id,platform_fee_percent,automatic_payouts_enabled,version) VALUES(1,5.00,b'1',0);
ALTER TABLE tournaments ADD COLUMN automatic_payouts_enabled BIT NOT NULL DEFAULT b'0';
ALTER TABLE payment_vouchers MODIFY COLUMN registration_id BIGINT NULL;
ALTER TABLE payment_vouchers ADD COLUMN tournament_id BIGINT NULL, ADD COLUMN inventory_number INT NULL;
UPDATE payment_vouchers v JOIN registrations r ON r.id=v.registration_id SET v.tournament_id=r.tournament_id WHERE v.tournament_id IS NULL;
CREATE UNIQUE INDEX uk_voucher_inventory ON payment_vouchers(tournament_id,inventory_number);
ALTER TABLE registration_squad_members ADD COLUMN wallet_user_id BIGINT NULL,
 ADD COLUMN original_guest_user_id BIGINT NULL, ADD COLUMN member_claim_ticket VARCHAR(36) NULL;
CREATE UNIQUE INDEX uk_member_claim_ticket ON registration_squad_members(member_claim_ticket);
