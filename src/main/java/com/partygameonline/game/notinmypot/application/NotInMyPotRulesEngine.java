package com.partygameonline.game.notinmypot.application;

import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.game.notinmypot.domain.NotInMyPotAction;
import com.partygameonline.game.notinmypot.domain.NotInMyPotActionType;
import com.partygameonline.game.notinmypot.domain.NotInMyPotCard;
import com.partygameonline.game.notinmypot.domain.NotInMyPotCardCategory;
import com.partygameonline.game.notinmypot.domain.NotInMyPotEvent;
import com.partygameonline.game.notinmypot.domain.NotInMyPotGameState;
import com.partygameonline.game.notinmypot.domain.NotInMyPotIngredientType;
import com.partygameonline.game.notinmypot.domain.NotInMyPotPendingAction;
import com.partygameonline.game.notinmypot.domain.NotInMyPotPendingType;
import com.partygameonline.game.notinmypot.domain.NotInMyPotPhase;
import com.partygameonline.game.notinmypot.domain.NotInMyPotPlayerState;
import com.partygameonline.game.notinmypot.domain.NotInMyPotRole;
import com.partygameonline.game.core.RandomSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NotInMyPotRulesEngine {

    private NotInMyPotRulesEngine() {
    }

    public static ValidationResult validate(
            NotInMyPotGameState state,
            String actorId,
            NotInMyPotAction action
    ) {
        if (state == null || action == null) {
            return ValidationResult.reject("MALFORMED_ACTION", "The action could not be read");
        }
        if (state.isFinished()) {
            return ValidationResult.reject("GAME_ALREADY_FINISHED", "The game is already finished");
        }
        NotInMyPotPlayerState actor = state.player(actorId);
        if (actor == null) {
            return ValidationResult.reject("NOT_IN_GAME", "You are not in this game");
        }
        if (state.isDuplicateCommand(action.commandId())) {
            return ValidationResult.reject("DUPLICATE_REQUEST", "This request was already processed");
        }
        if (action.expectedVersion() != null && action.expectedVersion() != state.getStateVersion()) {
            return ValidationResult.reject("STALE_VERSION", "The game state has changed");
        }

        String type = normalized(action.type());
        if (NotInMyPotAction.TIMEOUT.equals(type)) {
            if (!state.timeoutIsDue(Instant.now())) {
                return ValidationResult.reject("TIMEOUT_NOT_DUE", "The turn or pending action has not expired");
            }
            String expectedActor = state.getPendingAction() == null
                    ? state.getCurrentPlayerId()
                    : state.getPendingAction().actorPlayerId();
            if (!actorId.equals(expectedActor)) {
                return ValidationResult.reject("NOT_YOUR_TURN", "It is not your turn");
            }
            return ValidationResult.ok();
        }

        if (!actor.isActive()) {
            return ValidationResult.reject("PLAYER_EXPELLED", "You cannot act after leaving the house");
        }

        if (state.getPendingAction() != null) {
            return validatePending(state, actor, action, type);
        }
        if (state.getPhase() != NotInMyPotPhase.PLAYING) {
            return ValidationResult.reject("WRONG_PHASE", "The game is not accepting a normal turn action");
        }
        if (!actorId.equals(state.getCurrentPlayerId())) {
            return ValidationResult.reject("NOT_YOUR_TURN", "It is not your turn");
        }

        return switch (type) {
            case NotInMyPotAction.PLAY_INGREDIENT -> validateIngredient(actor, action);
            case NotInMyPotAction.PLAY_ACTION -> validateAction(state, actor, action);
            case NotInMyPotAction.DECLARE_POT_READY -> validatePotReady(state, actor);
            case NotInMyPotAction.SELECT_TARGET,
                    NotInMyPotAction.RETURN_SHOPPING_CARDS,
                    NotInMyPotAction.ACKNOWLEDGE_SLOTTED_SPOON ->
                    ValidationResult.reject("WRONG_PENDING_ACTION", "There is no pending action for this command");
            default -> ValidationResult.reject("UNKNOWN_ACTION", "Unknown Not In My Pot action");
        };
    }

    public static List<NotInMyPotEvent> apply(
            NotInMyPotGameState state,
            String actorId,
            NotInMyPotAction action,
            RandomSource random
    ) {
        if (state.isDuplicateCommand(action.commandId())) {
            return List.of();
        }
        state.markCommandProcessed(action.commandId());
        List<NotInMyPotEvent> events = new ArrayList<>();
        String type = normalized(action.type());
        switch (type) {
            case NotInMyPotAction.PLAY_INGREDIENT -> applyIngredient(state, actorId, action, events);
            case NotInMyPotAction.PLAY_ACTION -> applyAction(state, actorId, action, random, events);
            case NotInMyPotAction.SELECT_TARGET -> applySelectedTarget(state, actorId, action, random, events);
            case NotInMyPotAction.ACKNOWLEDGE_SLOTTED_SPOON -> acknowledgeSlottedSpoon(state, actorId, events);
            case NotInMyPotAction.RETURN_SHOPPING_CARDS -> applyShoppingReturn(state, actorId, action, events);
            case NotInMyPotAction.DECLARE_POT_READY -> applyPotReady(state, events);
            case NotInMyPotAction.TIMEOUT -> applyTimeout(state, random, events);
            default -> {
                // Validation is authoritative. This branch is only defensive
                // for a caller that bypasses GameRuntimeService.
            }
        }
        state.bumpVersion();
        return List.copyOf(events);
    }

    public static List<NotInMyPotEvent> applyAbandon(
            NotInMyPotGameState state,
            String playerId,
            RandomSource random
    ) {
        List<NotInMyPotEvent> events = new ArrayList<>();
        NotInMyPotPlayerState player = state.player(playerId);
        if (player == null || state.isFinished() || !player.isActive()) {
            return events;
        }
        NotInMyPotPendingAction pending = state.getPendingAction();
        List<NotInMyPotCard> abandonedHand = new ArrayList<>(player.getHand());
        player.setActive(false);
        player.setConnected(false);
        moveHandToDiscard(state, player);
        addEvent(state, events, "PLAYER_ABANDONED", Map.of("playerId", playerId));

        if (pending != null && playerId.equals(pending.actorPlayerId())) {
            resolveAbandonedPendingAction(state, playerId, pending, abandonedHand, random, events);
        } else if (pending != null && pending.type() == NotInMyPotPendingType.SELECT_TARGET) {
            List<String> targets = pending.allowedTargetPlayerIds().stream()
                    .filter(targetId -> !targetId.equals(playerId))
                    .filter(targetId -> {
                        NotInMyPotPlayerState target = state.player(targetId);
                        return target != null && target.isActive();
                    })
                    .toList();
            state.setPendingAction(new NotInMyPotPendingAction(
                    pending.type(),
                    pending.actorPlayerId(),
                    pending.sourceCard(),
                    pending.inspectedCards(),
                    targets,
                    pending.startedAt(),
                    pending.expiresAt()
            ));
        }

        if (!checkAutomaticWinConditions(state, events) && playerId.equals(state.getCurrentPlayerId())) {
            advanceTurn(state, playerId, events);
        }
        state.bumpVersion();
        return List.copyOf(events);
    }

    private static ValidationResult validateIngredient(
            NotInMyPotPlayerState actor,
            NotInMyPotAction action
    ) {
        NotInMyPotCard card = actor.findHand(action.cardId());
        if (card == null) {
            return ValidationResult.reject("CARD_NOT_IN_HAND", "That card is not in your hand");
        }
        if (card.category() != NotInMyPotCardCategory.INGREDIENT) {
            return ValidationResult.reject("WRONG_CARD_CATEGORY", "Choose an ingredient card");
        }
        if (action.declaredType() == null || action.declaredType().isBlank()) {
            return ValidationResult.ok();
        }
        NotInMyPotIngredientType requestedType = NotInMyPotIngredientType.parse(action.declaredType());
        if (requestedType == null) {
            return ValidationResult.reject("INVALID_INGREDIENT_TYPE", "Ingredient type must be VEGETABLE, SALT, or MEAT");
        }
        return requestedType == card.ingredientType()
                ? ValidationResult.ok()
                : ValidationResult.reject("INGREDIENT_TYPE_MISMATCH", "The ingredient type does not match the card");
    }

    private static ValidationResult validateAction(
            NotInMyPotGameState state,
            NotInMyPotPlayerState actor,
            NotInMyPotAction action
    ) {
        NotInMyPotCard card = actor.findHand(action.cardId());
        if (card == null) {
            return ValidationResult.reject("CARD_NOT_IN_HAND", "That card is not in your hand");
        }
        if (card.category() != NotInMyPotCardCategory.ACTION) {
            return ValidationResult.reject("WRONG_CARD_CATEGORY", "Choose an action card");
        }
        NotInMyPotActionType actualType = card.actionType();
        if (action.actionType() != null) {
            NotInMyPotActionType declaredType = NotInMyPotActionType.parse(action.actionType());
            if (declaredType == null) {
                return ValidationResult.reject("INVALID_ACTION_TYPE", "That action type is not supported");
            }
            if (declaredType != actualType) {
                return ValidationResult.reject("ACTION_TYPE_MISMATCH", "The action type does not match the card");
            }
        }
        if (!actualType.requiresTarget() && action.targetPlayerId() != null) {
            return ValidationResult.reject("INVALID_TARGET", "This action does not use a target");
        }
        if (action.targetPlayerId() != null) {
            return validateTarget(state, actor.getPlayerId(), action.targetPlayerId());
        }
        if (actualType.requiresTarget()) {
            boolean hasTarget = state.activePlayers().stream()
                    .anyMatch(player -> !player.getPlayerId().equals(actor.getPlayerId()));
            if (!hasTarget) {
                return ValidationResult.reject("INVALID_TARGET", "No active target is available");
            }
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validatePotReady(
            NotInMyPotGameState state,
            NotInMyPotPlayerState actor
    ) {
        if (actor.getRole() != NotInMyPotRole.VEGETARIAN) {
            return ValidationResult.reject("MEAT_EATER_CANNOT_DECLARE", "Only a Vegetarian can declare the pot ready");
        }
        if (state.hasTurnActed()) {
            return ValidationResult.reject("POT_ALREADY_ACTED", "Pot Ready must be declared at the start of your turn");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validatePending(
            NotInMyPotGameState state,
            NotInMyPotPlayerState actor,
            NotInMyPotAction action,
            String type
    ) {
        NotInMyPotPendingAction pending = state.getPendingAction();
        if (!actor.getPlayerId().equals(pending.actorPlayerId())) {
            return ValidationResult.reject("NOT_YOUR_TURN", "Another player must finish the pending action");
        }
        if (pending.type() == NotInMyPotPendingType.SELECT_TARGET) {
            if (!NotInMyPotAction.SELECT_TARGET.equals(type)) {
                return ValidationResult.reject("WRONG_PENDING_ACTION", "Choose a target for the action first");
            }
            if (action.targetPlayerId() == null || !pending.allowedTargetPlayerIds().contains(action.targetPlayerId())) {
                return ValidationResult.reject("INVALID_TARGET", "That target is not allowed");
            }
            return validateTarget(state, actor.getPlayerId(), action.targetPlayerId());
        }
        if (pending.type() == NotInMyPotPendingType.INSPECT_SHUFFLED_POT) {
            if (!NotInMyPotAction.ACKNOWLEDGE_SLOTTED_SPOON.equals(type)) {
                return ValidationResult.reject("WRONG_PENDING_ACTION", "Review the shuffled pot cards first");
            }
            return ValidationResult.ok();
        }
        if (pending.type() == NotInMyPotPendingType.RETURN_SHOPPING_CARDS) {
            if (!NotInMyPotAction.RETURN_SHOPPING_CARDS.equals(type)) {
                return ValidationResult.reject("WRONG_PENDING_ACTION", "Return exactly two shopping cards first");
            }
            if (hasDuplicates(action.cardIds())) {
                return ValidationResult.reject("DUPLICATE_CARD", "A card cannot be selected twice");
            }
            if (action.cardIds().size() != NotInMyPotGameState.EMERGENCY_RETURN_COUNT) {
                return ValidationResult.reject("INVALID_CARD_COUNT", "Return exactly two cards");
            }
            for (String cardId : action.cardIds()) {
                if (actor.findHand(cardId) == null) {
                    return ValidationResult.reject("CARD_NOT_IN_HAND", "That card is not in your hand");
                }
            }
            return ValidationResult.ok();
        }
        return ValidationResult.reject("WRONG_PENDING_ACTION", "The pending action is no longer valid");
    }

    private static ValidationResult validateTarget(
            NotInMyPotGameState state,
            String actorId,
            String targetId
    ) {
        if (targetId == null || targetId.isBlank()) {
            return ValidationResult.reject("INVALID_TARGET", "Choose an active target");
        }
        if (actorId.equals(targetId)) {
            return ValidationResult.reject("SELF_TARGET", "You cannot target yourself");
        }
        NotInMyPotPlayerState target = state.player(targetId);
        if (target == null) {
            return ValidationResult.reject("INVALID_TARGET", "That target is not in the game");
        }
        if (!target.isActive()) {
            return ValidationResult.reject("TARGET_EXPELLED", "That player is already outside the house");
        }
        return ValidationResult.ok();
    }

    private static void applyIngredient(
            NotInMyPotGameState state,
            String actorId,
            NotInMyPotAction action,
            List<NotInMyPotEvent> events
    ) {
        NotInMyPotPlayerState actor = state.requirePlayer(actorId);
        NotInMyPotCard card = actor.findHand(action.cardId());
        actor.getHand().remove(card);
        state.getPot().addFirst(card);
        state.setTurnHasActed(true);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("playerId", actorId);
        payload.put("potCardCount", state.getPot().size());
        addEvent(state, events, "INGREDIENT_DECLARED", payload);
        finishRegularTurn(state, actor, events);
    }

    private static void applyAction(
            NotInMyPotGameState state,
            String actorId,
            NotInMyPotAction action,
            RandomSource random,
            List<NotInMyPotEvent> events
    ) {
        NotInMyPotPlayerState actor = state.requirePlayer(actorId);
        NotInMyPotCard card = actor.findHand(action.cardId());
        actor.getHand().remove(card);
        state.setTurnHasActed(true);
        NotInMyPotActionType type = card.actionType();
        addEvent(state, events, "ACTION_STARTED", Map.of(
                "playerId", actorId,
                "actionType", type.name()
        ));

        if (type.requiresTarget() && action.targetPlayerId() == null) {
            List<String> targets = state.activePlayers().stream()
                    .map(NotInMyPotPlayerState::getPlayerId)
                    .filter(id -> !id.equals(actorId))
                    .toList();
            if (!targets.isEmpty()) {
                beginPending(state, new NotInMyPotPendingAction(
                        NotInMyPotPendingType.SELECT_TARGET,
                        actorId,
                        card,
                        List.of(),
                        targets,
                        Instant.now(),
                        pendingDeadline(state)
                ));
                addEvent(state, events, "TARGET_SELECTION_REQUIRED", Map.of(
                        "playerId", actorId,
                        "actionType", type.name()
                ));
                return;
            }
        }

        state.getDiscardPile().add(card);
        if (type.requiresTarget()) {
            resolveTargetAction(state, actor, type, action.targetPlayerId(), events);
            if (!state.isFinished()) {
                addEvent(state, events, "ACTION_RESOLVED", Map.of("actionType", type.name()));
                finishRegularTurn(state, actor, events);
            }
            return;
        }

        switch (type) {
            case SCOOP_OUT -> applyScoop(state, actor, events);
            case SLOTTED_SPOON -> applySlottedSpoon(state, actor, random, events);
            case EMERGENCY_SHOPPING -> applyEmergencyShopping(state, actor, events);
            default -> {
                addEvent(state, events, "ACTION_RESOLVED", Map.of("actionType", type.name()));
                finishRegularTurn(state, actor, events);
            }
        }
    }

    private static void applySelectedTarget(
            NotInMyPotGameState state,
            String actorId,
            NotInMyPotAction action,
            RandomSource random,
            List<NotInMyPotEvent> events
    ) {
        NotInMyPotPendingAction pending = state.getPendingAction();
        NotInMyPotPlayerState actor = state.requirePlayer(actorId);
        state.setPendingAction(null);
        state.setPhase(NotInMyPotPhase.PLAYING);
        state.getDiscardPile().add(pending.sourceCard());
        NotInMyPotActionType type = pending.sourceCard().actionType();
        resolveTargetAction(state, actor, type, action.targetPlayerId(), events);
        if (!state.isFinished()) {
            addEvent(state, events, "ACTION_RESOLVED", Map.of("actionType", type.name()));
            finishRegularTurn(state, actor, events);
        }
    }

    private static void resolveTargetAction(
            NotInMyPotGameState state,
            NotInMyPotPlayerState actor,
            NotInMyPotActionType type,
            String targetId,
            List<NotInMyPotEvent> events
    ) {
        NotInMyPotPlayerState target = state.requirePlayer(targetId);
        if (type == NotInMyPotActionType.OUT_OF_HOUSE) {
            state.incrementDoorCount(targetId);
            addEvent(state, events, "PLAYER_DOOR_UPDATED", Map.of(
                    "actorPlayerId", actor.getPlayerId(),
                    "playerId", targetId,
                    "doorCount", state.doorCount(targetId)
            ));
            if (state.doorCount(targetId) >= NotInMyPotGameState.MAX_DOORS) {
                expel(state, target, events);
            }
            return;
        }
        int discarded = target.getHand().size();
        moveHandToDiscard(state, target);
        int drawn = drawCards(state, target, NotInMyPotGameState.TRASH_DRAW_COUNT, "TRASH_REPLACEMENT", events);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("playerId", actor.getPlayerId());
        payload.put("targetPlayerId", targetId);
        payload.put("discardedCount", discarded);
        payload.put("drawnCount", drawn);
        addEvent(state, events, "TRASH_OUT_RESOLVED", payload);
    }

    private static void expel(
            NotInMyPotGameState state,
            NotInMyPotPlayerState target,
            List<NotInMyPotEvent> events
    ) {
        target.setActive(false);
        state.revealRole(target.getPlayerId());
        int discarded = target.getHand().size();
        moveHandToDiscard(state, target);
        addEvent(state, events, "PLAYER_EXPELLED", Map.of(
                "playerId", target.getPlayerId(),
                "role", target.getRole().name(),
                "doorCount", state.doorCount(target.getPlayerId()),
                "discardedCount", discarded
        ));
    }

    private static void applyScoop(
            NotInMyPotGameState state,
            NotInMyPotPlayerState actor,
            List<NotInMyPotEvent> events
    ) {
        int removed = 0;
        while (removed < NotInMyPotGameState.SCOOP_LIMIT && !state.getPot().isEmpty()) {
            state.getDiscardPile().add(state.getPot().removeFirst());
            removed++;
        }
        addEvent(state, events, "SCOOP_OUT_RESOLVED", Map.of(
                "playerId", actor.getPlayerId(),
                "removedCount", removed,
                "potCardCount", state.getPot().size()
        ));
        addEvent(state, events, "ACTION_RESOLVED", Map.of("actionType", NotInMyPotActionType.SCOOP_OUT.name()));
        finishRegularTurn(state, actor, events);
    }

    private static void applySlottedSpoon(
            NotInMyPotGameState state,
            NotInMyPotPlayerState actor,
            RandomSource random,
            List<NotInMyPotEvent> events
    ) {
        List<NotInMyPotCard> inspected = new ArrayList<>();
        while (inspected.size() < NotInMyPotGameState.SLOTTED_SPOON_LIMIT && !state.getPot().isEmpty()) {
            inspected.add(state.getPot().removeFirst());
        }
        if (inspected.isEmpty()) {
            addEvent(state, events, "SLOTTED_SPOON_RESOLVED", Map.of(
                    "playerId", actor.getPlayerId(),
                    "cardCount", 0
            ));
            addEvent(state, events, "ACTION_RESOLVED", Map.of(
                    "actionType", NotInMyPotActionType.SLOTTED_SPOON.name()
            ));
            finishRegularTurn(state, actor, events);
            return;
        }
        List<NotInMyPotCard> randomizedPotOrder = new ArrayList<>(inspected);
        random.shuffle(randomizedPotOrder);
        putOnPotTop(state, randomizedPotOrder);
        List<NotInMyPotCard> privateInspectionOrder = new ArrayList<>(inspected);
        random.shuffle(privateInspectionOrder);
        beginPending(state, new NotInMyPotPendingAction(
                NotInMyPotPendingType.INSPECT_SHUFFLED_POT,
                actor.getPlayerId(),
                null,
                privateInspectionOrder,
                List.of(),
                Instant.now(),
                pendingDeadline(state)
        ));
        addEvent(state, events, "SLOTTED_SPOON_INSPECTION_REQUIRED", Map.of(
                "playerId", actor.getPlayerId(),
                "cardCount", inspected.size()
        ));
    }

    private static void applyEmergencyShopping(
            NotInMyPotGameState state,
            NotInMyPotPlayerState actor,
            List<NotInMyPotEvent> events
    ) {
        int drawn = drawCards(state, actor, NotInMyPotGameState.EMERGENCY_DRAW_COUNT, "EMERGENCY_SHOPPING", events);
        addEvent(state, events, "EMERGENCY_SHOPPING_RESOLVED", Map.of(
                "playerId", actor.getPlayerId(),
                "drawnCount", drawn,
                "handCount", actor.getHand().size()
        ));
        if (state.isFinished()) {
            return;
        }
        beginPending(state, new NotInMyPotPendingAction(
                NotInMyPotPendingType.RETURN_SHOPPING_CARDS,
                actor.getPlayerId(),
                null,
                List.of(),
                List.of(),
                Instant.now(),
                pendingDeadline(state)
        ));
        addEvent(state, events, "SHOPPING_RETURN_REQUIRED", Map.of(
                "playerId", actor.getPlayerId(),
                "cardCount", NotInMyPotGameState.EMERGENCY_RETURN_COUNT
        ));
    }

    private static void acknowledgeSlottedSpoon(
            NotInMyPotGameState state,
            String actorId,
            List<NotInMyPotEvent> events
    ) {
        NotInMyPotPendingAction pending = state.getPendingAction();
        state.setPendingAction(null);
        state.setPhase(NotInMyPotPhase.PLAYING);
        addEvent(state, events, "SLOTTED_SPOON_RESOLVED", Map.of(
                "playerId", actorId,
                "cardCount", pending.inspectedCards().size()
        ));
        addEvent(state, events, "ACTION_RESOLVED", Map.of(
                "actionType", NotInMyPotActionType.SLOTTED_SPOON.name()
        ));
        finishRegularTurn(state, state.requirePlayer(actorId), events);
    }

    private static void applyShoppingReturn(
            NotInMyPotGameState state,
            String actorId,
            NotInMyPotAction action,
            List<NotInMyPotEvent> events
    ) {
        NotInMyPotPlayerState actor = state.requirePlayer(actorId);
        completeShoppingReturn(state, actor, action.cardIds(), false, events);
    }

    private static void completeShoppingReturn(
            NotInMyPotGameState state,
            NotInMyPotPlayerState actor,
            List<String> cardIds,
            boolean automatic,
            List<NotInMyPotEvent> events
    ) {
        List<NotInMyPotCard> returned = new ArrayList<>();
        for (String cardId : cardIds) {
            NotInMyPotCard card = actor.findHand(cardId);
            if (card != null) {
                actor.getHand().remove(card);
                returned.add(card);
            }
        }
        if (returned.size() == NotInMyPotGameState.EMERGENCY_RETURN_COUNT) {
            state.getDrawPile().addFirst(returned.get(1));
            state.getDrawPile().addFirst(returned.getFirst());
        }
        state.setPendingAction(null);
        state.setPhase(NotInMyPotPhase.PLAYING);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("playerId", actor.getPlayerId());
        payload.put("returnedCount", returned.size());
        payload.put("automatic", automatic);
        addEvent(state, events, "SHOPPING_CARDS_RETURNED", payload);
        addEvent(state, events, "ACTION_RESOLVED", Map.of(
                "actionType", NotInMyPotActionType.EMERGENCY_SHOPPING.name()
        ));
        if (!state.isFinished() && actor.isActive()) {
            finishEmergencyTurn(state, actor, events);
        }
    }

    private static void applyPotReady(
            NotInMyPotGameState state,
            List<NotInMyPotEvent> events
    ) {
        state.setTurnHasActed(true);
        int score = state.scorePot();
        NotInMyPotRole winner = score >= NotInMyPotRules.targetScore(state.getPlayers().size())
                ? NotInMyPotRole.VEGETARIAN
                : NotInMyPotRole.MEAT_EATER;
        addEvent(state, events, "POT_REVEALED", Map.of(
                "score", score,
                "targetScore", NotInMyPotRules.targetScore(state.getPlayers().size())
        ));
        finishGame(state, winner, events, "POT_READY_DECLARED", true);
    }

    private static void beginPending(
            NotInMyPotGameState state,
            NotInMyPotPendingAction pending
    ) {
        state.setTurnDeadline(null);
        state.setPendingAction(pending);
        state.setPhase(NotInMyPotPhase.RESOLVING_ACTION);
    }

    private static Instant pendingDeadline(NotInMyPotGameState state) {
        return Instant.now().plusSeconds(state.getSettings().turnSeconds());
    }

    private static void finishRegularTurn(
            NotInMyPotGameState state,
            NotInMyPotPlayerState actor,
            List<NotInMyPotEvent> events
    ) {
        if (state.isFinished() || state.getPendingAction() != null) {
            return;
        }
        if (checkAutomaticWinConditions(state, events)) {
            return;
        }
        if (actor.getHand().size() != NotInMyPotGameState.HAND_SIZE - 1) {
            throw new IllegalStateException("A normal turn must leave exactly two cards before refill");
        }
        drawCards(state, actor, NotInMyPotGameState.NORMAL_DRAW_COUNT, "TURN_REFILL", events);
        if (state.isFinished()) {
            return;
        }
        advanceTurn(state, actor.getPlayerId(), events);
    }

    private static void finishEmergencyTurn(
            NotInMyPotGameState state,
            NotInMyPotPlayerState actor,
            List<NotInMyPotEvent> events
    ) {
        if (state.isFinished()) {
            return;
        }
        if (actor.getHand().size() != NotInMyPotGameState.HAND_SIZE) {
            throw new IllegalStateException("Emergency Shopping must finish with three cards");
        }
        if (checkAutomaticWinConditions(state, events)) {
            return;
        }
        advanceTurn(state, actor.getPlayerId(), events);
    }

    private static int drawCards(
            NotInMyPotGameState state,
            NotInMyPotPlayerState player,
            int count,
            String reason,
            List<NotInMyPotEvent> events
    ) {
        int drawn = 0;
        while (drawn < count && !state.getDrawPile().isEmpty() && !state.isFinished()) {
            player.getHand().add(state.getDrawPile().removeFirst());
            drawn++;
            if (state.getDrawPile().isEmpty()) {
                checkAutomaticWinConditions(state, events);
            }
        }
        if (drawn > 0) {
            addEvent(state, events, "CARDS_DRAWN", Map.of(
                    "playerId", player.getPlayerId(),
                    "drawnCount", drawn,
                    "reason", reason
            ));
        }
        return drawn;
    }

    private static void moveHandToDiscard(
            NotInMyPotGameState state,
            NotInMyPotPlayerState player
    ) {
        state.getDiscardPile().addAll(player.getHand());
        player.getHand().clear();
    }

    private static void advanceTurn(
            NotInMyPotGameState state,
            String fromPlayerId,
            List<NotInMyPotEvent> events
    ) {
        List<NotInMyPotPlayerState> players = state.getPlayers();
        int start = -1;
        for (int index = 0; index < players.size(); index++) {
            if (players.get(index).getPlayerId().equals(fromPlayerId)) {
                start = index;
                break;
            }
        }
        for (int offset = 1; offset <= players.size(); offset++) {
            NotInMyPotPlayerState candidate = players.get((start + offset + players.size()) % players.size());
            if (!candidate.isActive()) {
                continue;
            }
            state.setCurrentPlayerId(candidate.getPlayerId());
            state.incrementTurnNumber();
            state.setTurnHasActed(false);
            state.setPhase(NotInMyPotPhase.PLAYING);
            state.setTurnDeadline(Instant.now().plusSeconds(state.getSettings().turnSeconds()));
            addEvent(state, events, "TURN_STARTED", Map.of(
                    "playerId", candidate.getPlayerId(),
                    "turnNumber", state.getTurnNumber()
            ));
            return;
        }
        checkAutomaticWinConditions(state, events);
    }

    private static boolean checkAutomaticWinConditions(
            NotInMyPotGameState state,
            List<NotInMyPotEvent> events
    ) {
        if (state.isFinished()) {
            return true;
        }
        long activeVegetarians = state.activePlayers().stream()
                .filter(player -> player.getRole() == NotInMyPotRole.VEGETARIAN)
                .count();
        long activeMeatEaters = state.activePlayers().stream()
                .filter(player -> player.getRole() == NotInMyPotRole.MEAT_EATER)
                .count();
        if (activeMeatEaters == 0) {
            finishGame(state, NotInMyPotRole.VEGETARIAN, events, "ALL_MEAT_EATERS_EXPELLED", false);
            return true;
        }
        if (activeVegetarians == activeMeatEaters) {
            finishGame(state, NotInMyPotRole.MEAT_EATER, events, "FACTIONS_EQUAL", false);
            return true;
        }
        if (state.getDrawPile().isEmpty()) {
            finishGame(state, NotInMyPotRole.MEAT_EATER, events, "DRAW_PILE_EMPTY", false);
            return true;
        }
        return false;
    }

    private static void finishGame(
            NotInMyPotGameState state,
            NotInMyPotRole winner,
            List<NotInMyPotEvent> events,
            String reason,
            boolean revealPot
    ) {
        if (state.isFinished()) {
            return;
        }
        state.finish(winner, revealPot);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("winnerFaction", winner.name());
        payload.put("winnerPlayerIds", state.getWinnerPlayerIds());
        payload.put("targetScore", NotInMyPotRules.targetScore(state.getPlayers().size()));
        payload.put("reason", reason);
        payload.put("potRevealed", revealPot);
        if (state.getFinalPotScore() != null) {
            payload.put("finalScore", state.getFinalPotScore());
        }
        addEvent(state, events, "GAME_ENDED", payload);
    }

    private static void applyTimeout(
            NotInMyPotGameState state,
            RandomSource random,
            List<NotInMyPotEvent> events
    ) {
        NotInMyPotPendingAction pending = state.getPendingAction();
        if (pending == null) {
            NotInMyPotPlayerState actor = state.requirePlayer(state.getCurrentPlayerId());
            state.setTurnDeadline(null);
            addEvent(state, events, "TURN_TIMED_OUT", Map.of(
                    "playerId", actor.getPlayerId(),
                    "turnNumber", state.getTurnNumber(),
                    "automatic", true
            ));
            autoPlayRandomCard(state, actor, random, events);
            return;
        }
        NotInMyPotPlayerState actor = state.requirePlayer(pending.actorPlayerId());
        addEvent(state, events, "ACTION_TIMED_OUT", Map.of(
                "playerId", actor.getPlayerId(),
                "pendingType", pending.type().name()
        ));
        switch (pending.type()) {
            case SELECT_TARGET -> {
                List<String> targets = pending.allowedTargetPlayerIds().stream()
                        .filter(id -> {
                            NotInMyPotPlayerState target = state.player(id);
                            return target != null && target.isActive();
                        })
                        .toList();
                if (targets.isEmpty()) {
                    state.getDiscardPile().add(pending.sourceCard());
                    state.setPendingAction(null);
                    state.setPhase(NotInMyPotPhase.PLAYING);
                    addEvent(state, events, "ACTION_RESOLVED", Map.of("automatic", true));
                    finishRegularTurn(state, actor, events);
                } else {
                    String targetId = targets.get(random.nextInt(targets.size()));
                    state.setPendingAction(null);
                    state.setPhase(NotInMyPotPhase.PLAYING);
                    state.getDiscardPile().add(pending.sourceCard());
                    resolveTargetAction(state, actor, pending.sourceCard().actionType(), targetId, events);
                    if (!state.isFinished()) {
                        addEvent(state, events, "ACTION_RESOLVED", Map.of(
                                "actionType", pending.sourceCard().actionType().name(),
                                "automatic", true
                        ));
                        finishRegularTurn(state, actor, events);
                    }
                }
            }
            case INSPECT_SHUFFLED_POT -> {
                state.setPendingAction(null);
                state.setPhase(NotInMyPotPhase.PLAYING);
                addEvent(state, events, "SLOTTED_SPOON_RESOLVED", Map.of(
                        "playerId", actor.getPlayerId(),
                        "cardCount", pending.inspectedCards().size(),
                        "automatic", true
                ));
                addEvent(state, events, "ACTION_RESOLVED", Map.of(
                        "actionType", NotInMyPotActionType.SLOTTED_SPOON.name(),
                        "automatic", true
                ));
                finishRegularTurn(state, actor, events);
            }
            case RETURN_SHOPPING_CARDS -> {
                List<String> firstTwo = actor.getHand().stream()
                        .limit(NotInMyPotGameState.EMERGENCY_RETURN_COUNT)
                        .map(NotInMyPotCard::cardId)
                        .toList();
                completeShoppingReturn(state, actor, firstTwo, true, events);
            }
        }
    }

    private static void autoPlayRandomCard(
            NotInMyPotGameState state,
            NotInMyPotPlayerState actor,
            RandomSource random,
            List<NotInMyPotEvent> events
    ) {
        boolean hasTarget = state.activePlayers().stream()
                .anyMatch(player -> !player.getPlayerId().equals(actor.getPlayerId()));
        List<NotInMyPotCard> playableCards = actor.getHand().stream()
                .filter(card -> card.isIngredient()
                        || !card.actionType().requiresTarget()
                        || hasTarget)
                .toList();
        if (playableCards.isEmpty()) {
            advanceTurn(state, actor.getPlayerId(), events);
            return;
        }

        NotInMyPotCard card = playableCards.get(random.nextInt(playableCards.size()));
        if (card.isIngredient()) {
            applyIngredient(state, actor.getPlayerId(), new NotInMyPotAction(
                    NotInMyPotAction.PLAY_INGREDIENT,
                    null,
                    null,
                    card.cardId(),
                    card.ingredientType().name(),
                    null,
                    null,
                    List.of()
            ), events);
            return;
        }

        String targetId = null;
        if (card.actionType().requiresTarget()) {
            List<String> targets = state.activePlayers().stream()
                    .map(NotInMyPotPlayerState::getPlayerId)
                    .filter(id -> !id.equals(actor.getPlayerId()))
                    .toList();
            targetId = targets.get(random.nextInt(targets.size()));
        }
        applyAction(state, actor.getPlayerId(), new NotInMyPotAction(
                NotInMyPotAction.PLAY_ACTION,
                null,
                null,
                card.cardId(),
                null,
                card.actionType().name(),
                targetId,
                List.of()
        ), random, events);
    }

    private static void resolveAbandonedPendingAction(
            NotInMyPotGameState state,
            String playerId,
            NotInMyPotPendingAction pending,
            List<NotInMyPotCard> abandonedHand,
            RandomSource random,
            List<NotInMyPotEvent> events
    ) {
        if (pending.type() == NotInMyPotPendingType.INSPECT_SHUFFLED_POT) {
            state.setPendingAction(null);
            state.setPhase(NotInMyPotPhase.PLAYING);
            addEvent(state, events, "SLOTTED_SPOON_RESOLVED", Map.of(
                    "playerId", playerId,
                    "cardCount", pending.inspectedCards().size(),
                    "automatic", true
            ));
            return;
        }
        if (pending.type() == NotInMyPotPendingType.RETURN_SHOPPING_CARDS) {
            List<String> firstTwo = abandonedHand.stream()
                    .limit(NotInMyPotGameState.EMERGENCY_RETURN_COUNT)
                    .map(NotInMyPotCard::cardId)
                    .toList();
            List<NotInMyPotCard> returned = abandonedHand.stream()
                    .filter(card -> firstTwo.contains(card.cardId()))
                    .toList();
            if (returned.size() == NotInMyPotGameState.EMERGENCY_RETURN_COUNT) {
                state.getDiscardPile().removeAll(returned);
                state.getDrawPile().addFirst(returned.get(1));
                state.getDrawPile().addFirst(returned.getFirst());
            }
            state.setPendingAction(null);
            state.setPhase(NotInMyPotPhase.PLAYING);
            return;
        }
        state.getDiscardPile().add(pending.sourceCard());
        state.setPendingAction(null);
        state.setPhase(NotInMyPotPhase.PLAYING);
        addEvent(state, events, "ACTION_RESOLVED", Map.of("automatic", true));
    }

    /** Adds a list expressed in top-to-bottom order to the top of the pot. */
    private static void putOnPotTop(
            NotInMyPotGameState state,
            List<NotInMyPotCard> topToBottom
    ) {
        for (int index = topToBottom.size() - 1; index >= 0; index--) {
            state.getPot().addFirst(topToBottom.get(index));
        }
    }

    private static boolean hasDuplicates(List<String> values) {
        return values.size() != new HashSet<>(values).size();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static void addEvent(
            NotInMyPotGameState state,
            List<NotInMyPotEvent> events,
            String type,
            Map<String, Object> payload
    ) {
        NotInMyPotEvent event = NotInMyPotEvent.of(type, payload);
        state.addPublicEvent(event);
        events.add(event);
    }
}
