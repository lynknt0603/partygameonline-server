package com.partygameonline.profile.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAvatarRequest(
        @NotBlank String avatarKey
) {
}
