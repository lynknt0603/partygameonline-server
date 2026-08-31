package com.partygameonline.game.runtime;

import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.RandomSource;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GameSession {

    private final String roomId;
    private final String gameId;
    private final GameConfig config;
    private final RandomSource random;
    private final Instant startedAt;
    private Object state;
    private boolean finished;
    private String winnerPlayerId;
    private String result;
    private Instant finishedAt;
    private UUID persistedMatchId;
    private final Set<Integer> eloProcessedRoundNumbers = new HashSet<>();
    private final Set<String> forfeitedPlayerIds = new HashSet<>();

    public GameSession(
            String roomId,
            String gameId,
            GameConfig config,
            RandomSource random,
            Object state,
            Instant startedAt
    ) {
        this.roomId = roomId;
        this.gameId = gameId;
        this.config = config;
        this.random = random;
        this.state = state;
        this.startedAt = startedAt;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getGameId() {
        return gameId;
    }

    public GameConfig getConfig() {
        return config;
    }

    public RandomSource getRandom() {
        return random;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Object getState() {
        return state;
    }

    public void setState(Object state) {
        this.state = state;
    }

    public boolean isFinished() {
        return finished;
    }

    public String getWinnerPlayerId() {
        return winnerPlayerId;
    }

    public String getResult() {
        return result;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public UUID getPersistedMatchId() {
        return persistedMatchId;
    }

    public void markPersisted(UUID matchId) {
        this.persistedMatchId = matchId;
    }

    public boolean isEloRoundProcessed(int roundNumber) {
        return eloProcessedRoundNumbers.contains(roundNumber);
    }

    public void markEloRoundProcessed(int roundNumber) {
        eloProcessedRoundNumbers.add(roundNumber);
    }

    public boolean isForfeited(String playerId) {
        return playerId != null && forfeitedPlayerIds.contains(playerId);
    }

    public void markForfeited(String playerId) {
        if (playerId != null && !playerId.isBlank()) {
            forfeitedPlayerIds.add(playerId);
        }
    }

    public Set<String> getForfeitedPlayerIds() {
        return Set.copyOf(forfeitedPlayerIds);
    }

    public void finish(String winnerPlayerId, Instant finishedAt) {
        finish(winnerPlayerId, finishedAt, "COMPLETED");
    }

    public void finish(String winnerPlayerId, Instant finishedAt, String result) {
        this.finished = true;
        this.winnerPlayerId = winnerPlayerId;
        this.finishedAt = finishedAt;
        this.result = result;
    }
}
