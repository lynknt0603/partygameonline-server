package com.partygameonline.game.nob;

import com.partygameonline.game.core.GameManifest;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobTimingSettings;
import java.util.Map;
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

    @Override
    public Map<String, Object> defaultRoomSettings() {
        return Map.of("nob", NobTimingSettings.defaults().toMap());
    }

    @Override
    public Map<String, Object> normalizeRoomSettings(Map<String, Object> requested) {
        return Map.of("nob", NobTimingSettings.fromMap(rawSettings(requested, "nob")).toMap());
    }

    private static Object rawSettings(Map<String, Object> requested, String key) {
        if (requested == null) {
            return null;
        }
        return requested.containsKey(key) ? requested.get(key) : requested;
    }
}
