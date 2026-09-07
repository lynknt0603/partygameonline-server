package com.partygameonline.game.liarsnumber.domain;

public record LiarsNumberClaim(
        String senderId,
        String receiverId,
        int declaredType
) {
}
