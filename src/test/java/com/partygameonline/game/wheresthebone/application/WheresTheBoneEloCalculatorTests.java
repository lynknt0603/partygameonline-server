package com.partygameonline.game.wheresthebone.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WheresTheBoneEloCalculatorTests {

    @Test
    void allEqualThreeWinnersAndTwoLosersUsesTheSmallerWinnerPool() {
        Map<String, WheresTheBoneEloCalculator.EloChange> changes = calculate(
                List.of(player("W1", 5000), player("W2", 5000), player("W3", 5000),
                        player("L1", 5000), player("L2", 5000)),
                Set.of("W1", "W2", "W3")
        );

        assertThat(changes.get("W1").actualDelta()).isEqualTo(50);
        assertThat(changes.get("W2").actualDelta()).isEqualTo(50);
        assertThat(changes.get("W3").actualDelta()).isEqualTo(50);
        assertThat(changes.get("L1").actualDelta()).isEqualTo(-75);
        assertThat(changes.get("L2").actualDelta()).isEqualTo(-75);
        assertZeroSum(changes);
    }

    @Test
    void fourWinnersAndTwoLosersDistributesTheLoserPool() {
        Map<String, WheresTheBoneEloCalculator.EloChange> changes = calculate(
                players(4, 2, 5000),
                Set.of("W1", "W2", "W3", "W4")
        );

        assertThat(changes.values()).filteredOn(WheresTheBoneEloCalculator.EloChange::winner)
                .extracting(WheresTheBoneEloCalculator.EloChange::actualDelta)
                .containsExactly(38, 38, 38, 38);
        assertThat(changes.get("L1").actualDelta()).isEqualTo(-76);
        assertThat(changes.get("L2").actualDelta()).isEqualTo(-76);
        assertZeroSum(changes);
    }

    @Test
    void highRatedLoserIsCappedAtOneHundredAndWinnersReceiveThePool() {
        Map<String, WheresTheBoneEloCalculator.EloChange> changes = calculate(
                List.of(player("W1", 5000), player("W2", 5000), player("W3", 5000),
                        player("W4", 5000), player("L1", 6500)),
                Set.of("W1", "W2", "W3", "W4")
        );

        assertThat(changes.get("L1").actualDelta()).isEqualTo(-100);
        assertThat(changes.values()).filteredOn(WheresTheBoneEloCalculator.EloChange::winner)
                .extracting(WheresTheBoneEloCalculator.EloChange::actualDelta)
                .containsExactly(25, 25, 25, 25);
        assertZeroSum(changes);
    }

    @Test
    void lowRatedWinnerCanReceiveTheFullUnderdogRewardWhenPoolAllows() {
        Map<String, WheresTheBoneEloCalculator.EloChange> changes = calculate(
                List.of(player("W1", 4000), player("L1", 5000), player("L2", 5000),
                        player("L3", 5000), player("L4", 5000)),
                Set.of("W1")
        );

        assertThat(changes.get("W1").desired()).isEqualTo(75.0);
        assertThat(changes.get("W1").actualDelta()).isEqualTo(75);
        assertThat(changes.get("L1").actualDelta()).isNegative();
        assertZeroSum(changes);
    }

    @Test
    void highRatedWinnerReceivesLessThanTheBaseReward() {
        Map<String, WheresTheBoneEloCalculator.EloChange> changes = calculate(
                List.of(player("W1", 6500), player("L1", 5000), player("L2", 5000),
                        player("L3", 5000), player("L4", 5000)),
                Set.of("W1")
        );

        assertThat(changes.get("W1").desired()).isEqualTo(30.0);
        assertThat(changes.get("W1").actualDelta()).isLessThan(50);
        assertZeroSum(changes);
    }

    @Test
    void loserFactorChangesWithRatingAndNeverExceedsTheCap() {
        Map<String, WheresTheBoneEloCalculator.EloChange> changes = calculate(
                List.of(player("W1", 5000), player("L1", 4000), player("L2", 5500)),
                Set.of("W1")
        );

        assertThat(changes.get("L1").desired()).isLessThan(76.0);
        assertThat(changes.get("L2").desired()).isGreaterThan(76.0).isLessThanOrEqualTo(100.0);
        assertThat(changes.values()).filteredOn(change -> !change.winner())
                .allSatisfy(change -> assertThat(change.actualDelta()).isBetween(-100, -1));
        assertZeroSum(changes);
    }

    @Test
    void ratingCanFallBelowFiveThousandButNeverBelowZero() {
        Map<String, WheresTheBoneEloCalculator.EloChange> changes = calculate(
                List.of(player("W1", 5000), player("W2", 5000), player("L1", 5010)),
                Set.of("W1", "W2")
        );

        assertThat(changes.get("L1").actualDelta()).isEqualTo(-76);
        assertThat(changes.get("L1").newElo()).isEqualTo(4934);
        assertThat(changes.values()).allSatisfy(change ->
                assertThat(change.newElo()).isEqualTo(change.oldElo() + change.actualDelta()).isGreaterThanOrEqualTo(0));
        assertZeroSum(changes);
    }

    @Test
    void deterministicLargestRemainderRoundingPreservesZeroSum() {
        List<WheresTheBoneEloCalculator.PlayerRating> players = List.of(
                player("W1", 5250), player("W2", 5550),
                player("L1", 5000), player("L2", 5001), player("L3", 5002)
        );
        Set<String> winners = Set.of("W1", "W2");

        Map<String, WheresTheBoneEloCalculator.EloChange> first = calculate(players, winners);
        Map<String, WheresTheBoneEloCalculator.EloChange> second = calculate(players, winners);

        assertThat(first).isEqualTo(second);
        assertZeroSum(first);
    }

    @Test
    void manyLobbySizesAndRatingCombinationsRemainZeroSum() {
        for (int playerCount = 4; playerCount <= 8; playerCount++) {
            for (int winnerCount = 1; winnerCount < playerCount; winnerCount++) {
                List<WheresTheBoneEloCalculator.PlayerRating> players =
                        java.util.stream.IntStream.rangeClosed(1, playerCount)
                                .mapToObj(index -> player("P" + index, 3500 + index * 317))
                                .toList();
                Set<String> winners = java.util.stream.IntStream.rangeClosed(1, winnerCount)
                        .mapToObj(index -> "P" + index)
                        .collect(Collectors.toSet());
                assertZeroSum(calculate(players, winners));
            }
        }
    }

    @Test
    void rejectsAZeroRatedLoserBecauseEveryLoserNeedsANegativeDelta() {
        assertThatIllegalArgumentException().isThrownBy(() -> calculate(
                List.of(player("W1", 5000), player("L1", 0)),
                Set.of("W1")
        ));
    }

    private static Map<String, WheresTheBoneEloCalculator.EloChange> calculate(
            List<WheresTheBoneEloCalculator.PlayerRating> players,
            Set<String> winners
    ) {
        return WheresTheBoneEloCalculator.calculate(players, winners).stream()
                .collect(Collectors.toMap(
                        WheresTheBoneEloCalculator.EloChange::playerId,
                        Function.identity()
                ));
    }

    private static List<WheresTheBoneEloCalculator.PlayerRating> players(
            int winnerCount,
            int loserCount,
            int elo
    ) {
        List<WheresTheBoneEloCalculator.PlayerRating> players = new java.util.ArrayList<>();
        for (int index = 1; index <= winnerCount; index++) {
            players.add(player("W" + index, elo));
        }
        for (int index = 1; index <= loserCount; index++) {
            players.add(player("L" + index, elo));
        }
        return players;
    }

    private static WheresTheBoneEloCalculator.PlayerRating player(String id, int elo) {
        return new WheresTheBoneEloCalculator.PlayerRating(id, elo);
    }

    private static void assertZeroSum(Map<String, WheresTheBoneEloCalculator.EloChange> changes) {
        assertThat(changes.values()).allSatisfy(change -> {
            if (change.winner()) {
                assertThat(change.actualDelta()).isPositive();
            } else {
                assertThat(change.actualDelta()).isBetween(-100, -1);
            }
            assertThat(change.newElo()).isEqualTo(change.oldElo() + change.actualDelta());
        });
        assertThat(changes.values().stream()
                .mapToInt(WheresTheBoneEloCalculator.EloChange::actualDelta)
                .sum()).isZero();
    }
}
