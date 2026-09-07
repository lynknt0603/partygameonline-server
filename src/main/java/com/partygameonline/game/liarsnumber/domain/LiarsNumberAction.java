package com.partygameonline.game.liarsnumber.domain;

public record LiarsNumberAction(
        String type,
        String commandId,
        String cardId,
        Integer declaredType,
        String guess,
        String targetPlayerId
) {
}
