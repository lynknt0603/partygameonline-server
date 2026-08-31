package com.partygameonline.ranking.application;

import com.partygameonline.ranking.infrastructure.UserGameStatisticEntity;
import java.util.List;
import java.util.Map;

/**
 * Per-game ELO rules. Policies only calculate changes; persistence and match
 * bookkeeping remain in {@link EloRatingService}.
 */
public interface GameEloPolicy {

    /**
     * Returns the game id handled by this policy. A {@code null} id denotes
     * the fallback policy for games without a custom rating model.
     */
    String gameCode();

    EloRatingService.EloMatchResult calculateRound(
            List<EloRatingService.PlayerOutcome> outcomes,
            Map<String, Integer> ratings
    );

    EloRatingService.EloMatchResult calculateMatch(
            List<EloRatingService.PlayerOutcome> outcomes,
            Map<String, Integer> ratings,
            Object gameState
    );

    EloRatingService.EloMatchResult calculateForfeit(String playerId, int currentElo);

    /** Applies a calculated delta using this game's rating-floor rule. */
    default void applyDelta(UserGameStatisticEntity statistic, int delta) {
        statistic.applyRatingDelta(delta);
    }
}
