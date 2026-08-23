package com.partygameonline.session.domain;

import java.io.Serial;
import java.io.Serializable;

public record PlayerPrincipal(
        String playerId,
        String displayName,
        SessionKind kind
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static PlayerPrincipal guest(String playerId, String displayName) {
        return new PlayerPrincipal(playerId, displayName, SessionKind.GUEST);
    }

    public static PlayerPrincipal member(String playerId, String displayName) {
        return new PlayerPrincipal(playerId, displayName, SessionKind.MEMBER);
    }

    public PlayerPrincipal withDisplayName(String displayName) {
        return new PlayerPrincipal(playerId, displayName, kind);
    }
}
