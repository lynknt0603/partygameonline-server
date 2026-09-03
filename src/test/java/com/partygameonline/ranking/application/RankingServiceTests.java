package com.partygameonline.ranking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.partygameonline.game.nob.NobGameManifest;
import com.partygameonline.game.notinmypot.NotInMyPotGameManifest;
import com.partygameonline.game.nob.infrastructure.NobGameRoundEntity;
import com.partygameonline.game.nob.infrastructure.NobGameRoundJpaRepository;
import com.partygameonline.game.wheresthebone.WheresTheBoneGameManifest;
import com.partygameonline.history.infrastructure.MatchPlayerEntity;
import com.partygameonline.history.infrastructure.MatchPlayerJpaRepository;
import com.partygameonline.ranking.api.dto.RankingResponse;
import com.partygameonline.ranking.infrastructure.UserGameStatisticEntity;
import com.partygameonline.ranking.infrastructure.UserGameStatisticJpaRepository;
import com.partygameonline.session.domain.PlayerPrincipal;
import com.partygameonline.user.infrastructure.UserEntity;
import com.partygameonline.user.infrastructure.UserJpaRepository;
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

    @Mock
    private UserJpaRepository userRepository;

    @InjectMocks
    private RankingService rankingService;

    @Test
    void excludesNobPlayersWhoOnlyHaveAForfeitPenalty() {
        UserGameStatisticEntity forfeitOnly = UserGameStatisticEntity.newStatistic(
                "forfeit-only",
                NobGameManifest.ID
        );
        forfeitOnly.applyRatingDelta(-50);
        when(statisticRepository.findByGameCode(NobGameManifest.ID)).thenReturn(List.of(forfeitOnly));

        RankingResponse response = rankingService.getRanking(
                NobGameManifest.ID,
                "highestElo",
                null,
                0,
                7,
                null
        );

        assertThat(response.totalPlayers()).isZero();
        assertThat(response.podium()).isEmpty();
        assertThat(response.entries()).isEmpty();
    }

    @Test
    void resolvesLegacyDatabaseUserIdToAccountIdentity() {
        UUID databaseId = UUID.randomUUID();
        String legacyPlayerId = databaseId.toString();
        String userKey = UUID.randomUUID().toString();
        Instant now = Instant.now();
        UserEntity user = new UserEntity(
                databaseId,
                "Blood Moon",
                "lynknt01",
                "encrypted",
                userKey,
                "vampire.png",
                now,
                now
        );
        UserGameStatisticEntity statistic = statistic(legacyPlayerId, 250);
        when(statisticRepository.findByGameCode(NobGameManifest.ID)).thenReturn(List.of(statistic));
        when(userRepository.findAllById(List.of(databaseId))).thenReturn(List.of(user));

        RankingResponse response = rankingService.getRanking(
                NobGameManifest.ID,
                "highestElo",
                null,
                0,
                7,
                null
        );

        assertThat(response.podium()).singleElement().satisfies(entry -> {
            assertThat(entry.username()).isEqualTo("lynknt01");
            assertThat(entry.displayName()).isEqualTo("Blood Moon");
            assertThat(entry.avatarUrl()).endsWith("/vampire.png");
        });
    }

    @Test
    void ranksByHighestEloAndKeepsCurrentPlayerRank() {
        UserGameStatisticEntity first = statistic("p-first", 900);
        UserGameStatisticEntity second = statistic("p-second", 500);
        when(statisticRepository.findByGameCode(NobGameManifest.ID)).thenReturn(List.of(first, second));
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
        when(statisticRepository.findByGameCode(NobGameManifest.ID)).thenReturn(List.of(vampire, werewolf));
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
        when(statisticRepository.findByGameCode(NobGameManifest.ID)).thenReturn(List.of(player));
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

    @Test
    void notInMyPotRankingUsesItsOwnEloAndDoesNotLoadNobBloodlines() {
        UserGameStatisticEntity first = UserGameStatisticEntity.newStatistic(
                "pot-first",
                NotInMyPotGameManifest.ID
        );
        first.applyRatingDelta(350);
        UserGameStatisticEntity second = UserGameStatisticEntity.newStatistic(
                "pot-second",
                NotInMyPotGameManifest.ID
        );
        second.applyRatingDelta(100);
        when(statisticRepository.findByGameCode(NotInMyPotGameManifest.ID))
                .thenReturn(List.of(second, first));
        when(matchPlayerRepository.findByPlayerIdInOrderByCreatedAtDescIdAsc(
                List.of("pot-second", "pot-first")
        )).thenReturn(List.of(
                MatchPlayerEntity.newPlayer(UUID.randomUUID(), null, "pot-first", "Chef One", 0),
                MatchPlayerEntity.newPlayer(UUID.randomUUID(), null, "pot-second", "Chef Two", 1)
        ));

        RankingResponse response = rankingService.getRanking(
                NotInMyPotGameManifest.ID,
                "bloodlineWins",
                "VAMPIRE",
                0,
                7,
                null
        );

        assertThat(response.gameId()).isEqualTo(NotInMyPotGameManifest.ID);
        assertThat(response.sort()).isEqualTo("highestElo");
        assertThat(response.bloodline()).isNull();
        assertThat(response.podium()).extracting(RankingResponse.RankingEntry::displayName)
                .containsExactly("Chef One", "Chef Two");
        assertThat(response.podium().getFirst().elo()).isEqualTo(5350);
        assertThat(response.podium().getFirst().highestElo()).isEqualTo(5350);
        assertThat(response.podium().getFirst().favoriteBloodline()).isNull();
    }

    @Test
    void notInMyPotVegetarianRankingUsesFactionWinsThenWinRateAndReturnsRealRates() {
        UserGameStatisticEntity frequentWinner = UserGameStatisticEntity.newStatistic(
                "pot-frequent",
                NotInMyPotGameManifest.ID
        );
        UserGameStatisticEntity perfectWinner = UserGameStatisticEntity.newStatistic(
                "pot-perfect",
                NotInMyPotGameManifest.ID
        );
        UserGameStatisticEntity meatOnly = UserGameStatisticEntity.newStatistic(
                "pot-meat-only",
                NotInMyPotGameManifest.ID
        );
        List<String> playerIds = List.of("pot-perfect", "pot-meat-only", "pot-frequent");
        when(statisticRepository.findByGameCode(NotInMyPotGameManifest.ID))
                .thenReturn(List.of(perfectWinner, meatOnly, frequentWinner));
        when(matchPlayerRepository.findByPlayerIdInOrderByCreatedAtDescIdAsc(playerIds))
                .thenReturn(List.of(
                        player("pot-frequent", "Frequent", "WIN", "VEGETARIAN"),
                        player("pot-perfect", "Perfect", "WIN", "VEGETARIAN"),
                        player("pot-meat-only", "Carnivore", "WIN", "MEAT_EATER")
                ));
        when(matchPlayerRepository.findByGameIdAndPlayerIdInOrderByCreatedAtDescIdAsc(
                NotInMyPotGameManifest.ID,
                playerIds
        )).thenReturn(List.of(
                player("pot-frequent", "Frequent", "WIN", "VEGETARIAN"),
                player("pot-frequent", "Frequent", "WIN", "VEGETARIAN"),
                player("pot-frequent", "Frequent", "WIN", "VEGETARIAN"),
                player("pot-frequent", "Frequent", "LOSS", "VEGETARIAN"),
                player("pot-perfect", "Perfect", "WIN", "VEGETARIAN"),
                player("pot-perfect", "Perfect", "WIN", "VEGETARIAN"),
                player("pot-meat-only", "Carnivore", "WIN", "MEAT_EATER")
        ));

        RankingResponse response = rankingService.getRanking(
                NotInMyPotGameManifest.ID,
                "vegetarianWins",
                null,
                0,
                7,
                null
        );

        assertThat(response.sort()).isEqualTo("vegetarianWins");
        assertThat(response.totalPlayers()).isEqualTo(2);
        assertThat(response.podium()).extracting(RankingResponse.RankingEntry::displayName)
                .containsExactly("Frequent", "Perfect");
        assertThat(response.podium().getFirst().vegetarianMatches()).isEqualTo(4);
        assertThat(response.podium().getFirst().vegetarianWins()).isEqualTo(3);
        assertThat(response.podium().getFirst().vegetarianWinRate()).isEqualTo(75.0);
        assertThat(response.podium().get(1).vegetarianMatches()).isEqualTo(2);
        assertThat(response.podium().get(1).vegetarianWins()).isEqualTo(2);
        assertThat(response.podium().get(1).vegetarianWinRate()).isEqualTo(100.0);

        RankingResponse meatEaters = rankingService.getRanking(
                NotInMyPotGameManifest.ID,
                "meatEaterWins",
                null,
                0,
                7,
                null
        );
        assertThat(meatEaters.sort()).isEqualTo("meatEaterWins");
        assertThat(meatEaters.podium()).singleElement().satisfies(entry -> {
            assertThat(entry.displayName()).isEqualTo("Carnivore");
            assertThat(entry.meatEaterMatches()).isEqualTo(1);
            assertThat(entry.meatEaterWins()).isEqualTo(1);
            assertThat(entry.meatEaterWinRate()).isEqualTo(100.0);
        });
    }

    @Test
    void wheresTheBoneRankingCountsWinsForThreeRoleGroups() {
        UserGameStatisticEntity alpha = UserGameStatisticEntity.newStatistic(
                "dog-alpha",
                WheresTheBoneGameManifest.ID
        );
        UserGameStatisticEntity beta = UserGameStatisticEntity.newStatistic(
                "dog-beta",
                WheresTheBoneGameManifest.ID
        );
        UserGameStatisticEntity cursed = UserGameStatisticEntity.newStatistic(
                "dog-cursed",
                WheresTheBoneGameManifest.ID
        );
        UserGameStatisticEntity thief = UserGameStatisticEntity.newStatistic(
                "dog-thief",
                WheresTheBoneGameManifest.ID
        );
        UserGameStatisticEntity yard = UserGameStatisticEntity.newStatistic(
                "dog-yard",
                WheresTheBoneGameManifest.ID
        );
        List<String> playerIds = List.of("dog-alpha", "dog-beta", "dog-cursed", "dog-thief", "dog-yard");
        when(statisticRepository.findByGameCode(WheresTheBoneGameManifest.ID))
                .thenReturn(List.of(alpha, beta, cursed, thief, yard));
        when(matchPlayerRepository.findByPlayerIdInOrderByCreatedAtDescIdAsc(playerIds))
                .thenReturn(List.of(
                        player("dog-alpha", "Alpha", "WIN", "WHITE_DOG"),
                        player("dog-beta", "Beta", "WIN", "WHITE_DOG"),
                        player("dog-cursed", "Cursed", "WIN", "PACKMATE"),
                        player("dog-thief", "Thief", "WIN", "BONE_THIEF"),
                        player("dog-yard", "Yard", "WIN", "YARD_DOG")
                ));
        when(matchPlayerRepository.findByGameIdAndPlayerIdInOrderByCreatedAtDescIdAsc(
                WheresTheBoneGameManifest.ID,
                playerIds
        )).thenReturn(List.of(
                player("dog-alpha", "Alpha", "WIN", "WHITE_DOG"),
                player("dog-alpha", "Alpha", "LOSS", "WHITE_DOG"),
                player("dog-beta", "Beta", "WIN", "WHITE_DOG"),
                player("dog-beta", "Beta", "WIN", "WHITE_DOG"),
                player("dog-cursed", "Cursed", "WIN", "PACKMATE"),
                player("dog-thief", "Thief", "WIN", "BONE_THIEF"),
                player("dog-thief", "Thief", "WIN", "BONE_THIEF"),
                player("dog-yard", "Yard", "WIN", "YARD_DOG")
        ));

        RankingResponse whiteDogs = rankingService.getRanking(
                WheresTheBoneGameManifest.ID,
                "roleWins",
                null,
                "WHITE_DOG",
                0,
                7,
                null
        );

        assertThat(whiteDogs.role()).isEqualTo("WHITE_DOG");
        assertThat(whiteDogs.totalPlayers()).isEqualTo(2);
        assertThat(whiteDogs.podium()).extracting(RankingResponse.RankingEntry::displayName)
                .containsExactly("Beta", "Alpha");
        assertThat(whiteDogs.podium()).extracting(RankingResponse.RankingEntry::favoriteRole)
                .containsOnly("WHITE_DOG");
        assertThat(whiteDogs.podium()).extracting(RankingResponse.RankingEntry::roleWins)
                .containsExactly(2, 1);

        RankingResponse yardTeam = rankingService.getRanking(
                WheresTheBoneGameManifest.ID,
                "roleWins",
                null,
                "YARD_TEAM",
                0,
                7,
                null
        );
        assertThat(yardTeam.role()).isEqualTo("YARD_TEAM");
        assertThat(yardTeam.podium()).singleElement().satisfies(entry -> {
            assertThat(entry.displayName()).isEqualTo("Yard");
            assertThat(entry.favoriteRole()).isEqualTo("YARD_TEAM");
            assertThat(entry.roleWins()).isEqualTo(1);
        });

        RankingResponse boneThiefTeam = rankingService.getRanking(
                WheresTheBoneGameManifest.ID,
                "roleWins",
                null,
                "BONE_THIEF_TEAM",
                0,
                7,
                null
        );
        assertThat(boneThiefTeam.role()).isEqualTo("BONE_THIEF_TEAM");
        assertThat(boneThiefTeam.totalPlayers()).isEqualTo(2);
        assertThat(boneThiefTeam.podium()).extracting(RankingResponse.RankingEntry::displayName)
                .containsExactly("Thief", "Cursed");
        assertThat(boneThiefTeam.podium()).extracting(RankingResponse.RankingEntry::favoriteRole)
                .containsOnly("BONE_THIEF_TEAM");
        assertThat(boneThiefTeam.podium()).extracting(RankingResponse.RankingEntry::roleWins)
                .containsExactly(2, 1);
    }

    private static UserGameStatisticEntity statistic(String playerId, int delta) {
        UserGameStatisticEntity statistic = UserGameStatisticEntity.newStatistic(playerId, NobGameManifest.ID);
        statistic.applyRatingDelta(delta);
        statistic.completeMatch(false);
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

    private static MatchPlayerEntity player(String playerId, String displayName, String result, String role) {
        return MatchPlayerEntity.newPlayer(
                UUID.randomUUID(),
                null,
                playerId,
                displayName,
                0,
                result,
                null,
                role,
                null
        );
    }
}
