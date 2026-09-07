package com.partygameonline.game.liarsnumber.api.dto;

import java.util.List;
import java.util.Set;

public record LiarsNumberActiveRoundView(
        LiarsNumberCardView card,
        String originalSenderId,
        String currentSenderId,
        String currentReceiverId,
        Integer declaredType,
        List<LiarsNumberClaimView> history,
        Set<String> seenBy
) {
}
