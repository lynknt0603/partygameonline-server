package com.partygameonline.game.nob.api.dto;

import java.time.Instant;

public record NobAnnouncementView(
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
}
