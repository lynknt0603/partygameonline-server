package com.partygameonline.session.api.dto;

import com.partygameonline.common.validation.DisplayNameRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGuestSessionRequest(
        @NotBlank
        @Size(min = 1, max = DisplayNameRules.MAX_LENGTH, message = "must be at most 10 characters")
        String displayName
) {
}
