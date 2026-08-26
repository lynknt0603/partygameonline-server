package com.partygameonline.game.notinmypot.domain;

import java.util.Locale;

public enum NotInMyPotActionType {
    OUT_OF_HOUSE,
    SCOOP_OUT,
    SLOTTED_SPOON,
    EMERGENCY_SHOPPING,
    TRASH_OUT;

    public static NotInMyPotActionType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean requiresTarget() {
        return this == OUT_OF_HOUSE || this == TRASH_OUT;
    }
}
