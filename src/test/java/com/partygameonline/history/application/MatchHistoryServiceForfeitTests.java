package com.partygameonline.history.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.GameOutcomeState;
import com.partygameonline.game.core.GameRegistry;
import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.nob.infrastructure.NobGameRoundJpaRepository;
import com.partygameonline.game.notinmypot.NotInMyPotGameManifest;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.history.infrastructure.MatchEntity;
import com.partygameonline.history.infrastructure.MatchJpaRepository;
import com.partygameonline.history.infrastructure.MatchPlayerJpaRepository;
import com.partygameonline.ranking.application.EloRatingService;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomId;
import com.partygameonline.room.domain.RoomName;
import com.partygameonline.room.domain.RoomVisibility;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class MatchHistoryServiceForfeitTests {

    @Test
    void massForfeitNotInMyPotMatchIsUnrankedAndDoesNotRewardSoleSurvivor() {
        MatchJpaRepository matchRepository = mock(MatchJpaRepository.class);
        MatchPlayerJpaRepository matchPlayerRepository = mock(MatchPlayerJpaRepository.class);
        GameRegistry gameRegistry = mock(GameRegistry.class);
        NobGameRoundJpaRepository roundRepository = mock(NobGameRoundJpaRepository.class);
        EloRatingService eloRatingService = mock(EloRatingService.class);
        MatchHistoryService service = new MatchHistoryService(
                matchRepository,
                matchPlayerRepository,
                gameRegistry,
                roundRepository,
                eloRatingService
        );

        RoomId roomId = new RoomId("BOTS");
        GameRoom room = new GameRoom(
                roomId,
                new RoomName("Bot forfeit"),
                NotInMyPotGameManifest.ID,
                "host",
                "Host",
                4,
                RoomVisibility.PRIVATE,
                Instant.now()
        );
        room.join("bot-1", "Bot 1");
        room.join("bot-2", "Bot 2");
        room.join("bot-3", "Bot 3");
        room.markDisconnected("bot-1");
        room.markDisconnected("bot-2");
        room.markDisconnected("bot-3");

        List<String> playerIds = List.of("host", "bot-1", "bot-2", "bot-3");
        GameOutcomeState factionOutcome = mock(GameOutcomeState.class);
        when(factionOutcome.winnerPlayerIds()).thenReturn(Set.of("bot-1", "bot-2"));
        GameSession session = new GameSession(
                roomId.value(),
                NotInMyPotGameManifest.ID,
                new GameConfig(
                        NotInMyPotGameManifest.ID,
                        roomId.value(),
                        playerIds,
                        Map.of("host", "Host", "bot-1", "Bot 1", "bot-2", "Bot 2", "bot-3", "Bot 3"),
                        1L
                ),
                new SeededRandomSource(1L),
                factionOutcome,
                Instant.now()
        );
        session.finish("bot-1", Instant.now());

        when(matchRepository.save(any(MatchEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        for (String botId : List.of("bot-1", "bot-2", "bot-3")) {
            when(eloRatingService.applyForfeit(NotInMyPotGameManifest.ID, botId))
                    .thenReturn(forfeit(botId));
        }
        service.recordIfFinished(room, session);

        InOrder order = inOrder(eloRatingService);
        order.verify(eloRatingService).applyForfeit(NotInMyPotGameManifest.ID, "bot-1");
        order.verify(eloRatingService).applyForfeit(NotInMyPotGameManifest.ID, "bot-2");
        order.verify(eloRatingService).applyForfeit(NotInMyPotGameManifest.ID, "bot-3");
        verify(eloRatingService, never()).completeMatch(any(), any(), any(), any());
        verify(eloRatingService, never()).applyForfeit(NotInMyPotGameManifest.ID, "host");
        assertThat(session.getForfeitedPlayerIds()).containsExactlyInAnyOrder("bot-1", "bot-2", "bot-3");
        ArgumentCaptor<MatchEntity> matches = ArgumentCaptor.forClass(MatchEntity.class);
        verify(matchRepository, atLeastOnce()).save(matches.capture());
        MatchEntity persisted = matches.getAllValues().get(matches.getAllValues().size() - 1);
        assertThat(persisted.getWinnerPlayerId()).isNull();
        assertThat(persisted.getResult()).isEqualTo("UNRANKED_FORFEIT");
    }

    private static EloRatingService.EloMatchResult forfeit(String playerId) {
        return new EloRatingService.EloMatchResult(Map.of(
                playerId,
                new EloRatingService.EloChange(playerId, false, 5000, -100, 4900)
        ), 5000);
    }
}
