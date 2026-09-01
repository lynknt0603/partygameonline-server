package com.partygameonline.room.api.dto;

import java.util.Map;

public record RoomSettingsRequest(
        Map<String, Object> nob,
        Map<String, Object> notInMyPot,
        Map<String, Object> wheresTheBone,
        Boolean locked,
        Integer maxPlayers
) {

    public RoomSettingsRequest(Map<String, Object> nob) {
        this(nob, Map.of(), Map.of(), null, null);
    }

    public RoomSettingsRequest(Map<String, Object> nob, Map<String, Object> notInMyPot) {
        this(nob, notInMyPot, Map.of(), null, null);
    }

    public RoomSettingsRequest(Map<String, Object> nob, Map<String, Object> notInMyPot, Boolean locked) {
        this(nob, notInMyPot, Map.of(), locked, null);
    }

    public RoomSettingsRequest(
            Map<String, Object> nob,
            Map<String, Object> notInMyPot,
            Map<String, Object> wheresTheBone,
            Boolean locked
    ) {
        this(nob, notInMyPot, wheresTheBone, locked, null);
    }
}
