package com.partygameonline.catalog.api.dto;

import com.partygameonline.game.core.GameManifest;

public record GameResponse(
        String id,
        String name,
        int minPlayers,
        int maxPlayers,
        boolean enabled
) {

    public static GameResponse from(GameManifest manifest) {
        return new GameResponse(
                manifest.id(),
                manifest.name(),
                manifest.minPlayers(),
                manifest.maxPlayers(),
                manifest.enabled()
        );
    }
}
