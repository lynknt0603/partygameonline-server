package com.partygameonline.profile.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDisplayNameRequest(
        @NotBlank
        @Size(max = 32)
        String displayName
) {
}
