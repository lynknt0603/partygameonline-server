package com.partygameonline.game.wheresthebone.domain;

import com.partygameonline.game.core.GameEloChange;
import com.partygameonline.game.core.GameEloChangeSink;
import com.partygameonline.game.core.GameOutcomeState;
import com.partygameonline.game.core.GamePlayerOutcome;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WheresTheBoneGameState implements GameOutcomeState, GameEloChangeSink {

    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 8;
    public static final int MAX_HOUR = 6;
    public static final int DISCUSSION_SECONDS_PER_PLAYER = 60;
    public static final int VOTING_SECONDS = 60;

    private final String roomId;
    private final String hostPlayerId;
    private final List<String> playerIds;
    private final Map<String, String> displayNames;
    private final Map<String, Integer> seats = new LinkedHashMap<>();
    private final Map<String, WheresTheBoneRole> roles = new LinkedHashMap<>();
    private final Map<String, List<Integer>> diceRolls = new LinkedHashMap<>();
    private final Map<String, List<Integer>> wakeHours = new LinkedHashMap<>();
    private final Set<String> currentHourDone = new LinkedHashSet<>();
    private final Map<String, List<WheresTheBonePeek>> peekResults = new LinkedHashMap<>();
    private final Map<String, Map<Integer, Set<String>>> coAwakeRecords = new LinkedHashMap<>();
    private final Map<String, List<Integer>> witnessedBoneTakenHours = new LinkedHashMap<>();
    private final Map<String, List<Integer>> observedBonePresentHours = new LinkedHashMap<>();
    private final Map<String, List<Integer>> observedBoneMissingHours = new LinkedHashMap<>();
    private final Set<String> packmates = new LinkedHashSet<>();
    private final Set<String> abandonedPlayerIds = new LinkedHashSet<>();
    private final Set<String> pendingPackCandidates = new LinkedHashSet<>();
    private final Map<String, String> votes = new LinkedHashMap<>();
    private final Set<String> discussionSkipRequesters = new LinkedHashSet<>();
    private final Map<String, Boolean> discussionSkipResponses = new LinkedHashMap<>();
    private final List<WheresTheBoneEvent> events = new ArrayList<>();
    private final Map<String, GameEloChange> eloChanges = new LinkedHashMap<>();
    private final Set<String> processedCommandIds = new LinkedHashSet<>();
    private final List<String> winnerPlayerIds = new ArrayList<>();

    private WheresTheBoneSettings settings;
    private WheresTheBonePhase phase = WheresTheBonePhase.WAKE_SELECTION;
    private WheresTheBonePackSelectionMode packSelectionMode;
    private int currentHour;
    private int version = 1;
    private Instant phaseStartedAt;
    private Instant deadline;
    private Instant suspendedNightDeadline;
    private boolean boneTaken;
    private Integer boneTakenHour;
    private String boneTakenBy;
    private String discussionSkipRequesterId;
    private int pendingPackCount;
    private WheresTheBoneRole winnerFaction;
    private Set<String> revealedPlayerIds = new LinkedHashSet<>();

    public WheresTheBoneGameState(
            String roomId,
            String hostPlayerId,
            List<String> playerIds,
            Map<String, String> displayNames,
            WheresTheBoneSettings settings
    ) {
        this.roomId = roomId;
        this.hostPlayerId = hostPlayerId;
        this.playerIds = List.copyOf(playerIds);
        this.displayNames = Map.copyOf(displayNames);
        this.settings = settings == null ? WheresTheBoneSettings.defaults() : settings;
        for (int seat = 0; seat < this.playerIds.size(); seat++) {
            seats.put(this.playerIds.get(seat), seat);
        }
        this.phaseStartedAt = Instant.now();
    }

    public String getRoomId() { return roomId; }
    public String getHostPlayerId() { return hostPlayerId; }
    public List<String> getPlayerIds() { return playerIds; }
    public String displayName(String playerId) { return displayNames.getOrDefault(playerId, playerId); }
    public int seat(String playerId) { return seats.getOrDefault(playerId, 0); }
    public Map<String, WheresTheBoneRole> getRoles() { return roles; }
    public Map<String, List<Integer>> getDiceRolls() { return diceRolls; }
    public Map<String, List<Integer>> getWakeHours() { return wakeHours; }
    public Set<String> getCurrentHourDone() { return currentHourDone; }
    public Map<String, List<WheresTheBonePeek>> getPeekResults() { return peekResults; }
    public Map<String, Map<Integer, Set<String>>> getCoAwakeRecords() { return coAwakeRecords; }
    public Map<String, List<Integer>> getWitnessedBoneTakenHours() { return witnessedBoneTakenHours; }
    public Map<String, List<Integer>> getObservedBonePresentHours() { return observedBonePresentHours; }
    public Map<String, List<Integer>> getObservedBoneMissingHours() { return observedBoneMissingHours; }
    public Set<String> getPackmates() { return packmates; }
    public Set<String> getAbandonedPlayerIds() { return Collections.unmodifiableSet(abandonedPlayerIds); }
    public Set<String> getPendingPackCandidates() { return pendingPackCandidates; }
    public Map<String, String> getVotes() { return votes; }
    public Set<String> getDiscussionSkipRequesters() { return discussionSkipRequesters; }
    public Map<String, Boolean> getDiscussionSkipResponses() { return discussionSkipResponses; }
    public List<WheresTheBoneEvent> getEvents() { return List.copyOf(events); }
    public Map<String, GameEloChange> getEloChanges() { return Map.copyOf(eloChanges); }
    public WheresTheBoneSettings getSettings() { return settings; }
    public void setSettings(WheresTheBoneSettings settings) { this.settings = settings; }
    public WheresTheBonePhase getPhase() { return phase; }
    public void setPhase(WheresTheBonePhase phase) { this.phase = phase; }
    public WheresTheBonePackSelectionMode getPackSelectionMode() { return packSelectionMode; }
    public void setPackSelectionMode(WheresTheBonePackSelectionMode mode) { this.packSelectionMode = mode; }
    public int getCurrentHour() { return currentHour; }
    public void setCurrentHour(int currentHour) { this.currentHour = currentHour; }
    public int getVersion() { return version; }
    public void bumpVersion() { version++; }
    public Instant getPhaseStartedAt() { return phaseStartedAt; }
    public void setPhaseStartedAt(Instant phaseStartedAt) { this.phaseStartedAt = phaseStartedAt; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }
    public Instant getSuspendedNightDeadline() { return suspendedNightDeadline; }
    public void setSuspendedNightDeadline(Instant value) { this.suspendedNightDeadline = value; }
    public boolean isBoneTaken() { return boneTaken; }
    public void setBoneTaken(boolean value) { boneTaken = value; }
    public Integer getBoneTakenHour() { return boneTakenHour; }
    public void setBoneTakenHour(Integer value) { boneTakenHour = value; }
    public String getBoneTakenBy() { return boneTakenBy; }
    public void setBoneTakenBy(String value) { boneTakenBy = value; }
    public String getDiscussionSkipRequesterId() { return discussionSkipRequesterId; }
    public void setDiscussionSkipRequesterId(String value) { discussionSkipRequesterId = value; }
    public int getPendingPackCount() { return pendingPackCount; }
    public void setPendingPackCount(int value) { pendingPackCount = value; }
    public WheresTheBoneRole getWinnerFaction() { return winnerFaction; }
    public void setWinnerFaction(WheresTheBoneRole value) { winnerFaction = value; }
    public Set<String> getRevealedPlayerIds() { return Collections.unmodifiableSet(revealedPlayerIds); }
    public void setRevealedPlayerIds(Set<String> ids) { revealedPlayerIds = new LinkedHashSet<>(ids); }
    public List<String> getWinnerPlayerIds() { return List.copyOf(winnerPlayerIds); }
    public void setWinnerPlayerIds(Iterable<String> ids) { winnerPlayerIds.clear(); ids.forEach(winnerPlayerIds::add); }

    public boolean isFinished() { return phase == WheresTheBonePhase.RESULT; }
    public boolean isActive(String playerId) { return playerIds.contains(playerId) && !abandonedPlayerIds.contains(playerId); }
    public void abandon(String playerId) { if (playerIds.contains(playerId)) abandonedPlayerIds.add(playerId); }
    public List<String> activePlayerIds() { return playerIds.stream().filter(this::isActive).toList(); }
    public boolean isProcessed(String commandId) { return commandId != null && processedCommandIds.contains(commandId); }
    public void markProcessed(String commandId) { if (commandId != null && !commandId.isBlank()) processedCommandIds.add(commandId); }
    public boolean isAwakeAt(String playerId, int hour) { return wakeHours.getOrDefault(playerId, List.of()).contains(hour); }
    public boolean isAwakeNow(String playerId) { return phase == WheresTheBonePhase.NIGHT_HOUR && isAwakeAt(playerId, currentHour); }
    public boolean hasSelectedWake(String playerId) { return !wakeHours.getOrDefault(playerId, List.of()).isEmpty(); }
    public boolean isDone(String playerId) { return currentHourDone.contains(playerId); }
    public void markDone(String playerId) { currentHourDone.add(playerId); }
    public void clearDone() { currentHourDone.clear(); }
    public List<Integer> diceFor(String playerId) { return diceRolls.getOrDefault(playerId, List.of()); }
    public List<Integer> wakeFor(String playerId) { return wakeHours.getOrDefault(playerId, List.of()); }
    public Set<String> awakePlayerIds() {
        if (phase != WheresTheBonePhase.NIGHT_HOUR || currentHour < 1 || currentHour > MAX_HOUR) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String id : playerIds) if (isActive(id) && isAwakeAt(id, currentHour)) result.add(id);
        return result;
    }
    public List<WheresTheBonePeek> peekFor(String playerId) { return peekResults.computeIfAbsent(playerId, ignored -> new ArrayList<>()); }
    public Map<Integer, Set<String>> coAwakeFor(String playerId) { return coAwakeRecords.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>()); }
    public List<Integer> witnessedFor(String playerId) { return witnessedBoneTakenHours.computeIfAbsent(playerId, ignored -> new ArrayList<>()); }
    public List<Integer> observedPresentFor(String playerId) { return observedBonePresentHours.computeIfAbsent(playerId, ignored -> new ArrayList<>()); }
    public List<Integer> observedMissingFor(String playerId) { return observedBoneMissingHours.computeIfAbsent(playerId, ignored -> new ArrayList<>()); }
    public void addEvent(WheresTheBoneEvent event) { if (event != null) events.add(event); }

    @Override
    public Set<String> winnerPlayerIds() { return Collections.unmodifiableSet(new LinkedHashSet<>(winnerPlayerIds)); }

    @Override
    public GamePlayerOutcome playerOutcome(String playerId) {
        WheresTheBoneRole role = roles.get(playerId);
        return role == null ? null : new GamePlayerOutcome(winnerPlayerIds.contains(playerId) ? 1 : 0, role.name(), null);
    }

    @Override
    public void recordEloChanges(Map<String, GameEloChange> changes) {
        eloChanges.clear();
        if (changes != null) eloChanges.putAll(changes);
    }
}
