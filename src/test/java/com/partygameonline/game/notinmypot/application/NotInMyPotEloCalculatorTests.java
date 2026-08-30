package com.partygameonline.game.notinmypot.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NotInMyPotEloCalculatorTests {

    @Test
    void distributesFiveWinnerRewardAcrossTwoLosers() {
        Map<String, NotInMyPotEloCalculator.EloChange> changes = calculate(5, 2);

        assertThat(changes.values()).filteredOn(change -> change.eloChange() > 0)
                .allSatisfy(change -> assertThat(change.eloChange()).isEqualTo(50));
        assertThat(changes.get("L1").eloChange()).isEqualTo(-125);
        assertThat(changes.get("L2").eloChange()).isEqualTo(-125);
        assertThat(netChange(changes)).isZero();
    }

    @Test
    void givesTheHigherRatedLoserTheSlightlyLargerShare() {
        List<NotInMyPotEloCalculator.EloChange> changes =
                NotInMyPotEloCalculator.calculateEloChanges(
                        java.util.stream.IntStream.rangeClosed(1, 5)
                                .mapToObj(index -> player("W" + index, 5000))
                                .toList(),
                        List.of(player("high", 6500), player("low", 5500))
                );
        Map<String, NotInMyPotEloCalculator.EloChange> byId = byId(changes);

        assertThat(byId.get("high").eloChange()).isEqualTo(-131);
        assertThat(byId.get("low").eloChange()).isEqualTo(-119);
        assertThat(netChange(byId)).isZero();
    }

    @Test
    void distributesFourWinnerRewardAcrossTwoLosers() {
        Map<String, NotInMyPotEloCalculator.EloChange> changes = calculate(4, 2);

        assertThat(changes.get("L1").eloChange()).isEqualTo(-100);
        assertThat(changes.get("L2").eloChange()).isEqualTo(-100);
        assertThat(netChange(changes)).isZero();
    }

    @Test
    void givesTheOnlyLoserTheWholeThreeWinnerPool() {
        Map<String, NotInMyPotEloCalculator.EloChange> changes = calculate(3, 1);

        assertThat(changes.get("L1").eloChange()).isEqualTo(-150);
        assertThat(netChange(changes)).isZero();
    }

    @Test
    void givesTheOnlyLoserTheWholeTwoWinnerPool() {
        Map<String, NotInMyPotEloCalculator.EloChange> changes = calculate(2, 1);

        assertThat(changes.get("L1").eloChange()).isEqualTo(-100);
        assertThat(netChange(changes)).isZero();
    }

    @Test
    void clampsExtremeLoserRatingDifferenceToTenPercent() {
        List<NotInMyPotEloCalculator.EloChange> changes =
                NotInMyPotEloCalculator.calculateEloChanges(
                        List.of(player("W1", 5000), player("W2", 5000)),
                        List.of(player("high", 1_000_000), player("low", -1_000_000))
                );
        Map<String, NotInMyPotEloCalculator.EloChange> byId = byId(changes);

        // Average loser ELO is zero. Both raw adjustments are far outside the
        // allowed range, so the weights are exactly 1.10 and 0.90.
        assertThat(byId.get("high").eloChange()).isEqualTo(-55);
        assertThat(byId.get("low").eloChange()).isEqualTo(-45);
        assertThat(netChange(byId)).isZero();
    }

    @Test
    void allowsTheCalculatedRatingToFallBelowFiveThousand() {
        NotInMyPotEloCalculator.EloChange change =
                NotInMyPotEloCalculator.calculateEloChanges(
                        List.of(player("W1", 5000), player("W2", 5000), player("W3", 5000)),
                        List.of(player("L1", 100))
                ).getLast();

        assertThat(change.eloChange()).isEqualTo(-150);
        assertThat(change.newElo()).isEqualTo(-50);
    }

    @Test
    void rejectsAnEmptyTeam() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NotInMyPotEloCalculator.calculateEloChanges(
                        List.of(), List.of(player("L1", 5000))));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NotInMyPotEloCalculator.calculateEloChanges(
                        List.of(player("W1", 5000)), List.of()));
    }

    private static Map<String, NotInMyPotEloCalculator.EloChange> calculate(
            int winnerCount,
            int loserCount
    ) {
        List<NotInMyPotEloCalculator.PlayerRating> winners = java.util.stream.IntStream.rangeClosed(1, winnerCount)
                .mapToObj(index -> player("W" + index, 5000))
                .toList();
        List<NotInMyPotEloCalculator.PlayerRating> losers = java.util.stream.IntStream.rangeClosed(1, loserCount)
                .mapToObj(index -> player("L" + index, 5000))
                .toList();
        return byId(NotInMyPotEloCalculator.calculateEloChanges(winners, losers));
    }

    private static NotInMyPotEloCalculator.PlayerRating player(String id, int elo) {
        return new NotInMyPotEloCalculator.PlayerRating(id, elo);
    }

    private static Map<String, NotInMyPotEloCalculator.EloChange> byId(
            List<NotInMyPotEloCalculator.EloChange> changes
    ) {
        return changes.stream().collect(Collectors.toMap(
                NotInMyPotEloCalculator.EloChange::id,
                Function.identity()
        ));
    }

    private static int netChange(Map<String, NotInMyPotEloCalculator.EloChange> changes) {
        return changes.values().stream().mapToInt(NotInMyPotEloCalculator.EloChange::eloChange).sum();
    }
}
