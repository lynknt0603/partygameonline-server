package com.partygameonline.security;

import com.partygameonline.session.domain.PlayerPrincipal;
import java.io.Serial;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class PlayerAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final PlayerPrincipal principal;

    public PlayerAuthentication(PlayerPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_PLAYER")));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public PlayerPrincipal getPrincipal() {
        return principal;
    }
}
