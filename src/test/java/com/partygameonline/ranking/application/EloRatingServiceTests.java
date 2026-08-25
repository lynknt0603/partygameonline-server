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
    void finalWinnerReceivesTheAccumulatedLossesWithoutSplittingTheBonus() {
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
                "A", new NobEloChange(5000, 50, 5050),
                "B", new NobEloChange(5000, -50, 4950)
        ));

        EloRatingService.EloMatchResult result = service.completeNobMatch(
                List.of("A", "B"),
                Set.of("A"),
                state
        );

        assertThat(winner.getElo()).isEqualTo(5100);
        assertThat(loser.getElo()).isEqualTo(4950);
        assertThat(winner.getTotalMatch()).isEqualTo(1);
        assertThat(winner.getTotalWin()).isEqualTo(1);
        assertThat(result.changes().get("A").eloDelta()).isEqualTo(100);
    }

    private UserGameStatisticJpaRepository serviceRepository() {
        return repository;
    }

}
