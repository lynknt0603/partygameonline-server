package com.partygameonline.game.core;

public record PlayerContext(String playerId, String displayName, ViewerKind kind) {

    public static PlayerContext player(String playerId, String displayName) {
        return new PlayerContext(playerId, displayName, ViewerKind.PLAYER);
    }

    public static PlayerContext spectator(String playerId, String displayName) {
        return new PlayerContext(playerId, displayName, ViewerKind.SPECTATOR);
    }
}
