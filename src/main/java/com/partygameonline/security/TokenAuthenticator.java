package com.partygameonline.security;

import com.partygameonline.common.avatar.AvatarCatalog;
import com.partygameonline.session.domain.PlayerPrincipal;
import com.partygameonline.session.domain.SessionKind;
import com.partygameonline.user.infrastructure.UserJpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TokenAuthenticator {

    private final AuthTokenService tokens;
    private final UserJpaRepository users;

    public TokenAuthenticator(AuthTokenService tokens, UserJpaRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Optional<PlayerPrincipal> authenticate(String token) {
        return tokens.verify(token).flatMap(principal -> {
            if (principal.kind() != SessionKind.MEMBER) {
                return Optional.of(principal);
            }
            return users.findByUserKey(principal.playerId()).map(user -> PlayerPrincipal.member(
                    user.getUserKey(),
                    user.getDisplayName(),
                    user.getCreatedAt(),
                    AvatarCatalog.urlForKey(user.getAvatarKey())
            ));
        });
    }
}
