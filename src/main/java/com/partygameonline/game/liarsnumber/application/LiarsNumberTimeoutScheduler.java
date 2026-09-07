package com.partygameonline.game.liarsnumber.application;

import com.partygameonline.game.liarsnumber.LiarsNumberGameManifest;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberGameState;
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
@ConditionalOnProperty(name = "games.liars-number.timeout-scheduler-enabled", matchIfMissing = true)
public class LiarsNumberTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(LiarsNumberTimeoutScheduler.class);

    private final GameSessionRepository sessionRepository;
    private final GameActionDispatcher dispatcher;

    public LiarsNumberTimeoutScheduler(GameSessionRepository sessionRepository, GameActionDispatcher dispatcher) {
        this.sessionRepository = sessionRepository;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 500)
    public void tick() {
        Instant now = Instant.now();
        for (GameSession session : sessionRepository.findAll()) {
            if (session.isFinished() || !LiarsNumberGameManifest.ID.equals(session.getGameId())) {
                continue;
            }
            if (!(session.getState() instanceof LiarsNumberGameState state) || !state.timeoutIsDue(now)) {
                continue;
            }
            String actorId = state.currentActorId();
            var actor = state.findPlayer(actorId);
            if (actor == null) {
                continue;
            }
            String requestId = "liars-number-timeout-" + UUID.randomUUID();
            try {
                dispatcher.dispatch(
                        PlayerPrincipal.guest(actorId, actor.getDisplayName()),
                        session.getRoomId(),
                        requestId,
                        Map.of("type", "TIMEOUT", "commandId", requestId)
                );
            } catch (RuntimeException ex) {
                log.warn("Liar's Number timeout skipped roomId={} reason={}", session.getRoomId(), ex.getMessage());
            }
        }
    }
}
