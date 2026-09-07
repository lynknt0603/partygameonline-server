package com.partygameonline.game.liarsnumber.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record LiarsNumberEvent(String type, Map<String, Object> payload) {

    public LiarsNumberEvent {
        payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
    }

    public static LiarsNumberEvent of(String type) {
        return new LiarsNumberEvent(type, Map.of());
    }

    public static LiarsNumberEvent of(String type, Map<String, Object> payload) {
        return new LiarsNumberEvent(type, payload);
    }
}
