package com.partygameonline.game.wheresthebone;

import com.partygameonline.game.core.GameManifest;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneGameState;
import com.partygameonline.game.wheresthebone.domain.WheresTheBoneSettings;
import java.util.Map;
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

    @Override
    public Map<String, Object> defaultRoomSettings() {
        return Map.of("wheresTheBone", WheresTheBoneSettings.defaults().toMap());
    }

    @Override
    public Map<String, Object> normalizeRoomSettings(Map<String, Object> requested) {
        return Map.of("wheresTheBone", WheresTheBoneSettings.fromMap(rawSettings(requested, "wheresTheBone")).toMap());
    }

    @Override
    public int requiredPlayers(int roomMaxPlayers) {
        return roomMaxPlayers;
    }

    private static Object rawSettings(Map<String, Object> requested, String key) {
        if (requested == null) {
            return null;
        }
        return requested.containsKey(key) ? requested.get(key) : requested;
    }
}
