package com.partygameonline.game.core;

/** Generic per-player result needed by a round ELO policy. */
public record GameEloRoundPlayer(String playerId, boolean winner, Integer score) {
}
