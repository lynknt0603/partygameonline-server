package com.partygameonline.game.nob.domain;

public record NobObservation(
        String kind,
        String targetPlayerId,
        NobBloodline bloodline,
        String cardCode,
        Integer moonMarkValue
) {
}
