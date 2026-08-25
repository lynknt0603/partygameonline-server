package com.partygameonline.history.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchHistoryItemResponse(
        UUID matchId,
        String gameId,
        String gameDisplayName,
        String roomId,
        Instant finishedAt,
        long durationSeconds,
        String result,
        Integer score,
        String role,
        String bloodline,
        List<MatchHistoryPlayerResponse> players
) {
}
