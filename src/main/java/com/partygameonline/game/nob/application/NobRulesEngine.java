package com.partygameonline.game.nob.application;

import com.partygameonline.game.core.RandomSource;
import com.partygameonline.game.core.ValidationResult;
import com.partygameonline.game.nob.domain.NobAction;
import com.partygameonline.game.nob.domain.NobBloodline;
import com.partygameonline.game.nob.domain.NobBloodlineKnowledge;
import com.partygameonline.game.nob.domain.NobBloodlineType;
import com.partygameonline.game.nob.domain.NobCardInstance;
import com.partygameonline.game.nob.domain.NobDecisionType;
import com.partygameonline.game.nob.domain.NobEffectCode;
import com.partygameonline.game.nob.domain.NobEvent;
import com.partygameonline.game.nob.domain.NobGameState;
import com.partygameonline.game.nob.domain.NobInspectReveal;
import com.partygameonline.game.nob.domain.NobKillSource;
import com.partygameonline.game.nob.domain.NobMoonMark;
import com.partygameonline.game.nob.domain.NobObservation;
import com.partygameonline.game.nob.domain.NobPendingDecision;
import com.partygameonline.game.nob.domain.NobPhase;
import com.partygameonline.game.nob.domain.NobPhaseState;
import com.partygameonline.game.nob.domain.NobPlayerState;
import com.partygameonline.game.nob.domain.NobResolutionItem;
import com.partygameonline.game.nob.domain.NobRoleType;
import com.partygameonline.game.nob.scoring.NobScoringService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NobRulesEngine {

    private NobRulesEngine() {
    }

    public static ValidationResult validate(NobGameState state, String actorId, NobAction action) {
        if (state.isFinished()) {
            return ValidationResult.reject("GAME_ALREADY_FINISHED", "The game is already finished");
        }
        NobPlayerState actor = state.player(actorId);
        if (actor == null) {
            return ValidationResult.reject("NOT_IN_GAME", "You are not in this game");
        }
        if (state.isDuplicateCommand(action.commandId())) {
            return ValidationResult.ok();
        }
        if (action.expectedVersion() != null && action.expectedVersion() != state.getVersion()) {
            return ValidationResult.reject("STALE_VERSION", "The game state has changed");
        }
        if (NobAction.TIMEOUT.equals(action.type())) {
            return state.timeoutIsDue(Instant.now())
                    ? ValidationResult.ok()
                    : ValidationResult.reject("TIMEOUT_NOT_DUE", "No timed decision has expired");
        }
        if (!actor.isAlive() && !isReaction(action, actorId, state) && !isMoonPick(state, action)) {
            return ValidationResult.reject("PLAYER_ELIMINATED", "You cannot act after being eliminated");
        }
        NobPendingDecision pending = state.getPendingDecision();
        return switch (action.type()) {
            case NobAction.DRAFT_PICK -> validateDraft(state, actor, action);
            case NobAction.PHASE_SUBMIT -> validateSubmit(state, actor, action);
            case NobAction.CHOOSE_OPTION -> isMoonPick(state, action)
                    ? validateMoonPick(state, actorId, action)
                    : validatePending(state, actorId, action, pending);
            case NobAction.CHOOSE_TARGET, NobAction.HUNTER_DECISION,
                    NobAction.REACTION -> validatePending(state, actorId, action, pending);
            default -> ValidationResult.reject("UNKNOWN_ACTION", "Unknown NOB action");
        };
    }

    public static List<NobEvent> apply(NobGameState state, String actorId, NobAction action, RandomSource random) {
        if (state.isDuplicateCommand(action.commandId())) {
            return List.of();
        }
        List<NobEvent> events = new ArrayList<>();
        if (action.commandId() != null) {
            state.getProcessedCommandIds().add(action.commandId());
        }
        if (NobAction.TIMEOUT.equals(action.type())) {
            applyTimeout(state, random, events);
            state.bumpVersion();
            return events;
        }
        switch (action.type()) {
            case NobAction.DRAFT_PICK -> applyDraft(state, actorId, action, random, events);
            case NobAction.PHASE_SUBMIT -> applySubmit(state, actorId, action, random, events);
            case NobAction.CHOOSE_TARGET -> applyTarget(state, actorId, action, random, events);
            case NobAction.CHOOSE_OPTION -> {
                if (state.getPhase() == NobPhase.ROUND_SUMMARY) {
                    applyMoonPick(state, actorId, action.option(), random, events, false);
                } else {
                    applyOption(state, actorId, action, random, events);
                }
            }
            case NobAction.HUNTER_DECISION -> applyOption(state, actorId, action, random, events);
            case NobAction.REACTION -> applyReaction(state, actorId, action, random, events);
            default -> {
            }
        }
        state.bumpVersion();
        return events;
    }

    public static List<NobEvent> applyAbandon(NobGameState state, String playerId, RandomSource random) {
        List<NobEvent> events = new ArrayList<>();
        NobPlayerState player = state.player(playerId);
        if (player == null || state.isFinished()) {
            return events;
        }
        if (player.isAlive()) {
            eliminate(state, playerId, null, events);
        }
        NobPendingDecision pending = state.getPendingDecision();
        if (pending != null && playerId.equals(pending.actorId())) {
            applyTimeout(state, random, events);
        } else if ((state.getPhase() == NobPhase.DRAFT_PICK_1 || state.getPhase() == NobPhase.DRAFT_PICK_2)
                && !state.getDraftPicks().containsKey(playerId)) {
            List<NobCardInstance> hand = state.getDraftHands().get(playerId);
            if (hand != null && !hand.isEmpty()) {
                applyDraft(state, playerId, new NobAction(
                        NobAction.DRAFT_PICK, null, null, hand.getFirst().instanceId(), null, List.of(), null
                ), random, events);
            }
        } else if (isNightPhase(state.getPhase())
                && state.getPhaseState() == NobPhaseState.WAITING_FOR_PHASE_SUBMISSIONS) {
            state.getPhaseSubmissions().putIfAbsent(playerId, List.of("PASS"));
            if (state.getPhaseSubmissions().size() >= state.playersWhoMustSubmit().size()) {
                state.closePhaseSubmissions();
                advanceResolution(state, random, events);
            }
        }
        state.bumpVersion();
        return events;
    }

    private static boolean isMoonPick(NobGameState state, NobAction action) {
        return state.getPhase() == NobPhase.ROUND_SUMMARY && NobAction.CHOOSE_OPTION.equals(action.type());
    }

    private static ValidationResult validateMoonPick(NobGameState state, String actorId, NobAction action) {
        if (!state.hasUnclaimedMoonPick(actorId)) {
            return ValidationResult.reject("NO_PENDING_DECISION", "No Moon Mark pick is waiting");
        }
        if (action.decisionId() != null && !action.decisionId().equals("moon-" + actorId)) {
            return ValidationResult.reject("STALE_DECISION", "This decision is no longer active");
        }
        if (action.option() == null) {
            return ValidationResult.reject("INVALID_OPTION", "That option is not allowed");
        }
        boolean allowed = state.getMoonTokenOffers().getOrDefault(actorId, List.of()).stream()
                .anyMatch(option -> option.optionId().equals(action.option()));
        return allowed
                ? ValidationResult.ok()
                : ValidationResult.reject("INVALID_OPTION", "That option is not allowed");
    }

    private static void applyMoonPick(
            NobGameState state,
            String actorId,
            String optionId,
            RandomSource random,
            List<NobEvent> events,
            boolean fromTimeout
    ) {
        if (!state.hasUnclaimedMoonPick(actorId) || optionId == null) {
            return;
        }
        List<com.partygameonline.game.nob.domain.NobMoonTokenOption> options =
                new ArrayList<>(state.getMoonTokenOffers().getOrDefault(actorId, List.of()));
        com.partygameonline.game.nob.domain.NobMoonTokenOption match = options.stream()
                .filter(option -> option.optionId().equals(optionId))
                .findFirst()
                .orElse(null);
        if (match == null) {
            if (options.isEmpty()) {
                return;
            }
            match = options.get(fromTimeout ? random.nextInt(options.size()) : 0);
        }
        final com.partygameonline.game.nob.domain.NobMoonTokenOption chosen = match;
        NobPlayerState player = state.requirePlayer(actorId);
        player.getMoonMarks().add(chosen.mark());
        options.removeIf(option -> option.optionId().equals(chosen.optionId()));
        state.getMoonTokenOffers().put(actorId, List.copyOf(options));
        state.recordMoonPick(actorId);
        if (!state.hasUnclaimedMoonPick(actorId)) {
            for (var leftover : options) {
                state.getMoonMarkPool().add(leftover.mark());
            }
            state.getMoonTokenOffers().put(actorId, List.of());
        }
        events.add(NobEvent.of("NOB_MOON_MARK_COUNT_CHANGED", Map.of(
                "playerId", actorId,
                "value", chosen.mark().value()
        )));
        state.announce("MOON_MARK_RECEIVED", actorId, null, null, null, "nob.moonMark.received");
        state.log(
                "NOB_MOON_MARK_COUNT_CHANGED",
                player.getDisplayName() + " received a Moon Mark",
                actorId,
                null
        );
    }

    private static void finishRoundSummary(NobGameState state, RandomSource random, List<NobEvent> events) {
        List<String> winners = NobScoringService.winnersAtOrOverTarget(state);
        if (!winners.isEmpty()) {
            state.setFinished(true);
            state.setPhase(NobPhase.GAME_OVER);
            state.getWinnerPlayerIds().clear();
            state.getWinnerPlayerIds().addAll(winners);
            events.add(NobEvent.of("NOB_GAME_OVER", Map.of("winners", winners)));
            return;
        }
        state.startNextRound(random);
        events.add(NobEvent.of("NOB_ROUND_STARTED", Map.of("round", state.getRoundNumber())));
    }

    private static boolean isReaction(NobAction action, String actorId, NobGameState state) {
        return NobAction.REACTION.equals(action.type())
                && state.getPendingDecision() != null
                && actorId.equals(state.getPendingDecision().actorId());
    }

    private static ValidationResult validateDraft(NobGameState state, NobPlayerState actor, NobAction action) {
        if (state.getPhase() != NobPhase.DRAFT_PICK_1 && state.getPhase() != NobPhase.DRAFT_PICK_2) {
            return ValidationResult.reject("WRONG_PHASE", "It is not the draft phase");
        }
        if (state.getDraftPicks().containsKey(actor.getPlayerId())) {
            return ValidationResult.reject("ALREADY_SUBMITTED", "You already picked");
        }
        List<NobCardInstance> hand = state.getDraftHands().get(actor.getPlayerId());
        if (hand == null || action.cardInstanceId() == null) {
            return ValidationResult.reject("INVALID_CARD", "Choose a card from your draft hand");
        }
        boolean owned = hand.stream().anyMatch(card -> card.instanceId().equals(action.cardInstanceId()));
        return owned ? ValidationResult.ok() : ValidationResult.reject("CARD_NOT_IN_HAND", "That card is not in your draft hand");
    }

    private static ValidationResult validateSubmit(NobGameState state, NobPlayerState actor, NobAction action) {
        if (!isNightPhase(state.getPhase()) || state.getPhaseState() != NobPhaseState.WAITING_FOR_PHASE_SUBMISSIONS) {
            return ValidationResult.reject("WRONG_PHASE", "Submissions are not open");
        }
        if (state.getPhaseSubmissions().containsKey(actor.getPlayerId())) {
            return ValidationResult.reject("ALREADY_SUBMITTED", "You already submitted");
        }
        if (state.playersWhoMustSubmit().stream().noneMatch(player -> player.getPlayerId().equals(actor.getPlayerId()))) {
            return ValidationResult.reject("NOTHING_TO_SUBMIT", "You have no card for this phase");
        }
        if ("PASS".equalsIgnoreCase(action.option()) || "PASS".equalsIgnoreCase(action.cardInstanceId())) {
            return ValidationResult.ok();
        }
        List<NobCardInstance> matching = matchingPhaseCards(actor, state.getPhase());
        if (action.playAllMatching()) {
            if (matching.size() < 2) {
                return ValidationResult.reject("INVALID_CARD", "You need two cards of this role to play both");
            }
            return ValidationResult.ok();
        }
        List<String> ids = action.resolvedCardInstanceIds();
        if (ids.isEmpty()) {
            return ValidationResult.reject("INVALID_CARD", "Choose a card for this phase or pass");
        }
        for (String id : ids) {
            NobCardInstance card = actor.findHand(id);
            if (card == null || !card.matchesPhase(state.getPhase())) {
                return ValidationResult.reject("INVALID_CARD", "Choose a card for this phase or pass");
            }
        }
        return ValidationResult.ok();
    }

    private static List<NobCardInstance> matchingPhaseCards(NobPlayerState actor, NobPhase phase) {
        return actor.getHand().stream().filter(card -> card.matchesPhase(phase)).toList();
    }

    private static ValidationResult validatePending(
            NobGameState state,
            String actorId,
            NobAction action,
            NobPendingDecision pending
    ) {
        if (pending == null) {
            return ValidationResult.reject("NO_PENDING_DECISION", "No decision is waiting");
        }
        if (action.decisionId() != null && !action.decisionId().equals(pending.decisionId())) {
            return ValidationResult.reject("STALE_DECISION", "This decision is no longer active");
        }
        if (!pending.actorId().equals(actorId)) {
            return ValidationResult.reject("NOT_YOUR_DECISION", "This decision is not yours");
        }
        if (NobAction.CHOOSE_TARGET.equals(action.type())) {
            List<String> targets = targetsOf(action);
            if (targets.isEmpty()) {
                return ValidationResult.reject("INVALID_TARGET", "That target is not allowed");
            }
            for (String target : targets) {
                if (!pending.allowedTargetIds().contains(target)) {
                    return ValidationResult.reject("INVALID_TARGET", "That target is not allowed");
                }
            }
            return ValidationResult.ok();
        }
        if (pending.type() == NobDecisionType.CHOOSE_HIDDEN_CARD || pending.type() == NobDecisionType.CHOOSE_MOON_TOKEN) {
            String cardId = action.option() != null && !action.option().isBlank()
                    ? action.option()
                    : action.cardInstanceId();
            if (cardId == null || !pending.allowedOptions().contains(cardId)) {
                return ValidationResult.reject("INVALID_CARD", "Choose one of the facedown cards");
            }
            return ValidationResult.ok();
        }
        if (action.option() == null || !pending.allowedOptions().contains(action.option())) {
            return ValidationResult.reject("INVALID_OPTION", "That option is not allowed");
        }
        if (pending.type() == NobDecisionType.ECHO_CHOOSE) {
            if (action.cardInstanceId() == null || action.cardInstanceId().isBlank()) {
                return ValidationResult.reject("INVALID_CARD", "Choose a card from the Echo pool");
            }
            NobCardInstance selected = state.getEchoHold().stream()
                    .filter(card -> card.instanceId().equals(action.cardInstanceId())
                            || card.cardCode().equals(action.cardInstanceId()))
                    .findFirst()
                    .orElse(null);
            if (selected == null) {
                return ValidationResult.reject("INVALID_CARD", "Choose a card from the Echo pool");
            }
            if ("PLAY_NOW".equals(action.option()) && !canPlayNow(selected)) {
                return ValidationResult.reject("INVALID_OPTION", "That card cannot be played now");
            }
        }
        return ValidationResult.ok();
    }

    private static void applyDraft(
            NobGameState state,
            String actorId,
            NobAction action,
            RandomSource random,
            List<NobEvent> events
    ) {
        state.getDraftPicks().put(actorId, action.cardInstanceId());
        if (state.getDraftPicks().size() < state.getPlayers().size()) {
            return;
        }
        if (state.getPhase() == NobPhase.DRAFT_PICK_1) {
            passLeft(state);
            state.getDraftPicks().clear();
            state.setPhase(NobPhase.DRAFT_PICK_2);
            state.setPhaseDeadline(NobGameState.nowPlusSeconds(state.getDraftSeconds()));
            events.add(NobEvent.of("NOB_PHASE_CHANGED", Map.of("phase", NobPhase.DRAFT_PICK_2.name())));
            return;
        }
        finishDraft(state, random, events);
    }

    private static void passLeft(NobGameState state) {
        int n = state.getPlayers().size();
        Map<String, List<NobCardInstance>> next = new LinkedHashMap<>();
        for (NobPlayerState player : state.getPlayers()) {
            String pickId = state.getDraftPicks().get(player.getPlayerId());
            List<NobCardInstance> hand = state.getDraftHands().get(player.getPlayerId());
            NobCardInstance kept = hand.stream().filter(card -> card.instanceId().equals(pickId)).findFirst().orElseThrow();
            player.getHand().add(kept);
            List<NobCardInstance> rest = hand.stream().filter(card -> !card.instanceId().equals(pickId)).toList();
            NobPlayerState left = state.getPlayers().get((player.getSeat() + 1) % n);
            next.computeIfAbsent(left.getPlayerId(), ignored -> new ArrayList<>()).addAll(rest);
        }
        state.getDraftHands().clear();
        state.getDraftHands().putAll(next);
    }

    private static void finishDraft(NobGameState state, RandomSource random, List<NobEvent> events) {
        for (NobPlayerState player : state.getPlayers()) {
            String pickId = state.getDraftPicks().get(player.getPlayerId());
            List<NobCardInstance> hand = state.getDraftHands().get(player.getPlayerId());
            for (NobCardInstance card : hand) {
                if (card.instanceId().equals(pickId)) {
                    player.getHand().add(card);
                } else {
                    state.getDiscardPile().add(card);
                }
            }
        }
        state.getDraftHands().clear();
        state.getDraftPicks().clear();
        state.beginNightPhase(NobPhase.SHADOW_STALKER);
        events.add(NobEvent.of("NOB_PHASE_CHANGED", Map.of("phase", NobPhase.SHADOW_STALKER.name())));
        if (state.playersWhoMustSubmit().isEmpty()) {
            advancePhase(state, random, events);
        }
    }

    private static void applySubmit(NobGameState state, String actorId, NobAction action, RandomSource random, List<NobEvent> events) {
        List<String> submitted;
        if ("PASS".equalsIgnoreCase(action.option()) || "PASS".equalsIgnoreCase(action.cardInstanceId())) {
            submitted = List.of("PASS");
        } else if (action.playAllMatching()) {
            submitted = matchingPhaseCards(state.requirePlayer(actorId), state.getPhase()).stream()
                    .map(NobCardInstance::instanceId)
                    .toList();
        } else {
            submitted = action.resolvedCardInstanceIds();
        }
        state.getPhaseSubmissions().put(actorId, submitted);
        if (state.getPhaseSubmissions().size() < state.playersWhoMustSubmit().size()) {
            state.hurrySharedDeadline();
            return;
        }
        state.closePhaseSubmissions();
        events.add(NobEvent.of("NOB_PHASE_CHANGED", Map.of("phase", state.getPhase().name(), "resolving", true)));
        advanceResolution(state, random, events);
    }

    private static void advanceResolution(NobGameState state, RandomSource random, List<NobEvent> events) {
        if (state.getPendingDecision() != null) {
            return;
        }
        state.clearEchoShowcase();
        state.setResolutionDisplayExpiresAt(null);
        NobResolutionItem next = state.pollNextLiveCard();
        if (next == null) {
            state.setCurrentResolvingCard(null);
            advancePhase(state, random, events);
            return;
        }
        for (NobPlayerState player : state.getPlayers()) {
            player.setInspectReveal(null);
        }
        NobPlayerState owner = state.requirePlayer(next.ownerPlayerId());
        state.revealCard(owner, next.card());
        state.setCurrentResolvingCard(next.card());
        state.setCurrentActorPlayerId(owner.getPlayerId());
        state.announce("CARD_RESOLVING", owner.getPlayerId(), null, next.card().cardCode(), null, "nob.card.resolving");
        startCard(state, owner, next.card(), random, events);
    }

    private static void startCard(
            NobGameState state,
            NobPlayerState owner,
            NobCardInstance card,
            RandomSource random,
            List<NobEvent> events
    ) {
        List<String> others = state.alivePlayers().stream()
                .map(NobPlayerState::getPlayerId)
                .filter(id -> !id.equals(owner.getPlayerId()))
                .toList();
        switch (card.effectCode()) {
            case LOOK_BLOODLINE, LOOK_BLOODLINE_AND_RANDOM_CARD, UNMASK, BLIND_ELIMINATE, INSPECT_THEN_DECIDE,
                    FINAL_JUDGEMENT, MOON_BROKER -> {
                if (others.isEmpty()) {
                    completeCurrentResolution(state);
                    return;
                }
                state.setPhaseState(NobPhaseState.WAITING_FOR_TARGET);
                state.setPendingDecision(state.newDecision(
                        owner.getPlayerId(),
                        NobDecisionType.CHOOSE_TARGET,
                        List.of(),
                        others,
                        card.instanceId(),
                        state.timeoutSecondsFor(NobDecisionType.CHOOSE_TARGET),
                        Map.of("effect", card.effectCode().name())
                ));
                state.announce(
                        "PLAYER_SELECTING_TARGET",
                        owner.getPlayerId(),
                        null,
                        card.cardCode(),
                        null,
                        "nob.actor.selectingTarget"
                );
            }
            case BLOODLINE_EXCHANGE -> {
                if (others.size() < 2) {
                    completeCurrentResolution(state);
                    return;
                }
                state.setPhaseState(NobPhaseState.WAITING_FOR_TARGET);
                state.setPendingDecision(state.newDecision(
                        owner.getPlayerId(),
                        NobDecisionType.CHOOSE_TARGET,
                        List.of(),
                        others,
                        card.instanceId(),
                        state.timeoutSecondsFor(NobDecisionType.CHOOSE_TARGET),
                        Map.of("effect", card.effectCode().name(), "need", 2)
                ));
                state.announce(
                        "PLAYER_SELECTING_TARGET",
                        owner.getPlayerId(),
                        null,
                        card.cardCode(),
                        null,
                        "nob.actor.selectingTarget"
                );
            }
            case ECHOES_OF_FALLEN -> beginEchoes(state, owner, card, random, events);
            case MOON_THIEF -> beginMoonThief(state, owner, card, events);
            default -> advanceResolution(state, random, events);
        }
    }

    private static void applyTarget(
            NobGameState state,
            String actorId,
            NobAction action,
            RandomSource random,
            List<NobEvent> events
    ) {
        NobPendingDecision pending = state.getPendingDecision();
        NobPlayerState actor = state.requirePlayer(actorId);
        NobCardInstance card = findUsed(actor, pending.sourceCardInstanceId());
        String effect = String.valueOf(pending.context().get("effect"));
        @SuppressWarnings("unchecked")
        List<String> already = pending.context().containsKey("picked")
                ? new ArrayList<>((List<String>) pending.context().get("picked"))
                : new ArrayList<>();
        for (String target : targetsOf(action)) {
            if (!already.contains(target) && pending.allowedTargetIds().contains(target)) {
                already.add(target);
            }
        }
        int need = pending.context().get("need") instanceof Number number ? number.intValue() : 1;
        if (already.size() < need) {
            Map<String, Object> ctx = new HashMap<>(pending.context());
            ctx.put("picked", List.copyOf(already));
            state.setPendingDecision(state.newDecision(
                    actorId,
                    NobDecisionType.CHOOSE_TARGET,
                    List.of(),
                    pending.allowedTargetIds().stream().filter(id -> !already.contains(id)).toList(),
                    pending.sourceCardInstanceId(),
                    state.timeoutSecondsFor(NobDecisionType.CHOOSE_TARGET),
                    ctx
            ));
            return;
        }
        state.setPendingDecision(null);
        NobEffectCode code = NobEffectCode.valueOf(effect);
        switch (code) {
            case LOOK_BLOODLINE -> inspectBloodline(state, actor, already.getFirst(), events);
            case LOOK_BLOODLINE_AND_RANDOM_CARD -> inspectBloodlineAndCard(
                    state, actor, already.getFirst(), card == null ? null : card.instanceId(), events);
            case BLOODLINE_EXCHANGE -> beginSwap(state, actor, already, card, events);
            case UNMASK -> beginUnmask(state, actor, already.getFirst(), card, events);
            case BLIND_ELIMINATE -> beginKill(state, actor, already.getFirst(), card, NobKillSource.FERAL_KILLER, random, events);
            case INSPECT_THEN_DECIDE -> beginHunter(state, actor, already.getFirst(), card, events);
            case FINAL_JUDGEMENT -> applyFinalJudgement(state, actor, already.getFirst(), events);
            case MOON_BROKER -> beginMoonBroker(state, actor, already.getFirst(), card, events);
            case MOON_THIEF -> stealMoon(state, actor, already.getFirst(), random, events);
            default -> {
            }
        }
        if (state.getPendingDecision() == null && state.getActiveKill() == null) {
            completeCurrentResolution(state);
        }
    }

    private static void applyOption(
            NobGameState state,
            String actorId,
            NobAction action,
            RandomSource random,
            List<NobEvent> events
    ) {
        NobPendingDecision pending = state.getPendingDecision();
        state.setPendingDecision(null);
        String option = action.option();
        switch (pending.type()) {
            case HUNTER_DECISION -> {
                String targetId = String.valueOf(pending.context().get("targetId"));
                if ("ELIMINATE".equals(option)) {
                    beginKill(state, state.requirePlayer(actorId), targetId,
                            findUsed(state.requirePlayer(actorId), pending.sourceCardInstanceId()),
                            NobKillSource.HUNTER, random, events);
                } else {
                    NobPlayerState hunter = state.requirePlayer(actorId);
                    NobPlayerState spared = state.requirePlayer(targetId);
                    state.log(
                            "NOB_PLAYER_SPARED",
                            hunter.getDisplayName() + " spared " + spared.getDisplayName(),
                            actorId,
                            targetId,
                            null,
                            resolvingCode(state)
                    );
                    state.announce(
                            "PLAYER_SPARED",
                            actorId,
                            targetId,
                            pending.sourceCardInstanceId(),
                            null,
                            "nob.hunter.spared"
                    );
                }
            }
            case SHAPE_SWAP -> {
                String aId = String.valueOf(pending.context().get("a"));
                String bId = String.valueOf(pending.context().get("b"));
                if ("SWAP".equals(option)) {
                    swapBloodlines(state, aId, bId, events);
                    state.log("NOB_SHAPE_SWAP", "", actorId, aId, bId, resolvingCode(state));
                } else {
                    concealBloodlinesAfterShape(state, aId, bId, events);
                    state.log("NOB_SHAPE_KEEP", "", actorId, aId, bId, resolvingCode(state));
                }
            }
            case UNMASK_REVEAL -> {
                if ("REVEAL_PUBLIC".equals(option)) {
                    revealBloodlinePublic(
                            state,
                            String.valueOf(pending.context().get("targetId")),
                            actorId,
                            events
                    );
                }
            }
            case CHOOSE_HIDDEN_CARD -> {
                String targetId = String.valueOf(pending.context().get("targetId"));
                String cardId = option != null && !option.isBlank() ? option : action.cardInstanceId();
                inspectHiddenCard(state, state.requirePlayer(actorId), targetId, cardId, events);
            }
            case ECHO_CHOOSE -> applyEchoChoice(state, actorId, pending, option, action.cardInstanceId(), random, events);
            case MOON_BROKER -> applyMoonBroker(state, actorId, pending, option, random, events);
            case CHOOSE_MOON_TOKEN -> applyMoonTokenPick(state, actorId, pending, option, action.cardInstanceId(), events);
            default -> {
            }
        }
        if (state.getPendingDecision() == null && state.getActiveKill() == null) {
            completeCurrentResolution(state);
        }
    }

    private static String resolvingCode(NobGameState state) {
        return state.getCurrentResolvingCard() == null ? null : state.getCurrentResolvingCard().cardCode();
    }

    private static void inspectBloodline(NobGameState state, NobPlayerState actor, String targetId, List<NobEvent> events) {
        NobPlayerState target = state.requirePlayer(targetId);
        actor.getObservations().add(new NobObservation("BLOODLINE", targetId, target.getCurrentBloodline(), null, null));
        events.add(NobEvent.of("NOB_PRIVATE_BLOODLINE_SEEN", Map.of("viewerId", actor.getPlayerId())));
        actor.setInspectReveal(new NobInspectReveal(
                targetId,
                target.getCurrentBloodline(),
                null,
                Instant.now().plusSeconds(3)
        ));
        state.log(
                "NOB_INSPECTED",
                actor.getDisplayName() + " inspected " + target.getDisplayName(),
                actor.getPlayerId(),
                targetId,
                null,
                resolvingCode(state)
        );
    }

    private static void inspectBloodlineAndCard(
            NobGameState state,
            NobPlayerState actor,
            String targetId,
            String sourceCardInstanceId,
            List<NobEvent> events
    ) {
        inspectBloodline(state, actor, targetId, events);
        NobPlayerState target = state.requirePlayer(targetId);
        List<NobCardInstance> hidden = List.copyOf(target.getHand());
        if (hidden.isEmpty()) {
            return;
        }
        if (hidden.size() == 1) {
            inspectHiddenCard(state, actor, targetId, hidden.getFirst().instanceId(), events);
            return;
        }
        state.setPhaseState(NobPhaseState.WAITING_FOR_OPTION);
        state.setPendingDecision(state.newDecision(
                actor.getPlayerId(),
                NobDecisionType.CHOOSE_HIDDEN_CARD,
                hidden.stream().map(NobCardInstance::instanceId).toList(),
                List.of(),
                sourceCardInstanceId,
                state.timeoutSecondsFor(NobDecisionType.CHOOSE_HIDDEN_CARD),
                Map.of("targetId", targetId)
        ));
        events.add(NobEvent.of("NOB_DECISION_REQUIRED", Map.of("actorId", actor.getPlayerId())));
    }

    private static void inspectHiddenCard(
            NobGameState state,
            NobPlayerState actor,
            String targetId,
            String cardInstanceId,
            List<NobEvent> events
    ) {
        if (cardInstanceId == null || cardInstanceId.isBlank()) {
            return;
        }
        NobPlayerState target = state.requirePlayer(targetId);
        NobCardInstance seen = target.getHand().stream()
                .filter(card -> card.instanceId().equals(cardInstanceId))
                .findFirst()
                .orElse(null);
        if (seen == null) {
            return;
        }
        actor.getObservations().add(new NobObservation("CARD", targetId, null, seen.cardCode(), null));
        events.add(NobEvent.of("NOB_PRIVATE_CARD_SEEN", Map.of("viewerId", actor.getPlayerId())));
        NobInspectReveal previous = actor.getInspectReveal();
        actor.setInspectReveal(new NobInspectReveal(
                targetId,
                previous == null ? target.getCurrentBloodline() : previous.bloodline(),
                seen.cardCode(),
                Instant.now().plusSeconds(3)
        ));
    }

    private static void beginSwap(
            NobGameState state,
            NobPlayerState actor,
            List<String> targets,
            NobCardInstance card,
            List<NobEvent> events
    ) {
        NobPlayerState a = state.requirePlayer(targets.get(0));
        NobPlayerState b = state.requirePlayer(targets.get(1));
        actor.getObservations().add(new NobObservation("BLOODLINE", a.getPlayerId(), a.getCurrentBloodline(), null, null));
        actor.getObservations().add(new NobObservation("BLOODLINE", b.getPlayerId(), b.getCurrentBloodline(), null, null));
        state.log(
                "NOB_SHAPE_PREVIEW",
                actor.getDisplayName() + " inspected " + a.getDisplayName() + " and " + b.getDisplayName(),
                actor.getPlayerId(),
                a.getPlayerId(),
                b.getPlayerId(),
                card == null ? resolvingCode(state) : card.cardCode()
        );
        state.setPhaseState(NobPhaseState.WAITING_FOR_OPTION);
        state.setPendingDecision(state.newDecision(
                actor.getPlayerId(),
                NobDecisionType.SHAPE_SWAP,
                List.of("SWAP", "KEEP"),
                List.of(a.getPlayerId(), b.getPlayerId()),
                card == null ? null : card.instanceId(),
                state.timeoutSecondsFor(NobDecisionType.SHAPE_SWAP),
                Map.of("a", a.getPlayerId(), "b", b.getPlayerId())
        ));
        events.add(NobEvent.of("NOB_DECISION_REQUIRED", Map.of("actorId", actor.getPlayerId())));
    }

    private static void swapBloodlines(NobGameState state, String aId, String bId, List<NobEvent> events) {
        NobPlayerState a = state.requirePlayer(aId);
        NobPlayerState b = state.requirePlayer(bId);
        NobBloodline tmp = a.getCurrentBloodline();
        a.setCurrentBloodline(b.getCurrentBloodline());
        b.setCurrentBloodline(tmp);
        invalidateObservedBloodlines(state, aId, bId);
        concealBloodlinesAfterShape(state, aId, bId, events);
    }

    private static void invalidateObservedBloodlines(NobGameState state, String aId, String bId) {
        Set<String> swappedPlayerIds = Set.of(aId, bId);
        for (NobPlayerState viewer : state.getPlayers()) {
            viewer.getObservations().removeIf(observation ->
                    "BLOODLINE".equals(observation.kind())
                            && swappedPlayerIds.contains(observation.targetPlayerId()));
            NobInspectReveal reveal = viewer.getInspectReveal();
            if (reveal != null
                    && reveal.bloodline() != null
                    && swappedPlayerIds.contains(reveal.targetPlayerId())) {
                viewer.setInspectReveal(reveal.cardCode() == null
                        ? null
                        : new NobInspectReveal(
                                reveal.targetPlayerId(),
                                null,
                                reveal.cardCode(),
                                reveal.displayUntil()));
            }
        }
    }

    private static void concealBloodlinesAfterShape(
            NobGameState state,
            String aId,
            String bId,
            List<NobEvent> events
    ) {
        NobPlayerState a = state.requirePlayer(aId);
        NobPlayerState b = state.requirePlayer(bId);
        a.setKnowledgeState(NobBloodlineKnowledge.UNKNOWN_AFTER_SWAP);
        b.setKnowledgeState(NobBloodlineKnowledge.UNKNOWN_AFTER_SWAP);
        events.add(NobEvent.of("NOB_MY_BLOODLINE_KNOWLEDGE_LOST_AFTER_SWAP"));
    }

    private static void beginUnmask(
            NobGameState state,
            NobPlayerState actor,
            String targetId,
            NobCardInstance card,
            List<NobEvent> events
    ) {
        inspectBloodline(state, actor, targetId, events);
        state.setPhaseState(NobPhaseState.WAITING_FOR_OPTION);
        state.setPendingDecision(state.newDecision(
                actor.getPlayerId(),
                NobDecisionType.UNMASK_REVEAL,
                List.of("KEEP_SECRET", "REVEAL_PUBLIC"),
                List.of(),
                card == null ? null : card.instanceId(),
                state.timeoutSecondsFor(NobDecisionType.UNMASK_REVEAL),
                Map.of("targetId", targetId)
        ));
    }

    private static void revealBloodlinePublic(
            NobGameState state,
            String targetId,
            String actorId,
            List<NobEvent> events
    ) {
        NobPlayerState target = state.requirePlayer(targetId);
        target.setKnowledgeState(NobBloodlineKnowledge.PUBLICLY_REVEALED);
        NobBloodline line = target.getCurrentBloodline();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("playerId", targetId);
        payload.put("type", line.type().name());
        if (line.rank() != null) {
            payload.put("rank", line.rank());
        }
        events.add(NobEvent.of("NOB_BLOODLINE_PUBLICLY_REVEALED", payload));
        String label = bloodlineLabel(line);
        state.log(
                "NOB_BLOODLINE_PUBLICLY_REVEALED",
                target.getDisplayName() + " Bloodline was revealed: " + label,
                actorId,
                targetId,
                null,
                resolvingCode(state)
        );
        state.announce(
                "BLOODLINE_PUBLICLY_REVEALED",
                actorId,
                targetId,
                null,
                null,
                "nob.bloodline.revealed"
        );
        for (NobPlayerState player : state.getPlayers()) {
            boolean known = player.getObservations().stream().anyMatch(obs ->
                    "BLOODLINE".equals(obs.kind())
                            && targetId.equals(obs.targetPlayerId())
                            && obs.bloodline() != null);
            if (!known) {
                player.getObservations().add(new NobObservation("BLOODLINE", targetId, line, null, null));
            }
        }
    }

    private static String bloodlineLabel(NobBloodline line) {
        if (line.type() == NobBloodlineType.HALFBLOOD) {
            return "Halfblood";
        }
        return line.type().name() + (line.rank() != null ? " " + line.rank() : "");
    }

    private static void beginHunter(
            NobGameState state,
            NobPlayerState actor,
            String targetId,
            NobCardInstance card,
            List<NobEvent> events
    ) {
        inspectBloodline(state, actor, targetId, events);
        state.setPhaseState(NobPhaseState.WAITING_FOR_HUNTER_DECISION);
        NobPendingDecision hunterDecision = state.newDecision(
                actor.getPlayerId(),
                NobDecisionType.HUNTER_DECISION,
                List.of("SPARE", "ELIMINATE"),
                List.of(),
                card == null ? null : card.instanceId(),
                state.timeoutSecondsFor(NobDecisionType.HUNTER_DECISION),
                Map.of("targetId", targetId)
        );
        state.setPendingDecision(hunterDecision);
        if (actor.getInspectReveal() != null) {
            NobInspectReveal reveal = actor.getInspectReveal();
            actor.setInspectReveal(new NobInspectReveal(
                    reveal.targetPlayerId(),
                    reveal.bloodline(),
                    reveal.cardCode(),
                    hunterDecision.expiresAt()
            ));
        }
        state.announce(
                "PLAYER_DECIDING",
                actor.getPlayerId(),
                targetId,
                card == null ? null : card.cardCode(),
                null,
                "nob.hunter.deciding"
        );
    }

    private static void applyFinalJudgement(NobGameState state, NobPlayerState actor, String targetId, List<NobEvent> events) {
        revealBloodlinePublic(state, actor.getPlayerId(), actor.getPlayerId(), events);
        state.announce("ELIMINATION_SUCCESS", actor.getPlayerId(), targetId, null, null, "nob.elimination.success");
        eliminate(state, targetId, actor.getPlayerId(), events);
    }

    private static void beginMoonThief(NobGameState state, NobPlayerState actor, NobCardInstance card, List<NobEvent> events) {
        List<String> eligible = state.alivePlayers().stream()
                .filter(player -> !player.getPlayerId().equals(actor.getPlayerId()))
                .filter(player -> player.moonMarkCount() > actor.moonMarkCount())
                .map(NobPlayerState::getPlayerId)
                .toList();
        if (eligible.isEmpty()) {
            state.announce(
                    "MOON_THIEF_NO_TARGET",
                    actor.getPlayerId(),
                    null,
                    card == null ? null : card.cardCode(),
                    null,
                    "nob.moonThief.alreadyRichest"
            );
            state.holdResultDisplayFor(5_000);
            return;
        }
        state.setPhaseState(NobPhaseState.WAITING_FOR_TARGET);
        state.setPendingDecision(state.newDecision(
                actor.getPlayerId(),
                NobDecisionType.CHOOSE_TARGET,
                List.of(),
                eligible,
                card == null ? null : card.instanceId(),
                state.timeoutSecondsFor(NobDecisionType.CHOOSE_TARGET),
                Map.of("effect", NobEffectCode.MOON_THIEF.name())
        ));
        state.announce(
                "PLAYER_SELECTING_TARGET",
                actor.getPlayerId(),
                null,
                card == null ? null : card.cardCode(),
                null,
                "nob.actor.selectingTarget"
        );
        events.add(NobEvent.of("NOB_DECISION_REQUIRED", Map.of("actorId", actor.getPlayerId())));
    }

    private static void stealMoon(
            NobGameState state,
            NobPlayerState actor,
            String targetId,
            RandomSource random,
            List<NobEvent> events
    ) {
        NobPlayerState target = state.requirePlayer(targetId);
        if (target.getMoonMarks().isEmpty() || target.moonMarkCount() <= actor.moonMarkCount()) {
            return;
        }
        if (target.getMoonMarks().size() == 1) {
            takeMoonMark(state, actor, target, target.getMoonMarks().getFirst(), events);
            return;
        }
        List<NobMoonMark> marks = new ArrayList<>(target.getMoonMarks());
        List<String> pick = new ArrayList<>();
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("targetId", targetId);
        ctx.put("mode", "STEAL");
        ctx.put("effect", NobEffectCode.MOON_THIEF.name());
        while (pick.size() < 2 && !marks.isEmpty()) {
            NobMoonMark mark = marks.remove(random.nextInt(marks.size()));
            pick.add(mark.tokenId());
            ctx.put("v:" + mark.tokenId(), mark.value());
        }
        state.setPhaseState(NobPhaseState.WAITING_FOR_OPTION);
        state.setPendingDecision(state.newDecision(
                actor.getPlayerId(),
                NobDecisionType.CHOOSE_MOON_TOKEN,
                pick,
                List.of(),
                state.getCurrentResolvingCard() == null ? null : state.getCurrentResolvingCard().instanceId(),
                state.timeoutSecondsFor(NobDecisionType.CHOOSE_MOON_TOKEN),
                ctx
        ));
        events.add(NobEvent.of("NOB_DECISION_REQUIRED", Map.of("actorId", actor.getPlayerId())));
    }

    private static void takeMoonMark(
            NobGameState state,
            NobPlayerState actor,
            NobPlayerState target,
            NobMoonMark mark,
            List<NobEvent> events
    ) {
        if (mark == null || !target.getMoonMarks().remove(mark)) {
            return;
        }
        actor.getMoonMarks().add(mark);
        revealBloodlinePublic(state, actor.getPlayerId(), actor.getPlayerId(), events);
        events.add(NobEvent.of("NOB_MOON_MARK_COUNT_CHANGED", Map.of(
                "playerId", actor.getPlayerId(),
                "fromPlayerId", target.getPlayerId(),
                "value", mark.value()
        )));
        state.announce("MOON_MARK_RECEIVED", actor.getPlayerId(), target.getPlayerId(), null, null, "nob.moonMark.received");
        state.log(
                "NOB_MOON_STOLEN",
                actor.getDisplayName() + " stole a Moon Mark from " + target.getDisplayName(),
                actor.getPlayerId(),
                target.getPlayerId(),
                null,
                resolvingCode(state)
        );
    }

    private static void beginMoonBroker(
            NobGameState state,
            NobPlayerState actor,
            String targetId,
            NobCardInstance card,
            List<NobEvent> events
    ) {
        List<String> options = new ArrayList<>(List.of("INSPECT_BLOODLINE", "SKIP"));
        if (!state.requirePlayer(targetId).getMoonMarks().isEmpty()) {
            options.add("INSPECT_TOKEN");
        }
        state.setPhaseState(NobPhaseState.WAITING_FOR_OPTION);
        state.setPendingDecision(state.newDecision(
                actor.getPlayerId(),
                NobDecisionType.MOON_BROKER,
                options,
                List.of(),
                card == null ? null : card.instanceId(),
                state.timeoutSecondsFor(NobDecisionType.MOON_BROKER),
                Map.of("targetId", targetId, "step", "MENU")
        ));
        events.add(NobEvent.of("NOB_DECISION_REQUIRED", Map.of("actorId", actor.getPlayerId())));
    }

    private static void applyMoonBroker(
            NobGameState state,
            String actorId,
            NobPendingDecision pending,
            String option,
            RandomSource random,
            List<NobEvent> events
    ) {
        NobPlayerState actor = state.requirePlayer(actorId);
        String targetId = String.valueOf(pending.context().get("targetId"));
        NobPlayerState target = state.requirePlayer(targetId);
        if ("INSPECT_BLOODLINE".equals(option)) {
            inspectBloodline(state, actor, targetId, events);
            return;
        }
        if ("INSPECT_TOKEN".equals(option)) {
            beginMoonTokenInspect(state, actor, target, pending.sourceCardInstanceId(), random, events);
            return;
        }
        if ("SWAP".equals(option)) {
            swapInspectedMoon(state, actor, target, pending, events);
        }
    }

    private static void beginMoonTokenInspect(
            NobGameState state,
            NobPlayerState actor,
            NobPlayerState target,
            String sourceCardId,
            RandomSource random,
            List<NobEvent> events
    ) {
        List<NobMoonMark> marks = new ArrayList<>(target.getMoonMarks());
        if (marks.isEmpty()) {
            return;
        }
        if (marks.size() == 1) {
            revealMoonMark(state, actor, target, marks.getFirst(), events);
            offerMoonSwap(state, actor, target, sourceCardId, marks.getFirst().tokenId(), events);
            return;
        }
        List<String> pick = new ArrayList<>();
        while (pick.size() < 2 && !marks.isEmpty()) {
            pick.add(marks.remove(random.nextInt(marks.size())).tokenId());
        }
        state.setPhaseState(NobPhaseState.WAITING_FOR_OPTION);
        state.setPendingDecision(state.newDecision(
                actor.getPlayerId(),
                NobDecisionType.CHOOSE_MOON_TOKEN,
                pick,
                List.of(),
                sourceCardId,
                state.timeoutSecondsFor(NobDecisionType.CHOOSE_MOON_TOKEN),
                Map.of("targetId", target.getPlayerId())
        ));
        events.add(NobEvent.of("NOB_DECISION_REQUIRED", Map.of("actorId", actor.getPlayerId())));
    }

    private static void applyMoonTokenPick(
            NobGameState state,
            String actorId,
            NobPendingDecision pending,
            String option,
            String cardInstanceId,
            List<NobEvent> events
    ) {
        NobPlayerState actor = state.requirePlayer(actorId);
        String targetId = String.valueOf(pending.context().get("targetId"));
        NobPlayerState target = state.requirePlayer(targetId);
        String tokenId = option != null && !option.isBlank() ? option : cardInstanceId;
        NobMoonMark mark = target.getMoonMarks().stream()
                .filter(item -> item.tokenId().equals(tokenId))
                .findFirst()
                .orElse(null);
        if (mark == null && !target.getMoonMarks().isEmpty()) {
            mark = target.getMoonMarks().getFirst();
        }
        if ("STEAL".equals(pending.context().get("mode"))) {
            takeMoonMark(state, actor, target, mark, events);
            return;
        }
        if (mark != null) {
            revealMoonMark(state, actor, target, mark, events);
        }
        offerMoonSwap(state, actor, target, pending.sourceCardInstanceId(), mark == null ? tokenId : mark.tokenId(), events);
    }

    private static void revealMoonMark(
            NobGameState state,
            NobPlayerState actor,
            NobPlayerState target,
            NobMoonMark mark,
            List<NobEvent> events
    ) {
        actor.getObservations().add(new NobObservation("MOON", target.getPlayerId(), null, null, mark.value()));
        events.add(NobEvent.of("NOB_PRIVATE_MOON_SEEN", Map.of("viewerId", actor.getPlayerId())));
        state.log(
                "NOB_MOON_INSPECTED",
                actor.getDisplayName() + " inspected a Moon Mark of " + target.getDisplayName(),
                actor.getPlayerId(),
                target.getPlayerId(),
                null,
                resolvingCode(state)
        );
    }

    private static void offerMoonSwap(
            NobGameState state,
            NobPlayerState actor,
            NobPlayerState target,
            String sourceCardId,
            String inspectedTokenId,
            List<NobEvent> events
    ) {
        List<String> options = new ArrayList<>();
        options.add("KEEP");
        if (!actor.getMoonMarks().isEmpty() && !target.getMoonMarks().isEmpty()) {
            options.add("SWAP");
        }
        state.setPhaseState(NobPhaseState.WAITING_FOR_OPTION);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("targetId", target.getPlayerId());
        ctx.put("step", "SWAP");
        if (inspectedTokenId != null) {
            ctx.put("inspectedTokenId", inspectedTokenId);
        }
        state.setPendingDecision(state.newDecision(
                actor.getPlayerId(),
                NobDecisionType.MOON_BROKER,
                options,
                List.of(),
                sourceCardId,
                state.timeoutSecondsFor(NobDecisionType.MOON_BROKER),
                ctx
        ));
        events.add(NobEvent.of("NOB_DECISION_REQUIRED", Map.of("actorId", actor.getPlayerId())));
    }

    private static void swapInspectedMoon(
            NobGameState state,
            NobPlayerState actor,
            NobPlayerState target,
            NobPendingDecision pending,
            List<NobEvent> events
    ) {
        if (actor.getMoonMarks().isEmpty() || target.getMoonMarks().isEmpty()) {
            return;
        }
        String inspectedId = pending.context().get("inspectedTokenId") instanceof String id ? id : null;
        int theirsIndex = 0;
        if (inspectedId != null) {
            for (int i = 0; i < target.getMoonMarks().size(); i++) {
                if (inspectedId.equals(target.getMoonMarks().get(i).tokenId())) {
                    theirsIndex = i;
                    break;
                }
            }
        }
        var mine = actor.getMoonMarks().removeFirst();
        var theirs = target.getMoonMarks().remove(theirsIndex);
        actor.getMoonMarks().add(theirs);
        target.getMoonMarks().add(mine);
        events.add(NobEvent.of("NOB_MOON_MARK_COUNT_CHANGED"));
    }

    private static void beginEchoes(
            NobGameState state,
            NobPlayerState actor,
            NobCardInstance card,
            RandomSource random,
            List<NobEvent> events
    ) {
        state.getEchoHold().clear();
        int take = Math.min(2, state.getDiscardPile().size());
        for (int i = 0; i < take; i++) {
            int index = random.nextInt(state.getDiscardPile().size());
            state.getEchoHold().add(state.getDiscardPile().remove(index));
        }
        state.setEchoSource(card);
        state.setEchoPicked(null);
        if (state.getEchoHold().isEmpty()) {
            completeCurrentResolution(state);
            return;
        }
        for (NobCardInstance pulled : state.getEchoHold()) {
            actor.getObservations().add(new NobObservation("ECHO", actor.getPlayerId(), null, pulled.cardCode(), null));
        }
        List<String> options = new ArrayList<>();
        options.add("KEEP_FOR_LATER");
        options.add("PLAY_NOW");
        state.setPhaseState(NobPhaseState.WAITING_FOR_OPTION);
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("source", card.instanceId());
        state.setPendingDecision(state.newDecision(
                actor.getPlayerId(),
                NobDecisionType.ECHO_CHOOSE,
                options,
                List.of(),
                card.instanceId(),
                state.timeoutSecondsFor(NobDecisionType.ECHO_CHOOSE),
                ctx
        ));
        events.add(NobEvent.of("NOB_DECISION_REQUIRED", Map.of("actorId", actor.getPlayerId())));
    }

    private static boolean canPlayNow(NobCardInstance card) {
        return card.roleType() != NobRoleType.SPECIAL
                && card.effectCode() != NobEffectCode.VEIL_REVERSAL
                && card.effectCode() != NobEffectCode.LAST_OFFERING
                && card.effectCode() != NobEffectCode.LAST_HOPE;
    }

    private static void applyEchoChoice(
            NobGameState state,
            String actorId,
            NobPendingDecision pending,
            String option,
            String cardInstanceId,
            RandomSource random,
            List<NobEvent> events
    ) {
        NobPlayerState actor = state.requirePlayer(actorId);
        List<NobCardInstance> drawn = new ArrayList<>(state.getEchoHold());
        if (drawn.isEmpty()) {
            return;
        }
        if (cardInstanceId == null || cardInstanceId.isBlank()) {
            drawn.forEach(card -> state.getDiscardPile().add(card));
            state.getEchoHold().clear();
            return;
        }
        NobCardInstance chosen = drawn.stream()
                .filter(card -> card.instanceId().equals(cardInstanceId) || card.cardCode().equals(cardInstanceId))
                .findFirst()
                .orElse(null);
        if (chosen == null) {
            drawn.forEach(card -> state.getDiscardPile().add(card));
            state.getEchoHold().clear();
            return;
        }
        if ("PLAY_NOW".equals(option) && !canPlayNow(chosen)) {
            drawn.forEach(card -> state.getDiscardPile().add(card));
            state.getEchoHold().clear();
            return;
        }
        for (NobCardInstance card : drawn) {
            if (!card.instanceId().equals(chosen.instanceId())) {
                state.getDiscardPile().add(card);
            }
        }
        state.getEchoHold().clear();
        state.setEchoPicked(chosen);
        actor.getHand().add(chosen);
        if ("PLAY_NOW".equals(option)) {
            state.revealCard(actor, chosen);
            startCard(state, actor, chosen, random, events);
        }
    }

    private static void beginKill(
            NobGameState state,
            NobPlayerState attacker,
            String targetId,
            NobCardInstance card,
            NobKillSource source,
            RandomSource random,
            List<NobEvent> events
    ) {
        NobPlayerState target = state.requirePlayer(targetId);
        state.setActiveKill(new NobGameState.NobKillAttempt(
                attacker.getPlayerId(),
                targetId,
                source,
                card == null ? null : card.instanceId()
        ));
        String auto = autoReactionOption(target);
        if (auto != null) {
            applyReactionEffect(state, auto, random, events);
            return;
        }
        finalizeKill(state, events);
    }

    private static String autoReactionOption(NobPlayerState target) {
        if (target.getHand().stream().anyMatch(card -> card.effectCode() == NobEffectCode.VEIL_REVERSAL)) {
            return "VEIL_REVERSAL";
        }
        if (target.getHand().stream().anyMatch(card -> card.effectCode() == NobEffectCode.LAST_OFFERING)) {
            return "LAST_OFFERING";
        }
        return null;
    }

    private static void applyReaction(
            NobGameState state,
            String actorId,
            NobAction action,
            RandomSource random,
            List<NobEvent> events
    ) {
        state.setPendingDecision(null);
        applyReactionEffect(state, action.option(), random, events);
        if (state.getPendingDecision() == null) {
            completeCurrentResolution(state);
        }
    }

    private static void applyReactionEffect(
            NobGameState state,
            String option,
            RandomSource random,
            List<NobEvent> events
    ) {
        NobGameState.NobKillAttempt kill = state.getActiveKill();
        if (kill == null) {
            return;
        }
        NobPlayerState target = state.requirePlayer(kill.targetId());
        if ("VEIL_REVERSAL".equals(option)) {
            revealSpecial(state, target, NobEffectCode.VEIL_REVERSAL, events);
            state.announce(
                    "VEIL_REVERSAL",
                    kill.attackerId(),
                    kill.targetId(),
                    "NOB-SP-VEIL-REVERSAL",
                    "NOB-SP-VEIL-REVERSAL",
                    "nob.reaction.veilReversal"
            );
            state.holdResultDisplay();
            eliminate(state, kill.attackerId(), kill.targetId(), events);
            state.setActiveKill(null);
            return;
        }
        if ("LAST_OFFERING".equals(option)) {
            revealSpecial(state, target, NobEffectCode.LAST_OFFERING, events);
            state.awardMoonMark(target, random);
            state.announce(
                    "GLORIOUS_SACRIFICE",
                    kill.attackerId(),
                    kill.targetId(),
                    "NOB-SP-LAST-OFFERING",
                    "NOB-SP-LAST-OFFERING",
                    "nob.reaction.gloriousSacrifice"
            );
            state.holdResultDisplay();
            finalizeKill(state, events);
            return;
        }
        finalizeKill(state, events);
    }

    private static void finalizeKill(NobGameState state, List<NobEvent> events) {
        NobGameState.NobKillAttempt kill = state.getActiveKill();
        state.setActiveKill(null);
        if (kill != null) {
            state.announce(
                    "ELIMINATION_SUCCESS",
                    kill.attackerId(),
                    kill.targetId(),
                    null,
                    null,
                    "nob.elimination.success"
            );
            eliminate(state, kill.targetId(), kill.attackerId(), events);
        }
    }

    private static NobCardInstance revealSpecial(
            NobGameState state,
            NobPlayerState player,
            NobEffectCode code,
            List<NobEvent> events
    ) {
        NobCardInstance card = player.getHand().stream().filter(item -> item.effectCode() == code).findFirst().orElse(null);
        if (card == null) {
            return null;
        }
        player.getHand().remove(card);
        player.getRevealedCards().add(card);
        player.getUsedCards().add(card);
        if (state != null) {
            state.setCurrentResolvingCard(card);
            state.log(
                    "NOB_ROLE_REVEALED",
                    player.getDisplayName() + " revealed " + card.cardCode(),
                    player.getPlayerId(),
                    null,
                    null,
                    card.cardCode()
            );
        }
        events.add(NobEvent.of("NOB_REACTION_REVEALED", Map.of("cardCode", card.cardCode())));
        return card;
    }

    private static void eliminate(NobGameState state, String playerId, String actorId, List<NobEvent> events) {
        NobPlayerState player = state.requirePlayer(playerId);
        player.setAlive(false);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("playerId", playerId);
        if (actorId != null) {
            payload.put("actorPlayerId", actorId);
        }
        events.add(NobEvent.of("NOB_PLAYER_ELIMINATED", payload));
        String actorName = actorId == null ? null : state.requirePlayer(actorId).getDisplayName();
        String text = actorName == null
                ? player.getDisplayName() + " was eliminated"
                : actorName + " eliminated " + player.getDisplayName();
        state.log("NOB_PLAYER_ELIMINATED", text, actorId, playerId, null, resolvingCode(state));
    }

    private static void advancePhase(NobGameState state, RandomSource random, List<NobEvent> events) {
        NobPhase next = switch (state.getPhase()) {
            case SHADOW_STALKER -> NobPhase.BLOOD_SEER;
            case BLOOD_SEER -> NobPhase.SHAPESHIFTER;
            case SHAPESHIFTER -> NobPhase.FERAL_KILLER;
            case FERAL_KILLER -> NobPhase.HUNTER;
            case HUNTER -> NobPhase.BLOODLINE_REVEAL;
            default -> NobPhase.SCORING;
        };
        if (next == NobPhase.BLOODLINE_REVEAL) {
            revealAndScore(state, random, events);
            return;
        }
        state.beginNightPhase(next);
        events.add(NobEvent.of("NOB_PHASE_CHANGED", Map.of("phase", next.name())));
        if (state.playersWhoMustSubmit().isEmpty()) {
            advancePhase(state, random, events);
        }
    }

    private static void revealAndScore(NobGameState state, RandomSource random, List<NobEvent> events) {
        state.setPhase(NobPhase.BLOODLINE_REVEAL);
        state.setPhaseState(NobPhaseState.IDLE);
        for (NobPlayerState player : state.alivePlayers()) {
            player.setKnowledgeState(NobBloodlineKnowledge.PUBLICLY_REVEALED);
            player.getHand().stream()
                    .filter(card -> card.effectCode() == NobEffectCode.LAST_HOPE)
                    .findFirst()
                    .ifPresent(card -> {
                        player.getHand().remove(card);
                        player.getRevealedCards().add(card);
                        player.getUsedCards().add(card);
                        events.add(NobEvent.of("NOB_ROLE_REVEALED", Map.of(
                                "playerId", player.getPlayerId(),
                                "cardCode", card.cardCode()
                        )));
                        state.setCurrentResolvingCard(card);
                        state.log(
                                "NOB_ROLE_REVEALED",
                                player.getDisplayName() + " revealed " + card.cardCode(),
                                player.getPlayerId(),
                                null,
                                null,
                                card.cardCode()
                        );
                        state.announce(
                                "LAST_HOPE_TRIGGERED",
                                player.getPlayerId(),
                                null,
                                card.cardCode(),
                                null,
                                "nob.lastHope.triggered"
                        );
                    });
        }
        events.add(NobEvent.of("NOB_FINAL_REVEAL"));
        for (NobPlayerState player : state.getPlayers()) {
            player.setKnowledgeState(NobBloodlineKnowledge.PUBLICLY_REVEALED);
        }
        state.setPhase(NobPhase.SCORING);
        NobScoringService.MainResult result = NobScoringService.compareSurvivors(state);
        boolean lastHope = result == NobScoringService.MainResult.LAST_HOPE_HALFBLOOD
                || state.alivePlayers().stream().anyMatch(player ->
                player.getRevealedCards().stream().anyMatch(card -> card.effectCode() == NobEffectCode.LAST_HOPE));
        String winning = switch (result) {
            case VAMPIRE -> "VAMPIRE";
            case WEREWOLF -> "WEREWOLF";
            default -> null;
        };
        state.setLastRoundResult(new com.partygameonline.game.nob.domain.NobRoundResult(result.name(), winning, lastHope));
        Map<String, Object> roundPayload = new LinkedHashMap<>();
        roundPayload.put("result", result.name());
        roundPayload.put("winningBloodline", winning == null ? "" : winning);
        roundPayload.put("lastHopeTriggered", lastHope);
        events.add(NobEvent.of("NOB_ROUND_RESULT", roundPayload));
        state.announce("ROUND_RESULT", null, null, null, null, "nob.round.result");
        List<String> rewards = NobScoringService.rewardPlayerIds(state, result);
        state.beginRoundSummary(rewards, random);
        events.add(NobEvent.of("NOB_PHASE_CHANGED", Map.of("phase", NobPhase.ROUND_SUMMARY.name())));
    }

    private static void completeCurrentResolution(NobGameState state) {
        if (state.getPendingDecision() != null || state.getActiveKill() != null) {
            return;
        }
        state.holdResultDisplay();
    }

    private static void applyTimeout(NobGameState state, RandomSource random, List<NobEvent> events) {
        Instant now = Instant.now();
        if (state.getResolutionDisplayExpiresAt() != null && !state.getResolutionDisplayExpiresAt().isAfter(now)) {
            if (!state.getPresentationQueue().isEmpty()) {
                state.setAnnouncement(state.getPresentationQueue().poll());
                state.setResolutionDisplayExpiresAt(now.plusMillis(Math.max(state.getTiming().resolutionCardDisplayMs(), 1)));
                return;
            }
            state.setResolutionDisplayExpiresAt(null);
            advanceResolution(state, random, events);
            return;
        }
        if (state.getPhase() == NobPhase.ROUND_SUMMARY) {
            for (String playerId : List.copyOf(state.unclaimedMoonPlayerIds())) {
                while (state.hasUnclaimedMoonPick(playerId)) {
                    List<com.partygameonline.game.nob.domain.NobMoonTokenOption> options =
                            new ArrayList<>(state.getMoonTokenOffers().getOrDefault(playerId, List.of()));
                    if (options.isEmpty()) {
                        break;
                    }
                    String optionId = options.get(random.nextInt(options.size())).optionId();
                    emitAutoAction(state, events, playerId, "MOON_MARK_PICK");
                    applyMoonPick(state, playerId, optionId, random, events, true);
                }
            }
            finishRoundSummary(state, random, events);
            return;
        }
        NobPendingDecision pending = state.getPendingDecision();
        if (pending != null) {
            switch (pending.type()) {
                case CHOOSE_TARGET -> {
                    List<String> pool = new ArrayList<>(pending.allowedTargetIds());
                    List<String> auto = new ArrayList<>();
                    int need = pending.context().get("need") instanceof Number number ? number.intValue() : 1;
                    while (!pool.isEmpty() && auto.size() < need) {
                        auto.add(pool.remove(random.nextInt(pool.size())));
                    }
                    emitAutoAction(state, events, pending.actorId(), "CHOOSE_TARGET");
                    if (auto.isEmpty()) {
                        state.setPendingDecision(null);
                        advanceResolution(state, random, events);
                        return;
                    }
                    applyTarget(state, pending.actorId(), new NobAction(
                            NobAction.CHOOSE_TARGET, null, null, null, null, auto, null
                    ), random, events);
                }
                case HUNTER_DECISION -> {
                    emitAutoAction(state, events, pending.actorId(), "HUNTER_DECISION");
                    applyOption(state, pending.actorId(), new NobAction(
                            NobAction.HUNTER_DECISION, null, null, null, null, List.of(), "SPARE"
                    ), random, events);
                }
                case REACTION -> {
                    emitAutoAction(state, events, pending.actorId(), "REACTION");
                    String auto = pending.allowedOptions().contains("VEIL_REVERSAL")
                            ? "VEIL_REVERSAL"
                            : pending.allowedOptions().contains("LAST_OFFERING")
                                    ? "LAST_OFFERING"
                                    : "DECLINE";
                    applyReaction(state, pending.actorId(), new NobAction(
                            NobAction.REACTION, null, null, null, null, List.of(), auto
                    ), random, events);
                }
                case SHAPE_SWAP -> applyOption(state, pending.actorId(), new NobAction(
                        NobAction.CHOOSE_OPTION, null, null, null, null, List.of(), "KEEP"
                ), random, events);
                case UNMASK_REVEAL -> applyOption(state, pending.actorId(), new NobAction(
                        NobAction.CHOOSE_OPTION, null, null, null, null, List.of(), "KEEP_SECRET"
                ), random, events);
                case CHOOSE_HIDDEN_CARD -> {
                    String cardId = pending.allowedOptions().isEmpty()
                            ? null
                            : pending.allowedOptions().get(random.nextInt(pending.allowedOptions().size()));
                    applyOption(state, pending.actorId(), new NobAction(
                            NobAction.CHOOSE_OPTION, null, null, cardId, null, List.of(), cardId
                    ), random, events);
                }
                case ECHO_CHOOSE -> {
                    String echoId = state.getEchoHold().isEmpty() ? null : state.getEchoHold().getFirst().instanceId();
                    applyOption(state, pending.actorId(), new NobAction(
                            NobAction.CHOOSE_OPTION, null, null, echoId, null, List.of(), "KEEP_FOR_LATER"
                    ), random, events);
                }
                case CHOOSE_MOON_TOKEN -> {
                    String tokenId = pending.allowedOptions().isEmpty()
                            ? null
                            : pending.allowedOptions().get(random.nextInt(pending.allowedOptions().size()));
                    applyOption(state, pending.actorId(), new NobAction(
                            NobAction.CHOOSE_OPTION, null, null, tokenId, null, List.of(), tokenId
                    ), random, events);
                }
                case MOON_BROKER -> {
                    String auto = pending.allowedOptions().contains("SKIP")
                            ? "SKIP"
                            : pending.allowedOptions().contains("KEEP") ? "KEEP" : "SKIP";
                    applyOption(state, pending.actorId(), new NobAction(
                            NobAction.CHOOSE_OPTION, null, null, null, null, List.of(), auto
                    ), random, events);
                }
                default -> {
                    state.setPendingDecision(null);
                    advanceResolution(state, random, events);
                }
            }
            return;
        }
        if (state.getPhase() == NobPhase.DRAFT_PICK_1 || state.getPhase() == NobPhase.DRAFT_PICK_2) {
            for (NobPlayerState player : List.copyOf(state.getPlayers())) {
                if (!state.getDraftPicks().containsKey(player.getPlayerId())) {
                    List<NobCardInstance> hand = state.getDraftHands().get(player.getPlayerId());
                    if (hand != null && !hand.isEmpty()) {
                        NobCardInstance pick = hand.get(random.nextInt(hand.size()));
                        emitAutoAction(state, events, player.getPlayerId(), "DRAFT_PICK");
                        applyDraft(state, player.getPlayerId(), new NobAction(
                                NobAction.DRAFT_PICK, null, null, pick.instanceId(), null, List.of(), null
                        ), random, events);
                    }
                }
            }
            return;
        }
        if (isNightPhase(state.getPhase())
                && state.getPhaseState() == NobPhaseState.WAITING_FOR_PHASE_SUBMISSIONS
                && state.getCurrentResolvingCard() == null) {
            for (NobPlayerState player : state.playersWhoMustSubmit()) {
                if (state.getPhaseSubmissions().putIfAbsent(player.getPlayerId(), List.of("PASS")) == null) {
                    emitAutoAction(state, events, player.getPlayerId(), "PHASE_SUBMIT");
                }
            }
            state.closePhaseSubmissions();
            advanceResolution(state, random, events);
            return;
        }
        if (state.getPendingDecision() == null
                && state.getActiveKill() == null
                && state.getCurrentResolvingCard() != null) {
            completeCurrentResolution(state);
        }
    }

    private static void emitAutoAction(NobGameState state, List<NobEvent> events, String playerId, String actionType) {
        events.add(NobEvent.of("NOB_PLAYER_AUTO_ACTION", Map.of("playerId", playerId, "actionType", actionType)));
        state.announce("PLAYER_AUTO_ACTION", playerId, null, null, null, "nob.timeout.autoAction");
    }

    public static boolean isNightPhase(NobPhase phase) {
        return phase == NobPhase.SHADOW_STALKER
                || phase == NobPhase.BLOOD_SEER
                || phase == NobPhase.SHAPESHIFTER
                || phase == NobPhase.FERAL_KILLER
                || phase == NobPhase.HUNTER;
    }

    private static List<String> targetsOf(NobAction action) {
        List<String> targets = new ArrayList<>();
        if (action.targetPlayerIds() != null) {
            for (String id : action.targetPlayerIds()) {
                if (id != null && !id.isBlank() && !targets.contains(id)) {
                    targets.add(id);
                }
            }
        }
        if (targets.isEmpty() && action.cardInstanceId() != null && !action.cardInstanceId().isBlank()) {
            targets.add(action.cardInstanceId());
        }
        return targets;
    }

    private static NobCardInstance findUsed(NobPlayerState player, String instanceId) {
        if (instanceId == null) {
            return null;
        }
        return player.getUsedCards().stream()
                .filter(card -> card.instanceId().equals(instanceId))
                .findFirst()
                .orElse(null);
    }
}
