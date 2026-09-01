package com.partygameonline.profile.api.dto;

import com.partygameonline.common.validation.DisplayNameRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDisplayNameRequest(
        @NotBlank
        @Size(max = DisplayNameRules.MAX_LENGTH, message = "must be at most 10 characters")
        String displayName
) {
}
