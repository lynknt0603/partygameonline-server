package com.partygameonline.game.bloodbound.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BloodBoundGameState {
    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 8;

    private final String roomId;
    private int version = 1;
    private BloodBoundPhase phase = BloodBoundPhase.LOOK_LEFT;
    private int roundNumber = 1;

    private String daggerPlayerId;
    private String targetPlayerId;
    private String intervenerPlayerId;

    private BloodClan winnerClan;
    private String capturedPlayerId;

    private final List<BloodBoundPlayerState> players = new ArrayList<>();
    private final List<BloodBoundEvent> logs = new ArrayList<>();
    private Instant phaseDeadline;

    public BloodBoundGameState(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomId() {
        return roomId;
    }

    public int getVersion() {
        return version;
    }

    public void incrementVersion() {
        this.version++;
    }

    public BloodBoundPhase getPhase() {
        return phase;
    }

    public void setPhase(BloodBoundPhase phase) {
        this.phase = phase;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public String getDaggerPlayerId() {
        return daggerPlayerId;
    }

    public void setDaggerPlayerId(String daggerPlayerId) {
        this.daggerPlayerId = daggerPlayerId;
    }

    public String getTargetPlayerId() {
        return targetPlayerId;
    }

    public void setTargetPlayerId(String targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    public String getIntervenerPlayerId() {
        return intervenerPlayerId;
    }

    public void setIntervenerPlayerId(String intervenerPlayerId) {
        this.intervenerPlayerId = intervenerPlayerId;
    }

    public BloodClan getWinnerClan() {
        return winnerClan;
    }

    public void setWinnerClan(BloodClan winnerClan) {
        this.winnerClan = winnerClan;
    }

    public String getCapturedPlayerId() {
        return capturedPlayerId;
    }

    public void setCapturedPlayerId(String capturedPlayerId) {
        this.capturedPlayerId = capturedPlayerId;
    }

    public List<BloodBoundPlayerState> getPlayers() {
        return players;
    }

    public void addPlayer(BloodBoundPlayerState player) {
        this.players.add(player);
    }

    public BloodBoundPlayerState player(String playerId) {
        if (playerId == null) {
            return null;
        }
        for (BloodBoundPlayerState p : players) {
            if (p.getPlayerId().equals(playerId)) {
                return p;
            }
        }
        return null;
    }

    public Optional<BloodBoundPlayerState> findPlayer(String playerId) {
        return Optional.ofNullable(player(playerId));
    }

    public List<BloodBoundEvent> getLogs() {
        return logs;
    }

    public void addLog(BloodBoundEvent log) {
        this.logs.add(log);
    }

    public Instant getPhaseDeadline() {
        return phaseDeadline;
    }

    public void setPhaseDeadline(Instant phaseDeadline) {
        this.phaseDeadline = phaseDeadline;
    }
}
