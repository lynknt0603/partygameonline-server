package com.partygameonline.history.api.dto;

public record MatchPlayerResponse(
        String playerId,
        String displayName,
        Integer seat,
        boolean winner
) {
}
