package com.partygameonline.game.nob.api.dto;

public record NobRoundResultView(
        String result,
        String winningBloodline,
        boolean lastHopeTriggered
) {
}
