package com.partygameonline.session.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

public record PlayerPrincipal(
        String playerId,
        String displayName,
        SessionKind kind,
        Instant createdAt
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static PlayerPrincipal guest(String playerId, String displayName) {
        return new PlayerPrincipal(playerId, displayName, SessionKind.GUEST, Instant.now());
    }

    public static PlayerPrincipal member(String playerId, String displayName, Instant createdAt) {
        return new PlayerPrincipal(playerId, displayName, SessionKind.MEMBER, createdAt);
    }

    public PlayerPrincipal withDisplayName(String displayName) {
        return new PlayerPrincipal(playerId, displayName, kind, createdAt);
    }
}
