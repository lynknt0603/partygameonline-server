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
 * Per-player, per-game rating state. {@code elo} remains the generic/fallback
 * value, while supported ranked games keep explicit columns so one game's
 * rating can never overwrite another game's rating.
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
    public static final String NOB_GAME = "night-of-bloodlines";
    public static final String NOT_IN_MY_POT_GAME = "not-in-my-pot";

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

    @Column(name = "elo_not_in_my_pot", nullable = false)
    private int eloNotInMyPot;

    @Column(name = "highest_elo", nullable = false)
    private int highestElo;

    @Column(name = "highest_elo_not_in_my_pot", nullable = false)
    private int highestEloNotInMyPot;

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
        this.eloNotInMyPot = DEFAULT_ELO;
        this.highestElo = DEFAULT_ELO;
        this.highestEloNotInMyPot = DEFAULT_ELO;
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
        int nextElo = Math.max(0, getEloForGame() + delta);
        this.elo = nextElo;
        if (NOB_GAME.equals(gameCode)) {
            this.eloNob = nextElo;
        } else if (NOT_IN_MY_POT_GAME.equals(gameCode)) {
            this.eloNotInMyPot = nextElo;
            this.highestEloNotInMyPot = Math.max(this.highestEloNotInMyPot, nextElo);
        }
        this.highestElo = Math.max(this.highestElo, nextElo);
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

    public int getEloNotInMyPot() {
        return eloNotInMyPot;
    }

    public int getHighestElo() {
        return highestElo;
    }

    public int getHighestEloNotInMyPot() {
        return highestEloNotInMyPot;
    }

    public int getEloForGame() {
        return switch (gameCode) {
            case NOB_GAME -> eloNob;
            case NOT_IN_MY_POT_GAME -> eloNotInMyPot;
            default -> elo;
        };
    }

    public int getHighestEloForGame() {
        return NOT_IN_MY_POT_GAME.equals(gameCode) ? highestEloNotInMyPot : highestElo;
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
