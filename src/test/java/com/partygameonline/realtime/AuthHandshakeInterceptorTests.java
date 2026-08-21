package com.partygameonline.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.partygameonline.security.PlayerAuthentication;
import com.partygameonline.security.SecurityProperties;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class AuthHandshakeInterceptorTests {

    @Test
    void rejectsMissingPrincipalAndDisallowedOrigin() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Cors(List.of("http://localhost:5173")),
                new SecurityProperties.Cookie(false, "lax")
        );
        AuthHandshakeInterceptor interceptor = new AuthHandshakeInterceptor(properties);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/ws"));
        when(request.getHeaders()).thenReturn(headers("https://evil.example"));
        when(request.getPrincipal()).thenReturn(null);

        assertThat(interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), new HashMap<>()))
                .isFalse();
    }

    @Test
    void acceptsSessionPrincipalFromAllowedOrigin() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Cors(List.of("http://localhost:5173")),
                new SecurityProperties.Cookie(false, "lax")
        );
        AuthHandshakeInterceptor interceptor = new AuthHandshakeInterceptor(properties);
        PlayerPrincipal player = PlayerPrincipal.guest("p1", "Linh");
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/ws"));
        when(request.getHeaders()).thenReturn(headers("http://localhost:5173"));
        when(request.getPrincipal()).thenReturn(new PlayerAuthentication(player));

        Map<String, Object> attributes = new HashMap<>();
        assertThat(interceptor.beforeHandshake(request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes))
                .isTrue();
        assertThat(attributes.get(WebSocketConnectionHub.PLAYER_ATTRIBUTE)).isEqualTo(player);
    }

    private static HttpHeaders headers(String origin) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ORIGIN, origin);
        return headers;
    }
}
