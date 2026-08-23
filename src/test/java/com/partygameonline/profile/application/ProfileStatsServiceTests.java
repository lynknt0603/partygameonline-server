package com.partygameonline.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.partygameonline.game.nob.NobGameManifest;
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

    private static MatchEntity match(String finishedAt) {
        Instant finished = Instant.parse(finishedAt);
        return MatchEntity.completed(
                NobGameManifest.ID,
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
}
