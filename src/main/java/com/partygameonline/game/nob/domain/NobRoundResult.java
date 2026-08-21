package com.partygameonline.game.nob.domain;

public record NobRoundResult(
        String result,
        String winningBloodline,
        boolean lastHopeTriggered
) {
}
