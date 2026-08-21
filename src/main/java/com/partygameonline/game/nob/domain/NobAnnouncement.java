package com.partygameonline.game.nob.domain;

import java.time.Instant;
import java.util.UUID;

public record NobAnnouncement(
        String id,
        String type,
        String actorPlayerId,
        String targetPlayerId,
        String cardCode,
        String reactionCardCode,
        String messageKey,
        Instant createdAt,
        Instant displayUntil
) {

    public static NobAnnouncement of(
            String type,
            String actorPlayerId,
            String targetPlayerId,
            String cardCode,
            String reactionCardCode,
            String messageKey,
            int displayMs
    ) {
        Instant now = Instant.now();
        return new NobAnnouncement(
                UUID.randomUUID().toString(),
                type,
                actorPlayerId,
                targetPlayerId,
                cardCode,
                reactionCardCode,
                messageKey,
                now,
                now.plusMillis(Math.max(displayMs, 500))
        );
    }
}
