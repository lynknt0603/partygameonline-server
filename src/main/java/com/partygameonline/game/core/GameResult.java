package com.partygameonline.game.core;

import java.util.List;

public record GameResult<S, E>(
        S state,
        List<E> events,
        boolean finished,
        String winnerPlayerId
) {

    public GameResult {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public static <S, E> GameResult<S, E> of(S state, List<E> events) {
        return new GameResult<>(state, events, false, null);
    }

    public static <S, E> GameResult<S, E> finished(S state, List<E> events, String winnerPlayerId) {
        return new GameResult<>(state, events, true, winnerPlayerId);
    }
}
