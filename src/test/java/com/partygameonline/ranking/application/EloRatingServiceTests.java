package com.partygameonline.ranking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.partygameonline.ranking.infrastructure.UserGameStatisticJpaRepository;
import com.partygameonline.ranking.infrastructure.UserGameStatisticEntity;
import com.partygameonline.game.nob.domain.NobEloChange;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobPlayerState;
import com.partygameonline.game.nob.domain.NobRoundResult;
import com.partygameonline.game.notinmypot.NotInMyPotGameManifest;
import com.partygameonline.game.wheresthebone.WheresTheBoneGameManifest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EloRatingServiceTests {

    private EloRatingService service;
    private UserGameStatisticJpaRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(UserGameStatisticJpaRepository.class);
        service = new EloRatingService(repository);
    }

    @Test
    void usesExactBoundaryBandsForBelowAverageWinnerAndLoser() {
        assertThat(service.calculateEloDelta(4900, 5000, true)).isEqualTo(50);
        assertThat(service.calculateEloDelta(4900, 5000, false)).isEqualTo(-50);
        assertThat(service.calculateEloDelta(4899, 5000, true)).isEqualTo(55);
        assertThat(service.calculateEloDelta(4899, 5000, false)).isEqualTo(-45);
        assertThat(service.calculateEloDelta(4699, 5000, true)).isEqualTo(60);
        assertThat(service.calculateEloDelta(4699, 5000, false)).isEqualTo(-40);
        assertThat(service.calculateEloDelta(4199, 5000, true)).isEqualTo(70);
        assertThat(service.calculateEloDelta(4199, 5000, false)).isEqualTo(-30);
        assertThat(service.calculateEloDelta(3799, 5000, true)).isEqualTo(75);
        assertThat(service.calculateEloDelta(3799, 5000, false)).isEqualTo(-25);
        assertThat(service.calculateEloDelta(3199, 5000, true)).isEqualTo(80);
        assertThat(service.calculateEloDelta(3199, 5000, false)).isEqualTo(-20);
        assertThat(service.calculateEloDelta(4200, 5000, true)).isEqualTo(65);
        assertThat(service.calculateEloDelta(4200, 5000, false)).isEqualTo(-35);
    }

    @Test
    void givesTheMirrorValuesToAnAboveAveragePlayer() {
        assertThat(service.calculateEloDelta(5100, 5000, true)).isEqualTo(50);
        assertThat(service.calculateEloDelta(5101, 5000, true)).isEqualTo(45);
        assertThat(service.calculateEloDelta(5301, 5000, true)).isEqualTo(40);
        assertThat(service.calculateEloDelta(5501, 5000, true)).isEqualTo(35);
        assertThat(service.calculateEloDelta(5901, 5000, true)).isEqualTo(30);
        assertThat(service.calculateEloDelta(6501, 5000, true)).isEqualTo(25);
        assertThat(service.calculateEloDelta(6801, 5000, true)).isEqualTo(20);
        assertThat(service.calculateEloDelta(5101, 5000, false)).isEqualTo(-55);
        assertThat(service.calculateEloDelta(6801, 5000, false)).isEqualTo(-80);
    }

    @Test
    void equalAverageDoesNotDependOnRoomSize() {
        assertThat(service.calculateEloDelta(5000, 5000, true)).isEqualTo(50);
        assertThat(service.calculateEloDelta(5000, 5000, false)).isEqualTo(-50);
    }

    @Test
    void ratingNeverDropsBelowZero() {
        UserGameStatisticEntity statistic = UserGameStatisticEntity.newStatistic("player", "night-of-bloodlines");

        statistic.applyRatingDelta(-6000);

        assertThat(statistic.getElo()).isZero();
        assertThat(statistic.getEloNob()).isZero();
        assertThat(statistic.getHighestElo()).isEqualTo(5000);
    }

    @Test
    void notInMyPotUsesItsOwnEloColumn() {
        UserGameStatisticEntity winner = UserGameStatisticEntity.newStatistic(
                "pot-winner",
                NotInMyPotGameManifest.ID
        );
        UserGameStatisticEntity loser = UserGameStatisticEntity.newStatistic(
                "pot-loser",
                NotInMyPotGameManifest.ID
        );
        when(repository.findByUserIdAndGameCodeForUpdate("pot-winner", NotInMyPotGameManifest.ID))
                .thenReturn(Optional.of(winner));
        when(repository.findByUserIdAndGameCodeForUpdate("pot-loser", NotInMyPotGameManifest.ID))
                .thenReturn(Optional.of(loser));

        EloRatingService.EloMatchResult result = service.completeNotInMyPotMatch(
                List.of("pot-winner", "pot-loser"),
                Set.of("pot-winner")
        );

        assertThat(winner.getEloNotInMyPot()).isEqualTo(5050);
        assertThat(winner.getHighestEloNotInMyPot()).isEqualTo(5050);
        assertThat(loser.getEloNotInMyPot()).isEqualTo(4950);
        assertThat(winner.getEloNob()).isEqualTo(UserGameStatisticEntity.DEFAULT_ELO);
        assertThat(loser.getEloNob()).isEqualTo(UserGameStatisticEntity.DEFAULT_ELO);
        assertThat(result.changes().get("pot-winner").newElo()).isEqualTo(5050);
    }

    @Test
    void notInMyPotHasNoRatingFloorAndDoesNotChangeNobRating() {
        List<String> playerIds = List.of("winner-1", "winner-2", "winner-3", "loser");
        UserGameStatisticEntity winner1 = UserGameStatisticEntity.newStatistic("winner-1", NotInMyPotGameManifest.ID);
        UserGameStatisticEntity winner2 = UserGameStatisticEntity.newStatistic("winner-2", NotInMyPotGameManifest.ID);
        UserGameStatisticEntity winner3 = UserGameStatisticEntity.newStatistic("winner-3", NotInMyPotGameManifest.ID);
        UserGameStatisticEntity loser = UserGameStatisticEntity.newStatistic("loser", NotInMyPotGameManifest.ID);
        loser.applyRatingDelta(-4900);
        when(repository.findByUserIdAndGameCodeForUpdate("winner-1", NotInMyPotGameManifest.ID)).thenReturn(Optional.of(winner1));
        when(repository.findByUserIdAndGameCodeForUpdate("winner-2", NotInMyPotGameManifest.ID)).thenReturn(Optional.of(winner2));
        when(repository.findByUserIdAndGameCodeForUpdate("winner-3", NotInMyPotGameManifest.ID)).thenReturn(Optional.of(winner3));
        when(repository.findByUserIdAndGameCodeForUpdate("loser", NotInMyPotGameManifest.ID)).thenReturn(Optional.of(loser));

        service.completeNotInMyPotMatch(playerIds, Set.of("winner-1", "winner-2", "winner-3"));

        assertThat(loser.getEloNotInMyPot()).isEqualTo(-50);
        assertThat(loser.getEloNob()).isEqualTo(UserGameStatisticEntity.DEFAULT_ELO);
        assertThat(loser.getElo()).isEqualTo(100);
    }

    @Test
    void forfeitAppliesPenaltyWithoutCountingACompletedMatch() {
        UserGameStatisticEntity leaver = UserGameStatisticEntity.newStatistic(
                "leaver",
                NotInMyPotGameManifest.ID
        );
        when(repository.findByUserIdAndGameCodeForUpdate("leaver", NotInMyPotGameManifest.ID))
                .thenReturn(Optional.of(leaver));

        EloRatingService.EloMatchResult result = service.applyForfeit(
                NotInMyPotGameManifest.ID,
                "leaver"
        );

        assertThat(result.changes().get("leaver").eloDelta()).isEqualTo(-100);
        assertThat(leaver.getEloNotInMyPot()).isEqualTo(4900);
        assertThat(leaver.getTotalMatch()).isZero();
        assertThat(leaver.getTotalWin()).isZero();
        assertThat(leaver.getEloNob()).isEqualTo(UserGameStatisticEntity.DEFAULT_ELO);
    }

    @Test
    void notInMyPotStillRewardsRemainingWinnersWhenEveryLoserForfeited() {
        UserGameStatisticEntity winner = UserGameStatisticEntity.newStatistic(
                "winner",
                NotInMyPotGameManifest.ID
        );
        when(repository.findByUserIdAndGameCodeForUpdate("winner", NotInMyPotGameManifest.ID))
                .thenReturn(Optional.of(winner));

        EloRatingService.EloMatchResult result = service.completeMatch(
                NotInMyPotGameManifest.ID,
                List.of("winner"),
                Set.of("winner"),
                null
        );

        assertThat(result.changes().get("winner").eloDelta()).isEqualTo(50);
        assertThat(winner.getEloNotInMyPot()).isEqualTo(5050);
        assertThat(winner.getTotalMatch()).isEqualTo(1);
        assertThat(winner.getTotalWin()).isEqualTo(1);
    }

    @Test
    void wheresTheBoneUsesItsZeroSumPolicyWithoutChangingOtherGameRatings() {
        List<String> playerIds = List.of("W1", "W2", "W3", "W4", "L1", "L2");
        Map<String, UserGameStatisticEntity> statistics = new java.util.LinkedHashMap<>();
        for (String playerId : playerIds) {
            UserGameStatisticEntity statistic = UserGameStatisticEntity.newStatistic(
                    playerId,
                    WheresTheBoneGameManifest.ID
            );
            statistics.put(playerId, statistic);
            when(repository.findByUserIdAndGameCodeForUpdate(playerId, WheresTheBoneGameManifest.ID))
                    .thenReturn(Optional.of(statistic));
        }

        EloRatingService.EloMatchResult result = service.completeMatch(
                WheresTheBoneGameManifest.ID,
                playerIds,
                Set.of("W1", "W2", "W3", "W4"),
                null
        );

        assertThat(result.changes().values().stream()
                .mapToInt(EloRatingService.EloChange::eloDelta)
                .sum()).isZero();
        assertThat(result.changes().get("W1").eloDelta()).isEqualTo(38);
        assertThat(result.changes().get("L1").eloDelta()).isEqualTo(-76);
        assertThat(statistics.values()).allSatisfy(statistic -> {
            assertThat(statistic.getEloNob()).isEqualTo(UserGameStatisticEntity.DEFAULT_ELO);
            assertThat(statistic.getTotalMatch()).isEqualTo(1);
        });
    }

    @Test
    void wheresTheBoneForfeitDoesNotApplyAnImmediateStandaloneRatingChange() {
        UserGameStatisticEntity leaver = UserGameStatisticEntity.newStatistic(
                "leaver",
                WheresTheBoneGameManifest.ID
        );
        when(repository.findByUserIdAndGameCodeForUpdate("leaver", WheresTheBoneGameManifest.ID))
                .thenReturn(Optional.of(leaver));

        EloRatingService.EloMatchResult result = service.applyForfeit(
                WheresTheBoneGameManifest.ID,
                "leaver"
        );

        assertThat(result.changes()).isEmpty();
        assertThat(leaver.getEloForGame()).isEqualTo(UserGameStatisticEntity.DEFAULT_ELO);
        assertThat(leaver.getTotalMatch()).isZero();
    }

    @Test
    void nobRoundUsesRoundWinnerFlagsInsteadOfCumulativeScores() {
        mockDefaultRatings("A", "B", "C", "D");

        EloRatingService.EloMatchResult result = service.previewRound(
                "night-of-bloodlines",
                List.of(
                        new EloRatingService.PlayerOutcome("A", true, 1),
                        new EloRatingService.PlayerOutcome("B", false, 12),
                        new EloRatingService.PlayerOutcome("C", false, 9),
                        new EloRatingService.PlayerOutcome("D", false, 5)
                ),
                Map.of()
        );

        assertThat(result.changes().get("A").eloDelta()).isEqualTo(17);
        assertThat(result.changes().get("B").eloDelta()).isEqualTo(-17);
        assertThat(result.changes().get("C").eloDelta()).isEqualTo(-17);
        assertThat(result.changes().get("D").eloDelta()).isEqualTo(-17);
    }

    @Test
    void nobRoundDoesNotUseTiedCumulativePlacements() {
        mockDefaultRatings("A", "B", "C", "D");

        EloRatingService.EloMatchResult result = service.previewRound(
                "night-of-bloodlines",
                List.of(
                        new EloRatingService.PlayerOutcome("A", true, 1),
                        new EloRatingService.PlayerOutcome("B", false, 12),
                        new EloRatingService.PlayerOutcome("C", false, 8),
                        new EloRatingService.PlayerOutcome("D", false, 3)
                ),
                Map.of()
        );

        assertThat(result.changes().get("A").eloDelta()).isEqualTo(17);
        assertThat(result.changes().get("B").eloDelta()).isEqualTo(-17);
        assertThat(result.changes().get("C").eloDelta()).isEqualTo(-17);
        assertThat(result.changes().get("D").eloDelta()).isEqualTo(-17);
    }

    @Test
    void everyoneKeepsTheirEloWhenAllMoonMarkScoresAreEqual() {
        mockDefaultRatings("A", "B", "C", "D");

        EloRatingService.EloMatchResult result = service.previewRound(
                "night-of-bloodlines",
                List.of(
                        new EloRatingService.PlayerOutcome("A", true, 10),
                        new EloRatingService.PlayerOutcome("B", true, 10),
                        new EloRatingService.PlayerOutcome("C", true, 10),
                        new EloRatingService.PlayerOutcome("D", true, 10)
                ),
                Map.of()
        );

        assertThat(result.changes().values()).allMatch(change -> change.eloDelta() == 0);
        assertThat(result.changes().values()).allMatch(change -> change.newElo() == 5000);
    }

    @Test
    void nobRoundRespectsRatingFloorForLoser() {
        when(repository.findByUserIdAndGameCode("A", "night-of-bloodlines"))
                .thenReturn(Optional.of(UserGameStatisticEntity.newStatistic("A", "night-of-bloodlines")));
        UserGameStatisticEntity empty = UserGameStatisticEntity.newStatistic("B", "night-of-bloodlines");
        empty.applyRatingDelta(-5000);
        when(repository.findByUserIdAndGameCode("B", "night-of-bloodlines")).thenReturn(Optional.of(empty));

        EloRatingService.EloMatchResult result = service.previewRound(
                "night-of-bloodlines",
                List.of(
                        new EloRatingService.PlayerOutcome("A", true, 10),
                        new EloRatingService.PlayerOutcome("B", false, 1)
                ),
                Map.of()
        );

        assertThat(result.changes().get("A").eloDelta()).isEqualTo(7);
        assertThat(result.changes().get("B").eloDelta()).isZero();
    }

    @Test
    void finalRatingsCommitRoundDeltasAndAddTheFiftyPointMatchSettlement() {
        UserGameStatisticEntity winner = UserGameStatisticEntity.newStatistic("A", "night-of-bloodlines");
        UserGameStatisticEntity loser = UserGameStatisticEntity.newStatistic("B", "night-of-bloodlines");
        when(serviceRepository().findByUserIdAndGameCodeForUpdate("A", "night-of-bloodlines"))
                .thenReturn(Optional.of(winner));
        when(serviceRepository().findByUserIdAndGameCodeForUpdate("B", "night-of-bloodlines"))
                .thenReturn(Optional.of(loser));

        NobGameState state = new NobGameState("ABCD");
        state.getPlayers().add(new NobPlayerState("A", "A", 0));
        state.getPlayers().add(new NobPlayerState("B", "B", 1));
        state.recordCompletedRound(new NobRoundResult("VAMPIRE", "VAMPIRE", false), List.of("A"));
        state.recordRoundEloChanges(1, Map.of(
                "A", new NobEloChange(5000, 17, 5017),
                "B", new NobEloChange(5000, -17, 4983)
        ));

        EloRatingService.EloMatchResult result = service.completeNobMatch(
                List.of("A", "B"),
                Set.of("A"),
                state
        );

        assertThat(winner.getElo()).isEqualTo(5067);
        assertThat(loser.getElo()).isEqualTo(4933);
        assertThat(winner.getTotalMatch()).isEqualTo(1);
        assertThat(winner.getTotalWin()).isEqualTo(1);
        assertThat(result.changes().get("A").eloDelta()).isEqualTo(67);
        assertThat(result.changes().get("B").eloDelta()).isEqualTo(-67);
    }

    @Test
    void nobFinalRewardIsFiftyAndTheOtherPlayersShareTheExactLoss() {
        List<String> playerIds = List.of("A", "B", "C", "D");
        Map<String, UserGameStatisticEntity> statistics = new java.util.LinkedHashMap<>();
        for (String playerId : playerIds) {
            UserGameStatisticEntity statistic = UserGameStatisticEntity.newStatistic(
                    playerId,
                    "night-of-bloodlines"
            );
            statistics.put(playerId, statistic);
            when(repository.findByUserIdAndGameCodeForUpdate(playerId, "night-of-bloodlines"))
                    .thenReturn(Optional.of(statistic));
        }

        NobGameState state = new NobGameState("ABCD");
        playerIds.forEach(playerId -> state.getPlayers().add(
                new NobPlayerState(playerId, playerId, state.getPlayers().size())
        ));
        state.recordCompletedRound(new NobRoundResult("VAMPIRE", "VAMPIRE", false), List.of("A"));
        state.recordRoundEloChanges(1, Map.of(
                "A", new NobEloChange(5000, 0, 5000),
                "B", new NobEloChange(5000, 0, 5000),
                "C", new NobEloChange(5000, 0, 5000),
                "D", new NobEloChange(5000, 0, 5000)
        ));

        EloRatingService.EloMatchResult result = service.completeNobMatch(
                playerIds,
                Set.of("A"),
                state
        );

        assertThat(result.changes().get("A").eloDelta()).isEqualTo(50);
        assertThat(result.changes().get("B").eloDelta()).isEqualTo(-17);
        assertThat(result.changes().get("C").eloDelta()).isEqualTo(-17);
        assertThat(result.changes().get("D").eloDelta()).isEqualTo(-16);
        assertThat(result.changes().values()).extracting(EloRatingService.EloChange::eloDelta)
                .satisfies(deltas -> assertThat(deltas.stream().mapToInt(Integer::intValue).sum()).isZero());
    }

    private void mockDefaultRatings(String... playerIds) {
        for (String playerId : playerIds) {
            when(repository.findByUserIdAndGameCode(playerId, "night-of-bloodlines"))
                    .thenReturn(Optional.of(UserGameStatisticEntity.newStatistic(playerId, "night-of-bloodlines")));
        }
    }

    private UserGameStatisticJpaRepository serviceRepository() {
        return repository;
    }

}
