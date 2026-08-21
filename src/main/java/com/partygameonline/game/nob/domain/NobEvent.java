package com.partygameonline.game.nob.domain;

import java.util.Map;

public record NobEvent(String type, Map<String, Object> payload) {

    public static NobEvent of(String type) {
        return new NobEvent(type, Map.of());
    }

    public static NobEvent of(String type, Map<String, Object> payload) {
        return new NobEvent(type, payload);
    }
}
