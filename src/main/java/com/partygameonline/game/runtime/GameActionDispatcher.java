package com.partygameonline.game.runtime;

import com.partygameonline.game.core.GameActionFormatException;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.history.application.MatchHistoryService;
import com.partygameonline.realtime.RoomRealtimePublisher;
import com.partygameonline.room.application.RoomService;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomId;
import com.partygameonline.room.domain.RoomPlayer;
import com.partygameonline.room.infrastructure.RoomLocks;
import com.partygameonline.room.infrastructure.RoomRepository;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GameActionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GameActionDispatcher.class);

    private final RoomLocks roomLocks;
    private final RoomRepository roomRepository;
    private final GameRuntimeService runtimeService;
    private final RoomRealtimePublisher realtimePublisher;
    private final MatchHistoryService matchHistoryService;
    private final RoomService roomService;

    public GameActionDispatcher(
            RoomLocks roomLocks,
            RoomRepository roomRepository,
            GameRuntimeService runtimeService,
            RoomRealtimePublisher realtimePublisher,
            MatchHistoryService matchHistoryService,
            RoomService roomService
    ) {
        this.roomLocks = roomLocks;
        this.roomRepository = roomRepository;
        this.runtimeService = runtimeService;
        this.realtimePublisher = realtimePublisher;
        this.matchHistoryService = matchHistoryService;
        this.roomService = roomService;
    }

    public void dispatch(PlayerPrincipal player, String rawRoomId, String requestId, Map<String, Object> payload) {
        RoomId roomId;
        try {
            roomId = RoomId.parse(rawRoomId);
        } catch (RuntimeException ex) {
            realtimePublisher.actionRejected(
                    rawRoomId,
                    requestId,
                    player.playerId(),
                    "ROOM_NOT_FOUND",
                    "The room was not found"
            );
            return;
        }
        roomLocks.withRoom(roomId.value(), () -> {
            GameRoom room = roomRepository.findById(roomId).orElse(null);
            if (room == null) {
                realtimePublisher.actionRejected(
                        roomId.value(),
                        requestId,
                        player.playerId(),
                        "ROOM_NOT_FOUND",
                        "The room was not found"
                );
                return null;
            }
            RoomPlayer actor = room.findPlayer(player.playerId()).orElse(null);
            if (actor == null) {
                realtimePublisher.actionRejected(
                        roomId.value(),
                        requestId,
                        player.playerId(),
                        "NOT_ROOM_MEMBER",
                        "You are not a member of this room"
                );
                return null;
            }
            GameSession session = runtimeService.findSession(roomId.value()).orElse(null);
            if (session == null || session.isFinished()) {
                realtimePublisher.actionRejected(
                        roomId.value(),
                        requestId,
                        player.playerId(),
                        "GAME_NOT_RUNNING",
                        "The game is not running"
                );
                return null;
            }
            PlayerContext context = PlayerContext.player(actor.getPlayerId(), actor.getDisplayName());
            AppliedAction applied;
            try {
                applied = runtimeService.applyAction(session, context, payload);
            } catch (GameActionFormatException ex) {
                realtimePublisher.actionRejected(
                        roomId.value(),
                        requestId,
                        player.playerId(),
                        "MALFORMED_ACTION",
                        "The action could not be read"
                );
                return null;
            }
            if (!applied.accepted()) {
                realtimePublisher.actionRejected(
                        roomId.value(),
                        requestId,
                        player.playerId(),
                        applied.rejection().errorCode(),
                        applied.rejection().message()
                );
                return null;
            }
            if (applied.result().finished()) {
                room.markFinished();
                matchHistoryService.recordIfFinished(room, session);
            }
            Map<String, Object> views = runtimeService.projectViews(room, session);
            List<Object> events = List.copyOf(applied.result().events());
            log.info(
                    "Game action accepted roomId={} gameId={} playerId={} requestId={} finished={}",
                    roomId.value(),
                    session.getGameId(),
                    player.playerId(),
                    requestId,
                    applied.result().finished()
            );
            realtimePublisher.gameEvents(room, requestId, player.playerId(), events, views);
            if (applied.result().finished()) {
                realtimePublisher.gameFinished(room, requestId, applied.result().winnerPlayerId(), views);
                roomService.recycleFinishedRoom(room);
            }
            return null;
        });
    }
}
