package com.partygameonline.session.domain;

import com.partygameonline.common.avatar.AvatarCatalog;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

public record PlayerPrincipal(
        String playerId,
        String displayName,
        SessionKind kind,
        Instant createdAt,
        String avatarUrl
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public PlayerPrincipal(String playerId, String displayName, SessionKind kind, Instant createdAt) {
        this(playerId, displayName, kind, createdAt, AvatarCatalog.DEFAULT_URL);
    }

    public static PlayerPrincipal guest(String playerId, String displayName) {
        return new PlayerPrincipal(playerId, displayName, SessionKind.GUEST, Instant.now(), AvatarCatalog.DEFAULT_URL);
    }

    public static PlayerPrincipal member(String playerId, String displayName, Instant createdAt) {
        return member(playerId, displayName, createdAt, AvatarCatalog.DEFAULT_URL);
    }

    public static PlayerPrincipal member(String playerId, String displayName, Instant createdAt, String avatarUrl) {
        return new PlayerPrincipal(playerId, displayName, SessionKind.MEMBER, createdAt, avatarUrl);
    }

    public PlayerPrincipal withDisplayName(String displayName) {
        return new PlayerPrincipal(playerId, displayName, kind, createdAt, avatarUrl);
    }

    public PlayerPrincipal withAvatarUrl(String avatarUrl) {
        return new PlayerPrincipal(playerId, displayName, kind, createdAt, avatarUrl);
    }
}
