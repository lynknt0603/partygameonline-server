package com.partygameonline.ranking.application;

import com.partygameonline.game.liarsnumber.LiarsNumberGameManifest;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberGameState;
import com.partygameonline.ranking.infrastructure.UserGameStatisticEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Fixed room-size ELO settlement for Liar's Number. */
@Component
public final class LiarsNumberGameEloPolicy implements GameEloPolicy {

    private static final int WIN_REWARD = 10;

    @Override
    public String gameCode() {
        return LiarsNumberGameManifest.ID;
    }

    @Override
    public EloRatingService.EloMatchResult calculateRound(
            List<EloRatingService.PlayerOutcome> outcomes,
            Map<String, Integer> ratings
    ) {
        return calculateMatch(outcomes, ratings, null);
    }

    @Override
    public EloRatingService.EloMatchResult calculateMatch(
            List<EloRatingService.PlayerOutcome> outcomes,
            Map<String, Integer> ratings,
            Object gameState
    ) {
        int playerCount = gameState instanceof LiarsNumberGameState state
                ? state.getPlayerCount()
                : outcomes.size();
        int loserDelta = -playerCount * WIN_REWARD;
        Map<String, EloRatingService.EloChange> changes = new LinkedHashMap<>();
        for (EloRatingService.PlayerOutcome outcome : outcomes) {
            int oldElo = ratings.getOrDefault(outcome.playerId(), EloRatingService.DEFAULT_ELO);
            int requestedDelta = outcome.winner() ? WIN_REWARD : loserDelta;
            int newElo = Math.max(EloRatingService.MIN_ELO, oldElo + requestedDelta);
            // Keep the requested loss for the result screen while persisting
            // the rating floor through applyDelta on the entity.
            changes.put(outcome.playerId(), new EloRatingService.EloChange(
                    outcome.playerId(), outcome.winner(), oldElo, requestedDelta, newElo
            ));
        }
        double average = ratings.values().stream().mapToInt(Integer::intValue).average()
                .orElse(EloRatingService.DEFAULT_ELO);
        return new EloRatingService.EloMatchResult(changes, average);
    }

    @Override
    public EloRatingService.EloMatchResult calculateForfeit(String playerId, int currentElo) {
        int delta = -WIN_REWARD;
        return new EloRatingService.EloMatchResult(Map.of(
                playerId,
                new EloRatingService.EloChange(
                        playerId, false, currentElo, delta, Math.max(EloRatingService.MIN_ELO, currentElo + delta)
                )
        ), currentElo);
    }

    @Override
    public void applyDelta(UserGameStatisticEntity statistic, int delta) {
        statistic.applyRatingDelta(delta);
    }
}
