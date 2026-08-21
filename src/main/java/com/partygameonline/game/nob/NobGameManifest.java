package com.partygameonline.game.nob;

import com.partygameonline.game.core.GameManifest;
import com.partygameonline.game.nob.domain.NobGameState;
import org.springframework.stereotype.Component;

@Component
public class NobGameManifest implements GameManifest {

    public static final String ID = "night-of-bloodlines";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Night of Bloodlines";
    }

    @Override
    public int minPlayers() {
        return NobGameState.MIN_PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return NobGameState.MAX_PLAYERS;
    }

    @Override
    public boolean enabled() {
        return true;
    }
}
