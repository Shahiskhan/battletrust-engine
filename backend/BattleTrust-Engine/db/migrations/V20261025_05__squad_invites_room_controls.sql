-- One-time upgrade after V20261024_04. Review/run with your migration runner before prod validation.
ALTER TABLE tournaments ADD COLUMN archived BIT NOT NULL DEFAULT b'0',
 ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
 ADD COLUMN room_chat_enabled BIT NOT NULL DEFAULT b'1',
 ADD COLUMN room_invite_token VARCHAR(36) NULL;
CREATE UNIQUE INDEX uk_room_invite_token ON tournaments(room_invite_token);
ALTER TABLE registrations ADD COLUMN squad_invite_token VARCHAR(36) NULL;
CREATE UNIQUE INDEX uk_squad_invite_token ON registrations(squad_invite_token);
-- No ledger rows or financial contracts are deleted or rewritten.
ALTER TABLE users ADD COLUMN demo_account BIT NOT NULL DEFAULT b'0';
