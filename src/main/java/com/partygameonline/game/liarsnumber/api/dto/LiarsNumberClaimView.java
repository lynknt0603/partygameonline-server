package com.partygameonline.game.liarsnumber.api.dto;

public record LiarsNumberClaimView(
        String senderId,
        String receiverId,
        int declaredType
) {
}
