package com.partygameonline.game.bloodbound.api;

import com.partygameonline.common.error.ApiException;
import com.partygameonline.game.core.GameActionFormatException;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.bloodbound.BloodBoundGameManifest;
import com.partygameonline.game.bloodbound.api.dto.BloodBoundCommandRequest;
import com.partygameonline.game.bloodbound.api.dto.BloodBoundView;
import com.partygameonline.game.runtime.AppliedAction;
import com.partygameonline.game.runtime.GameRuntimeService;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.history.application.MatchHistoryService;
import com.partygameonline.realtime.RoomRealtimePublisher;
import com.partygameonline.room.api.dto.RoomResponse;
import com.partygameonline.room.application.RoomService;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomException;
import com.partygameonline.room.domain.RoomId;
import com.partygameonline.room.domain.RoomPlayer;
import com.partygameonline.room.infrastructure.RoomLocks;
import com.partygameonline.room.infrastructure.RoomRepository;
import com.partygameonline.session.domain.PlayerPrincipal;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/games/blood-bound/rooms")
public class BloodBoundGameController {

    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final RoomLocks roomLocks;
    private final GameRuntimeService runtimeService;
    private final RoomRealtimePublisher realtimePublisher;
    private final MatchHistoryService matchHistoryService;

    public BloodBoundGameController(
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
    public RoomResponse start(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomId
    ) {
        requireGame(roomService.get(roomId));
        return RoomResponse.from(roomService.start(principal, roomId));
    }

    @GetMapping("/{roomId}/snapshot")
    public BloodBoundView snapshot(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomId
    ) {
        return roomLocks.withRoom(RoomId.parse(roomId).value(), () -> {
            GameRoom room = requireRoom(roomId, principal);
            GameSession session = requireSession(room);
            RoomPlayer player = room.findPlayer(principal.playerId()).orElseThrow(RoomException::notMember);
            return (BloodBoundView) runtimeService.projectView(session, player);
        });
    }

    @PostMapping("/{roomId}/command")
    public BloodBoundView command(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomId,
            @Valid @RequestBody BloodBoundCommandRequest request
    ) {
        return roomLocks.withRoom(RoomId.parse(roomId).value(), () -> {
            GameRoom room = requireRoom(roomId, principal);
            GameSession session = requireSession(room);
            RoomPlayer actor = room.findPlayer(principal.playerId()).orElseThrow(RoomException::notMember);
            String commandId = request.commandId() == null || request.commandId().isBlank()
                    ? UUID.randomUUID().toString()
                    : request.commandId();
            Map<String, Object> payload = toPayload(request);
            payload.put("commandId", commandId);
            AppliedAction applied;
            try {
                applied = runtimeService.applyAction(
                        session,
                        PlayerContext.player(actor.getPlayerId(), actor.getDisplayName()),
                        payload
                );
            } catch (GameActionFormatException ex) {
                throw new ApiException("MALFORMED_ACTION", HttpStatus.BAD_REQUEST, "The action could not be read");
            }
            if (!applied.accepted()) {
                throw new ApiException(
                        applied.rejection().errorCode(),
                        HttpStatus.CONFLICT,
                        applied.rejection().message()
                );
            }
            if (applied.result().finished()) {
                room.markFinished();
                matchHistoryService.recordIfFinished(room, session);
            }
            Map<String, Object> views = runtimeService.projectViews(room, session);
            List<Object> events = List.copyOf(applied.result().events());
            if (!events.isEmpty()) {
                realtimePublisher.gameEvents(room, commandId, principal.playerId(), events, views);
            }
            if (applied.result().finished()) {
                realtimePublisher.gameFinished(room, commandId, applied.result().winnerPlayerId(), views);
                roomService.recycleFinishedRoom(room);
            }
            return (BloodBoundView) views.get(principal.playerId());
        });
    }

    private GameRoom requireRoom(String roomId, PlayerPrincipal principal) {
        GameRoom room = roomRepository.findById(RoomId.parse(roomId)).orElseThrow(RoomException::notFound);
        requireGame(room);
        if (principal == null || room.findPlayer(principal.playerId()).isEmpty()) {
            throw RoomException.notMember();
        }
        return room;
    }

    private GameSession requireSession(GameRoom room) {
        GameSession session = runtimeService.findSession(room.getId().value()).orElse(null);
        if (session == null || session.isFinished()) {
            throw new ApiException("GAME_NOT_RUNNING", HttpStatus.CONFLICT, "The game is not running");
        }
        return session;
    }

    private static void requireGame(GameRoom room) {
        if (!BloodBoundGameManifest.ID.equals(room.getGameId())) {
            throw new ApiException("WRONG_GAME", HttpStatus.CONFLICT, "This room is not Huyết Thệ (Crimson Vow)");
        }
    }

    private static Map<String, Object> toPayload(BloodBoundCommandRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request.type() != null) {
            payload.put("type", request.type().name());
        }
        if (request.targetPlayerId() != null) {
            payload.put("targetPlayerId", request.targetPlayerId());
        }
        if (request.tokenType() != null) {
            payload.put("tokenType", request.tokenType().name());
        }
        if (request.roleRank() != null) {
            payload.put("roleRank", request.roleRank());
        }
        if (request.abilityTargetPlayerId() != null) {
            payload.put("abilityTargetPlayerId", request.abilityTargetPlayerId());
        }
        return payload;
    }
}
