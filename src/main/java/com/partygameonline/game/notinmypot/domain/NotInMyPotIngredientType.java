package com.partygameonline.game.notinmypot.domain;

import java.util.Locale;

public enum NotInMyPotIngredientType {
    VEGETABLE(1),
    SALT(0),
    MEAT(-2);

    private final int score;

    NotInMyPotIngredientType(int score) {
        this.score = score;
    }

    public int score() {
        return score;
    }

    public static NotInMyPotIngredientType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
