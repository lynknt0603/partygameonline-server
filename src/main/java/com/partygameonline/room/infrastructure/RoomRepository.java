package com.partygameonline.room.infrastructure;

import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomId;
import java.util.List;
import java.util.Optional;

public interface RoomRepository {

    GameRoom save(GameRoom room);

    Optional<GameRoom> findById(RoomId roomId);

    Optional<GameRoom> findByPlayerId(String playerId);

    List<GameRoom> findPublicWaiting();

    boolean exists(RoomId roomId);

    void delete(RoomId roomId);

    void indexPlayer(String playerId, RoomId roomId);

    void removePlayerIndex(String playerId);

    void deleteAll();
}
