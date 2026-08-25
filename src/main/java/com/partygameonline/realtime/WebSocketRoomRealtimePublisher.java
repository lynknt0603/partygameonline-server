package com.partygameonline.realtime;

import com.partygameonline.room.api.dto.RoomResponse;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomPlayer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
                Map.of("actorPlayerId", actorPlayerId, "events", events),
                viewsByPlayer,
                recipients(room)
        );
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
