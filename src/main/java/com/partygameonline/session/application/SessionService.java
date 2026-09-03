package com.partygameonline.session.application;

import com.partygameonline.common.avatar.AvatarCatalog;
import com.partygameonline.session.domain.PlayerPrincipal;
import com.partygameonline.session.domain.SessionKind;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    public PlayerPrincipal createOrRefreshGuest(
            String displayName,
            PlayerPrincipal current
    ) {
        if (current != null && current.kind() == SessionKind.MEMBER) {
            return current;
        }
        String normalizedName = displayName.trim();
        if (current != null && current.kind() == SessionKind.GUEST) {
            return current.withDisplayName(normalizedName);
        }
        return PlayerPrincipal.guest(UUID.randomUUID().toString(), normalizedName);
    }

    public PlayerPrincipal createMember(
            String playerId,
            String displayName,
            Instant createdAt,
            String avatarUrl
    ) {
        return PlayerPrincipal.member(playerId, displayName, createdAt, avatarUrl);
    }

    public PlayerPrincipal createMember(
            String playerId,
            String displayName,
            Instant createdAt
    ) {
        return createMember(
                playerId,
                displayName,
                createdAt,
                AvatarCatalog.DEFAULT_URL
        );
    }
}
