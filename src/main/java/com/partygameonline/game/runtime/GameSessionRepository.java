package com.partygameonline.game.runtime;

import java.util.Collection;
import java.util.Optional;

public interface GameSessionRepository {

    GameSession save(GameSession session);

    Optional<GameSession> findByRoomId(String roomId);

    void deleteByRoomId(String roomId);

    Collection<GameSession> findAll();
}
