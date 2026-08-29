package com.partygameonline.game.notinmypot.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Room-owned settings captured when a Not In My Pot match starts. */
public record NotInMyPotSettings(
        int turnSeconds,
        boolean showActionHistory
) {

    public static final int DEFAULT_TURN_SECONDS = 30;
    public static final int MIN_TURN_SECONDS = 10;
    public static final int MAX_TURN_SECONDS = 120;

    public static NotInMyPotSettings defaults() {
        return new NotInMyPotSettings(DEFAULT_TURN_SECONDS, true);
    }

    public NotInMyPotSettings {
        turnSeconds = validSeconds(turnSeconds) ? turnSeconds : DEFAULT_TURN_SECONDS;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("turnSeconds", turnSeconds);
        map.put("showActionHistory", showActionHistory);
        return map;
    }

    public static NotInMyPotSettings fromMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return defaults();
        }
        return new NotInMyPotSettings(
                intValue(map, "turnSeconds", DEFAULT_TURN_SECONDS),
                booleanValue(map, "showActionHistory", true)
        );
    }

    public static NotInMyPotSettings fromRoomSettings(Map<String, Object> roomSettings) {
        if (roomSettings == null) {
            return defaults();
        }
        Object raw = roomSettings.get("notInMyPot");
        if (raw == null) {
            raw = roomSettings.get("not-in-my-pot");
        }
        return fromMap(raw);
    }

    private static boolean validSeconds(int value) {
        return value >= MIN_TURN_SECONDS && value <= MAX_TURN_SECONDS;
    }

    private static int intValue(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean booleanValue(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text.trim())) {
                return true;
            }
            if ("false".equalsIgnoreCase(text.trim())) {
                return false;
            }
        }
        return fallback;
    }
}
