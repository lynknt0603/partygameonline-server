package com.partygameonline.game.bloodbound.api.dto;

import com.partygameonline.game.bloodbound.domain.RevealedToken;
import java.util.List;

public record BloodBoundPublicPlayerView(
        String playerId,
        String displayName,
        int seatIndex,
        int wounds,
        List<RevealedToken> revealedTokens,
        boolean hasRevealedRank,
        boolean hasUsedAbility,
        boolean isShielded,
        boolean isDaggerHolder,
        boolean connected
) {}
