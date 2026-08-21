package com.partygameonline.room.application;

import com.partygameonline.common.error.ResourceNotFoundException;
import com.partygameonline.game.core.GameManifest;
import com.partygameonline.game.core.GameRegistry;
import com.partygameonline.game.nob.NobGameManifest;
import com.partygameonline.game.nob.domain.NobTimingSettings;
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
                        maxPlayers,
                        roomVisibility,
                        Instant.now()
                );
                if (NobGameManifest.ID.equals(game.id())) {
                    room.replaceSettings(Map.of("nob", NobTimingSettings.defaults().toMap()));
                }
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
            room.join(principal.playerId(), principal.displayName());
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

    public GameRoom ready(PlayerPrincipal principal, String rawRoomId, boolean ready) {
        RoomId roomId = RoomId.parse(rawRoomId);
        return roomLocks.withRoom(roomId.value(), () -> {
            GameRoom room = roomRepository.findById(roomId).orElseThrow(RoomException::notFound);
            room.setReady(principal.playerId(), ready);
            realtimePublisher.playerReadyChanged(room, principal.playerId(), ready);
            return room;
        });
    }

    public GameRoom updateSettings(PlayerPrincipal principal, String rawRoomId, Map<String, Object> nobSettings) {
        RoomId roomId = RoomId.parse(rawRoomId);
        return roomLocks.withRoom(roomId.value(), () -> {
            GameRoom room = roomRepository.findById(roomId).orElseThrow(RoomException::notFound);
            if (!room.getHostPlayerId().equals(principal.playerId())) {
                throw RoomException.notHost();
            }
            if (!NobGameManifest.ID.equals(room.getGameId())) {
                throw RoomException.invalidSettings();
            }
            Map<String, Object> next = new LinkedHashMap<>(room.getSettings());
            next.put("nob", NobTimingSettings.fromMap(nobSettings).toMap());
            room.replaceSettings(next);
            realtimePublisher.roomSettingsChanged(room);
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
            room.start(principal.playerId(), game.minPlayers());
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
                    AppliedAction applied = gameRuntimeService.abandon(
                            session,
                            PlayerContext.player(player.getPlayerId(), player.getDisplayName())
                    );
                    if (applied.accepted() && applied.result().finished()) {
                        room.markFinished();
                        matchHistoryService.recordIfFinished(room, session);
                        Map<String, Object> views = gameRuntimeService.projectViews(room, session);
                        realtimePublisher.gameFinished(room, null, applied.result().winnerPlayerId(), views);
                    }
                    return null;
                })
        );
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
