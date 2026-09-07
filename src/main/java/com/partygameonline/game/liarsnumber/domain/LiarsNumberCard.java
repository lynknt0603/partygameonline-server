package com.partygameonline.game.liarsnumber.domain;

public record LiarsNumberCard(
        String cardId,
        int typeId,
        LiarsNumberCardVariant variant
) {
    public LiarsNumberCard {
        if (cardId == null || cardId.isBlank()) {
            throw new IllegalArgumentException("cardId is required");
        }
        if (typeId < 1 || typeId > 8) {
            throw new IllegalArgumentException("typeId must be between 1 and 8");
        }
        if (variant == null) {
            throw new IllegalArgumentException("variant is required");
        }
    }

    public int penaltyWeight() {
        return variant == LiarsNumberCardVariant.ROMAN ? 2 : 1;
    }

    public String label() {
        return variant == LiarsNumberCardVariant.ROMAN ? roman(typeId) : String.valueOf(typeId);
    }

    public static String roman(int typeId) {
        return switch (typeId) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            default -> throw new IllegalArgumentException("typeId must be between 1 and 8");
        };
    }
}
