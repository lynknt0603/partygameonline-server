package com.partygameonline.game.core;

import java.util.List;
import java.util.Map;

public record GameConfig(
        String gameId,
        String roomId,
        List<String> playerIds,
        Map<String, String> displayNames,
        long seed,
        Map<String, Object> settings
) {

    public GameConfig {
        playerIds = List.copyOf(playerIds);
        displayNames = Map.copyOf(displayNames);
        settings = settings == null ? Map.of() : Map.copyOf(settings);
    }

    public GameConfig(
            String gameId,
            String roomId,
            List<String> playerIds,
            Map<String, String> displayNames,
            long seed
    ) {
        this(gameId, roomId, playerIds, displayNames, seed, Map.of());
    }

    public String displayName(String playerId) {
        return displayNames.getOrDefault(playerId, playerId);
    }
}
