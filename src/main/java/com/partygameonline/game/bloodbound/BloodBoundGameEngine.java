package com.partygameonline.game.bloodbound;

import com.partygameonline.common.UniqueDisplayNames;
import com.partygameonline.game.core.GameActionFormatException;
import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.GameEngine;
import com.partygameonline.game.core.GameResult;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.RandomSource;
import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.game.bloodbound.application.BloodBoundRulesEngine;
import com.partygameonline.game.bloodbound.domain.BloodBoundAction;
import com.partygameonline.game.bloodbound.domain.BloodBoundActionType;
import com.partygameonline.game.bloodbound.domain.BloodBoundEvent;
import com.partygameonline.game.bloodbound.domain.BloodBoundGameState;
import com.partygameonline.game.bloodbound.domain.BloodBoundPhase;
import com.partygameonline.game.bloodbound.domain.BloodBoundPlayerState;
import com.partygameonline.game.bloodbound.domain.BloodBoundSettings;
import com.partygameonline.game.bloodbound.domain.BloodClan;
import com.partygameonline.game.bloodbound.domain.ClueTokenType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BloodBoundGameEngine
        implements GameEngine<BloodBoundGameState, BloodBoundAction, BloodBoundEvent> {

    private final BloodBoundRulesEngine rulesEngine;

    public BloodBoundGameEngine(BloodBoundRulesEngine rulesEngine) {
        this.rulesEngine = rulesEngine;
    }

    @Override
    public String gameType() {
        return BloodBoundGameManifest.ID;
    }

    @Override
    public BloodBoundGameState createGame(GameConfig config, RandomSource random) {
        int count = config.playerIds().size();
        if (count < BloodBoundGameState.MIN_PLAYERS || count > BloodBoundGameState.MAX_PLAYERS) {
            throw new IllegalArgumentException("Huyết Thệ requires between "
                    + BloodBoundGameState.MIN_PLAYERS + " and "
                    + BloodBoundGameState.MAX_PLAYERS + " players");
        }

        BloodBoundGameState state = new BloodBoundGameState(config.roomId());
        state.configure(BloodBoundSettings.fromRoomSettings(config.settings()));
        int half = count / 2;

        record Card(BloodClan clan, int rank) {}
        List<Card> deck = new ArrayList<>();
        for (int r = 1; r <= half; r++) {
            deck.add(new Card(BloodClan.ROSE, r));
            deck.add(new Card(BloodClan.FAN, r));
        }
        if (count % 2 == 1) {
            deck.add(new Card(BloodClan.INQUISITOR, 8));
        }

        random.shuffle(deck);

        Map<String, String> uniqueNames = UniqueDisplayNames.uniquifyAll(
                config.playerIds(),
                config.displayNames()
        );

        for (int seat = 0; seat < count; seat++) {
            String playerId = config.playerIds().get(seat);
            String name = uniqueNames.getOrDefault(playerId, config.displayName(playerId));
            Card card = deck.get(seat);
            BloodBoundPlayerState player = new BloodBoundPlayerState(
                    playerId,
                    name,
                    seat,
                    card.clan(),
                    card.rank()
            );
            state.addPlayer(player);
        }

        String startDaggerPlayerId = config.playerIds().getFirst();
        state.setDaggerPlayerId(startDaggerPlayerId);
        state.setPhase(BloodBoundPhase.LOOK_LEFT);

        BloodBoundEvent startEvt = BloodBoundEvent.log(
                "Game started. Inspect your left neighbor's clue.",
                "Trò chơi bắt đầu. Hãy bí mật xem manh mối của người bên trái."
        );
        state.addLog(startEvt);

        return state;
    }

    @Override
    public BloodBoundAction decodeAction(Map<String, Object> payload) {
        if (payload == null) {
            throw new GameActionFormatException("Action payload cannot be null");
        }
        String commandId = (String) payload.get("commandId");
        String rawType = (String) payload.get("type");
        if (rawType == null) {
            throw new GameActionFormatException("Missing action type");
        }
        BloodBoundActionType type;
        String normalizedType = rawType.toUpperCase();
        if ("ACKNOWLEDGE_LOOK_LEFT".equals(normalizedType)) {
            normalizedType = "LOOK_LEFT_ACK";
        } else if ("PASS_INTERVENE".equals(normalizedType)) {
            normalizedType = "PASS_INTERVENTION";
        } else if ("REVEAL_CLUE".equals(normalizedType)) {
            normalizedType = "REVEAL_WOUND_TOKEN";
        }

        try {
            type = BloodBoundActionType.valueOf(normalizedType);
        } catch (IllegalArgumentException e) {
            throw new GameActionFormatException("Unknown action type: " + rawType);
        }

        String targetPlayerId = (String) payload.get("targetPlayerId");

        ClueTokenType tokenType = null;
        String rawTokenType = (String) payload.get("tokenType");
        if (rawTokenType != null) {
            try {
                tokenType = ClueTokenType.valueOf(rawTokenType.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        Integer roleRank = null;
        Object rawRank = payload.get("roleRank");
        if (rawRank instanceof Number n) {
            roleRank = n.intValue();
        }

        String abilityTargetPlayerId = (String) payload.get("abilityTargetPlayerId");
        if (abilityTargetPlayerId == null && type == BloodBoundActionType.USE_ABILITY) {
            abilityTargetPlayerId = targetPlayerId;
        }

        return new BloodBoundAction(
                commandId,
                type,
                targetPlayerId,
                tokenType,
                roleRank,
                abilityTargetPlayerId
        );
    }

    @Override
    public ValidationResult validate(BloodBoundGameState state, PlayerContext actor, BloodBoundAction action) {
        return rulesEngine.validate(state, actor, action);
    }

    @Override
    public GameResult<BloodBoundGameState, BloodBoundEvent> apply(
            BloodBoundGameState state,
            PlayerContext actor,
            BloodBoundAction action,
            RandomSource random
    ) {
        return rulesEngine.apply(state, actor, action, random);
    }

    @Override
    public GameResult<BloodBoundGameState, BloodBoundEvent> onPlayerAbandoned(
            BloodBoundGameState state,
            PlayerContext player,
            RandomSource random
    ) {
        BloodBoundPlayerState abandoned = state.player(player.playerId());
        if (abandoned != null) {
            abandoned.setConnected(false);
        }

        List<BloodBoundEvent> events = new ArrayList<>();
        if (state.getPhase() == BloodBoundPhase.WOUND_ASSIGNMENT) {
            String vicId = state.getIntervenerPlayerId() != null
                    ? state.getIntervenerPlayerId()
                    : state.getTargetPlayerId();
            if (player.playerId().equals(vicId)) {
                GameResult<BloodBoundGameState, BloodBoundEvent> res = rulesEngine.apply(
                        state,
                        player,
                        new BloodBoundAction(null, BloodBoundActionType.REVEAL_WOUND_TOKEN, null, ClueTokenType.RANK, null, null),
                        random
                );
                events.addAll(res.events());
                if (state.getPhase() == BloodBoundPhase.GAME_OVER) {
                    return res;
                }
            }
        }

        BloodBoundPlayerState currentDaggerHolder = state.player(state.getDaggerPlayerId());
        boolean daggerHolderDisconnected = currentDaggerHolder != null && !currentDaggerHolder.isConnected();
        if (state.getPhase() != BloodBoundPhase.GAME_OVER && daggerHolderDisconnected) {
            List<BloodBoundPlayerState> players = state.getPlayers();
            int currentSeat = currentDaggerHolder.getSeat();
            int total = players.size();
            for (int i = 1; i < total; i++) {
                int nextSeat = (currentSeat + i) % total;
                for (BloodBoundPlayerState candidate : players) {
                    if (candidate.getSeat() == nextSeat && candidate.isConnected() && candidate.getWounds() < 4) {
                        state.setDaggerPlayerId(candidate.getPlayerId());
                        state.setPhase(BloodBoundPhase.ATTACK_CHOICE);
                        state.setPhaseDeadline(java.time.Instant.now().plusSeconds(state.getSettings().turnSeconds()));
                        state.setTargetPlayerId(null);
                        state.setIntervenerPlayerId(null);
                        BloodBoundEvent passEvt = BloodBoundEvent.log(
                                currentDaggerHolder.getDisplayName() + " is disconnected. Dagger passed to " + candidate.getDisplayName() + ".",
                                currentDaggerHolder.getDisplayName() + " đã mất kết nối. Đoản Kiếm được chuyển cho " + candidate.getDisplayName() + "."
                        );
                        events.add(passEvt);
                        state.addLog(passEvt);
                        break;
                    }
                }
                if (!currentDaggerHolder.getPlayerId().equals(state.getDaggerPlayerId())) {
                    break;
                }
            }
        }
        state.incrementVersion();
        return GameResult.of(state, events);
    }

    public GameResult<BloodBoundGameState, BloodBoundEvent> checkPhaseTimeout(BloodBoundGameState state, java.time.Instant now) {
        return rulesEngine.checkPhaseTimeout(state, now);
    }
}
