package com.partygameonline.game.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryGameSessionRepository implements GameSessionRepository {

    private final ConcurrentHashMap<String, GameSession> sessions = new ConcurrentHashMap<>();

    @Override
    public GameSession save(GameSession session) {
        sessions.put(session.getRoomId(), session);
        return session;
    }

    @Override
    public Optional<GameSession> findByRoomId(String roomId) {
        return Optional.ofNullable(sessions.get(roomId));
    }

    @Override
    public void deleteByRoomId(String roomId) {
        sessions.remove(roomId);
    }

    @Override
    public Collection<GameSession> findAll() {
        return List.copyOf(sessions.values());
    }
}
