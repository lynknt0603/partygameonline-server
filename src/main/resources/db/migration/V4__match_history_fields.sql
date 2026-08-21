ALTER TABLE matches
    ADD COLUMN room_id VARCHAR(8),
    ADD COLUMN winner_player_id VARCHAR(64),
    ADD COLUMN result VARCHAR(32);

CREATE INDEX idx_matches_finished_at ON matches (finished_at DESC);

ALTER TABLE match_players
    ADD COLUMN result VARCHAR(16);
