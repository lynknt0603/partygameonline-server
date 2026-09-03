package com.partygameonline.game.nob.api;

import com.partygameonline.common.error.ApiException;
import com.partygameonline.game.core.GameActionFormatException;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.nob.NobGameManifest;
import com.partygameonline.game.nob.api.dto.NobCommandRequest;
import com.partygameonline.game.nob.api.dto.NobView;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.runtime.AppliedAction;
import com.partygameonline.game.runtime.GameRuntimeService;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.history.application.MatchHistoryService;
import com.partygameonline.realtime.RoomRealtimePublisher;
import com.partygameonline.room.application.RoomService;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomException;
import com.partygameonline.room.domain.RoomId;
import com.partygameonline.room.domain.RoomPlayer;
import com.partygameonline.room.infrastructure.RoomLocks;
import com.partygameonline.room.infrastructure.RoomRepository;
import com.partygameonline.session.domain.PlayerPrincipal;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/games/nob/rooms")
public class NobGameController {

    private static final CacheControl SNAPSHOT_CACHE_CONTROL = CacheControl.noCache().cachePrivate();

    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final RoomLocks roomLocks;
    private final GameRuntimeService runtimeService;
    private final RoomRealtimePublisher realtimePublisher;
    private final MatchHistoryService matchHistoryService;

    public NobGameController(
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
    public Object start(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomId
    ) {
        GameRoom room = roomService.get(roomId);
        requireNob(room);
        return roomService.start(principal, roomId);
    }

    @GetMapping("/{roomId}/snapshot")
    public ResponseEntity<NobView> snapshot(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        return roomLocks.withRoom(RoomId.parse(roomId).value(), () -> {
            GameRoom room = requireNobRoom(roomId, principal);
            GameSession session = requireSession(room);
            RoomPlayer viewer = room.findPlayer(principal.playerId()).orElseThrow(RoomException::notMember);
            NobGameState state = (NobGameState) session.getState();
            String etag = snapshotEtag(state.getVersion(), principal.playerId());
            if (matchesEtag(ifNoneMatch, etag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .cacheControl(SNAPSHOT_CACHE_CONTROL)
                        .eTag(etag)
                        .build();
            }
            NobView view = (NobView) runtimeService.projectView(session, viewer);
            return ResponseEntity.ok()
                    .cacheControl(SNAPSHOT_CACHE_CONTROL)
                    .eTag(etag)
                    .body(view);
        });
    }

    static String snapshotEtag(int version, String playerId) {
        UUID viewerKey = UUID.nameUUIDFromBytes(playerId.getBytes(StandardCharsets.UTF_8));
        return "\"nob-" + version + "-" + viewerKey + "\"";
    }

    static boolean matchesEtag(String ifNoneMatch, String currentEtag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        return Arrays.stream(ifNoneMatch.split(","))
                .map(String::trim)
                .anyMatch(candidate -> candidate.equals("*")
                        || candidate.equals(currentEtag)
                        || candidate.equals("W/" + currentEtag));
    }

    @PostMapping("/{roomId}/command")
    public NobView command(
            @AuthenticationPrincipal PlayerPrincipal principal,
            @PathVariable String roomId,
            @Valid @RequestBody NobCommandRequest request
    ) {
        return roomLocks.withRoom(RoomId.parse(roomId).value(), () -> {
            GameRoom room = requireNobRoom(roomId, principal);
            GameSession session = requireSession(room);
            RoomPlayer actor = room.findPlayer(principal.playerId()).orElseThrow(RoomException::notMember);
            Map<String, Object> payload = toPayload(request);
            AppliedAction applied;
            try {
                applied = runtimeService.applyAction(session, PlayerContext.player(actor.getPlayerId(), actor.getDisplayName()), payload);
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
            String requestId = request.commandId() == null ? UUID.randomUUID().toString() : request.commandId();
            if (!events.isEmpty()) {
                realtimePublisher.gameEvents(room, requestId, principal.playerId(), events, views);
            }
            if (applied.result().finished()) {
                realtimePublisher.gameFinished(room, requestId, applied.result().winnerPlayerId(), views);
                roomService.recycleFinishedRoom(room);
            }
            return (NobView) views.get(principal.playerId());
        });
    }

    private GameRoom requireNobRoom(String roomId, PlayerPrincipal principal) {
        GameRoom room = roomRepository.findById(RoomId.parse(roomId)).orElseThrow(RoomException::notFound);
        requireNob(room);
        if (room.findPlayer(principal.playerId()).isEmpty()) {
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

    private static void requireNob(GameRoom room) {
        if (!NobGameManifest.ID.equals(room.getGameId())) {
            throw new ApiException("WRONG_GAME", HttpStatus.CONFLICT, "This room is not Night of Bloodlines");
        }
    }

    private static Map<String, Object> toPayload(NobCommandRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", request.type());
        if (request.commandId() != null) {
            payload.put("commandId", request.commandId());
        }
        if (request.expectedVersion() != null) {
            payload.put("expectedVersion", request.expectedVersion());
        }
        if (request.cardInstanceId() != null) {
            payload.put("cardInstanceId", request.cardInstanceId());
        }
        if (request.cardCode() != null) {
            payload.put("cardCode", request.cardCode());
        }
        if (request.option() != null) {
            payload.put("option", request.option());
        }
        if (request.targetPlayerId() != null) {
            payload.put("targetPlayerId", request.targetPlayerId());
        }
        List<String> targets = request.targetPlayerIds() == null ? List.of() : new ArrayList<>(request.targetPlayerIds());
        if (!targets.isEmpty()) {
            payload.put("targetPlayerIds", targets);
        }
        return payload;
    }
}
