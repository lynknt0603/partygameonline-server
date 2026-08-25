package com.partygameonline.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthRequest(
        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "must contain only letters, numbers, or underscore")
        String username,

        @NotBlank
        @Size(min = 3, max = 128)
        String password
) {
}
