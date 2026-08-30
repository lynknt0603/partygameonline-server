-- Not In My Pot has no rating floor. Keep the shared/NOB ELO columns
-- non-negative; this game reads and writes its dedicated column instead.
ALTER TABLE user_game_statistic
    DROP CONSTRAINT IF EXISTS ck_user_game_statistic_elo_not_in_my_pot_non_negative;
