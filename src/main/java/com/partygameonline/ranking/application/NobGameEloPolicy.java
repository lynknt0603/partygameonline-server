package com.partygameonline.ranking.application;

import com.partygameonline.game.nob.NobGameManifest;
import com.partygameonline.game.nob.domain.NobCompletedRound;
import com.partygameonline.game.nob.domain.NobGameState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Round-ledger ELO rules owned by Night of Bloodlines. */
@Component
public final class NobGameEloPolicy implements GameEloPolicy {

    @Override
    public String gameCode() {
        return NobGameManifest.ID;
    }

    @Override
    public EloRatingService.EloMatchResult calculateRound(
            List<EloRatingService.PlayerOutcome> outcomes,
            Map<String, Integer> ratings
    ) {
        if (outcomes == null || outcomes.isEmpty()) {
            return new EloRatingService.EloMatchResult(Map.of(), EloRatingService.DEFAULT_ELO);
        }

        // NOB rounds are decided by the bloodline result/reward ledger. The
        // score carried by a snapshot is the cumulative Moon Mark total and
        // is not the result of this round. Passing it to the generic ranked
        // policy made round one compare a table of zeroes and produced ±0;
        // later rounds could even reward a round loser with positive ELO.
        // Treat a complete tie as a draw, then calculate the signed change
        // from this round's WIN/LOSS flags only.
        Map<String, Integer> effectiveRatings = ratings == null ? Map.of() : ratings;
        boolean sameResult = outcomes.stream().map(EloRatingService.PlayerOutcome::winner).distinct().count() == 1;
        if (sameResult) {
            Map<String, EloRatingService.EloChange> unchanged = new LinkedHashMap<>();
            double roomAverage = effectiveRatings.isEmpty()
                    ? EloRatingService.DEFAULT_ELO
                    : effectiveRatings.values().stream().mapToInt(Integer::intValue).average().orElse(EloRatingService.DEFAULT_ELO);
            for (EloRatingService.PlayerOutcome outcome : outcomes) {
                int elo = effectiveRatings.getOrDefault(outcome.playerId(), EloRatingService.DEFAULT_ELO);
                unchanged.put(outcome.playerId(), new EloRatingService.EloChange(
                        outcome.playerId(), outcome.winner(), elo, 0, elo
                ));
            }
            return new EloRatingService.EloMatchResult(unchanged, roomAverage);
        }

        List<EloRatingService.PlayerOutcome> roundResults = outcomes.stream()
                .map(outcome -> new EloRatingService.PlayerOutcome(outcome.playerId(), outcome.winner()))
                .toList();
        return new DefaultGameEloPolicy().calculateRound(roundResults, effectiveRatings);
    }

    @Override
    public EloRatingService.EloMatchResult calculateMatch(
            List<EloRatingService.PlayerOutcome> outcomes,
            Map<String, Integer> ratings,
            Object gameState
    ) {
        if (!(gameState instanceof NobGameState state) || state.getCompletedRounds().isEmpty()) {
            return new DefaultGameEloPolicy().calculateMatch(outcomes, ratings, null);
        }

        Map<String, Integer> firstRoundElo = new LinkedHashMap<>();
        for (NobCompletedRound round : state.getCompletedRounds()) {
            state.getRoundEloChanges(round.roundNumber()).forEach((playerId, change) ->
                    firstRoundElo.putIfAbsent(playerId, change.oldElo())
            );
        }

        Map<String, EloRatingService.EloChange> changes = new LinkedHashMap<>();
        for (EloRatingService.PlayerOutcome outcome : outcomes) {
            String playerId = outcome.playerId();
            int oldElo = ratings.getOrDefault(playerId, EloRatingService.DEFAULT_ELO);
            int finalElo = oldElo;
            for (NobCompletedRound round : state.getCompletedRounds()) {
                for (var snapshot : round.players()) {
                    if (playerId.equals(snapshot.playerId())) {
                        finalElo = Math.max(EloRatingService.MIN_ELO, finalElo + nullToZero(snapshot.eloDelta()));
                    }
                }
            }
            int aggregateOldElo = firstRoundElo.getOrDefault(playerId, oldElo);
            changes.put(playerId, new EloRatingService.EloChange(
                    playerId,
                    outcome.winner(),
                    aggregateOldElo,
                    finalElo - aggregateOldElo,
                    finalElo
            ));
        }
        double roomAverage = ratings.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(EloRatingService.DEFAULT_ELO);
        return new EloRatingService.EloMatchResult(changes, roomAverage);
    }

    @Override
    public EloRatingService.EloMatchResult calculateForfeit(String playerId, int currentElo) {
        return new DefaultGameEloPolicy().calculateForfeit(playerId, currentElo);
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
