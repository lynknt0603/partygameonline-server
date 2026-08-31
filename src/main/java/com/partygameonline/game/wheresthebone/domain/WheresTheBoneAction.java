package com.partygameonline.game.wheresthebone.domain;

import java.util.List;

public record WheresTheBoneAction(
        WheresTheBoneActionType type,
        String commandId,
        Integer expectedVersion,
        Integer hour,
        String targetPlayerId,
        List<String> targetPlayerIds,
        Boolean agree
) {
    public WheresTheBoneAction {
        targetPlayerIds = targetPlayerIds == null ? List.of() : List.copyOf(targetPlayerIds);
    }
}
