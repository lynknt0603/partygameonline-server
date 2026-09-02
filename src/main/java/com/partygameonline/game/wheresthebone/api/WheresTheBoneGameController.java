package com.partygameonline.game.wheresthebone.api;

import com.partygameonline.common.error.ApiException;
import com.partygameonline.game.core.GameActionFormatException;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.runtime.AppliedAction;
import com.partygameonline.game.runtime.GameRuntimeService;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.game.wheresthebone.WheresTheBoneGameManifest;
import com.partygameonline.game.wheresthebone.api.dto.WheresTheBoneView;
import com.partygameonline.room.application.RoomService;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomException;
import com.partygameonline.room.domain.RoomId;
import com.partygameonline.room.api.dto.RoomResponse;
import com.partygameonline.room.infrastructure.RoomLocks;
import com.partygameonline.room.infrastructure.RoomRepository;
import com.partygameonline.realtime.RoomRealtimePublisher;
import com.partygameonline.history.application.MatchHistoryService;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/games/wheres-the-bone/rooms")
public class WheresTheBoneGameController {

    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final RoomLocks roomLocks;
    private final GameRuntimeService runtimeService;
    private final RoomRealtimePublisher realtimePublisher;
    private final MatchHistoryService matchHistoryService;

    public WheresTheBoneGameController(
            RoomService roomService,
            RoomRepository roomRepository,
            RoomLocks roomLocks,
            GameRuntimeService runtimeService,
            RoomRealtimePublisher realtimePublisher,
            MatchHistoryService matchHistoryService
    ) {
        this.roomService = roomService;
        this.roomRepository = roomRepository;
        this.roomLocks = roomLocks;
        this.runtimeService = runtimeService;
        this.realtimePublisher = realtimePublisher;
        this.matchHistoryService = matchHistoryService;
    }

    @PostMapping("/{roomId}/start")
    public RoomResponse start(@AuthenticationPrincipal PlayerPrincipal principal, @PathVariable String roomId) {
        GameRoom room = roomService.get(roomId);
        requireGame(room);
        return RoomResponse.from(roomService.start(principal, roomId));
    }

    @GetMapping("/{roomId}/snapshot")
    public WheresTheBoneView snapshot(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomId
    ) {
        return roomLocks.withRoom(RoomId.parse(roomId).value(), () -> {
            GameRoom room = roomRepository.findById(RoomId.parse(roomId)).orElseThrow(RoomException::notFound);
            requireGame(room);
            if (principal == null || room.findPlayer(principal.playerId()).isEmpty()) {
                throw RoomException.notMember();
            }
            GameSession session = runtimeService.findSession(room.getId().value()).orElse(null);
            if (session == null || session.isFinished()) {
                throw new ApiException("GAME_NOT_RUNNING", HttpStatus.CONFLICT, "The game is not running");
            }
            return (WheresTheBoneView) runtimeService.projectView(
                    session,
                    room.findPlayer(principal.playerId()).orElseThrow(RoomException::notMember)
            );
        });
    }

    @PostMapping("/{roomId}/command")
    public WheresTheBoneView command(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomId,
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> request
    ) {
        return roomLocks.withRoom(RoomId.parse(roomId).value(), () -> {
            GameRoom room = roomRepository.findById(RoomId.parse(roomId)).orElseThrow(RoomException::notFound);
            requireGame(room);
            if (principal == null) throw RoomException.notMember();
            var actor = room.findPlayer(principal.playerId()).orElseThrow(RoomException::notMember);
            GameSession session = runtimeService.findSession(room.getId().value()).orElse(null);
            if (session == null || session.isFinished()) {
                throw new ApiException("GAME_NOT_RUNNING", HttpStatus.CONFLICT, "The game is not running");
            }
            Map<String, Object> payload = request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
            String commandId = payload.get("commandId") instanceof String value && !value.isBlank()
                    ? value : UUID.randomUUID().toString();
            payload.put("commandId", commandId);
            AppliedAction applied;
            try {
                applied = runtimeService.applyAction(session, PlayerContext.player(actor.getPlayerId(), actor.getDisplayName()), payload);
            } catch (GameActionFormatException ex) {
                throw new ApiException("MALFORMED_ACTION", HttpStatus.BAD_REQUEST, "The action could not be read");
            }
            if (!applied.accepted()) {
                throw new ApiException(applied.rejection().errorCode(), HttpStatus.CONFLICT, applied.rejection().message());
            }
            if (applied.result().finished()) {
                room.markFinished();
                matchHistoryService.recordIfFinished(room, session);
            }
            Map<String, Object> views = runtimeService.projectViews(room, session);
            if (!applied.result().events().isEmpty()) {
                realtimePublisher.gameEvents(room, commandId, principal.playerId(), List.copyOf(applied.result().events()), views);
            }
            if (applied.result().finished()) {
                realtimePublisher.gameFinished(room, commandId, applied.result().winnerPlayerId(), views);
                roomService.recycleFinishedRoom(room);
            }
            return (WheresTheBoneView) views.get(principal.playerId());
        });
    }

    private static void requireGame(GameRoom room) {
        if (!WheresTheBoneGameManifest.ID.equals(room.getGameId())) {
            throw new ApiException("WRONG_GAME", HttpStatus.CONFLICT, "This room is not Where's the Bone");
        }
    }
}
