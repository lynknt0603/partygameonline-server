package com.partygameonline.game.notinmypot;

import com.partygameonline.game.core.GameManifest;
import com.partygameonline.game.notinmypot.domain.NotInMyPotGameState;
import com.partygameonline.game.notinmypot.domain.NotInMyPotSettings;
import java.util.Map;
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

    @Override
    public Map<String, Object> defaultRoomSettings() {
        return Map.of("notInMyPot", NotInMyPotSettings.defaults().toMap());
    }

    @Override
    public Map<String, Object> normalizeRoomSettings(Map<String, Object> requested) {
        return Map.of("notInMyPot", NotInMyPotSettings.fromMap(rawSettings(requested, "notInMyPot")).toMap());
    }

    private static Object rawSettings(Map<String, Object> requested, String key) {
        if (requested == null) {
            return null;
        }
        return requested.containsKey(key) ? requested.get(key) : requested;
    }
}
