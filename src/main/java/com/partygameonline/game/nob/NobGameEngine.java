package com.partygameonline.game.nob;

import com.partygameonline.game.core.GameActionFormatException;
import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.GameEngine;
import com.partygameonline.game.core.GameResult;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.RandomSource;
import com.partygameonline.common.UniqueDisplayNames;
import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.game.nob.application.NobRulesEngine;
import com.partygameonline.game.nob.config.NobGameProperties;
import com.partygameonline.game.nob.domain.NobAction;
import com.partygameonline.game.nob.domain.NobEvent;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobPlayerState;
import com.partygameonline.game.nob.domain.NobTimingSettings;
import com.partygameonline.game.nob.infrastructure.NobGameAuditService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NobGameEngine implements GameEngine<NobGameState, NobAction, NobEvent> {

    private final NobGameProperties properties;
    private final NobGameAuditService auditService;

    public NobGameEngine(NobGameProperties properties) {
        this(properties, null);
    }

    @Autowired
    public NobGameEngine(NobGameProperties properties, NobGameAuditService auditService) {
        this.properties = properties;
        this.auditService = auditService;
    }

    @Override
    public String gameType() {
        return NobGameManifest.ID;
    }

    @Override
    public NobGameState createGame(GameConfig config, RandomSource random) {
        int count = config.playerIds().size();
        if (count < NobGameState.MIN_PLAYERS || count > NobGameState.MAX_PLAYERS) {
            throw new IllegalArgumentException("NOB requires 4-11 players");
        }
        NobGameState state = new NobGameState(config.roomId());
        state.configure(properties.targetScore(), NobTimingSettings.fromRoomSettings(config.settings()));
        int seat = 0;
        Map<String, String> uniqueNames = UniqueDisplayNames.uniquifyAll(config.playerIds(), config.displayNames());
        for (String playerId : config.playerIds()) {
            state.getPlayers().add(new NobPlayerState(
                    playerId,
                    uniqueNames.getOrDefault(playerId, config.displayName(playerId)),
                    seat++
            ));
        }
        state.seedMoonMarks(
                properties.moonMarks().value2Count(),
                properties.moonMarks().value3Count(),
                properties.moonMarks().value4Count()
        );
        state.assignBloodlines(random);
        state.dealDraft(random);
        state.log("NOB_GAME_STARTED", "Night of Bloodlines started");
        persist(state, List.of(NobEvent.of("NOB_GAME_STARTED"), NobEvent.of("NOB_ROUND_STARTED")));
        return state;
    }

    @Override
    public NobAction decodeAction(Map<String, Object> payload) {
        Object rawType = payload.get("type");
        if (!(rawType instanceof String type) || type.isBlank()) {
            throw new GameActionFormatException("type is required");
        }
        String commandId = stringValue(payload.get("commandId"));
        Integer expectedVersion = intValue(payload.get("expectedVersion"));
        String cardInstanceId = stringValue(payload.get("cardInstanceId"));
        String cardCode = stringValue(payload.get("cardCode"));
        String option = stringValue(payload.get("option"));
        String decisionId = stringValue(payload.get("decisionId"));
        List<String> targets = new ArrayList<>();
        Object rawTargets = payload.get("targetPlayerIds");
        if (rawTargets instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String id && !id.isBlank()) {
                    targets.add(id);
                }
            }
        }
        String singleTarget = stringValue(payload.get("targetPlayerId"));
        if (singleTarget != null && !targets.contains(singleTarget)) {
            targets.add(singleTarget);
        }
        List<String> cardIds = stringList(payload.get("cardInstanceIds"));
        return new NobAction(
                type,
                commandId,
                expectedVersion,
                cardInstanceId,
                cardCode,
                List.copyOf(targets),
                option,
                decisionId,
                cardIds
        );
    }

    @Override
    public ValidationResult validate(NobGameState state, PlayerContext actor, NobAction action) {
        return NobRulesEngine.validate(state, actor.playerId(), action);
    }

    @Override
    public GameResult<NobGameState, NobEvent> apply(
            NobGameState state,
            PlayerContext actor,
            NobAction action,
            RandomSource random
    ) {
        List<NobEvent> events = NobRulesEngine.apply(state, actor.playerId(), action, random);
        persist(state, events);
        return toResult(state, events);
    }

    @Override
    public GameResult<NobGameState, NobEvent> onPlayerAbandoned(
            NobGameState state,
            PlayerContext player,
            RandomSource random
    ) {
        List<NobEvent> events = NobRulesEngine.applyAbandon(state, player.playerId(), random);
        persist(state, events);
        return toResult(state, events);
    }

    private void persist(NobGameState state, List<NobEvent> events) {
        if (auditService != null) {
            auditService.record(state, events);
        }
    }

    private static GameResult<NobGameState, NobEvent> toResult(NobGameState state, List<NobEvent> events) {
        if (state.isFinished()) {
            String winner = state.getWinnerPlayerIds().isEmpty() ? null : state.getWinnerPlayerIds().getFirst();
            return GameResult.finished(state, events, winner);
        }
        return GameResult.of(state, events);
    }

    private static String stringValue(Object raw) {
        return raw instanceof String value && !value.isBlank() ? value : null;
    }

    private static List<String> stringList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String id && !id.isBlank() && !values.contains(id)) {
                    values.add(id);
                }
            }
        }
        return List.copyOf(values);
    }

    private static Integer intValue(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
