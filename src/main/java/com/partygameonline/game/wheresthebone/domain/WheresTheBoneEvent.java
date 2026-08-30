package com.partygameonline.game.wheresthebone.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record WheresTheBoneEvent(String type, Map<String, Object> payload) {
    public WheresTheBoneEvent {
        payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
    }

    public static WheresTheBoneEvent of(String type, Map<String, Object> payload) {
        return new WheresTheBoneEvent(type, payload);
    }
}
