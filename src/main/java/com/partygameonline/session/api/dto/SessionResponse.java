package com.partygameonline.session.api.dto;

import com.partygameonline.session.domain.PlayerPrincipal;

public record SessionResponse(
        String playerId,
        String displayName,
        String kind,
        String avatarUrl,
        String currentRoomId,
        String accessToken
) {

    public static SessionResponse from(PlayerPrincipal principal, String currentRoomId, String accessToken) {
        return new SessionResponse(
                principal.playerId(),
                principal.displayName(),
                principal.kind().name(),
                principal.avatarUrl(),
                currentRoomId,
                accessToken
        );
    }
}
