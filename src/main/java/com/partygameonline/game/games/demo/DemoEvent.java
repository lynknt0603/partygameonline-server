package com.partygameonline.game.games.demo;

public record DemoEvent(
        String type,
        String playerId,
        String cardId,
        String nextPlayerId,
        Integer turnNumber
) {

    public static DemoEvent drawn(String playerId) {
        return new DemoEvent("CARD_DRAWN", playerId, null, null, null);
    }

    public static DemoEvent played(String playerId, String cardId) {
        return new DemoEvent("CARD_PLAYED", playerId, cardId, null, null);
    }

    public static DemoEvent turnEnded(String playerId, String nextPlayerId, int turnNumber) {
        return new DemoEvent("TURN_ENDED", playerId, null, nextPlayerId, turnNumber);
    }

    public static DemoEvent won(String winnerPlayerId) {
        return new DemoEvent("GAME_WON", winnerPlayerId, null, null, null);
    }

    public static DemoEvent forfeited(String abandonedPlayerId, String winnerPlayerId) {
        return new DemoEvent("GAME_FORFEIT", abandonedPlayerId, null, winnerPlayerId, null);
    }
}
