package com.partygameonline.game.core;

import java.util.Map;

/**
 * Catalogue metadata for one game module. Rules stay in the game module, not here.
 */
public interface GameManifest {

    String id();

    String name();

    int minPlayers();

    int maxPlayers();

    boolean enabled();

    /** Default room settings owned by this game module. */
    default Map<String, Object> defaultRoomSettings() {
        return Map.of();
    }

    /** Normalizes the room settings payload before it is stored. */
    default Map<String, Object> normalizeRoomSettings(Map<String, Object> requested) {
        return Map.of();
    }

    /** Number of players required to start a room for this game. */
    default int requiredPlayers(int roomMaxPlayers) {
        return minPlayers();
    }
}
