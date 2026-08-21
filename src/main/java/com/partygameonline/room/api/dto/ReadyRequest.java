package com.partygameonline.room.api.dto;

import jakarta.validation.constraints.NotNull;

public record ReadyRequest(@NotNull Boolean ready) {
}
