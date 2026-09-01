package com.partygameonline.session.api.dto;

import com.partygameonline.session.domain.PlayerPrincipal;

public record SessionResponse(
        String playerId,
        String displayName,
        String kind,
        String avatarUrl,
        String currentRoomId
) {

    public static SessionResponse from(PlayerPrincipal principal) {
        return from(principal, null);
    }

    public static SessionResponse from(PlayerPrincipal principal, String currentRoomId) {
        return new SessionResponse(
                principal.playerId(),
                principal.displayName(),
                principal.kind().name(),
                principal.avatarUrl(),
                currentRoomId
        );
    }
}
