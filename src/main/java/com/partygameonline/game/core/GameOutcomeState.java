package com.partygameonline.game.core;

import java.util.Set;

/**
 * Optional contract for games whose final result can have more than one
 * winning player (for example, a faction game).
 */
public interface GameOutcomeState {

    Set<String> winnerPlayerIds();

    GamePlayerOutcome playerOutcome(String playerId);
}
