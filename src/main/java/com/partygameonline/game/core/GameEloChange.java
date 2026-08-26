package com.partygameonline.game.core;

/** Generic, game-agnostic ELO result that a game view may expose at game end. */
public record GameEloChange(
        String playerId,
        boolean winner,
        int oldElo,
        int eloDelta,
        int newElo
) {
}
