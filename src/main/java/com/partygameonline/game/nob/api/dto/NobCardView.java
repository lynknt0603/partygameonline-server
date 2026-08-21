package com.partygameonline.game.nob.api.dto;

public record NobCardView(
        String instanceId,
        String cardCode,
        String roleType,
        Integer number,
        String effectCode
) {
}
