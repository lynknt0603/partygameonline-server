package com.partygameonline.history.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_players")
public class MatchPlayerEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "player_id", nullable = false, length = 64)
    private String playerId;

    @Column(name = "display_name", nullable = false, length = 32)
    private String displayName;

    @Column(name = "seat")
    private Short seat;

    @Column(length = 16)
    private String result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MatchPlayerEntity() {
    }

    public MatchPlayerEntity(
            UUID id,
            UUID matchId,
            UUID userId,
            String playerId,
            String displayName,
            Short seat,
            Instant createdAt
    ) {
        this(id, matchId, userId, playerId, displayName, seat, null, createdAt);
    }

    public MatchPlayerEntity(
            UUID id,
            UUID matchId,
            UUID userId,
            String playerId,
            String displayName,
            Short seat,
            String result,
            Instant createdAt
    ) {
        this.id = id;
        this.matchId = matchId;
        this.userId = userId;
        this.playerId = playerId;
        this.displayName = displayName;
        this.seat = seat;
        this.result = result;
        this.createdAt = createdAt;
    }

    public static MatchPlayerEntity newPlayer(
            UUID matchId,
            UUID userId,
            String playerId,
            String displayName,
            Integer seat
    ) {
        return newPlayer(matchId, userId, playerId, displayName, seat, null);
    }

    public static MatchPlayerEntity newPlayer(
            UUID matchId,
            UUID userId,
            String playerId,
            String displayName,
            Integer seat,
            String result
    ) {
        return new MatchPlayerEntity(
                UUID.randomUUID(),
                matchId,
                userId,
                playerId,
                displayName,
                seat == null ? null : seat.shortValue(),
                result,
                Instant.now()
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Short getSeat() {
        return seat;
    }

    public String getResult() {
        return result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
