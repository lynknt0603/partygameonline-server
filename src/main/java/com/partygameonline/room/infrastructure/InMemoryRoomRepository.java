package com.partygameonline.room.infrastructure;

import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryRoomRepository implements RoomRepository {

    private final ConcurrentHashMap<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> playerRooms = new ConcurrentHashMap<>();

    @Override
    public GameRoom save(GameRoom room) {
        rooms.put(room.getId().value(), room);
        return room;
    }

    @Override
    public Optional<GameRoom> findById(RoomId roomId) {
        return Optional.ofNullable(rooms.get(roomId.value()));
    }

    @Override
    public Optional<GameRoom> findByPlayerId(String playerId) {
        String roomId = playerRooms.get(playerId);
        if (roomId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(rooms.get(roomId));
    }

    @Override
    public List<GameRoom> findPublicWaiting() {
        return rooms.values().stream().filter(GameRoom::isPublicWaiting).toList();
    }

    @Override
    public boolean exists(RoomId roomId) {
        return rooms.containsKey(roomId.value());
    }

    @Override
    public void delete(RoomId roomId) {
        GameRoom removed = rooms.remove(roomId.value());
        if (removed != null) {
            removed.getPlayers().forEach(player -> playerRooms.remove(player.getPlayerId(), roomId.value()));
        }
    }

    @Override
    public void indexPlayer(String playerId, RoomId roomId) {
        playerRooms.put(playerId, roomId.value());
    }

    @Override
    public void removePlayerIndex(String playerId) {
        playerRooms.remove(playerId);
    }

    @Override
    public void deleteAll() {
        rooms.clear();
        playerRooms.clear();
    }
}
