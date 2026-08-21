package com.partygameonline.game.games.demo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DemoGameState(
        List<String> playerIds,
        Map<String, List<String>> hands,
        List<String> deck,
        List<String> discard,
        String currentPlayerId,
        int turnNumber,
        boolean hasDrawn,
        boolean hasPlayed,
        boolean finished,
        String winnerPlayerId
) {

    public DemoGameState {
        playerIds = List.copyOf(playerIds);
        Map<String, List<String>> copiedHands = new LinkedHashMap<>();
        hands.forEach((playerId, hand) -> copiedHands.put(playerId, List.copyOf(hand)));
        hands = Map.copyOf(copiedHands);
        deck = List.copyOf(deck);
        discard = List.copyOf(discard);
    }

    public List<String> handOf(String playerId) {
        return hands.getOrDefault(playerId, List.of());
    }

    public String opponentOf(String playerId) {
        for (String candidate : playerIds) {
            if (!candidate.equals(playerId)) {
                return candidate;
            }
        }
        return null;
    }

    public String nextPlayer(String playerId) {
        int index = playerIds.indexOf(playerId);
        if (index < 0) {
            return playerId;
        }
        return playerIds.get((index + 1) % playerIds.size());
    }

    DemoGameState withTurnFlags(boolean hasDrawn, boolean hasPlayed) {
        return new DemoGameState(
                playerIds,
                hands,
                deck,
                discard,
                currentPlayerId,
                turnNumber,
                hasDrawn,
                hasPlayed,
                finished,
                winnerPlayerId
        );
    }

    DemoGameState withHandsAndDeck(Map<String, List<String>> hands, List<String> deck, boolean hasDrawn) {
        return new DemoGameState(
                playerIds,
                hands,
                deck,
                discard,
                currentPlayerId,
                turnNumber,
                hasDrawn,
                hasPlayed,
                finished,
                winnerPlayerId
        );
    }

    DemoGameState withPlay(Map<String, List<String>> hands, List<String> discard, boolean finished, String winnerPlayerId) {
        return new DemoGameState(
                playerIds,
                hands,
                deck,
                discard,
                currentPlayerId,
                turnNumber,
                hasDrawn,
                true,
                finished,
                winnerPlayerId
        );
    }

    DemoGameState withNextTurn(String nextPlayerId, int turnNumber) {
        return new DemoGameState(
                playerIds,
                hands,
                deck,
                discard,
                nextPlayerId,
                turnNumber,
                false,
                false,
                finished,
                winnerPlayerId
        );
    }

    DemoGameState finishedAs(String winnerPlayerId) {
        return new DemoGameState(
                playerIds,
                hands,
                deck,
                discard,
                currentPlayerId,
                turnNumber,
                hasDrawn,
                hasPlayed,
                true,
                winnerPlayerId
        );
    }

    static Map<String, List<String>> replaceHand(
            Map<String, List<String>> hands,
            String playerId,
            List<String> hand
    ) {
        Map<String, List<String>> next = new LinkedHashMap<>(hands);
        next.put(playerId, new ArrayList<>(hand));
        return next;
    }
}
