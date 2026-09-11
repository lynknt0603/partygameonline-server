package com.partygameonline.game.bloodbound.domain;

import java.util.Map;

/** Room settings captured when a Blood Bound (Huyết Thệ) match is configured. */
public record BloodBoundSettings(int turnSeconds, int interventionSeconds) {

    public static final int DEFAULT_TURN_SECONDS = 30;
    public static final int DEFAULT_INTERVENTION_SECONDS = 15;

    public BloodBoundSettings {
        turnSeconds = validTurnSeconds(turnSeconds) ? turnSeconds : DEFAULT_TURN_SECONDS;
        interventionSeconds = validInterventionSeconds(interventionSeconds) ? interventionSeconds : DEFAULT_INTERVENTION_SECONDS;
    }

    public static BloodBoundSettings defaults() {
        return new BloodBoundSettings(DEFAULT_TURN_SECONDS, DEFAULT_INTERVENTION_SECONDS);
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "turnSeconds", turnSeconds,
                "interventionSeconds", interventionSeconds
        );
    }

    public static BloodBoundSettings fromRoomSettings(Map<String, Object> roomSettings) {
        if (roomSettings == null) {
            return defaults();
        }
        return fromMap(roomSettings.get("bloodBound"));
    }

    public static BloodBoundSettings fromMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return defaults();
        }
        return new BloodBoundSettings(
                intValue(map.get("turnSeconds"), DEFAULT_TURN_SECONDS),
                intValue(map.get("interventionSeconds"), DEFAULT_INTERVENTION_SECONDS)
        );
    }

    private static boolean validTurnSeconds(int value) {
        return value == 15 || value == 20 || value == 30 || value == 45 || value == 60;
    }

    private static boolean validInterventionSeconds(int value) {
        return value == 5 || value == 10 || value == 15 || value == 20 || value == 30;
    }

    private static int intValue(Object value, int fallback) {
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
}
