package com.partygameonline.game.nob.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record NobTimingSettings(
        int draftPickSeconds,
        int phaseSubmitSeconds,
        int targetDecisionSeconds,
        int optionDecisionSeconds,
        int hunterDecisionSeconds,
        int reactionDecisionSeconds,
        int resolutionCardDisplayMs,
        int announcementDisplayMs,
        int roundSummarySeconds
) {

    public static NobTimingSettings defaults() {
        return new NobTimingSettings(30, 30, 30, 30, 30, 10, 2500, 3000, 30);
    }

    public NobTimingSettings {
        draftPickSeconds = clamp(draftPickSeconds, 10, 120, 30);
        phaseSubmitSeconds = clamp(phaseSubmitSeconds, 10, 120, 30);
        targetDecisionSeconds = clamp(targetDecisionSeconds, 10, 120, 30);
        optionDecisionSeconds = clamp(optionDecisionSeconds, 10, 120, 30);
        hunterDecisionSeconds = clamp(hunterDecisionSeconds, 10, 120, 30);
        reactionDecisionSeconds = clamp(reactionDecisionSeconds, 5, 30, 10);
        resolutionCardDisplayMs = clamp(resolutionCardDisplayMs, 500, 10_000, 2500);
        announcementDisplayMs = clamp(announcementDisplayMs, 500, 15_000, 3000);
        roundSummarySeconds = clamp(roundSummarySeconds, 10, 120, 30);
    }

    public int secondsFor(NobDecisionType type) {
        return switch (type) {
            case CHOOSE_TARGET, CHOOSE_HIDDEN_CARD -> targetDecisionSeconds;
            case HUNTER_DECISION -> hunterDecisionSeconds;
            case REACTION -> reactionDecisionSeconds;
            case MOON_MARK_PICK -> roundSummarySeconds;
            case SHAPE_SWAP, UNMASK_REVEAL, ECHO_CHOOSE, MOON_BROKER, CHOOSE_MOON_TOKEN, CHOOSE_OPTION -> optionDecisionSeconds;
            default -> optionDecisionSeconds;
        };
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("draftPickSeconds", draftPickSeconds);
        map.put("phaseSubmitSeconds", phaseSubmitSeconds);
        map.put("targetDecisionSeconds", targetDecisionSeconds);
        map.put("optionDecisionSeconds", optionDecisionSeconds);
        map.put("hunterDecisionSeconds", hunterDecisionSeconds);
        map.put("reactionDecisionSeconds", reactionDecisionSeconds);
        map.put("resolutionCardDisplayMs", resolutionCardDisplayMs);
        map.put("announcementDisplayMs", announcementDisplayMs);
        map.put("roundSummarySeconds", roundSummarySeconds);
        return map;
    }

    public static NobTimingSettings fromMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return defaults();
        }
        return new NobTimingSettings(
                intVal(map, "draftPickSeconds", 30),
                intVal(map, "phaseSubmitSeconds", 30),
                intVal(map, "targetDecisionSeconds", 30),
                intVal(map, "optionDecisionSeconds", 30),
                intVal(map, "hunterDecisionSeconds", 30),
                intVal(map, "reactionDecisionSeconds", 10),
                intVal(map, "resolutionCardDisplayMs", 2500),
                intVal(map, "announcementDisplayMs", 3000),
                intVal(map, "roundSummarySeconds", 30)
        );
    }

    public static NobTimingSettings fromRoomSettings(Map<String, Object> roomSettings) {
        if (roomSettings == null) {
            return defaults();
        }
        return fromMap(roomSettings.get("nob"));
    }

    private static int intVal(Map<?, ?> map, String key, int fallback) {
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

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }
}
