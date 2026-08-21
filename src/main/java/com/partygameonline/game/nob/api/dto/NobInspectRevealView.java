package com.partygameonline.game.nob.api.dto;

import java.time.Instant;

public record NobInspectRevealView(
        String targetPlayerId,
        NobBloodlineView bloodline,
        String cardCode,
        Instant displayUntil
) {
}
