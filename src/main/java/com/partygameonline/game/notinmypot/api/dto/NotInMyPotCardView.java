package com.partygameonline.game.notinmypot.api.dto;

public record NotInMyPotCardView(
        String cardId,
        String category,
        String type,
        Integer score
) {
}
