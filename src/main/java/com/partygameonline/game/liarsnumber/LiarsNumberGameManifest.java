package com.partygameonline.game.liarsnumber;

import com.partygameonline.game.core.GameManifest;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberGameState;
import org.springframework.stereotype.Component;

@Component
public final class LiarsNumberGameManifest implements GameManifest {

    public static final String ID = "liars-number";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Liar’s Number";
    }

    @Override
    public int minPlayers() {
        return LiarsNumberGameState.MIN_PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return LiarsNumberGameState.MAX_PLAYERS;
    }

    @Override
    public boolean enabled() {
        return true;
    }
}
