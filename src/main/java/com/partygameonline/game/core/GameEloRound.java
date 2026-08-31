package com.partygameonline.game.core;

import java.util.List;

/** Generic round result exposed by games that settle ELO progressively. */
public record GameEloRound(int roundNumber, List<GameEloRoundPlayer> players) {

    public GameEloRound {
        players = players == null ? List.of() : List.copyOf(players);
    }
}
