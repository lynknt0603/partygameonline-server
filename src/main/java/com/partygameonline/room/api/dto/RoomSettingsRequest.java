package com.partygameonline.room.api.dto;

import java.util.Map;

public record RoomSettingsRequest(
        Map<String, Object> nob,
        Map<String, Object> notInMyPot,
        Boolean locked
) {

    public RoomSettingsRequest(Map<String, Object> nob) {
        this(nob, Map.of(), null);
    }

    public RoomSettingsRequest(Map<String, Object> nob, Map<String, Object> notInMyPot) {
        this(nob, notInMyPot, null);
    }
}
