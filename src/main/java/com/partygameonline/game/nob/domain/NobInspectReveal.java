package com.partygameonline.game.nob.domain;

import java.time.Instant;

public record NobInspectReveal(
        String targetPlayerId,
        NobBloodline bloodline,
        String cardCode,
        Instant displayUntil
) {
}
