package com.partygameonline.history.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchResponse(
        UUID id,
        String gameId,
        String roomId,
        Instant startedAt,
        Instant finishedAt,
        String winnerPlayerId,
        String result,
        List<MatchPlayerResponse> players
) {
}
