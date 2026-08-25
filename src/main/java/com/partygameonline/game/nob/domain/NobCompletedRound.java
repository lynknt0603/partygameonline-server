package com.partygameonline.game.nob.domain;

import java.util.List;

public record NobCompletedRound(
        int roundNumber,
        NobRoundResult roundResult,
        List<NobRoundPlayerSnapshot> players
) {

    public NobCompletedRound {
        players = players == null ? List.of() : List.copyOf(players);
    }
}
