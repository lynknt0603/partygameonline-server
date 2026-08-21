package com.partygameonline.game.games.demo;

import java.util.List;

public record DemoView(
        String you,
        String currentPlayerId,
        int turnNumber,
        boolean yourTurn,
        boolean hasDrawn,
        boolean hasPlayed,
        List<String> hand,
        int deckSize,
        int opponentHandSize,
        List<String> discard,
        boolean finished,
        String winnerPlayerId
) {
}
