-- Registered-player statistics must use users.user_key, which is also the
-- identifier stored in authenticated sessions and match history.
UPDATE user_game_statistic statistic
SET user_id = account.user_key,
    updated_at = CURRENT_TIMESTAMP
FROM users account
WHERE statistic.user_id = account.id::text
  AND NOT EXISTS (
      SELECT 1
      FROM user_game_statistic canonical
      WHERE canonical.user_id = account.user_key
        AND canonical.game_code = statistic.game_code
  );
