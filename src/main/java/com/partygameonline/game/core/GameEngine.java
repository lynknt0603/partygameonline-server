package com.partygameonline.game.core;

import java.util.List;
import java.util.Map;

public interface GameEngine<S, A, E> {

    String gameType();

    S createGame(GameConfig config, RandomSource random);

    A decodeAction(Map<String, Object> payload);

    ValidationResult validate(S state, PlayerContext actor, A action);

    GameResult<S, E> apply(S state, PlayerContext actor, A action, RandomSource random);

    default GameResult<S, E> onPlayerAbandoned(S state, PlayerContext player, RandomSource random) {
        return GameResult.of(state, List.of());
    }
}
