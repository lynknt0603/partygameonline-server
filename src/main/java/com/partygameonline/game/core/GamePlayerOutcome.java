package com.partygameonline.game.core;

/** Durable per-player result data exposed to the generic match history. */
public record GamePlayerOutcome(Integer score, String role, String bloodline) {
}
