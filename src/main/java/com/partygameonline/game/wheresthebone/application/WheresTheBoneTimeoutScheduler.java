package com.partygameonline.game.wheresthebone.application;

import com.partygameonline.game.runtime.GameActionDispatcher;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.game.runtime.GameSessionRepository;
import com.partygameonline.game.wheresthebone.WheresTheBoneGameManifest;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneGameState;
import com.partygameonline.room.domain.RoomId;
import com.partygameonline.room.infrastructure.RoomRepository;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "games.wheres-the-bone.timeout-scheduler-enabled", matchIfMissing = true)
public class WheresTheBoneTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(WheresTheBoneTimeoutScheduler.class);
    private final GameSessionRepository sessionRepository;
    private final GameActionDispatcher dispatcher;
    private final RoomRepository roomRepository;

    public WheresTheBoneTimeoutScheduler(
            GameSessionRepository sessionRepository,
            GameActionDispatcher dispatcher,
            RoomRepository roomRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.dispatcher = dispatcher;
        this.roomRepository = roomRepository;
    }

    @Scheduled(fixedDelay = 500)
    public void tick() {
        Instant now = Instant.now();
        for (GameSession session : sessionRepository.findAll()) {
            if (session.isFinished() || !WheresTheBoneGameManifest.ID.equals(session.getGameId())
                    || !(session.getState() instanceof WheresTheBoneGameState state)
                    || state.getDeadline() == null || state.getDeadline().isAfter(now)) {
                continue;
            }
            String actorId = roomRepository.findById(RoomId.parse(session.getRoomId()))
                    .flatMap(room -> room.getPlayers().stream().findFirst())
                    .map(player -> player.getPlayerId())
                    .orElse(null);
            if (actorId == null) continue;
            String commandId = "wheres-the-bone-timeout-" + UUID.randomUUID();
            try {
                dispatcher.dispatch(
                        PlayerPrincipal.guest(actorId, state.displayName(actorId)),
                        session.getRoomId(),
                        commandId,
                        Map.of("type", "TIMEOUT", "commandId", commandId, "expectedVersion", state.getVersion())
                );
            } catch (RuntimeException ex) {
                log.warn("Where's the Bone timeout skipped roomId={} reason={}", session.getRoomId(), ex.getMessage());
            }
        }
    }

}
