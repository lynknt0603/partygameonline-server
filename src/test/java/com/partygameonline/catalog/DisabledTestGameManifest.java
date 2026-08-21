package com.partygameonline.catalog;

import com.partygameonline.game.core.GameManifest;
import org.springframework.stereotype.Component;

@Component
public class DisabledTestGameManifest implements GameManifest {

    public static final String ID = "disabled-test-game";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Disabled Test Game";
    }

    @Override
    public int minPlayers() {
        return 2;
    }

    @Override
    public int maxPlayers() {
        return 4;
    }

    @Override
    public boolean enabled() {
        return false;
    }
}
