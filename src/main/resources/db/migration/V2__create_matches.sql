CREATE TABLE matches (
    id          UUID        NOT NULL,
    game_id     VARCHAR(64) NOT NULL,
    started_at  TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_matches PRIMARY KEY (id)
);

CREATE INDEX idx_matches_game_id ON matches (game_id);
CREATE INDEX idx_matches_created_at ON matches (created_at DESC);
