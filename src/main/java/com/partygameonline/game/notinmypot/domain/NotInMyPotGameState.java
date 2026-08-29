package com.partygameonline.game.notinmypot.domain;

import com.partygameonline.game.core.GameEloChange;
import com.partygameonline.game.core.GameEloChangeSink;
import com.partygameonline.game.core.GameOutcomeState;
import com.partygameonline.game.core.GamePlayerOutcome;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NotInMyPotGameState implements GameOutcomeState, GameEloChangeSink {

    public static final int MIN_PLAYERS = 3;
    public static final int MAX_PLAYERS = 8;
    public static final int HAND_SIZE = 3;
    public static final int MAX_DOORS = 3;
    public static final int SCOOP_LIMIT = 2;
    public static final int SLOTTED_SPOON_LIMIT = 3;
    public static final int EMERGENCY_DRAW_COUNT = 3;
    public static final int EMERGENCY_RETURN_COUNT = 2;
    public static final int TRASH_DRAW_COUNT = 3;
    public static final int NORMAL_DRAW_COUNT = 1;

    private final String roomId;
    private final List<NotInMyPotPlayerState> players = new ArrayList<>();
    private final Deque<NotInMyPotCard> drawPile = new ArrayDeque<>();
    private final Deque<NotInMyPotCard> pot = new ArrayDeque<>();
    private final List<NotInMyPotCard> discardPile = new ArrayList<>();
    private final Map<String, NotInMyPotRole> roles = new LinkedHashMap<>();
    private final Map<String, NotInMyPotRole> publicRoles = new LinkedHashMap<>();
    private final Map<String, Integer> doorCountByPlayer = new LinkedHashMap<>();
    private final List<NotInMyPotEvent> publicEvents = new ArrayList<>();
    private final Set<String> processedCommandIds = new LinkedHashSet<>();
    private final List<String> winnerPlayerIds = new ArrayList<>();
    private final Map<String, GameEloChange> eloChanges = new LinkedHashMap<>();

    private NotInMyPotPhase phase = NotInMyPotPhase.STARTING;
    private NotInMyPotSettings settings = NotInMyPotSettings.defaults();
    private int targetScore;
    private String currentPlayerId;
    private Instant turnDeadline;
    private int turnNumber = 1;
    private int stateVersion = 1;
    private boolean turnHasActed;
    private boolean finished;
    private NotInMyPotRole winnerFaction;
    private Integer finalPotScore;
    private NotInMyPotPendingAction pendingAction;

    public NotInMyPotGameState(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomId() {
        return roomId;
    }

    public NotInMyPotPhase getPhase() {
        return phase;
    }

    public void setPhase(NotInMyPotPhase phase) {
        this.phase = phase;
    }

    public NotInMyPotSettings getSettings() {
        return settings;
    }

    public void configure(NotInMyPotSettings settings) {
        this.settings = settings == null ? NotInMyPotSettings.defaults() : settings;
    }

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public void setCurrentPlayerId(String currentPlayerId) {
        this.currentPlayerId = currentPlayerId;
    }

    public Instant getTurnDeadline() {
        return turnDeadline;
    }

    public void setTurnDeadline(Instant turnDeadline) {
        this.turnDeadline = turnDeadline;
    }

    public int getTargetScore() {
        return targetScore;
    }

    public void setTargetScore(int targetScore) {
        this.targetScore = Math.max(0, targetScore);
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
    }

    public void incrementTurnNumber() {
        turnNumber += 1;
    }

    public int getStateVersion() {
        return stateVersion;
    }

    public int getVersion() {
        return stateVersion;
    }

    public void bumpVersion() {
        stateVersion += 1;
    }

    public boolean hasTurnActed() {
        return turnHasActed;
    }

    public boolean isTurnBeginning() {
        return !turnHasActed;
    }

    public void setTurnHasActed(boolean turnHasActed) {
        this.turnHasActed = turnHasActed;
    }

    public boolean isFinished() {
        return finished;
    }

    public NotInMyPotRole getWinnerFaction() {
        return winnerFaction;
    }

    public Integer getFinalPotScore() {
        return finalPotScore;
    }

    public void addPlayer(NotInMyPotPlayerState player) {
        players.add(player);
        roles.put(player.getPlayerId(), player.getRole());
        publicRoles.remove(player.getPlayerId());
        doorCountByPlayer.put(player.getPlayerId(), player.getDoorCount());
    }

    public List<NotInMyPotPlayerState> getPlayers() {
        return players;
    }

    public NotInMyPotPlayerState player(String playerId) {
        return players.stream()
                .filter(player -> player.getPlayerId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    public NotInMyPotPlayerState requirePlayer(String playerId) {
        NotInMyPotPlayerState player = player(playerId);
        if (player == null) {
            throw new IllegalArgumentException("Unknown player");
        }
        return player;
    }

    public List<NotInMyPotPlayerState> activePlayers() {
        return players.stream().filter(NotInMyPotPlayerState::isActive).toList();
    }

    public Map<String, List<NotInMyPotCard>> getHands() {
        Map<String, List<NotInMyPotCard>> result = new LinkedHashMap<>();
        for (NotInMyPotPlayerState player : players) {
            result.put(player.getPlayerId(), List.copyOf(player.getHand()));
        }
        return Collections.unmodifiableMap(result);
    }

    public Map<String, NotInMyPotRole> getRoles() {
        return Map.copyOf(roles);
    }

    public Deque<NotInMyPotCard> getDrawPile() {
        return drawPile;
    }

    public Deque<NotInMyPotCard> getPot() {
        return pot;
    }

    public List<NotInMyPotCard> getPotBottomToTop() {
        List<NotInMyPotCard> result = new ArrayList<>(pot);
        Collections.reverse(result);
        return List.copyOf(result);
    }

    public List<NotInMyPotCard> getDiscardPile() {
        return discardPile;
    }

    public Map<String, Integer> getDoorCountByPlayer() {
        return Map.copyOf(doorCountByPlayer);
    }

    public int doorCount(String playerId) {
        return doorCountByPlayer.getOrDefault(playerId, 0);
    }

    public void incrementDoorCount(String playerId) {
        int next = doorCount(playerId) + 1;
        doorCountByPlayer.put(playerId, next);
        requirePlayer(playerId).setDoorCount(next);
    }

    public Map<String, NotInMyPotRole> getPublicRoles() {
        return Map.copyOf(publicRoles);
    }

    public void revealRole(String playerId) {
        NotInMyPotRole role = roles.get(playerId);
        if (role != null) {
            publicRoles.put(playerId, role);
        }
    }

    public void revealAllRoles() {
        publicRoles.clear();
        publicRoles.putAll(roles);
    }

    public List<NotInMyPotEvent> getPublicEvents() {
        return List.copyOf(publicEvents);
    }

    public void addPublicEvent(NotInMyPotEvent event) {
        if (event != null) {
            publicEvents.add(event);
        }
    }

    public Set<String> getProcessedCommandIds() {
        return processedCommandIds;
    }

    public boolean isDuplicateCommand(String commandId) {
        return commandId != null && !commandId.isBlank() && processedCommandIds.contains(commandId);
    }

    public void markCommandProcessed(String commandId) {
        if (commandId != null && !commandId.isBlank()) {
            processedCommandIds.add(commandId);
        }
    }

    public NotInMyPotPendingAction getPendingAction() {
        return pendingAction;
    }

    public void setPendingAction(NotInMyPotPendingAction pendingAction) {
        this.pendingAction = pendingAction;
    }

    public boolean timeoutIsDue(Instant now) {
        Instant reference = now == null ? Instant.now() : now;
        if (pendingAction != null) {
            return pendingAction.expiresAt() != null
                    && !pendingAction.expiresAt().isAfter(reference);
        }
        return phase == NotInMyPotPhase.PLAYING
                && currentPlayerId != null
                && turnDeadline != null
                && !turnDeadline.isAfter(reference);
    }

    public int scorePot() {
        return pot.stream().mapToInt(NotInMyPotCard::score).sum();
    }

    public List<String> getWinnerPlayerIds() {
        return List.copyOf(winnerPlayerIds);
    }

    @Override
    public Set<String> winnerPlayerIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(winnerPlayerIds));
    }

    public void finish(NotInMyPotRole winnerFaction) {
        finish(winnerFaction, true);
    }

    public void finish(NotInMyPotRole winnerFaction, boolean calculateFinalPotScore) {
        if (finished) {
            return;
        }
        this.finished = true;
        this.phase = NotInMyPotPhase.GAME_OVER;
        this.winnerFaction = winnerFaction;
        this.finalPotScore = calculateFinalPotScore ? scorePot() : null;
        this.pendingAction = null;
        this.currentPlayerId = null;
        this.turnDeadline = null;
        this.turnHasActed = false;
        this.winnerPlayerIds.clear();
        for (NotInMyPotPlayerState player : players) {
            if (player.getRole() == winnerFaction) {
                winnerPlayerIds.add(player.getPlayerId());
            }
        }
        revealAllRoles();
    }

    @Override
    public GamePlayerOutcome playerOutcome(String playerId) {
        NotInMyPotPlayerState player = player(playerId);
        if (player == null) {
            return null;
        }
        return new GamePlayerOutcome(finalPotScore, player.getRole().name(), null);
    }

    public Map<String, GameEloChange> getEloChanges() {
        return Map.copyOf(eloChanges);
    }

    @Override
    public void recordEloChanges(Map<String, GameEloChange> changes) {
        eloChanges.clear();
        if (changes != null) {
            eloChanges.putAll(changes);
        }
    }
}
