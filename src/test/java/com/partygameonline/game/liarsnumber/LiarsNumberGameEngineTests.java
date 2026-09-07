package com.partygameonline.game.liarsnumber;

import static org.assertj.core.api.Assertions.assertThat;

import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.SeededRandomSource;
import com.partygameonline.game.liarsnumber.application.LiarsNumberRules;
import com.partygameonline.game.liarsnumber.api.dto.LiarsNumberView;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberAction;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberActionType;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberCard;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberGameState;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberPhase;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberPlayerState;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiarsNumberGameEngineTests {

    private final LiarsNumberGameEngine engine = new LiarsNumberGameEngine();
    private final LiarsNumberGameProjector projector = new LiarsNumberGameProjector();

    @Test
    void deckHasSevenNormalAndOneRomanCardForEachNumber() {
        List<LiarsNumberCard> deck = LiarsNumberRules.createDeck();

        assertThat(deck).hasSize(64);
        for (int type = 1; type <= 8; type++) {
            int cardType = type;
            assertThat(deck).filteredOn(card -> card.typeId() == cardType && card.variant().name().equals("NORMAL"))
                    .hasSize(7);
            assertThat(deck).filteredOn(card -> card.typeId() == cardType && card.variant().name().equals("ROMAN"))
                    .hasSize(1);
        }
    }

    @Test
    void twoPlayerSetupRemovesTenCardsAndUsesFivePointThreshold() {
        LiarsNumberGameState state = engine.createGame(config("p1", "p2"), new SeededRandomSource(7L));

        assertThat(state.getRemovedCards()).hasSize(10);
        assertThat(state.getPlayers()).allSatisfy(player -> assertThat(player.getHand()).hasSize(27));
        assertThat(state.threshold()).isEqualTo(5);
        assertThat(state.getPhase()).isEqualTo(LiarsNumberPhase.SELECT_CARD);
    }

    @Test
    void threePlayerSetupDealsEntireDeckAndKeepsTenCardsInPlay() {
        LiarsNumberGameState state = engine.createGame(config("p1", "p2", "p3"), new SeededRandomSource(8L));

        assertThat(state.getRemovedCards()).isEmpty();
        assertThat(state.getPlayers().stream().mapToInt(player -> player.getHand().size()).sum()).isEqualTo(64);
        assertThat(state.getPlayers().stream().mapToInt(player -> player.getHand().size()).max()).hasValue(22);
        assertThat(state.getPlayers().stream().mapToInt(player -> player.getHand().size()).min()).hasValue(21);
        assertThat(state.threshold()).isEqualTo(4);
    }

    @Test
    void turnTimerIsUnlimitedByDefaultAndAcceptsFiveSecondStepsUpToThirty() {
        LiarsNumberGameState defaultState = engine.createGame(config("p1", "p2"), new SeededRandomSource(9L));

        assertThat(defaultState.getTurnSeconds()).isZero();
        assertThat(defaultState.getTurnDeadline()).isNull();

        LiarsNumberGameState timedState = engine.createGame(
                configWithSettings(Map.of("liarsNumber", Map.of("turnSeconds", 15))),
                new SeededRandomSource(10L)
        );

        assertThat(timedState.getTurnSeconds()).isEqualTo(15);
        assertThat(timedState.getTurnDeadline()).isBetween(
                Instant.now().plusSeconds(13),
                Instant.now().plusSeconds(16)
        );
        assertThat(Duration.between(Instant.now(), timedState.getTurnDeadline()).toSeconds()).isBetween(13L, 15L);
    }

    @Test
    void invalidTurnTimerFallsBackToUnlimited() {
        LiarsNumberSettings settings = LiarsNumberSettings.fromMap(Map.of("turnSeconds", 7));

        assertThat(settings.turnSeconds()).isZero();
        assertThat(LiarsNumberSettings.fromMap(Map.of("turnSeconds", 30)).turnSeconds()).isEqualTo(30);
    }

    @Test
    void expiredTurnAutomaticallyChoosesAValidAction() {
        LiarsNumberGameState state = engine.createGame(
                configWithSettings(Map.of("liarsNumber", Map.of("turnSeconds", 5))),
                new SeededRandomSource(11L)
        );
        String actorId = state.currentActorId();
        state.resetTurnDeadline(Instant.now().minusSeconds(10));
        LiarsNumberAction timeout = action(LiarsNumberActionType.TIMEOUT, null, null, null, "timeout");

        assertThat(engine.validate(state, player(actorId), timeout).valid()).isTrue();
        engine.apply(state, player(actorId), timeout, new SeededRandomSource(12L));

        assertThat(state.getPhase()).isEqualTo(LiarsNumberPhase.SELECT_TARGET);
        assertThat(state.getActiveRound()).isNotNull();
        assertThat(state.getTurnDeadline()).isAfter(Instant.now());
    }

    @Test
    void twoPlayerGamesRejectPeekAndPassAndAFalseGuessPenalizesReceiver() {
        LiarsNumberGameState state = engine.createGame(config("p1", "p2"), new SeededRandomSource(2L));
        String starter = state.getCurrentRoundStarterId();
        String receiver = starter.equals("p1") ? "p2" : "p1";
        LiarsNumberCard card = state.findPlayer(starter).getHand().getFirst();

        apply(state, starter, action(LiarsNumberActionType.SELECT_CARD, card.cardId(), null, null, "select"));
        assertThat(engine.validate(state, player(receiver), action(LiarsNumberActionType.PEEK_AND_PASS, null, null, null, "peek")).valid())
                .isFalse();
        apply(state, starter, action(LiarsNumberActionType.SELECT_TARGET, null, receiver, null, "target"));
        apply(state, starter, action(LiarsNumberActionType.DECLARE_TYPE, null, null, card.typeId(), "declare"));
        apply(state, receiver, action(LiarsNumberActionType.GUESS, null, null, null, "guess", "FALSE"));

        assertThat(state.getLastPenaltyPlayerId()).isEqualTo(receiver);
        assertThat(state.findPlayer(receiver).getPenaltyCards()).containsExactly(card);
        assertThat(state.getPhase()).isEqualTo(LiarsNumberPhase.SELECT_CARD);
        assertThat(state.getCurrentRoundStarterId()).isEqualTo(receiver);
    }

    @Test
    void cardStaysFaceDownForOtherPlayersUntilTheGuessResolves() {
        LiarsNumberGameState state = engine.createGame(config("p1", "p2", "p3"), new SeededRandomSource(12L));
        String starter = state.getCurrentRoundStarterId();
        String receiver = state.getPlayers().stream().map(LiarsNumberPlayerState::getPlayerId)
                .filter(id -> !id.equals(starter)).findFirst().orElseThrow();
        LiarsNumberCard card = state.findPlayer(starter).getHand().getFirst();

        apply(state, starter, action(LiarsNumberActionType.SELECT_CARD, card.cardId(), null, null, "hide-select"));
        apply(state, starter, action(LiarsNumberActionType.SELECT_TARGET, null, receiver, null, "hide-target"));

        LiarsNumberView senderView = projector.project(state, player(starter));
        LiarsNumberView receiverView = projector.project(state, player(receiver));
        assertThat(senderView.activeRound().card().faceUp()).isTrue();
        assertThat(receiverView.activeRound().card().faceUp()).isFalse();
        assertThat(receiverView.activeRound().card().typeId()).isNull();
    }

    @Test
    void penaltyBelongsToTheMostRecentSenderAfterAPass() {
        LiarsNumberGameState state = engine.createGame(config("p1", "p2", "p3"), new SeededRandomSource(13L));
        String starter = state.getCurrentRoundStarterId();
        String firstReceiver = state.getPlayers().stream().map(LiarsNumberPlayerState::getPlayerId)
                .filter(id -> !id.equals(starter)).findFirst().orElseThrow();
        String secondReceiver = state.getPlayers().stream().map(LiarsNumberPlayerState::getPlayerId)
                .filter(id -> !id.equals(starter) && !id.equals(firstReceiver)).findFirst().orElseThrow();
        LiarsNumberCard card = state.findPlayer(starter).getHand().getFirst();

        apply(state, starter, action(LiarsNumberActionType.SELECT_CARD, card.cardId(), null, null, "pass-select"));
        apply(state, starter, action(LiarsNumberActionType.SELECT_TARGET, null, firstReceiver, null, "pass-target"));
        apply(state, starter, action(LiarsNumberActionType.DECLARE_TYPE, null, null, card.typeId(), "pass-declare"));
        apply(state, firstReceiver, action(LiarsNumberActionType.PEEK_AND_PASS, null, null, null, "pass-peek"));
        apply(state, firstReceiver, action(LiarsNumberActionType.SELECT_PASS_TARGET, null, secondReceiver, null, "pass-next"));
        apply(state, firstReceiver, action(LiarsNumberActionType.PASS_DECLARE_TYPE, null, null, card.typeId(), "pass-claim"));
        apply(state, secondReceiver, action(LiarsNumberActionType.GUESS, null, null, null, "pass-guess", "TRUE"));

        assertThat(state.getLastPenaltyPlayerId()).isEqualTo(firstReceiver);
        assertThat(state.findPlayer(firstReceiver).getPenaltyCards()).containsExactly(card);
        assertThat(state.findPlayer(starter).getPenaltyCards()).isEmpty();
    }

    @Test
    void romanPenaltyCountsAsTwoPointsForItsNumberOnly() {
        LiarsNumberPlayerState player = new LiarsNumberPlayerState("p1", "P1", 0);
        LiarsNumberCard roman = new LiarsNumberCard("roman-3", 3, com.partygameonline.game.liarsnumber.domain.LiarsNumberCardVariant.ROMAN);
        player.getPenaltyCards().add(roman);

        assertThat(player.penaltyScoreForType(3)).isEqualTo(2);
        assertThat(player.penaltyScoreForType(4)).isZero();
    }

    @Test
    void abandoningPlayerImmediatelyLosesAndPublishesTheGameOverEvent() {
        LiarsNumberGameState state = engine.createGame(config("p1", "p2", "p3"), new SeededRandomSource(14L));

        var result = engine.onPlayerAbandoned(state, player("p2"), new SeededRandomSource(15L));

        assertThat(result.finished()).isTrue();
        assertThat(result.winnerPlayerId()).isIn("p1", "p3");
        assertThat(state.getLoserId()).isEqualTo("p2");
        assertThat(state.getGameOverReason()).isEqualTo("ABANDONED");
        assertThat(state.getWinnerPlayerIds()).containsExactlyInAnyOrder("p1", "p3");
        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("LIARS_NUMBER_GAME_OVER");
            assertThat(event.payload()).containsEntry("loserId", "p2").containsEntry("reason", "ABANDONED");
        });
    }

    private static GameConfig config(String... ids) {
        return new GameConfig(
                LiarsNumberGameManifest.ID,
                "ROOM",
                List.of(ids),
                Map.of("p1", "P1", "p2", "P2", "p3", "P3"),
                11L
        );
    }

    private static GameConfig configWithSettings(Map<String, Object> settings) {
        return new GameConfig(
                LiarsNumberGameManifest.ID,
                "ROOM",
                List.of("p1", "p2"),
                Map.of("p1", "P1", "p2", "P2"),
                11L,
                settings
        );
    }

    private void apply(LiarsNumberGameState state, String playerId, LiarsNumberAction action) {
        engine.apply(state, player(playerId), action, new SeededRandomSource(3L));
    }

    private static PlayerContext player(String id) {
        return PlayerContext.player(id, id);
    }

    private static LiarsNumberAction action(
            LiarsNumberActionType type, String cardId, String target, Integer declaredType, String commandId
    ) {
        return action(type, cardId, target, declaredType, commandId, null);
    }

    private static LiarsNumberAction action(
            LiarsNumberActionType type, String cardId, String target, Integer declaredType, String commandId, String guess
    ) {
        return new LiarsNumberAction(type.name(), commandId, cardId, declaredType, guess, target);
    }
}
