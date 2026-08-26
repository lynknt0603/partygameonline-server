package com.partygameonline.ranking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.partygameonline.game.nob.NobGameManifest;
import com.partygameonline.game.nob.infrastructure.NobGameRoundEntity;
import com.partygameonline.game.nob.infrastructure.NobGameRoundJpaRepository;
import com.partygameonline.history.infrastructure.MatchPlayerEntity;
import com.partygameonline.history.infrastructure.MatchPlayerJpaRepository;
import com.partygameonline.ranking.api.dto.RankingResponse;
import com.partygameonline.ranking.infrastructure.UserGameStatisticEntity;
import com.partygameonline.ranking.infrastructure.UserGameStatisticJpaRepository;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RankingServiceTests {

    @Mock
    private UserGameStatisticJpaRepository statisticRepository;

    @Mock
    private MatchPlayerJpaRepository matchPlayerRepository;

    @Mock
    private NobGameRoundJpaRepository roundRepository;

    @InjectMocks
    private RankingService rankingService;

    @Test
    void ranksByHighestEloAndKeepsCurrentPlayerRank() {
        UserGameStatisticEntity first = statistic("p-first", 900);
        UserGameStatisticEntity second = statistic("p-second", 500);
        when(statisticRepository.findByGameCodeOrderByHighestEloDescEloNobDescTotalWinDescUserIdAsc(
                NobGameManifest.ID
        )).thenReturn(List.of(first, second));
        when(matchPlayerRepository.findByPlayerIdInOrderByCreatedAtDescIdAsc(List.of("p-first", "p-second")))
                .thenReturn(List.of(
                        MatchPlayerEntity.newPlayer(UUID.randomUUID(), null, "p-first", "NightHowl", 0),
                        MatchPlayerEntity.newPlayer(UUID.randomUUID(), null, "p-second", "SilverPaw", 0)
                ));
        when(roundRepository.findByPlayerIdInOrderByCreatedAtDescIdAsc(List.of("p-first", "p-second")))
                .thenReturn(List.of(
                        round("p-first", "VAMPIRE", "WIN"),
                        round("p-second", "WEREWOLF", "WIN")
                ));

        RankingResponse response = rankingService.getRanking(
                NobGameManifest.ID,
                "highestElo",
                null,
                0,
                7,
                PlayerPrincipal.guest("p-second", "SilverPaw")
        );

        assertThat(response.podium()).extracting(RankingResponse.RankingEntry::displayName)
                .containsExactly("NightHowl", "SilverPaw");
        assertThat(response.podium().getFirst().highestElo()).isEqualTo(5900);
        assertThat(response.me().rank()).isEqualTo(2);
        assertThat(response.podium().getFirst().favoriteBloodline()).isEqualTo("VAMPIRE");
    }

    @Test
    void bloodlineFilterUsesRoundSnapshots() {
        UserGameStatisticEntity vampire = statistic("p-vampire", 400);
        UserGameStatisticEntity werewolf = statistic("p-werewolf", 800);
        when(statisticRepository.findByGameCodeOrderByHighestEloDescEloNobDescTotalWinDescUserIdAsc(
                NobGameManifest.ID
        )).thenReturn(List.of(vampire, werewolf));
        when(matchPlayerRepository.findByPlayerIdInOrderByCreatedAtDescIdAsc(List.of("p-vampire", "p-werewolf")))
                .thenReturn(List.of(
                        MatchPlayerEntity.newPlayer(UUID.randomUUID(), null, "p-vampire", "Vamp", 0),
                        MatchPlayerEntity.newPlayer(UUID.randomUUID(), null, "p-werewolf", "Wolf", 0)
                ));
        when(roundRepository.findByPlayerIdInOrderByCreatedAtDescIdAsc(List.of("p-vampire", "p-werewolf")))
                .thenReturn(List.of(
                        round("p-vampire", "VAMPIRE", "WIN"),
                        round("p-werewolf", "WEREWOLF", "WIN")
                ));

        RankingResponse response = rankingService.getRanking(
                NobGameManifest.ID,
                "bloodlineWins",
                "VAMPIRE",
                0,
                7,
                null
        );

        assertThat(response.totalPlayers()).isEqualTo(1);
        assertThat(response.podium()).singleElement().satisfies(entry -> {
            assertThat(entry.displayName()).isEqualTo("Vamp");
            assertThat(entry.favoriteBloodline()).isEqualTo("VAMPIRE");
            assertThat(entry.bloodlineWins()).isEqualTo(1);
        });
    }

    @Test
    void unfilteredBloodlineRankingUsesTheMostWinsFromOneBloodline() {
        UserGameStatisticEntity player = statistic("p-mixed", 800);
        when(statisticRepository.findByGameCodeOrderByHighestEloDescEloNobDescTotalWinDescUserIdAsc(
                NobGameManifest.ID
        )).thenReturn(List.of(player));
        when(matchPlayerRepository.findByPlayerIdInOrderByCreatedAtDescIdAsc(List.of("p-mixed")))
                .thenReturn(List.of(
                        MatchPlayerEntity.newPlayer(UUID.randomUUID(), null, "p-mixed", "Mixed", 0)
                ));
        when(roundRepository.findByPlayerIdInOrderByCreatedAtDescIdAsc(List.of("p-mixed")))
                .thenReturn(List.of(
                        round("p-mixed", "VAMPIRE", "WIN"),
                        round("p-mixed", "VAMPIRE", "WIN"),
                        round("p-mixed", "WEREWOLF", "WIN"),
                        round("p-mixed", "WEREWOLF", "WIN"),
                        round("p-mixed", "WEREWOLF", "WIN"),
                        round("p-mixed", "HALFBLOOD", "WIN")
                ));

        RankingResponse response = rankingService.getRanking(
                NobGameManifest.ID,
                "bloodlineWins",
                null,
                0,
                7,
                null
        );

        assertThat(response.podium()).singleElement().satisfies(entry -> {
            assertThat(entry.favoriteBloodline()).isEqualTo("WEREWOLF");
            assertThat(entry.bloodlineWins()).isEqualTo(3);
        });
    }

    private static UserGameStatisticEntity statistic(String playerId, int delta) {
        UserGameStatisticEntity statistic = UserGameStatisticEntity.newStatistic(playerId, NobGameManifest.ID);
        statistic.applyRatingDelta(delta);
        return statistic;
    }

    private static NobGameRoundEntity round(String playerId, String bloodline, String result) {
        return new NobGameRoundEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                playerId,
                bloodline,
                result,
                "SCORE",
                false,
                4,
                Instant.now()
        );
    }
}
