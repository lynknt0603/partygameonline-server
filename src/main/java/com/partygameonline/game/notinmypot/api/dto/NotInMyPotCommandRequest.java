package com.partygameonline.game.notinmypot.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record NotInMyPotCommandRequest(
        String commandId,
        @NotBlank String type,
        Integer expectedVersion,
        String cardId,
        String declaredType,
        String actionType,
        String targetPlayerId,
        List<String> cardIds
) {
}
