package com.partygameonline.game.liarsnumber;

import com.partygameonline.common.UniqueDisplayNames;
import com.partygameonline.game.core.GameActionFormatException;
import com.partygameonline.game.core.GameConfig;
import com.partygameonline.game.core.GameEngine;
import com.partygameonline.game.core.GameResult;
import com.partygameonline.game.core.PlayerContext;
import com.partygameonline.game.core.RandomSource;
import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.game.liarsnumber.application.LiarsNumberRules;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberAction;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberActionType;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberActiveRound;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberCard;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberClaim;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberEvent;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberGameState;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberPhase;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberPlayerState;
import com.partygameonline.game.liarsnumber.domain.LiarsNumberSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class LiarsNumberGameEngine
        implements GameEngine<LiarsNumberGameState, LiarsNumberAction, LiarsNumberEvent> {

    @Override
    public String gameType() {
        return LiarsNumberGameManifest.ID;
    }

    @Override
    public LiarsNumberGameState createGame(GameConfig config, RandomSource random) {
        int playerCount = config.playerIds().size();
        if (playerCount < LiarsNumberGameState.MIN_PLAYERS || playerCount > LiarsNumberGameState.MAX_PLAYERS) {
            throw new IllegalArgumentException("Liar's Number supports 2 to 6 players");
        }
        LiarsNumberGameState state = new LiarsNumberGameState(config.roomId(), playerCount);
        Map<String, String> names = UniqueDisplayNames.uniquifyAll(config.playerIds(), config.displayNames());
        for (int seat = 0; seat < playerCount; seat++) {
            String playerId = config.playerIds().get(seat);
            state.addPlayer(new LiarsNumberPlayerState(
                    playerId,
                    names.getOrDefault(playerId, config.displayName(playerId)),
                    seat
            ));
        }

        List<LiarsNumberCard> deck = LiarsNumberRules.createDeck();
        random.shuffle(deck);
        if (playerCount == 2) {
            state.getRemovedCards().addAll(new ArrayList<>(deck.subList(0, LiarsNumberGameState.TWO_PLAYER_REMOVED_CARDS)));
            deck = new ArrayList<>(deck.subList(LiarsNumberGameState.TWO_PLAYER_REMOVED_CARDS, deck.size()));
        }
        for (int index = 0; index < deck.size(); index++) {
            state.getPlayers().get(index % playerCount).getHand().add(deck.get(index));
        }
        String starterId = state.getPlayers().get(random.nextInt(playerCount)).getPlayerId();
        state.setCurrentRoundStarterId(starterId);
        state.configure(LiarsNumberSettings.fromRoomSettings(config.settings()));
        state.addPublicEvent(LiarsNumberEvent.of("LIARS_NUMBER_GAME_STARTED", Map.of(
                "playerCount", playerCount,
                "lossThreshold", state.threshold(),
                "removedCardCount", state.getRemovedCards().size(),
                "currentRoundStarterId", starterId
        )));
        return state;
    }

    @Override
    public LiarsNumberAction decodeAction(Map<String, Object> payload) {
        Object rawType = payload.get("type");
        if (!(rawType instanceof String type) || type.isBlank()) {
            throw new GameActionFormatException("type is required");
        }
        return new LiarsNumberAction(
                type,
                stringValue(payload.get("commandId")),
                stringValue(payload.get("cardId")),
                integerValue(payload.get("declaredType")),
                stringValue(payload.get("guess")),
                stringValue(payload.get("targetPlayerId"))
        );
    }

    @Override
    public ValidationResult validate(LiarsNumberGameState state, PlayerContext actor, LiarsNumberAction action) {
        if (state == null || actor == null || state.findPlayer(actor.playerId()) == null) {
            return ValidationResult.reject("NOT_PLAYER", "You are not a player in this game");
        }
        if (state.isFinished()) {
            return ValidationResult.reject("GAME_OVER", "The game has already finished");
        }
        if (state.isProcessedCommand(action.commandId())) {
            return ValidationResult.reject("DUPLICATE_COMMAND", "This command was already processed");
        }
        LiarsNumberActionType type = parseType(action.type());
        if (type == null) {
            return ValidationResult.reject("UNKNOWN_ACTION", "Unsupported Liar's Number action");
        }
        LiarsNumberActiveRound round = state.getActiveRound();
        return switch (type) {
            case SELECT_CARD -> validateSelectCard(state, actor.playerId(), action);
            case SELECT_TARGET -> validateSelectTarget(state, actor.playerId(), action, round);
            case DECLARE_TYPE -> validateDeclare(state, actor.playerId(), action, round, LiarsNumberPhase.DECLARE_TYPE);
            case GUESS -> validateGuess(state, actor.playerId(), action, round);
            case PEEK_AND_PASS -> validatePeek(state, actor.playerId(), round);
            case SELECT_PASS_TARGET -> validatePassTarget(state, actor.playerId(), action, round);
            case PASS_DECLARE_TYPE -> validateDeclare(state, actor.playerId(), action, round, LiarsNumberPhase.PASS_DECLARE_TYPE);
            case TIMEOUT -> validateTimeout(state, actor.playerId());
        };
    }

    @Override
    public GameResult<LiarsNumberGameState, LiarsNumberEvent> apply(
            LiarsNumberGameState state,
            PlayerContext actor,
            LiarsNumberAction action,
            RandomSource random
    ) {
        state.markCommandProcessed(action.commandId());
        LiarsNumberActionType type = parseType(action.type());
        List<LiarsNumberEvent> events = new ArrayList<>();
        switch (type) {
            case SELECT_CARD -> selectCard(state, actor.playerId(), action.cardId(), events);
            case SELECT_TARGET -> {
                state.getActiveRound().setCurrentReceiverId(action.targetPlayerId());
                state.setPhase(LiarsNumberPhase.DECLARE_TYPE);
                addEvent(state, events, "LIARS_NUMBER_TARGET_SELECTED", Map.of(
                        "senderId", actor.playerId(), "receiverId", action.targetPlayerId()
                ));
            }
            case DECLARE_TYPE, PASS_DECLARE_TYPE -> declare(state, actor.playerId(), action.declaredType(), events);
            case PEEK_AND_PASS -> peek(state, actor.playerId(), events);
            case SELECT_PASS_TARGET -> {
                state.getActiveRound().setCurrentReceiverId(action.targetPlayerId());
                state.setPhase(LiarsNumberPhase.PASS_DECLARE_TYPE);
                addEvent(state, events, "LIARS_NUMBER_PASS_TARGET_SELECTED", Map.of(
                        "senderId", actor.playerId(), "receiverId", action.targetPlayerId()
                ));
            }
            case GUESS -> resolveGuess(state, actor.playerId(), action.guess(), events);
            case TIMEOUT -> applyTimeout(state, actor.playerId(), random, events);
        }
        state.bumpVersion();
        state.resetTurnDeadline();
        return toResult(state, events);
    }

    @Override
    public GameResult<LiarsNumberGameState, LiarsNumberEvent> onPlayerAbandoned(
            LiarsNumberGameState state,
            PlayerContext player,
            RandomSource random
    ) {
        List<LiarsNumberEvent> events = new ArrayList<>();
        if (!state.isFinished() && state.findPlayer(player.playerId()) != null) {
            state.finish(player.playerId(), "ABANDONED");
            addEvent(state, events, "LIARS_NUMBER_GAME_OVER", Map.of(
                    "loserId", player.playerId(),
                    "reason", "ABANDONED"
            ));
            state.bumpVersion();
        }
        return toResult(state, events);
    }

    private static void selectCard(
            LiarsNumberGameState state, String playerId, String cardId, List<LiarsNumberEvent> events
    ) {
        LiarsNumberPlayerState player = state.findPlayer(playerId);
        LiarsNumberCard card = player.findHand(cardId);
        player.getHand().remove(card);
        state.setActiveRound(new LiarsNumberActiveRound(card, playerId));
        state.setPhase(LiarsNumberPhase.SELECT_TARGET);
        addEvent(state, events, "LIARS_NUMBER_CARD_SELECTED", Map.of("senderId", playerId));
    }

    private static void addEvent(
            LiarsNumberGameState state, List<LiarsNumberEvent> events, String type, Map<String, Object> payload
    ) {
        LiarsNumberEvent event = LiarsNumberEvent.of(type, payload);
        state.addPublicEvent(event);
        events.add(event);
    }

    private static void declare(
            LiarsNumberGameState state,
            String senderId,
            int declaredType,
            List<LiarsNumberEvent> events
    ) {
        LiarsNumberActiveRound round = state.getActiveRound();
        round.setDeclaredType(declaredType);
        round.addClaim(new LiarsNumberClaim(senderId, round.getCurrentReceiverId(), declaredType));
        state.setPhase(LiarsNumberPhase.RECEIVER_DECISION);
        LiarsNumberEvent event = LiarsNumberEvent.of("LIARS_NUMBER_CLAIM_DECLARED", Map.of(
                "senderId", senderId,
                "receiverId", round.getCurrentReceiverId(),
                "declaredType", declaredType
        ));
        state.addPublicEvent(event);
        events.add(event);
    }

    private static void peek(LiarsNumberGameState state, String playerId, List<LiarsNumberEvent> events) {
        LiarsNumberActiveRound round = state.getActiveRound();
        round.addSeenBy(playerId);
        round.setCurrentSenderId(playerId);
        round.setCurrentReceiverId(null);
        state.setPhase(LiarsNumberPhase.SELECT_PASS_TARGET);
        LiarsNumberEvent event = LiarsNumberEvent.of("LIARS_NUMBER_CARD_PEEKED", Map.of("playerId", playerId));
        state.addPublicEvent(event);
        events.add(event);
    }

    private static void resolveGuess(
            LiarsNumberGameState state,
            String receiverId,
            String rawGuess,
            List<LiarsNumberEvent> events
    ) {
        LiarsNumberActiveRound round = state.getActiveRound();
        boolean claimIsTrue = LiarsNumberRules.claimIsTrue(round.getCard(), round.getDeclaredType());
        boolean guessedTrue = "TRUE".equalsIgnoreCase(rawGuess);
        boolean receiverCorrect = guessedTrue == claimIsTrue;
        String penaltyPlayerId = receiverCorrect ? round.getCurrentSenderId() : receiverId;
        LiarsNumberPlayerState penaltyPlayer = state.findPlayer(penaltyPlayerId);
        penaltyPlayer.getPenaltyCards().add(round.getCard());
        int penaltyScore = LiarsNumberRules.getPenaltyScoreForType(penaltyPlayer, round.getCard().typeId());
        state.recordResolution(
                round.getCard(),
                penaltyPlayerId,
                round.getCard().typeId(),
                penaltyScore,
                claimIsTrue,
                receiverCorrect
        );
        LiarsNumberEvent reveal = LiarsNumberEvent.of("LIARS_NUMBER_CARD_REVEALED", Map.of(
                "cardId", round.getCard().cardId(),
                "typeId", round.getCard().typeId(),
                "variant", round.getCard().variant().name(),
                "declaredType", round.getDeclaredType(),
                "claimIsTrue", claimIsTrue,
                "receiverCorrect", receiverCorrect,
                "penaltyPlayerId", penaltyPlayerId,
                "penaltyWeight", round.getCard().penaltyWeight(),
                "penaltyScore", penaltyScore,
                "lossThreshold", state.threshold()
        ));
        state.addPublicEvent(reveal);
        events.add(reveal);
        state.setActiveRound(null);
        if (penaltyScore >= state.threshold()) {
            finish(state, penaltyPlayerId, "PENALTY_THRESHOLD", events, penaltyScore, round.getCard().typeId());
            return;
        }
        state.setCurrentRoundStarterId(penaltyPlayerId);
        state.incrementRoundNumber();
        if (penaltyPlayer.getHand().isEmpty()) {
            finish(state, penaltyPlayerId, "NO_CARDS", events, penaltyScore, round.getCard().typeId());
            return;
        }
        state.setPhase(LiarsNumberPhase.SELECT_CARD);
    }

    private static void finish(
            LiarsNumberGameState state,
            String loserId,
            String reason,
            List<LiarsNumberEvent> events,
            int score,
            int typeId
    ) {
        state.finish(loserId, reason);
        LiarsNumberEvent over = LiarsNumberEvent.of("LIARS_NUMBER_GAME_OVER", Map.of(
                "loserId", loserId,
                "reason", reason,
                "penaltyType", typeId,
                "penaltyScore", score,
                "lossThreshold", state.threshold()
        ));
        state.addPublicEvent(over);
        events.add(over);
    }

    private static ValidationResult validateSelectCard(LiarsNumberGameState state, String actorId, LiarsNumberAction action) {
        if (state.getPhase() != LiarsNumberPhase.SELECT_CARD || !actorId.equals(state.getCurrentRoundStarterId())) {
            return ValidationResult.reject("NOT_ROUND_STARTER", "Only the round starter can choose a card");
        }
        LiarsNumberPlayerState player = state.findPlayer(actorId);
        if (action.cardId() == null || player.findHand(action.cardId()) == null) {
            return ValidationResult.reject("CARD_NOT_IN_HAND", "Choose a card from your hand");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateSelectTarget(
            LiarsNumberGameState state, String actorId, LiarsNumberAction action, LiarsNumberActiveRound round
    ) {
        if (state.getPhase() != LiarsNumberPhase.SELECT_TARGET || round == null || !actorId.equals(round.getCurrentSenderId())) {
            return ValidationResult.reject("INVALID_PHASE", "Choose a receiver for this card");
        }
        return targetValidation(state, actorId, action.targetPlayerId(), false, round);
    }

    private static ValidationResult validateDeclare(
            LiarsNumberGameState state, String actorId, LiarsNumberAction action,
            LiarsNumberActiveRound round, LiarsNumberPhase expectedPhase
    ) {
        if (state.getPhase() != expectedPhase || round == null || !actorId.equals(round.getCurrentSenderId())) {
            return ValidationResult.reject("INVALID_PHASE", "You cannot declare a number now");
        }
        return validDeclaredType(action.declaredType());
    }

    private static ValidationResult validateGuess(
            LiarsNumberGameState state, String actorId, LiarsNumberAction action, LiarsNumberActiveRound round
    ) {
        if (state.getPhase() != LiarsNumberPhase.RECEIVER_DECISION || round == null
                || !actorId.equals(round.getCurrentReceiverId())) {
            return ValidationResult.reject("NOT_RECEIVER", "Only the current receiver can guess");
        }
        if (!"TRUE".equalsIgnoreCase(action.guess()) && !"FALSE".equalsIgnoreCase(action.guess())) {
            return ValidationResult.reject("INVALID_GUESS", "Choose TRUE or FALSE");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validatePeek(LiarsNumberGameState state, String actorId, LiarsNumberActiveRound round) {
        if (state.getPlayerCount() == 2) {
            return ValidationResult.reject("PASS_NOT_ALLOWED", "Two-player games do not allow peek and pass");
        }
        if (state.getPhase() != LiarsNumberPhase.RECEIVER_DECISION || round == null
                || !actorId.equals(round.getCurrentReceiverId())) {
            return ValidationResult.reject("NOT_RECEIVER", "Only the current receiver can peek");
        }
        if (availablePassTargets(state, actorId, round).isEmpty()) {
            return ValidationResult.reject("NO_PASS_TARGET", "There is no player who has not seen this card");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validatePassTarget(
            LiarsNumberGameState state, String actorId, LiarsNumberAction action, LiarsNumberActiveRound round
    ) {
        if (state.getPhase() != LiarsNumberPhase.SELECT_PASS_TARGET || round == null
                || !actorId.equals(round.getCurrentSenderId())) {
            return ValidationResult.reject("INVALID_PHASE", "Choose a new receiver");
        }
        return targetValidation(state, actorId, action.targetPlayerId(), true, round);
    }

    private static ValidationResult targetValidation(
            LiarsNumberGameState state, String actorId, String targetId, boolean pass, LiarsNumberActiveRound round
    ) {
        if (targetId == null || targetId.equals(actorId) || state.findPlayer(targetId) == null) {
            return ValidationResult.reject("INVALID_RECEIVER", "Choose another player");
        }
        if (pass && round.getSeenBy().contains(targetId)) {
            return ValidationResult.reject("PLAYER_ALREADY_SAW_CARD", "That player has already seen this card");
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validDeclaredType(Integer value) {
        return value == null || value < 1 || value > 8
                ? ValidationResult.reject("INVALID_DECLARED_TYPE", "Choose a number from 1 to 8")
                : ValidationResult.ok();
    }

    private static ValidationResult validateTimeout(LiarsNumberGameState state, String actorId) {
        if (!actorId.equals(state.currentActorId())) {
            return ValidationResult.reject("NOT_CURRENT_PLAYER", "Only the current player can time out");
        }
        return state.timeoutIsDue(Instant.now())
                ? ValidationResult.ok()
                : ValidationResult.reject("TIMEOUT_NOT_DUE", "The turn has not expired");
    }

    private static void applyTimeout(
            LiarsNumberGameState state,
            String actorId,
            RandomSource random,
            List<LiarsNumberEvent> events
    ) {
        LiarsNumberActiveRound round = state.getActiveRound();
        switch (state.getPhase()) {
            case SELECT_CARD -> {
                List<LiarsNumberCard> hand = state.findPlayer(actorId).getHand();
                selectCard(state, actorId, hand.get(random.nextInt(hand.size())).cardId(), events);
            }
            case SELECT_TARGET -> {
                List<String> targets = state.getPlayers().stream()
                        .map(LiarsNumberPlayerState::getPlayerId)
                        .filter(id -> !id.equals(actorId))
                        .toList();
                String targetId = targets.get(random.nextInt(targets.size()));
                round.setCurrentReceiverId(targetId);
                state.setPhase(LiarsNumberPhase.DECLARE_TYPE);
                addEvent(state, events, "LIARS_NUMBER_TARGET_SELECTED", Map.of(
                        "senderId", actorId, "receiverId", targetId, "automatic", true
                ));
            }
            case DECLARE_TYPE, PASS_DECLARE_TYPE -> declare(state, actorId, random.nextInt(8) + 1, events);
            case RECEIVER_DECISION -> resolveGuess(
                    state,
                    actorId,
                    random.nextInt(2) == 0 ? "TRUE" : "FALSE",
                    events
            );
            case SELECT_PASS_TARGET -> {
                List<String> targets = availablePassTargets(state, actorId, round);
                String targetId = targets.get(random.nextInt(targets.size()));
                round.setCurrentReceiverId(targetId);
                state.setPhase(LiarsNumberPhase.PASS_DECLARE_TYPE);
                addEvent(state, events, "LIARS_NUMBER_PASS_TARGET_SELECTED", Map.of(
                        "senderId", actorId, "receiverId", targetId, "automatic", true
                ));
            }
            case GAME_OVER -> {
                // Validation prevents timeout after the game has finished.
            }
        }
    }

    public static List<String> availablePassTargets(
            LiarsNumberGameState state, String currentPlayerId, LiarsNumberActiveRound round
    ) {
        if (round == null || state.getPlayerCount() == 2) {
            return List.of();
        }
        return state.getPlayers().stream()
                .map(LiarsNumberPlayerState::getPlayerId)
                .filter(id -> !id.equals(currentPlayerId) && !round.getSeenBy().contains(id))
                .toList();
    }

    private static LiarsNumberActionType parseType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LiarsNumberActionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static GameResult<LiarsNumberGameState, LiarsNumberEvent> toResult(
            LiarsNumberGameState state, List<LiarsNumberEvent> events
    ) {
        if (state.isFinished()) {
            String winner = state.getWinnerPlayerIds().isEmpty() ? null : state.getWinnerPlayerIds().getFirst();
            return GameResult.finished(state, List.copyOf(events), winner);
        }
        return GameResult.of(state, List.copyOf(events));
    }

    private static String stringValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
