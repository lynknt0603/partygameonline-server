package com.partygameonline.game.core;

/**
 * Catalogue metadata for one game module. Rules stay in the game module, not here.
 */
public interface GameManifest {

    String id();

    String name();

    int minPlayers();

    int maxPlayers();

    boolean enabled();
}
