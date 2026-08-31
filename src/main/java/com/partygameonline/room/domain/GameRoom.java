package com.partygameonline.room.domain;

import com.partygameonline.common.UniqueDisplayNames;
import com.partygameonline.common.avatar.AvatarCatalog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GameRoom {

    private static final String LOCKED_SETTING = "locked";

    private final RoomId id;
    private final RoomName name;
    private final String gameId;
    private String hostPlayerId;
    private int maxPlayers;
    private final RoomVisibility visibility;
    private final Instant createdAt;
    private RoomStatus status;
    private long serverSequence;
    private final List<RoomPlayer> players = new ArrayList<>();
    private final Map<String, Object> settings = new LinkedHashMap<>();

    public GameRoom(
            RoomId id,
            RoomName name,
            String gameId,
            String hostPlayerId,
            String hostDisplayName,
            int maxPlayers,
            RoomVisibility visibility,
            Instant createdAt
    ) {
        this(
                id,
                name,
                gameId,
                hostPlayerId,
                hostDisplayName,
                AvatarCatalog.DEFAULT_URL,
                maxPlayers,
                visibility,
                createdAt
        );
    }

    public GameRoom(
            RoomId id,
            RoomName name,
            String gameId,
            String hostPlayerId,
            String hostDisplayName,
            String hostAvatarUrl,
            int maxPlayers,
            RoomVisibility visibility,
            Instant createdAt
    ) {
        this.id = id;
        this.name = name;
        this.gameId = gameId;
        this.hostPlayerId = hostPlayerId;
        this.maxPlayers = maxPlayers;
        this.visibility = visibility;
        this.createdAt = createdAt;
        this.status = RoomStatus.WAITING;
        this.players.add(new RoomPlayer(
                hostPlayerId,
                hostDisplayName,
                hostAvatarUrl,
                PlayerLobbyState.CONNECTED
        ));
    }

    public RoomId getId() {
        return id;
    }

    public RoomName getName() {
        return name;
    }

    public String getGameId() {
        return gameId;
    }

    public String getHostPlayerId() {
        return hostPlayerId;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public RoomVisibility getVisibility() {
        return visibility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public long getServerSequence() {
        return serverSequence;
    }

    public long nextSequence() {
        serverSequence += 1;
        return serverSequence;
    }

    public List<RoomPlayer> getPlayers() {
        return List.copyOf(players);
    }

    public Map<String, Object> getSettings() {
        return Map.copyOf(settings);
    }

    public boolean isLocked() {
        return Boolean.TRUE.equals(settings.get(LOCKED_SETTING));
    }

    public void replaceSettings(Map<String, Object> next) {
        requireWaiting();
        settings.clear();
        if (next != null) {
            settings.putAll(next);
        }
    }

    /**
     * Changes the lobby capacity before the game starts. The room cannot be
     * shrunk below its current occupancy; otherwise an existing player would
     * be left in a room that can no longer contain them.
     */
    public void updateMaxPlayers(int nextMaxPlayers) {
        requireWaiting();
        if (nextMaxPlayers < players.size()) {
            throw RoomException.maxPlayersBelowCurrentCount(players.size(), nextMaxPlayers);
        }
        this.maxPlayers = nextMaxPlayers;
    }

    public boolean isPublicWaiting() {
        return visibility == RoomVisibility.PUBLIC && status == RoomStatus.WAITING && !isLocked();
    }

    public Optional<RoomPlayer> findPlayer(String playerId) {
        return players.stream().filter(player -> player.getPlayerId().equals(playerId)).findFirst();
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public void join(String playerId, String displayName) {
        join(playerId, displayName, AvatarCatalog.DEFAULT_URL);
    }

    public void join(String playerId, String displayName, String avatarUrl) {
        requireWaiting();
        if (findPlayer(playerId).isPresent()) {
            throw RoomException.alreadyJoined();
        }
        if (isLocked()) {
            throw RoomException.locked();
        }
        if (players.size() >= maxPlayers) {
            throw RoomException.full();
        }
        players.add(new RoomPlayer(
                playerId,
                uniqueJoinName(displayName),
                avatarUrl,
                PlayerLobbyState.CONNECTED
        ));
    }

    private List<String> currentDisplayNames() {
        return players.stream().map(RoomPlayer::getDisplayName).toList();
    }

    private String uniqueJoinName(String requested) {
        String base = UniqueDisplayNames.normalize(requested);
        if (!UniqueDisplayNames.familyOccupied(base, currentDisplayNames())) {
            return base;
        }
        for (RoomPlayer player : players) {
            if (player.getDisplayName().equals(base)) {
                List<String> taken = new ArrayList<>(currentDisplayNames());
                taken.remove(player.getDisplayName());
                player.setDisplayName(UniqueDisplayNames.nextNumbered(base, taken));
            }
        }
        return UniqueDisplayNames.nextNumbered(base, currentDisplayNames());
    }

    public void leave(String playerId) {
        RoomPlayer player = findPlayer(playerId).orElseThrow(RoomException::notMember);
        players.remove(player);
        if (hostPlayerId.equals(playerId) && !players.isEmpty()) {
            hostPlayerId = players.getFirst().getPlayerId();
        }
    }

    public void kick(String actorPlayerId, String targetPlayerId) {
        requireWaiting();
        if (!hostPlayerId.equals(actorPlayerId)) {
            throw RoomException.notHost();
        }
        if (hostPlayerId.equals(targetPlayerId)) {
            throw RoomException.cannotKickHost();
        }
        RoomPlayer target = findPlayer(targetPlayerId).orElseThrow(RoomException::notMember);
        players.remove(target);
    }

    public void setReady(String playerId, boolean ready) {
        requireWaiting();
        RoomPlayer player = findPlayer(playerId).orElseThrow(RoomException::notMember);
        player.setState(ready ? PlayerLobbyState.READY : PlayerLobbyState.CONNECTED);
    }

    public void start(String actorPlayerId, int minPlayers) {
        requireWaiting();
        if (!hostPlayerId.equals(actorPlayerId)) {
            throw RoomException.notHost();
        }
        if (players.size() < minPlayers) {
            throw RoomException.notEnoughPlayers();
        }
        boolean guestsReady = players.stream()
                .filter(player -> !hostPlayerId.equals(player.getPlayerId()))
                .allMatch(RoomPlayer::isReady);
        if (!guestsReady) {
            throw RoomException.playersNotReady();
        }
        this.status = RoomStatus.STARTING;
    }

    public void markInGame() {
        if (status != RoomStatus.STARTING) {
            throw RoomException.alreadyStarted();
        }
        this.status = RoomStatus.IN_GAME;
    }

    public void markFinished() {
        if (status != RoomStatus.IN_GAME && status != RoomStatus.STARTING) {
            throw RoomException.alreadyStarted();
        }
        this.status = RoomStatus.FINISHED;
    }

    public void returnToWaiting() {
        if (status != RoomStatus.FINISHED) {
            throw RoomException.alreadyStarted();
        }
        this.status = RoomStatus.WAITING;
        for (RoomPlayer player : players) {
            if (player.getState() != PlayerLobbyState.DISCONNECTED) {
                player.setState(PlayerLobbyState.CONNECTED);
            }
        }
    }

    public void close(String actorPlayerId) {
        if (!hostPlayerId.equals(actorPlayerId)) {
            throw RoomException.notHost();
        }
        if (status == RoomStatus.FINISHED || status == RoomStatus.CLOSED) {
            throw RoomException.alreadyStarted();
        }
        this.status = RoomStatus.CLOSED;
    }

    public void markDisconnected(String playerId) {
        RoomPlayer player = findPlayer(playerId).orElseThrow(RoomException::notMember);
        player.setState(PlayerLobbyState.DISCONNECTED);
    }

    public void markReconnected(String playerId) {
        RoomPlayer player = findPlayer(playerId).orElseThrow(RoomException::notMember);
        if (status == RoomStatus.WAITING) {
            player.setState(PlayerLobbyState.CONNECTED);
            return;
        }
        player.setState(PlayerLobbyState.CONNECTED);
    }

    private void requireWaiting() {
        if (status != RoomStatus.WAITING) {
            throw RoomException.alreadyStarted();
        }
    }
}
