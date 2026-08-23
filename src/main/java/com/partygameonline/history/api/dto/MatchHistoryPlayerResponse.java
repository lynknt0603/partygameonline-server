package com.partygameonline.history.api.dto;

public record MatchHistoryPlayerResponse(
        String playerId,
        String displayName,
        String result,
        Integer score,
        String role,
        String bloodline
) {
}
