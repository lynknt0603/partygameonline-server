package com.partygameonline.game.nob.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record NobPendingDecision(
        String decisionId,
        String actorId,
        NobDecisionType type,
        List<String> allowedOptions,
        List<String> allowedTargetIds,
        String sourceCardInstanceId,
        Instant startedAt,
        Instant expiresAt,
        Map<String, Object> context
) {
}
