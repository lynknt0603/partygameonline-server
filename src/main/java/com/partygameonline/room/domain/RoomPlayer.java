package com.partygameonline.room.domain;

public class RoomPlayer {

    private final String playerId;
    private String displayName;
    private PlayerLobbyState state;

    public RoomPlayer(String playerId, String displayName, PlayerLobbyState state) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.state = state;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public PlayerLobbyState getState() {
        return state;
    }

    public void setState(PlayerLobbyState state) {
        this.state = state;
    }

    public boolean isReady() {
        return state == PlayerLobbyState.READY;
    }
}
