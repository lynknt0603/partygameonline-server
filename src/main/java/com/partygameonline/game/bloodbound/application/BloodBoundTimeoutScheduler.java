package com.partygameonline.game.bloodbound.application;

import com.partygameonline.game.bloodbound.BloodBoundGameManifest;
import com.partygameonline.game.bloodbound.domain.BloodBoundActionType;
import com.partygameonline.game.bloodbound.domain.BloodBoundGameState;
import com.partygameonline.game.runtime.GameActionDispatcher;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.game.runtime.GameSessionRepository;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "games.blood-bound.timeout-scheduler-enabled", matchIfMissing = true)
public class BloodBoundTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(BloodBoundTimeoutScheduler.class);

    private final GameSessionRepository sessionRepository;
    private final GameActionDispatcher dispatcher;

    public BloodBoundTimeoutScheduler(
            GameSessionRepository sessionRepository,
            GameActionDispatcher dispatcher
    ) {
        this.sessionRepository = sessionRepository;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 500)
    public void tick() {
        Instant now = Instant.now();
        Collection<GameSession> sessions;
        try {
            sessions = sessionRepository.findAll();
        } catch (RuntimeException ex) {
            log.error("BloodBound timeout scan failed", ex);
            return;
        }

        for (GameSession session : sessions) {
            try {
                if (session.isFinished() || !BloodBoundGameManifest.ID.equals(session.getGameId())) {
                    continue;
                }
                if (!(session.getState() instanceof BloodBoundGameState state)
                        || !state.timeoutIsDue(now)) {
                    continue;
                }

                String actorId = state.getTargetPlayerId() != null
                        ? state.getTargetPlayerId()
                        : (state.getPlayers().isEmpty() ? null : state.getPlayers().getFirst().getPlayerId());
                if (actorId == null) {
                    continue;
                }
                var actor = state.player(actorId);
                String displayName = actor != null ? actor.getDisplayName() : actorId;

                String commandId = "bloodbound-timeout-" + UUID.randomUUID();
                dispatcher.dispatch(
                        PlayerPrincipal.guest(actorId, displayName),
                        session.getRoomId(),
                        commandId,
                        Map.of("type", BloodBoundActionType.TIMEOUT.name(), "commandId", commandId)
                );
            } catch (RuntimeException ex) {
                log.warn("BloodBound timeout skipped roomId={} reason={}", session.getRoomId(), ex.getMessage());
            }
        }
    }
}
