package com.partygameonline.ranking.application;

import com.partygameonline.game.notinmypot.NotInMyPotGameManifest;
import com.partygameonline.game.notinmypot.application.NotInMyPotEloCalculator;
import com.partygameonline.ranking.infrastructure.UserGameStatisticEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Team-pool ELO rules owned by Not In My Pot. */
@Component
public final class NotInMyPotGameEloPolicy implements GameEloPolicy {

    public static final int FORFEIT_PENALTY = 100;

    @Override
    public String gameCode() {
        return NotInMyPotGameManifest.ID;
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
        List<NotInMyPotEloCalculator.PlayerRating> winners = new ArrayList<>();
        List<NotInMyPotEloCalculator.PlayerRating> losers = new ArrayList<>();
        for (EloRatingService.PlayerOutcome outcome : outcomes) {
            NotInMyPotEloCalculator.PlayerRating rating = new NotInMyPotEloCalculator.PlayerRating(
                    outcome.playerId(), ratings.getOrDefault(outcome.playerId(), EloRatingService.DEFAULT_ELO)
            );
            if (outcome.winner()) {
                winners.add(rating);
            } else {
                losers.add(rating);
            }
        }
        if (winners.isEmpty()) {
            return allForfeited(outcomes, ratings);
        }
        if (losers.isEmpty()) {
            return winnersOnly(winners, ratings);
        }
        List<NotInMyPotEloCalculator.EloChange> calculated =
                NotInMyPotEloCalculator.calculateEloChanges(winners, losers);
        Map<String, EloRatingService.EloChange> changes = new LinkedHashMap<>();
        for (NotInMyPotEloCalculator.EloChange change : calculated) {
            changes.put(change.id(), new EloRatingService.EloChange(
                    change.id(), change.eloChange() > 0, change.oldElo(), change.eloChange(), change.newElo()
            ));
        }
        double average = ratings.values().stream().mapToInt(Integer::intValue).average().orElse(5000);
        return new EloRatingService.EloMatchResult(changes, average);
    }

    @Override
    public EloRatingService.EloMatchResult calculateForfeit(String playerId, int currentElo) {
        int delta = -FORFEIT_PENALTY;
        return new EloRatingService.EloMatchResult(Map.of(
                playerId,
                new EloRatingService.EloChange(playerId, false, currentElo, delta, currentElo + delta)
        ), currentElo);
    }

    @Override
    public void applyDelta(UserGameStatisticEntity statistic, int delta) {
        statistic.applyNotInMyPotRatingDelta(delta);
    }

    private static EloRatingService.EloMatchResult allForfeited(
            List<EloRatingService.PlayerOutcome> outcomes,
            Map<String, Integer> ratings
    ) {
        Map<String, EloRatingService.EloChange> changes = new LinkedHashMap<>();
        for (EloRatingService.PlayerOutcome outcome : outcomes) {
            int oldElo = ratings.getOrDefault(outcome.playerId(), EloRatingService.DEFAULT_ELO);
            int delta = -NotInMyPotEloCalculator.BASE_REWARD;
            changes.put(outcome.playerId(), new EloRatingService.EloChange(
                    outcome.playerId(), false, oldElo, delta, oldElo + delta
            ));
        }
        double average = ratings.values().stream().mapToInt(Integer::intValue).average().orElse(5000);
        return new EloRatingService.EloMatchResult(changes, average);
    }

    private static EloRatingService.EloMatchResult winnersOnly(
            List<NotInMyPotEloCalculator.PlayerRating> winners,
            Map<String, Integer> ratings
    ) {
        Map<String, EloRatingService.EloChange> changes = new LinkedHashMap<>();
        for (NotInMyPotEloCalculator.PlayerRating winner : winners) {
            int newElo = Math.addExact(winner.elo(), NotInMyPotEloCalculator.BASE_REWARD);
            changes.put(winner.id(), new EloRatingService.EloChange(
                    winner.id(), true, winner.elo(), NotInMyPotEloCalculator.BASE_REWARD, newElo
            ));
        }
        double average = ratings.values().stream().mapToInt(Integer::intValue).average().orElse(5000);
        return new EloRatingService.EloMatchResult(changes, average);
    }
}
