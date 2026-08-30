package com.partygameonline.game.wheresthebone.api.dto;

import java.util.List;

public record WheresTheBonePlayerView(
        String playerId,
        String displayName,
        int seat,
        boolean connected,
        boolean you,
        String role,
        boolean winner,
        boolean awake,
        boolean voted,
        List<Integer> wakeHours,
        Integer oldElo,
        Integer eloDelta,
        Integer newElo
) {
}
