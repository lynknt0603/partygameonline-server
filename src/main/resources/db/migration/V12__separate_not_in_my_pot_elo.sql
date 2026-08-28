ALTER TABLE user_game_statistic
    ADD COLUMN elo_not_in_my_pot INTEGER NOT NULL DEFAULT 5000,
    ADD COLUMN highest_elo_not_in_my_pot INTEGER NOT NULL DEFAULT 5000;

UPDATE user_game_statistic
SET elo_not_in_my_pot = elo,
    highest_elo_not_in_my_pot = highest_elo
WHERE game_code = 'not-in-my-pot';

ALTER TABLE user_game_statistic
    ADD CONSTRAINT ck_user_game_statistic_elo_not_in_my_pot_non_negative
        CHECK (elo_not_in_my_pot >= 0),
    ADD CONSTRAINT ck_user_game_statistic_highest_elo_not_in_my_pot_non_negative
        CHECK (highest_elo_not_in_my_pot >= 0);
