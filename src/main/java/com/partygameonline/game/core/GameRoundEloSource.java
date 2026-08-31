package com.partygameonline.game.core;

import java.util.List;
import java.util.Map;

/** Optional game-state hook for progressive, round-based ELO settlement. */
public interface GameRoundEloSource {

    List<GameEloRound> completedEloRounds();

    Map<String, Integer> eloSimulation();

    void recordGameEloRoundChanges(int roundNumber, Map<String, GameEloChange> changes);
}
