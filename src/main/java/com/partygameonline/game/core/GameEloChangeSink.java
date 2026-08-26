package com.partygameonline.game.core;

import java.util.Map;

/** Optional hook for a game state that displays the generic ELO result. */
public interface GameEloChangeSink {

    void recordEloChanges(Map<String, GameEloChange> changes);
}
