ALTER TABLE match_players
    ADD COLUMN score INT,
    ADD COLUMN role VARCHAR(64),
    ADD COLUMN bloodline VARCHAR(32);

CREATE INDEX idx_match_players_player_match
    ON match_players (player_id, match_id);
