package com.partygameonline.game.nob.domain;

import com.partygameonline.game.nob.catalog.NobCardDef;
import java.util.UUID;

public record NobCardInstance(
        String instanceId,
        String cardCode,
        NobRoleType roleType,
        Integer number,
        NobEffectCode effectCode
) {

    public static NobCardInstance from(NobCardDef def) {
        return new NobCardInstance(UUID.randomUUID().toString(), def.cardCode(), def.roleType(), def.number(), def.effectCode());
    }

    public boolean matchesPhase(NobPhase phase) {
        return switch (phase) {
            case SHADOW_STALKER -> roleType == NobRoleType.SHADOW_STALKER;
            case BLOOD_SEER -> roleType == NobRoleType.BLOOD_SEER;
            case SHAPESHIFTER -> roleType == NobRoleType.SHAPESHIFTER;
            case FERAL_KILLER -> roleType == NobRoleType.FERAL_KILLER;
            case HUNTER -> roleType == NobRoleType.HUNTER;
            default -> false;
        };
    }
}
