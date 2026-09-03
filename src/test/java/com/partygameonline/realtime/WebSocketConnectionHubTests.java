package com.partygameonline.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class WebSocketConnectionHubTests {

    @Test
    void capsConcurrentConnectionsPerPlayer() {
        WebSocketConnectionHub hub = new WebSocketConnectionHub();
        WebSocketSession first = mock(WebSocketSession.class);
        WebSocketSession second = mock(WebSocketSession.class);
        WebSocketSession third = mock(WebSocketSession.class);

        assertThat(hub.register("p1", first)).isTrue();
        assertThat(hub.register("p1", second)).isTrue();
        assertThat(hub.register("p1", third)).isFalse();

        hub.unregister(first);
        assertThat(hub.register("p1", third)).isTrue();
    }
}
