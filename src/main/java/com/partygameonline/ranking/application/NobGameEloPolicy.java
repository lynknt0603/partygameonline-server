package com.partygameonline.ranking.application;

import com.partygameonline.game.nob.NobGameManifest;
import com.partygameonline.game.nob.domain.NobCompletedRound;
import com.partygameonline.game.nob.domain.NobGameState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Round-ledger ELO rules owned by Night of Bloodlines. */
@Component
public final class NobGameEloPolicy implements GameEloPolicy {

    static final int ROUND_DELTA_DIVISOR = 3;
    static final int MATCH_WIN_REWARD = 50;

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
        EloRatingService.EloMatchResult base = new DefaultGameEloPolicy()
                .calculateRound(roundResults, effectiveRatings);
        Map<String, EloRatingService.EloChange> scaled = new LinkedHashMap<>();
        base.changes().forEach((playerId, change) -> {
            int delta = (int) Math.round(change.eloDelta() / (double) ROUND_DELTA_DIVISOR);
            int newElo = Math.max(EloRatingService.MIN_ELO, change.oldElo() + delta);
            scaled.put(playerId, new EloRatingService.EloChange(
                    playerId,
                    change.winner(),
                    change.oldElo(),
                    newElo - change.oldElo(),
                    newElo
            ));
        });
        return new EloRatingService.EloMatchResult(scaled, base.roomAverageElo());
    }

    @Override
    public EloRatingService.EloMatchResult calculateMatch(
            List<EloRatingService.PlayerOutcome> outcomes,
            Map<String, Integer> ratings,
            Object gameState
    ) {
        NobGameState state = gameState instanceof NobGameState nobState ? nobState : null;
        Map<String, Integer> roundDeltas = accumulatedRoundDeltas(state);
        Map<String, Integer> matchSettlement = matchSettlement(outcomes, state);

        Map<String, EloRatingService.EloChange> changes = new LinkedHashMap<>();
        for (EloRatingService.PlayerOutcome outcome : outcomes) {
            String playerId = outcome.playerId();
            int oldElo = ratings.getOrDefault(playerId, EloRatingService.DEFAULT_ELO);
            int requestedDelta = roundDeltas.getOrDefault(playerId, 0)
                    + matchSettlement.getOrDefault(playerId, 0);
            int finalElo = Math.max(EloRatingService.MIN_ELO, oldElo + requestedDelta);
            changes.put(playerId, new EloRatingService.EloChange(
                    playerId,
                    outcome.winner(),
                    oldElo,
                    finalElo - oldElo,
                    finalElo
            ));
        }
        double roomAverage = ratings.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(EloRatingService.DEFAULT_ELO);
        return new EloRatingService.EloMatchResult(changes, roomAverage);
    }

    private static Map<String, Integer> accumulatedRoundDeltas(NobGameState state) {
        if (state == null || state.getCompletedRounds().isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (NobCompletedRound round : state.getCompletedRounds()) {
            for (var snapshot : round.players()) {
                result.merge(snapshot.playerId(), nullToZero(snapshot.eloDelta()), Integer::sum);
            }
        }
        return result;
    }

    private static Map<String, Integer> matchSettlement(
            List<EloRatingService.PlayerOutcome> outcomes,
            NobGameState state
    ) {
        List<EloRatingService.PlayerOutcome> winners = outcomes.stream()
                .filter(EloRatingService.PlayerOutcome::winner)
                .toList();
        List<EloRatingService.PlayerOutcome> losers = new ArrayList<>(outcomes.stream()
                .filter(outcome -> !outcome.winner())
                .toList());
        if (winners.isEmpty() || losers.isEmpty()) {
            return Map.of();
        }

        // The final winners are selected by the game's Moon Mark/token score.
        // When integer division leaves a remainder, lower-token losers pay the
        // extra point first so the settlement remains exactly zero-sum.
        Map<String, Integer> tokenScores = new LinkedHashMap<>();
        if (state != null) {
            state.getPlayers().forEach(player -> tokenScores.put(player.getPlayerId(), player.score()));
        }
        losers.sort(Comparator
                .comparingInt((EloRatingService.PlayerOutcome outcome) ->
                        tokenScores.getOrDefault(outcome.playerId(), 0))
                .thenComparing(EloRatingService.PlayerOutcome::playerId));

        int totalReward = Math.multiplyExact(winners.size(), MATCH_WIN_REWARD);
        int baseLoss = totalReward / losers.size();
        int remainder = totalReward % losers.size();
        Map<String, Integer> settlement = new LinkedHashMap<>();
        winners.forEach(winner -> settlement.put(winner.playerId(), MATCH_WIN_REWARD));
        for (int index = 0; index < losers.size(); index++) {
            int loss = baseLoss + (index < remainder ? 1 : 0);
            settlement.put(losers.get(index).playerId(), -loss);
        }
        return settlement;
    }

    @Override
    public EloRatingService.EloMatchResult calculateForfeit(String playerId, int currentElo) {
        return new DefaultGameEloPolicy().calculateForfeit(playerId, currentElo);
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
