package com.partygameonline.game.wheresthebone.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Room settings captured when a Where's the Bone match starts. */
public record WheresTheBoneSettings(
        int nightSeconds,
        int packSelectionSeconds,
        boolean showActionHistory,
        boolean whiteDogEnabled
) {

    public static final int DEFAULT_NIGHT_SECONDS = 10;
    public static final int DEFAULT_PACK_SELECTION_SECONDS = 10;
    public static final int MIN_SECONDS = 5;
    public static final int MAX_SECONDS = 120;

    public static WheresTheBoneSettings defaults() {
        return new WheresTheBoneSettings(DEFAULT_NIGHT_SECONDS, DEFAULT_PACK_SELECTION_SECONDS, true, false);
    }

    public WheresTheBoneSettings {
        nightSeconds = validSeconds(nightSeconds) ? nightSeconds : DEFAULT_NIGHT_SECONDS;
        packSelectionSeconds = validSeconds(packSelectionSeconds)
                ? packSelectionSeconds
                : DEFAULT_PACK_SELECTION_SECONDS;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nightSeconds", nightSeconds);
        result.put("packSelectionSeconds", packSelectionSeconds);
        result.put("showActionHistory", showActionHistory);
        result.put("whiteDogEnabled", whiteDogEnabled);
        return result;
    }

    public static WheresTheBoneSettings fromRoomSettings(Map<String, Object> roomSettings) {
        if (roomSettings == null) {
            return defaults();
        }
        return fromMap(roomSettings.get("wheresTheBone"));
    }

    public static WheresTheBoneSettings fromMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return defaults();
        }
        return new WheresTheBoneSettings(
                intValue(map, "nightSeconds", DEFAULT_NIGHT_SECONDS),
                intValue(map, "packSelectionSeconds", DEFAULT_PACK_SELECTION_SECONDS),
                booleanValue(map, "showActionHistory", true),
                booleanValue(map, "whiteDogEnabled", false)
        );
    }

    private static boolean validSeconds(int value) {
        return value >= MIN_SECONDS && value <= MAX_SECONDS;
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
        if (value instanceof Boolean bool) {
            return bool;
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
