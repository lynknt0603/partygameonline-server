package com.partygameonline.game.games.demo;

import com.partygameonline.game.core.GameActionFormatException;
import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.GameEngine;
import com.partygameonline.game.core.GameResult;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.RandomSource;
import com.partygameonline.game.core.ValidationResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DemoCardGameEngine implements GameEngine<DemoGameState, DemoAction, DemoEvent> {

    @Override
    public String gameType() {
        return DemoCardGameManifest.ID;
    }

    @Override
    public DemoGameState createGame(GameConfig config, RandomSource random) {
        List<String> deck = DemoCards.standard52();
        random.shuffle(deck);
        Map<String, List<String>> hands = new LinkedHashMap<>();
        for (String playerId : config.playerIds()) {
            List<String> hand = new ArrayList<>(DemoCards.STARTING_HAND_SIZE);
            for (int i = 0; i < DemoCards.STARTING_HAND_SIZE; i++) {
                hand.add(deck.removeFirst());
            }
            hands.put(playerId, hand);
        }
        return new DemoGameState(
                config.playerIds(),
                hands,
                deck,
                List.of(),
                config.playerIds().getFirst(),
                1,
                false,
                false,
                false,
                null
        );
    }

    @Override
    public DemoAction decodeAction(Map<String, Object> payload) {
        Object rawType = payload.get("type");
        if (!(rawType instanceof String type) || type.isBlank()) {
            throw new GameActionFormatException("type is required");
        }
        return switch (type) {
            case "DRAW_CARD" -> new DemoAction.DrawCard();
            case "PLAY_CARD" -> {
                Object cardId = payload.get("cardId");
                if (!(cardId instanceof String id) || id.isBlank()) {
                    throw new GameActionFormatException("cardId is required");
                }
                yield new DemoAction.PlayCard(id);
            }
            case "END_TURN" -> new DemoAction.EndTurn();
            default -> throw new GameActionFormatException("Unknown action type");
        };
    }

    @Override
    public ValidationResult validate(DemoGameState state, PlayerContext actor, DemoAction action) {
        if (state.finished()) {
            return ValidationResult.reject("GAME_ALREADY_FINISHED", "The game is already finished");
        }
        if (!state.playerIds().contains(actor.playerId())) {
            return ValidationResult.reject("NOT_IN_GAME", "You are not in this game");
        }
        if (!actor.playerId().equals(state.currentPlayerId())) {
            return ValidationResult.reject("NOT_YOUR_TURN", "It is not your turn");
        }
        return switch (action) {
            case DemoAction.DrawCard ignored -> validateDraw(state);
            case DemoAction.PlayCard play -> validatePlay(state, actor.playerId(), play.cardId());
            case DemoAction.EndTurn ignored -> ValidationResult.ok();
        };
    }

    @Override
    public GameResult<DemoGameState, DemoEvent> apply(
            DemoGameState state,
            PlayerContext actor,
            DemoAction action,
            RandomSource random
    ) {
        return switch (action) {
            case DemoAction.DrawCard ignored -> applyDraw(state, actor.playerId());
            case DemoAction.PlayCard play -> applyPlay(state, actor.playerId(), play.cardId());
            case DemoAction.EndTurn ignored -> applyEndTurn(state, actor.playerId());
        };
    }

    @Override
    public GameResult<DemoGameState, DemoEvent> onPlayerAbandoned(
            DemoGameState state,
            PlayerContext player,
            RandomSource random
    ) {
        if (state.finished()) {
            return GameResult.of(state, List.of());
        }
        String winner = state.opponentOf(player.playerId());
        if (winner == null) {
            return GameResult.of(state, List.of());
        }
        DemoGameState finished = state.finishedAs(winner);
        return GameResult.finished(finished, List.of(DemoEvent.forfeited(player.playerId(), winner)), winner);
    }

    private static ValidationResult validateDraw(DemoGameState state) {
        if (state.hasDrawn()) {
            return ValidationResult.reject("ALREADY_DRAWN", "You already drew this turn");
        }
        if (state.deck().isEmpty()) {
            return ValidationResult.reject("DECK_EMPTY", "The deck is empty");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validatePlay(DemoGameState state, String playerId, String cardId) {
        if (state.hasPlayed()) {
            return ValidationResult.reject("ALREADY_PLAYED", "You already played a card this turn");
        }
        if (!state.handOf(playerId).contains(cardId)) {
            return ValidationResult.reject("CARD_NOT_IN_HAND", "You do not have that card");
        }
        return ValidationResult.ok();
    }

    private static GameResult<DemoGameState, DemoEvent> applyDraw(DemoGameState state, String playerId) {
        List<String> deck = new ArrayList<>(state.deck());
        String card = deck.removeFirst();
        List<String> hand = new ArrayList<>(state.handOf(playerId));
        hand.add(card);
        DemoGameState next = state.withHandsAndDeck(
                DemoGameState.replaceHand(state.hands(), playerId, hand),
                deck,
                true
        );
        return GameResult.of(next, List.of(DemoEvent.drawn(playerId)));
    }

    private static GameResult<DemoGameState, DemoEvent> applyPlay(DemoGameState state, String playerId, String cardId) {
        List<String> hand = new ArrayList<>(state.handOf(playerId));
        hand.remove(cardId);
        List<String> discard = new ArrayList<>(state.discard());
        discard.add(cardId);
        boolean won = hand.isEmpty();
        DemoGameState next = state.withPlay(
                DemoGameState.replaceHand(state.hands(), playerId, hand),
                discard,
                won,
                won ? playerId : null
        );
        if (won) {
            return GameResult.finished(next, List.of(DemoEvent.played(playerId, cardId), DemoEvent.won(playerId)), playerId);
        }
        return GameResult.of(next, List.of(DemoEvent.played(playerId, cardId)));
    }

    private static GameResult<DemoGameState, DemoEvent> applyEndTurn(DemoGameState state, String playerId) {
        String nextPlayer = state.nextPlayer(playerId);
        int turnNumber = state.turnNumber() + 1;
        DemoGameState next = state.withNextTurn(nextPlayer, turnNumber);
        return GameResult.of(next, List.of(DemoEvent.turnEnded(playerId, nextPlayer, turnNumber)));
    }
}
