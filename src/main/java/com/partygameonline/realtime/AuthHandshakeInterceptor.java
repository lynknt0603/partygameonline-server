package com.partygameonline.realtime;

import com.partygameonline.security.SecurityProperties;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private final SecurityProperties securityProperties;

    public AuthHandshakeInterceptor(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (!originAllowed(request)) {
            return false;
        }
        if (!(request.getPrincipal() instanceof Authentication authentication)) {
            return false;
        }
        if (!(authentication.getPrincipal() instanceof PlayerPrincipal player)) {
            return false;
        }
        attributes.put(WebSocketConnectionHub.PLAYER_ATTRIBUTE, player);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }

    private boolean originAllowed(ServerHttpRequest request) {
        String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (origin == null || origin.isBlank()) {
            return true;
        }
        List<String> allowed = securityProperties.cors().allowedOrigins();
        if (!allowed.isEmpty()) {
            return allowed.contains(origin);
        }
        try {
            URI originUri = URI.create(origin);
            URI requestUri = request.getURI();
            return requestUri.getScheme() != null
                    && requestUri.getScheme().equalsIgnoreCase(originUri.getScheme())
                    && requestUri.getHost() != null
                    && requestUri.getHost().equalsIgnoreCase(originUri.getHost())
                    && effectivePort(requestUri) == effectivePort(originUri);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) || "wss".equalsIgnoreCase(uri.getScheme())
                ? 443
                : 80;
    }
}
