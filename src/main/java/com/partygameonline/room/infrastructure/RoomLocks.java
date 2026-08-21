package com.partygameonline.room.infrastructure;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class RoomLocks {

    private final KeyedLocks playerLocks = new KeyedLocks();
    private final KeyedLocks roomLocks = new KeyedLocks();

    public <T> T withPlayer(String playerId, Supplier<T> action) {
        return playerLocks.withLock(playerId, action);
    }

    public <T> T withPlayerThenRoom(String playerId, String roomId, Supplier<T> action) {
        return playerLocks.withLock(playerId, () -> roomLocks.withLock(roomId, action));
    }

    public <T> T withRoom(String roomId, Supplier<T> action) {
        return roomLocks.withLock(roomId, action);
    }
}
