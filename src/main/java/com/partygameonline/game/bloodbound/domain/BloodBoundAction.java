package com.partygameonline.game.bloodbound.domain;

public record BloodBoundAction(
        String commandId,
        BloodBoundActionType type,
        String targetPlayerId,
        ClueTokenType tokenType,
        Integer roleRank,
        String abilityTargetPlayerId
) {
    public static BloodBoundAction of(BloodBoundActionType type) {
        return new BloodBoundAction(null, type, null, null, null, null);
    }

    public static BloodBoundAction attack(String targetPlayerId) {
        return new BloodBoundAction(null, BloodBoundActionType.ATTACK, targetPlayerId, null, null, null);
    }
}
