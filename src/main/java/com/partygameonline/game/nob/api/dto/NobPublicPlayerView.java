package com.partygameonline.game.nob.api.dto;

import java.util.List;

public record NobPublicPlayerView(
        String playerId,
        String displayName,
        int seat,
        boolean alive,
        boolean connected,
        boolean you,
        int moonMarkCount,
        Integer score,
        NobBloodlineView publiclyRevealedBloodline,
        List<NobCardView> revealedCards,
        int hiddenCardCount
) {
}
