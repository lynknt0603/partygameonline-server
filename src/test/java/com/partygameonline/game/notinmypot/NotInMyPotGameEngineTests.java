package com.partygameonline.game.notinmypot;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.game.notinmypot.api.dto.NotInMyPotView;
import com.partygameonline.game.notinmypot.application.NotInMyPotRules;
import com.partygameonline.game.notinmypot.application.NotInMyPotRulesEngine;
import com.partygameonline.game.notinmypot.domain.NotInMyPotAction;
import com.partygameonline.game.notinmypot.domain.NotInMyPotActionType;
import com.partygameonline.game.notinmypot.domain.NotInMyPotCard;
import com.partygameonline.game.notinmypot.domain.NotInMyPotGameState;
import com.partygameonline.game.notinmypot.domain.NotInMyPotIngredientType;
import com.partygameonline.game.notinmypot.domain.NotInMyPotPendingType;
import com.partygameonline.game.notinmypot.domain.NotInMyPotPhase;
import com.partygameonline.game.notinmypot.domain.NotInMyPotPlayerState;
import com.partygameonline.game.notinmypot.domain.NotInMyPotRole;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotInMyPotGameEngineTests {

    private final NotInMyPotGameEngine engine = new NotInMyPotGameEngine();
    private final NotInMyPotGameProjector projector = new NotInMyPotGameProjector();

    @Test
    void createsTheConfiguredDeckAndRoleDistributionForEveryPlayerCount() {
        for (int playerCount = 3; playerCount <= 8; playerCount++) {
            NotInMyPotGameState state = newGame(playerCount);
            NotInMyPotRules.RoleDistribution distribution = NotInMyPotRules.roleDistribution(playerCount);
            int deckSize = NotInMyPotRules.buildDeck(playerCount).size();

            assertThat(state.getPlayers()).hasSize(playerCount);
            assertThat(state.getPlayers().stream()
                    .filter(player -> player.getRole() == NotInMyPotRole.VEGETARIAN)
                    .count()).isEqualTo(distribution.vegetarians());
            assertThat(state.getPlayers().stream()
                    .filter(player -> player.getRole() == NotInMyPotRole.MEAT_EATER)
                    .count()).isEqualTo(distribution.meatEaters());
            assertThat(state.getPlayers()).allSatisfy(player ->
                    assertThat(player.getHand()).hasSize(NotInMyPotGameState.HAND_SIZE));
            assertThat(state.getDrawPile()).hasSize(deckSize - (playerCount * NotInMyPotGameState.HAND_SIZE));
            assertThat(state.getPot()).isEmpty();
            assertThat(state.getDiscardPile()).isEmpty();
            assertThat(state.getPhase()).isEqualTo(NotInMyPotPhase.PLAYING);
            assertThat(state.getCurrentPlayerId()).isNotBlank();
            assertThat(state.getPublicRoles()).isEmpty();
            assertThat(state.getPlayers().stream()
                    .flatMap(player -> player.getHand().stream())
                    .count() + state.getDrawPile().size())
                    .isEqualTo(deckSize);
        }
    }

    @Test
    void playingAnIngredientKeepsTheActualCardHiddenAndStoresOnlyTheDeclaration() {
        NotInMyPotGameState state = newGame(3);
        NotInMyPotPlayerState actor = currentPlayer(state);
        NotInMyPotCard meat = ingredient("lie-meat", NotInMyPotIngredientType.MEAT);
        replaceHand(actor, meat, ingredient("lie-salt", NotInMyPotIngredientType.SALT),
                ingredient("lie-veg", NotInMyPotIngredientType.VEGETABLE));
        ensureDrawPile(state, 3);

        NotInMyPotAction action = new NotInMyPotAction(
                NotInMyPotAction.PLAY_INGREDIENT,
                "ingredient-1",
                state.getStateVersion(),
                meat.cardId(),
                "VEGETABLE",
                null,
                null,
                List.of()
        );
        assertThat(engine.validate(state, player(actor), action).valid()).isTrue();
        List<?> events = engine.apply(state, player(actor), action, new SeededRandomSource(4)).events();

        assertThat(state.getPot().getFirst()).isEqualTo(meat);
        assertThat(events).anySatisfy(event -> {
            var typed = (com.partygameonline.game.notinmypot.domain.NotInMyPotEvent) event;
            if ("INGREDIENT_DECLARED".equals(typed.type())) {
                assertThat(typed.payload()).containsEntry("declaredType", "VEGETABLE");
                assertThat(typed.payload()).doesNotContainKey("actualType");
            }
        });
        NotInMyPotView actorView = projector.project(state, player(actor));
        NotInMyPotView opponentView = projector.project(
                state,
                player(state.getPlayers().stream()
                        .filter(candidate -> !candidate.getPlayerId().equals(actor.getPlayerId()))
                        .findFirst()
                        .orElseThrow())
        );
        assertThat(actorView.myRole()).isNotBlank();
        assertThat(actorView.finalPot()).isEmpty();
        assertThat(opponentView.players()).filteredOn(view -> !view.you())
                .allMatch(view -> view.role() == null);
        assertThat(opponentView.finalPot()).isEmpty();
    }

    @Test
    void outOfHouseRevealsTheRoleAndExpelsAfterThreeDoors() {
        NotInMyPotGameState state = newGame(3);
        NotInMyPotPlayerState actor = currentPlayer(state);
        NotInMyPotPlayerState target = state.getPlayers().stream()
                .filter(player -> !player.getPlayerId().equals(actor.getPlayerId()))
                .findFirst()
                .orElseThrow();
        NotInMyPotCard first = NotInMyPotCard.action("door-1", NotInMyPotActionType.OUT_OF_HOUSE);
        replaceHand(actor, first,
                ingredient("door-filler-1", NotInMyPotIngredientType.SALT),
                ingredient("door-filler-2", NotInMyPotIngredientType.SALT));
        replaceHand(target,
                ingredient("target-1", NotInMyPotIngredientType.MEAT),
                ingredient("target-2", NotInMyPotIngredientType.SALT),
                ingredient("target-3", NotInMyPotIngredientType.VEGETABLE));
        ensureDrawPile(state, 10);

        for (int door = 1; door <= NotInMyPotGameState.MAX_DOORS; door++) {
            state.setCurrentPlayerId(actor.getPlayerId());
            state.setTurnHasActed(false);
            NotInMyPotCard card = door == 1
                    ? first
                    : NotInMyPotCard.action("door-" + door, NotInMyPotActionType.OUT_OF_HOUSE);
            if (door > 1) {
                actor.getHand().set(0, card);
            }
            NotInMyPotAction action = playAction(state, actor, card, target.getPlayerId(), "door-cmd-" + door);
            assertThat(engine.validate(state, player(actor), action).valid()).isTrue();
            engine.apply(state, player(actor), action, new SeededRandomSource(door));
            assertThat(state.doorCount(target.getPlayerId())).isEqualTo(door);
        }

        assertThat(target.isActive()).isFalse();
        assertThat(target.getHand()).isEmpty();
        assertThat(state.getPublicRoles()).containsEntry(target.getPlayerId(), target.getRole());
        assertThat(state.getPublicEvents()).anySatisfy(event -> {
            if ("PLAYER_EXPELLED".equals(event.type())) {
                assertThat(event.payload()).containsEntry("role", target.getRole().name());
            }
        });
    }

    @Test
    void scoopRemovesAtMostTwoTopCardsWithoutRevealingThem() {
        NotInMyPotGameState state = newGame(4);
        NotInMyPotPlayerState actor = currentPlayer(state);
        NotInMyPotCard actionCard = NotInMyPotCard.action("scoop", NotInMyPotActionType.SCOOP_OUT);
        replaceHand(actor, actionCard,
                ingredient("scoop-filler-1", NotInMyPotIngredientType.SALT),
                ingredient("scoop-filler-2", NotInMyPotIngredientType.SALT));
        NotInMyPotCard bottom = ingredient("pot-bottom", NotInMyPotIngredientType.MEAT);
        NotInMyPotCard middle = ingredient("pot-middle", NotInMyPotIngredientType.SALT);
        NotInMyPotCard top = ingredient("pot-top", NotInMyPotIngredientType.VEGETABLE);
        state.getPot().addFirst(bottom);
        state.getPot().addFirst(middle);
        state.getPot().addFirst(top);
        ensureDrawPile(state, 10);

        NotInMyPotAction action = playAction(state, actor, actionCard, null, "scoop-cmd");
        engine.apply(state, player(actor), action, new SeededRandomSource(7));

        assertThat(state.getPot()).extracting(NotInMyPotCard::cardId)
                .containsExactly(bottom.cardId());
        assertThat(state.getDiscardPile()).extracting(NotInMyPotCard::cardId)
                .contains(actionCard.cardId(), top.cardId());
        assertThat(state.getPublicEvents()).filteredOn(event -> "SCOOP_OUT_RESOLVED".equals(event.type()))
                .singleElement()
                .extracting(event -> event.payload().get("removedCount"))
                .isEqualTo(2);
    }

    @Test
    void slottedSpoonOnlyShowsInspectedCardsToTheActorAndValidatesReorder() {
        NotInMyPotGameState state = newGame(4);
        NotInMyPotPlayerState actor = currentPlayer(state);
        NotInMyPotCard actionCard = NotInMyPotCard.action("slotted", NotInMyPotActionType.SLOTTED_SPOON);
        replaceHand(actor, actionCard,
                ingredient("slotted-filler-1", NotInMyPotIngredientType.SALT),
                ingredient("slotted-filler-2", NotInMyPotIngredientType.SALT));
        List<NotInMyPotCard> potCards = List.of(
                ingredient("slotted-bottom", NotInMyPotIngredientType.MEAT),
                ingredient("slotted-middle", NotInMyPotIngredientType.SALT),
                ingredient("slotted-top", NotInMyPotIngredientType.VEGETABLE)
        );
        for (NotInMyPotCard card : potCards) {
            state.getPot().addFirst(card);
        }
        ensureDrawPile(state, 10);

        NotInMyPotAction play = playAction(state, actor, actionCard, null, "slotted-play");
        engine.apply(state, player(actor), play, new SeededRandomSource(9));
        assertThat(state.getPhase()).isEqualTo(NotInMyPotPhase.RESOLVING_ACTION);
        assertThat(state.getPendingAction()).isNotNull();
        assertThat(state.getPendingAction().type()).isEqualTo(NotInMyPotPendingType.REORDER_POT_CARDS);
        assertThat(projector.project(state, player(actor)).privateInspectedCards()).hasSize(3);
        assertThat(projector.project(state, player(otherPlayer(state, actor))).privateInspectedCards()).isEmpty();

        List<String> selected = state.getPendingAction().inspectedCardIds();
        List<String> invalid = new ArrayList<>(selected);
        invalid.set(0, selected.get(1));
        ValidationResult duplicate = engine.validate(
                state,
                player(actor),
                new NotInMyPotAction(
                        NotInMyPotAction.REORDER_POT_CARDS,
                        "bad-order",
                        state.getStateVersion(),
                        null,
                        null,
                        null,
                        null,
                        invalid
                )
        );
        assertThat(duplicate.valid()).isFalse();
        assertThat(duplicate.errorCode()).isEqualTo("DUPLICATE_CARD");

        List<String> chosenOrder = selected.reversed();
        NotInMyPotAction reorder = new NotInMyPotAction(
                NotInMyPotAction.REORDER_POT_CARDS,
                "slotted-reorder",
                state.getStateVersion(),
                null,
                null,
                null,
                null,
                chosenOrder
        );
        assertThat(engine.validate(state, player(actor), reorder).valid()).isTrue();
        engine.apply(state, player(actor), reorder, new SeededRandomSource(10));
        assertThat(state.getPendingAction()).isNull();
        assertThat(state.getPot().stream().map(NotInMyPotCard::cardId).toList()).containsExactlyElementsOf(chosenOrder);
    }

    @Test
    void emergencyShoppingDrawsThreeThenReturnsExactlyTwoInTopOrder() {
        NotInMyPotGameState state = newGame(4);
        NotInMyPotPlayerState actor = currentPlayer(state);
        NotInMyPotCard actionCard = NotInMyPotCard.action("emergency", NotInMyPotActionType.EMERGENCY_SHOPPING);
        replaceHand(actor, actionCard,
                ingredient("emergency-filler-1", NotInMyPotIngredientType.SALT),
                ingredient("emergency-filler-2", NotInMyPotIngredientType.SALT));
        ensureDrawPile(state, 10);
        List<String> originalDrawTop = state.getDrawPile().stream()
                .limit(5)
                .map(NotInMyPotCard::cardId)
                .toList();

        NotInMyPotAction play = playAction(state, actor, actionCard, null, "emergency-play");
        engine.apply(state, player(actor), play, new SeededRandomSource(11));
        assertThat(state.getPendingAction().type()).isEqualTo(NotInMyPotPendingType.RETURN_SHOPPING_CARDS);
        assertThat(actor.getHand()).hasSize(5);
        assertThat(projector.project(state, player(actor)).pendingAction().allowedCardIds())
                .containsExactlyInAnyOrderElementsOf(actor.getHand().stream().map(NotInMyPotCard::cardId).toList());

        List<String> returned = actor.getHand().subList(0, 2).stream().map(NotInMyPotCard::cardId).toList();
        NotInMyPotAction finish = new NotInMyPotAction(
                NotInMyPotAction.RETURN_SHOPPING_CARDS,
                "emergency-return",
                state.getStateVersion(),
                null,
                null,
                null,
                null,
                returned
        );
        assertThat(engine.validate(state, player(actor), finish).valid()).isTrue();
        engine.apply(state, player(actor), finish, new SeededRandomSource(12));
        assertThat(actor.getHand()).hasSize(3);
        assertThat(state.getDrawPile().getFirst().cardId()).isEqualTo(returned.getFirst());
        assertThat(state.getDrawPile().stream().skip(1).findFirst().orElseThrow().cardId())
                .isEqualTo(returned.get(1));
        assertThat(originalDrawTop).doesNotContain(returned.getFirst(), returned.get(1));
        assertThat(state.getPendingAction()).isNull();
    }

    @Test
    void trashOutReplacesTheTargetHandButKeepsCardsPrivate() {
        NotInMyPotGameState state = newGame(4);
        NotInMyPotPlayerState actor = currentPlayer(state);
        NotInMyPotPlayerState target = otherPlayer(state, actor);
        NotInMyPotCard actionCard = NotInMyPotCard.action("trash", NotInMyPotActionType.TRASH_OUT);
        replaceHand(actor, actionCard,
                ingredient("trash-filler-1", NotInMyPotIngredientType.SALT),
                ingredient("trash-filler-2", NotInMyPotIngredientType.SALT));
        List<String> oldIds = List.of("old-1", "old-2", "old-3");
        replaceHand(target,
                ingredient(oldIds.get(0), NotInMyPotIngredientType.MEAT),
                ingredient(oldIds.get(1), NotInMyPotIngredientType.SALT),
                ingredient(oldIds.get(2), NotInMyPotIngredientType.VEGETABLE));
        ensureDrawPile(state, 10);

        NotInMyPotAction action = playAction(state, actor, actionCard, target.getPlayerId(), "trash-cmd");
        engine.apply(state, player(actor), action, new SeededRandomSource(13));

        assertThat(target.getHand()).hasSize(3);
        assertThat(target.getHand()).extracting(NotInMyPotCard::cardId).doesNotContainAnyElementsOf(oldIds);
        assertThat(state.getDiscardPile()).extracting(NotInMyPotCard::cardId).contains(actionCard.cardId());
        NotInMyPotView observer = projector.project(state, player(otherPlayer(state, actor)));
        assertThat(observer.players()).filteredOn(view -> view.playerId().equals(target.getPlayerId()))
                .singleElement()
                .extracting(view -> view.handCount())
                .isEqualTo(3);
        assertThat(state.getPublicEvents()).filteredOn(event -> "TRASH_OUT_RESOLVED".equals(event.type()))
                .singleElement()
                .extracting(event -> event.payload().keySet())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.COLLECTION)
                .doesNotContain("cardIds");
    }

    @Test
    void potReadyAndDrawPileEmptyUseServerComputedWinConditions() {
        NotInMyPotGameState potState = newGame(4);
        NotInMyPotPlayerState vegetarian = potState.getPlayers().stream()
                .filter(player -> player.getRole() == NotInMyPotRole.VEGETARIAN)
                .findFirst()
                .orElseThrow();
        potState.setCurrentPlayerId(vegetarian.getPlayerId());
        potState.setTurnHasActed(false);
        replaceHand(vegetarian,
                ingredient("pot-ready-hand-1", NotInMyPotIngredientType.SALT),
                ingredient("pot-ready-hand-2", NotInMyPotIngredientType.SALT),
                ingredient("pot-ready-hand-3", NotInMyPotIngredientType.SALT));
        for (int i = 0; i < NotInMyPotRules.targetScore(4); i++) {
            potState.getPot().addFirst(ingredient("ready-" + i, NotInMyPotIngredientType.VEGETABLE));
        }
        ensureDrawPile(potState, 5);
        NotInMyPotAction ready = new NotInMyPotAction(
                NotInMyPotAction.DECLARE_POT_READY,
                "ready-cmd",
                potState.getStateVersion(),
                null,
                null,
                null,
                null,
                List.of()
        );
        assertThat(engine.validate(potState, player(vegetarian), ready).valid()).isTrue();
        engine.apply(potState, player(vegetarian), ready, new SeededRandomSource(14));
        assertThat(potState.isFinished()).isTrue();
        assertThat(potState.getWinnerFaction()).isEqualTo(NotInMyPotRole.VEGETARIAN);
        assertThat(potState.getFinalPotScore()).isEqualTo(NotInMyPotRules.targetScore(4));
        assertThat(potState.getPublicRoles()).hasSize(4);
        assertThat(potState.getWinnerPlayerIds()).containsExactlyElementsOf(
                potState.getPlayers().stream()
                        .filter(player -> player.getRole() == NotInMyPotRole.VEGETARIAN)
                        .map(NotInMyPotPlayerState::getPlayerId)
                        .toList()
        );

        NotInMyPotGameState emptyDrawState = newGame(3);
        NotInMyPotPlayerState current = currentPlayer(emptyDrawState);
        replaceHand(current,
                ingredient("empty-draw-play", NotInMyPotIngredientType.SALT),
                ingredient("empty-draw-filler-1", NotInMyPotIngredientType.SALT),
                ingredient("empty-draw-filler-2", NotInMyPotIngredientType.SALT));
        emptyDrawState.getDrawPile().clear();
        emptyDrawState.getDrawPile().add(ingredient("last-card", NotInMyPotIngredientType.VEGETABLE));
        NotInMyPotAction playLast = new NotInMyPotAction(
                NotInMyPotAction.PLAY_INGREDIENT,
                "last-card-cmd",
                emptyDrawState.getStateVersion(),
                "empty-draw-play",
                "SALT",
                null,
                null,
                List.of()
        );
        engine.apply(emptyDrawState, player(current), playLast, new SeededRandomSource(15));
        assertThat(emptyDrawState.isFinished()).isTrue();
        assertThat(emptyDrawState.getWinnerFaction()).isEqualTo(NotInMyPotRole.MEAT_EATER);
        assertThat(emptyDrawState.getPublicEvents()).anyMatch(event ->
                "DRAW_PILE_EMPTY".equals(event.payload().get("reason")));
    }

    @Test
    void onlyTheVegetarianAtTheBeginningOfATurnMayDeclarePotReady() {
        NotInMyPotGameState state = newGame(3);
        NotInMyPotPlayerState meatEater = state.getPlayers().stream()
                .filter(player -> player.getRole() == NotInMyPotRole.MEAT_EATER)
                .findFirst()
                .orElseThrow();
        state.setCurrentPlayerId(meatEater.getPlayerId());
        ValidationResult meatRejected = engine.validate(
                state,
                player(meatEater),
                new NotInMyPotAction(NotInMyPotAction.DECLARE_POT_READY, "meat-ready", null,
                        null, null, null, null, List.of())
        );
        assertThat(meatRejected.errorCode()).isEqualTo("MEAT_EATER_CANNOT_DECLARE");

        NotInMyPotPlayerState vegetarian = state.getPlayers().stream()
                .filter(player -> player.getRole() == NotInMyPotRole.VEGETARIAN)
                .findFirst()
                .orElseThrow();
        state.setCurrentPlayerId(vegetarian.getPlayerId());
        state.setTurnHasActed(true);
        ValidationResult afterAction = engine.validate(
                state,
                player(vegetarian),
                new NotInMyPotAction(NotInMyPotAction.DECLARE_POT_READY, "late-ready", null,
                        null, null, null, null, List.of())
        );
        assertThat(afterAction.errorCode()).isEqualTo("POT_ALREADY_ACTED");
    }

    @Test
    void duplicateCommandsAreRejectedWithoutASecondStateTransition() {
        NotInMyPotGameState state = newGame(3);
        NotInMyPotPlayerState actor = currentPlayer(state);
        NotInMyPotCard card = actor.getHand().getFirst();
        if (!card.isIngredient()) {
            card = ingredient("idempotent-ingredient", NotInMyPotIngredientType.SALT);
            actor.getHand().set(0, card);
        }
        ensureDrawPile(state, 5);
        NotInMyPotAction action = new NotInMyPotAction(
                NotInMyPotAction.PLAY_INGREDIENT,
                "same-command",
                state.getStateVersion(),
                card.cardId(),
                "SALT",
                null,
                null,
                List.of()
        );
        engine.apply(state, player(actor), action, new SeededRandomSource(16));
        int version = state.getStateVersion();
        ValidationResult duplicate = engine.validate(state, player(actor), action);
        assertThat(duplicate.errorCode()).isEqualTo("DUPLICATE_REQUEST");
        assertThat(engine.apply(state, player(actor), action, new SeededRandomSource(17)).events()).isEmpty();
        assertThat(state.getStateVersion()).isEqualTo(version);
    }

    @Test
    void factionEqualityAndAllMeatEatersExpelledHaveDeterministicPriority() {
        NotInMyPotGameState equalState = newGame(4);
        NotInMyPotPlayerState equalActor = currentPlayer(equalState);
        equalState.getPlayers().stream()
                .filter(player -> player.getRole() == NotInMyPotRole.VEGETARIAN)
                .filter(player -> !player.getPlayerId().equals(equalActor.getPlayerId()))
                .limit(2)
                .forEach(player -> player.setActive(false));
        replaceHand(equalActor,
                ingredient("equal-play", NotInMyPotIngredientType.SALT),
                ingredient("equal-fill-1", NotInMyPotIngredientType.SALT),
                ingredient("equal-fill-2", NotInMyPotIngredientType.SALT));
        ensureDrawPile(equalState, 5);
        engine.apply(equalState, player(equalActor), new NotInMyPotAction(
                NotInMyPotAction.PLAY_INGREDIENT,
                "equal-check",
                equalState.getStateVersion(),
                "equal-play",
                "SALT",
                null,
                null,
                List.of()
        ), new SeededRandomSource(18));
        assertThat(equalState.getWinnerFaction()).isEqualTo(NotInMyPotRole.MEAT_EATER);

        NotInMyPotGameState meatGoneState = newGame(4);
        NotInMyPotPlayerState vegetarianActor = currentPlayer(meatGoneState);
        meatGoneState.getPlayers().stream()
                .filter(player -> player.getRole() == NotInMyPotRole.MEAT_EATER)
                .forEach(player -> player.setActive(false));
        replaceHand(vegetarianActor,
                ingredient("meat-gone-play", NotInMyPotIngredientType.SALT),
                ingredient("meat-gone-fill-1", NotInMyPotIngredientType.SALT),
                ingredient("meat-gone-fill-2", NotInMyPotIngredientType.SALT));
        ensureDrawPile(meatGoneState, 5);
        engine.apply(meatGoneState, player(vegetarianActor), new NotInMyPotAction(
                NotInMyPotAction.PLAY_INGREDIENT,
                "meat-gone-check",
                meatGoneState.getStateVersion(),
                "meat-gone-play",
                "SALT",
                null,
                null,
                List.of()
        ), new SeededRandomSource(19));
        assertThat(meatGoneState.getWinnerFaction()).isEqualTo(NotInMyPotRole.VEGETARIAN);
    }

    @Test
    void timeoutAndReconnectKeepPrivatePendingStateSafe() {
        NotInMyPotGameState state = newGame(4);
        NotInMyPotPlayerState actor = currentPlayer(state);
        NotInMyPotCard actionCard = NotInMyPotCard.action("timeout-slotted", NotInMyPotActionType.SLOTTED_SPOON);
        replaceHand(actor, actionCard,
                ingredient("timeout-filler-1", NotInMyPotIngredientType.SALT),
                ingredient("timeout-filler-2", NotInMyPotIngredientType.SALT));
        state.getPot().addFirst(ingredient("timeout-bottom", NotInMyPotIngredientType.MEAT));
        state.getPot().addFirst(ingredient("timeout-top", NotInMyPotIngredientType.VEGETABLE));
        ensureDrawPile(state, 10);

        engine.apply(state, player(actor), playAction(
                state,
                actor,
                actionCard,
                null,
                "timeout-play"
        ), new SeededRandomSource(20));
        List<String> inspected = projector.project(state, player(actor)).privateInspectedCards().stream()
                .map(card -> card.cardId())
                .toList();
        assertThat(projector.project(state, player(actor)).privateInspectedCards())
                .containsExactlyElementsOf(projector.project(state, player(actor)).privateInspectedCards());
        assertThat(projector.project(state, player(otherPlayer(state, actor))).privateInspectedCards()).isEmpty();

        var pending = state.getPendingAction();
        state.setPendingAction(new com.partygameonline.game.notinmypot.domain.NotInMyPotPendingAction(
                pending.type(),
                pending.actorPlayerId(),
                pending.sourceCard(),
                pending.inspectedCards(),
                pending.allowedTargetPlayerIds(),
                pending.startedAt(),
                Instant.now().minusSeconds(1)
        ));
        NotInMyPotAction timeout = new NotInMyPotAction(
                NotInMyPotAction.TIMEOUT,
                "timeout-cmd",
                state.getStateVersion(),
                null,
                null,
                null,
                null,
                List.of()
        );
        assertThat(engine.validate(state, player(actor), timeout).valid()).isTrue();
        engine.apply(state, player(actor), timeout, new SeededRandomSource(21));
        assertThat(state.getPendingAction()).isNull();
        assertThat(state.getPot().stream().map(NotInMyPotCard::cardId).toList())
                .containsExactlyElementsOf(inspected);
    }

    @Test
    void abandonmentSkipsThePlayerAndDoesNotExposeTheirRoleBeforeGameEnd() {
        NotInMyPotGameState state = newGame(4);
        NotInMyPotPlayerState actor = state.getPlayers().stream()
                .filter(player -> player.getRole() == NotInMyPotRole.VEGETARIAN)
                .findFirst()
                .orElseThrow();
        state.setCurrentPlayerId(actor.getPlayerId());
        String actorRole = actor.getRole().name();
        List<?> events = engine.onPlayerAbandoned(
                state,
                player(actor),
                new SeededRandomSource(22)
        ).events();

        assertThat(actor.isActive()).isFalse();
        assertThat(state.getPublicRoles()).doesNotContainKey(actor.getPlayerId());
        assertThat(state.getCurrentPlayerId()).isNotEqualTo(actor.getPlayerId());
        assertThat(events).anySatisfy(event -> assertThat(
                ((com.partygameonline.game.notinmypot.domain.NotInMyPotEvent) event).payload()
        ).doesNotContainValue(actorRole));
    }

    private NotInMyPotGameState newGame(int playerCount) {
        Map<String, String> names = new HashMap<>();
        List<String> playerIds = new ArrayList<>();
        for (int index = 0; index < playerCount; index++) {
            String id = "player-" + index;
            playerIds.add(id);
            names.put(id, "Player " + index);
        }
        return engine.createGame(
                new GameConfig(NotInMyPotGameManifest.ID, "ROOM", playerIds, names, 1L),
                new SeededRandomSource(100L + playerCount)
        );
    }

    private static NotInMyPotPlayerState currentPlayer(NotInMyPotGameState state) {
        return state.requirePlayer(state.getCurrentPlayerId());
    }

    private static NotInMyPotPlayerState otherPlayer(
            NotInMyPotGameState state,
            NotInMyPotPlayerState player
    ) {
        return state.getPlayers().stream()
                .filter(candidate -> !candidate.getPlayerId().equals(player.getPlayerId()))
                .findFirst()
                .orElseThrow();
    }

    private static PlayerContext player(NotInMyPotPlayerState player) {
        return PlayerContext.player(player.getPlayerId(), player.getDisplayName());
    }

    private static NotInMyPotAction playAction(
            NotInMyPotGameState state,
            NotInMyPotPlayerState actor,
            NotInMyPotCard card,
            String targetId,
            String commandId
    ) {
        return new NotInMyPotAction(
                NotInMyPotAction.PLAY_ACTION,
                commandId,
                state.getStateVersion(),
                card.cardId(),
                null,
                card.actionType().name(),
                targetId,
                List.of()
        );
    }

    private static NotInMyPotCard ingredient(String id, NotInMyPotIngredientType type) {
        return NotInMyPotCard.ingredient(id, type);
    }

    private static void replaceHand(NotInMyPotPlayerState player, NotInMyPotCard... cards) {
        player.getHand().clear();
        player.getHand().addAll(List.of(cards));
    }

    private static void ensureDrawPile(NotInMyPotGameState state, int count) {
        state.getDrawPile().clear();
        for (int index = 0; index < count; index++) {
            state.getDrawPile().add(ingredient("draw-" + index + "-" + count, NotInMyPotIngredientType.SALT));
        }
    }
}
