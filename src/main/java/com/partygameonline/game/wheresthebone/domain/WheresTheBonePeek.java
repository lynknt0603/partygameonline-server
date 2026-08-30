package com.partygameonline.game.wheresthebone.domain;

import java.util.List;

public record WheresTheBonePeek(String targetPlayerId, List<Integer> wakeHours) {
    public WheresTheBonePeek {
        wakeHours = List.copyOf(wakeHours == null ? List.of() : wakeHours);
    }
}
