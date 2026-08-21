package com.partygameonline.room.api.dto;

import com.partygameonline.room.domain.GameRoom;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RoomResponse(
        String id,
        String name,
        String gameId,
        String hostPlayerId,
        int maxPlayers,
        String visibility,
        String status,
        long serverSequence,
        Instant createdAt,
        List<RoomPlayerResponse> players,
        Map<String, Object> settings
) {

    public static RoomResponse from(GameRoom room) {
        return new RoomResponse(
                room.getId().value(),
                room.getName().value(),
                room.getGameId(),
                room.getHostPlayerId(),
                room.getMaxPlayers(),
                room.getVisibility().name(),
                room.getStatus().name(),
                room.getServerSequence(),
                room.getCreatedAt(),
                room.getPlayers().stream().map(RoomPlayerResponse::from).toList(),
                room.getSettings()
        );
    }
}
