package com.partygameonline.room.api.dto;

import com.partygameonline.room.domain.RoomVisibility;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank String gameId,
        @NotBlank @Size(min = 1, max = 40) String name,
        @Min(2) @Max(16) Integer maxPlayers,
        RoomVisibility visibility
) {
}
