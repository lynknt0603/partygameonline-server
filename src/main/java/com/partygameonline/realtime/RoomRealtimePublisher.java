package com.partygameonline.realtime;

import com.partygameonline.room.domain.GameRoom;
import java.util.List;
import java.util.Map;

public interface RoomRealtimePublisher {

    void playerJoined(GameRoom room, String playerId);

    void playerLeft(GameRoom room, String playerId);

    void playerReadyChanged(GameRoom room, String playerId, boolean ready);

    void roomSettingsChanged(GameRoom room);

    void roomClosed(GameRoom room);

    void playerDisconnected(GameRoom room, String playerId);

    void playerReconnected(GameRoom room, String playerId);

    void gameStarted(GameRoom room, Map<String, Object> viewsByPlayer);

    void gameEvents(
            GameRoom room,
            String requestId,
            String actorPlayerId,
            List<Object> events,
            Map<String, Object> viewsByPlayer
    );

    void gameFinished(GameRoom room, String requestId, String winnerPlayerId, Map<String, Object> viewsByPlayer);

    void gameSnapshot(GameRoom room, String requestId, String playerId, Object view);

    void resyncRequired(GameRoom room, String requestId, String playerId);

    void actionRejected(String roomId, String requestId, String playerId, String errorCode, String message);
}
