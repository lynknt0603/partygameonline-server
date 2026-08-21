package com.partygameonline.game.nob.api.dto;

import java.time.Instant;
import java.util.List;

public record NobPendingDecisionView(
        String decisionId,
        String type,
        String actorPlayerId,
        String targetPlayerId,
        String sourceCardCode,
        List<String> allowedOptions,
        List<String> allowedTargetIds,
        String sourceCardInstanceId,
        Instant startedAt,
        Instant expiresAt
) {
}
