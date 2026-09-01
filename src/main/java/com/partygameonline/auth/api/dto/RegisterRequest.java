package com.partygameonline.auth.api.dto;

import com.partygameonline.common.validation.DisplayNameRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "must contain only letters, numbers, or underscore")
        String username,

        @NotBlank
        @Size(min = 3, max = 128)
        String password,

        @NotBlank
        @Size(max = DisplayNameRules.MAX_LENGTH, message = "must be at most 10 characters")
        String displayName
) {
}
