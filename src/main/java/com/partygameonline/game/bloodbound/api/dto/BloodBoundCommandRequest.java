package com.partygameonline.game.bloodbound.api.dto;

import com.partygameonline.game.bloodbound.domain.BloodBoundAction;
import com.partygameonline.game.bloodbound.domain.BloodBoundActionType;
import com.partygameonline.game.bloodbound.domain.ClueTokenType;

public record BloodBoundCommandRequest(
        String commandId,
        BloodBoundActionType type,
        String targetPlayerId,
        ClueTokenType tokenType,
        Integer roleRank,
        String abilityTargetPlayerId
) {
    public BloodBoundAction toAction() {
        return new BloodBoundAction(
                commandId,
                type,
                targetPlayerId,
                tokenType,
                roleRank,
                abilityTargetPlayerId
        );
    }
}
