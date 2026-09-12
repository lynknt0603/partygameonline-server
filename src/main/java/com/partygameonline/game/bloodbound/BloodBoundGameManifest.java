package com.partygameonline.game.bloodbound;

import com.partygameonline.game.core.GameManifest;
import com.partygameonline.game.bloodbound.domain.BloodBoundGameState;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BloodBoundGameManifest implements GameManifest {

    public static final String ID = "blood-bound";
    public static final String GAME_CODE = "BLOOD_BOUND";
    private final boolean enabled;

    public BloodBoundGameManifest(
            @Value("${games.blood-bound.enabled:false}") boolean enabled
    ) {
        this.enabled = enabled;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Huyết Thệ (Crimson Vow)";
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
        return enabled;
    }

    @Override
    public Map<String, Object> defaultRoomSettings() {
        return Map.of("bloodBound", com.partygameonline.game.bloodbound.domain.BloodBoundSettings.defaults().toMap());
    }

    @Override
    public Map<String, Object> normalizeRoomSettings(Map<String, Object> requested) {
        Object raw = requested == null ? null : requested.get("bloodBound");
        return Map.of("bloodBound", com.partygameonline.game.bloodbound.domain.BloodBoundSettings.fromMap(raw).toMap());
    }
}
