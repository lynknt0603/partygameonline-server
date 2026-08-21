package com.partygameonline.game.nob.domain;

public record NobPublicLogEntry(
        String type,
        String text,
        String actorPlayerId,
        String targetPlayerId
) {

    public NobPublicLogEntry(String type, String text) {
        this(type, text, null, null);
    }
}
