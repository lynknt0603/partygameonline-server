package com.partygameonline.game.nob.domain;

public record NobPublicLogEntry(
        String type,
        String text,
        String actorPlayerId,
        String targetPlayerId,
        String extraTargetPlayerId,
        String cardCode
) {

    public NobPublicLogEntry(String type, String text) {
        this(type, text, null, null, null, null);
    }

    public NobPublicLogEntry(String type, String text, String actorPlayerId, String targetPlayerId) {
        this(type, text, actorPlayerId, targetPlayerId, null, null);
    }
}
