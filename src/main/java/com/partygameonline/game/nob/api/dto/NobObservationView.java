package com.partygameonline.game.nob.api.dto;

public record NobObservationView(
        String kind,
        String targetPlayerId,
        NobBloodlineView bloodline,
        String cardCode,
        Integer moonMarkValue
) {
}
