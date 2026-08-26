package com.partygameonline.game.notinmypot.api.dto;

import java.time.Instant;
import java.util.List;

public record NotInMyPotPendingActionView(
        String type,
        String actorPlayerId,
        int requiredCardCount,
        List<String> allowedTargetPlayerIds,
        List<String> allowedCardIds,
        Instant startedAt,
        Instant deadline
) {
}
