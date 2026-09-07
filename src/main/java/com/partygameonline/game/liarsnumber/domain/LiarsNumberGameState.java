package com.partygameonline.game.liarsnumber.domain;

import com.partygameonline.game.core.GameEloChange;
import com.partygameonline.game.core.GameEloChangeSink;
import com.partygameonline.game.core.GameOutcomeState;
import com.partygameonline.game.core.GamePlayerOutcome;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LiarsNumberGameState implements GameOutcomeState, GameEloChangeSink {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 6;
    public static final int DECK_SIZE = 64;
    public static final int TWO_PLAYER_REMOVED_CARDS = 10;
    public static final int NORMAL_THRESHOLD = 4;
    public static final int TWO_PLAYER_THRESHOLD = 5;

    private final String roomId;
    private final int playerCount;
    private final List<LiarsNumberPlayerState> players = new ArrayList<>();
    private final List<LiarsNumberCard> removedCards = new ArrayList<>();
    private final List<LiarsNumberEvent> publicEvents = new ArrayList<>();
    private final Set<String> processedCommandIds = new LinkedHashSet<>();
    private final List<String> winnerPlayerIds = new ArrayList<>();
    private final Map<String, GameEloChange> eloChanges = new LinkedHashMap<>();

    private LiarsNumberPhase phase = LiarsNumberPhase.SELECT_CARD;
    private String currentRoundStarterId;
    private LiarsNumberActiveRound activeRound;
    private LiarsNumberCard lastResolvedCard;
    private String lastPenaltyPlayerId;
    private Integer lastPenaltyType;
    private Integer lastPenaltyScore;
    private Integer lastPenaltyThreshold;
    private Boolean lastClaimIsTrue;
    private Boolean lastReceiverCorrect;
    private String gameOverReason;
    private String loserId;
    private int roundNumber = 1;
    private int stateVersion = 1;
    private int turnSeconds = LiarsNumberSettings.DEFAULT_TURN_SECONDS;
    private Instant turnDeadline;
    private Instant finishedAt;

    public LiarsNumberGameState(String roomId, int playerCount) {
        this.roomId = roomId;
        this.playerCount = playerCount;
    }

    public String getRoomId() { return roomId; }
    public int getPlayerCount() { return playerCount; }
    public List<LiarsNumberPlayerState> getPlayers() { return players; }
    public void addPlayer(LiarsNumberPlayerState player) { players.add(player); }
    public LiarsNumberPlayerState findPlayer(String playerId) {
        return players.stream().filter(player -> player.getPlayerId().equals(playerId)).findFirst().orElse(null);
    }
    public LiarsNumberPhase getPhase() { return phase; }
    public void setPhase(LiarsNumberPhase phase) { this.phase = phase; }
    public String getCurrentRoundStarterId() { return currentRoundStarterId; }
    public void setCurrentRoundStarterId(String id) { currentRoundStarterId = id; }
    public LiarsNumberActiveRound getActiveRound() { return activeRound; }
    public void setActiveRound(LiarsNumberActiveRound activeRound) { this.activeRound = activeRound; }
    public List<LiarsNumberCard> getRemovedCards() { return removedCards; }
    public List<LiarsNumberEvent> getPublicEvents() { return publicEvents; }
    public void addPublicEvent(LiarsNumberEvent event) { publicEvents.add(event); }
    public boolean isProcessedCommand(String commandId) { return commandId != null && processedCommandIds.contains(commandId); }
    public void markCommandProcessed(String commandId) { if (commandId != null && !commandId.isBlank()) processedCommandIds.add(commandId); }
    public int getRoundNumber() { return roundNumber; }
    public void incrementRoundNumber() { roundNumber += 1; }
    public int getStateVersion() { return stateVersion; }
    public void bumpVersion() { stateVersion += 1; }
    public int getTurnSeconds() { return turnSeconds; }
    public Instant getTurnDeadline() { return turnDeadline; }
    public void configure(LiarsNumberSettings settings) {
        this.turnSeconds = settings == null ? LiarsNumberSettings.DEFAULT_TURN_SECONDS : settings.turnSeconds();
        resetTurnDeadline();
    }
    public void resetTurnDeadline() {
        resetTurnDeadline(Instant.now());
    }
    public void resetTurnDeadline(Instant now) {
        turnDeadline = turnSeconds <= 0 || isFinished() ? null : now.plusSeconds(turnSeconds);
    }
    public boolean timeoutIsDue(Instant now) {
        return turnDeadline != null && !turnDeadline.isAfter(now);
    }
    public String currentActorId() {
        if (isFinished()) return null;
        if (phase == LiarsNumberPhase.SELECT_CARD) return currentRoundStarterId;
        if (activeRound == null) return null;
        return phase == LiarsNumberPhase.RECEIVER_DECISION
                ? activeRound.getCurrentReceiverId()
                : activeRound.getCurrentSenderId();
    }
    public int threshold() { return playerCount == 2 ? TWO_PLAYER_THRESHOLD : NORMAL_THRESHOLD; }
    public boolean isFinished() { return phase == LiarsNumberPhase.GAME_OVER; }
    public String getLoserId() { return loserId; }
    public String getGameOverReason() { return gameOverReason; }
    public Instant getFinishedAt() { return finishedAt; }
    public List<String> getWinnerPlayerIds() { return winnerPlayerIds; }
    public LiarsNumberCard getLastResolvedCard() { return lastResolvedCard; }
    public String getLastPenaltyPlayerId() { return lastPenaltyPlayerId; }
    public Integer getLastPenaltyType() { return lastPenaltyType; }
    public Integer getLastPenaltyScore() { return lastPenaltyScore; }
    public Integer getLastPenaltyThreshold() { return lastPenaltyThreshold; }
    public Boolean getLastClaimIsTrue() { return lastClaimIsTrue; }
    public Boolean getLastReceiverCorrect() { return lastReceiverCorrect; }
    public Map<String, GameEloChange> getEloChanges() { return eloChanges; }

    public void recordResolution(
            LiarsNumberCard card,
            String penaltyPlayerId,
            int penaltyType,
            int penaltyScore,
            boolean claimIsTrue,
            boolean receiverCorrect
    ) {
        lastResolvedCard = card;
        lastPenaltyPlayerId = penaltyPlayerId;
        lastPenaltyType = penaltyType;
        lastPenaltyScore = penaltyScore;
        lastPenaltyThreshold = threshold();
        lastClaimIsTrue = claimIsTrue;
        lastReceiverCorrect = receiverCorrect;
    }

    public void finish(String loserId, String reason) {
        this.loserId = loserId;
        this.gameOverReason = reason;
        this.finishedAt = Instant.now();
        this.phase = LiarsNumberPhase.GAME_OVER;
        this.turnDeadline = null;
        this.winnerPlayerIds.clear();
        for (LiarsNumberPlayerState player : players) {
            if (!player.getPlayerId().equals(loserId)) {
                this.winnerPlayerIds.add(player.getPlayerId());
            }
        }
    }

    public void recordEloChanges(Map<String, GameEloChange> changes) {
        eloChanges.clear();
        if (changes != null) {
            eloChanges.putAll(changes);
        }
    }

    @Override
    public Set<String> winnerPlayerIds() {
        return Set.copyOf(winnerPlayerIds);
    }

    @Override
    public GamePlayerOutcome playerOutcome(String playerId) {
        LiarsNumberPlayerState player = findPlayer(playerId);
        return player == null ? null : new GamePlayerOutcome(player.totalPenaltyScore(), null, null);
    }
}
