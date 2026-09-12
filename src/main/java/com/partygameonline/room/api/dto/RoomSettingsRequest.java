package com.partygameonline.room.api.dto;

import java.util.Map;

public record RoomSettingsRequest(
        Map<String, Object> nob,
        Map<String, Object> notInMyPot,
        Map<String, Object> wheresTheBone,
        Map<String, Object> liarsNumber,
        Map<String, Object> bloodBound,
        Boolean locked,
        Integer maxPlayers
) {

    public RoomSettingsRequest(Map<String, Object> nob) {
        this(nob, Map.of(), Map.of(), Map.of(), Map.of(), null, null);
    }

    public RoomSettingsRequest(Map<String, Object> nob, Map<String, Object> notInMyPot) {
        this(nob, notInMyPot, Map.of(), Map.of(), Map.of(), null, null);
    }

    public RoomSettingsRequest(Map<String, Object> nob, Map<String, Object> notInMyPot, Boolean locked) {
        this(nob, notInMyPot, Map.of(), Map.of(), Map.of(), locked, null);
    }

    public RoomSettingsRequest(
            Map<String, Object> nob,
            Map<String, Object> notInMyPot,
            Map<String, Object> wheresTheBone,
            Boolean locked
    ) {
        this(nob, notInMyPot, wheresTheBone, Map.of(), Map.of(), locked, null);
    }

    public RoomSettingsRequest(
            Map<String, Object> nob,
            Map<String, Object> notInMyPot,
            Map<String, Object> wheresTheBone,
            Boolean locked,
            Integer maxPlayers
    ) {
        this(nob, notInMyPot, wheresTheBone, Map.of(), Map.of(), locked, maxPlayers);
    }

    public RoomSettingsRequest(
            Map<String, Object> nob,
            Map<String, Object> notInMyPot,
            Map<String, Object> wheresTheBone,
            Map<String, Object> liarsNumber,
            Boolean locked,
            Integer maxPlayers
    ) {
        this(nob, notInMyPot, wheresTheBone, liarsNumber, Map.of(), locked, maxPlayers);
    }
}
