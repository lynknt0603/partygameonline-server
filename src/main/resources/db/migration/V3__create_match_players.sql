CREATE TABLE match_players (
    id           UUID        NOT NULL,
    match_id     UUID        NOT NULL,
    user_id      UUID,
    player_id    VARCHAR(64) NOT NULL,
    display_name VARCHAR(32) NOT NULL,
    seat         SMALLINT,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_match_players PRIMARY KEY (id),
    CONSTRAINT fk_match_players_match FOREIGN KEY (match_id) REFERENCES matches (id) ON DELETE CASCADE,
    CONSTRAINT fk_match_players_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_match_players_match_player UNIQUE (match_id, player_id)
);

CREATE INDEX idx_match_players_user_id ON match_players (user_id);
CREATE INDEX idx_match_players_player_id ON match_players (player_id);
