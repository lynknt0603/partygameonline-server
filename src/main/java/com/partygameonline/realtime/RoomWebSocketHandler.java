package com.partygameonline.realtime;

import com.partygameonline.game.runtime.GameActionDispatcher;
import com.partygameonline.game.runtime.GameRuntimeService;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.room.application.RoomService;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomException;
import com.partygameonline.room.domain.RoomPlayer;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {

    private static final Logger log = LoggerFactory.getLogger(RoomWebSocketHandler.class);

    private final WebSocketConnectionHub hub;
    private final WebSocketRoomRealtimePublisher publisher;
    private final RoomService roomService;
    private final GameActionDispatcher gameActionDispatcher;
    private final GameRuntimeService gameRuntimeService;
    private final DisconnectGraceService disconnectGraceService;
    private final RequestIdDeduper requestIdDeduper;
    private final RoomChatService roomChatService;
    private final GameActionRateLimiter rateLimiter;
    private final JsonMapper jsonMapper;

    public RoomWebSocketHandler(
            WebSocketConnectionHub hub,
            WebSocketRoomRealtimePublisher publisher,
            RoomService roomService,
            GameActionDispatcher gameActionDispatcher,
            GameRuntimeService gameRuntimeService,
            DisconnectGraceService disconnectGraceService,
            RequestIdDeduper requestIdDeduper,
            RoomChatService roomChatService,
            GameActionRateLimiter rateLimiter,
            JsonMapper jsonMapper
    ) {
        this.hub = hub;
        this.publisher = publisher;
        this.roomService = roomService;
        this.gameActionDispatcher = gameActionDispatcher;
        this.gameRuntimeService = gameRuntimeService;
        this.disconnectGraceService = disconnectGraceService;
        this.requestIdDeduper = requestIdDeduper;
        this.roomChatService = roomChatService;
        this.rateLimiter = rateLimiter;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public List<String> getSubProtocols() {
        return List.of("boardverse");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        PlayerPrincipal player = principal(session);
        if (!hub.register(player.playerId(), session)) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (java.io.IOException ex) {
                log.debug("Failed to close excess websocket sessionId={}", session.getId(), ex);
            }
            return;
        }
        disconnectGraceService.cancel(player.playerId());
        WsServerEnvelope connected = WsServerEnvelope.of(
                WsMessageTypes.CONNECTED,
                null,
                null,
                null,
                Map.of("playerId", player.playerId(), "serverTime", Instant.now().toString())
        );
        hub.send(session, jsonMapper.writeValueAsString(connected));
        roomService.socketReconnected(player).ifPresent(room -> publishSnapshots(room, player, null, null));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (message.getPayloadLength() > 32_768) {
            sendError(session, null, null, "MESSAGE_TOO_LARGE", "Message exceeds the allowed size");
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (java.io.IOException ex) {
                log.debug("Failed to close oversized websocket sessionId={}", session.getId(), ex);
            }
            return;
        }
        PlayerPrincipal player = principal(session);
        WsClientEnvelope envelope;
        try {
            envelope = jsonMapper.readValue(message.getPayload(), WsClientEnvelope.class);
        } catch (RuntimeException ex) {
            sendError(session, null, null, "MALFORMED_REQUEST", "Message is not a valid websocket envelope");
            return;
        }
        if (envelope.requestId() == null || envelope.requestId().isBlank()) {
            sendError(session, envelope.roomId(), null, "MISSING_REQUEST_ID", "requestId is required");
            return;
        }
        if (envelope.type() == null || envelope.type().isBlank()) {
            sendError(session, envelope.roomId(), envelope.requestId(), "UNKNOWN_TYPE", "type is required");
            return;
        }
        switch (envelope.type()) {
            case WsMessageTypes.ROOM_SNAPSHOT -> handleRoomSnapshot(session, player, envelope);
            case WsMessageTypes.ROOM_CHAT -> handleRoomChat(session, player, envelope);
            case WsMessageTypes.GAME_ACTION -> handleGameAction(session, player, envelope);
            default -> sendError(
                    session,
                    envelope.roomId(),
                    envelope.requestId(),
                    "UNKNOWN_TYPE",
                    "Unsupported message type"
            );
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        releaseSocket(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Websocket transport error sessionId={}", session.getId(), exception);
        releaseSocket(session);
    }

    private void handleRoomSnapshot(WebSocketSession session, PlayerPrincipal player, WsClientEnvelope envelope) {
        if (envelope.roomId() == null || envelope.roomId().isBlank()) {
            sendError(session, null, envelope.requestId(), "ROOM_REQUIRED", "roomId is required");
            return;
        }
        GameRoom room;
        try {
            room = roomService.get(envelope.roomId());
        } catch (RoomException ex) {
            sendError(session, envelope.roomId(), envelope.requestId(), ex.getErrorCode(), ex.getClientMessage());
            return;
        }
        RoomPlayer member = room.findPlayer(player.playerId()).orElse(null);
        if (member == null) {
            sendError(session, envelope.roomId(), envelope.requestId(), "NOT_ROOM_MEMBER", "You are not a member of this room");
            return;
        }
        if (envelope.lastServerSequence() != null && envelope.lastServerSequence() < room.getServerSequence()) {
            publisher.resyncRequired(room, envelope.requestId(), player.playerId());
        }
        publishSnapshots(room, player, envelope.requestId(), member);
    }

    private void publishSnapshots(GameRoom room, PlayerPrincipal player, String requestId, RoomPlayer member) {
        RoomPlayer viewer = member != null ? member : room.findPlayer(player.playerId()).orElse(null);
        if (viewer == null) {
            return;
        }
        GameSession gameSession = gameRuntimeService.findSession(room.getId().value()).orElse(null);
        Object view = gameSession == null ? null : gameRuntimeService.projectView(gameSession, viewer);
        publisher.snapshot(room, requestId, player.playerId(), view, roomChatService.recent(room.getId().value()));
        if (gameSession != null) {
            publisher.gameSnapshot(room, requestId, player.playerId(), view);
        }
    }

    private void handleRoomChat(WebSocketSession session, PlayerPrincipal player, WsClientEnvelope envelope) {
        if (envelope.roomId() == null || envelope.roomId().isBlank()) {
            sendError(session, null, envelope.requestId(), "ROOM_REQUIRED", "roomId is required");
            return;
        }
        GameRoom room;
        try {
            room = roomService.get(envelope.roomId());
        } catch (RoomException ex) {
            sendError(session, envelope.roomId(), envelope.requestId(), ex.getErrorCode(), ex.getClientMessage());
            return;
        }
        if (room.findPlayer(player.playerId()).isEmpty()) {
            sendError(session, envelope.roomId(), envelope.requestId(), "NOT_ROOM_MEMBER", "You are not a member of this room");
            return;
        }
        if (!rateLimiter.tryAcquire(player.playerId(), "ROOM_CHAT")) {
            sendError(session, envelope.roomId(), envelope.requestId(), "RATE_LIMITED", "Too many messages; please slow down");
            return;
        }
        Object raw = envelope.payload() == null ? null : envelope.payload().get("text");
        String text = raw == null ? "" : String.valueOf(raw);
        RoomChatMessage message = roomChatService.append(
                room.getId().value(),
                player.playerId(),
                player.displayName(),
                text
        );
        if (message == null) {
            sendError(session, envelope.roomId(), envelope.requestId(), "INVALID_CHAT", "Message is empty");
            return;
        }
        publisher.roomChat(room, envelope.requestId(), message);
    }

    private void releaseSocket(WebSocketSession session) {
        PlayerPrincipal player = null;
        Object attribute = session.getAttributes().get(WebSocketConnectionHub.PLAYER_ATTRIBUTE);
        if (attribute instanceof PlayerPrincipal principal) {
            player = principal;
        }
        hub.unregister(session);
        if (player != null && !hub.hasOpenConnection(player.playerId())) {
            roomService.socketDisconnected(player);
            disconnectGraceService.schedule(player.playerId());
        }
    }

    private void handleGameAction(WebSocketSession session, PlayerPrincipal player, WsClientEnvelope envelope) {
        if (envelope.roomId() == null || envelope.roomId().isBlank()) {
            sendError(session, null, envelope.requestId(), "ROOM_REQUIRED", "roomId is required");
            return;
        }
        if (requestIdDeduper.isDuplicate(player.playerId(), envelope.requestId())) {
            send(session, WsServerEnvelope.of(
                    WsMessageTypes.ACTION_REJECTED,
                    envelope.roomId(),
                    null,
                    envelope.requestId(),
                    Map.of("errorCode", "DUPLICATE_REQUEST", "message", "This request was already processed")
            ));
            return;
        }
        gameActionDispatcher.dispatch(player, envelope.roomId(), envelope.requestId(), envelope.payload());
    }

    private void sendError(
            WebSocketSession session,
            String roomId,
            String requestId,
            String errorCode,
            String message
    ) {
        send(session, WsServerEnvelope.of(
                WsMessageTypes.ERROR,
                roomId,
                null,
                requestId,
                Map.of("errorCode", errorCode, "message", message)
        ));
    }

    private void send(WebSocketSession session, WsServerEnvelope envelope) {
        hub.send(session, jsonMapper.writeValueAsString(envelope));
    }

    private static PlayerPrincipal principal(WebSocketSession session) {
        Object attribute = session.getAttributes().get(WebSocketConnectionHub.PLAYER_ATTRIBUTE);
        if (attribute instanceof PlayerPrincipal player) {
            return player;
        }
        throw new IllegalStateException("Websocket session is missing the authenticated player");
    }
}
