package com.partygameonline.realtime;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WebSocketConnectionHub {

    public static final String PLAYER_ATTRIBUTE = "playerPrincipal";

    private static final Logger log = LoggerFactory.getLogger(WebSocketConnectionHub.class);

    private final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> sessionsByPlayer =
            new ConcurrentHashMap<>();

    public void register(String playerId, WebSocketSession session) {
        sessionsByPlayer.computeIfAbsent(playerId, ignored -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unregister(WebSocketSession session) {
        sessionsByPlayer.values().forEach(sessions -> sessions.remove(session));
        sessionsByPlayer.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public boolean hasOpenConnection(String playerId) {
        CopyOnWriteArraySet<WebSocketSession> sessions = sessionsByPlayer.get(playerId);
        if (sessions == null) {
            return false;
        }
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                return true;
            }
        }
        return false;
    }

    public void sendToPlayers(Collection<String> playerIds, String json) {
        for (String playerId : playerIds) {
            Set<WebSocketSession> sessions = sessionsByPlayer.get(playerId);
            if (sessions == null) {
                continue;
            }
            for (WebSocketSession session : sessions) {
                send(session, json);
            }
        }
    }

    public void send(WebSocketSession session, String json) {
        if (session == null || !session.isOpen()) {
            return;
        }
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException ex) {
                log.debug("Failed to send websocket message sessionId={}", session.getId(), ex);
            }
        }
    }
}
