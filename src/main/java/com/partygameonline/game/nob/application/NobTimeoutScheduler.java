package com.partygameonline.game.nob.application;

import com.partygameonline.game.nob.NobGameManifest;
import com.partygameonline.game.nob.config.NobGameProperties;
import com.partygameonline.game.nob.domain.NobAction;
import com.partygameonline.game.nob.domain.NobGameState;
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
@ConditionalOnProperty(name = "games.nob.timeout-scheduler-enabled", matchIfMissing = true)
public class NobTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(NobTimeoutScheduler.class);

    private final GameSessionRepository sessionRepository;
    private final GameActionDispatcher dispatcher;
    private final NobGameProperties properties;

    public NobTimeoutScheduler(
            GameSessionRepository sessionRepository,
            GameActionDispatcher dispatcher,
            NobGameProperties properties
    ) {
        this.sessionRepository = sessionRepository;
        this.dispatcher = dispatcher;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        if (!properties.timeoutSchedulerEnabled()) {
            return;
        }
        Instant now = Instant.now();
        for (GameSession session : sessionRepository.findAll()) {
            if (!NobGameManifest.ID.equals(session.getGameId()) || session.isFinished()) {
                continue;
            }
            if (!(session.getState() instanceof NobGameState state)) {
                continue;
            }
            if (!state.timeoutIsDue(now) || state.getPlayers().isEmpty()) {
                continue;
            }
            String actorId = state.getPendingDecision() != null
                    ? state.getPendingDecision().actorId()
                    : state.getPlayers().getFirst().getPlayerId();
            String displayName = state.requirePlayer(actorId).getDisplayName();
            try {
                dispatcher.dispatch(
                        PlayerPrincipal.guest(actorId, displayName),
                        session.getRoomId(),
                        "nob-timeout-" + UUID.randomUUID(),
                        Map.of("type", NobAction.TIMEOUT)
                );
            } catch (RuntimeException ex) {
                log.warn("NOB timeout skipped roomId={} reason={}", session.getRoomId(), ex.getMessage());
            }
        }
    }
}
