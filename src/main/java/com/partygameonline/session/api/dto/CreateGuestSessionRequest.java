package com.partygameonline.session.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGuestSessionRequest(
        @NotBlank @Size(min = 1, max = 32) String displayName
) {
}
