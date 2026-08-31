package com.partygameonline.room.application;

import com.partygameonline.common.UniqueDisplayNames;
import com.partygameonline.common.error.ResourceNotFoundException;
import com.partygameonline.game.core.GameManifest;
import com.partygameonline.game.core.GameRegistry;
import com.partygameonline.room.domain.GameRoom;
import com.partygameonline.room.domain.RoomException;
import com.partygameonline.room.domain.RoomId;
import com.partygameonline.room.domain.RoomName;
import com.partygameonline.room.domain.RoomStatus;
import com.partygameonline.room.domain.RoomVisibility;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.runtime.AppliedAction;
import com.partygameonline.game.runtime.GameRuntimeService;
import com.partygameonline.game.runtime.GameSession;
import com.partygameonline.game.notinmypot.NotInMyPotGameManifest;
import com.partygameonline.game.wheresthebone.WheresTheBoneGameManifest;
import com.partygameonline.history.application.MatchHistoryService;
import com.partygameonline.room.domain.PlayerLobbyState;
import com.partygameonline.realtime.RoomRealtimePublisher;
import com.partygameonline.room.infrastructure.RoomLocks;
import com.partygameonline.room.infrastructure.RoomRepository;
import com.partygameonline.session.domain.PlayerPrincipal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RoomService {

    private static final int MAX_ID_ATTEMPTS = 20;

    private final RoomRepository roomRepository;
    private final GameRegistry gameRegistry;
    private final RoomRealtimePublisher realtimePublisher;
    private final GameRuntimeService gameRuntimeService;
    private final MatchHistoryService matchHistoryService;
    private final RoomLocks roomLocks;

    public RoomService(
            RoomRepository roomRepository,
            GameRegistry gameRegistry,
            RoomRealtimePublisher realtimePublisher,
            GameRuntimeService gameRuntimeService,
            MatchHistoryService matchHistoryService,
            RoomLocks roomLocks
    ) {
        this.roomRepository = roomRepository;
        this.gameRegistry = gameRegistry;
        this.realtimePublisher = realtimePublisher;
        this.gameRuntimeService = gameRuntimeService;
        this.matchHistoryService = matchHistoryService;
        this.roomLocks = roomLocks;
    }

    public GameRoom create(PlayerPrincipal principal, String gameId, String name, Integer requestedMaxPlayers, RoomVisibility visibility) {
        GameManifest game = requireEnabledGame(gameId);
        int maxPlayers = resolveMaxPlayers(game, requestedMaxPlayers);
        RoomVisibility roomVisibility = visibility == null ? RoomVisibility.PUBLIC : visibility;

        return roomLocks.withPlayer(principal.playerId(), () -> {
            ensureNotInAnotherRoom(principal.playerId(), null);
            RoomId roomId = allocateRoomId();
            return roomLocks.withRoom(roomId.value(), () -> {
                GameRoom room = new GameRoom(
                        roomId,
                        new RoomName(name),
                        game.id(),
                        principal.playerId(),
                        principal.displayName(),
                        principal.avatarUrl(),
                        maxPlayers,
                        roomVisibility,
                        Instant.now()
                );
                Map<String, Object> initialSettings = new LinkedHashMap<>(game.defaultRoomSettings());
                initialSettings.put("locked", false);
                room.replaceSettings(initialSettings);
                roomRepository.save(room);
                roomRepository.indexPlayer(principal.playerId(), roomId);
                return room;
            });
        });
    }

    public List<GameRoom> listPublicWaiting() {
        return roomRepository.findPublicWaiting();
    }

    public GameRoom get(String rawRoomId) {
        return roomRepository.findById(RoomId.parse(rawRoomId)).orElseThrow(RoomException::notFound);
    }

    public GameRoom join(PlayerPrincipal principal, String rawRoomId) {
        RoomId roomId = RoomId.parse(rawRoomId);
        return roomLocks.withPlayerThenRoom(principal.playerId(), roomId.value(), () -> {
            GameRoom room = roomRepository.findById(roomId).orElseThrow(RoomException::notFound);
            ensureNotInAnotherRoom(principal.playerId(), roomId);
            room.join(principal.playerId(), principal.displayName(), principal.avatarUrl());
            roomRepository.indexPlayer(principal.playerId(), roomId);
            realtimePublisher.playerJoined(room, principal.playerId());
            return room;
        });
    }

    public void leave(PlayerPrincipal principal, String rawRoomId) {
        RoomId roomId = RoomId.parse(rawRoomId);
        roomLocks.withPlayerThenRoom(principal.playerId(), roomId.value(), () -> {
            GameRoom room = roomRepository.findById(roomId).orElseThrow(RoomException::notFound);
            RoomStatus status = room.getStatus();
            boolean running = status == RoomStatus.IN_GAME || status == RoomStatus.STARTING;
            GameSession session = running
                    ? gameRuntimeService.findSession(roomId.value()).orElse(null)
                    : null;
            if (session != null && !session.isFinished()) {
                AppliedAction applied = gameRuntimeService.abandon(
                        session,
                        PlayerContext.player(principal.playerId(), principal.displayName())
                );
                if (applied.accepted()) {
                    boolean notInMyPot = NotInMyPotGameManifest.ID.equals(session.getGameId());
                    if (notInMyPot || (!applied.result().finished()
                            && !WheresTheBoneGameManifest.ID.equals(session.getGameId()))) {
                        matchHistoryService.recordForfeit(
                                room,
                                session,
                                principal.playerId(),
                                principal.displayName()
                        );
                    }
                    if (applied.result().finished()) {
                        room.markFinished();
                        matchHistoryService.recordIfFinished(room, session);
                    }
                    Map<String, Object> views = gameRuntimeService.projectViews(room, session);
                    realtimePublisher.gameEvents(
                            room,
                            null,
                            principal.playerId(),
                            List.copyOf(applied.result().events()),
                            views
                    );
                    if (applied.result().finished()) {
                        realtimePublisher.gameFinished(room, null, applied.result().winnerPlayerId(), views);
                        recycleFinishedRoom(room);
                    }
                }
            }
            room.leave(principal.playerId());
            realtimePublisher.playerLeft(room, principal.playerId());
            roomRepository.removePlayerIndex(principal.playerId());
            if (room.isEmpty()) {
                roomRepository.delete(roomId);
                gameRuntimeService.removeSession(roomId.value());
            }
            return null;
        });
    }

    public void syncPlayerDisplayName(String playerId, String requestedName) {
        roomRepository.findByPlayerId(playerId).ifPresent(existing ->
                roomLocks.withRoom(existing.getId().value(), () -> {
                    GameRoom room = roomRepository.findById(existing.getId()).orElse(null);
                    if (room == null) {
                        return null;
                    }
                    var player = room.findPlayer(playerId).orElse(null);
                    if (player == null) {
                        return null;
                    }
                    String base = UniqueDisplayNames.normalize(requestedName);
                    List<String> taken = room.getPlayers().stream()
                            .filter(other -> !playerId.equals(other.getPlayerId()))
                            .map(com.partygameonline.room.domain.RoomPlayer::getDisplayName)
                            .toList();
                    String unique = taken.contains(base) || UniqueDisplayNames.familyOccupied(base, taken)
                            ? UniqueDisplayNames.nextNumbered(base, taken)
                            : base;
                    if (!unique.equals(player.getDisplayName())) {
                        player.setDisplayName(unique);
                        realtimePublisher.roomSettingsChanged(room);
                    }
                    return null;
                })
        );
    }

    public void syncPlayerAvatar(String playerId, String avatarUrl) {
        roomRepository.findByPlayerId(playerId).ifPresent(existing ->
                roomLocks.withRoom(existing.getId().value(), () -> {
                    GameRoom room = roomRepository.findById(existing.getId()).orElse(null);
                    if (room == null) {
                        return null;
                    }
                    var player = room.findPlayer(playerId).orElse(null);
                    if (player == null) {
                        return null;
                    }
                    if (!java.util.Objects.equals(avatarUrl, player.getAvatarUrl())) {
                        player.setAvatarUrl(avatarUrl);
                        realtimePublisher.roomSettingsChanged(room);
                    }
                    return null;
                })
        );
    }

    public GameRoom ready(PlayerPrincipal principal, String rawRoomId, boolean ready) {
        RoomId roomId = RoomId.parse(rawRoomId);
        return roomLocks.withRoom(roomId.value(), () -> {
            GameRoom room = roomRepository.findById(roomId).orElseThrow(RoomException::notFound);
            room.setReady(principal.playerId(), ready);
            realtimePublisher.playerReadyChanged(room, principal.playerId(), ready);
            return room;
        });
    }

    public GameRoom updateSettings(
            PlayerPrincipal principal,
            String rawRoomId,
            Map<String, Object> nobSettings,
            Map<String, Object> notInMyPotSettings,
            Map<String, Object> wheresTheBoneSettings,
            Boolean locked,
            Integer maxPlayers
    ) {
        RoomId roomId = RoomId.parse(rawRoomId);
        return roomLocks.withRoom(roomId.value(), () -> {
            GameRoom room = roomRepository.findById(roomId).orElseThrow(RoomException::notFound);
            if (!room.getHostPlayerId().equals(principal.playerId())) {
                throw RoomException.notHost();
            }
            GameManifest game = gameRegistry.findById(room.getGameId())
                    .orElseThrow(RoomException::invalidSettings);
            if (maxPlayers != null) {
                room.updateMaxPlayers(resolveMaxPlayers(game, maxPlayers));
            }
            Map<String, Object> next = new LinkedHashMap<>(room.getSettings());
            if (locked != null) {
                next.put("locked", locked);
            }
            Map<String, Object> requested = new LinkedHashMap<>();
            requested.put("nob", settingOrCurrent(nobSettings, "nob", room));
            requested.put("notInMyPot", settingOrCurrent(notInMyPotSettings, "notInMyPot", room));
            requested.put("wheresTheBone", settingOrCurrent(wheresTheBoneSettings, "wheresTheBone", room));
            next.putAll(game.normalizeRoomSettings(requested));
            room.replaceSettings(next);
            realtimePublisher.roomSettingsChanged(room);
            return room;
        });
    }

    /**
     * Backwards-compatible overload for callers that only send NOB settings.
     */
    public GameRoom updateSettings(PlayerPrincipal principal, String rawRoomId, Map<String, Object> nobSettings) {
        return updateSettings(principal, rawRoomId, nobSettings, Map.of(), Map.of(), null, null);
    }

    /** Backwards-compatible overload for callers that send both game settings. */
    public GameRoom updateSettings(
            PlayerPrincipal principal,
            String rawRoomId,
            Map<String, Object> nobSettings,
            Map<String, Object> notInMyPotSettings
    ) {
        return updateSettings(principal, rawRoomId, nobSettings, notInMyPotSettings, Map.of(), null, null);
    }

    /** Backwards-compatible overload retained for the original two-game API. */
    public GameRoom updateSettings(
            PlayerPrincipal principal,
            String rawRoomId,
            Map<String, Object> nobSettings,
            Map<String, Object> notInMyPotSettings,
            Boolean locked
    ) {
        return updateSettings(principal, rawRoomId, nobSettings, notInMyPotSettings, Map.of(), locked, null);
    }

    public GameRoom updateSettings(
            PlayerPrincipal principal,
            String rawRoomId,
            Map<String, Object> nobSettings,
            Map<String, Object> notInMyPotSettings,
            Map<String, Object> wheresTheBoneSettings,
            Boolean locked
    ) {
        return updateSettings(principal, rawRoomId, nobSettings, notInMyPotSettings, wheresTheBoneSettings, locked, null);
    }

    public GameRoom kick(PlayerPrincipal principal, String rawRoomId, String targetPlayerId) {
        RoomId roomId = RoomId.parse(rawRoomId);
        return roomLocks.withPlayerThenRoom(targetPlayerId, roomId.value(), () -> {
            GameRoom room = roomRepository.findById(roomId).orElseThrow(RoomException::notFound);
            room.kick(principal.playerId(), targetPlayerId);
            roomRepository.removePlayerIndex(targetPlayerId);
            realtimePublisher.playerLeft(room, targetPlayerId);
            return room;
        });
    }

    public void close(PlayerPrincipal principal, String rawRoomId) {
        RoomId roomId = RoomId.parse(rawRoomId);
        roomLocks.withRoom(roomId.value(), () -> {
            GameRoom room = roomRepository.findById(roomId).orElseThrow(RoomException::notFound);
            room.close(principal.playerId());
            realtimePublisher.roomClosed(room);
            gameRuntimeService.removeSession(roomId.value());
            for (var player : room.getPlayers()) {
                roomRepository.removePlayerIndex(player.getPlayerId());
            }
            roomRepository.delete(roomId);
            return null;
        });
    }

    public GameRoom start(PlayerPrincipal principal, String rawRoomId) {
        RoomId roomId = RoomId.parse(rawRoomId);
        return roomLocks.withRoom(roomId.value(), () -> {
            GameRoom room = roomRepository.findById(roomId).orElseThrow(RoomException::notFound);
            GameManifest game = requireEnabledGame(room.getGameId());
            int requiredPlayers = game.requiredPlayers(room.getMaxPlayers());
            room.start(principal.playerId(), requiredPlayers);
            Optional<GameSession> session = gameRuntimeService.startGame(room);
            Map<String, Object> views = session
                    .map(active -> gameRuntimeService.projectViews(room, active))
                    .orElse(Map.of());
            realtimePublisher.gameStarted(room, views);
            return room;
        });
    }

    public void socketDisconnected(PlayerPrincipal principal) {
        roomRepository.findByPlayerId(principal.playerId()).ifPresent(existing ->
                roomLocks.withRoom(existing.getId().value(), () -> {
                    GameRoom room = roomRepository.findById(existing.getId()).orElse(null);
                    if (room == null || room.findPlayer(principal.playerId()).isEmpty()) {
                        return null;
                    }
                    room.markDisconnected(principal.playerId());
                    realtimePublisher.playerDisconnected(room, principal.playerId());
                    return null;
                })
        );
    }

    public Optional<GameRoom> socketReconnected(PlayerPrincipal principal) {
        return roomRepository.findByPlayerId(principal.playerId()).map(existing ->
                roomLocks.withRoom(existing.getId().value(), () -> {
                    GameRoom room = roomRepository.findById(existing.getId()).orElse(null);
                    if (room == null || room.findPlayer(principal.playerId()).isEmpty()) {
                        return null;
                    }
                    boolean wasDisconnected = room.findPlayer(principal.playerId())
                            .map(player -> player.getState() == PlayerLobbyState.DISCONNECTED)
                            .orElse(false);
                    if (wasDisconnected) {
                        room.markReconnected(principal.playerId());
                        realtimePublisher.playerReconnected(room, principal.playerId());
                    }
                    return room;
                })
        );
    }

    public void expireDisconnect(String playerId) {
        roomRepository.findByPlayerId(playerId).ifPresent(existing ->
                roomLocks.withPlayerThenRoom(playerId, existing.getId().value(), () -> {
                    GameRoom room = roomRepository.findById(existing.getId()).orElse(null);
                    if (room == null) {
                        return null;
                    }
                    var player = room.findPlayer(playerId).orElse(null);
                    if (player == null || player.getState() != PlayerLobbyState.DISCONNECTED) {
                        return null;
                    }
                    if (room.getStatus() == RoomStatus.WAITING
                            || room.getStatus() == RoomStatus.FINISHED) {
                        room.leave(playerId);
                        realtimePublisher.playerLeft(room, playerId);
                        if (room.isEmpty()) {
                            roomRepository.delete(room.getId());
                            gameRuntimeService.removeSession(room.getId().value());
                        } else {
                            roomRepository.removePlayerIndex(playerId);
                        }
                        return null;
                    }
                    GameSession session = gameRuntimeService.findSession(room.getId().value()).orElse(null);
                    if (session == null || session.isFinished()) {
                        room.leave(playerId);
                        realtimePublisher.playerLeft(room, playerId);
                        if (room.isEmpty()) {
                            roomRepository.delete(room.getId());
                            gameRuntimeService.removeSession(room.getId().value());
                        } else {
                            roomRepository.removePlayerIndex(playerId);
                        }
                        return null;
                    }
                    if (session.isForfeited(playerId)) {
                        return null;
                    }
                    AppliedAction applied = gameRuntimeService.abandon(
                            session,
                            PlayerContext.player(player.getPlayerId(), player.getDisplayName())
                    );
                    if (applied.accepted()) {
                        if (NotInMyPotGameManifest.ID.equals(session.getGameId())) {
                            matchHistoryService.recordForfeit(
                                    room,
                                    session,
                                    player.getPlayerId(),
                                    player.getDisplayName()
                            );
                        }
                        if (applied.result().finished()) {
                            room.markFinished();
                            matchHistoryService.recordIfFinished(room, session);
                            Map<String, Object> views = gameRuntimeService.projectViews(room, session);
                            realtimePublisher.gameFinished(room, null, applied.result().winnerPlayerId(), views);
                            recycleFinishedRoom(room);
                        }
                    }
                    return null;
                })
        );
    }

    public void recycleFinishedRoom(GameRoom room) {
        if (room.getStatus() != RoomStatus.FINISHED) {
            return;
        }
        room.returnToWaiting();
        gameRuntimeService.removeSession(room.getId().value());
        realtimePublisher.roomSettingsChanged(room);
    }

    private GameManifest requireEnabledGame(String gameId) {
        GameManifest game = gameRegistry.findById(gameId)
                .orElseThrow(() -> new ResourceNotFoundException("GAME_NOT_FOUND", "The game was not found"));
        if (!game.enabled()) {
            throw RoomException.gameDisabled();
        }
        return game;
    }

    private int resolveMaxPlayers(GameManifest game, Integer requestedMaxPlayers) {
        if (requestedMaxPlayers == null) {
            return game.maxPlayers();
        }
        if (requestedMaxPlayers < game.minPlayers() || requestedMaxPlayers > game.maxPlayers()) {
            throw RoomException.invalidMaxPlayers();
        }
        return requestedMaxPlayers;
    }

    private Object settingOrCurrent(Map<String, Object> requested, String key, GameRoom room) {
        if (requested != null && !requested.isEmpty()) {
            return requested;
        }
        return room.getSettings().get(key);
    }

    private void ensureNotInAnotherRoom(String playerId, RoomId targetRoom) {
        roomRepository.findByPlayerId(playerId).ifPresent(existing -> {
            if (targetRoom == null || !existing.getId().equals(targetRoom)) {
                throw RoomException.alreadyInARoom();
            }
        });
    }

    private RoomId allocateRoomId() {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            RoomId candidate = RoomId.random();
            if (!roomRepository.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate a room id");
    }
}
