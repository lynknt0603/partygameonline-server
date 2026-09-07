package com.partygameonline.game.liarsnumber;

import com.partygameonline.game.core.GameManifest;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberGameState;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberSettings;
import java.util.Map;
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

    @Override
    public Map<String, Object> defaultRoomSettings() {
        return Map.of("liarsNumber", LiarsNumberSettings.defaults().toMap());
    }

    @Override
    public Map<String, Object> normalizeRoomSettings(Map<String, Object> requested) {
        Object raw = requested == null ? null : requested.get("liarsNumber");
        return Map.of("liarsNumber", LiarsNumberSettings.fromMap(raw).toMap());
    }
}
