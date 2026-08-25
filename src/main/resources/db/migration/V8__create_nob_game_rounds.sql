CREATE TABLE nob_game_rounds (
    id                  UUID         NOT NULL,
    game_id             UUID         NOT NULL,
    round_number        INTEGER      NOT NULL,
    player_id           VARCHAR(64)  NOT NULL,
    bloodline           VARCHAR(32),
    result              VARCHAR(16),
    round_result        VARCHAR(32),
    last_hope_triggered BOOLEAN      NOT NULL DEFAULT FALSE,
    score               INTEGER,
    created_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_nob_game_rounds PRIMARY KEY (id),
    CONSTRAINT fk_nob_game_rounds_game FOREIGN KEY (game_id) REFERENCES matches (id) ON DELETE CASCADE,
    CONSTRAINT uq_nob_game_rounds_game_round_player UNIQUE (game_id, round_number, player_id)
);

CREATE INDEX idx_nob_game_rounds_player ON nob_game_rounds (player_id, bloodline);
CREATE INDEX idx_nob_game_rounds_game_round ON nob_game_rounds (game_id, round_number);

-- Preserve the final-round statistics already stored by V7. Older games do not
-- have enough information to reconstruct earlier rounds, so they are represented
-- as one legacy round instead of being dropped from profile statistics.
INSERT INTO nob_game_rounds (
    id,
    game_id,
    round_number,
    player_id,
    bloodline,
    result,
    round_result,
    last_hope_triggered,
    score,
    created_at
)
SELECT
    gen_random_uuid(),
    m.id,
    1,
    p.player_id,
    p.bloodline,
    p.result,
    'LEGACY',
    FALSE,
    p.score,
    COALESCE(p.created_at, m.created_at)
FROM matches m
JOIN match_players p ON p.match_id = m.id
WHERE m.game_id = 'night-of-bloodlines'
  AND m.finished_at IS NOT NULL
  AND p.bloodline IS NOT NULL;
