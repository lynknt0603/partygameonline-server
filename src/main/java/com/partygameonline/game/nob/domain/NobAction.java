package com.partygameonline.game.nob.domain;

import java.util.ArrayList;
import java.util.List;

public record NobAction(
        String type,
        String commandId,
        Integer expectedVersion,
        String cardInstanceId,
        String cardCode,
        List<String> targetPlayerIds,
        String option,
        String decisionId,
        List<String> cardInstanceIds
) {

    public NobAction {
        targetPlayerIds = targetPlayerIds == null ? List.of() : List.copyOf(targetPlayerIds);
        cardInstanceIds = cardInstanceIds == null ? List.of() : List.copyOf(cardInstanceIds);
    }

    public NobAction(
            String type,
            String commandId,
            Integer expectedVersion,
            String cardInstanceId,
            String cardCode,
            List<String> targetPlayerIds,
            String option
    ) {
        this(type, commandId, expectedVersion, cardInstanceId, cardCode, targetPlayerIds, option, null, List.of());
    }

    public NobAction(
            String type,
            String commandId,
            Integer expectedVersion,
            String cardInstanceId,
            String cardCode,
            List<String> targetPlayerIds,
            String option,
            String decisionId
    ) {
        this(type, commandId, expectedVersion, cardInstanceId, cardCode, targetPlayerIds, option, decisionId, List.of());
    }

    public List<String> resolvedCardInstanceIds() {
        List<String> ids = new ArrayList<>();
        if (cardInstanceIds != null) {
            for (String id : cardInstanceIds) {
                if (id != null && !id.isBlank() && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        if (cardInstanceId != null && !cardInstanceId.isBlank() && !ids.contains(cardInstanceId)) {
            ids.add(0, cardInstanceId);
        }
        return List.copyOf(ids);
    }

    public boolean playAllMatching() {
        return "PLAY_BOTH".equalsIgnoreCase(option) || "PLAY_ALL".equalsIgnoreCase(option);
    }

    public static final String DRAFT_PICK = "NOB_DRAFT_PICK";
    public static final String PHASE_SUBMIT = "NOB_PHASE_SUBMIT";
    public static final String CHOOSE_TARGET = "NOB_CHOOSE_TARGET";
    public static final String CHOOSE_OPTION = "NOB_CHOOSE_OPTION";
    public static final String HUNTER_DECISION = "NOB_HUNTER_DECISION";
    public static final String REACTION = "NOB_REACTION";
    public static final String TIMEOUT = "NOB_TIMEOUT";
}
