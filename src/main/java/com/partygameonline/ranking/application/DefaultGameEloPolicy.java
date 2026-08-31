package com.partygameonline.ranking.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Default independent-player ELO model used by games without custom rules. */
@Component
public final class DefaultGameEloPolicy implements GameEloPolicy {

    public static final int DEFAULT_ELO = 5000;
    public static final int MIN_ELO = 0;

    @Override
    public String gameCode() {
        return null;
    }

    @Override
    public EloRatingService.EloMatchResult calculateRound(
            List<EloRatingService.PlayerOutcome> outcomes,
            Map<String, Integer> ratings
    ) {
        return calculate(outcomes, ratings, true);
    }

    @Override
    public EloRatingService.EloMatchResult calculateMatch(
            List<EloRatingService.PlayerOutcome> outcomes,
            Map<String, Integer> ratings,
            Object gameState
    ) {
        return calculate(outcomes, ratings, false);
    }

    @Override
    public EloRatingService.EloMatchResult calculateForfeit(String playerId, int currentElo) {
        int delta = calculateEloDelta(currentElo, currentElo, false);
        int newElo = Math.max(MIN_ELO, currentElo + delta);
        return new EloRatingService.EloMatchResult(Map.of(
                playerId,
                new EloRatingService.EloChange(playerId, false, currentElo, newElo - currentElo, newElo)
        ), currentElo);
    }

    public static int calculateEloDelta(int playerElo, double roomAverageElo, boolean winner) {
        double difference = Math.abs(playerElo - roomAverageElo);
        boolean belowAverage = playerElo < roomAverageElo;
        if (difference <= 100) {
            return winner ? 50 : -50;
        }
        if (difference <= 300) {
            return winner ? (belowAverage ? 55 : 45) : (belowAverage ? -45 : -55);
        }
        if (difference <= 500) {
            return winner ? (belowAverage ? 60 : 40) : (belowAverage ? -40 : -60);
        }
        if (difference <= 800) {
            return winner ? (belowAverage ? 65 : 35) : (belowAverage ? -35 : -65);
        }
        if (difference <= 1200) {
            return winner ? (belowAverage ? 70 : 30) : (belowAverage ? -30 : -70);
        }
        if (difference <= 1800) {
            return winner ? (belowAverage ? 75 : 25) : (belowAverage ? -25 : -75);
        }
        return winner ? (belowAverage ? 80 : 20) : (belowAverage ? -20 : -80);
    }

    private EloRatingService.EloMatchResult calculate(
            List<EloRatingService.PlayerOutcome> outcomes,
            Map<String, Integer> ratings,
            boolean rankedWhenScoresExist
    ) {
        if (outcomes.isEmpty()) {
            return new EloRatingService.EloMatchResult(Map.of(), DEFAULT_ELO);
        }
        double roomAverage = ratings.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(DEFAULT_ELO);
        if (rankedWhenScoresExist
                && outcomes.size() > 1
                && outcomes.stream().allMatch(outcome -> outcome.score() != null)) {
            return calculateRanked(outcomes, ratings, roomAverage);
        }
        Map<String, EloRatingService.EloChange> changes = new LinkedHashMap<>();
        for (EloRatingService.PlayerOutcome outcome : outcomes) {
            int oldElo = ratings.getOrDefault(outcome.playerId(), DEFAULT_ELO);
            int requestedDelta = calculateEloDelta(oldElo, roomAverage, outcome.winner());
            int newElo = Math.max(MIN_ELO, oldElo + requestedDelta);
            changes.put(outcome.playerId(), new EloRatingService.EloChange(
                    outcome.playerId(), outcome.winner(), oldElo, newElo - oldElo, newElo
            ));
        }
        return new EloRatingService.EloMatchResult(changes, roomAverage);
    }

    private static EloRatingService.EloMatchResult calculateRanked(
            List<EloRatingService.PlayerOutcome> outcomes,
            Map<String, Integer> ratings,
            double roomAverage
    ) {
        int opponentCount = outcomes.size() - 1;
        Map<String, Integer> rawDeltas = new LinkedHashMap<>();
        outcomes.forEach(outcome -> rawDeltas.put(outcome.playerId(), 0));

        for (int leftIndex = 0; leftIndex < outcomes.size(); leftIndex++) {
            EloRatingService.PlayerOutcome left = outcomes.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < outcomes.size(); rightIndex++) {
                EloRatingService.PlayerOutcome right = outcomes.get(rightIndex);
                if (left.score().equals(right.score())) {
                    continue;
                }
                EloRatingService.PlayerOutcome higherScore = left.score() > right.score() ? left : right;
                EloRatingService.PlayerOutcome lowerScore = higherScore == left ? right : left;
                int higherRating = ratings.getOrDefault(higherScore.playerId(), DEFAULT_ELO);
                int lowerRating = ratings.getOrDefault(lowerScore.playerId(), DEFAULT_ELO);
                double pairAverage = (higherRating + lowerRating) / 2.0;
                int pairTransfer = Math.max(
                        1,
                        (int) Math.round(calculateEloDelta(higherRating, pairAverage, true) / (double) opponentCount)
                );
                rawDeltas.compute(higherScore.playerId(), (ignored, delta) -> delta + pairTransfer);
                rawDeltas.compute(lowerScore.playerId(), (ignored, delta) -> delta - pairTransfer);
            }
        }

        Map<String, Integer> balancedDeltas = capLossesAndBalance(rawDeltas, ratings, outcomes);
        Map<String, EloRatingService.EloChange> changes = new LinkedHashMap<>();
        for (EloRatingService.PlayerOutcome outcome : outcomes) {
            int oldElo = ratings.getOrDefault(outcome.playerId(), DEFAULT_ELO);
            int delta = balancedDeltas.getOrDefault(outcome.playerId(), 0);
            changes.put(outcome.playerId(), new EloRatingService.EloChange(
                    outcome.playerId(), outcome.winner(), oldElo, delta, oldElo + delta
            ));
        }
        return new EloRatingService.EloMatchResult(changes, roomAverage);
    }

    private static Map<String, Integer> capLossesAndBalance(
            Map<String, Integer> rawDeltas,
            Map<String, Integer> ratings,
            List<EloRatingService.PlayerOutcome> outcomes
    ) {
        Map<String, Integer> balanced = new LinkedHashMap<>();
        int availableLossPool = 0;
        int requestedGainPool = 0;
        for (EloRatingService.PlayerOutcome outcome : outcomes) {
            String playerId = outcome.playerId();
            int rawDelta = rawDeltas.getOrDefault(playerId, 0);
            if (rawDelta < 0) {
                int cappedDelta = Math.max(rawDelta, -ratings.getOrDefault(playerId, DEFAULT_ELO));
                balanced.put(playerId, cappedDelta);
                availableLossPool -= cappedDelta;
            } else {
                balanced.put(playerId, 0);
                requestedGainPool += rawDelta;
            }
        }
        if (requestedGainPool == 0 || availableLossPool == 0) {
            return balanced;
        }

        int assigned = 0;
        List<GainShare> shares = new ArrayList<>();
        for (int index = 0; index < outcomes.size(); index++) {
            String playerId = outcomes.get(index).playerId();
            int requestedGain = Math.max(0, rawDeltas.getOrDefault(playerId, 0));
            if (requestedGain == 0) {
                continue;
            }
            double exactShare = requestedGain * (double) availableLossPool / requestedGainPool;
            int baseShare = (int) Math.floor(exactShare);
            balanced.put(playerId, baseShare);
            assigned += baseShare;
            shares.add(new GainShare(playerId, exactShare - baseShare, index));
        }
        shares.sort((left, right) -> {
            int byRemainder = Double.compare(right.remainder(), left.remainder());
            return byRemainder != 0 ? byRemainder : Integer.compare(left.order(), right.order());
        });
        for (int index = 0; index < availableLossPool - assigned; index++) {
            balanced.compute(shares.get(index).playerId(), (ignored, delta) -> delta + 1);
        }
        return balanced;
    }

    private record GainShare(String playerId, double remainder, int order) {
    }
}
