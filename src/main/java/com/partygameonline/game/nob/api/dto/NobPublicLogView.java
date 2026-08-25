package com.partygameonline.game.nob.api.dto;

public record NobPublicLogView(
        String type,
        String text,
        String actorPlayerId,
        String targetPlayerId,
        String extraTargetPlayerId,
        String cardCode
) {
}
