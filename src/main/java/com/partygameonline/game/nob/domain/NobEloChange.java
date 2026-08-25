package com.partygameonline.game.nob.domain;

public record NobEloChange(
        int oldElo,
        int eloDelta,
        int newElo
) {
}
