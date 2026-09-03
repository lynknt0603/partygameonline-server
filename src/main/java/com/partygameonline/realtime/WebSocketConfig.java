package com.partygameonline.realtime;

import com.partygameonline.security.SecurityProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomWebSocketHandler roomWebSocketHandler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;
    private final SecurityProperties securityProperties;

    public WebSocketConfig(
            RoomWebSocketHandler roomWebSocketHandler,
            AuthHandshakeInterceptor authHandshakeInterceptor,
            SecurityProperties securityProperties
    ) {
        this.roomWebSocketHandler = roomWebSocketHandler;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
        this.securityProperties = securityProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(roomWebSocketHandler, "/ws")
                // Legacy HttpSessionHandshakeInterceptor intentionally disabled:
                // mobile browsers can block its cross-site session cookie.
                .addInterceptors(authHandshakeInterceptor);
        if (!securityProperties.cors().allowedOrigins().isEmpty()) {
            registration.setAllowedOrigins(securityProperties.cors().allowedOrigins().toArray(String[]::new));
        }
    }
}
