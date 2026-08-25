package com.partygameonline.history.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matches")
public class MatchEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "game_id", nullable = false, length = 64)
    private String gameId;

    @Column(name = "room_id", length = 8)
    private String roomId;

    @Column(name = "winner_player_id", length = 64)
    private String winnerPlayerId;

    @Column(length = 32)
    private String result;

    @Column(name = "elo_processed", nullable = false)
    private boolean eloProcessed;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MatchEntity() {
    }

    public MatchEntity(UUID id, String gameId, Instant startedAt, Instant finishedAt, Instant createdAt) {
        this(id, gameId, null, null, null, startedAt, finishedAt, createdAt);
    }

    public MatchEntity(
            UUID id,
            String gameId,
            String roomId,
            String winnerPlayerId,
            String result,
            Instant startedAt,
            Instant finishedAt,
            Instant createdAt
    ) {
        this.id = id;
        this.gameId = gameId;
        this.roomId = roomId;
        this.winnerPlayerId = winnerPlayerId;
        this.result = result;
        this.eloProcessed = false;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.createdAt = createdAt;
    }

    public static MatchEntity newMatch(String gameId) {
        Instant now = Instant.now();
        return new MatchEntity(UUID.randomUUID(), gameId, now, null, now);
    }

    public static MatchEntity completed(
            String gameId,
            String roomId,
            String winnerPlayerId,
            String result,
            Instant startedAt,
            Instant finishedAt
    ) {
        Instant now = Instant.now();
        return new MatchEntity(
                UUID.randomUUID(),
                gameId,
                roomId,
                winnerPlayerId,
                result,
                startedAt,
                finishedAt,
                now
        );
    }

    public void finish(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getGameId() {
        return gameId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getWinnerPlayerId() {
        return winnerPlayerId;
    }

    public String getResult() {
        return result;
    }

    public boolean isEloProcessed() {
        return eloProcessed;
    }

    public void markEloProcessed() {
        this.eloProcessed = true;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
