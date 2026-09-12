package com.partygameonline.ranking.application;

import com.partygameonline.game.bloodbound.BloodBoundGameManifest;
import com.partygameonline.game.bloodbound.application.BloodBoundEloCalculator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Explicit policy registration for Blood Bound (Huyết Thệ)'s zero-sum team ELO model. */
@Component
public final class BloodBoundGameEloPolicy implements GameEloPolicy {

    private static final Logger log = LoggerFactory.getLogger(BloodBoundGameEloPolicy.class);
    private final DefaultGameEloPolicy delegate = new DefaultGameEloPolicy();

    @Override
    public String gameCode() {
        return BloodBoundGameManifest.ID;
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
        if (outcomes == null || outcomes.size() < 2) {
            return delegate.calculateMatch(outcomes, ratings, gameState);
        }

        Set<String> winners = outcomes.stream()
                .filter(EloRatingService.PlayerOutcome::winner)
                .map(EloRatingService.PlayerOutcome::playerId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (winners.isEmpty() || winners.size() >= outcomes.size()) {
            return delegate.calculateMatch(outcomes, ratings, gameState);
        }

        List<BloodBoundEloCalculator.PlayerRating> players = outcomes.stream()
                .map(outcome -> new BloodBoundEloCalculator.PlayerRating(
                        outcome.playerId(),
                        ratings.getOrDefault(outcome.playerId(), EloRatingService.DEFAULT_ELO)
                ))
                .toList();

        List<BloodBoundEloCalculator.EloChange> calculated =
                BloodBoundEloCalculator.calculate(players, winners);

        Map<String, EloRatingService.EloChange> changes = new LinkedHashMap<>();
        for (BloodBoundEloCalculator.EloChange change : calculated) {
            log.debug(
                    "BloodBound ELO playerId={} oldElo={} delta={} newElo={}",
                    change.playerId(),
                    change.oldElo(),
                    change.actualDelta(),
                    change.newElo()
            );
            changes.put(change.playerId(), new EloRatingService.EloChange(
                    change.playerId(),
                    change.winner(),
                    change.oldElo(),
                    change.actualDelta(),
                    change.newElo()
            ));
        }

        double average = ratings.values().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(EloRatingService.DEFAULT_ELO);
        return new EloRatingService.EloMatchResult(changes, average);
    }

    @Override
    public EloRatingService.EloMatchResult calculateForfeit(String playerId, int currentElo) {
        int delta = -50;
        int newElo = Math.max(EloRatingService.MIN_ELO, currentElo + delta);
        return new EloRatingService.EloMatchResult(Map.of(
                playerId,
                new EloRatingService.EloChange(playerId, false, currentElo, delta, newElo)
        ), currentElo);
    }
}
