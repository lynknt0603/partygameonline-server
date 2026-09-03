-- A standalone FORFEIT is an anti-abuse rating penalty, not a completed game.
-- Rebuild NOB match totals from the authoritative match history so production
-- rows created by the old behaviour no longer appear as completed losses.
WITH completed_nob AS (
    SELECT
        CASE
            WHEN account.user_key IS NOT NULL THEN account.user_key
            ELSE match_player.player_id
        END AS player_id,
        COUNT(DISTINCT match_row.id)::INTEGER AS total_match,
        COUNT(DISTINCT match_row.id) FILTER (
            WHERE UPPER(COALESCE(match_player.result, 'LOSS')) = 'WIN'
        )::INTEGER AS total_win
    FROM match_players match_player
    JOIN matches match_row ON match_row.id = match_player.match_id
    LEFT JOIN users account ON account.id::TEXT = match_player.player_id
    WHERE match_row.game_id = 'night-of-bloodlines'
      AND match_row.finished_at IS NOT NULL
      AND UPPER(COALESCE(match_row.result, 'COMPLETED')) NOT IN (
          'FORFEIT', 'UNRANKED_FORFEIT', 'CANCELLED', 'ABORTED', 'INVALID'
      )
    GROUP BY CASE
        WHEN account.user_key IS NOT NULL THEN account.user_key
        ELSE match_player.player_id
    END
)
UPDATE user_game_statistic statistic
SET total_match = COALESCE(completed.total_match, 0),
    total_win = COALESCE(completed.total_win, 0),
    updated_at = CURRENT_TIMESTAMP
FROM (
    SELECT
        statistic_row.id,
        completed_nob.total_match,
        completed_nob.total_win
    FROM user_game_statistic statistic_row
    LEFT JOIN completed_nob
      ON completed_nob.player_id = statistic_row.user_id
    WHERE statistic_row.game_code = 'night-of-bloodlines'
) completed
WHERE statistic.id = completed.id;
