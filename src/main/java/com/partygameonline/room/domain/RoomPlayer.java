package com.partygameonline.room.domain;

import com.partygameonline.common.avatar.AvatarCatalog;

public class RoomPlayer {

    private final String playerId;
    private String displayName;
    private String avatarUrl;
    private PlayerLobbyState state;

    public RoomPlayer(String playerId, String displayName, PlayerLobbyState state) {
        this(playerId, displayName, AvatarCatalog.DEFAULT_URL, state);
    }

    public RoomPlayer(String playerId, String displayName, String avatarUrl, PlayerLobbyState state) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl == null || avatarUrl.isBlank() ? AvatarCatalog.DEFAULT_URL : avatarUrl;
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

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl == null || avatarUrl.isBlank() ? AvatarCatalog.DEFAULT_URL : avatarUrl;
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
