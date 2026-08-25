package com.partygameonline.game.nob.infrastructure;

import com.partygameonline.game.nob.domain.NobCompletedRound;
import com.partygameonline.game.nob.domain.NobRoundPlayerSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "nob_game_rounds")
public class NobGameRoundEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "game_id", nullable = false, updatable = false)
    private UUID gameId;

    @Column(name = "round_number", nullable = false, updatable = false)
    private Integer roundNumber;

    @Column(name = "player_id", nullable = false, length = 64, updatable = false)
    private String playerId;

    @Column(name = "bloodline", length = 32, updatable = false)
    private String bloodline;

    @Column(name = "result", length = 16, updatable = false)
    private String result;

    @Column(name = "round_result", length = 32, updatable = false)
    private String roundResult;

    @Column(name = "last_hope_triggered", nullable = false, updatable = false)
    private boolean lastHopeTriggered;

    @Column(name = "score", updatable = false)
    private Integer score;

    @Column(name = "elo_delta", updatable = false)
    private Integer eloDelta;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NobGameRoundEntity() {
    }

    public NobGameRoundEntity(
            UUID id,
            UUID gameId,
            Integer roundNumber,
            String playerId,
            String bloodline,
            String result,
            String roundResult,
            boolean lastHopeTriggered,
            Integer score,
            Integer eloDelta,
            Instant createdAt
    ) {
        this.id = id;
        this.gameId = gameId;
        this.roundNumber = roundNumber;
        this.playerId = playerId;
        this.bloodline = bloodline;
        this.result = result;
        this.roundResult = roundResult;
        this.lastHopeTriggered = lastHopeTriggered;
        this.score = score;
        this.eloDelta = eloDelta;
        this.createdAt = createdAt;
    }

    public NobGameRoundEntity(
            UUID id,
            UUID gameId,
            Integer roundNumber,
            String playerId,
            String bloodline,
            String result,
            String roundResult,
            boolean lastHopeTriggered,
            Integer score,
            Instant createdAt
    ) {
        this(
                id,
                gameId,
                roundNumber,
                playerId,
                bloodline,
                result,
                roundResult,
                lastHopeTriggered,
                score,
                null,
                createdAt
        );
    }

    public static NobGameRoundEntity from(
            UUID gameId,
            NobCompletedRound round,
            NobRoundPlayerSnapshot player
    ) {
        return new NobGameRoundEntity(
                UUID.randomUUID(),
                gameId,
                round.roundNumber(),
                player.playerId(),
                player.bloodline(),
                player.result(),
                round.roundResult() == null ? null : round.roundResult().result(),
                round.roundResult() != null && round.roundResult().lastHopeTriggered(),
                player.score(),
                player.eloDelta(),
                Instant.now()
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getGameId() {
        return gameId;
    }

    public Integer getRoundNumber() {
        return roundNumber;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getBloodline() {
        return bloodline;
    }

    public String getResult() {
        return result;
    }

    public String getRoundResult() {
        return roundResult;
    }

    public boolean isLastHopeTriggered() {
        return lastHopeTriggered;
    }

    public Integer getScore() {
        return score;
    }

    public Integer getEloDelta() {
        return eloDelta;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
