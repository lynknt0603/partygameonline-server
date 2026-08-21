package com.partygameonline.game.core;

public interface GameStateProjector<S, V> {

    String gameType();

    V project(S authoritativeState, PlayerContext viewer);
}
