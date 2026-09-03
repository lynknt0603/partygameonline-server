package com.partygameonline.auth.api.dto;

import com.partygameonline.session.domain.PlayerPrincipal;

public record AuthResponse(String playerId, String displayName, String kind, String avatarUrl, String accessToken) {
    public static AuthResponse from(PlayerPrincipal principal, String accessToken) {
        return new AuthResponse(
                principal.playerId(),
                principal.displayName(),
                principal.kind().name(),
                principal.avatarUrl(),
                accessToken
        );
    }
}
