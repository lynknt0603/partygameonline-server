package com.partygameonline.room.api.dto;

import com.partygameonline.room.domain.RoomPlayer;

public record RoomPlayerResponse(
        String playerId,
        String displayName,
        String state
) {

    public static RoomPlayerResponse from(RoomPlayer player) {
        return new RoomPlayerResponse(player.getPlayerId(), player.getDisplayName(), player.getState().name());
    }
}
