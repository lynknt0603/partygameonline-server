package com.partygameonline.game.notinmypot;

import com.partygameonline.game.core.GameManifest;
import com.partygameonline.game.notinmypot.domain.NotInMyPotGameState;
import org.springframework.stereotype.Component;

@Component
public class NotInMyPotGameManifest implements GameManifest {

    /** Catalogue/room slug used by the existing platform. */
    public static final String ID = "not-in-my-pot";

    /** Human-readable game code used by product/UI documentation. */
    public static final String GAME_CODE = "NOT_IN_MY_POT";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Not In My Pot!";
    }

    @Override
    public int minPlayers() {
        return NotInMyPotGameState.MIN_PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return NotInMyPotGameState.MAX_PLAYERS;
    }

    @Override
    public boolean enabled() {
        return true;
    }
}
