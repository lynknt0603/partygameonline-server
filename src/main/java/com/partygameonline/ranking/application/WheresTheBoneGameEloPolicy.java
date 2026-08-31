package com.partygameonline.ranking.application;

import com.partygameonline.game.wheresthebone.WheresTheBoneGameManifest;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Explicit policy registration for Where's the Bone's standard ELO model. */
@Component
public final class WheresTheBoneGameEloPolicy implements GameEloPolicy {

    private final DefaultGameEloPolicy delegate = new DefaultGameEloPolicy();

    @Override
    public String gameCode() {
        return WheresTheBoneGameManifest.ID;
    }

    @Override
    public EloRatingService.EloMatchResult calculateRound(
            List<EloRatingService.PlayerOutcome> outcomes,
            Map<String, Integer> ratings
    ) {
        return delegate.calculateRound(outcomes, ratings);
    }

    @Override
    public EloRatingService.EloMatchResult calculateMatch(
            List<EloRatingService.PlayerOutcome> outcomes,
            Map<String, Integer> ratings,
            Object gameState
    ) {
        return delegate.calculateMatch(outcomes, ratings, gameState);
    }

    @Override
    public EloRatingService.EloMatchResult calculateForfeit(String playerId, int currentElo) {
        return delegate.calculateForfeit(playerId, currentElo);
    }
}
