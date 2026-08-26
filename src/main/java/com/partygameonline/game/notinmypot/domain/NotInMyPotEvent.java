package com.partygameonline.game.notinmypot.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record NotInMyPotEvent(String type, Map<String, Object> payload) {

    public NotInMyPotEvent {
        payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
    }

    public static NotInMyPotEvent of(String type) {
        return new NotInMyPotEvent(type, Map.of());
    }

    public static NotInMyPotEvent of(String type, Map<String, Object> payload) {
        return new NotInMyPotEvent(type, payload);
    }
}
