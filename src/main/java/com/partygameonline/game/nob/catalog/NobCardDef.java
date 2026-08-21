package com.partygameonline.game.nob.catalog;

import com.partygameonline.game.nob.domain.NobEffectCode;
import com.partygameonline.game.nob.domain.NobRoleType;

public record NobCardDef(
        String cardCode,
        NobRoleType roleType,
        Integer number,
        NobEffectCode effectCode
) {
}
