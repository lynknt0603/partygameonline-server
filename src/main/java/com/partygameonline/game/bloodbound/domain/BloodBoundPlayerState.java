package com.partygameonline.game.bloodbound.domain;

import java.util.ArrayList;
import java.util.List;

public class BloodBoundPlayerState {
    private final String playerId;
    private final String displayName;
    private final int seat;
    private BloodClan clan;
    private int rank;
    private int wounds;
    private final List<RevealedToken> revealedTokens = new ArrayList<>();
    private boolean hasRevealedRank;
    private boolean hasUsedAbility;
    private boolean isShielded;
    private boolean connected = true;

    public BloodBoundPlayerState(String playerId, String displayName, int seat) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.seat = seat;
    }

    public BloodBoundPlayerState(String playerId, String displayName, int seat, BloodClan clan, int rank) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.seat = seat;
        this.clan = clan;
        this.rank = rank;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSeat() {
        return seat;
    }

    public BloodClan getClan() {
        return clan;
    }

    public void setClan(BloodClan clan) {
        this.clan = clan;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public int getWounds() {
        return wounds;
    }

    public void setWounds(int wounds) {
        this.wounds = wounds;
    }

    public void addWound() {
        this.wounds++;
    }

    public void healWound() {
        if (this.wounds > 0) {
            this.wounds--;
        }
    }

    public List<RevealedToken> getRevealedTokens() {
        return revealedTokens;
    }

    public void addRevealedToken(RevealedToken token) {
        this.revealedTokens.add(token);
    }

    public boolean isHasRevealedRank() {
        return hasRevealedRank;
    }

    public void setHasRevealedRank(boolean hasRevealedRank) {
        this.hasRevealedRank = hasRevealedRank;
    }

    public boolean isHasUsedAbility() {
        return hasUsedAbility;
    }

    public void setHasUsedAbility(boolean hasUsedAbility) {
        this.hasUsedAbility = hasUsedAbility;
    }

    public boolean isShielded() {
        return isShielded;
    }

    public void setShielded(boolean shielded) {
        isShielded = shielded;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
}
