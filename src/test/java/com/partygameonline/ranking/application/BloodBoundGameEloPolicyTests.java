package com.partygameonline.ranking.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BloodBoundGameEloPolicyTests {

    private final BloodBoundGameEloPolicy policy = new BloodBoundGameEloPolicy();

    @Test
    void sixPlayerMatchIsZeroSumWithDefault5000Elo() {
        EloRatingService.EloMatchResult result = policy.calculateMatch(
                List.of(
                        new EloRatingService.PlayerOutcome("rose-1", true),
                        new EloRatingService.PlayerOutcome("rose-2", true),
                        new EloRatingService.PlayerOutcome("rose-3", true),
                        new EloRatingService.PlayerOutcome("fan-1", false),
                        new EloRatingService.PlayerOutcome("fan-2", false),
                        new EloRatingService.PlayerOutcome("fan-3", false)
                ),
                Map.of(
                        "rose-1", 5000,
                        "rose-2", 5000,
                        "rose-3", 5000,
                        "fan-1", 5000,
                        "fan-2", 5000,
                        "fan-3", 5000
                ),
                null
        );

        int winnerSum = result.changes().values().stream()
                .filter(EloRatingService.EloChange::winner)
                .mapToInt(EloRatingService.EloChange::eloDelta)
                .sum();

        int loserSum = result.changes().values().stream()
                .filter(c -> !c.winner())
                .mapToInt(EloRatingService.EloChange::eloDelta)
                .sum();

        assertThat(winnerSum).isPositive();
        assertThat(loserSum).isNegative();
        assertThat(winnerSum + loserSum).isZero();

        // With even ratings at 5000, each 3v3 player gains/loses 50
        assertThat(result.changes().get("rose-1").eloDelta()).isEqualTo(50);
        assertThat(result.changes().get("rose-1").newElo()).isEqualTo(5050);
        assertThat(result.changes().get("fan-1").eloDelta()).isEqualTo(-50);
        assertThat(result.changes().get("fan-1").newElo()).isEqualTo(4950);
    }

    @Test
    void eightPlayerMatchIsZeroSumWithDefault5000Elo() {
        EloRatingService.EloMatchResult result = policy.calculateMatch(
                List.of(
                        new EloRatingService.PlayerOutcome("w1", true),
                        new EloRatingService.PlayerOutcome("w2", true),
                        new EloRatingService.PlayerOutcome("w3", true),
                        new EloRatingService.PlayerOutcome("w4", true),
                        new EloRatingService.PlayerOutcome("l1", false),
                        new EloRatingService.PlayerOutcome("l2", false),
                        new EloRatingService.PlayerOutcome("l3", false),
                        new EloRatingService.PlayerOutcome("l4", false)
                ),
                Map.of(
                        "w1", 5000, "w2", 5000, "w3", 5000, "w4", 5000,
                        "l1", 5000, "l2", 5000, "l3", 5000, "l4", 5000
                ),
                null
        );

        int totalDelta = result.changes().values().stream()
                .mapToInt(EloRatingService.EloChange::eloDelta)
                .sum();

        assertThat(totalDelta).isZero();
        assertThat(result.changes().get("w1").eloDelta()).isEqualTo(50);
        assertThat(result.changes().get("l1").eloDelta()).isEqualTo(-50);
    }

    @Test
    void sevenPlayerMatchInquisitorSoloWinIsZeroSum() {
        EloRatingService.EloMatchResult result = policy.calculateMatch(
                List.of(
                        new EloRatingService.PlayerOutcome("inquisitor", true),
                        new EloRatingService.PlayerOutcome("r1", false),
                        new EloRatingService.PlayerOutcome("r2", false),
                        new EloRatingService.PlayerOutcome("r3", false),
                        new EloRatingService.PlayerOutcome("f1", false),
                        new EloRatingService.PlayerOutcome("f2", false),
                        new EloRatingService.PlayerOutcome("f3", false)
                ),
                Map.of(
                        "inquisitor", 5000,
                        "r1", 5000, "r2", 5000, "r3", 5000,
                        "f1", 5000, "f2", 5000, "f3", 5000
                ),
                null
        );

        int totalDelta = result.changes().values().stream()
                .mapToInt(EloRatingService.EloChange::eloDelta)
                .sum();

        assertThat(totalDelta).isZero();
        assertThat(result.changes().get("inquisitor").eloDelta()).isEqualTo(120);
        assertThat(result.changes().get("inquisitor").newElo()).isEqualTo(5120);

        for (String loserId : List.of("r1", "r2", "r3", "f1", "f2", "f3")) {
            assertThat(result.changes().get(loserId).eloDelta()).isEqualTo(-20);
            assertThat(result.changes().get(loserId).newElo()).isEqualTo(4980);
        }
    }

    @Test
    void sevenPlayerMatchClanWinIsZeroSum() {
        EloRatingService.EloMatchResult result = policy.calculateMatch(
                List.of(
                        new EloRatingService.PlayerOutcome("r1", true),
                        new EloRatingService.PlayerOutcome("r2", true),
                        new EloRatingService.PlayerOutcome("r3", true),
                        new EloRatingService.PlayerOutcome("f1", false),
                        new EloRatingService.PlayerOutcome("f2", false),
                        new EloRatingService.PlayerOutcome("f3", false),
                        new EloRatingService.PlayerOutcome("inquisitor", false)
                ),
                Map.of(
                        "r1", 5000, "r2", 5000, "r3", 5000,
                        "f1", 5000, "f2", 5000, "f3", 5000,
                        "inquisitor", 5000
                ),
                null
        );

        int totalDelta = result.changes().values().stream()
                .mapToInt(EloRatingService.EloChange::eloDelta)
                .sum();

        assertThat(totalDelta).isZero();
        int winnerTotal = result.changes().values().stream()
                .filter(EloRatingService.EloChange::winner)
                .mapToInt(EloRatingService.EloChange::eloDelta)
                .sum();
        assertThat(winnerTotal).isEqualTo(150);
    }

    @Test
    void underdogWinningTeamGainsMoreThanFavoriteWinningTeam() {
        // Underdog team (average 4600) beats favorite team (average 5400)
        EloRatingService.EloMatchResult underdogWin = policy.calculateMatch(
                List.of(
                        new EloRatingService.PlayerOutcome("underdog-1", true),
                        new EloRatingService.PlayerOutcome("underdog-2", true),
                        new EloRatingService.PlayerOutcome("underdog-3", true),
                        new EloRatingService.PlayerOutcome("fav-1", false),
                        new EloRatingService.PlayerOutcome("fav-2", false),
                        new EloRatingService.PlayerOutcome("fav-3", false)
                ),
                Map.of(
                        "underdog-1", 4600, "underdog-2", 4600, "underdog-3", 4600,
                        "fav-1", 5400, "fav-2", 5400, "fav-3", 5400
                ),
                null
        );

        // Favorite team (average 5400) beats underdog team (average 4600)
        EloRatingService.EloMatchResult favoriteWin = policy.calculateMatch(
                List.of(
                        new EloRatingService.PlayerOutcome("fav-1", true),
                        new EloRatingService.PlayerOutcome("fav-2", true),
                        new EloRatingService.PlayerOutcome("fav-3", true),
                        new EloRatingService.PlayerOutcome("underdog-1", false),
                        new EloRatingService.PlayerOutcome("underdog-2", false),
                        new EloRatingService.PlayerOutcome("underdog-3", false)
                ),
                Map.of(
                        "fav-1", 5400, "fav-2", 5400, "fav-3", 5400,
                        "underdog-1", 4600, "underdog-2", 4600, "underdog-3", 4600
                ),
                null
        );

        int underdogPerPlayerGain = underdogWin.changes().get("underdog-1").eloDelta();
        int favoritePerPlayerGain = favoriteWin.changes().get("fav-1").eloDelta();

        assertThat(underdogPerPlayerGain).isGreaterThan(50);
        assertThat(favoritePerPlayerGain).isLessThan(50);
        assertThat(underdogPerPlayerGain).isGreaterThan(favoritePerPlayerGain);

        // Both remain zero sum
        int underdogSum = underdogWin.changes().values().stream().mapToInt(EloRatingService.EloChange::eloDelta).sum();
        int favoriteSum = favoriteWin.changes().values().stream().mapToInt(EloRatingService.EloChange::eloDelta).sum();
        assertThat(underdogSum).isZero();
        assertThat(favoriteSum).isZero();
    }

    @Test
    void ratingsNeverDropBelowZero() {
        EloRatingService.EloMatchResult result = policy.calculateMatch(
                List.of(
                        new EloRatingService.PlayerOutcome("w1", true),
                        new EloRatingService.PlayerOutcome("l1", false)
                ),
                Map.of(
                        "w1", 5000,
                        "l1", 10
                ),
                null
        );

        assertThat(result.changes().get("l1").newElo()).isNotNegative();
    }

    @Test
    void zeroEloLoserDoesNotGenerateFreeEloForWinner() {
        EloRatingService.EloMatchResult result = policy.calculateMatch(
                List.of(
                        new EloRatingService.PlayerOutcome("w1", true),
                        new EloRatingService.PlayerOutcome("l1", false)
                ),
                Map.of(
                        "w1", 5000,
                        "l1", 0
                ),
                null
        );

        var winnerChange = result.changes().get("w1");
        var loserChange = result.changes().get("l1");

        assertThat(loserChange.newElo()).isEqualTo(0);
        assertThat(loserChange.eloDelta()).isEqualTo(0);
        assertThat(winnerChange.eloDelta()).isEqualTo(0);
        assertThat(winnerChange.newElo()).isEqualTo(5000);

        int totalDelta = result.changes().values().stream().mapToInt(EloRatingService.EloChange::eloDelta).sum();
        assertThat(totalDelta).isZero();
    }

    @Test
    void mixedZeroAndPositiveEloLosersPreserveZeroSum() {
        EloRatingService.EloMatchResult result = policy.calculateMatch(
                List.of(
                        new EloRatingService.PlayerOutcome("w1", true),
                        new EloRatingService.PlayerOutcome("l1", false),
                        new EloRatingService.PlayerOutcome("l2", false)
                ),
                Map.of(
                        "w1", 5000,
                        "l1", 0,
                        "l2", 100
                ),
                null
        );

        var l1 = result.changes().get("l1");
        var l2 = result.changes().get("l2");
        var w1 = result.changes().get("w1");

        assertThat(l1.newElo()).isEqualTo(0);
        assertThat(l1.eloDelta()).isEqualTo(0);
        assertThat(l2.eloDelta()).isNegative();
        assertThat(l2.newElo()).isEqualTo(100 + l2.eloDelta());
        assertThat(w1.eloDelta()).isEqualTo(-l2.eloDelta());
        assertThat(w1.newElo()).isEqualTo(5000 + w1.eloDelta());

        int totalDelta = result.changes().values().stream().mapToInt(EloRatingService.EloChange::eloDelta).sum();
        assertThat(totalDelta).isZero();
    }
}
