package com.partygameonline.game.liarsnumber.api.dto;

import java.util.List;
import java.util.Map;

public record LiarsNumberPlayerView(
        String playerId,
        String displayName,
        int seat,
        boolean you,
        int handCount,
        List<LiarsNumberCardView> penaltyCards,
        Map<Integer, Integer> penaltyScores,
        boolean winner,
        boolean loser,
        Integer oldElo,
        Integer eloDelta,
        Integer newElo
) {
}
