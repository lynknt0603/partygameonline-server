package com.partygameonline.game.nob.domain;

import java.util.List;

public record NobAction(
        String type,
        String commandId,
        Integer expectedVersion,
        String cardInstanceId,
        String cardCode,
        List<String> targetPlayerIds,
        String option,
        String decisionId
) {

    public NobAction(
            String type,
            String commandId,
            Integer expectedVersion,
            String cardInstanceId,
            String cardCode,
            List<String> targetPlayerIds,
            String option
    ) {
        this(type, commandId, expectedVersion, cardInstanceId, cardCode, targetPlayerIds, option, null);
    }

    public static final String DRAFT_PICK = "NOB_DRAFT_PICK";
    public static final String PHASE_SUBMIT = "NOB_PHASE_SUBMIT";
    public static final String CHOOSE_TARGET = "NOB_CHOOSE_TARGET";
    public static final String CHOOSE_OPTION = "NOB_CHOOSE_OPTION";
    public static final String HUNTER_DECISION = "NOB_HUNTER_DECISION";
    public static final String REACTION = "NOB_REACTION";
    public static final String TIMEOUT = "NOB_TIMEOUT";
}
