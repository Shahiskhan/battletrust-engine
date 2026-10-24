-- One-time upgrade from the preceding team-finance schema. New and legacy non-admin accounts require verification.
ALTER TABLE users ADD COLUMN email_verified BIT NOT NULL DEFAULT b'0';
CREATE TABLE email_verification (
 user_id BIGINT NOT NULL PRIMARY KEY,
 token_hash VARCHAR(64) NOT NULL UNIQUE,
 expires_at DATETIME(6) NOT NULL,
 last_sent_at DATETIME(6) NOT NULL,
 used_at DATETIME(6) NULL
) ENGINE=InnoDB;
-- The configured bootstrap admin is marked verified by the trusted initializer.
-- Do not bulk-mark other accounts verified; they must prove mailbox ownership.
