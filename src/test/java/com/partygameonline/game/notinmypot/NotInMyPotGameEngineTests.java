package com.partygameonline.game.notinmypot;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.GameEloChange;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.RandomSource;
import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.game.notinmypot.api.dto.NotInMyPotView;
import com.partygameonline.game.notinmypot.api.dto.NotInMyPotCardView;
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
import com.partygameonline.game.notinmypot.domain.NotInMyPotSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NotInMyPotGameEngineTests {

    private final NotInMyPotGameEngine engine = new NotInMyPotGameEngine();
    private final NotInMyPotGameProjector projector = new NotInMyPotGameProjector();

    @Test
    void createsTheConfiguredDeckAndRoleDistributionForEveryPlayerCount() {
        for (int playerCount = 3; playerCount <= 8; playerCount++) {
            NotInMyPotGameState state = newPreparingGame(playerCount);
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
            assertThat(state.getPhase()).isEqualTo(NotInMyPotPhase.ROLE_REVEAL);
            assertThat(state.getCurrentPlayerId()).isNull();
            assertThat(state.getTurnDeadline()).isAfter(Instant.now());
            assertThat(state.getPublicRoles()).isEmpty();
            assertThat(state.getPlayers().stream()
                    .flatMap(player -> player.getHand().stream())
                    .count() + state.getDrawPile().size())
                    .isEqualTo(deckSize);
        }
    }

    @Test
    void startsTheFirstTurnOnlyAfterTheFifteenSecondRoleRevealExpires() {
        NotInMyPotGameState state = newPreparingGame(4);
        NotInMyPotPlayerState timeoutActor = state.activePlayers().getFirst();

        assertThat(state.getPhase()).isEqualTo(NotInMyPotPhase.ROLE_REVEAL);
        assertThat(state.getCurrentPlayerId()).isNull();
        assertThat(state.getPublicEvents())
                .extracting(event -> event.type())
                .doesNotContain("TURN_STARTED");
        assertThat(projector.project(state, player(timeoutActor)).canAct()).isFalse();

        state.setTurnDeadline(Instant.now().minusSeconds(1));
        NotInMyPotAction timeout = new NotInMyPotAction(
                NotInMyPotAction.TIMEOUT,
                "role-reveal-timeout",
                state.getStateVersion(),
                null,
                null,
                null,
                null,
                List.of()
        );

        assertThat(engine.validate(state, player(timeoutActor), timeout).valid()).isTrue();
        engine.apply(state, player(timeoutActor), timeout, new SeededRandomSource(17L));

        assertThat(state.getPhase()).isEqualTo(NotInMyPotPhase.PLAYING);
        assertThat(state.getCurrentPlayerId()).isNotBlank();
        assertThat(state.getTurnNumber()).isEqualTo(1);
        assertThat(state.getTurnDeadline()).isAfter(Instant.now());
        assertThat(state.getPublicEvents())
                .extracting(event -> event.type())
                .contains("TURN_STARTED");
    }

    @Test
    void appliesRoomTurnSettingsAndProjectsHistoryVisibility() {
        Map<String, Object> roomSettings = Map.of(
                "notInMyPot", Map.of("turnSeconds", 45, "showActionHistory", false)
        );
        List<String> playerIds = List.of("player-0", "player-1", "player-2");
        Map<String, String> displayNames = Map.of(
                "player-0", "Player 0",
                "player-1", "Player 1",
                "player-2", "Player 2"
        );
        NotInMyPotGameState state = engine.createGame(
                new GameConfig(NotInMyPotGameManifest.ID, "ROOM", playerIds, displayNames, 7L, roomSettings),
                new SeededRandomSource(7L)
        );
        finishPreparation(state, new SeededRandomSource(8L));

        assertThat(state.getSettings()).isEqualTo(new NotInMyPotSettings(45, false));
        assertThat(state.getTurnDeadline()).isAfter(Instant.now());
        assertThat(projector.project(state, player(state.getPlayers().getFirst())).actionHistoryVisible()).isFalse();
        assertThat(projector.project(state, player(state.getPlayers().getFirst())).publicEvents()).isEmpty();

        NotInMyPotPlayerState actor = currentPlayer(state);
        NotInMyPotCard playedCard = ingredient("hidden-history-card", NotInMyPotIngredientType.VEGETABLE);
        replaceHand(actor, playedCard,
                ingredient("hidden-history-filler-1", NotInMyPotIngredientType.SALT),
                ingredient("hidden-history-filler-2", NotInMyPotIngredientType.SALT));
        NotInMyPotAction play = new NotInMyPotAction(
                NotInMyPotAction.PLAY_INGREDIENT,
                "hidden-history-play",
                state.getStateVersion(),
                playedCard.cardId(),
                null,
                null,
                null,
                List.of()
        );
        assertThat(engine.apply(state, player(actor), play, new SeededRandomSource(8L)).events()).isEmpty();
    }

    @Test
    void anExpiredNormalTurnRandomlyPlaysOneIngredientAndAdvancesToTheNextPlayer() {
        NotInMyPotGameState state = newGame(3);
        NotInMyPotPlayerState actor = currentPlayer(state);
        String previousPlayerId = actor.getPlayerId();
        NotInMyPotCard selected = ingredient("timeout-selected", NotInMyPotIngredientType.SALT);
        replaceHand(actor,
                ingredient("timeout-first", NotInMyPotIngredientType.VEGETABLE),
                selected,
                ingredient("timeout-last", NotInMyPotIngredientType.MEAT));
        ensureDrawPile(state, 5);
        state.setTurnDeadline(Instant.now().minusSeconds(1));
        NotInMyPotAction timeout = new NotInMyPotAction(
                NotInMyPotAction.TIMEOUT,
                "normal-turn-timeout",
                state.getStateVersion(),
                null,
                null,
                null,
                null,
                List.of()
        );

        assertThat(engine.validate(state, player(state.requirePlayer(previousPlayerId)), timeout).valid()).isTrue();
        engine.apply(state, player(state.requirePlayer(previousPlayerId)), timeout, new FixedRandomSource(1));

        assertThat(state.getPot().getFirst()).isEqualTo(selected);
        assertThat(actor.getHand()).extracting(NotInMyPotCard::cardId)
                .doesNotContain(selected.cardId());
        assertThat(state.getCurrentPlayerId()).isNotEqualTo(previousPlayerId);
        assertThat(state.getPublicEvents()).anySatisfy(event -> {
            if ("TURN_TIMED_OUT".equals(event.type())) {
                assertThat(event.payload())
                        .containsEntry("playerId", previousPlayerId)
                        .containsEntry("automatic", true);
            }
        });
    }

    @ParameterizedTest
    @EnumSource(NotInMyPotIngredientType.class)
    void timeoutCanAutomaticallyPlayEveryIngredientType(NotInMyPotIngredientType type) {
        NotInMyPotGameState state = newGame(4);
        NotInMyPotPlayerState actor = currentPlayer(state);
        NotInMyPotCard selected = ingredient("timeout-ingredient-" + type, type);
        replaceHand(actor,
                ingredient("timeout-ingredient-first-" + type, NotInMyPotIngredientType.SALT),
                selected,
                ingredient("timeout-ingredient-last-" + type, NotInMyPotIngredientType.SALT));
        ensureDrawPile(state, 10);
        selectPreferredCard(state, actor, selected.cardId(), "prefer-ingredient-" + type);

        applyExpiredTurn(state, actor, new FixedRandomSource(0), "timeout-ingredient-command-" + type);

        assertThat(state.getPot().getFirst()).isEqualTo(selected);
        assertThat(state.getPublicEvents()).anyMatch(event -> "INGREDIENT_DECLARED".equals(event.type()));
    }

    @ParameterizedTest
    @EnumSource(NotInMyPotActionType.class)
    void timeoutCanAutomaticallyPlayEveryActionCardType(NotInMyPotActionType type) {
        NotInMyPotGameState state = newGame(4);
        NotInMyPotPlayerState actor = currentPlayer(state);
        NotInMyPotCard first = NotInMyPotCard.action("timeout-action-first-" + type, type);
        NotInMyPotCard selected = NotInMyPotCard.action("timeout-action-selected-" + type, type);
        NotInMyPotCard last = NotInMyPotCard.action("timeout-action-last-" + type, type);
        replaceHand(actor, first, selected, last);
        state.getPot().addFirst(ingredient("timeout-pot-bottom-" + type, NotInMyPotIngredientType.MEAT));
        state.getPot().addFirst(ingredient("timeout-pot-top-" + type, NotInMyPotIngredientType.VEGETABLE));
        ensureDrawPile(state, 20);
        selectPreferredCard(state, actor, selected.cardId(), "prefer-action-" + type);

        applyExpiredTurn(state, actor, new FixedRandomSource(0), "timeout-action-command-" + type);

        assertThat(actor.getHand()).extracting(NotInMyPotCard::cardId).doesNotContain(selected.cardId());
        assertThat(state.getDiscardPile()).extracting(NotInMyPotCard::cardId).contains(selected.cardId());
        assertThat(state.getPublicEvents()).filteredOn(event -> "ACTION_STARTED".equals(event.type()))
                .singleElement()
                .satisfies(event -> assertThat(event.payload()).containsEntry("actionType", type.name()));

        switch (type) {
            case OUT_OF_HOUSE -> assertThat(state.getPlayers().stream()
                    .mapToInt(player -> state.doorCount(player.getPlayerId()))
                    .sum()).isEqualTo(1);
            case SCOOP_OUT -> assertThat(state.getPublicEvents())
                    .anyMatch(event -> "SCOOP_OUT_RESOLVED".equals(event.type()));
            case SLOTTED_SPOON -> assertThat(state.getPendingAction().type())
                    .isEqualTo(NotInMyPotPendingType.INSPECT_SHUFFLED_POT);
            case EMERGENCY_SHOPPING -> assertThat(state.getPendingAction().type())
                    .isEqualTo(NotInMyPotPendingType.RETURN_SHOPPING_CARDS);
            case TRASH_OUT -> assertThat(state.getPublicEvents())
                    .anyMatch(event -> "TRASH_OUT_RESOLVED".equals(event.type()));
        }
    }

    @Test
    void preferredCardCanBeChangedWithoutConsumingTheTurnOrMakingTheViewStale() {
        NotInMyPotGameState state = newGame(3);
        NotInMyPotPlayerState actor = currentPlayer(state);
        NotInMyPotCard preferred = actor.getHand().get(1);
        int versionBeforeSelection = state.getStateVersion();

        selectPreferredCard(state, actor, preferred.cardId(), "prefer-card");

        assertThat(state.getPreferredCardId()).isEqualTo(preferred.cardId());
        assertThat(state.getStateVersion()).isEqualTo(versionBeforeSelection);
        assertThat(state.getCurrentPlayerId()).isEqualTo(actor.getPlayerId());

        selectPreferredCard(state, actor, null, "clear-preferred-card");

        assertThat(state.getPreferredCardId()).isNull();
        assertThat(state.getStateVersion()).isEqualTo(versionBeforeSelection);
    }

    @Test
    void playingAnIngredientUsesItsActualTypeWithoutPublishingItAndRejectsAMismatchedClientType() {
        NotInMyPotGameState state = newGame(3);
        NotInMyPotPlayerState actor = currentPlayer(state);
        NotInMyPotCard meat = ingredient("fixed-meat", NotInMyPotIngredientType.MEAT);
        replaceHand(actor, meat, ingredient("fixed-salt", NotInMyPotIngredientType.SALT),
                ingredient("fixed-veg", NotInMyPotIngredientType.VEGETABLE));
        ensureDrawPile(state, 3);

        NotInMyPotAction mismatched = new NotInMyPotAction(
                NotInMyPotAction.PLAY_INGREDIENT,
                "ingredient-mismatch",
                state.getStateVersion(),
                meat.cardId(),
                "VEGETABLE",
                null,
                null,
                List.of()
        );
        ValidationResult rejected = engine.validate(state, player(actor), mismatched);
        assertThat(rejected.valid()).isFalse();
        assertThat(rejected.errorCode()).isEqualTo("INGREDIENT_TYPE_MISMATCH");

        NotInMyPotAction action = new NotInMyPotAction(
                NotInMyPotAction.PLAY_INGREDIENT,
                "ingredient-1",
                state.getStateVersion(),
                meat.cardId(),
                "MEAT",
                null,
                null,
                List.of()
        );
        assertThat(engine.validate(state, player(actor), action).valid()).isTrue();
        List<?> events = engine.apply(state, player(actor), action, new SeededRandomSource(4)).events();

        assertThat(state.getPot().getFirst()).isEqualTo(meat);
        assertThat(state.getPublicEvents()).filteredOn(event -> "CARDS_DRAWN".equals(event.type()))
                .singleElement()
                .satisfies(event -> assertThat(event.payload())
                        .containsEntry("playerId", actor.getPlayerId())
                        .containsEntry("drawnCount", 1)
                        .containsEntry("reason", "TURN_REFILL"));
        assertThat(events).anySatisfy(event -> {
            var typed = (com.partygameonline.game.notinmypot.domain.NotInMyPotEvent) event;
            if ("INGREDIENT_DECLARED".equals(typed.type())) {
                assertThat(typed.payload())
                        .containsEntry("playerId", actor.getPlayerId())
                        .doesNotContainKeys("declaredType", "actualType");
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
            if ("PLAYER_DOOR_UPDATED".equals(event.type())) {
                assertThat(event.payload())
                        .containsEntry("actorPlayerId", actor.getPlayerId())
                        .containsEntry("playerId", target.getPlayerId());
            }
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
    void slottedSpoonRandomizesOnlyTheThreeLifoCardsAndShowsThemWithoutPlayerReordering() {
        NotInMyPotGameState state = newGame(4);
        NotInMyPotPlayerState actor = currentPlayer(state);
        NotInMyPotCard actionCard = NotInMyPotCard.action("slotted", NotInMyPotActionType.SLOTTED_SPOON);
        replaceHand(actor, actionCard,
                ingredient("slotted-filler-1", NotInMyPotIngredientType.SALT),
                ingredient("slotted-filler-2", NotInMyPotIngredientType.SALT));
        NotInMyPotCard oldestUntouched = ingredient("slotted-oldest", NotInMyPotIngredientType.MEAT);
        NotInMyPotCard thirdLatest = ingredient("slotted-third-latest", NotInMyPotIngredientType.SALT);
        NotInMyPotCard secondLatest = ingredient("slotted-second-latest", NotInMyPotIngredientType.VEGETABLE);
        NotInMyPotCard latest = ingredient("slotted-latest", NotInMyPotIngredientType.MEAT);
        state.getPot().addFirst(oldestUntouched);
        state.getPot().addFirst(thirdLatest);
        state.getPot().addFirst(secondLatest);
        state.getPot().addFirst(latest);
        ensureDrawPile(state, 10);

        NotInMyPotAction play = playAction(state, actor, actionCard, null, "slotted-play");
        engine.apply(state, player(actor), play, new SeededRandomSource(9));
        assertThat(state.getPhase()).isEqualTo(NotInMyPotPhase.RESOLVING_ACTION);
        assertThat(state.getPendingAction()).isNotNull();
        assertThat(state.getPendingAction().type()).isEqualTo(NotInMyPotPendingType.INSPECT_SHUFFLED_POT);
        assertThat(projector.project(state, player(actor)).privateInspectedCards())
                .extracting(NotInMyPotCardView::cardId)
                .containsExactlyInAnyOrder(latest.cardId(), secondLatest.cardId(), thirdLatest.cardId());
        assertThat(projector.project(state, player(otherPlayer(state, actor))).privateInspectedCards()).isEmpty();
        assertThat(state.getPot().getLast()).isEqualTo(oldestUntouched);
        assertThat(state.getPot().stream().limit(3).toList())
                .containsExactlyInAnyOrder(latest, secondLatest, thirdLatest);
        List<String> serverChosenOrder = state.getPot().stream().map(NotInMyPotCard::cardId).toList();

        NotInMyPotAction acknowledge = new NotInMyPotAction(
                NotInMyPotAction.ACKNOWLEDGE_SLOTTED_SPOON,
                "slotted-acknowledge",
                state.getStateVersion(),
                null,
                null,
                null,
                null,
                List.of()
        );
        assertThat(engine.validate(state, player(actor), acknowledge).valid()).isTrue();
        engine.apply(state, player(actor), acknowledge, new SeededRandomSource(10));
        assertThat(state.getPendingAction()).isNull();
        assertThat(state.getPot().stream().map(NotInMyPotCard::cardId).toList())
                .containsExactlyElementsOf(serverChosenOrder);
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
        assertThat(state.getPublicEvents()).filteredOn(event -> "CARDS_DRAWN".equals(event.type()))
                .singleElement()
                .satisfies(event -> assertThat(event.payload())
                        .containsEntry("playerId", actor.getPlayerId())
                        .containsEntry("drawnCount", 3)
                        .containsEntry("reason", "EMERGENCY_SHOPPING"));
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
        assertThat(state.getPublicEvents())
                .filteredOn(event -> "CARDS_DRAWN".equals(event.type())
                        && "TRASH_REPLACEMENT".equals(event.payload().get("reason")))
                .singleElement()
                .satisfies(event -> assertThat(event.payload())
                        .containsEntry("playerId", target.getPlayerId())
                        .containsEntry("drawnCount", 3));
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
        assertThat(projector.project(potState, player(vegetarian)).finalPot()).isNotEmpty();
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
        assertThat(emptyDrawState.getFinalPotScore()).isNull();
        assertThat(emptyDrawState.playerOutcome(current.getPlayerId()).score()).isNull();
        assertThat(projector.project(emptyDrawState, player(current)).finalPot()).isEmpty();
        assertThat(emptyDrawState.getPublicEvents()).noneMatch(event -> "POT_REVEALED".equals(event.type()));
        assertThat(emptyDrawState.getPublicEvents()).anyMatch(event ->
                "DRAW_PILE_EMPTY".equals(event.payload().get("reason")));
        assertThat(emptyDrawState.getPublicEvents()).filteredOn(event -> "GAME_ENDED".equals(event.type()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.payload()).containsEntry("potRevealed", false);
                    assertThat(event.payload()).doesNotContainKey("finalScore");
                });
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
                card.ingredientType().name(),
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
        assertThat(equalState.getFinalPotScore()).isNull();
        assertThat(projector.project(equalState, player(equalActor)).finalPot()).isEmpty();
        assertThat(equalState.getPublicEvents()).filteredOn(event -> "GAME_ENDED".equals(event.type()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.payload()).containsEntry("reason", "FACTIONS_EQUAL");
                    assertThat(event.payload()).containsEntry("potRevealed", false);
                    assertThat(event.payload()).doesNotContainKey("finalScore");
                });

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
        assertThat(meatGoneState.getFinalPotScore()).isNull();
        assertThat(projector.project(meatGoneState, player(vegetarianActor)).finalPot()).isEmpty();
        assertThat(meatGoneState.getPublicEvents()).noneMatch(event -> "POT_REVEALED".equals(event.type()));
        assertThat(meatGoneState.getPublicEvents()).filteredOn(event -> "GAME_ENDED".equals(event.type()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.payload()).containsEntry("reason", "ALL_MEAT_EATERS_EXPELLED");
                    assertThat(event.payload()).containsEntry("potRevealed", false);
                    assertThat(event.payload()).doesNotContainKey("finalScore");
                });
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

    @Test
    void keepsEveryForfeitDeltaWhenFinalEloChangesAreRecorded() {
        NotInMyPotGameState state = newGame(4);

        state.recordEloChanges(Map.of(
                "player-0",
                new GameEloChange("player-0", false, 5000, -100, 4900),
                "player-1",
                new GameEloChange("player-1", false, 5100, -100, 5000)
        ));
        state.recordEloChanges(Map.of(
                "player-2",
                new GameEloChange("player-2", true, 5000, 50, 5050),
                "player-3",
                new GameEloChange("player-3", false, 5000, -50, 4950)
        ));

        assertThat(state.getEloChanges()).hasSize(4);
        assertThat(state.getEloChanges().get("player-0").eloDelta()).isEqualTo(-100);
        assertThat(state.getEloChanges().get("player-1").eloDelta()).isEqualTo(-100);
        assertThat(state.getEloChanges().get("player-2").eloDelta()).isEqualTo(50);
        assertThat(state.getEloChanges().get("player-3").eloDelta()).isEqualTo(-50);
    }

    private NotInMyPotGameState newGame(int playerCount) {
        NotInMyPotGameState state = newPreparingGame(playerCount);
        finishPreparation(state, new SeededRandomSource(200L + playerCount));
        return state;
    }

    private NotInMyPotGameState newPreparingGame(int playerCount) {
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

    private void finishPreparation(NotInMyPotGameState state, RandomSource random) {
        NotInMyPotPlayerState timeoutActor = state.activePlayers().getFirst();
        state.setTurnDeadline(Instant.now().minusSeconds(1));
        NotInMyPotAction timeout = new NotInMyPotAction(
                NotInMyPotAction.TIMEOUT,
                "finish-preparation-" + state.getRoomId() + "-" + state.getStateVersion(),
                state.getStateVersion(),
                null,
                null,
                null,
                null,
                List.of()
        );
        assertThat(engine.validate(state, player(timeoutActor), timeout).valid()).isTrue();
        engine.apply(state, player(timeoutActor), timeout, random);
    }

    private void selectPreferredCard(
            NotInMyPotGameState state,
            NotInMyPotPlayerState actor,
            String cardId,
            String commandId
    ) {
        NotInMyPotAction preference = new NotInMyPotAction(
                NotInMyPotAction.SET_PREFERRED_CARD,
                commandId,
                state.getStateVersion(),
                cardId,
                null,
                null,
                null,
                List.of()
        );
        assertThat(engine.validate(state, player(actor), preference).valid()).isTrue();
        engine.apply(state, player(actor), preference, new SeededRandomSource(91L));
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

    private void applyExpiredTurn(
            NotInMyPotGameState state,
            NotInMyPotPlayerState actor,
            RandomSource random,
            String commandId
    ) {
        state.setTurnDeadline(Instant.now().minusSeconds(1));
        NotInMyPotAction timeout = new NotInMyPotAction(
                NotInMyPotAction.TIMEOUT,
                commandId,
                state.getStateVersion(),
                null,
                null,
                null,
                null,
                List.of()
        );
        assertThat(engine.validate(state, player(actor), timeout).valid()).isTrue();
        engine.apply(state, player(actor), timeout, random);
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

    private record FixedRandomSource(int index) implements RandomSource {

        @Override
        public int nextInt(int bound) {
            return Math.floorMod(index, bound);
        }

        @Override
        public long nextLong() {
            return index;
        }
    }
}
