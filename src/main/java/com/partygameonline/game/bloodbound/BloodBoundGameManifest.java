package com.partygameonline.game.bloodbound;

import com.partygameonline.game.core.GameManifest;
import com.partygameonline.game.bloodbound.domain.BloodBoundGameState;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BloodBoundGameManifest implements GameManifest {

    public static final String ID = "blood-bound";
    public static final String GAME_CODE = "BLOOD_BOUND";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Blood Bound";
    }

    @Override
    public int minPlayers() {
        return BloodBoundGameState.MIN_PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return BloodBoundGameState.MAX_PLAYERS;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Map<String, Object> defaultRoomSettings() {
        return Map.of();
    }

    @Override
    public Map<String, Object> normalizeRoomSettings(Map<String, Object> requested) {
        return Map.of();
    }
}
