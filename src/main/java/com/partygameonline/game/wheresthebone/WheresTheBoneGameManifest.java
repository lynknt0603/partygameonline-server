package com.partygameonline.game.wheresthebone;

import com.partygameonline.game.core.GameManifest;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneGameState;
import org.springframework.stereotype.Component;

@Component
public final class WheresTheBoneGameManifest implements GameManifest {

    public static final String ID = "wheres-the-bone";

    @Override
    public String id() { return ID; }

    @Override
    public String name() { return "Where's the Bone"; }

    @Override
    public int minPlayers() { return WheresTheBoneGameState.MIN_PLAYERS; }

    @Override
    public int maxPlayers() { return WheresTheBoneGameState.MAX_PLAYERS; }

    @Override
    public boolean enabled() { return true; }
}
