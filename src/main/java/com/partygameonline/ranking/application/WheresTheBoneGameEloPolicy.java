package com.partygameonline.ranking.application;

import com.partygameonline.game.wheresthebone.WheresTheBoneGameManifest;
import com.partygameonline.game.wheresthebone.application.WheresTheBoneEloCalculator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Explicit policy registration for Where's the Bone's standard ELO model. */
@Component
public final class WheresTheBoneGameEloPolicy implements GameEloPolicy {

    private static final Logger log = LoggerFactory.getLogger(WheresTheBoneGameEloPolicy.class);
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
        List<WheresTheBoneEloCalculator.PlayerRating> players = outcomes.stream()
                .map(outcome -> new WheresTheBoneEloCalculator.PlayerRating(
                        outcome.playerId(),
                        ratings.getOrDefault(outcome.playerId(), EloRatingService.DEFAULT_ELO)
                ))
                .toList();
        Set<String> winners = outcomes.stream()
                .filter(EloRatingService.PlayerOutcome::winner)
                .map(EloRatingService.PlayerOutcome::playerId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<WheresTheBoneEloCalculator.EloChange> calculated =
                WheresTheBoneEloCalculator.calculate(players, winners);
        Map<String, EloRatingService.EloChange> changes = new LinkedHashMap<>();
        for (WheresTheBoneEloCalculator.EloChange change : calculated) {
            log.debug(
                    "ELO playerId={} oldElo={} opponentAvg={} factor={} desired={} actualDelta={} newElo={}",
                    change.playerId(),
                    change.oldElo(),
                    change.opponentAverage(),
                    change.factor(),
                    change.desired(),
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
        // A WTB abandon is settled with the official match result so the
        // player cannot avoid the loser pool by leaving early. There is no
        // standalone forfeit rating update for this game.
        return new EloRatingService.EloMatchResult(Map.of(), currentElo);
    }
}
