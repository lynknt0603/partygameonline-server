package com.partygameonline.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.partygameonline.game.nob.NobGameManifest;
import com.partygameonline.game.nob.infrastructure.NobGameRoundEntity;
import com.partygameonline.game.nob.infrastructure.NobGameRoundJpaRepository;
import com.partygameonline.game.wheresthebone.WheresTheBoneGameManifest;
import com.partygameonline.history.infrastructure.MatchEntity;
import com.partygameonline.history.infrastructure.MatchJpaRepository;
import com.partygameonline.history.infrastructure.MatchPlayerEntity;
import com.partygameonline.history.infrastructure.MatchPlayerJpaRepository;
import com.partygameonline.profile.api.dto.ProfileStatsResponse;
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
class ProfileStatsServiceTests {

    private static final String PLAYER_ID = "NB-7X9X2M";

    @Mock
    private MatchJpaRepository matchRepository;

    @Mock
    private MatchPlayerJpaRepository playerRepository;

    @Mock
    private NobGameRoundJpaRepository roundRepository;

    @InjectMocks
    private ProfileStatsService service;

    @Test
    void calculatesNobTotalsAndFactionBreakdownFromFinishedPlayerRows() {
        MatchEntity vampireWin = match("2026-08-20T10:00:00Z");
        MatchEntity vampireLoss = match("2026-08-20T11:00:00Z");
        MatchEntity werewolfWin = match("2026-08-20T12:00:00Z");
        MatchEntity halfbloodLoss = match("2026-08-20T13:00:00Z");
        List<MatchEntity> matches = List.of(vampireWin, vampireLoss, werewolfWin, halfbloodLoss);
        when(matchRepository.findAllFinishedForPlayerAndGame(PLAYER_ID, NobGameManifest.ID)).thenReturn(matches);
        when(playerRepository.findByMatchIdInOrderByMatchIdAscSeatAscIdAsc(
                matches.stream().map(MatchEntity::getId).toList()
        )).thenReturn(List.of(
                player(vampireWin, "WIN", "VAMPIRE"),
                player(vampireLoss, "LOSS", "VAMPIRE"),
                player(werewolfWin, "WIN", "WEREWOLF"),
                player(halfbloodLoss, "LOSS", "HALFBLOOD")
        ));
        when(roundRepository.findByGameIdInAndPlayerIdOrderByGameIdAscRoundNumberAscIdAsc(
                matches.stream().map(MatchEntity::getId).toList(), PLAYER_ID
        )).thenReturn(List.of(
                round(vampireWin, 1, "VAMPIRE", "WIN", "VAMPIRE"),
                round(vampireLoss, 1, "VAMPIRE", "LOSS", "WEREWOLF"),
                round(werewolfWin, 1, "WEREWOLF", "WIN", "WEREWOLF"),
                round(halfbloodLoss, 1, "HALFBLOOD", "LOSS", "VAMPIRE")
        ));

        ProfileStatsResponse response = service.getStats(
                new PlayerPrincipal(PLAYER_ID, "BloodMoon", com.partygameonline.session.domain.SessionKind.MEMBER,
                        Instant.parse("2025-02-12T00:00:00Z"))
        );

        assertThat(response.player().joinedAt()).isEqualTo("12/02/2025");
        assertThat(response.player().role()).isEqualTo("Member");
        assertThat(response.player().platform()).isEqualTo("Web");
        assertThat(response.nobStats().totalMatches()).isEqualTo(4);
        assertThat(response.nobStats().matchesWon()).isEqualTo(2);
        assertThat(response.nobStats().winRate()).isEqualTo(50.0);
        assertThat(response.nobStats().vampire()).isEqualTo(new ProfileStatsResponse.FactionStats(2, 1, 50.0));
        assertThat(response.nobStats().werewolf()).isEqualTo(new ProfileStatsResponse.FactionStats(1, 1, 100.0));
        assertThat(response.nobStats().halfblood()).isEqualTo(new ProfileStatsResponse.FactionStats(1, 0, 0.0));
    }

    @Test
    void countsFactionStatsPerRoundWhileTotalsStayPerGame() {
        MatchEntity firstGame = match("2026-08-21T10:00:00Z");
        MatchEntity secondGame = match("2026-08-21T11:00:00Z");
        List<MatchEntity> games = List.of(firstGame, secondGame);
        when(matchRepository.findAllFinishedForPlayerAndGame(PLAYER_ID, NobGameManifest.ID)).thenReturn(games);
        when(playerRepository.findByMatchIdInOrderByMatchIdAscSeatAscIdAsc(
                games.stream().map(MatchEntity::getId).toList()
        )).thenReturn(List.of(
                player(firstGame, "WIN", "VAMPIRE"),
                player(secondGame, "LOSS", "HALFBLOOD")
        ));
        when(roundRepository.findByGameIdInAndPlayerIdOrderByGameIdAscRoundNumberAscIdAsc(
                games.stream().map(MatchEntity::getId).toList(), PLAYER_ID
        )).thenReturn(List.of(
                round(firstGame, 1, "VAMPIRE", "WIN", "VAMPIRE"),
                round(firstGame, 2, "WEREWOLF", "LOSS", "WEREWOLF"),
                round(secondGame, 1, "HALFBLOOD", "WIN", "LAST_HOPE_HALFBLOOD")
        ));

        ProfileStatsResponse response = service.getStats(
                new PlayerPrincipal(PLAYER_ID, "BloodMoon", com.partygameonline.session.domain.SessionKind.MEMBER,
                        Instant.parse("2025-02-12T00:00:00Z"))
        );

        assertThat(response.nobStats().totalMatches()).isEqualTo(2);
        assertThat(response.nobStats().matchesWon()).isEqualTo(1);
        assertThat(response.nobStats().winRate()).isEqualTo(50.0);
        assertThat(response.nobStats().vampire()).isEqualTo(new ProfileStatsResponse.FactionStats(1, 1, 100.0));
        assertThat(response.nobStats().werewolf()).isEqualTo(new ProfileStatsResponse.FactionStats(1, 0, 0.0));
        assertThat(response.nobStats().halfblood()).isEqualTo(new ProfileStatsResponse.FactionStats(1, 1, 100.0));
    }

    @Test
    void groupsWheresTheBoneStatsIntoWhiteYardAndBoneThiefTeams() {
        MatchEntity whiteWin = match(WheresTheBoneGameManifest.ID, "2026-08-22T10:00:00Z");
        MatchEntity yardLoss = match(WheresTheBoneGameManifest.ID, "2026-08-22T11:00:00Z");
        MatchEntity thiefWin = match(WheresTheBoneGameManifest.ID, "2026-08-22T12:00:00Z");
        MatchEntity packmateLoss = match(WheresTheBoneGameManifest.ID, "2026-08-22T13:00:00Z");
        List<MatchEntity> matches = List.of(whiteWin, yardLoss, thiefWin, packmateLoss);
        when(matchRepository.findAllFinishedForPlayerAndGame(PLAYER_ID, NobGameManifest.ID))
                .thenReturn(List.of());
        when(matchRepository.findAllFinishedForPlayerAndGame(PLAYER_ID, WheresTheBoneGameManifest.ID))
                .thenReturn(matches);
        when(playerRepository.findByMatchIdInOrderByMatchIdAscSeatAscIdAsc(
                matches.stream().map(MatchEntity::getId).toList()
        )).thenReturn(List.of(
                bonePlayer(whiteWin, "WIN", "WHITE_DOG"),
                bonePlayer(yardLoss, "LOSS", "YARD_DOG"),
                bonePlayer(thiefWin, "WIN", "BONE_THIEF"),
                bonePlayer(packmateLoss, "LOSS", "PACKMATE")
        ));

        ProfileStatsResponse.WheresTheBoneStats stats = service.getStats(
                new PlayerPrincipal(PLAYER_ID, "BloodMoon", com.partygameonline.session.domain.SessionKind.MEMBER,
                        Instant.parse("2025-02-12T00:00:00Z"))
        ).wheresTheBoneStats();

        assertThat(stats.totalMatches()).isEqualTo(4);
        assertThat(stats.matchesWon()).isEqualTo(2);
        assertThat(stats.whiteDog()).isEqualTo(new ProfileStatsResponse.FactionStats(1, 1, 100.0));
        assertThat(stats.yardTeam()).isEqualTo(new ProfileStatsResponse.FactionStats(1, 0, 0.0));
        assertThat(stats.boneThiefTeam()).isEqualTo(new ProfileStatsResponse.FactionStats(2, 1, 50.0));
    }

    private static MatchEntity match(String finishedAt) {
        return match(NobGameManifest.ID, finishedAt);
    }

    private static MatchEntity match(String gameId, String finishedAt) {
        Instant finished = Instant.parse(finishedAt);
        return MatchEntity.completed(
                gameId,
                "ROOM1",
                null,
                "COMPLETED",
                finished.minusSeconds(60),
                finished
        );
    }

    private static MatchPlayerEntity player(MatchEntity match, String result, String bloodline) {
        return MatchPlayerEntity.newPlayer(
                match.getId(),
                null,
                PLAYER_ID,
                "BloodMoon",
                0,
                result,
                10,
                "HUNTER",
                bloodline
        );
    }

    private static MatchPlayerEntity bonePlayer(MatchEntity match, String result, String role) {
        return MatchPlayerEntity.newPlayer(
                match.getId(),
                null,
                PLAYER_ID,
                "BloodMoon",
                0,
                result,
                0,
                role,
                null
        );
    }

    private static NobGameRoundEntity round(
            MatchEntity match,
            int roundNumber,
            String bloodline,
            String result,
            String roundResult
    ) {
        return new NobGameRoundEntity(
                UUID.randomUUID(),
                match.getId(),
                roundNumber,
                PLAYER_ID,
                bloodline,
                result,
                roundResult,
                false,
                5,
                Instant.now()
        );
    }
}
