package com.partygameonline.game.liarsnumber.api.dto;

public record LiarsNumberCardView(
        String cardId,
        Integer typeId,
        String variant,
        String label,
        boolean faceUp,
        int penaltyWeight
) {
}
