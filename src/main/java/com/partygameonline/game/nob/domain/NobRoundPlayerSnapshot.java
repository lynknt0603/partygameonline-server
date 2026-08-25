package com.partygameonline.game.nob.domain;

public record NobRoundPlayerSnapshot(
        String playerId,
        String bloodline,
        String result,
        Integer score,
        Integer eloDelta
) {

    public NobRoundPlayerSnapshot(
            String playerId,
            String bloodline,
            String result,
            Integer score
    ) {
        this(playerId, bloodline, result, score, null);
    }
}
