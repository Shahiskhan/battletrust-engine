-- Apply once after _05 using the reviewed migration process before prod ddl validation.
ALTER TABLE tournaments ADD COLUMN planned_rounds INT NOT NULL DEFAULT 0,
 ADD COLUMN registration_open BIT NOT NULL DEFAULT b'1';
ALTER TABLE match_results ADD COLUMN revision INT NOT NULL DEFAULT 1;
ALTER TABLE match_result_rankings ADD COLUMN placement_points INT NOT NULL DEFAULT 0;
CREATE TABLE tournament_rounds (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tournament_id BIGINT NOT NULL,round_number INT NOT NULL,map_name VARCHAR(100) NOT NULL,
 status VARCHAR(16) NOT NULL,room_id VARCHAR(100),room_password VARCHAR(255),
 started_at DATETIME(6),completed_at DATETIME(6),
 UNIQUE KEY uk_round_number(tournament_id,round_number),
 CONSTRAINT fk_round_tournament FOREIGN KEY(tournament_id) REFERENCES tournaments(id)
) ENGINE=InnoDB;
CREATE TABLE match_player_scores (
 match_result_id BIGINT NOT NULL,user_id BIGINT NOT NULL,captain_user_id BIGINT NOT NULL,kills INT NOT NULL,
 CONSTRAINT fk_player_score_result FOREIGN KEY(match_result_id) REFERENCES match_results(id)
) ENGINE=InnoDB;
CREATE TABLE score_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,match_result_id BIGINT NOT NULL,revision INT NOT NULL,
 edited_by VARCHAR(254) NOT NULL,reason VARCHAR(1000) NOT NULL,snapshot LONGTEXT NOT NULL,created_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_score_audit_revision(match_result_id,revision)
) ENGINE=InnoDB;
-- Historical tournaments keep planned_rounds=0 and their original payout/ranking contract.
