package com.partygameonline.game.liarsnumber.domain;

import java.util.Map;

/** Room settings captured when a Liar's Number match starts. */
public record LiarsNumberSettings(int turnSeconds) {

    public static final int DEFAULT_TURN_SECONDS = 0;
    public static final int MIN_TIMED_TURN_SECONDS = 5;
    public static final int MAX_TURN_SECONDS = 60;

    public LiarsNumberSettings {
        turnSeconds = validTurnSeconds(turnSeconds) ? turnSeconds : DEFAULT_TURN_SECONDS;
    }

    public static LiarsNumberSettings defaults() {
        return new LiarsNumberSettings(DEFAULT_TURN_SECONDS);
    }

    public Map<String, Object> toMap() {
        return Map.of("turnSeconds", turnSeconds);
    }

    public static LiarsNumberSettings fromRoomSettings(Map<String, Object> roomSettings) {
        if (roomSettings == null) {
            return defaults();
        }
        return fromMap(roomSettings.get("liarsNumber"));
    }

    public static LiarsNumberSettings fromMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return defaults();
        }
        return new LiarsNumberSettings(intValue(map.get("turnSeconds")));
    }

    private static boolean validTurnSeconds(int value) {
        return value == DEFAULT_TURN_SECONDS
                || (value >= MIN_TIMED_TURN_SECONDS && value <= MAX_TURN_SECONDS && value % 5 == 0);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return DEFAULT_TURN_SECONDS;
            }
        }
        return DEFAULT_TURN_SECONDS;
    }
}
