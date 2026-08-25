package com.partygameonline.realtime;

import java.time.Instant;

public record RoomChatMessage(
        String messageId,
        String playerId,
        String displayName,
        String text,
        Instant sentAt,
        String kind
) {
    public static final String USER = "USER";
}
