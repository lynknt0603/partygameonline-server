package com.partygameonline.realtime;

import com.partygameonline.security.SecurityProperties;
import com.partygameonline.security.TokenAuthenticator;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    private final SecurityProperties securityProperties;
    private final TokenAuthenticator tokenAuthenticator;

    public AuthHandshakeInterceptor(
            SecurityProperties securityProperties,
            TokenAuthenticator tokenAuthenticator
    ) {
        this.securityProperties = securityProperties;
        this.tokenAuthenticator = tokenAuthenticator;
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
        PlayerPrincipal player = bearerToken(request)
                .flatMap(tokenAuthenticator::authenticate)
                .orElse(null);
        if (player == null) {
            return false;
        }
        attributes.put(WebSocketConnectionHub.PLAYER_ATTRIBUTE, player);
        return true;
    }

    private java.util.Optional<String> bearerToken(ServerHttpRequest request) {
        return request.getHeaders().getOrEmpty("Sec-WebSocket-Protocol").stream()
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> value.startsWith("bearer."))
                .map(value -> value.substring("bearer.".length()))
                .filter(value -> !value.isBlank())
                .findFirst();
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
