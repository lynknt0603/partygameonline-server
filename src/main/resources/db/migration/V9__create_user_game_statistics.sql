CREATE TABLE user_game_statistics (
    id          UUID         NOT NULL,
    user_id     VARCHAR(64)  NOT NULL,
    game_code   VARCHAR(64)  NOT NULL,
    elo         INTEGER      NOT NULL DEFAULT 5000,
    elo_nob     INTEGER      NOT NULL DEFAULT 5000,
    highest_elo INTEGER      NOT NULL DEFAULT 5000,
    total_match INTEGER      NOT NULL DEFAULT 0,
    total_win   INTEGER      NOT NULL DEFAULT 0,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_user_game_statistics PRIMARY KEY (id),
    CONSTRAINT uq_user_game_statistics_user_game UNIQUE (user_id, game_code),
    CONSTRAINT ck_user_game_statistics_elo_non_negative CHECK (elo >= 0),
    CONSTRAINT ck_user_game_statistics_elo_nob_non_negative CHECK (elo_nob >= 0),
    CONSTRAINT ck_user_game_statistics_highest_elo_non_negative CHECK (highest_elo >= 0),
    CONSTRAINT ck_user_game_statistics_total_match_non_negative CHECK (total_match >= 0),
    CONSTRAINT ck_user_game_statistics_total_win_non_negative CHECK (total_win >= 0)
);

CREATE INDEX idx_user_game_statistics_user ON user_game_statistics (user_id);

ALTER TABLE nob_game_rounds ADD COLUMN elo_delta INTEGER;
