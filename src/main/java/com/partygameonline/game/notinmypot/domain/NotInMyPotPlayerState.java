package com.partygameonline.game.notinmypot.domain;

import java.util.ArrayList;
import java.util.List;

public class NotInMyPotPlayerState {

    private final String playerId;
    private final String displayName;
    private final int seat;
    private final NotInMyPotRole role;
    private final List<NotInMyPotCard> hand = new ArrayList<>();
    private boolean active = true;
    private boolean connected = true;
    private int doorCount;

    public NotInMyPotPlayerState(
            String playerId,
            String displayName,
            int seat,
            NotInMyPotRole role
    ) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.seat = seat;
        this.role = role;
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

    public NotInMyPotRole getRole() {
        return role;
    }

    public List<NotInMyPotCard> getHand() {
        return hand;
    }

    public NotInMyPotCard findHand(String cardId) {
        if (cardId == null) {
            return null;
        }
        return hand.stream().filter(card -> card.cardId().equals(cardId)).findFirst().orElse(null);
    }

    public boolean isActive() {
        return active;
    }

    public boolean isExpelled() {
        return !active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public int getDoorCount() {
        return doorCount;
    }

    public void setDoorCount(int doorCount) {
        this.doorCount = Math.max(0, doorCount);
    }
}
