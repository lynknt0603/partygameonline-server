package com.partygameonline.game.nob.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record NobCommandRequest(
        String commandId,
        @NotBlank String type,
        Integer expectedVersion,
        String cardInstanceId,
        String cardCode,
        List<String> targetPlayerIds,
        String targetPlayerId,
        String option
) {
}
