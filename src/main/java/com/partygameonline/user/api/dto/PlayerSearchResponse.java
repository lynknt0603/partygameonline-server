package com.partygameonline.user.api.dto;

public record PlayerSearchResponse(
        String playerId,
        String username,
        String displayName
) {
}
