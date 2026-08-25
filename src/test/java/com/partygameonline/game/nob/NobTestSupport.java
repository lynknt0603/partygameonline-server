package com.partygameonline.game.nob;

import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.game.nob.application.NobRulesEngine;
import com.partygameonline.game.nob.config.NobGameProperties;
import com.partygameonline.game.nob.domain.NobAction;
import com.partygameonline.game.nob.domain.NobEvent;
import com.partygameonline.game.nob.domain.NobGameState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NobTestSupport {

    private NobTestSupport() {
    }

    static NobGameEngine engine() {
        return new NobGameEngine(NobGameProperties.defaults());
    }

    static GameConfig config(String... playerIds) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String id : playerIds) {
            names.put(id, id.toUpperCase());
        }
        return new GameConfig(NobGameManifest.ID, "ROOM", List.of(playerIds), names, 1L);
    }

    static NobGameState fourPlayers(long seed) {
        return create(seed, "p1", "p2", "p3", "p4");
    }

    static NobGameState create(long seed, String... playerIds) {
        return engine().createGame(config(playerIds), new SeededRandomSource(seed));
    }

    static List<NobEvent> apply(NobGameState state, String actorId, NobAction action) {
        NobAction resolved = action;
        if (resolved.decisionId() == null && state.getPendingDecision() != null) {
            resolved = new NobAction(
                    action.type(),
                    action.commandId(),
                    action.expectedVersion(),
                    action.cardInstanceId(),
                    action.cardCode(),
                    action.targetPlayerIds(),
                    action.option(),
                    state.getPendingDecision().decisionId(),
                    action.cardInstanceIds()
            );
        } else if (resolved.decisionId() == null && state.hasUnclaimedMoonPick(actorId)) {
            resolved = new NobAction(
                    action.type(),
                    action.commandId(),
                    action.expectedVersion(),
                    action.cardInstanceId(),
                    action.cardCode(),
                    action.targetPlayerIds(),
                    action.option(),
                    "moon-" + actorId,
                    action.cardInstanceIds()
            );
        }
        ValidationResult validation = NobRulesEngine.validate(state, actorId, resolved);
        if (!validation.valid()) {
            throw new AssertionError(validation.errorCode() + ": " + validation.message());
        }
        return NobRulesEngine.apply(state, actorId, resolved, new SeededRandomSource(7));
    }

    static void flushPresentation(NobGameState state) {
        int guard = 0;
        while (state.getResolutionDisplayExpiresAt() != null && guard++ < 8) {
            state.setResolutionDisplayExpiresAt(java.time.Instant.now().minusSeconds(1));
            NobRulesEngine.apply(
                    state,
                    state.getPlayers().getFirst().getPlayerId(),
                    new NobAction(NobAction.TIMEOUT, "flush-" + guard, null, null, null, List.of(), null),
                    new SeededRandomSource(7)
            );
        }
    }

    static ValidationResult validate(NobGameState state, String actorId, NobAction action) {
        return NobRulesEngine.validate(state, actorId, action);
    }

    static NobAction draft(String instanceId) {
        return new NobAction(NobAction.DRAFT_PICK, null, null, instanceId, null, List.of(), null);
    }

    static NobAction pass() {
        return new NobAction(NobAction.PHASE_SUBMIT, null, null, null, null, List.of(), "PASS");
    }

    static NobAction submit(String instanceId) {
        return new NobAction(NobAction.PHASE_SUBMIT, null, null, instanceId, null, List.of(), null);
    }

    static NobAction submitBoth() {
        return new NobAction(NobAction.PHASE_SUBMIT, null, null, null, null, List.of(), "PLAY_BOTH");
    }

    static NobAction submitCards(String... instanceIds) {
        return new NobAction(
                NobAction.PHASE_SUBMIT,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                List.of(instanceIds)
        );
    }

    static NobAction target(String... playerIds) {
        return new NobAction(NobAction.CHOOSE_TARGET, null, null, null, null, List.of(playerIds), null);
    }

    static NobAction option(String option) {
        return new NobAction(NobAction.CHOOSE_OPTION, null, null, null, null, List.of(), option);
    }

    static NobAction hunter(String option) {
        return new NobAction(NobAction.HUNTER_DECISION, null, null, null, null, List.of(), option);
    }

    static NobAction reaction(String option) {
        return new NobAction(NobAction.REACTION, null, null, null, null, List.of(), option);
    }

    static PlayerContext viewer(String playerId) {
        return PlayerContext.player(playerId, playerId.toUpperCase());
    }
}
