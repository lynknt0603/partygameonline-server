package com.partygameonline.game.notinmypot;

import com.partygameonline.common.UniqueDisplayNames;
import com.partygameonline.game.core.GameActionFormatException;
import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.GameEngine;
import com.partygameonline.game.core.GameResult;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.RandomSource;
import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.game.notinmypot.application.NotInMyPotRules;
import com.partygameonline.game.notinmypot.application.NotInMyPotRulesEngine;
import com.partygameonline.game.notinmypot.domain.NotInMyPotAction;
import com.partygameonline.game.notinmypot.domain.NotInMyPotActionType;
import com.partygameonline.game.notinmypot.domain.NotInMyPotCard;
import com.partygameonline.game.notinmypot.domain.NotInMyPotEvent;
import com.partygameonline.game.notinmypot.domain.NotInMyPotGameState;
import com.partygameonline.game.notinmypot.domain.NotInMyPotPlayerState;
import com.partygameonline.game.notinmypot.domain.NotInMyPotRole;
import com.partygameonline.game.notinmypot.domain.NotInMyPotSettings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class NotInMyPotGameEngine
        implements GameEngine<NotInMyPotGameState, NotInMyPotAction, NotInMyPotEvent> {

    @Override
    public String gameType() {
        return NotInMyPotGameManifest.ID;
    }

    @Override
    public NotInMyPotGameState createGame(GameConfig config, RandomSource random) {
        int playerCount = config.playerIds().size();
        NotInMyPotRules.validatePlayerCount(playerCount);
        NotInMyPotGameState state = new NotInMyPotGameState(config.roomId());
        state.configure(NotInMyPotSettings.fromRoomSettings(config.settings()));
        state.setTargetScore(NotInMyPotRules.targetScore(playerCount));
        List<NotInMyPotRole> roles = NotInMyPotRules.rolesFor(playerCount);
        random.shuffle(roles);
        Map<String, String> uniqueNames = UniqueDisplayNames.uniquifyAll(
                config.playerIds(),
                config.displayNames()
        );
        for (int seat = 0; seat < config.playerIds().size(); seat++) {
            String playerId = config.playerIds().get(seat);
            state.addPlayer(new NotInMyPotPlayerState(
                    playerId,
                    uniqueNames.getOrDefault(playerId, config.displayName(playerId)),
                    seat,
                    roles.get(seat)
            ));
        }

        List<NotInMyPotCard> deck = NotInMyPotRules.buildDeck(playerCount);
        random.shuffle(deck);
        state.getDrawPile().addAll(deck);
        for (NotInMyPotPlayerState player : state.getPlayers()) {
            for (int card = 0; card < NotInMyPotGameState.HAND_SIZE; card++) {
                player.getHand().add(state.getDrawPile().removeFirst());
            }
        }
        state.setPhase(com.partygameonline.game.notinmypot.domain.NotInMyPotPhase.PLAYING);
        state.setCurrentPlayerId(
                state.getPlayers().get(random.nextInt(state.getPlayers().size())).getPlayerId()
        );
        state.setTurnDeadline(Instant.now().plusSeconds(state.getSettings().turnSeconds()));
        state.addPublicEvent(NotInMyPotEvent.of("NOT_IN_MY_POT_GAME_STARTED", Map.of(
                "playerCount", playerCount,
                "targetScore", NotInMyPotRules.targetScore(playerCount),
                "drawPileCount", state.getDrawPile().size()
        )));
        state.addPublicEvent(NotInMyPotEvent.of("TURN_STARTED", Map.of(
                "playerId", state.getCurrentPlayerId(),
                "turnNumber", state.getTurnNumber()
        )));
        return state;
    }

    @Override
    public NotInMyPotAction decodeAction(Map<String, Object> payload) {
        Object rawType = payload.get("type");
        if (!(rawType instanceof String type) || type.isBlank()) {
            throw new GameActionFormatException("type is required");
        }
        return new NotInMyPotAction(
                type,
                stringValue(payload.get("commandId")),
                integerValue(payload.get("expectedVersion")),
                firstString(payload, "cardId", "cardInstanceId"),
                firstString(payload, "declaredType", "ingredientType"),
                firstString(payload, "actionType", "action"),
                stringValue(payload.get("targetPlayerId")),
                stringList(payload, "cardIds", "orderedCardIds", "returnCardIds")
        );
    }

    @Override
    public ValidationResult validate(
            NotInMyPotGameState state,
            PlayerContext actor,
            NotInMyPotAction action
    ) {
        return NotInMyPotRulesEngine.validate(state, actor.playerId(), action);
    }

    @Override
    public GameResult<NotInMyPotGameState, NotInMyPotEvent> apply(
            NotInMyPotGameState state,
            PlayerContext actor,
            NotInMyPotAction action,
            RandomSource random
    ) {
        List<NotInMyPotEvent> events = NotInMyPotRulesEngine.apply(
                state,
                actor.playerId(),
                action,
                random
        );
        return toResult(state, events);
    }

    @Override
    public GameResult<NotInMyPotGameState, NotInMyPotEvent> onPlayerAbandoned(
            NotInMyPotGameState state,
            PlayerContext player,
            RandomSource random
    ) {
        List<NotInMyPotEvent> events = NotInMyPotRulesEngine.applyAbandon(
                state,
                player.playerId(),
                random
        );
        return toResult(state, events);
    }

    private static GameResult<NotInMyPotGameState, NotInMyPotEvent> toResult(
            NotInMyPotGameState state,
            List<NotInMyPotEvent> events
    ) {
        // Action history is a room setting. Keep the authoritative events in
        // state for server rules, but do not publish them when the host has
        // disabled the activity log (the projected view is filtered too).
        List<NotInMyPotEvent> publishedEvents = state.getSettings().showActionHistory()
                ? events
                : List.of();
        if (state.isFinished()) {
            String winner = state.getWinnerPlayerIds().isEmpty()
                    ? null
                    : state.getWinnerPlayerIds().getFirst();
            return GameResult.finished(state, publishedEvents, winner);
        }
        return GameResult.of(state, publishedEvents);
    }

    private static String firstString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            String value = stringValue(payload.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String stringValue(Object raw) {
        return raw instanceof String value && !value.isBlank() ? value : null;
    }

    private static Integer integerValue(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String value && !value.isBlank()) {
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<String> stringList(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object raw = payload.get(key);
            if (!(raw instanceof List<?> list)) {
                continue;
            }
            List<String> values = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String value && !value.isBlank()) {
                    values.add(value);
                }
            }
            return List.copyOf(values);
        }
        return List.of();
    }
}
