package com.partygameonline.auth.api.dto;

import com.partygameonline.session.domain.PlayerPrincipal;

public record AuthResponse(String playerId, String displayName, String kind) {
    public static AuthResponse from(PlayerPrincipal principal) {
        return new AuthResponse(principal.playerId(), principal.displayName(), principal.kind().name());
    }
}
