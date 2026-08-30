package com.partygameonline.realtime;

import com.partygameonline.game.nob.domain.NobEvent;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneEvent;
import com.partygameonline.room.api.dto.RoomResponse;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomPlayer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class WebSocketRoomRealtimePublisher implements RoomRealtimePublisher {

    private final WebSocketConnectionHub hub;
    private final JsonMapper jsonMapper;

    public WebSocketRoomRealtimePublisher(WebSocketConnectionHub hub, JsonMapper jsonMapper) {
        this.hub = hub;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void playerJoined(GameRoom room, String playerId) {
        emitStateChange(room, WsMessageTypes.PLAYER_JOINED, null, Map.of("playerId", playerId), recipients(room));
    }

    @Override
    public void playerLeft(GameRoom room, String playerId) {
        Set<String> recipients = recipients(room);
        recipients.add(playerId);
        emitStateChange(room, WsMessageTypes.PLAYER_LEFT, null, Map.of("playerId", playerId), recipients);
    }

    @Override
    public void playerReadyChanged(GameRoom room, String playerId, boolean ready) {
        emitStateChange(
                room,
                WsMessageTypes.PLAYER_READY_CHANGED,
                null,
                Map.of("playerId", playerId, "ready", ready),
                recipients(room)
        );
    }

    @Override
    public void roomSettingsChanged(GameRoom room) {
        emitStateChange(room, WsMessageTypes.ROOM_SETTINGS_CHANGED, null, Map.of(), recipients(room));
    }

    @Override
    public void roomClosed(GameRoom room) {
        emitStateChange(room, WsMessageTypes.ROOM_CLOSED, null, Map.of(), recipients(room));
    }

    @Override
    public void playerDisconnected(GameRoom room, String playerId) {
        emitStateChange(
                room,
                WsMessageTypes.PLAYER_DISCONNECTED,
                null,
                Map.of("playerId", playerId),
                recipients(room)
        );
    }

    @Override
    public void playerReconnected(GameRoom room, String playerId) {
        emitStateChange(
                room,
                WsMessageTypes.PLAYER_RECONNECTED,
                null,
                Map.of("playerId", playerId),
                recipients(room)
        );
    }

    @Override
    public void gameStarted(GameRoom room, Map<String, Object> viewsByPlayer) {
        long sequence = room.nextSequence();
        emitPerPlayer(room, WsMessageTypes.GAME_STARTED, sequence, null, Map.of(), viewsByPlayer, recipients(room));
    }

    @Override
    public void gameEvents(
            GameRoom room,
            String requestId,
            String actorPlayerId,
            List<Object> events,
            Map<String, Object> viewsByPlayer
    ) {
        long sequence = room.nextSequence();
        emitPerPlayer(
                room,
                WsMessageTypes.GAME_EVENTS,
                sequence,
                requestId,
                gameEventsPayload(events),
                viewsByPlayer,
                recipients(room)
        );
    }

    /**
     * Game events are broadcast to every player, so fields that identify a
     * secret actor or reveal a private value must never be sent in this
     * shared envelope. Player-specific secrets remain available through the
     * projected view sent to that player.
     */
    static Map<String, Object> gameEventsPayload(List<Object> events) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actorPlayerId", null);
        boolean wheresTheBone = events != null && events.stream().anyMatch(WheresTheBoneEvent.class::isInstance);
        payload.put("events", wheresTheBone
                ? List.of(new WheresTheBoneEvent("STATE_CHANGED", Map.of()))
                : events == null
                        ? List.of()
                        : events.stream().map(WebSocketRoomRealtimePublisher::sanitizeEvent).toList());
        return payload;
    }

    static Object sanitizeEvent(Object event) {
        if (event instanceof WheresTheBoneEvent boneEvent) {
            // Defensive fallback for callers that sanitize a single event. The
            // shared envelope collapses all private game events to STATE_CHANGED.
            return new WheresTheBoneEvent("STATE_CHANGED", Map.of());
        }
        if (!(event instanceof NobEvent nobEvent)) {
            return event;
        }
        Map<String, Object> payload = new LinkedHashMap<>(
                nobEvent.payload() == null ? Map.of() : nobEvent.payload()
        );
        switch (nobEvent.type()) {
            case "NOB_MOON_MARK_COUNT_CHANGED" -> payload.remove("value");
            case "NOB_PRIVATE_BLOODLINE_SEEN",
                    "NOB_PRIVATE_CARD_SEEN",
                    "NOB_PRIVATE_MOON_SEEN" -> payload.remove("viewerId");
            case "NOB_DECISION_REQUIRED" -> payload.remove("actorId");
            case "NOB_PLAYER_AUTO_ACTION" -> payload.remove("playerId");
            default -> {
                // Public events keep their public payload unchanged.
            }
        }
        return new NobEvent(nobEvent.type(), Collections.unmodifiableMap(payload));
    }

    @Override
    public void gameFinished(
            GameRoom room,
            String requestId,
            String winnerPlayerId,
            Map<String, Object> viewsByPlayer
    ) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("winnerPlayerId", winnerPlayerId);
        extra.put("finished", true);
        long sequence = room.nextSequence();
        emitPerPlayer(room, WsMessageTypes.GAME_FINISHED, sequence, requestId, extra, viewsByPlayer, recipients(room));
    }

    @Override
    public void resyncRequired(GameRoom room, String requestId, String playerId) {
        emit(room, WsMessageTypes.RESYNC_REQUIRED, room.getServerSequence(), requestId, Map.of(), Set.of(playerId));
    }

    @Override
    public void gameSnapshot(GameRoom room, String requestId, String playerId, Object view) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (view != null) {
            extra.put("view", view);
        }
        emit(room, WsMessageTypes.GAME_SNAPSHOT, room.getServerSequence(), requestId, extra, Set.of(playerId));
    }

    @Override
    public void actionRejected(String roomId, String requestId, String playerId, String errorCode, String message) {
        WsServerEnvelope envelope = WsServerEnvelope.of(
                WsMessageTypes.ACTION_REJECTED,
                roomId,
                null,
                requestId,
                Map.of("errorCode", errorCode, "message", message)
        );
        hub.sendToPlayers(Set.of(playerId), jsonMapper.writeValueAsString(envelope));
    }

    public void snapshot(GameRoom room, String requestId, Set<String> recipients) {
        emit(room, WsMessageTypes.ROOM_SNAPSHOT, room.getServerSequence(), requestId, Map.of(), recipients);
    }

    public void snapshot(GameRoom room, String requestId, String playerId, Object view) {
        snapshot(room, requestId, playerId, view, List.of());
    }

    public void snapshot(
            GameRoom room,
            String requestId,
            String playerId,
            Object view,
            List<RoomChatMessage> chat
    ) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (view != null) {
            extra.put("view", view);
        }
        if (chat != null && !chat.isEmpty()) {
            extra.put("chat", chat);
        }
        emit(room, WsMessageTypes.ROOM_SNAPSHOT, room.getServerSequence(), requestId, extra, Set.of(playerId));
    }

    @Override
    public void roomChat(GameRoom room, String requestId, RoomChatMessage message) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("message", message);
        emit(room, WsMessageTypes.ROOM_CHAT, room.getServerSequence(), requestId, extra, recipients(room));
    }

    private void emitStateChange(
            GameRoom room,
            String type,
            String requestId,
            Map<String, Object> extra,
            Set<String> recipients
    ) {
        emit(room, type, room.nextSequence(), requestId, extra, recipients);
    }

    private void emit(
            GameRoom room,
            String type,
            long sequence,
            String requestId,
            Map<String, Object> extra,
            Set<String> recipients
    ) {
        Map<String, Object> payload = new LinkedHashMap<>(extra);
        payload.put("room", RoomResponse.from(room));
        WsServerEnvelope envelope = WsServerEnvelope.of(type, room.getId().value(), sequence, requestId, payload);
        hub.sendToPlayers(recipients, jsonMapper.writeValueAsString(envelope));
    }

    private void emitPerPlayer(
            GameRoom room,
            String type,
            long sequence,
            String requestId,
            Map<String, Object> extra,
            Map<String, Object> viewsByPlayer,
            Set<String> recipients
    ) {
        if (viewsByPlayer == null || viewsByPlayer.isEmpty()) {
            emit(room, type, sequence, requestId, extra, recipients);
            return;
        }
        for (String playerId : recipients) {
            Map<String, Object> payload = new LinkedHashMap<>(extra);
            Object view = viewsByPlayer.get(playerId);
            if (view != null) {
                payload.put("view", view);
            }
            emit(room, type, sequence, requestId, payload, Set.of(playerId));
        }
    }

    private static Set<String> recipients(GameRoom room) {
        Set<String> playerIds = new LinkedHashSet<>();
        for (RoomPlayer player : room.getPlayers()) {
            playerIds.add(player.getPlayerId());
        }
        return playerIds;
    }
}
