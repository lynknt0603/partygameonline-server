package com.partygameonline.game.notinmypot.application;

import com.partygameonline.game.notinmypot.NotInMyPotGameManifest;
import com.partygameonline.game.notinmypot.domain.NotInMyPotAction;
import com.partygameonline.game.notinmypot.domain.NotInMyPotGameState;
import com.partygameonline.game.notinmypot.domain.NotInMyPotPhase;
import com.partygameonline.game.runtime.GameActionDispatcher;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.game.runtime.GameSessionRepository;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "games.not-in-my-pot.timeout-scheduler-enabled", matchIfMissing = true)
public class NotInMyPotTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotInMyPotTimeoutScheduler.class);

    private final GameSessionRepository sessionRepository;
    private final GameActionDispatcher dispatcher;

    public NotInMyPotTimeoutScheduler(
            GameSessionRepository sessionRepository,
            GameActionDispatcher dispatcher
    ) {
        this.sessionRepository = sessionRepository;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        Instant now = Instant.now();
        for (GameSession session : sessionRepository.findAll()) {
            if (session.isFinished() || !NotInMyPotGameManifest.ID.equals(session.getGameId())) {
                continue;
            }
            if (!(session.getState() instanceof NotInMyPotGameState state)
                    || !state.timeoutIsDue(now)) {
                continue;
            }
            String actorId = state.getPhase() == NotInMyPotPhase.ROLE_REVEAL
                    ? state.activePlayers().stream()
                            .findFirst()
                            .map(player -> player.getPlayerId())
                            .orElse(null)
                    : state.getPendingAction() == null
                            ? state.getCurrentPlayerId()
                            : state.getPendingAction().actorPlayerId();
            var actor = state.player(actorId);
            if (actor == null) {
                continue;
            }
            String requestId = "nimp-timeout-" + UUID.randomUUID();
            try {
                dispatcher.dispatch(
                        PlayerPrincipal.guest(actorId, actor.getDisplayName()),
                        session.getRoomId(),
                        requestId,
                        Map.of("type", NotInMyPotAction.TIMEOUT, "commandId", requestId)
                );
            } catch (RuntimeException ex) {
                log.warn("Not In My Pot timeout skipped roomId={} reason={}", session.getRoomId(), ex.getMessage());
            }
        }
    }
}
