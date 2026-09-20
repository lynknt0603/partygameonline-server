package com.partygameonline.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank
        @Size(max = 128)
        String currentPassword,

        @NotBlank
        @Size(min = 3, max = 128)
        String newPassword
) {
}
