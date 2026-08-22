package com.partygameonline.realtime;

public final class WsMessageTypes {

    public static final String CONNECTED = "CONNECTED";
    public static final String ERROR = "ERROR";
    public static final String RESYNC_REQUIRED = "RESYNC_REQUIRED";

    public static final String ROOM_SNAPSHOT = "ROOM_SNAPSHOT";
    public static final String ROOM_CHAT = "ROOM_CHAT";
    public static final String PLAYER_JOINED = "PLAYER_JOINED";
    public static final String PLAYER_LEFT = "PLAYER_LEFT";
    public static final String PLAYER_READY_CHANGED = "PLAYER_READY_CHANGED";
    public static final String PLAYER_DISCONNECTED = "PLAYER_DISCONNECTED";
    public static final String PLAYER_RECONNECTED = "PLAYER_RECONNECTED";
    public static final String ROOM_SETTINGS_CHANGED = "ROOM_SETTINGS_CHANGED";
    public static final String ROOM_CLOSED = "ROOM_CLOSED";

    public static final String GAME_STARTED = "GAME_STARTED";
    public static final String GAME_ACTION = "GAME_ACTION";
    public static final String GAME_EVENTS = "GAME_EVENTS";
    public static final String GAME_SNAPSHOT = "GAME_SNAPSHOT";
    public static final String ACTION_REJECTED = "ACTION_REJECTED";
    public static final String GAME_FINISHED = "GAME_FINISHED";

    private WsMessageTypes() {
    }
}
