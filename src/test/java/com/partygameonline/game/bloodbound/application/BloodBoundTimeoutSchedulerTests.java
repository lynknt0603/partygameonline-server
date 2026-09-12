package com.partygameonline.game.bloodbound.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.partygameonline.game.bloodbound.BloodBoundGameManifest;
import com.partygameonline.game.bloodbound.domain.BloodBoundGameState;
import com.partygameonline.game.bloodbound.domain.BloodBoundPhase;
import com.partygameonline.game.bloodbound.domain.BloodBoundPlayerState;
import com.partygameonline.game.bloodbound.domain.BloodClan;
import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.runtime.GameActionDispatcher;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.game.runtime.InMemoryGameSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BloodBoundTimeoutSchedulerTests {

    private InMemoryGameSessionRepository sessionRepository;
    private GameActionDispatcher dispatcher;
    private BloodBoundTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        sessionRepository = new InMemoryGameSessionRepository();
        dispatcher = mock(GameActionDispatcher.class);
        scheduler = new BloodBoundTimeoutScheduler(sessionRepository, dispatcher);
    }

    @Test
    void tickDispatchesTimeoutWhenDue() {
        BloodBoundGameState state = new BloodBoundGameState("ROOM-1");
        state.setPhase(BloodBoundPhase.INTERVENTION_WINDOW);
        state.setDaggerPlayerId("p1");
        state.setTargetPlayerId("p2");
        state.setPhaseDeadline(Instant.now().minusSeconds(1));
        state.addPlayer(new BloodBoundPlayerState("p1", "Alice", 0, BloodClan.ROSE, 1));
        state.addPlayer(new BloodBoundPlayerState("p2", "Bob", 1, BloodClan.FAN, 1));

        GameConfig config = new GameConfig(BloodBoundGameManifest.ID, "ROOM-1", List.of("p1", "p2"), Map.of("p1", "Alice", "p2", "Bob"), 1L);
        GameSession session = new GameSession(
                "ROOM-1",
                BloodBoundGameManifest.ID,
                config,
                new SeededRandomSource(1L),
                state,
                Instant.now()
        );
        sessionRepository.save(session);

        scheduler.tick();

        verify(dispatcher).dispatch(
                any(),
                eq("ROOM-1"),
                argThat(cmdId -> cmdId.startsWith("bloodbound-timeout-")),
                argThat(payload -> "TIMEOUT".equals(payload.get("type")))
        );
    }

    @Test
    void tickIgnoresWhenDeadlineNotDueOrFinished() {
        BloodBoundGameState state = new BloodBoundGameState("ROOM-2");
        state.setPhase(BloodBoundPhase.INTERVENTION_WINDOW);
        state.setPhaseDeadline(Instant.now().plusSeconds(10));
        state.addPlayer(new BloodBoundPlayerState("p1", "Alice", 0, BloodClan.ROSE, 1));

        GameConfig config = new GameConfig(BloodBoundGameManifest.ID, "ROOM-2", List.of("p1"), Map.of("p1", "Alice"), 1L);
        GameSession session = new GameSession(
                "ROOM-2",
                BloodBoundGameManifest.ID,
                config,
                new SeededRandomSource(1L),
                state,
                Instant.now()
        );
        sessionRepository.save(session);

        scheduler.tick();

        verify(dispatcher, never()).dispatch(any(), any(), any(), any());
    }
}
