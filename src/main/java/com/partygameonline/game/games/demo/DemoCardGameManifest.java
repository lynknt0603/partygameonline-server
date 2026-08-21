package com.partygameonline.game.games.demo;

import com.partygameonline.game.core.GameManifest;
import org.springframework.stereotype.Component;

@Component
public class DemoCardGameManifest implements GameManifest {

    public static final String ID = "demo-card-game";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Demo Card Game";
    }

    @Override
    public int minPlayers() {
        return 2;
    }

    @Override
    public int maxPlayers() {
        return 2;
    }

    @Override
    public boolean enabled() {
        return true;
    }
}
