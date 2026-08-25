package com.partygameonline.ranking.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-player, per-game rating state.  {@code elo} is the generic rating that
 * future games can use; {@code eloNob} is kept as an explicit NOB column so a
 * game-specific migration can be added without changing the user table.
 */
@Entity
@Table(
        name = "user_game_statistic",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_game_statistics_user_game",
                columnNames = {"user_id", "game_code"}
        )
)
public class UserGameStatisticEntity {

    public static final int DEFAULT_ELO = 5000;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 64, updatable = false)
    private String userId;

    @Column(name = "game_code", nullable = false, length = 64, updatable = false)
    private String gameCode;

    @Column(nullable = false)
    private int elo;

    @Column(name = "elo_nob", nullable = false)
    private int eloNob;

    @Column(name = "highest_elo", nullable = false)
    private int highestElo;

    @Column(name = "total_match", nullable = false)
    private int totalMatch;

    @Column(name = "total_win", nullable = false)
    private int totalWin;

    @Column(nullable = false)
    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserGameStatisticEntity() {
    }

    private UserGameStatisticEntity(String userId, String gameCode) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.gameCode = gameCode;
        this.elo = DEFAULT_ELO;
        this.eloNob = DEFAULT_ELO;
        this.highestElo = DEFAULT_ELO;
        this.totalMatch = 0;
        this.totalWin = 0;
        this.version = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static UserGameStatisticEntity newStatistic(String userId, String gameCode) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (gameCode == null || gameCode.isBlank()) {
            throw new IllegalArgumentException("gameCode is required");
        }
        return new UserGameStatisticEntity(userId, gameCode);
    }

    public void applyRatingDelta(int delta) {
        this.elo = Math.max(0, this.elo + delta);
        if ("night-of-bloodlines".equals(gameCode)) {
            this.eloNob = this.elo;
        }
        this.highestElo = Math.max(this.highestElo, this.elo);
        this.updatedAt = Instant.now();
    }

    public void completeMatch(boolean winner) {
        this.totalMatch += 1;
        if (winner) {
            this.totalWin += 1;
        }
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getGameCode() {
        return gameCode;
    }

    public int getElo() {
        return elo;
    }

    public int getEloNob() {
        return eloNob;
    }

    public int getHighestElo() {
        return highestElo;
    }

    public int getTotalMatch() {
        return totalMatch;
    }

    public int getTotalWin() {
        return totalWin;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
