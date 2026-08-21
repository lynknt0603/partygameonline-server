package com.partygameonline.session.application;

import com.partygameonline.security.PlayerAuthentication;
import com.partygameonline.session.domain.PlayerPrincipal;
import com.partygameonline.session.domain.SessionKind;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public PlayerPrincipal createOrRefreshGuest(
            String displayName,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String normalizedName = displayName.trim();
        PlayerPrincipal principal = existingGuest()
                .map(current -> current.withDisplayName(normalizedName))
                .orElseGet(() -> PlayerPrincipal.guest(UUID.randomUUID().toString(), normalizedName));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new PlayerAuthentication(principal));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return principal;
    }

    public void terminate(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.setHeader("Clear-Site-Data", "\"cookies\"");
    }

    private java.util.Optional<PlayerPrincipal> existingGuest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof PlayerPrincipal principal
                && principal.kind() == SessionKind.GUEST) {
            return java.util.Optional.of(principal);
        }
        return java.util.Optional.empty();
    }
}
