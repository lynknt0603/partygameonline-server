package com.partygameonline.game.runtime;

import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.GameRegistry;
import com.partygameonline.game.core.GameResult;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.RandomSource;
import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomPlayer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GameRuntimeService {

    private final GameRegistry gameRegistry;
    private final GameSessionRepository sessionRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public GameRuntimeService(GameRegistry gameRegistry, GameSessionRepository sessionRepository) {
        this.gameRegistry = gameRegistry;
        this.sessionRepository = sessionRepository;
    }

    public Optional<GameSession> startGame(GameRoom room) {
        if (!gameRegistry.hasEngine(room.getGameId())) {
            return Optional.empty();
        }
        long seed = secureRandom.nextLong();
        RandomSource random = new SeededRandomSource(seed);
        Map<String, String> displayNames = new LinkedHashMap<>();
        for (RoomPlayer player : room.getPlayers()) {
            displayNames.put(player.getPlayerId(), player.getDisplayName());
        }
        GameConfig config = new GameConfig(
                room.getGameId(),
                room.getId().value(),
                displayNames.keySet().stream().toList(),
                displayNames,
                seed,
                room.getSettings()
        );
        Object state = gameRegistry.createGame(room.getGameId(), config, random);
        GameSession session = new GameSession(
                room.getId().value(),
                room.getGameId(),
                config,
                random,
                state,
                Instant.now()
        );
        sessionRepository.save(session);
        room.markInGame();
        return Optional.of(session);
    }

    public Optional<GameSession> findSession(String roomId) {
        return sessionRepository.findByRoomId(roomId);
    }

    public void removeSession(String roomId) {
        sessionRepository.deleteByRoomId(roomId);
    }

    public AppliedAction applyAction(GameSession session, PlayerContext actor, Map<String, Object> payload) {
        Object action = gameRegistry.decodeAction(session.getGameId(), payload);
        ValidationResult validation = gameRegistry.validate(session.getGameId(), session.getState(), actor, action);
        if (!validation.valid()) {
            return AppliedAction.rejected(validation);
        }
        GameResult<Object, Object> result = gameRegistry.apply(
                session.getGameId(),
                session.getState(),
                actor,
                action,
                session.getRandom()
        );
        session.setState(result.state());
        if (result.finished()) {
            session.finish(result.winnerPlayerId(), Instant.now(), "COMPLETED");
        }
        sessionRepository.save(session);
        return AppliedAction.accepted(result);
    }

    public AppliedAction abandon(GameSession session, PlayerContext player) {
        GameResult<Object, Object> result = gameRegistry.onPlayerAbandoned(
                session.getGameId(),
                session.getState(),
                player,
                session.getRandom()
        );
        session.setState(result.state());
        if (result.finished()) {
            session.finish(result.winnerPlayerId(), Instant.now(), "FORFEIT");
        }
        sessionRepository.save(session);
        return AppliedAction.accepted(result);
    }

    public Map<String, Object> projectViews(GameRoom room, GameSession session) {
        Map<String, Object> views = new LinkedHashMap<>();
        for (RoomPlayer player : room.getPlayers()) {
            views.put(player.getPlayerId(), projectView(session, player));
        }
        return views;
    }

    public Object projectView(GameSession session, RoomPlayer player) {
        return gameRegistry.project(
                session.getGameId(),
                session.getState(),
                PlayerContext.player(player.getPlayerId(), player.getDisplayName())
        );
    }
}
